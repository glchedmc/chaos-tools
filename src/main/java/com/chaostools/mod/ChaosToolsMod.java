package com.chaostools.mod;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

import com.chaostools.mod.enchantment.ModEnchantments;
import com.chaostools.mod.item.ModComponents;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class ChaosToolsMod implements ModInitializer
{
	public static final String MOD_ID = "chaostools";

	// --- Debt ---
	private static final Set<String> WORTHLESS_BLOCKS = Set.of("minecraft:dirt",
		"minecraft:coarse_dirt", "minecraft:sand", "minecraft:red_sand",
		"minecraft:gravel", "minecraft:mud", "minecraft:podzol",
		"minecraft:grass_block");
	private static final int HUNGER_DRAIN_INTERVAL_TICKS = 40; // ~2s

	// --- Diet ---
	// How much scale is removed per hit, per enchant level. Level 1 = subtle
	// (-0.03/hit); level 10 = dramatic (-0.3/hit, near the floor in 3 hits).
	private static final double DIET_SHRINK_PER_HIT_PER_LEVEL = 0.03;
	private static final double DIET_SCALE_FLOOR = 0.15;

	private int tickCounter = 0;

	@Override
	public void onInitialize()
	{
		ModComponents.init();
		registerDebt();
		registerDiet();
		registerPermit();
	}

	// ============================== DEBT ==============================

	private void registerDebt()
	{
		// The actual curse: to mine ONE valuable block, you need
		// enchantLevel banked credits ALREADY saved up from mining
		// worthless blocks first. Level 1 = 1:1. Level 50 = 50 worthless
		// blocks banked before you can mine a single good one.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if(level.isClientSide())
				return true;

			ItemStack stack = player.getMainHandItem();
			int enchantLevel = getEnchantLevel(level, stack, ModEnchantments.DEBT);
			if(enchantLevel <= 0)
				return true;

			String blockId =
				BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			if(WORTHLESS_BLOCKS.contains(blockId))
				return true;

			int credits = stack.getOrDefault(ModComponents.DEBT, 0);
			if(credits >= enchantLevel)
				return true;

			if(player instanceof ServerPlayer serverPlayer)
				serverPlayer.sendSystemMessage(Component.literal(
					"Need " + (enchantLevel - credits)
						+ " more worthless blocks mined first ("
						+ credits + "/" + enchantLevel + " banked)"),
					true);

			return false;
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if(level.isClientSide())
				return;
			if(!(player instanceof ServerPlayer serverPlayer))
				return;

			ItemStack stack = player.getMainHandItem();
			int enchantLevel = getEnchantLevel(level, stack, ModEnchantments.DEBT);
			if(enchantLevel <= 0)
				return;

			String blockId =
				BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			int credits = stack.getOrDefault(ModComponents.DEBT, 0);

			if(WORTHLESS_BLOCKS.contains(blockId))
			{
				credits += 1;
				stack.set(ModComponents.DEBT, credits);
				serverPlayer.sendSystemMessage(Component.literal(
					"Banked: " + credits + "/" + enchantLevel), true);
			}
			else
			{
				credits -= enchantLevel;
				stack.set(ModComponents.DEBT, credits);
				serverPlayer.sendSystemMessage(Component.literal(
					"Spent " + enchantLevel + " credits"), true);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if(tickCounter % HUNGER_DRAIN_INTERVAL_TICKS != 0)
				return;

			for(ServerPlayer player : server.getPlayerList().getPlayers())
			{
				Level level = player.level();
				boolean holdingUnpaidTool =
					isDebtUnpaid(level, player.getMainHandItem())
						|| isDebtUnpaid(level, player.getOffhandItem());

				if(!holdingUnpaidTool)
					continue;

				FoodData foodData = player.getFoodData();
				if(foodData.getFoodLevel() > 1)
					foodData.setFoodLevel(foodData.getFoodLevel() - 1);
			}
		});
	}

	private boolean isDebtUnpaid(Level level, ItemStack stack)
	{
		int enchantLevel = getEnchantLevel(level, stack, ModEnchantments.DEBT);
		if(enchantLevel <= 0)
			return false;

		return stack.getOrDefault(ModComponents.DEBT, 0) < enchantLevel;
	}

	// ============================== DIET ==============================

	private void registerDiet()
	{
		// Shrink the target a little every time it's hit by a
		// Diet-enchanted weapon. NOTE: ServerLivingEntityEvents.ALLOW_DAMAGE
		// is meant as an allow/deny filter, but it's also a convenient
		// "damage is about to happen" hook - we always return true (never
		// block the hit), just piggyback the shrink effect here since I'm
		// not certain a dedicated "after damage" event exists under a
		// name I can rely on.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			applyDietShrink(entity, source);
			return true;
		});

		// Cap death drops to a single item when killed by a Diet weapon.
		LootTableEvents.MODIFY_DROPS.register((key, context, drops) -> {
			Object thisEntity =
				context.getOptionalParameter(LootContextParams.THIS_ENTITY);
			if(!(thisEntity instanceof LivingEntity))
				return;

			// KILLER_ENTITY/DIRECT_KILLER_ENTITY were renamed to
			// ATTACKING_ENTITY/DIRECT_ATTACKING_ENTITY somewhere between
			// 1.20.6 and 1.21 - confirmed from real dated docs, not a
			// guess this time. "Direct" means whoever actually delivered
			// the hit (vs. e.g. a skeleton's arrow being the "direct"
			// attacker while the skeleton itself is just "attacking").
			Object killer = context.getOptionalParameter(
				LootContextParams.DIRECT_ATTACKING_ENTITY);
			if(!(killer instanceof ServerPlayer player))
				return;

			int enchantLevel = getEnchantLevel(player.level(),
				player.getMainHandItem(), ModEnchantments.DIET);
			if(enchantLevel <= 0)
				return;

			if(drops.isEmpty())
				return;

			ItemStack keep = drops.get(0).copyWithCount(1);
			drops.clear();
			drops.add(keep);
		});
	}

	private void applyDietShrink(LivingEntity entity, DamageSource source)
	{
		if(!(source.getEntity() instanceof ServerPlayer player))
			return;

		int enchantLevel = getEnchantLevel(entity.level(),
			player.getMainHandItem(), ModEnchantments.DIET);
		if(enchantLevel <= 0)
			return;

		AttributeInstance scaleAttribute = entity.getAttribute(Attributes.SCALE);
		if(scaleAttribute == null)
			return;

		double shrinkAmount = DIET_SHRINK_PER_HIT_PER_LEVEL * enchantLevel;
		double newScale = Math.max(DIET_SCALE_FLOOR,
			scaleAttribute.getBaseValue() - shrinkAmount);
		scaleAttribute.setBaseValue(newScale);
	}

	// ============================== PERMIT ==============================

	private static final Random RANDOM = new Random();
	private static final int PERMIT_BASE_CHANCE = 55;
	private static final int PERMIT_CHANCE_PER_LEVEL = 4;
	private static final int PERMIT_MAX_CHANCE = 95;

	private void registerPermit()
	{
		// Simplest of the three so far: pure per-attempt dice roll, no
		// persistent state needed at all. Higher enchant level = better
		// approval odds.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if(level.isClientSide())
				return true;

			ItemStack stack = player.getMainHandItem();
			int enchantLevel = getEnchantLevel(level, stack, ModEnchantments.PERMIT);
			if(enchantLevel <= 0)
				return true;

			int approvalChance = Math.min(PERMIT_MAX_CHANCE,
				PERMIT_BASE_CHANCE + PERMIT_CHANCE_PER_LEVEL * enchantLevel);

			boolean approved = RANDOM.nextInt(100) < approvalChance;

			if(player instanceof ServerPlayer serverPlayer)
				serverPlayer.sendSystemMessage(Component.literal(approved
					? "Permit approved." : "Permit denied - try again."),
					true);

			return approved;
		});
	}

	// ============================== SHARED ==============================

	/**
	 * Enchantments live in a dynamic (data-driven) registry, so we can't
	 * just call a static field like BuiltInRegistries.ENCHANTMENT - we
	 * have to ask whatever Level we're currently in for its registry
	 * access, then look our enchantment up by its ResourceKey. Returns 0
	 * if the stack doesn't have the given enchantment at all.
	 */
	private int getEnchantLevel(Level level, ItemStack stack,
		ResourceKey<Enchantment> key)
	{
		if(stack.isEmpty())
			return 0;

		Optional<Holder.Reference<Enchantment>> holder =
			level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);

		if(holder.isEmpty())
			return 0;

		return EnchantmentHelper.getItemEnchantmentLevel(holder.get(), stack);
	}
}

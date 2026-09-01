package com.chaostools.mod.enchantment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Enchantments live in a DYNAMIC (data-driven) registry, not a static one
 * like Item - the actual enchantment definition is the JSON file at
 * data/chaostools/enchantment/debt.json, not a Java object we construct.
 * This class just holds a ResourceKey - a typed "pointer" we can use to
 * look the real thing up later, once the game's registries are loaded.
 */
public final class ModEnchantments
{
	public static final ResourceKey<Enchantment> DEBT = ResourceKey.create(
		Registries.ENCHANTMENT,
		Identifier.fromNamespaceAndPath("chaostools", "debt"));

	public static final ResourceKey<Enchantment> DIET = ResourceKey.create(
		Registries.ENCHANTMENT,
		Identifier.fromNamespaceAndPath("chaostools", "diet"));

	public static final ResourceKey<Enchantment> PERMIT = ResourceKey.create(
		Registries.ENCHANTMENT,
		Identifier.fromNamespaceAndPath("chaostools", "permit"));

	private ModEnchantments()
	{}
}

package com.chaostools.mod.item;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * Custom data components for chaostools items - the modern (1.20.5+)
 * replacement for storing arbitrary state on an ItemStack via NBT tags.
 * NOTE: this specific registration pattern (builder().persistent(codec)
 * .networkSynchronized(streamCodec).build()) predates the 26.x rendering
 * rewrite that caused most of this session's surprises, so confidence
 * here is higher than the render-pipeline fixes - but still not
 * cross-checked against a real source the way those were.
 */
public final class ModComponents
{
	public static final DataComponentType<Integer> DEBT = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath("chaostools", "debt"),
		DataComponentType.<Integer>builder().persistent(Codec.INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT).build());

	private ModComponents()
	{}

	public static void init()
	{
		// Just needs to be referenced once to trigger the static
		// initializers above during mod startup.
	}
}

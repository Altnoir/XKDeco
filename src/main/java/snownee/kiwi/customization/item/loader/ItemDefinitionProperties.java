package snownee.kiwi.customization.item.loader;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import snownee.kiwi.customization.util.codec.CustomizationCodecs;
import snownee.kiwi.customization.util.codec.JavaOps;

public record ItemDefinitionProperties(
		PartialVanillaProperties vanillaProperties) {

	private static final ItemDefinitionProperties EMPTY;

	static {
		PartialVanillaProperties vanillaProperties = PartialVanillaProperties.MAP_CODEC.codec()
				.parse(JavaOps.INSTANCE, Map.of())
				.getOrThrow(false, e -> {
					throw new IllegalStateException("Failed to parse empty ItemDefinitionProperties: " + e);
				});
		EMPTY = new ItemDefinitionProperties(vanillaProperties);
	}

	public static MapCodec<ItemDefinitionProperties> mapCodec() {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				PartialVanillaProperties.MAP_CODEC.forGetter(ItemDefinitionProperties::vanillaProperties)
		).apply(instance, ItemDefinitionProperties::new));
	}

	public static MapCodec<Optional<ItemDefinitionProperties>> mapCodecField() {
		return mapCodec().codec().optionalFieldOf(ItemCodecs.ITEM_PROPERTIES_KEY);
	}

	public static ItemDefinitionProperties empty() {
		return EMPTY;
	}

	public ItemDefinitionProperties merge(ItemDefinitionProperties templateProps) {
		return new ItemDefinitionProperties(vanillaProperties.merge(templateProps.vanillaProperties));
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private static <T> Optional<T> or(Optional<T> a, Optional<T> b) {
		return a.isPresent() ? a : b;
	}

	public record PartialVanillaProperties(
			Optional<Integer> maxStackSize,
			Optional<Integer> maxDamage,
			Optional<ResourceKey<Item>> craftingRemainingItem,
			Optional<FoodProperties> food,
			Optional<Rarity> rarity
	) {
		public static final MapCodec<PartialVanillaProperties> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.intRange(1, 64).optionalFieldOf("stacks_to").forGetter(PartialVanillaProperties::maxStackSize),
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("max_damage").forGetter(PartialVanillaProperties::maxDamage),
				ResourceKey.codec(Registries.ITEM)
						.optionalFieldOf("crafting_remaining_item")
						.forGetter(PartialVanillaProperties::craftingRemainingItem),
				CustomizationCodecs.FOOD.optionalFieldOf("food").forGetter(PartialVanillaProperties::food),
				CustomizationCodecs.RARITY_CODEC.optionalFieldOf("rarity").forGetter(PartialVanillaProperties::rarity)
		).apply(instance, PartialVanillaProperties::new));

		public PartialVanillaProperties merge(PartialVanillaProperties templateProps) {
			return new PartialVanillaProperties(
					or(this.maxStackSize, templateProps.maxStackSize),
					or(this.maxDamage, templateProps.maxDamage),
					or(this.craftingRemainingItem, templateProps.craftingRemainingItem),
					or(this.food, templateProps.food),
					or(this.rarity, templateProps.rarity));
		}
	}
}

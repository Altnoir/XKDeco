package snownee.kiwi.customization.placement;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.kiwi.Kiwi;
import snownee.kiwi.customization.block.KBlockSettings;
import snownee.kiwi.customization.block.loader.KBlockDefinition;
import snownee.kiwi.customization.block.loader.KBlockTemplate;
import snownee.kiwi.customization.duck.KBlockProperties;
import snownee.kiwi.customization.item.MultipleBlockItem;
import snownee.kiwi.customization.util.BlockPredicateHelper;
import snownee.kiwi.customization.util.KHolder;
import snownee.kiwi.customization.util.codec.CustomizationCodecs;
import snownee.kiwi.loader.Platform;
import snownee.kiwi.util.Util;

public record PlaceChoices(
		List<PlaceTarget> target,
		List<Alter> alter,
		List<Limit> limit,
		List<Interests> interests,
		boolean skippable) {
	public static final BiMap<String, BlockFaceType> BLOCK_FACE_TYPES = HashBiMap.create();

	static {
		BLOCK_FACE_TYPES.put("any", BlockFaceType.ANY);
		BLOCK_FACE_TYPES.put("horizontal", BlockFaceType.HORIZONTAL);
		BLOCK_FACE_TYPES.put("vertical", BlockFaceType.VERTICAL);
		BLOCK_FACE_TYPES.put("clicked_face", (context, direction) -> context.getClickedFace() == direction.getOpposite());
		for (Direction direction : Util.DIRECTIONS) {
			BLOCK_FACE_TYPES.put(direction.getSerializedName(), (context, dir) -> dir == direction);
		}
	}

	public static final Codec<PlaceChoices> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			CustomizationCodecs.compactList(PlaceTarget.CODEC).fieldOf("target").forGetter(PlaceChoices::target),
			CustomizationCodecs.strictOptionalField(CustomizationCodecs.compactList(Alter.CODEC), "alter", List.of())
					.forGetter(PlaceChoices::alter),
			CustomizationCodecs.strictOptionalField(CustomizationCodecs.compactList(Limit.CODEC), "limit", List.of())
					.forGetter(PlaceChoices::limit),
			CustomizationCodecs.strictOptionalField(CustomizationCodecs.compactList(Interests.CODEC), "interests", List.of())
					.forGetter(PlaceChoices::interests),
			Codec.BOOL.optionalFieldOf("skippable", true).forGetter(PlaceChoices::skippable)
	).apply(instance, PlaceChoices::new));

	public record Preparation(
			Map<ResourceLocation, PlaceChoices> choices,
			Map<KBlockTemplate, KHolder<PlaceChoices>> byTemplate,
			Map<ResourceLocation, KHolder<PlaceChoices>> byBlock) {
		public static Preparation of(
				Supplier<Map<ResourceLocation, PlaceChoices>> choicesSupplier,
				Map<ResourceLocation, KBlockTemplate> templates) {
			Map<ResourceLocation, PlaceChoices> choices = Platform.isDataGen() ? Map.of() : choicesSupplier.get();
			Map<KBlockTemplate, KHolder<PlaceChoices>> byTemplate = Maps.newHashMap();
			Map<ResourceLocation, KHolder<PlaceChoices>> byBlock = Maps.newHashMap();
			for (var entry : choices.entrySet()) {
				KHolder<PlaceChoices> holder = new KHolder<>(entry.getKey(), entry.getValue());
				for (PlaceTarget target : holder.value().target) {
					switch (target.type()) {
						case TEMPLATE -> {
							KBlockTemplate template = templates.get(target.id());
							if (template == null) {
								Kiwi.LOGGER.error("Template {} not found for place choices {}", target.id(), holder);
								continue;
							}
							KHolder<PlaceChoices> oldChoices = byTemplate.put(template, holder);
							if (oldChoices != null) {
								Kiwi.LOGGER.error("Duplicate place choices for template {}: {} and {}", template, oldChoices, holder);
							}
						}
						case BLOCK -> {
							KHolder<PlaceChoices> oldChoices = byBlock.put(target.id(), holder);
							if (oldChoices != null) {
								Kiwi.LOGGER.error("Duplicate place choices for block {}: {} and {}", target.id(), oldChoices, holder);
							}
						}
					}
				}
			}
			return new Preparation(choices, byTemplate, byBlock);
		}

		public boolean attachChoicesA(Block block, KBlockDefinition definition) {
			KHolder<PlaceChoices> choices = byTemplate.get(definition.template().template());
			setTo(block, choices);
			return choices != null;
		}

		public int attachChoicesB() {
			AtomicInteger counter = new AtomicInteger();
			byBlock.forEach((blockId, choices) -> {
				Block block = BuiltInRegistries.BLOCK.get(blockId);
				if (block == Blocks.AIR) {
					Kiwi.LOGGER.error("Block %s not found for place choices %s".formatted(blockId, choices));
					return;
				}
				setTo(block, choices);
				counter.incrementAndGet();
			});
			return counter.get();
		}
	}

	public static void setTo(Block block, @Nullable KHolder<PlaceChoices> holder) {
		KBlockSettings settings = KBlockSettings.of(block);
		if (settings == null && holder != null) {
			((KBlockProperties) block.properties).kiwi$setSettings(settings = KBlockSettings.empty());
		}
		if (settings != null) {
			settings.placeChoices = holder == null ? null : holder.value();
		}
	}

	public int test(BlockState baseState, BlockState targetState) {
		for (Limit limit : this.limit) {
			if (!limit.test(baseState, targetState)) {
				return Integer.MIN_VALUE;
			}
		}
		int interest = 0;
		for (Interests provider : this.interests) {
			if (provider.when().smartTest(baseState, targetState)) {
				interest += provider.bonus;
			}
		}
		return interest;
	}

	public record Limit(String type, List<ParsedProtoTag> tags) {
		public static final Codec<Limit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("type").forGetter(Limit::type),
				CustomizationCodecs.compactList(ParsedProtoTag.CODEC).fieldOf("tags").forGetter(Limit::tags)
		).apply(instance, Limit::new));

		public boolean test(BlockState baseState, BlockState targetState) {
			for (ParsedProtoTag tag : tags) {
				ParsedProtoTag resolvedTag = tag.resolve(baseState, Rotation.NONE);
				//noinspection SwitchStatementWithTooFewBranches
				boolean pass = switch (this.type) {
					case "has_tags" -> {
						for (Direction direction : Util.DIRECTIONS) {
							Collection<PlaceSlot> slots = PlaceSlot.find(targetState, direction);
							for (PlaceSlot slot : slots) {
								if (slot.hasTag(resolvedTag)) {
									yield true;
								}
							}
						}
						yield false;
					}
					default -> false;
				};
				if (!pass) {
					return false;
				}
			}
			return true;
		}
	}

	public record Interests(StatePropertiesPredicate when, int bonus) {
		public static final Codec<Interests> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				StatePropertiesPredicate.CODEC.fieldOf("when").forGetter(Interests::when),
				Codec.INT.fieldOf("bonus").forGetter(Interests::bonus)
		).apply(instance, Interests::new));
	}

	//TODO check if `use` exists when attaching choices
	public record Alter(List<AlterCondition> when, String use) {
		public static final Codec<Alter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ExtraCodecs.nonEmptyList(CustomizationCodecs.compactList(AlterCondition.CODEC)).fieldOf("when").forGetter(Alter::when),
				Codec.STRING.fieldOf("use").forGetter(Alter::use)
		).apply(instance, Alter::new));

		@Nullable
		public BlockState alter(BlockItem blockItem, BlockPlaceContext context) {
			if (!(blockItem instanceof MultipleBlockItem multipleBlockItem)) {
				return null;
			}
			for (AlterCondition condition : when) {
				if (condition.test(context)) {
					Block block = multipleBlockItem.getBlock(use);
					Preconditions.checkNotNull(block, "Block %s not found in %s", use, multipleBlockItem);
					Preconditions.checkState(
							block != blockItem.getBlock(),
							"Block %s is the same as the original block, dead loop detected", block);
					BlockState blockState = block.getStateForPlacement(context);
					if (blockState == null) {
						return null;
					}
					KBlockSettings settings = KBlockSettings.of(block);
					if (settings != null) {
						blockState = settings.getStateForPlacement(blockState, context);
					}
					return blockState;
				}
			}
			return null;
		}
	}

	public record AlterCondition(String target, BlockFaceType faces, BlockPredicate block, List<ParsedProtoTag> tags) {
		public static final Codec<AlterCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				CustomizationCodecs.strictOptionalField(Codec.STRING, "target", "neighbor").forGetter(AlterCondition::target),
				CustomizationCodecs.strictOptionalField(CustomizationCodecs.simpleByNameCodec(BLOCK_FACE_TYPES), "faces", BlockFaceType.ANY)
						.forGetter(AlterCondition::faces),
				CustomizationCodecs.strictOptionalField(CustomizationCodecs.BLOCK_PREDICATE, "block", BlockPredicate.ANY)
						.forGetter(AlterCondition::block),
				CustomizationCodecs.strictOptionalField(CustomizationCodecs.compactList(ParsedProtoTag.CODEC), "tags", List.of())
						.forGetter(AlterCondition::tags)
		).apply(instance, AlterCondition::new));

		public boolean test(BlockPlaceContext context) {
			List<Direction> directions = switch (target) {
				case "clicked_face" -> List.of(context.getClickedFace().getOpposite());
				case "neighbor" -> Util.DIRECTIONS;
				default -> throw new IllegalStateException("Unexpected value: " + target);
			};
			BlockPos pos = context.getClickedPos();
			BlockPos.MutableBlockPos mutable = pos.mutable();
			directions:
			for (Direction direction : directions) {
				if (!faces.test(context, direction)) {
					continue;
				}
				BlockState neighbor = context.getLevel().getBlockState(mutable.setWithOffset(pos, direction));
				if (!BlockPredicateHelper.fastMatch(block, neighbor)) {
					continue;
				}
				for (ParsedProtoTag tag : tags) {
					ParsedProtoTag resolvedTag = tag.resolve(neighbor, Rotation.NONE);
					if (PlaceSlot.find(neighbor, direction.getOpposite()).stream().noneMatch(slot -> slot.hasTag(resolvedTag))) {
						continue directions;
					}
				}
				return true;
			}
			return false;
		}
	}

	public interface BlockFaceType extends BiPredicate<UseOnContext, Direction> {
		BlockFaceType ANY = (context, direction) -> true;
		BlockFaceType HORIZONTAL = (context, direction) -> direction.getAxis().isHorizontal();
		BlockFaceType VERTICAL = (context, direction) -> direction.getAxis().isVertical();
	}
}

package org.teacon.xkdeco.block.command;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExportCreativeTabsCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands
				.literal("exportCreativeTabs")
				.requires(source -> source.hasPermission(2))
				.then(Commands.argument("signPos", BlockPosArgument.blockPos())
						.executes(ctx -> exportCreativeTabs(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "signPos")))));
	}

	private static int exportCreativeTabs(CommandSourceStack source, BlockPos pos) {
		DataResult<Pair<ResourceLocation, Direction>> result = checkIsValidSign(source, pos);
		if (result.error().isPresent()) {
			source.sendFailure(Component.literal(result.error().get().message()));
			return 0;
		}
		Direction direction = result.result().orElseThrow().getSecond();
		ServerLevel level = source.getLevel();
		List<BaseContainerBlockEntity> containers = findContainerSequence(level, pos, direction);
		Map<String, Collection<String>> data = Maps.newLinkedHashMap();
		try {
			LinkedHashSet<String> items = collectItems(source, containers);
			if (!items.isEmpty()) {
				data.put(result.result().orElseThrow().getFirst().toString(), items);
			}
			for (Direction leftOrRight : List.of(Rotation.CLOCKWISE_90.rotate(direction), Rotation.COUNTERCLOCKWISE_90.rotate(direction))) {
				BlockPos.MutableBlockPos mutablePos = pos.mutable().move(leftOrRight);
				int failed = 0;
				while (failed < 5) {
					mutablePos.move(leftOrRight);
					DataResult<Pair<ResourceLocation, Direction>> result2 = checkIsValidSign(source, mutablePos);
					if (result2.error().isPresent()) {
						failed++;
						continue;
					}
					Pair<ResourceLocation, Direction> pair = result2.result().orElseThrow();
					if (direction != pair.getSecond()) {
						failed++;
						continue;
					}
					String tabId = pair.getFirst().toString();
					if (data.containsKey(tabId)) {
						failed = 0;
						continue;
					}
					containers = findContainerSequence(level, mutablePos, direction);
					items = collectItems(source, containers);
					if (!items.isEmpty()) {
						data.put(tabId, items);
					}
					failed = 0;
				}
			}
		} catch (RuntimeException e) {
			source.sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("exported_creative_tabs.json"))) {
			new Gson().toJson(data, writer);
		} catch (Exception e) {
			source.sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Creative tabs exported"), false);
		return 1;
	}

	private static List<BaseContainerBlockEntity> findContainerSequence(ServerLevel level, BlockPos pos, Direction direction) {
		BlockPos.MutableBlockPos mutablePos = pos.mutable();
		List<BaseContainerBlockEntity> containers = Lists.newArrayList();
		while (true) {
			mutablePos.move(direction);
			if (level.getBlockEntity(mutablePos) instanceof BaseContainerBlockEntity container) {
				containers.add(container);
			} else {
				break;
			}
		}
		return containers;
	}

	private static LinkedHashSet<String> collectItems(CommandSourceStack source, List<BaseContainerBlockEntity> containers) {
		ServerLevel level = source.getLevel();
		LinkedHashSet<String> items = Sets.newLinkedHashSet();
		for (BaseContainerBlockEntity container : containers) {
			level.setBlockAndUpdate(container.getBlockPos().below(), Blocks.YELLOW_WOOL.defaultBlockState());
		}
		for (BaseContainerBlockEntity container : containers) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				if (!items.add(item)) {
					for (BaseContainerBlockEntity container2 : containers) {
						if (container2.hasAnyMatching($ -> ItemStack.isSameItemSameTags($, stack))) {
							level.setBlockAndUpdate(container2.getBlockPos().below(), Blocks.RED_WOOL.defaultBlockState());
						}
					}
					throw new IllegalStateException("Duplicate item: %s (%s)".formatted(stack.getHoverName().getString(), item));
				}
			}
			level.setBlockAndUpdate(container.getBlockPos().below(), Blocks.LIME_WOOL.defaultBlockState());
		}
		return items;
	}

	private static DataResult<Pair<ResourceLocation, Direction>> checkIsValidSign(CommandSourceStack source, BlockPos pos) {
		ServerLevel level = source.getLevel();
		BlockState blockState = level.getBlockState(pos);
		if (!(blockState.getBlock() instanceof SignBlock block)) {
			return DataResult.error(() -> "Target block is not a sign");
		}
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity blockEntity)) {
			return DataResult.error(() -> "Target block is not a sign");
		}
		String signText = String.join("", Arrays.stream(blockEntity.getFrontText().getMessages(false))
				.map(Component::getString)
				.toList());
		if (signText.isBlank()) {
			return DataResult.error(() -> "The sign is empty");
		}
		ResourceLocation tabId = ResourceLocation.tryParse(signText);
		if (tabId == null) {
			return DataResult.error(() -> "The sign text is not a valid resource location");
		}
		Direction direction = Direction.fromYRot(block.getYRotationDegrees(blockState)).getOpposite();
		return DataResult.success(Pair.of(tabId, direction));
	}

}

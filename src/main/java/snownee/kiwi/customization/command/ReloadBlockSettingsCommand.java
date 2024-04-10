package snownee.kiwi.customization.command;

import java.util.Set;

import snownee.kiwi.customization.block.loader.KBlockDefinition;
import snownee.kiwi.customization.block.KBlockSettings;
import org.teacon.xkdeco.util.CommonProxy;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import snownee.kiwi.Kiwi;

public class ReloadBlockSettingsCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands
				.literal("reloadBlockSettings")
				.requires(source -> source.hasPermission(2))
				.executes(ctx -> reloadSlots(ctx.getSource())));
	}

	private static int reloadSlots(CommandSourceStack source) {
		Stopwatch stopwatch = Stopwatch.createStarted();
		CommonProxy.BlockFundamentals fundamentals = CommonProxy.BlockFundamentals.reload(CommonProxy.collectKiwiPacks(), false);
		long parseTime = stopwatch.elapsed().toMillis();
		stopwatch.reset().start();
		Set<Block> set = Sets.newHashSet();
		BuiltInRegistries.BLOCK.holders().forEach(holder -> {
			KBlockDefinition definition = fundamentals.blocks().get(holder.key().location());
			if (definition == null || !set.add(holder.value())) {
				return;
			}
			KBlockSettings.Builder builder = definition.createSettings(holder.key().location(), fundamentals.shapes());
			holder.value().properties = builder.get();
		});
		Blocks.rebuildCache();
		ReloadSlotsCommand.reloadSlots(fundamentals);
		long attachTime = stopwatch.elapsed().toMillis();
		Kiwi.LOGGER.info("Parse time %dms + Attach time %dms = %dms".formatted(parseTime, attachTime, parseTime + attachTime));
		source.sendSuccess(() -> Component.literal("%d Block settings reloaded".formatted(set.size())), false);
		return 1;
	}

}

package snownee.kiwi.customization.builder;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import snownee.kiwi.customization.CustomizationClient;
import snownee.kiwi.customization.block.family.BlockFamilies;
import snownee.kiwi.customization.block.family.BlockFamily;
import snownee.kiwi.customization.util.KHolder;
import snownee.kiwi.customization.network.CApplyBuilderRulePacket;

public class BuildersButton {
	private static final BuilderModePreview PREVIEW_RENDERER = new BuilderModePreview();

	public static BuilderModePreview getPreviewRenderer() {
		return PREVIEW_RENDERER;
	}

	private static boolean builderMode;

	public static boolean isBuilderModeOn() {
		return builderMode;
	}

	public static boolean onLongPress() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isSpectator()) { //TODO automatically disable builder mode
			return false;
		}
		builderMode = !builderMode;
		RandomSource random = RandomSource.create();
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
				SoundEvents.EXPERIENCE_ORB_PICKUP,
				(random.nextFloat() - random.nextFloat()) * 0.35F + 0.9F));
		return true;
	}

	public static boolean onDoublePress() {
		return false;
	}

	public static boolean onShortPress() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		Screen screen = mc.screen;
		if (screen instanceof ConvertScreen) {
			screen.onClose();
			return true;
		}
		if (screen instanceof AbstractContainerScreen<?> containerScreen && containerScreen.getMenu().getCarried().isEmpty()) {
			Slot slot = CustomizationClient.getSlotUnderMouse(containerScreen);
			if (slot == null || !slot.hasItem() || !slot.allowModification(mc.player)) {
				return false;
			}
			if (screen instanceof CreativeModeInventoryScreen && slot.container != mc.player.getInventory()) {
				return false;
			}
			List<KHolder<BlockFamily>> families = BlockFamilies.findQuickSwitch(slot.getItem().getItem());
			if (families.isEmpty()) {
				return false;
			}
			CustomizationClient.pushScreen(mc, new ConvertScreen(screen, slot, slot.index, families));
			return true;
		}
		if (screen != null) {
			return false;
		}
		List<KHolder<BlockFamily>> families = BlockFamilies.findQuickSwitch(mc.player.getMainHandItem().getItem());
		if (!families.isEmpty()) {
			mc.setScreen(new ConvertScreen(null, null, mc.player.getInventory().selected, families));
			return true;
		}
		families = BlockFamilies.findQuickSwitch(mc.player.getOffhandItem().getItem());
		if (!families.isEmpty()) {
			mc.setScreen(new ConvertScreen(null, null, Inventory.SLOT_OFFHAND, families));
			return true;
		}
		return false;
	}

	public static boolean startDestroyBlock(BlockPos pos, Direction face) {
		LocalPlayer player = ensureBuilderMode();
		if (player == null) {
			return false;
		}
		BlockState blockState = player.level().getBlockState(pos);
		return true;
	}

	public static boolean performUseItemOn(InteractionHand hand, BlockHitResult hitResult) {
		LocalPlayer player = ensureBuilderMode();
		if (player == null) {
			return false;
		}
		if (hand == InteractionHand.OFF_HAND) {
			return false;
		}
		BuilderModePreview preview = getPreviewRenderer();
		KHolder<BuilderRule> rule = preview.rule;
		BlockPos pos = preview.pos;
		List<BlockPos> positions = preview.positions;
		if (rule == null || positions.isEmpty() || !hitResult.getBlockPos().equals(pos)) {
			return true;
		}
		CApplyBuilderRulePacket.send(new UseOnContext(player, hand, hitResult), rule, positions);
		return true;
	}

	private static LocalPlayer ensureBuilderMode() {
		if (!isBuilderModeOn()) {
			return null;
		}
		return Minecraft.getInstance().player;
	}

	public static void renderDebugText(List<String> left, List<String> right) {
		if (!isBuilderModeOn() || Minecraft.getInstance().options.renderDebug) {
			return;
		}
		left.add("Builder Mode is on, long press %s to toggle".formatted(CustomizationClient.buildersButtonKey.getTranslatedKeyMessage()
				.getString()));
	}

	public static boolean cancelRenderHighlight() {
		return !getPreviewRenderer().positions.isEmpty();
	}
}

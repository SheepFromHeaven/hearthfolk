package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/**
 * Client-only "pick a block in the world" mode used by the structure marker GUI's work-area
 * center button: closes the GUI, shows a crosshair-anchored prompt and the structure's range
 * gizmo, and captures the next right-click *release* (not press - a held-down click would
 * otherwise re-fire every tick) on a block within that range to send back to the server as the
 * new work area center. Clicks outside the range are rejected with feedback and the mode stays
 * active; opening any other screen (e.g. pressing Esc) cancels it.
 *
 * Kept separate from the GUI/network classes so this file's bytecode never references
 * Minecraft/Screen directly outside of what's already dedicated-server-safe; only referenced
 * from client-only registration code (see MineNow's `Dist.CLIENT` guard).
 */
public class WorldPickMode {
    private static BlockPos markerPos;
    private static String structureName;
    private static int rangeRadius;
    private static boolean forcedRangeVisible;

    private WorldPickMode() {}

    public static void activate(BlockPos markerPos, String structureName, int rangeRadius) {
        WorldPickMode.markerPos = markerPos;
        WorldPickMode.structureName = structureName;
        WorldPickMode.rangeRadius = rangeRadius;
        if (!RangeTracker.isShowingRange(markerPos)) {
            RangeTracker.toggleRange(markerPos);
            forcedRangeVisible = true;
        }
    }

    public static boolean isActive() {
        return markerPos != null;
    }

    private static void deactivate() {
        if (forcedRangeVisible) {
            RangeTracker.toggleRange(markerPos);
            forcedRangeVisible = false;
        }
        markerPos = null;
        structureName = null;
    }

    /**
     * Only the horizontal reach is constrained; height is intentionally unbounded since a marker
     * placed high up (e.g. in a building's roof) should still be able to center on ground level
     * far below, or vice versa.
     */
    private static boolean isWithinRange(BlockPos pos) {
        int dx = pos.getX() - markerPos.getX();
        int dz = pos.getZ() - markerPos.getZ();
        return dx * dx + dz * dz <= rangeRadius * rangeRadius;
    }

    private static void onRightClickReleased() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.literal("You need to be looking at a block to set the work area center.")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        var pos = blockHit.getBlockPos();
        if (!isWithinRange(pos)) {
            player.displayClientMessage(Component.literal(
                    "That block is outside " + structureName + "'s range - pick a block within the shown area.")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        ModNetwork.sendSetWorkAreaCenter(markerPos, pos);
        deactivate();
    }

    public static void register() {
        InputEvent.MouseButton.Pre.BUS.addListener(event -> {
            if (!isActive() || event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) {
                return false;
            }
            if (event.getAction() == InputConstants.RELEASE) {
                onRightClickReleased();
            }
            // Suppress vanilla's normal use-item/attack handling for the right button entirely
            // while picking, on both press and release, so a held click can't sneak through.
            return true;
        });

        ScreenEvent.Opening.BUS.addListener(event -> {
            if (isActive()) {
                deactivate();
            }
            return false;
        });

        AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().add(
                ForgeLayeredDraw.VANILLA_ROOT,
                Identifier.fromNamespaceAndPath("minenow", "world_pick_overlay"),
                (guiGraphics, deltaTracker) -> {
                    if (!isActive()) {
                        return;
                    }
                    var mc = Minecraft.getInstance();
                    var text = Component.literal("Right-click a block within range to set the work area center for "
                            + structureName + " (Esc to cancel)");
                    int width = mc.getWindow().getGuiScaledWidth();
                    int height = mc.getWindow().getGuiScaledHeight();
                    guiGraphics.drawCenteredString(mc.font, text, width / 2, height / 2 + 20, 0xFFFFFFFF);
                }
        ));
    }
}

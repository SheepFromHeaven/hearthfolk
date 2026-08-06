package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.dominion.DominionPathNode;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Places and removes {@link DominionPathNode} street-routing hints (invisible, no physical block).
 *
 * <ul>
 *   <li><b>Right-click</b> on empty space in a dominion: places a node with the wand's active impact.
 *   <li><b>Right-click</b> on an existing node: removes it.
 *   <li><b>Shift+right-click</b> anywhere: cycles the wand's active impact (LOW → MEDIUM → HIGH → LOW)
 *       without placing or removing anything.
 * </ul>
 *
 * The active impact is stored in the wand's custom data and survives inventory save/load.
 * Nodes are only visible while the wand is held (see {@code PathNodeGizmoRenderer}).
 */
public class PathNodeWandItem extends Item {
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final String TAG_IMPACT = "impact";

    public PathNodeWandItem(Properties properties) {
        super(properties);
    }

    // -------------------------------------------------------------------------
    // Impact stored on the ItemStack
    // -------------------------------------------------------------------------

    public static DominionPathNode.Impact getImpact(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return DominionPathNode.Impact.MEDIUM;
        try {
            return DominionPathNode.Impact.valueOf(data.copyTag().getStringOr(TAG_IMPACT, ""));
        } catch (IllegalArgumentException e) {
            return DominionPathNode.Impact.MEDIUM;
        }
    }

    private static void setImpact(ItemStack stack, DominionPathNode.Impact impact) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_IMPACT, impact.name()));
    }

    private static DominionPathNode.Impact cycleImpact(ItemStack stack) {
        DominionPathNode.Impact next = getImpact(stack).next();
        setImpact(stack, next);
        return next;
    }

    // -------------------------------------------------------------------------
    // Interaction
    // -------------------------------------------------------------------------

    /** Shift+right-click in air: cycle impact mode. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var stack = player.getItemInHand(hand);
        var impact = cycleImpact(stack);
        player.displayClientMessage(
                Component.literal("Path node mode: " + impactLabel(impact)), true);
        return InteractionResult.SUCCESS;
    }

    /** Right-click on a block: shift = cycle mode; no-shift = place or remove. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;

        if (player.isShiftKeyDown()) {
            var impact = cycleImpact(context.getItemInHand());
            player.displayClientMessage(
                    Component.literal("Path node mode: " + impactLabel(impact)), true);
            return InteractionResult.SUCCESS;
        }

        var pos = context.getClickedPos().relative(context.getClickedFace());
        var dominion = DominionSavedData.get(level).findDominionContaining(pos);
        if (dominion == null) {
            player.displayClientMessage(Component.literal("No dominion here."), true);
            return InteractionResult.FAIL;
        }

        var existing = dominion.getPathNodeAt(pos);
        if (existing != null) {
            dominion.removePathNode(existing.getId());
            player.displayClientMessage(Component.literal("Removed path node."), true);
        } else {
            var impact = getImpact(context.getItemInHand());
            dominion.addPathNode(new DominionPathNode(pos, DominionPathNode.Kind.STREET, impact));
            player.displayClientMessage(
                    Component.literal("Placed " + impactLabel(impact) + " path node."), true);
        }
        DominionSavedData.get(level).setDirty();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND || !(entity instanceof ServerPlayer player)) return;
        if (level.getGameTime() % SYNC_INTERVAL_TICKS != 0) return;
        var dominion = DominionSavedData.get(level).findDominionContaining(player.blockPosition());
        var nodes = dominion == null ? java.util.List.<DominionPathNode>of() : dominion.getPathNodes().stream().toList();
        ModNetwork.sendPathNodeSync(player, nodes);
    }

    private static String impactLabel(DominionPathNode.Impact impact) {
        return switch (impact) {
            case LOW    -> "low (green)";
            case MEDIUM -> "medium (yellow)";
            case HIGH   -> "high (red)";
        };
    }
}

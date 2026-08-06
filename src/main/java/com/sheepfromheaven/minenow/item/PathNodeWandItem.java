package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.dominion.DominionPathNode;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import javax.annotation.Nullable;

/**
 * Toggles a {@link DominionPathNode} (an invisible street-routing hint, no physical block) at the
 * clicked position: places one if none exists there yet, removes it if one already does. Only
 * works within a dominion the player belongs to. Nodes are only visible (see client-side
 * {@code PathNodeGizmoRenderer}) while this wand is the held item, so it periodically pushes the
 * nearby dominion's nodes to the holder while selected.
 */
public class PathNodeWandItem extends Item {
    private static final int SYNC_INTERVAL_TICKS = 20;

    public PathNodeWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND || !(entity instanceof ServerPlayer player)) return;
        if (level.getGameTime() % SYNC_INTERVAL_TICKS != 0) return;
        var dominion = DominionSavedData.get(level).findDominionContaining(player.blockPosition());
        var positions = dominion == null
                ? java.util.List.<net.minecraft.core.BlockPos>of()
                : dominion.getPathNodes().stream().map(DominionPathNode::getPos).toList();
        ModNetwork.sendPathNodeSync(player, positions);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
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
            dominion.addPathNode(new DominionPathNode(pos, DominionPathNode.Kind.STREET));
            player.displayClientMessage(Component.literal("Placed path node."), true);
        }
        DominionSavedData.get(level).setDirty();
        return InteractionResult.SUCCESS;
    }
}

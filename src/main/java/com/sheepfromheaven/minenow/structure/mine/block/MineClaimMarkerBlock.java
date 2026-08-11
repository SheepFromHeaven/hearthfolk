package com.sheepfromheaven.minenow.structure.mine.block;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.structure.mine.MineClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Placed anywhere to claim its column as extra mining territory for the nearest MINE structure -
 * unlike {@link com.sheepfromheaven.minenow.structure.field.block.FieldMarkerBlock}, there's no
 * flood fill here: the claim is just this block's own position, auto-assigned to the nearest MINE
 * in the same dominion, if any.
 */
public class MineClaimMarkerBlock extends Block {
    public MineClaimMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof Player player)) {
            return;
        }
        var data = DominionSavedData.get(serverLevel);
        var dominion = data.findDominionContaining(pos);
        if (dominion == null) {
            player.displayClientMessage(Component.literal("No dominion nearby! Place a Town Banner first."), false);
            level.destroyBlock(pos, true);
            return;
        }
        if (!dominion.getOwnerUuid().equals(player.getUUID())) {
            player.displayClientMessage(Component.literal("This is not your dominion!"), false);
            level.destroyBlock(pos, true);
            return;
        }
        if (dominion.getMineClaimAt(pos) != null) {
            player.displayClientMessage(Component.literal("This spot is already claimed by another mine claim!"), false);
            level.destroyBlock(pos, true);
            return;
        }

        var claim = new MineClaim(pos);
        var mine = dominion.findNearestStructureOfType(StructureType.MINE, pos, s -> true).orElse(null);
        if (mine != null) {
            claim.setAssignedStructureId(mine.getId());
        }
        dominion.addMineClaim(claim);
        data.setDirty();

        var message = mine != null
                ? "Mine claim registered, assigned to " + mine.getName() + "."
                : "Mine claim registered. No Mine nearby to assign it to yet.";
        player.displayClientMessage(Component.literal(message), false);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            var data = DominionSavedData.get(serverLevel);
            for (var dominion : data.getAllDominions()) {
                var claim = dominion.getMineClaimAt(pos);
                if (claim != null) {
                    dominion.removeMineClaim(claim.getId());
                    data.setDirty();
                    player.displayClientMessage(Component.literal("Mine claim removed."), false);
                    break;
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}

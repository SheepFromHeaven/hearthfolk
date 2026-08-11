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
        // A claim attaches to a Mine whose territory covers this spot. The territory can reach beyond the
        // dominion's own bounds, so the claim itself need not be inside the dominion - only the Mine is.
        var match = data.findStructureWithTerritoryContaining(pos, StructureType.MINE, player.getUUID());
        if (match == null) {
            player.displayClientMessage(Component.literal("Place this within one of your Mines' territory."), false);
            level.destroyBlock(pos, true);
            return;
        }
        var dominion = match.dominion();
        var mine = match.structure();
        if (dominion.getMineClaimAt(pos) != null) {
            player.displayClientMessage(Component.literal("This spot is already claimed by another mine claim!"), false);
            level.destroyBlock(pos, true);
            return;
        }

        var claim = new MineClaim(pos);
        claim.setAssignedStructureId(mine.getId());
        dominion.addMineClaim(claim);
        data.setDirty();
        player.displayClientMessage(Component.literal("Mine claim registered, assigned to " + mine.getName() + "."), false);
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

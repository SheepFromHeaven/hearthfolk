package com.sheepfromheaven.minenow.structure.field.block;

import com.sheepfromheaven.minenow.core.Config;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.structure.field.CropKind;
import com.sheepfromheaven.minenow.structure.field.Field;
import com.sheepfromheaven.minenow.structure.field.FieldAreaDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Placed on farmland to claim it as a field for a farmer to tend. On placement, flood-fills the
 * contiguous farmland it's standing on (see {@link FieldAreaDetector}) and auto-assigns the new
 * field to the nearest FARM structure in the same dominion, if any - a farm can have any number of
 * fields assigned to it this way. Right-click cycles which crop the allowance-list toggle points at;
 * shift-right-click toggles that crop on/off for this field.
 */
public class FieldMarkerBlock extends Block {
    public FieldMarkerBlock(Properties properties) {
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

        var below = pos.below();
        var result = FieldAreaDetector.detect(level, below, Config.maxRoomSize);
        switch (result) {
            case FieldAreaDetector.Result.NoFarmland ignored -> {
                player.displayClientMessage(Component.literal("Place the field marker on farmland!"), false);
                level.destroyBlock(pos, true);
            }
            case FieldAreaDetector.Result.TooLarge(var blocks) -> {
                player.displayClientMessage(Component.literal(
                        "Field too large! Reached " + blocks.size() + " tiles (max " + Config.maxRoomSize + ")."), false);
                level.destroyBlock(pos, true);
            }
            case FieldAreaDetector.Result.Success(var blocks) -> {
                var existing = dominion.getFieldContaining(below);
                if (existing != null) {
                    player.displayClientMessage(Component.literal("This farmland is already claimed by another field!"), false);
                    level.destroyBlock(pos, true);
                    return;
                }

                var field = new Field(pos, blocks);
                var farm = dominion.findNearestStructureOfType(StructureType.FARM, pos, s -> true).orElse(null);
                if (farm != null) {
                    field.setAssignedStructureId(farm.getId());
                }
                dominion.addField(field);
                data.setDirty();

                var message = farm != null
                        ? "Field registered with " + blocks.size() + " farmland tiles, assigned to " + farm.getName() + "."
                        : "Field registered with " + blocks.size() + " farmland tiles. No Farm nearby to assign it to yet.";
                player.displayClientMessage(Component.literal(message), false);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        var dominion = DominionSavedData.get(serverLevel).findDominionContaining(pos);
        var field = dominion != null ? dominion.getFieldAt(pos) : null;
        if (field == null) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            boolean nowAllowed = field.toggleCrop(field.getHighlightedCrop());
            player.displayClientMessage(Component.literal(
                    field.getHighlightedCrop().getDisplayName() + " is now " + (nowAllowed ? "allowed" : "blocked") + " on this field."), true);
        } else {
            var kind = field.cycleHighlightedCrop();
            player.displayClientMessage(Component.literal(
                    "Now editing: " + kind.getDisplayName() + " (" + (field.allows(kind) ? "allowed" : "blocked") + ") - shift-click to toggle"), true);
        }
        DominionSavedData.get(serverLevel).setDirty();
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            var data = DominionSavedData.get(serverLevel);
            for (var dominion : data.getAllDominions()) {
                var field = dominion.getFieldAt(pos);
                if (field != null) {
                    dominion.removeField(field.getId());
                    data.setDirty();
                    player.displayClientMessage(Component.literal("Field removed."), false);
                    break;
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}

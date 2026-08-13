package com.sheepfromheaven.minenow.structure.field.block;

import com.mojang.serialization.MapCodec;
import com.sheepfromheaven.minenow.core.Config;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.structure.field.Field;
import com.sheepfromheaven.minenow.structure.field.FieldAreaDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Placed anywhere on solid ground to claim a field for a farmer to tend. On placement, flood-fills
 * the ground it's standing on like water spreading over terrain (see {@link FieldAreaDetector} - it
 * no longer needs to already be farmland, and doesn't need walls either; it just stops wherever a
 * solid block actually blocks it) and auto-assigns the new field to the nearest FARM structure in the
 * same dominion, if any - a farm can have any number of fields assigned to it this way. Right-click
 * opens the marker's seed-template inventory, whose contents decide which crops a farmer may plant
 * here (see {@link FieldMarkerBlockEntity}).
 */
public class FieldMarkerBlock extends BaseEntityBlock {
    public FieldMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(FieldMarkerBlock::new);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FieldMarkerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof Player player)) {
            return;
        }
        var data = DominionSavedData.get(serverLevel);
        // The field attaches to a Farm whose territory covers this spot; the territory may reach beyond the
        // dominion's own bounds, so the field need not be inside the dominion - only the Farm is.
        var match = data.findStructureWithTerritoryContaining(pos, StructureType.FARM, player.getUUID());
        if (match == null) {
            player.displayClientMessage(Component.literal("Place this within one of your Farms' territory."), false);
            level.destroyBlock(pos, true);
            return;
        }
        var dominion = match.dominion();
        var farm = match.structure();

        var below = pos.below();
        var result = FieldAreaDetector.detect(level, below, pos, Config.maxRoomSize);
        switch (result) {
            case FieldAreaDetector.Result.NotEnclosed ignored -> {
                player.displayClientMessage(Component.literal("Place the field marker on solid ground!"), false);
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
                    player.displayClientMessage(Component.literal("This ground is already claimed by another field!"), false);
                    level.destroyBlock(pos, true);
                    return;
                }

                var field = new Field(pos, blocks);
                field.setAssignedStructureId(farm.getId());
                dominion.addField(field);
                data.setDirty();

                player.displayClientMessage(Component.literal(
                        "Field registered with " + blocks.size() + " tiles, assigned to " + farm.getName() + "."), false);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FieldMarkerBlockEntity be) {
            serverPlayer.openMenu(be);
        }
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

package com.sheepfromheaven.minenow.block;

import com.mojang.serialization.MapCodec;
import com.sheepfromheaven.minenow.Config;
import com.sheepfromheaven.minenow.client.StructureMarkerScreen;
import com.sheepfromheaven.minenow.dominion.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Set;

public class StructureMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<StructureMarkerBlock> CODEC = simpleCodec(StructureMarkerBlock::new);

    public StructureMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructureMarkerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player) {
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

            var result = RoomDetector.detect(level, pos, Config.maxRoomSize);
            switch (result) {
                case RoomDetector.Result.TooLarge(var visited) -> {
                    player.displayClientMessage(Component.literal("Room too large! Reached " + visited.size() + " blocks (max " + Config.maxRoomSize + "). Showing fill area in red."), false);
                    spawnDebugParticles(serverLevel, visited, 255, 50, 50);
                    level.destroyBlock(pos, true);
                }
                case RoomDetector.Result.NotEnclosed(var visited) -> {
                    player.displayClientMessage(Component.literal("Room not enclosed! Fill leaked through " + visited.size() + " blocks. Showing fill area in red."), false);
                    spawnDebugParticles(serverLevel, visited, 255, 50, 50);
                    level.destroyBlock(pos, true);
                }
                case RoomDetector.Result.Success(var blocks) -> {
                    var existing = dominion.getStructureContaining(pos);
                    if (existing != null) {
                        player.displayClientMessage(Component.literal("This space is already part of " + existing.getName() + "!"), false);
                        level.destroyBlock(pos, true);
                        return;
                    }

                    var structure = new DominionStructure("New Structure", StructureType.HOUSE, pos, blocks);
                    dominion.addStructure(structure);
                    data.setDirty();

                    var statuses = structure.checkRequirements(level);
                    if (level.getBlockEntity(pos) instanceof StructureMarkerBlockEntity be) {
                        be.setStructureData(structure.getName(), structure.getType(), dominion.getId(), structure.getId());
                        be.setRequirementStatuses(statuses);
                    }

                    spawnDebugParticles(serverLevel, blocks, 50, 255, 50);
                    player.displayClientMessage(Component.literal(
                            "Structure registered! " + blocks.size() + " blocks detected. Right-click to configure."), false);
                }
            }
        }
    }

    private static void spawnDebugParticles(ServerLevel level, Set<BlockPos> blocks, int red, int green, int blue) {
        int color = (red << 16) | (green << 8) | blue;
        var particle = new DustParticleOptions(color, 1.5f);
        for (var bp : blocks) {
            level.sendParticles(particle, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            var data = DominionSavedData.get(serverLevel);
            var dominion = data.findDominionContaining(pos);
            if (dominion != null) {
                var structure = dominion.getStructureAt(pos);
                if (structure != null && level.getBlockEntity(pos) instanceof StructureMarkerBlockEntity be) {
                    be.setRequirementStatuses(structure.checkRequirements(level));
                }
            }
        } else if (level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof StructureMarkerBlockEntity be && be.getDominionId() != null) {
                Minecraft.getInstance().setScreen(new StructureMarkerScreen(
                        be.getStructureName(), be.getStructureType(), pos, be.getRequirementStatuses()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof StructureMarkerBlockEntity be && be.getDominionId() != null) {
                var data = DominionSavedData.get(serverLevel);
                for (var dominion : data.getAllDominions()) {
                    var structure = dominion.getStructureAt(pos);
                    if (structure != null) {
                        dominion.removeStructure(structure.getId());
                        data.setDirty();
                        player.displayClientMessage(Component.literal(structure.getName() + " has been removed."), false);
                        break;
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}

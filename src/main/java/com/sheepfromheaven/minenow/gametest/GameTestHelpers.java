package com.sheepfromheaven.minenow.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

public final class GameTestHelpers {
    public static final long WORK_HOURS = 3000L;

    private GameTestHelpers() {}

    /** Stone floor plus barrier walls, so villagers can't stroll into neighbouring tests. */
    public static void fence(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int width = (int) (bounds.maxX - bounds.minX);
        int height = (int) (bounds.maxY - bounds.minY);
        int length = (int) (bounds.maxZ - bounds.minZ);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        for (int y = 1; y < height; y++) {
            for (int x = 0; x < width; x++) {
                helper.setBlock(x, y, 0, Blocks.BARRIER);
                helper.setBlock(x, y, length - 1, Blocks.BARRIER);
            }
            for (int z = 0; z < length; z++) {
                helper.setBlock(0, y, z, Blocks.BARRIER);
                helper.setBlock(width - 1, y, z, Blocks.BARRIER);
            }
        }
    }

    public static void placeBed(GameTestHelper helper, BlockPos head) {
        BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        helper.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
        helper.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
    }

    public static Villager spawnWithHome(GameTestHelper helper, BlockPos spawnAt, BlockPos bedHead) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, spawnAt);
        giveHome(helper, villager, bedHead);
        return villager;
    }

    public static void giveHome(GameTestHelper helper, Villager villager, BlockPos bedHead) {
        placeBed(helper, bedHead);
        ServerLevel level = helper.getLevel();
        BlockPos absBed = helper.absolutePos(bedHead);
        level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(absBed), absBed, 1)
                .orElseThrow(() -> helper.assertionException(bedHead, "test.error.expected_block", "bed POI"));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), absBed));
    }

    public static Villager spawnEmployed(GameTestHelper helper, BlockPos spawnAt, BlockPos jobSite,
                                         ResourceKey<VillagerProfession> profession, ResourceKey<PoiType> poi) {
        ServerLevel level = helper.getLevel();
        BlockPos absJobSite = helper.absolutePos(jobSite);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, spawnAt);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), profession));
        level.getPoiManager().take(type -> type.is(poi), (type, pos) -> pos.equals(absJobSite), absJobSite, 1)
                .orElseThrow(() -> helper.assertionException(jobSite, "test.error.expected_block", "job site POI"));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), absJobSite));
        villager.refreshBrain(level);
        return villager;
    }

    /** A 7x7 oak fence pen whose west side is at {@code x} (z 9 to 15), with an oak fence gate in the middle of that side. */
    public static BlockPos gatedPen(GameTestHelper helper, int x) {
        for (int i = 0; i < 7; i++) {
            helper.setBlock(x + i, 1, 9, Blocks.OAK_FENCE);
            helper.setBlock(x + i, 1, 15, Blocks.OAK_FENCE);
            helper.setBlock(x, 1, 9 + i, Blocks.OAK_FENCE);
            helper.setBlock(x + 6, 1, 9 + i, Blocks.OAK_FENCE);
        }
        BlockPos gate = new BlockPos(x, 1, 12);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.EAST));
        return gate;
    }
}

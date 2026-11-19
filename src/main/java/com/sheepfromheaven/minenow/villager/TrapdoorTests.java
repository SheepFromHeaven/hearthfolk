package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** Villagers can't open trapdoors, but a one-block corridor with one in it must not leave them stuck in front of it. */
public final class TrapdoorTests {
    private static final BlockPos START = new BlockPos(3, 1, 6);
    private static final BlockPos GOAL = new BlockPos(15, 1, 6);
    private static final BlockPos TRAPDOOR = new BlockPos(9, 1, 6);

    private TrapdoorTests() {}

    private static BlockState trapdoor(boolean open, Half half) {
        return Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, open).setValue(TrapDoorBlock.HALF, half).setValue(TrapDoorBlock.FACING, Direction.EAST);
    }

    /** A corridor between stone walls, {@code width} blocks wide (1 or 3), with the given trapdoor in the middle of it, and a villager at one end sent to the other. */
    private static Villager corridor(GameTestHelper helper, int width, BlockState inTheWay) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        int low = width == 1 ? 6 : 5;
        int high = width == 1 ? 6 : 7;
        for (int x = 1; x <= 17; x++) {
            for (int z = low - 1; z <= high + 1; z++) {
                boolean wall = z == low - 1 || z == high + 1 || x == 1 || x == 17;
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) if (wall) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        helper.setBlock(TRAPDOOR, inTheWay);
        return helper.spawn(EntityTypes.VILLAGER, START);
    }

    private static void reaches(GameTestHelper helper, Villager villager) {
        BlockPos goal = helper.absolutePos(GOAL);
        helper.onEachTick(() -> villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(goal, 0.5F, 1)));
        helper.succeedWhen(() -> helper.assertTrue(villager.blockPosition().distManhattan(goal) <= 2,
                "The villager should get to the end, is at " + helper.relativePos(villager.blockPosition())));
    }

    private static void noWayThrough(GameTestHelper helper, Villager villager) {
        helper.runAfterDelay(10, () -> {
            var path = villager.getNavigation().createPath(helper.absolutePos(GOAL), 0);
            helper.assertTrue(path == null || !path.canReach(), "A trapdoor that fills a one-block corridor leaves no way through, so it plans none");
            helper.succeed();
        });
    }

    public static void aVillagerWalksOverAClosedTrapdoorLyingInItsWay(GameTestHelper helper) {
        reaches(helper, corridor(helper, 1, trapdoor(false, Half.BOTTOM)));
    }

    public static void aVillagerWalksAroundAnOpenTrapdoorInAWideCorridor(GameTestHelper helper) {
        reaches(helper, corridor(helper, 3, trapdoor(true, Half.BOTTOM)));
    }

    public static void aVillagerWalksAroundATopHalfTrapdoorInAWideCorridor(GameTestHelper helper) {
        reaches(helper, corridor(helper, 3, trapdoor(false, Half.TOP)));
    }

    public static void anOpenTrapdoorFillingAOneBlockCorridorIsNotPlannedThrough(GameTestHelper helper) {
        noWayThrough(helper, corridor(helper, 1, trapdoor(true, Half.BOTTOM)));
    }

    public static void aTopHalfTrapdoorFillingAOneBlockCorridorIsNotPlannedThrough(GameTestHelper helper) {
        noWayThrough(helper, corridor(helper, 1, trapdoor(false, Half.TOP)));
    }
}

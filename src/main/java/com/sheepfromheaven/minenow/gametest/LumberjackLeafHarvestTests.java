package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.ModEntities;
import com.sheepfromheaven.minenow.entity.job.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class LumberjackLeafHarvestTests {

    /**
     * Reproduces the "lumberjack leaves the canopy standing (or, worse, ignores its drops) after
     * felling the trunk" gap: once the trunk is fully chopped, every canopy leaf block should be
     * harvested directly (its loot table rolled and the result added to the inhabitant's inventory)
     * rather than left for vanilla decay - so the lumberjack doesn't have to loiter around waiting on
     * decay ticks, and doesn't turn into a general item-entity vacuum that would also pick up anything
     * a player drops on the ground nearby.
     */
    public static void fellingTrunkHarvestsCanopyLeavesDirectly(GameTestHelper helper) {
        helper.makeFloor();

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var soilRelPos = new BlockPos(6, 0, 6);
        var logRelPos = new BlockPos(6, 1, 6);
        var leafRelPos = new BlockPos(7, 1, 6);

        helper.setBlock(barrelRelPos, Blocks.BARREL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(soilRelPos.offset(dx, 0, dz), Blocks.GRASS_BLOCK);
            }
        }
        helper.setBlock(logRelPos, Blocks.OAK_LOG);
        helper.setBlock(leafRelPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var logPos = helper.absolutePos(logRelPos);
        var leafPos = helper.absolutePos(leafRelPos);

        if (level.getBlockEntity(barrelPos) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.IRON_AXE));
        }

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        // Drive the job through GOTO_HUT -> FIND_AXE -> GOTO_TREE -> CHOPPING, teleporting past
        // each travel leg's arrival check instead of waiting on real navigation ticks.
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        workGoal.tick();
        inhabitant.snapTo(logPos.getX() + 0.5, logPos.getY(), logPos.getZ() + 0.5);
        workGoal.tick();

        helper.assertTrue("Chopping wood".equals(workGoal.getStatusDescription()),
                "Job should have reached the chopping phase before the trunk is felled");

        // The single-log trunk is felled after CHOP_TICKS ticks of chopping.
        for (int i = 0; i < 40; i++) {
            workGoal.tick();
        }

        var leafGone = !level.getBlockState(leafPos).is(BlockTags.LEAVES);
        var itemEntitiesNearby = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(leafPos).inflate(3));

        helper.succeedWhen(() -> {
            helper.assertTrue(leafGone, "The canopy leaf should be harvested (cleared) once the trunk is felled");
            helper.assertTrue(itemEntitiesNearby.isEmpty(),
                    "Leaf drops should go straight into the inhabitant's inventory, not spawn as loose item entities");
        });
    }

    /**
     * Reproduces the "lumberjack can't reach a tree boxed in by its own low-hanging canopy" bug: when
     * every tile around the trunk's base is blocked by decayable (non-persistent) leaves, the old
     * standable-neighbor scan rejected all 8 of them and gave up on the tree entirely (see
     * {@code LumberjackJob#unreachableTreeBases}). The lumberjack should instead clear a decayable leaf
     * out of its way to get a foothold, exactly like a player would just walk through leaves.
     */
    public static void lumberjackClearsDecayableLeavesBlockingTrunkAccess(GameTestHelper helper) {
        helper.makeFloor();

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var logRelPos = new BlockPos(6, 1, 6);

        helper.setBlock(barrelRelPos, Blocks.BARREL);
        // makeFloor() only fills relative y = -1, one below the trunk's own neighbor tiles (y = 1) - lay
        // solid ground under the trunk and all 8 neighbors so they're standable once their leaves are cleared.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(logRelPos.below().offset(dx, 0, dz), Blocks.GRASS_BLOCK);
            }
        }
        helper.setBlock(logRelPos, Blocks.OAK_LOG);
        // Box the trunk in on all 8 surrounding tiles (and their headroom) with decayable leaves - the
        // scan checks cardinal and diagonal neighbors alike, so every one of them needs to be blocked to
        // actually exercise the "clear a leaf to get a foothold" path instead of just picking a free tile.
        var leafState = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                var leafPos = logRelPos.offset(dx, 0, dz);
                helper.setBlock(leafPos, leafState);
                helper.setBlock(leafPos.above(), leafState);
            }
        }

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var logPos = helper.absolutePos(logRelPos);

        if (level.getBlockEntity(barrelPos) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.IRON_AXE));
        }

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        workGoal.tick();
        // Teleport adjacent to the boxed-in trunk rather than driving real navigation - findStandableNeighbor
        // (called from tickGotoTree, still in the same tick chain) is what's under test, not pathfinding.
        inhabitant.snapTo(logPos.getX() + 0.5, logPos.getY(), logPos.getZ() + 0.5);
        workGoal.tick();

        helper.succeedWhen(() -> helper.assertTrue("Chopping wood".equals(workGoal.getStatusDescription()),
                "Lumberjack should clear a decayable leaf to get a foothold and start chopping the boxed-in trunk"));
    }
}

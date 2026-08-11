package com.sheepfromheaven.minenow.jobs.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.jobs.WorkGoal;
import com.sheepfromheaven.minenow.jobs.miner.MinerJob;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class MinerJobTests {

    /**
     * Drives a Miner through a full cycle: walk to the claim, dig a stairway down, tunnel through
     * solid rock (breaking a raw iron ore block embedded in the path along the way), then return and
     * deposit everything - ore and stone spoil alike - into the Mine's barrel.
     */
    public static void minerDigsStairwayTunnelsThroughOreAndDepositsAtTheMine(GameTestHelper helper) {
        var setup = setUpMineWithRockAndOre(helper);

        driveUntil(setup.workGoal(), setup.inhabitant(), setup.markerPos(),
                () -> helper.getLevel().getBlockEntity(setup.barrelPos()) instanceof Container barrel
                        && containsOreDrop(barrel));

        helper.succeedWhen(() -> {
            var barrel = (Container) helper.getLevel().getBlockEntity(setup.barrelPos());
            helper.assertTrue(barrel != null, "Barrel should still be present");
            boolean hasStone = false;
            boolean hasOreDrop = false;
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                var stack = barrel.getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.is(net.minecraft.world.item.Items.COBBLESTONE) || stack.is(net.minecraft.world.item.Items.STONE)) hasStone = true;
                if (stack.is(net.minecraft.world.item.Items.RAW_IRON) || stack.is(net.minecraft.world.item.Items.IRON_ORE)) hasOreDrop = true;
            }
            helper.assertTrue(hasStone, "Tunnel spoil (stone/cobblestone) should be deposited");
            helper.assertTrue(hasOreDrop, "The iron ore encountered in the tunnel should be mined and deposited");
        });
    }

    /**
     * When the per-structure support-beam toggle is enabled, the miner should place cosmetic wood
     * blocks (planks/fences) somewhere along the path it dug, without ever overwriting the walkable
     * tunnel/stairway blocks it just carved.
     */
    public static void minerPlacesSupportBeamsWhenToggleIsEnabled(GameTestHelper helper) {
        var setup = setUpMineWithRockAndOre(helper);
        setup.structure().setPlaceSupportBeams(true);

        driveTicks(setup.workGoal(), setup.inhabitant(), setup.markerPos(), 400);

        helper.succeedWhen(() -> {
            boolean foundSupport = false;
            var level = helper.getLevel();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = 0; dz <= 20; dz++) {
                    for (int dy = -MinerJob.MAX_DEPTH - 2; dy <= 2; dy++) {
                        var pos = setup.markerPos().offset(dx, dy, dz);
                        var state = level.getBlockState(pos);
                        if (state.is(Blocks.OAK_PLANKS) || state.is(Blocks.OAK_FENCE)) {
                            foundSupport = true;
                        }
                    }
                }
            }
            helper.assertTrue(foundSupport, "A cosmetic support beam block should have been placed along the dug path");
        });
    }

    /**
     * A miner holding only a wooden pickaxe should mine coal ore (wooden-tier harvestable) it walks
     * past, but must leave iron ore (needs a stone-tier-or-better pickaxe) untouched - in place, no
     * drop collected - rather than silently breaking it bare-tool-style.
     */
    public static void minerWithWoodenPickaxeLeavesIronOreUntouched(GameTestHelper helper) {
        var setup = setUpMineWithRockCoalAndIron(helper, new ItemStack(Items.WOODEN_PICKAXE));

        driveUntil(setup.workGoal(), setup.inhabitant(), setup.markerPos(),
                () -> helper.getLevel().getBlockEntity(setup.barrelPos()) instanceof Container barrel
                        && containsCoalDrop(barrel));

        helper.succeedWhen(() -> {
            var barrel = (Container) helper.getLevel().getBlockEntity(setup.barrelPos());
            helper.assertTrue(barrel != null, "Barrel should still be present");
            boolean hasCoalDrop = containsCoalDrop(barrel);
            boolean hasIronDrop = false;
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                var stack = barrel.getItem(i);
                if (!stack.isEmpty() && (stack.is(Items.RAW_IRON) || stack.is(Items.IRON_ORE))) hasIronDrop = true;
            }
            helper.assertTrue(hasCoalDrop, "Coal ore should be mined with a wooden pickaxe");
            helper.assertFalse(hasIronDrop, "Iron ore should NOT be minable with only a wooden pickaxe");
            helper.assertTrue(helper.getLevel().getBlockState(setup.oreOrIronPos()).is(Blocks.IRON_ORE),
                    "Iron ore block should still be in place, untouched");
        });
    }

    /** A miner holding a stone pickaxe (iron-tier-or-better also works) can mine both coal and iron ore. */
    public static void minerWithStonePickaxeMinesCoalAndIronOre(GameTestHelper helper) {
        var setup = setUpMineWithRockCoalAndIron(helper, new ItemStack(Items.STONE_PICKAXE));

        driveUntil(setup.workGoal(), setup.inhabitant(), setup.markerPos(),
                () -> helper.getLevel().getBlockEntity(setup.barrelPos()) instanceof Container barrel
                        && containsCoalDrop(barrel) && containsOreDrop(barrel));

        helper.succeedWhen(() -> {
            var barrel = (Container) helper.getLevel().getBlockEntity(setup.barrelPos());
            helper.assertTrue(barrel != null, "Barrel should still be present");
            helper.assertTrue(containsCoalDrop(barrel), "Coal ore should be mined with a stone pickaxe");
            helper.assertTrue(containsOreDrop(barrel), "Iron ore should be mined with a stone pickaxe");
        });
    }

    /**
     * A mine claim marker placed well outside the structure's own work-area radius should extend
     * the mineable area to (also) surround it, rather than the miner staying confined to a circle
     * around the Mine's own marker.
     */
    public static void minerTunnelsAroundAPlacedMineClaimOutsideTheStructuresOwnWorkArea(GameTestHelper helper) {
        var markerRelPos = new BlockPos(2, MinerJob.MAX_DEPTH + 3, 2);
        var barrelRelPos = markerRelPos.offset(-1, 0, -1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        // The claim sits far outside the structure's own (radius 6) work area - nothing to mine there
        // at all, so if the miner ever fell back to the marker's own circle it would find no rock and
        // never produce ore.
        var claimRelPos = markerRelPos.offset(8, 0, 0);
        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 20; z++) {
                for (int y = -MinerJob.MAX_DEPTH - 2; y <= 1; y++) {
                    helper.setBlock(claimRelPos.offset(x, y, z), Blocks.STONE);
                }
            }
        }
        var oreRelPos = claimRelPos.offset(1, -MinerJob.MAX_DEPTH, 3);
        helper.setBlock(oreRelPos, Blocks.IRON_ORE);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var claimPos = helper.absolutePos(claimRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos, barrelPos));
        structure.setWorkAreaCenter(markerPos);
        structure.setWorkAreaRadius(6);
        dominion.addStructure(structure);

        var claim = new com.sheepfromheaven.minenow.structure.mine.MineClaim(claimPos);
        claim.setAssignedStructureId(structure.getId());
        dominion.addMineClaim(claim);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        inhabitant.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);

        // GameTest mobs don't actually walk along their path just from calling tick() - mirror the
        // other jobs' tests (e.g. FarmerJobTests) by snapping straight to the claim once the job has
        // picked it, rather than relying on real pathfinding movement to cross the gap to it.
        workGoal.tick();
        inhabitant.snapTo(claimPos.getX() + 0.5, claimPos.getY(), claimPos.getZ() + 0.5);

        for (int i = 0; i < 3000 && !carriesOreDrop(inhabitant); i++) {
            workGoal.tick();
        }

        helper.succeedWhen(() ->
                helper.assertTrue(carriesOreDrop(inhabitant),
                        "Ore near the placed claim should have been mined and picked up"));
    }

    private static boolean carriesOreDrop(com.sheepfromheaven.minenow.inhabitant.InhabitantEntity inhabitant) {
        var inv = inhabitant.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.is(Items.RAW_IRON) || stack.is(Items.IRON_ORE)) return true;
        }
        return false;
    }

    private record MineSetup(com.sheepfromheaven.minenow.inhabitant.InhabitantEntity inhabitant, WorkGoal workGoal,
                              DominionStructure structure, BlockPos markerPos, BlockPos barrelPos, BlockPos oreOrIronPos) {}

    private static MineSetup setUpMineWithRockAndOre(GameTestHelper helper) {
        // Give the miner an iron pickaxe up front - this test is about tunneling/depositing mechanics,
        // not tool-tier gating (see minerWithWoodenPickaxeLeavesIronOreUntouched for that), so it needs
        // a tool sufficient to mine the iron ore embedded in its path.
        return setUpMine(helper, new ItemStack(Items.IRON_PICKAXE), false);
    }

    private static MineSetup setUpMineWithRockCoalAndIron(GameTestHelper helper, ItemStack heldPickaxe) {
        return setUpMine(helper, heldPickaxe, true);
    }

    private static MineSetup setUpMine(GameTestHelper helper, ItemStack heldPickaxe, boolean withCoalToo) {
        GameTestHelpers.makeFloor(helper);

        // The marker sits well above the local floor so the stairway has room to descend the full
        // MAX_DEPTH without falling outside the test structure's (finite) bounding box.
        var markerRelPos = new BlockPos(2, MinerJob.MAX_DEPTH + 3, 2);
        var barrelRelPos = markerRelPos.offset(-1, 0, -1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        // A generous solid-rock volume south of and below the marker, so the stairway (dug south+down)
        // and the tunnel that follows always have real stone to carve through instead of hitting void.
        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 20; z++) {
                for (int y = -MinerJob.MAX_DEPTH - 2; y <= 1; y++) {
                    helper.setBlock(markerRelPos.offset(x, y, z), Blocks.STONE);
                }
            }
        }
        // One ore block embedded in a tunnel wall, just past where the stairway bottoms out.
        var oreRelPos = markerRelPos.offset(1, -MinerJob.MAX_DEPTH, 3);
        helper.setBlock(oreRelPos, Blocks.IRON_ORE);
        if (withCoalToo) {
            // Placed several tunnel steps further along (not alongside the iron ore at z=3) - the
            // inhabitant's carry inventory is only 2 slots (cobblestone spoil + one ore), so mining two
            // different ore types in the very same step would silently drop the second one on the
            // floor; spacing them out lets a deposit trip happen in between, which is incidental to
            // what this test is actually verifying (tool-tier gating, not inventory capacity).
            var coalRelPos = markerRelPos.offset(-1, -MinerJob.MAX_DEPTH, 6);
            helper.setBlock(coalRelPos, Blocks.COAL_ORE);
        }

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var orePos = helper.absolutePos(oreRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos, barrelPos));
        structure.setWorkAreaCenter(markerPos);
        structure.setWorkAreaRadius(6);
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        if (heldPickaxe != null) {
            inhabitant.setItemSlot(EquipmentSlot.MAINHAND, heldPickaxe);
        }

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);

        return new MineSetup(inhabitant, workGoal, structure, markerPos, barrelPos, orePos);
    }

    private static boolean containsCoalDrop(Container barrel) {
        for (int i = 0; i < barrel.getContainerSize(); i++) {
            var stack = barrel.getItem(i);
            if (stack.is(Items.COAL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsOreDrop(Container barrel) {
        for (int i = 0; i < barrel.getContainerSize(); i++) {
            var stack = barrel.getItem(i);
            if (stack.is(net.minecraft.world.item.Items.RAW_IRON) || stack.is(net.minecraft.world.item.Items.IRON_ORE)) {
                return true;
            }
        }
        return false;
    }

    private static void driveUntil(WorkGoal workGoal, com.sheepfromheaven.minenow.inhabitant.InhabitantEntity inhabitant,
                                    BlockPos markerPos, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < 3000 && !done.getAsBoolean(); i++) {
            workGoal.tick();
        }
    }

    private static void driveTicks(WorkGoal workGoal, com.sheepfromheaven.minenow.inhabitant.InhabitantEntity inhabitant,
                                    BlockPos markerPos, int ticks) {
        for (int i = 0; i < ticks; i++) {
            workGoal.tick();
        }
    }
}

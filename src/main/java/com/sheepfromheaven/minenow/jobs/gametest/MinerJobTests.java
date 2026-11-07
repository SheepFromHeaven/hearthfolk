package com.sheepfromheaven.minenow.jobs.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.jobs.WorkGoal;
import com.sheepfromheaven.minenow.jobs.miner.MinerJob;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

        // Driven one real game tick at a time (instead of the other tests' synchronous drive
        // loops) so this specific test is watchable in-game - the whole point of it is seeing
        // the miner actually walk, dig, and mine over time rather than the result appearing instantly.
        helper.onEachTick(() -> setup.workGoal().tick());

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
     * The branches dug from the junction must never go back toward the stairway (see {@code
     * BRANCH_DIRECTIONS}, which excludes the stairway's approach direction entirely) - otherwise a
     * branch, or one of its perpendicular strip-mining ribs, would tunnel through the immediate vicinity
     * of the only way out, and could even carve away part of the exit path itself. A cell a couple of
     * blocks behind the hub (north, for this {@code MAX_DEPTH}'s spiral - see {@code
     * STAIRWAY_APPROACH_DIRECTION}) should stay solid rock even after the miner exhausts every branch
     * and rib it can dig. Reaching a genuinely reintroduced 4th (backward) branch the "natural" way -
     * walking all the way out to CLAIM_RADIUS on the first three - would take an impractically large
     * tick budget (ribs alone add dozens of steps per branch), so this is a sanity check within a
     * reasonable budget rather than an exhaustive one; the real guarantee is structural, since {@code
     * BRANCH_DIRECTIONS} is a fixed 3-element array that the backward direction is never part of.
     */
    public static void minerNeverBranchesBackTowardTheStairway(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var markerRelPos = new BlockPos(10, MinerJob.MAX_DEPTH + 3, 10);
        var barrelRelPos = markerRelPos.offset(-1, 0, -1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        // Deliberately minimal - just enough rock for the stairway and junction corridor. Once a branch
        // steps past this into open space it has to bridge the missing floor with carried spoil (see
        // ensureFloor); running out of that spoil abandons the branch and moves to the next one almost
        // immediately, instead of needing to walk all the way out to CLAIM_RADIUS (10 blocks) - which
        // would take an impractically large tick budget to actually reach a hypothetical 4th branch.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 6; z++) {
                for (int y = -MinerJob.MAX_DEPTH - 2; y <= 1; y++) {
                    helper.setBlock(markerRelPos.offset(x, y, z), Blocks.STONE);
                }
            }
        }

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);
        var claim = new com.sheepfromheaven.minenow.structure.mine.MineClaim(markerPos);
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

        for (int i = 0; i < 30000; i++) {
            workGoal.tick();
        }

        helper.succeedWhen(() -> {
            var hubPos = markerPos.offset(0, -MinerJob.MAX_DEPTH, 1);
            var behindHub = hubPos.relative(Direction.NORTH, 2);
            helper.assertTrue(level.getBlockState(behindHub).is(Blocks.STONE),
                    "A cell behind the hub (toward the stairway) should never be touched by any branch or rib");
        });
    }

    /**
     * From the far end of the junction corridor past the hub, the forward branch (the first of the
     * three branch directions - see {@code BRANCH_DIRECTIONS}, which always starts with the direction
     * continuing straight on from the stairway's last step) must dig perpendicular strip-mining ribs,
     * not just march straight ahead. An ore block placed 2 blocks east of the spine's centerline -
     * beyond the 1-block lateral reach of the ordinary ore-neighbor peek every spine step already does -
     * can only be found if a genuine rib excursion (which reaches {@code RIB_LENGTH} blocks out) walks
     * past it. Both the hub and the junction's far end are computed directly from the deterministic
     * spiral stairway math (4-direction cycle, net +1 south after {@code MAX_DEPTH}=5 steps, so the
     * approach/forward direction is south) so the ore can be placed precisely relative to them rather
     * than guessed.
     */
    public static void minerStripMinesRibsOffTheSouthBranch(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var markerRelPos = new BlockPos(6, MinerJob.MAX_DEPTH + 3, 6);
        var barrelRelPos = markerRelPos.offset(-1, 0, -1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        for (int x = -4; x <= 4; x++) {
            for (int z = 0; z <= 12; z++) {
                for (int y = -MinerJob.MAX_DEPTH - 2; y <= 1; y++) {
                    helper.setBlock(markerRelPos.offset(x, y, z), Blocks.STONE);
                }
            }
        }
        // hub = marker + (0, -MAX_DEPTH, +1) - the spiral stairway's four-direction cycle (S,W,N,E) nets
        // +1 south (and 0 east/west) after 5 steps. Branching starts 3 more steps south of that, at the
        // junction corridor's far end (marker offset (0, -MAX_DEPTH, 4)) - the branch's 4th spine step
        // from there (where the first rib pair kicks in, see STRIP_INTERVAL) sits at marker offset
        // (0, -MAX_DEPTH, 8). The rib's "left" (east) excursion reaches 2 blocks out on its second step.
        var ribOreRelPos = markerRelPos.offset(2, -MinerJob.MAX_DEPTH, 8);
        helper.setBlock(ribOreRelPos, Blocks.IRON_ORE);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);
        var claim = new com.sheepfromheaven.minenow.structure.mine.MineClaim(markerPos);
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

        driveUntil(workGoal, inhabitant, markerPos,
                () -> helper.getLevel().getBlockEntity(barrelPos) instanceof Container barrel
                        && containsOreDrop(barrel));

        helper.succeedWhen(() -> {
            var barrel = (Container) helper.getLevel().getBlockEntity(barrelPos);
            helper.assertTrue(barrel != null && containsOreDrop(barrel),
                    "The ore reachable only via a strip-mining rib (2 blocks off the spine) should have been mined");
        });
    }

    /**
     * A branch tunnel that opens into a pre-existing gap (a ravine/cave crossing its path) must bridge
     * the missing floor with spoil from its own inventory and keep mining beyond it, instead of walking
     * the inhabitant off the edge and getting the job stuck.
     */
    /**
     * After a deposit trip triggered by a full carry inventory, the miner must resume exactly where
     * digging left off - not restart the whole branch from the hub - otherwise it would spend every
     * single trip re-walking (and, before this was fixed, re-processing as if redigging) ground it had
     * already cleared. Tunnel-step count is a simple counter across the whole branch that's only ever
     * reset when a brand new hub is dug (never per-resume), so it climbing well past what a single
     * 2-slot-inventory trip could produce on its own proves progress survives across deposit trips.
     */
    public static void minerResumesDiggingWithoutRestartingTheBranch(GameTestHelper helper) {
        var setup = setUpMineWithRockAndOre(helper);

        boolean sawResumePhase = false;
        for (int i = 0; i < 4000 && !sawResumePhase; i++) {
            setup.workGoal().tick();
            sawResumePhase = "Heading back to the dig site".equals(setup.workGoal().getStatusDescription());
        }
        boolean finalSawResumePhase = sawResumePhase;

        helper.succeedWhen(() -> helper.assertTrue(finalSawResumePhase,
                "After a deposit trip triggered by a full carry inventory, the miner should head straight "
                        + "back to the dig site (the RESUME_DIGGING phase) instead of restarting the whole "
                        + "claim-approach/stairway sequence from scratch"));
    }

    public static void minerBridgesARavineInBranchTunnelInsteadOfGettingStuck(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var markerRelPos = new BlockPos(6, MinerJob.MAX_DEPTH + 3, 6);
        var barrelRelPos = markerRelPos.offset(-1, 0, -1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 10; z++) {
                for (int y = -MinerJob.MAX_DEPTH - 2; y <= 1; y++) {
                    helper.setBlock(markerRelPos.offset(x, y, z), Blocks.STONE);
                }
            }
        }
        // hub = marker offset (0, -MAX_DEPTH, +1); branching starts 3 more steps south of that, at the
        // junction corridor's far end (marker offset (0, -MAX_DEPTH, 4)). The forward branch's step 2
        // from there = marker offset (0, -MAX_DEPTH, 6). Carve that cell, its headroom, and - crucially -
        // the floor beneath it open as a pre-existing ravine, so the branch tunnel arrives at an edge
        // with nothing to stand on.
        var gapRelPos = markerRelPos.offset(0, -MinerJob.MAX_DEPTH, 6);
        helper.setBlock(gapRelPos, Blocks.AIR);
        helper.setBlock(gapRelPos.above(), Blocks.AIR);
        helper.setBlock(gapRelPos.below(), Blocks.AIR);
        helper.setBlock(gapRelPos.below(2), Blocks.AIR);
        // Ore just beyond the ravine, on the spine (step 4 from the junction's far end) - only reachable
        // if mining continues past the gap.
        var oreRelPos = markerRelPos.offset(1, -MinerJob.MAX_DEPTH, 8);
        helper.setBlock(oreRelPos, Blocks.IRON_ORE);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var gapFloorPos = helper.absolutePos(gapRelPos.below());

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);
        var claim = new com.sheepfromheaven.minenow.structure.mine.MineClaim(markerPos);
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

        driveUntil(workGoal, inhabitant, markerPos,
                () -> helper.getLevel().getBlockEntity(barrelPos) instanceof Container barrel
                        && containsOreDrop(barrel));

        helper.succeedWhen(() -> {
            helper.assertTrue(!level.getBlockState(gapFloorPos).getCollisionShape(level, gapFloorPos).isEmpty(),
                    "The ravine's missing floor should have been bridged with spoil");
            var barrel = (Container) helper.getLevel().getBlockEntity(barrelPos);
            helper.assertTrue(barrel != null && containsOreDrop(barrel),
                    "Mining should continue past the bridged ravine and reach the ore beyond it");
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
     * The spiral stairway must carve a genuine two-block-tall passage the whole way down, even through
     * plain dirt where no tool-tier gating is ever involved - not just at each resting cell, but at the
     * "doorway" where the path steps down a level. GameTest mobs never actually walk their path (see the
     * comment on {@link #minerTunnelsAroundAPlacedMineClaimOutsideTheStructuresOwnWorkArea}), so this
     * replays the exact same step geometry {@code tickDigStairway} uses instead of relying on real
     * pathfinding to notice a stair it can't physically descend.
     */
    public static void minerDigsAFullyWalkableStairwayThroughDirt(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var markerRelPos = new BlockPos(2, MinerJob.MAX_DEPTH + 3, 2);
        var barrelRelPos = markerRelPos.offset(-1, 0, -1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 4; z++) {
                for (int y = -MinerJob.MAX_DEPTH - 2; y <= 1; y++) {
                    helper.setBlock(markerRelPos.offset(x, y, z), Blocks.DIRT);
                }
            }
        }

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);
        var claim = new com.sheepfromheaven.minenow.structure.mine.MineClaim(markerPos);
        claim.setAssignedStructureId(structure.getId());
        dominion.addMineClaim(claim);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);

        for (int i = 0; i < 400; i++) {
            workGoal.tick();
        }

        helper.succeedWhen(() -> {
            var spiralDirections = new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
            var cursor = markerPos;
            for (int depth = 0; depth < MinerJob.MAX_DEPTH; depth++) {
                var dir = spiralDirections[depth % spiralDirections.length];
                var next = cursor.below().relative(dir);
                var newHead = next.above();
                var doorwayTop = newHead.above();
                helper.assertTrue(level.getBlockState(next).isAir(), "Stair step " + depth + " foot cell should be air");
                helper.assertTrue(level.getBlockState(newHead).isAir(), "Stair step " + depth + " head cell should be air");
                helper.assertTrue(level.getBlockState(doorwayTop).isAir(),
                        "Stair step " + depth + " doorway-top cell should be air - a two-block-tall mob "
                                + "can't descend a step whose ceiling is still hanging over the doorway");
                cursor = next;
            }
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
        return setUpMine(helper, new ItemStack(Items.IRON_PICKAXE), false, true);
    }

    /**
     * Mining must take real time based on the block's hardness and the tool's actual mining speed - not
     * an instant break regardless of pickaxe tier - and must cost the pickaxe durability, just like a
     * player mining the same block would.
     */
    public static void minerMinesWithRealTimingAndSpendsDurability(GameTestHelper helper) {
        var setup = setUpMine(helper, new ItemStack(Items.IRON_PICKAXE), false, true);

        // GOTO_CLAIM/FIND_PICKAXE/FIND_TORCHES each resolve in a single tick (the inhabitant starts
        // right on the marker, already holding a pickaxe, with no torches in storage), so mining on the
        // first stairway riser (a stone block, hardness 1.5) starts around tick 3 - an iron pickaxe
        // (speed 6, correct tool) needs ceil(1.5 * 30 / 6) = 8 ticks to break it, so it must still be
        // solid well before that.
        var riserPos = setup.markerPos().relative(Direction.SOUTH);
        for (int i = 0; i < 6; i++) {
            setup.workGoal().tick();
        }
        helper.assertTrue(helper.getLevel().getBlockState(riserPos).is(Blocks.STONE),
                "An iron pickaxe should not instantly break stone - it should still be mid-swing this early");

        for (int i = 0; i < 60; i++) {
            setup.workGoal().tick();
        }

        helper.succeedWhen(() -> {
            helper.assertFalse(helper.getLevel().getBlockState(riserPos).is(Blocks.STONE),
                    "The riser should be broken by now");
            helper.assertTrue(setup.inhabitant().getItemBySlot(EquipmentSlot.MAINHAND).getDamageValue() > 0,
                    "Mining stone should have cost the pickaxe durability, same as a player mining it");
        });
    }

    /**
     * With torches available in storage, the miner should fetch some into its off-hand and place them
     * along the branch tunnel at regular intervals, rather than leaving the whole thing dark.
     */
    public static void minerPlacesTorchesAlongTheBranchTunnel(GameTestHelper helper) {
        var setup = setUpMineWithRockAndOre(helper);
        if (helper.getLevel().getBlockEntity(setup.barrelPos()) instanceof Container container) {
            container.setItem(1, new ItemStack(Items.TORCH, 16));
        }

        // The carry inventory is only 2 slots, so a single trip's worth of spine steps rarely reaches a
        // full TORCH_INTERVAL before it fills up and heads back to deposit - drive several trips' worth
        // of ticks (the counter survives across trips, see MinerJob#tickDigStairway) rather than
        // stopping at the very first deposit. Each trip now also spends real ticks retracing its way
        // back down to the resume point (see MinerJob#tickResumeDigging), so this needs a larger budget
        // than a single dig-to-full-inventory cycle would.
        driveTicks(setup.workGoal(), setup.inhabitant(), setup.markerPos(), 10000);

        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            boolean foundTorch = false;
            for (int x = -3; x <= 3; x++) {
                for (int z = 0; z <= 20; z++) {
                    for (int y = -MinerJob.MAX_DEPTH - 2; y <= 2; y++) {
                        if (level.getBlockState(setup.markerPos().offset(x, y, z)).is(Blocks.TORCH)) {
                            foundTorch = true;
                        }
                    }
                }
            }
            helper.assertTrue(foundTorch, "A torch fetched from storage should have been placed along the dug tunnel");
        });
    }

    private static MineSetup setUpMineWithRockCoalAndIron(GameTestHelper helper, ItemStack heldPickaxe) {
        return setUpMine(helper, heldPickaxe, true, true);
    }

    /** A miner with no pickaxe at all should not carve through solid rock bare-handed. */
    public static void minerWithoutPickaxeLeavesStoneUntouched(GameTestHelper helper) {
        var setup = setUpMine(helper, null, false, true);

        driveTicks(setup.workGoal(), setup.inhabitant(), setup.markerPos(), 400);

        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            boolean stoneStillPresent = false;
            for (int x = -2; x <= 2; x++) {
                for (int z = 0; z <= 5; z++) {
                    for (int y = -MinerJob.MAX_DEPTH - 2; y <= 1; y++) {
                        if (level.getBlockState(setup.markerPos().offset(x, y, z)).is(Blocks.STONE)) {
                            stoneStillPresent = true;
                        }
                    }
                }
            }
            helper.assertTrue(stoneStillPresent, "Bare-handed miner should not have broken any stone");
        });
    }

    /**
     * A bare-handed miner reaching solid stone should stop and report that it needs a pickaxe, instead
     * of silently skipping the block while the tunnel/stairway state still advances past it - then,
     * once a suitable pickaxe shows up in storage, it should equip it and actually carve through.
     */
    public static void minerWithoutPickaxeReportsNeedsPickaxeThenMinesOnceOneArrives(GameTestHelper helper) {
        var setup = setUpMine(helper, null, false, true);

        for (int i = 0; i < 150; i++) {
            setup.workGoal().tick();
        }

        helper.assertTrue("Needs a better pickaxe".equals(setup.workGoal().getStatusDescription()),
                "Miner should report needing a pickaxe once blocked by solid stone, got: "
                        + setup.workGoal().getStatusDescription());
        helper.assertTrue(setup.workGoal().getMissingItemLabel() != null,
                "Missing item label should be set while blocked on a pickaxe-gated block");

        if (helper.getLevel().getBlockEntity(setup.barrelPos()) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        }

        driveUntil(setup.workGoal(), setup.inhabitant(), setup.markerPos(),
                () -> helper.getLevel().getBlockEntity(setup.barrelPos()) instanceof Container barrel
                        && containsOreDrop(barrel));

        helper.succeedWhen(() -> {
            helper.assertTrue(!"Needs a better pickaxe".equals(setup.workGoal().getStatusDescription()),
                    "Miner should no longer be blocked once it has fetched a suitable pickaxe");
            var barrel = (Container) helper.getLevel().getBlockEntity(setup.barrelPos());
            helper.assertTrue(barrel != null && containsOreDrop(barrel),
                    "Once equipped, the miner should mine and deposit the iron ore it was previously blocked on");
        });
    }

    /**
     * A player handing the blocked miner a pickaxe directly (setting its main-hand slot rather than
     * dropping the tool in the Mine's storage) must be noticed immediately - not just when a suitable
     * pickaxe happens to be fetched from a container - since that's the natural way a player watching
     * an inhabitant get stuck would try to help.
     */
    public static void minerHandedAPickaxeDirectlyResumesMiningImmediately(GameTestHelper helper) {
        var setup = setUpMine(helper, null, false, true);

        for (int i = 0; i < 150; i++) {
            setup.workGoal().tick();
        }
        helper.assertTrue("Needs a better pickaxe".equals(setup.workGoal().getStatusDescription()),
                "Miner should be blocked on solid stone before being handed a pickaxe, got: "
                        + setup.workGoal().getStatusDescription());

        setup.inhabitant().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        setup.workGoal().tick();

        helper.succeedWhen(() -> helper.assertTrue(
                !"Needs a better pickaxe".equals(setup.workGoal().getStatusDescription()),
                "Miner should resume mining on the very next tick once handed a pickaxe directly, got: "
                        + setup.workGoal().getStatusDescription()));
    }

    /**
     * Bedrock sitting directly in the stairway's very first dig step must survive even when the miner
     * holds a pickaxe good enough to mine everything else around it.
     */
    public static void minerNeverBreaksBedrockEvenWithAPickaxe(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var markerRelPos = new BlockPos(2, 4, 2);
        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 3; z++) {
                for (int y = 0; y <= 3; y++) {
                    helper.setBlock(markerRelPos.offset(x, -y, z), Blocks.STONE);
                }
            }
        }
        // The first stairway step (depthDug=0, direction SOUTH) targets exactly this cell.
        helper.setBlock(markerRelPos.below().relative(Direction.SOUTH), Blocks.BEDROCK);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(markerRelPos.offset(-1, 0, -1));
        helper.setBlock(markerRelPos.offset(-1, 0, -1), Blocks.BARREL);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);
        var claim = new com.sheepfromheaven.minenow.structure.mine.MineClaim(markerPos);
        claim.setAssignedStructureId(structure.getId());
        dominion.addMineClaim(claim);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        inhabitant.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);

        for (int i = 0; i < 200; i++) {
            workGoal.tick();
        }

        var bedrockPos = markerPos.below().relative(Direction.SOUTH);
        helper.succeedWhen(() -> helper.assertTrue(level.getBlockState(bedrockPos).is(Blocks.BEDROCK),
                "Bedrock should never be broken, even by a diamond pickaxe"));
    }

    /** A Mine with no claim placed in its territory has nothing to work - the miner should mine nothing. */
    public static void minerWithNoClaimDoesNotMine(GameTestHelper helper) {
        var setup = setUpMine(helper, new ItemStack(Items.IRON_PICKAXE), false, false);

        driveTicks(setup.workGoal(), setup.inhabitant(), setup.markerPos(), 400);

        helper.succeedWhen(() -> {
            var barrel = (Container) helper.getLevel().getBlockEntity(setup.barrelPos());
            helper.assertTrue(barrel != null, "Barrel should still be present");
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                helper.assertTrue(barrel.getItem(i).isEmpty(),
                        "A Mine with no claim placed should not mine or deposit anything");
            }
        });
    }

    private static MineSetup setUpMine(GameTestHelper helper, ItemStack heldPickaxe, boolean withCoalToo, boolean withClaim) {
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
        dominion.addStructure(structure);
        if (withClaim) {
            // The Mine needs a claim within its territory as a work condition; put one on the marker so the
            // miner works the same rock volume (dug south+down from here) these tests build around it.
            var claim = new com.sheepfromheaven.minenow.structure.mine.MineClaim(markerPos);
            claim.setAssignedStructureId(structure.getId());
            dominion.addMineClaim(claim);
        }

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

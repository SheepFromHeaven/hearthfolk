package com.sheepfromheaven.minenow.inhabitant.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class RestNeedTests {

    public static void restNeedDecaysWhileInhabitantIsAwake(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(1, 1, 1));

        helper.succeedWhen(() -> helper.assertTrue(
                inhabitant.getNeed(NeedType.REST) < 1.0f, "Rest need should decay while the inhabitant is awake"));
    }

    public static void restNeedRestoresWhileInhabitantIsSleeping(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var bedRelPos = new BlockPos(3, 1, 3);
        helper.setBlock(bedRelPos, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD));

        var level = helper.getLevel();
        var bedPos = helper.absolutePos(bedRelPos);
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
        var structure = new DominionStructure("Home", StructureType.HOUSE, bedPos, Set.of(bedPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(1, 1, 1));
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setHomeStructureId(structure.getId());
        inhabitant.setNeed(NeedType.REST, 0.3f);

        // Night window from GoHomeGoal: dayTime in [12542, 23460).
        level.setDayTime(13000L);

        helper.succeedWhen(() -> helper.assertTrue(
                inhabitant.isSleeping() && inhabitant.getNeed(NeedType.REST) > 0.3f,
                "Rest need should climb back up while the inhabitant sleeps"));
    }
}

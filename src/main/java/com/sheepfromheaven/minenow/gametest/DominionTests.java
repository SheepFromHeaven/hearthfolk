package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.block.ModBlocks;
import com.sheepfromheaven.minenow.block.TownBannerBlock;
import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class DominionTests {

    public static void disbandingDominionConvertsInhabitantsAndClearsData(GameTestHelper helper) {
        helper.makeFloor();

        var level = helper.getLevel();
        var bannerRelPos = new BlockPos(1, 1, 1);
        var bannerPos = helper.absolutePos(bannerRelPos);
        helper.setBlock(bannerRelPos, ModBlocks.TOWN_BANNER.get());

        var structureRelPos = new BlockPos(3, 1, 3);
        var structurePos = helper.absolutePos(structureRelPos);
        helper.setBlock(structureRelPos, Blocks.STONE);

        var data = DominionSavedData.get(level);
        var dominion = data.createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
        var dominionId = dominion.getId();
        var structure = new DominionStructure("Home", StructureType.HOUSE, structurePos, Set.of(structurePos));
        dominion.addStructure(structure);

        var inhabitantPos = new BlockPos(2, 1, 2);
        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), inhabitantPos);
        inhabitant.setDominionId(dominionId);
        dominion.addInhabitant(new DominionInhabitant(inhabitant.getUUID(), "Test Inhabitant"));

        var player = helper.makeMockPlayer(GameType.CREATIVE);
        var bannerBlock = (TownBannerBlock) level.getBlockState(bannerPos).getBlock();
        bannerBlock.playerWillDestroy(level, bannerPos, level.getBlockState(bannerPos), player);

        helper.succeedWhen(() -> {
            helper.assertTrue(inhabitant.isRemoved(), "Inhabitant entity should be discarded after disband");

            var villagerAbsPos = helper.absolutePos(inhabitantPos);
            boolean villagerSpawned = !level.getEntitiesOfClass(Villager.class, new AABB(villagerAbsPos).inflate(2.0))
                    .isEmpty();
            helper.assertTrue(villagerSpawned, "A vanilla villager should replace the inhabitant after disband");

            helper.assertTrue(level.getBlockState(structurePos).isAir(),
                    "Structure blocks should be destroyed after disband");

            helper.assertTrue(DominionSavedData.get(level).getDominionById(dominionId) == null,
                    "Dominion data should be deleted after disband");
        });
    }
}

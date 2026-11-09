package com.sheepfromheaven.minenow.fletcher;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class FletcherTests {
    private static final BlockPos TABLE = new BlockPos(5, 1, 12);

    private FletcherTests() {}

    public static void fletcherMakesArrowsFromFlintSticksAndFeathers(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.getInventory().addItem(new ItemStack(Items.FLINT, 2));
        fletcher.getInventory().addItem(new ItemStack(Items.STICK, 2));
        fletcher.getInventory().addItem(new ItemStack(Items.FEATHER, 2));

        helper.succeedWhen(() -> helper.assertTrue(fletcher.getInventory().countItem(Items.ARROW) == 8,
                "Each flint, stick and feather make four arrows, has " + fletcher.getInventory().countItem(Items.ARROW)));
    }

    public static void aJourneymanFletcherMakesBows(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.setVillagerData(fletcher.getVillagerData().withLevel(3));
        fletcher.getInventory().addItem(new ItemStack(Items.STICK, 3));
        fletcher.getInventory().addItem(new ItemStack(Items.STRING, 3));

        helper.succeedWhen(() -> helper.assertTrue(fletcher.getInventory().countItem(Items.BOW) == 1,
                "A journeyman strings a bow from three sticks and three string, has " + fletcher.getInventory().countItem(Items.BOW)));
    }

    public static void aNoviceFletcherMakesNoBows(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.getInventory().addItem(new ItemStack(Items.STICK, 3));
        fletcher.getInventory().addItem(new ItemStack(Items.STRING, 3));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(fletcher.getInventory().countItem(Items.BOW) == 0, "Bows unlock at journeyman");
            helper.succeed();
        });
    }

    public static void fletcherPractisesAtATargetAndCollectsItsArrows(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.setVillagerData(fletcher.getVillagerData().withLevel(5));
        fletcher.getInventory().addItem(new ItemStack(Items.BOW));
        fletcher.getInventory().addItem(new ItemStack(Items.ARROW, 8));
        BlockPos target = new BlockPos(17, 2, 12);
        helper.setBlock(target.below(), Blocks.STONE);
        helper.setBlock(target, Blocks.TARGET);
        boolean[] hit = {false};
        int[] fewest = {8};
        helper.onEachTick(() -> {
            if (helper.getBlockState(target).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER) > 0) hit[0] = true;
            fewest[0] = Math.min(fewest[0], fletcher.getInventory().countItem(Items.ARROW));
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0], "The fletcher shoots at the target, arrows " + fletcher.getInventory().countItem(Items.ARROW) + ", fewest " + fewest[0]
                    + ", at " + helper.relativePos(fletcher.blockPosition()).toShortString() + ", running " + fletcher.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList());
            helper.assertTrue(fewest[0] < 8, "...with its own arrows");
            helper.assertTrue(fletcher.getInventory().countItem(Items.ARROW) == 8, "...and collects them again, has " + fletcher.getInventory().countItem(Items.ARROW));
            helper.assertTrue(fletcher.getInventory().countItem(Items.BOW) == 1, "It keeps its bow");
        });
    }

    static Villager spawnFletcher(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(TABLE, Blocks.FLETCHING_TABLE);
        return GameTestHelpers.spawnEmployed(helper, TABLE.east(), TABLE, VillagerProfession.FLETCHER, PoiTypes.FLETCHER);
    }
}

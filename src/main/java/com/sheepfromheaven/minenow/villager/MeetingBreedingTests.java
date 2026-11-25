package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Willing villagers gathered at the bell pair up there, not only in the idle moments between work and the meeting. */
public final class MeetingBreedingTests {
    private MeetingBreedingTests() {}

    public static void twoWillingVillagersAtTheBellHaveAChild(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setTime(GameTestHelpers.LATE_AFTERNOON);
        BlockPos bell = new BlockPos(12, 1, 12);
        Villager a = GameTestHelpers.spawnWithHome(helper, new BlockPos(10, 1, 12), new BlockPos(4, 1, 4));
        Villager b = GameTestHelpers.spawnWithHome(helper, new BlockPos(14, 1, 12), new BlockPos(4, 1, 20));
        GameTestHelpers.placeBed(helper, new BlockPos(20, 1, 4));
        a.getInventory().addItem(new ItemStack(Items.POTATO, Villager.BREEDING_FOOD_THRESHOLD));
        b.getInventory().addItem(new ItemStack(Items.POTATO, Villager.BREEDING_FOOD_THRESHOLD));
        VillageTests.claimBell(helper, a, bell);
        VillageTests.claimBell(helper, b, bell);

        helper.succeedWhen(() -> {
            long children = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(40), Villager::isBaby).size();
            helper.assertTrue(children > 0, "Two willing villagers at the bell have a child, but none was born");
        });
    }
}

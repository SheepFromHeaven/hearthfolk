package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

public final class RegenerationTests {
    private RegenerationTests() {}

    public static void aWellFedVillagerHealsOverTime(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.setHealth(10);

        helper.succeedWhen(() -> helper.assertTrue(villager.getHealth() >= 12,
                "A villager that has eaten recently heals, health is " + villager.getHealth()));
    }

    public static void aHungryVillagerDoesNotHeal(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.setHealth(10);
        Meals.makeHungry(villager);

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(villager.getHealth() == 10, "A hungry villager with no food doesn't heal, health is " + villager.getHealth());
            helper.succeed();
        });
    }

    public static void withoutMealsAVillagerDoesNotHeal(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.setHealth(10);

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(villager.getHealth() == 10, "With meals off nothing heals, health is " + villager.getHealth());
            helper.succeed();
        });
    }

    public static void withoutHealingAWellFedVillagerDoesNotHeal(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.setHealth(10);

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(villager.getHealth() == 10, "With healing off a fed villager doesn't heal, health is " + villager.getHealth());
            helper.succeed();
        });
    }
}

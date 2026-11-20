package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

public final class WorkXpTests {
    private WorkXpTests() {}

    public static void workExperienceLevelsAVillagerUp(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER));
        helper.assertTrue(villager.getVillagerData().level() == 1, "A new villager starts at level 1");
        WorkXp.award(villager, 4);
        helper.assertTrue(villager.getVillagerXp() == 4 && villager.getVillagerData().level() == 1, "A little work gives experience but no level yet, xp " + villager.getVillagerXp());
        WorkXp.award(villager, 6);
        helper.assertTrue(villager.getVillagerData().level() == 2, "10 experience reaches level 2, it's " + villager.getVillagerData().level());
        helper.succeed();
    }

    public static void workExperienceHasADailyCap(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER));
        WorkXp.award(villager, 1000);
        helper.assertTrue(villager.getVillagerXp() == WorkXp.DAILY_CAP, "A day's work is capped at " + WorkXp.DAILY_CAP + ", xp " + villager.getVillagerXp());
        WorkXp.award(villager, 5);
        helper.assertTrue(villager.getVillagerXp() == WorkXp.DAILY_CAP, "Nothing more that day, xp " + villager.getVillagerXp());
        villager.setData(ModVillagers.WORK_XP_DAY, villager.getData(ModVillagers.WORK_XP_DAY) - 1);
        WorkXp.award(villager, 5);
        helper.assertTrue(villager.getVillagerXp() == WorkXp.DAILY_CAP + 5, "The next day it earns again, xp " + villager.getVillagerXp());
        helper.succeed();
    }

    public static void smallActionsAddUpToWholeExperience(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER));
        for (int i = 0; i < 9; i++) WorkXp.award(villager, WorkXp.HARVESTED);
        helper.assertTrue(villager.getVillagerXp() == 0, "Nine harvests are not yet a whole point, xp " + villager.getVillagerXp());
        WorkXp.award(villager, WorkXp.HARVESTED);
        helper.assertTrue(villager.getVillagerXp() == 1, "The tenth makes one, xp " + villager.getVillagerXp());
        for (int i = 0; i < 20; i++) WorkXp.award(villager, WorkXp.MINED);
        helper.assertTrue(villager.getVillagerXp() == 2, "Twenty blocks make another, xp " + villager.getVillagerXp());
        helper.succeed();
    }

    public static void withoutLevelThroughWorkProgressIsKept(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER));
        villager.setData(ModVillagers.WORK_XP_PROGRESS, 0.5);
        WorkXp.award(villager, 3);
        helper.assertTrue(villager.getVillagerXp() == 0, "With work experience switched off, work gives no experience, xp " + villager.getVillagerXp());
        helper.assertTrue(villager.getData(ModVillagers.WORK_XP_PROGRESS) == 0.5, "Carried progress is kept for when the switch comes back on, is " + villager.getData(ModVillagers.WORK_XP_PROGRESS));
        helper.succeed();
    }

    public static void unemployedVillagersEarnNoWorkExperience(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        WorkXp.award(villager, 5);
        helper.assertTrue(villager.getVillagerXp() == 0, "No job, no experience, xp " + villager.getVillagerXp());
        helper.succeed();
    }
}

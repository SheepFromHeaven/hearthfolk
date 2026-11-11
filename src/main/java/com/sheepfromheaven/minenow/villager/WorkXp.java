package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Experience earned by the work itself, on top of what trading with players gives. Vanilla's thresholds and its
 * level-up (new trades) apply unchanged; a day's work is capped so no chore can be farmed for levels. Small actions earn fractions of a point, carried over until they add up to a whole one.
 */
public final class WorkXp {
    /** About ten days of steady work take a villager from novice to master (vanilla needs 250 xp). */
    public static final int DAILY_CAP = 24;
    private static final long DAY = 24000L;

    public static final double HARVESTED = 0.1;
    public static final double MINED = 0.05;
    public static final double FELLED = 0.2;
    public static final double PAVED = 0.1;
    public static final double FURNACE_OUTPUT = 0.5;
    public static final double BONE_MEALED = 0.5;
    public static final double DYED = 1;
    public static final double DISPLAYED = 1;
    public static final double ARCHERY_ROUND = 1;
    public static final double CAUGHT = 1;
    public static final double HEALED = 1;
    public static final double SHEARED = 1;
    public static final double CULLED = 1;
    public static final double CRAFTED = 1;
    public static final double ENCHANTED = 2;
    public static final double RESTORED = 1;

    private WorkXp() {}

    public static void award(Villager villager, double xp) {
        if (xp <= 0 || !(villager.level() instanceof ServerLevel level)) return;
        var profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) return;
        int day = (int) (level.getGameTime() / DAY);
        if (villager.getData(ModVillagers.WORK_XP_DAY) != day) {
            villager.setData(ModVillagers.WORK_XP_DAY, day);
            villager.setData(ModVillagers.WORK_XP_TODAY, 0.0);
        }
        double today = villager.getData(ModVillagers.WORK_XP_TODAY);
        double granted = Math.min(xp, DAILY_CAP - today);
        if (granted <= 1e-9) return;
        villager.setData(ModVillagers.WORK_XP_TODAY, today + granted);
        double progress = villager.getData(ModVillagers.WORK_XP_PROGRESS) + granted;
        int whole = (int) (progress + 1e-9);
        villager.setData(ModVillagers.WORK_XP_PROGRESS, Math.max(0.0, progress - whole));
        if (whole == 0) return;
        villager.setVillagerXp(villager.getVillagerXp() + whole);
        while (villager.shouldIncreaseLevel()) villager.increaseMerchantCareer(level);
    }
}

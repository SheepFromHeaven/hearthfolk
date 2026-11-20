package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Vanilla only takes the profession from a job-less villager with no experience, and work experience ({@link WorkXp})
 * ends that at the first fish or crop. A novice that has had no job site for a day gives its trade up anyway.
 */
final class JobLoss {
    static final long PATIENCE = 24000;
    static final int MOVE_RANGE = 12;

    private JobLoss() {}

    static void tick(ServerLevel level, Villager villager) {
        releaseSwitchedOffJob(level, villager);
        forgetGoneJobSite(level, villager);
        var profession = villager.getVillagerData().profession();
        if (villager.isBaby() || profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)
                || villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent()) {
            villager.removeData(ModVillagers.NO_JOB_SINCE);
            if (villager.isBaby() || profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) villager.removeData(ModVillagers.LAST_JOB_SITE);
            else villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .filter(site -> site.dimension() == level.dimension())
                    .ifPresent(site -> villager.setData(ModVillagers.LAST_JOB_SITE, java.util.Optional.of(site.pos())));
            return;
        }
        long since = villager.getData(ModVillagers.NO_JOB_SINCE);
        if (since == Long.MIN_VALUE) {
            villager.setData(ModVillagers.NO_JOB_SINCE, level.getGameTime());
        } else if (level.getGameTime() - since < PATIENCE && takeMovedJobBlock(level, villager)) {
            villager.removeData(ModVillagers.NO_JOB_SINCE);
        } else if (level.getGameTime() - since >= PATIENCE && villager.getVillagerData().level() <= 1) {
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE));
            villager.removeData(ModVillagers.NO_JOB_SINCE);
            villager.removeData(ModVillagers.LAST_JOB_SITE);
            villager.refreshBrain(level);
        }
    }

    /**
     * A Hearthfolk profession has no vanilla work to fall back to, so switched off its villagers lose the job at once,
     * whatever their level, keeping experience, pockets and home chest. A villager that still claims the job block of a
     * switched-off profession (vanilla finds job sites through a tag, not through the profession's own predicate) lets it go.
     */
    private static void releaseSwitchedOffJob(ServerLevel level, Villager villager) {
        if (villager.isBaby()) return;
        var memory = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (!MineNowConfig.professionEnabled(MineNowConfig.Profession.MINER)) com.sheepfromheaven.minenow.miner.Miner.forgetMine(villager.getUUID());
        if (MineNowConfig.switchedOffHearthfolk(villager.getVillagerData().profession())) {
            if (villager.getVillagerData().profession().is(com.sheepfromheaven.minenow.merchant.Merchant.PROFESSION)) com.sheepfromheaven.minenow.merchant.Merchant.release(level, villager);
            villager.removeData(com.sheepfromheaven.minenow.golemsmith.Golemsmith.SITE);
            if (memory.isPresent()) villager.releasePoi(MemoryModuleType.JOB_SITE);
            villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE));
            villager.removeData(ModVillagers.NO_JOB_SINCE);
            villager.removeData(ModVillagers.LAST_JOB_SITE);
            villager.refreshBrain(level);
            return;
        }
        if (memory.isEmpty() || memory.get().dimension() != level.dimension()) return;
        var type = level.getPoiManager().getType(memory.get().pos());
        if (type.isEmpty()) return;
        boolean claimedOff = false;
        for (MineNowConfig.Profession each : MineNowConfig.Profession.values()) {
            if (!each.losesJobWhenOff() || MineNowConfig.professionEnabled(each)) continue;
            if (type.get().unwrapKey().filter(key -> key.identifier().equals(each.key().identifier())).isPresent()) claimedOff = true;
        }
        if (claimedOff) {
            villager.releasePoi(MemoryModuleType.JOB_SITE);
            villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        }
    }

    /**
     * A workstation the player moved: within a day of losing its job site, a villager takes a free job block of its
     * trade placed near the old one before any other villager can, so moving a workstation costs nothing.
     */
    private static boolean takeMovedJobBlock(ServerLevel level, Villager villager) {
        var last = villager.getData(ModVillagers.LAST_JOB_SITE);
        if (last.isEmpty()) return false;
        var profession = villager.getVillagerData().profession().value();
        var pois = level.getPoiManager();
        var found = pois.getInRange(type -> profession.heldJobSite().test(type), last.get(), MOVE_RANGE, PoiManager.Occupancy.HAS_SPACE)
                .map(PoiRecord::getPos)
                .filter(pos -> !pos.equals(last.get()))
                .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(last.get())));
        if (found.isEmpty() || pois.take(type -> profession.heldJobSite().test(type), (type, pos) -> pos.equals(found.get()), found.get(), 1).isEmpty()) return false;
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), found.get()));
        return true;
    }

    /**
     * Drops a job site whose block is gone, or has become another kind of point of interest (a chest where the barrel stood). {@link WalkToPoi} only notices that while the villager works and the place is
     * loaded, so one moved away from its barrel, or resting, kept pointing at nothing and never lost its trade. A place
     * in an unloaded chunk is looked up in the point of interest storage only every 30 s, since that can read from disk.
     */
    private static void forgetGoneJobSite(ServerLevel level, Villager villager) {
        var site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension());
        if (site.isEmpty()) return;
        BlockPos pos = site.get().pos();
        if (!level.isLoaded(pos) && (level.getGameTime() / 40 + villager.getId()) % 15 != 0) return;
        var profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) return;
        if (level.getPoiManager().getType(pos).filter(type -> profession.value().heldJobSite().test(type)).isPresent()) return;
        if (villager.getVillagerXp() == 0) villager.setVillagerXp(1);
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
    }

    /** One line for the villager report: whether it has a job site and, without one, when its trade lapses. */
    static String describe(ServerLevel level, Villager villager) {
        var profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) return "Job site: not needed, it has no trade";
        var site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isPresent()) {
            BlockPos at = site.get().pos();
            String block = level.isLoaded(at) ? level.getBlockState(at).getBlock().getName().getString() : "chunk not loaded";
            String poi = level.getPoiManager().getType(at).map(type -> type.unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown")).orElse("none");
            return "Job site: " + at.toShortString() + " (block: " + block + ", point of interest: " + poi + ")";
        }
        if (villager.getVillagerData().level() > 1) return "Job site: none, but it keeps its trade at level " + villager.getVillagerData().level();
        long since = villager.getData(ModVillagers.NO_JOB_SINCE);
        if (since == Long.MIN_VALUE) return "Job site: none, counting starts within a few seconds";
        long left = Math.max(0, PATIENCE - (level.getGameTime() - since));
        return "Job site: none, its trade lapses in " + (left / 1200) + " min of game time";
    }
}

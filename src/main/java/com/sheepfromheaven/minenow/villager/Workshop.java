package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.ChestBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A working villager's workshop storage: the chests within {@link #RANGE} blocks of its job block (and
 * {@link #HEIGHT} up or down), the nearest first, as many as its level (novice 1, master 5). Only plain chests, so a
 * trapped chest is always the player's; a chest is anyone's home chest or one workshop's, never both (they share the
 * {@code home_storage} job site type). Locked for players like a home chest. Trade goods (tools, supplies, produce)
 * are unloaded here, food and emeralds go home ({@link #personal}); it counts towards the villager's goods.
 */
public final class Workshop {
    static final int RANGE = 6;
    static final int HEIGHT = 2;

    private Workshop() {}

    public static List<BlockPos> chestsOf(ServerLevel level, Villager villager) {
        return villager.getExistingData(ModVillagers.WORKSHOP).orElse(List.of());
    }

    /** Food and emeralds belong at home; everything else a villager unloads is trade goods for its workshop. */
    static boolean personal(ItemStack stack) {
        return stack.is(Items.EMERALD) || stack.has(DataComponents.VILLAGER_FOOD) || Meals.isMeal(stack);
    }

    static boolean isWorkshopChest(ServerLevel level, Villager villager, BlockPos pos) {
        return chestsOf(level, villager).stream().anyMatch(chest -> HomeStorage.halves(level, chest).contains(pos));
    }

    /** Every chest a villager keeps goods in: its home chest, then its workshop chests. */
    static List<BlockPos> allChests(ServerLevel level, Villager villager) {
        List<BlockPos> chests = new ArrayList<>();
        HomeStorage.storageIn(level, villager).ifPresent(chests::add);
        chests.addAll(chestsOf(level, villager));
        return chests;
    }

    static void tick(ServerLevel level, Villager villager) {
        Optional<BlockPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        var profession = villager.getVillagerData().profession();
        if (site.isEmpty() || villager.isBaby() || profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) {
            release(level, villager);
            return;
        }
        List<BlockPos> mine = new ArrayList<>();
        for (BlockPos chest : chestsOf(level, villager)) {
            if (inRange(site.get(), chest) && level.getBlockState(chest).getBlock() instanceof ChestBlock && level.getPoiManager().exists(chest, HomeStorage::isStoragePoi)) mine.add(chest);
            else HomeStorage.releaseChest(level, chest);
        }
        int limit = Math.clamp(villager.getVillagerData().level(), 1, 5);
        while (mine.size() > limit) HomeStorage.releaseChest(level, mine.removeLast());
        PoiManager pois = level.getPoiManager();
        while (mine.size() < limit) {
            Optional<BlockPos> free = pois.getInRange(HomeStorage::isStoragePoi, site.get(), RANGE + HEIGHT, PoiManager.Occupancy.HAS_SPACE)
                    .map(PoiRecord::getPos)
                    .filter(pos -> inRange(site.get(), pos) && !mine.contains(pos) && HomeStorage.otherHalfIsFree(level, pos) && !StorageTheft.hasHopperBelow(level, pos))
                    .min(Comparator.comparingDouble(pos -> pos.distSqr(site.get())));
            if (free.isEmpty()) break;
            pois.take(HomeStorage::isStoragePoi, (type, pos) -> pos.equals(free.get()), free.get(), 1);
            mine.add(free.get());
        }
        for (BlockPos chest : mine) {
            HomeStorage.takeAllHalves(level, chest);
            StorageTheft.check(level, villager, chest);
        }
        if (mine.isEmpty()) villager.removeData(ModVillagers.WORKSHOP);
        else villager.setData(ModVillagers.WORKSHOP, List.copyOf(mine));
    }

    static void release(ServerLevel level, Villager villager) {
        for (BlockPos chest : chestsOf(level, villager)) HomeStorage.releaseChest(level, chest);
        villager.removeData(ModVillagers.WORKSHOP);
    }

    /** A broken workshop chest: its goods stay the villager's, and the chest is forgotten (it claims another). */
    static void broken(ServerLevel level, Villager villager, BlockPos pos) {
        List<BlockPos> mine = new ArrayList<>(chestsOf(level, villager));
        for (BlockPos chest : List.copyOf(mine)) {
            if (!HomeStorage.halves(level, chest).contains(pos)) continue;
            if (level.getBlockEntity(pos) instanceof Container container) Belongings.drop(level, pos, container, villager);
            HomeStorage.releaseChest(level, chest);
            mine.remove(chest);
        }
        if (mine.isEmpty()) villager.removeData(ModVillagers.WORKSHOP);
        else villager.setData(ModVillagers.WORKSHOP, List.copyOf(mine));
    }

    private static boolean inRange(BlockPos site, BlockPos chest) {
        return Math.abs(chest.getX() - site.getX()) <= RANGE && Math.abs(chest.getZ() - site.getZ()) <= RANGE && Math.abs(chest.getY() - site.getY()) <= HEIGHT;
    }
}

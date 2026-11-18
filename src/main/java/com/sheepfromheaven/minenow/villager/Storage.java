package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;

/** The chests a villager keeps its goods in, for code outside this package: its home chest, then its workshop chests. */
public final class Storage {
    private Storage() {}

    public static List<BlockPos> chests(ServerLevel level, Villager villager) {
        return Workshop.allChests(level, villager);
    }

    public static boolean isWorkshopStorage(ServerLevel level, Villager villager, BlockPos pos) {
        return Workshop.isWorkshopChest(level, villager, pos);
    }
}

package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.villager.IdleReport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/** The farmer's part of {@link IdleReport}: what its field looks like. */
public final class FarmerReport {
    private FarmerReport() {}

    public static void register() {
        IdleReport.addExtra(VillagerProfession.FARMER, (farmer, lines) -> {
            if (farmer.level() instanceof ServerLevel level) lines.add(fieldLine(level, farmer));
        });
    }

    public static List<Component> describe(ServerLevel level, Villager farmer) {
        return IdleReport.describe(level, farmer);
    }

    private static String fieldLine(ServerLevel level, Villager farmer) {
        Optional<BlockPos> composter = farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension()).map(GlobalPos::pos);
        if (composter.isEmpty()) return "Field: no composter claimed";
        int radius = Field.radiusFor(farmer.getVillagerData().level());
        int ripe = 0, empty = 0, tillable = 0;
        List<BlockPos> ground = Field.ground(level, composter.get(), radius);
        for (BlockPos pos : ground) {
            BlockState above = level.getBlockState(pos.above());
            if (Crops.isRipe(above)) ripe++;
            else if (Field.isFarmland(level.getBlockState(pos))) { if (above.isAir()) empty++; }
            else tillable++;
        }
        return "Field: " + ground.size() + " blocks within " + radius + " of " + composter.get().toShortString()
                + ": " + ripe + " ripe, " + empty + " to plant, " + tillable + " to till";
    }
}

package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;

public final class PlayerBedTests {
    private static final BlockPos BED = new BlockPos(4, 1, 4);
    private static final BlockPos SPARE_BED = new BlockPos(4, 1, 8);

    private PlayerBedTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    /** What right-clicking a bed does first, fired directly: the real click needs night, and the time of day is shared by every test in the batch. */
    private static void sleepIn(GameTestHelper helper, BlockPos bed) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent(player(helper), helper.absolutePos(bed), null));
    }

    private static boolean taken(GameTestHelper helper, BlockPos bed) {
        return helper.getLevel().getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), helper.absolutePos(bed), 0, PoiManager.Occupancy.IS_OCCUPIED) > 0;
    }

    public static void aBedAPlayerUsesIsNotClaimedByAVillager(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        GameTestHelpers.placeBed(helper, BED);
        GameTestHelpers.placeBed(helper, SPARE_BED);
        sleepIn(helper, BED);
        helper.assertTrue(taken(helper, BED), "A bed a player used is taken, so no villager can claim it");
        Villager villager = helper.spawn(EntityTypes.VILLAGER, BED.east(3));

        helper.succeedWhen(() -> {
            var home = villager.getBrain().getMemory(MemoryModuleType.HOME);
            helper.assertTrue(home.isPresent(), "The villager should claim the bed that is free");
            helper.assertTrue(home.get().pos().equals(helper.absolutePos(SPARE_BED)), "It took the player's bed: " + home.get());
        });
    }

    public static void aPlayerUsingAVillagersBedTakesItOver(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = GameTestHelpers.spawnWithHome(helper, BED.east(3), BED);
        helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent(), "The villager starts with the bed");

        sleepIn(helper, BED);

        helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(), "The villager gives the bed up when a player uses it");
        helper.assertTrue(taken(helper, BED), "...and the bed stays taken, now for the player");
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(), "It doesn't take the bed back, has " + villager.getBrain().getMemory(MemoryModuleType.HOME));
            helper.succeed();
        });
    }
}

package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class FollowTests {
    private static final BlockPos PLAYER = new BlockPos(18, 1, 12);
    private static final BlockPos VILLAGER = new BlockPos(10, 1, 12);

    private FollowTests() {}

    public static void unemployedVillagerFollowsAPlayerHoldingAnEmerald(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = GameTestHelpers.spawnWithHome(helper, VILLAGER, new BlockPos(4, 1, 12));
        ServerPlayer player = playerHolding(helper, Items.EMERALD);

        int[] ticks = {0, 0};
        helper.onEachTick(() -> {
            if (++ticks[0] > 100 && villager.distanceTo(player) < 3.0F) ticks[1]++;
        });

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(ticks[1] > 160, "Villager should stay with the player, was close for " + ticks[1] + " of 200 ticks");
            helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "Following nearby must not cost the villager its bed");
            helper.succeed();
        });
    }

    public static void employedVillagerIgnoresTheEmerald(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(VILLAGER.west(2), Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, VILLAGER, VILLAGER.west(2), VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new net.minecraft.world.item.ItemStack(Items.IRON_HOE));
        ServerPlayer player = playerHolding(helper, Items.EMERALD);
        boolean[] followed = {false};
        helper.onEachTick(() -> followed[0] |= farmer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget() instanceof net.minecraft.world.entity.ai.behavior.EntityTracker tracker && tracker.getEntity() == player)
                .isPresent());

        helper.runAfterDelay(200, () -> {
            helper.assertFalse(followed[0], "A villager with a job should not follow the emerald");
            helper.succeed();
        });
    }

    public static void followingFarFromHomeAbandonsTheOldBed(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, VILLAGER);
        BlockPos farAway = helper.absolutePos(VILLAGER).offset(40, 0, 0);
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), farAway));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), farAway.north()));
        playerHolding(helper, Items.EMERALD);

        helper.succeedWhen(() -> {
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "A villager led far from its bed should give it up");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT), "...and its old meeting bell");
        });
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerHolding(GameTestHelper helper, net.minecraft.world.item.Item item) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(PLAYER);
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        helper.onEachTick(() -> player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
        return player;
    }
}

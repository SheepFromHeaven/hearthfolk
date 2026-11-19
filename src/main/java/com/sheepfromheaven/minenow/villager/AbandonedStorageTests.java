package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** A barrel or chest a villager locked never stays locked once nobody owns it. */
public final class AbandonedStorageTests {
    private static final BlockPos BARREL = new BlockPos(6, 1, 6);

    private AbandonedStorageTests() {}

    @SuppressWarnings("removal")
    public static void aFishermansBarrelOpensAgainWhenHeDies(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(BARREL, Blocks.BARREL);
        Villager fisherman = GameTestHelpers.spawnEmployed(helper, BARREL.east(), BARREL, VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        boolean[] killed = {false};

        helper.succeedWhen(() -> {
            BaseContainerBlockEntity barrel = (BaseContainerBlockEntity) helper.getBlockEntity(BARREL, BaseContainerBlockEntity.class);
            if (!killed[0]) {
                helper.assertFalse(barrel.canOpen(player), "The fisherman locks his barrel first");
                killed[0] = true;
                fisherman.kill(helper.getLevel());
            }
            helper.assertTrue(barrel.canOpen(player), "Once he is dead the barrel opens for anyone");
        });
    }

    @SuppressWarnings("removal")
    public static void anAbandonedLockedBarrelOpensWhenARightClickFindsNoOwner(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(BARREL, Blocks.BARREL);
        HomeStorage.lock(helper.getLevel(), helper.absolutePos(BARREL));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BaseContainerBlockEntity barrel = (BaseContainerBlockEntity) helper.getBlockEntity(BARREL, BaseContainerBlockEntity.class);
        helper.assertFalse(barrel.canOpen(player), "Locked to begin with");

        helper.runAfterDelay(5, () -> {
            BlockPos abs = helper.absolutePos(BARREL);
            var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            helper.assertFalse(event.isCanceled(), "The click goes through to the barrel");
            helper.assertTrue(barrel.canOpen(player), "A lock nobody holds is lifted");
            helper.succeed();
        });
    }

    @SuppressWarnings("removal")
    public static void aBarrelWithThePlayersItemsStaysOpenWhenAFishermanTakesItAsWorkplace(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(BARREL, Blocks.BARREL);
        ((BaseContainerBlockEntity) helper.getBlockEntity(BARREL, BaseContainerBlockEntity.class)).setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        GameTestHelpers.spawnEmployed(helper, BARREL.east(), BARREL, VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(100, () -> {
            BaseContainerBlockEntity barrel = (BaseContainerBlockEntity) helper.getBlockEntity(BARREL, BaseContainerBlockEntity.class);
            helper.assertTrue(barrel.canOpen(player), "The player's things are not locked away from the player");
            helper.succeed();
        });
    }
}

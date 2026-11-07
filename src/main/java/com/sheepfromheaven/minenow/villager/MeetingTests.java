package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class MeetingTests {
    private static final BlockPos FAR_BELL = new BlockPos(2, 1, 2);
    private static final BlockPos NEAR_BELL = new BlockPos(21, 1, 21);

    private MeetingTests() {}

    public static void villagerPicksTheNearestBellWhenTheAfternoonStarts(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(FAR_BELL, Blocks.BELL);
        helper.setBlock(NEAR_BELL, Blocks.BELL);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, NEAR_BELL.west(2).north(2));
        BlockPos far = helper.absolutePos(FAR_BELL);
        helper.getLevel().getPoiManager().take(type -> type.is(PoiTypes.MEETING), (type, pos) -> pos.equals(far), far, 1);
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), far));

        helper.succeedWhen(() -> helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).map(p -> p.pos().equals(helper.absolutePos(NEAR_BELL))).orElse(false),
                "At the start of the afternoon the villager should pick the bell nearest to it, has "
                        + villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT)));
    }

    public static void villagersDoNotHandItemsToEachOther(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager giver = helper.spawn(EntityTypes.VILLAGER, new BlockPos(10, 1, 12));
        Villager taker = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));
        giver.getInventory().addItem(new ItemStack(Items.BREAD, 64));
        boolean[] thrown = {false};
        helper.onEachTick(() -> {
            if (!helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), item -> item.getItem().is(Items.BREAD)).isEmpty()) thrown[0] = true;
        });

        helper.runAfterDelay(1100, () -> {
            helper.assertTrue(!thrown[0] && taker.getInventory().countItem(Items.BREAD) == 0,
                    "Villagers should not hand items to each other; goods change hands by trading");
            helper.succeed();
        });
    }
}

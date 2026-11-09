package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

public final class VillagerNamesTests {
    private static final BlockPos SPAWN = new BlockPos(5, 1, 5);

    private VillagerNamesTests() {}

    public static void villagerIsGivenANameWhenItSpawns(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, SPAWN);

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.hasCustomName(), "Villager should have been given a name");
            String name = villager.getCustomName().getString();
            helper.assertTrue(VillagerNames.names().contains(name), "'" + name + "' is not from the name list");
        });
    }

    public static void aNameFromANameTagIsKept(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        BlockPos at = helper.absolutePos(SPAWN);
        villager.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        villager.setCustomName(Component.literal("Bob"));
        helper.getLevel().addFreshEntity(villager);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue("Bob".equals(villager.getCustomName().getString()), "Name tag name should be kept, got " + villager.getCustomName());
            helper.succeed();
        });
    }
}

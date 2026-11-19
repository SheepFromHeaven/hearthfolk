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

    public static void withoutNamesASpawnedVillagerStaysNameless(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.assertTrue(!VillagerNames.names().isEmpty(), "The name list should be loaded, or this proves nothing");
        Villager villager = helper.spawn(EntityTypes.VILLAGER, SPAWN);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!villager.hasCustomName(), "With names switched off a villager gets no name");
            helper.succeed();
        });
    }

    public static void withoutNamesABornVillagerStaysNameless(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager baby = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        VillagerNames.nameIfMissing(baby);
        helper.assertTrue(!baby.hasCustomName(), "With names switched off a newborn is not named, so its birth is not announced by name");
        helper.succeed();
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

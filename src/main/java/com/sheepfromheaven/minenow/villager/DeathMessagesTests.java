package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

/** A named villager's death is told to the players near it, like a pet's or a player's. */
public final class DeathMessagesTests {
    private DeathMessagesTests() {}

    public static void aNamedVillagersDeathMessageHasItsName(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        villager.setCustomName(Component.literal("Jim"));

        helper.runAfterDelay(5, () -> {
            Optional<Component> message = DeathMessages.message(villager, helper.getLevel().damageSources().fall());
            helper.assertTrue(message.isPresent() && message.get().getString().contains("Jim"),
                    "The message names the villager, is " + message.map(Component::getString).orElse("missing"));
            helper.succeed();
        });
    }

    public static void anUnnamedVillagerDiesWithoutAMessage(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        villager.setCustomName(null);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(DeathMessages.message(villager, helper.getLevel().damageSources().fall()).isEmpty(), "No name, no message");
            helper.succeed();
        });
    }

    public static void onlyPlayersNearTheVillagerAreTold(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        @SuppressWarnings("removal")
        ServerPlayer near = helper.makeMockServerPlayerInLevel();
        @SuppressWarnings("removal")
        ServerPlayer far = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(8, 1, 6));
        helper.onEachTick(() -> {
            near.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            far.snapTo(at.getX() + 0.5 + DeathMessages.RANGE + 50, at.getY(), at.getZ() + 0.5);
        });

        helper.runAfterDelay(5, () -> {
            var told = DeathMessages.recipients(helper.getLevel(), villager);
            helper.assertTrue(told.contains(near), "A player beside the villager is told");
            helper.assertFalse(told.contains(far), "A player " + (DeathMessages.RANGE + 50) + " blocks away is not");
            helper.succeed();
        });
    }
}

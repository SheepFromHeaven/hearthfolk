package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/** A baby villager's arrival is told to the players near it, like a death. */
public final class BirthMessagesTests {
    private BirthMessagesTests() {}

    public static void aNamedBabysBirthMessageHasItsName(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager baby = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        baby.setBaby(true);
        baby.setCustomName(Component.literal("Tom"));

        helper.runAfterDelay(5, () -> {
            var message = DeathMessages.birth(baby);
            helper.assertTrue(message.isPresent() && message.get().getString().contains("Tom"),
                    "The message names the baby, is " + message.map(Component::getString).orElse("missing"));
            helper.succeed();
        });
    }

    public static void anAdultVillagerIsNotAnnouncedAsBorn(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager adult = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        adult.setCustomName(Component.literal("Jim"));

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(DeathMessages.birth(adult).isEmpty(), "Only babies are born");
            helper.succeed();
        });
    }
}

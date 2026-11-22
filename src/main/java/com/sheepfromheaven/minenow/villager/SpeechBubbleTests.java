package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class SpeechBubbleTests {
    private SpeechBubbleTests() {}

    @SuppressWarnings("removal")
    public static void aBubbleIsSentToPlayersWithin16BlocksOnly(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        ServerPlayer near = helper.makeMockServerPlayerInLevel();
        ServerPlayer far = helper.makeMockServerPlayerInLevel();
        near.snapTo(villager.getX() + 10, villager.getY(), villager.getZ());
        far.snapTo(villager.getX() + 24, villager.getY(), villager.getZ());

        List<ServerPlayer> got = new ArrayList<>();
        List<SpeechBubbles.Payload> payloads = new ArrayList<>();
        var original = SpeechBubbles.sender;
        SpeechBubbles.sender = (player, payload) -> {
            got.add(player);
            payloads.add(payload);
        };
        try {
            SpeechBubbles.show(villager, new ItemStack(Items.IRON_HOE), 100);
        } finally {
            SpeechBubbles.sender = original;
        }

        helper.assertTrue(got.contains(near), "The player 10 blocks away gets the bubble");
        helper.assertTrue(!got.contains(far), "The player 24 blocks away does not");
        helper.assertTrue(payloads.getFirst().entityId() == villager.getId() && payloads.getFirst().icon().is(Items.IRON_HOE) && payloads.getFirst().ticks() == 100,
                "The payload names the villager, icon and duration");
        helper.succeed();
    }
}

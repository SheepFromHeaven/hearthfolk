package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ChatBubbleTests {
    private static final BlockPos BELL = new BlockPos(12, 1, 12);
    private static final BlockPos COMPOSTER_A = new BlockPos(11, 1, 14);
    private static final BlockPos COMPOSTER_B = new BlockPos(13, 1, 14);

    private ChatBubbleTests() {}

    /** Records the chat bubbles (not the need bubbles, which last longer) shown over the given villagers (and passes everything on, so other tests' bubbles still go where they went). */
    private static List<SpeechBubbles.Payload> record(Villager... villagers) {
        List<SpeechBubbles.Payload> sent = new CopyOnWriteArrayList<>();
        var original = SpeechBubbles.sender;
        SpeechBubbles.sender = (player, payload) -> {
            original.accept(player, payload);
            for (Villager villager : villagers) {
                if (payload.ticks() == ChatBubbles.SHOWN_TICKS && villager.getId() == payload.entityId() && (sent.isEmpty() || sent.getLast() != payload)) sent.add(payload);
            }
        };
        return sent;
    }

    @SuppressWarnings("removal")
    private static void playerNear(GameTestHelper helper, Villager villager) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(villager.getX() + 6, villager.getY(), villager.getZ());
    }

    private static Villager[] farmersAtTheBell(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setTime(GameTestHelpers.LATE_AFTERNOON);
        helper.setBlock(COMPOSTER_A, Blocks.COMPOSTER);
        helper.setBlock(COMPOSTER_B, Blocks.COMPOSTER);
        Villager a = GameTestHelpers.spawnEmployed(helper, new BlockPos(11, 1, 12), COMPOSTER_A, VillagerProfession.FARMER, PoiTypes.FARMER);
        Villager b = GameTestHelpers.spawnEmployed(helper, new BlockPos(13, 1, 12), COMPOSTER_B, VillagerProfession.FARMER, PoiTypes.FARMER);
        a.getInventory().addItem(new ItemStack(Items.WHEAT, 5));
        b.getInventory().addItem(new ItemStack(Items.CARROT, 5));
        VillageTests.claimBell(helper, a, BELL);
        VillageTests.claimBell(helper, b, BELL);
        playerNear(helper, a);
        return new Villager[]{a, b};
    }

    public static void twoVillagersAtABellChatInTurnAboutTheirGoods(GameTestHelper helper) {
        Villager[] pair = farmersAtTheBell(helper);
        Villager a = pair[0];
        Villager b = pair[1];
        // standing still: villagers that wander off during the chat drift out of earshot, and it ends before six bubbles
        a.setNoAi(true);
        b.setNoAi(true);
        List<SpeechBubbles.Payload> sent = record(a, b);

        helper.succeedWhen(() -> {
            helper.assertTrue(sent.size() >= 2 * ChatBubbles.EXCHANGES, "Three exchanges are six bubbles, got " + sent.size());
            for (int i = 0; i < sent.size(); i++) {
                Villager speaker = sent.get(i).entityId() == a.getId() ? a : b;
                helper.assertTrue(i == 0 || sent.get(i).entityId() != sent.get(i - 1).entityId(), "The villagers take turns, bubble " + i + " repeats the speaker");
                var expected = speaker == a ? Items.WHEAT : Items.CARROT;
                helper.assertTrue(sent.get(i).icon().is(expected), "Bubble " + i + " is a topic from the speaker's goods, got " + sent.get(i).icon());
            }
        });
    }

    public static void afterADeathInTheVillageAMembersBubbleIsAPoppy(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setTime(GameTestHelpers.LATE_AFTERNOON);
        Villager a = helper.spawn(EntityTypes.VILLAGER, new BlockPos(11, 1, 12));
        Villager b = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));
        Villager victim = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 15));
        VillageTests.claimBell(helper, a, BELL);
        VillageTests.claimBell(helper, b, BELL);
        VillageTests.claimBell(helper, victim, BELL);
        playerNear(helper, a);
        List<SpeechBubbles.Payload> sent = record(a, b);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(victim.hasCustomName(), "A villager has a name, so its death is announced");
            victim.kill(helper.getLevel());
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(!sent.isEmpty(), "A member chats");
            helper.assertTrue(sent.getFirst().icon().is(Items.POPPY), "A member's first bubble after the death is a poppy, got " + sent.getFirst().icon());
        });
    }

    public static void showcaseVillagersAreNeverPickedToChat(GameTestHelper helper) {
        Villager[] pair = farmersAtTheBell(helper);
        pair[0].addTag(SpeechBubbles.SHOWCASE_TAG);
        pair[1].addTag(SpeechBubbles.SHOWCASE_TAG);
        List<SpeechBubbles.Payload> sent = record(pair);
        helper.assertTrue(!ChatBubbles.topics(helper.getLevel(), pair[0]).isEmpty(), "The farmer has something to say, so only the tag holds the chat back");

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(sent.isEmpty(), "Showcase villagers never chat: " + sent);
            helper.succeed();
        });
    }

    public static void withoutSpeechBubblesVillagersDoNotChat(GameTestHelper helper) {
        Villager[] pair = farmersAtTheBell(helper);
        List<SpeechBubbles.Payload> sent = record(pair);
        helper.assertTrue(!ChatBubbles.topics(helper.getLevel(), pair[0]).isEmpty(), "The farmer has something to say, so only the switch holds the chat back");

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(sent.isEmpty(), "With speech bubbles off nobody chats: " + sent);
            helper.succeed();
        });
    }
}

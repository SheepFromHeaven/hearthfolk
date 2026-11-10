package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/** Villages as groups of bells ({@link Villages}); the two-village tests use the 72-block-long field so their areas don't touch. */
public final class VillageTests {
    private VillageTests() {}

    public static void villagersThatClaimedABellAreOneVillage(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(12, 1, 12);
        Villager a = settle(helper, new BlockPos(6, 1, 6), new BlockPos(4, 1, 4), bell);
        Villager b = settle(helper, new BlockPos(18, 1, 18), new BlockPos(20, 1, 19), bell);

        helper.succeedWhen(() -> {
            Optional<Villages.Village> village = Villages.of(helper.getLevel(), a);
            helper.assertTrue(village.isPresent(), "A villager that claimed a bell belongs to its village");
            helper.assertTrue(village.equals(Villages.of(helper.getLevel(), b)), "...the same one as every other villager that claimed it");
            helper.assertTrue(village.get().bells().contains(helper.absolutePos(bell)), "The bell is the village's");
            helper.assertTrue(village.get().contains(helper.absolutePos(new BlockPos(4, 1, 4)).west(15)), "Its area reaches 16 blocks past the beds");
            helper.assertFalse(village.get().contains(helper.absolutePos(new BlockPos(4, 1, 4)).west(20)), "...and no further");
        });
    }

    public static void bellsWhoseAreasTouchAreOneVillage(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos west = new BlockPos(4, 1, 12);
        BlockPos east = new BlockPos(44, 1, 12);
        Villager a = settle(helper, new BlockPos(6, 1, 12), new BlockPos(22, 1, 12), west);
        Villager b = settle(helper, new BlockPos(46, 1, 12), new BlockPos(46, 1, 8), east);

        helper.succeedWhen(() -> {
            Optional<Villages.Village> village = Villages.of(helper.getLevel(), a);
            helper.assertTrue(village.isPresent() && village.equals(Villages.of(helper.getLevel(), b)),
                    "Two bells 40 blocks apart are one village when a bed of one lies within 32 blocks of the other");
            helper.assertTrue(village.get().bells().size() == 2, "...with both bells, has " + village.get().bells().size());
        });
    }

    public static void bellsFarApartAreTwoVillages(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager a = settle(helper, new BlockPos(6, 1, 12), new BlockPos(6, 1, 8), new BlockPos(4, 1, 12));
        Villager b = settle(helper, new BlockPos(65, 1, 12), new BlockPos(65, 1, 8), new BlockPos(67, 1, 12));

        helper.succeedWhen(() -> {
            Optional<Villages.Village> first = Villages.of(helper.getLevel(), a);
            Optional<Villages.Village> second = Villages.of(helper.getLevel(), b);
            helper.assertTrue(first.isPresent() && second.isPresent(), "Each villager belongs to the village of its bell");
            helper.assertFalse(first.equals(second), "Bells 63 blocks apart with nothing between them are two villages");
        });
    }

    public static void bedsBetweenTwoBellsJoinThemIntoOneVillage(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager a = settle(helper, new BlockPos(6, 1, 12), new BlockPos(6, 1, 8), new BlockPos(4, 1, 12));
        Villager b = settle(helper, new BlockPos(65, 1, 12), new BlockPos(65, 1, 8), new BlockPos(67, 1, 12));
        for (int x : new int[] {26, 46}) {
            Villager between = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(x + 1, 1, 12));
            GameTestHelpers.giveHome(helper, between, new BlockPos(x, 1, 8));
            helper.onEachTick(() -> between.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT));
        }

        helper.succeedWhen(() -> {
            Optional<Villages.Village> village = Villages.of(helper.getLevel(), a);
            helper.assertTrue(village.isPresent() && village.equals(Villages.of(helper.getLevel(), b)),
                    "Two bells 63 blocks apart are one village when beds of villagers that claimed neither lie between them, each within 32 of the next");
            helper.assertTrue(village.get().bells().size() == 2, "...with both bells");
        });
    }

    public static void villagersBuyFromAnyoneInTheirVillageHoweverFar(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(4, 1, 12);
        Villager buyer = farmer(helper, new BlockPos(6, 1, 14), bell);
        buyer.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        Villager seller = farmer(helper, new BlockPos(64, 1, 12), bell);
        seller.getInventory().addItem(new ItemStack(Items.POTATO, 64));
        helper.onEachTick(() -> Meals.makeHungry(buyer));

        helper.succeedWhen(() -> helper.assertTrue(buyer.getInventory().countItem(Items.POTATO) > 0,
                "A hungry villager buys food from a villager of its own village 58 blocks away, has walked to "
                        + helper.relativePos(buyer.blockPosition()).toShortString()));
    }

    public static void villagesDoNotTradeWithEachOther(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager buyer = farmer(helper, new BlockPos(6, 1, 14), new BlockPos(4, 1, 12));
        buyer.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        Villager seller = farmer(helper, new BlockPos(45, 1, 12), new BlockPos(67, 1, 12));
        seller.getInventory().addItem(new ItemStack(Items.POTATO, 64));
        helper.onEachTick(() -> Meals.makeHungry(buyer));

        helper.runAfterDelay(1000, () -> {
            helper.assertFalse(Villages.of(helper.getLevel(), buyer).equals(Villages.of(helper.getLevel(), seller)), "The two are in different villages");
            helper.assertTrue(buyer.getInventory().countItem(Items.POTATO) == 0 && buyer.getInventory().countItem(Items.EMERALD) == 5,
                    "A villager doesn't buy from another village's villager 39 blocks away, has " + buyer.getInventory().countItem(Items.POTATO) + " potatoes");
            helper.succeed();
        });
    }

    private static Villager farmer(GameTestHelper helper, BlockPos composter, BlockPos bell) {
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.east(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        claimBell(helper, farmer, bell);
        return farmer;
    }

    private static Villager settle(GameTestHelper helper, BlockPos at, BlockPos bed, BlockPos bell) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, at);
        GameTestHelpers.giveHome(helper, villager, bed);
        claimBell(helper, villager, bell);
        return villager;
    }

    static void claimBell(GameTestHelper helper, Villager villager, BlockPos bell) {
        if (!helper.getBlockState(bell).is(Blocks.BELL)) helper.setBlock(bell, Blocks.BELL);
        BlockPos abs = helper.absolutePos(bell);
        helper.getLevel().getPoiManager().take(type -> type.is(PoiTypes.MEETING), (type, pos) -> pos.equals(abs), abs, 1);
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), abs));
    }
}

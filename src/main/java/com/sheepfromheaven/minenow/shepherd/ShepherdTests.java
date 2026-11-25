package com.sheepfromheaven.minenow.shepherd;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ShepherdTests {
    static final BlockPos LOOM = new BlockPos(6, 1, 12);

    private ShepherdTests() {}

    public static void shepherdWithoutShearsOffersToBuyThem(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        GameTestHelpers.giveStartingPurse(helper, shepherd);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(shepherd, InteractionHand.MAIN_HAND, shepherd.position());

        Optional<MerchantOffer> buyShears = shepherd.getOffers().stream().filter(o -> o.getCostA().is(Items.SHEARS)).findFirst();
        helper.assertTrue(buyShears.isPresent() && buyShears.get().getResult().is(Items.EMERALD),
                "A shepherd without shears should offer emeralds for them, offers: " + shepherd.getOffers().size());
        helper.succeed();
    }

    public static void shepherdShearsTheSheepPennedNearItsLoom(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.setVillagerData(shepherd.getVillagerData().withLevel(5));
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        buildPen(helper, 9);
        Sheep white = sheep(helper, 12, 11, DyeColor.WHITE);
        Sheep red = sheep(helper, 12, 13, DyeColor.RED);

        helper.succeedWhen(() -> {
            helper.assertTrue(white.isSheared() && red.isSheared(), "Both penned sheep should be sheared; white " + white.isSheared() + " at "
                    + helper.relativePos(white.blockPosition()).toShortString() + ", red " + red.isSheared() + " at " + helper.relativePos(red.blockPosition()).toShortString()
                    + ", shepherd at " + helper.relativePos(shepherd.blockPosition()).toShortString() + " on ground " + shepherd.onGround()
                    + ", running " + shepherd.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).toList()
                    + ", walk target " + shepherd.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(walk -> helper.relativePos(walk.getTarget().currentBlockPosition()).toShortString() + " speed " + walk.getSpeedModifier()).orElse("none")
                    + ", gate open " + helper.getBlockState(new BlockPos(9, 1, 12)).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN));
            helper.assertTrue(count(shepherd, Items.WOOL.white()) >= 1 && count(shepherd, Items.WOOL.red()) >= 1,
                    "The wool goes into the shepherd's inventory in the sheep's colour");
        });
    }

    public static void shepherdOpensTheFenceGateIntoThePenAndClosesIt(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.setVillagerData(shepherd.getVillagerData().withLevel(5));
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        buildPen(helper, 9);
        BlockPos gate = new BlockPos(9, 1, 12);
        helper.setBlock(gate.above(), Blocks.AIR);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
        List<Sheep> flock = List.of(sheep(helper, 13, 10, DyeColor.WHITE), sheep(helper, 14, 14, DyeColor.WHITE));
        boolean[] wasOpened = {false};
        helper.onEachTick(() -> wasOpened[0] |= helper.getBlockState(gate).getValue(FenceGateBlock.OPEN));

        helper.succeedWhen(() -> {
            helper.assertTrue(flock.stream().allMatch(Sheep::isSheared), "The shepherd should get into the pen through the gate and shear the flock");
            helper.assertTrue(wasOpened[0], "It opens the gate");
            helper.assertFalse(helper.getBlockState(gate).getValue(FenceGateBlock.OPEN), "It closes the gate behind it");
            helper.assertTrue(shepherd.blockPosition().getX() != helper.absolutePos(gate).getX() || shepherd.blockPosition().getZ() != helper.absolutePos(gate).getZ(),
                    "It is not standing in the gateway");
            for (Sheep sheep : flock) {
                BlockPos at = helper.relativePos(sheep.blockPosition());
                helper.assertTrue(at.getX() > 9 && at.getX() < 15 && at.getZ() > 9 && at.getZ() < 15, "No sheep escaped through the gate, one is at " + at);
            }
        });
    }

    public static void aShepherdBreedsItsFlockWithWheat(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        buildPen(helper, 9);
        flock(helper, 2);
        shepherd.getInventory().addItem(new ItemStack(Items.WHEAT, 4));

        helper.succeedWhen(() -> helper.assertTrue(pennedSheep(helper) >= 3,
                "A novice shepherd with two sheep and wheat breeds them, even without shears; the pen holds " + pennedSheep(helper) + ", wheat left " + shepherd.getInventory().countItem(Items.WHEAT)));
    }

    public static void aShepherdStopsBreedingAtItsFlockSize(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        buildPen(helper, 9);
        flock(helper, 4);
        shepherd.getInventory().addItem(new ItemStack(Items.WHEAT, 4));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(shepherd.getInventory().countItem(Items.WHEAT) == 4 && pennedSheep(helper) == 4,
                    "A novice keeps a flock of 4 and breeds no more, wheat " + shepherd.getInventory().countItem(Items.WHEAT) + ", sheep " + pennedSheep(helper));
            helper.succeed();
        });
    }

    public static void aMasterShepherdKeepsABiggerFlock(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.setVillagerData(shepherd.getVillagerData().withLevel(5));
        buildPen(helper, 9);
        flock(helper, 4);
        shepherd.getInventory().addItem(new ItemStack(Items.WHEAT, 4));

        helper.succeedWhen(() -> helper.assertTrue(pennedSheep(helper) >= 5,
                "A master shepherd grows its flock past a novice's 4, the pen holds " + pennedSheep(helper)));
    }

    public static void aShepherdWantsWheatOnlyWhileItsFlockCanGrow(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        buildPen(helper, 9);
        List<Sheep> sheep = flock(helper, 4);
        helper.assertFalse(wantsWheat(helper, shepherd), "With a full flock of 4 a novice shepherd buys no wheat");
        sheep.get(0).discard();
        sheep.get(1).discard();
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(wantsWheat(helper, shepherd), "With two sheep and room for more it wants wheat to breed them");
            sheep.get(2).discard();
            helper.runAfterDelay(1, () -> {
                helper.assertFalse(wantsWheat(helper, shepherd), "A single sheep can't breed, so it wants no wheat");
                helper.succeed();
            });
        });
    }

    public static void aJourneymanShepherdWeavesCarpets(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.setVillagerData(shepherd.getVillagerData().withLevel(3));
        shepherd.getInventory().addItem(new ItemStack(Items.WOOL.red(), 24));

        helper.succeedWhen(() -> helper.assertTrue(com.sheepfromheaven.minenow.villager.Economy.count(helper.getLevel(), shepherd, Items.CARPET.red()) >= 3,
                "A journeyman weaves its red wool into red carpets at its loom, has " + com.sheepfromheaven.minenow.villager.Economy.count(helper.getLevel(), shepherd, Items.CARPET.red())
                        + ", string " + com.sheepfromheaven.minenow.villager.Economy.count(helper.getLevel(), shepherd, Items.STRING) + ", red wool " + com.sheepfromheaven.minenow.villager.Economy.count(helper.getLevel(), shepherd, Items.WOOL.red())));
    }

    public static void anApprenticeShepherdWeavesNoCarpets(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.setVillagerData(shepherd.getVillagerData().withLevel(2));
        shepherd.getInventory().addItem(new ItemStack(Items.WOOL.red(), 24));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(com.sheepfromheaven.minenow.villager.Economy.count(helper.getLevel(), shepherd, Items.CARPET.red()) == 0, "Carpets wait for a journeyman, wool left " + com.sheepfromheaven.minenow.villager.Economy.count(helper.getLevel(), shepherd, Items.WOOL.red()));
            helper.succeed();
        });
    }

    /** {@code count} adult sheep inside the pen at x 9 (inside 10 to 14). */
    private static List<Sheep> flock(GameTestHelper helper, int count) {
        List<Sheep> sheep = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) sheep.add(sheep(helper, 11 + i % 3, 11 + i / 3 * 2, DyeColor.WHITE));
        return sheep;
    }

    private static int pennedSheep(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Sheep.class, new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(10, 1, 10))).expandTowards(4, 2, 4), Sheep::isAlive).size();
    }

    private static boolean wantsWheat(GameTestHelper helper, Villager shepherd) {
        return com.sheepfromheaven.minenow.villager.TradeTable.forVillager(shepherd).map(table -> table.suppliesWanted(helper.getLevel(), shepherd).stream().anyMatch(supply -> supply.item() == Items.WHEAT)).orElse(false);
    }

    public static void otherVillagersLeaveFenceGatesShut(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        buildPen(helper, 9);
        BlockPos gate = new BlockPos(9, 1, 12);
        helper.setBlock(gate.above(), Blocks.AIR);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
        Villager farmer = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
        helper.runAfterDelay(20, () -> farmer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                new net.minecraft.world.entity.ai.memory.WalkTarget(helper.absolutePos(new BlockPos(12, 1, 12)), 0.5F, 0)));
        boolean[] wasOpened = {false};
        helper.onEachTick(() -> wasOpened[0] |= helper.getBlockState(gate).getValue(FenceGateBlock.OPEN));

        helper.runAfterDelay(300, () -> {
            helper.assertFalse(wasOpened[0], "Only shepherds open fence gates");
            helper.succeed();
        });
    }

    public static void shepherdIgnoresSheepFarFromItsLoom(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        buildPen(helper, 16);
        Sheep far = sheep(helper, 19, 12, DyeColor.WHITE);

        helper.runAfterDelay(900, () -> {
            helper.assertFalse(far.isSheared(), "Sheep more than 8 blocks from the loom are not the shepherd's flock");
            helper.succeed();
        });
    }

    public static void shearingASheepTakesTime(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        buildPen(helper, 9);
        List<Sheep> flock = List.of(sheep(helper, 11, 11, DyeColor.WHITE), sheep(helper, 13, 12, DyeColor.WHITE), sheep(helper, 11, 13, DyeColor.WHITE));
        Map<Sheep, Long> shornAt = new HashMap<>();
        helper.onEachTick(() -> flock.forEach(sheep -> {
            if (sheep.isSheared()) shornAt.putIfAbsent(sheep, helper.getTick());
        }));

        helper.succeedWhen(() -> {
            helper.assertTrue(shornAt.size() == flock.size(), "The novice should shear the whole flock, sheared " + shornAt.size()
                    + "; unshorn at " + flock.stream().filter(sheep -> !sheep.isSheared()).map(sheep -> helper.relativePos(sheep.blockPosition()).toShortString()).toList()
                    + ", shepherd at " + helper.relativePos(shepherd.blockPosition()).toShortString());
            helper.assertTrue(shears(shepherd).getDamageValue() >= flock.size(), "Every sheep a novice shears wears its shears");
            List<Long> times = new ArrayList<>(shornAt.values());
            times.sort(Long::compare);
            for (int i = 1; i < times.size(); i++) {
                long gap = times.get(i) - times.get(i - 1);
                helper.assertTrue(gap >= ShepherdSkill.shearTicks(1), "A novice needs " + ShepherdSkill.shearTicks(1) + " ticks per sheep, took " + gap);
            }
        });
    }

    public static void shepherdUnloadsItsWoolAtHome(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        shepherd.getInventory().addItem(new ItemStack(Items.WOOL.white(), 64));
        BlockPos chest = new BlockPos(4, 1, 18);
        helper.setBlock(chest, Blocks.CHEST);
        GameTestHelpers.giveHome(helper, shepherd, new BlockPos(4, 1, 15));

        helper.succeedWhen(() -> {
            var container = helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int wool = 0;
            for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.WOOL.white())) wool += container.getItem(i).getCount();
            int spun = (container.countItem(Items.STRING) + shepherd.getInventory().countItem(Items.STRING)) / 2;
            helper.assertTrue(wool + spun >= 64, "The shepherd should carry its wool (or the string it spun) to its home chest, chest has " + wool + " and " + spun + " spun");
            helper.assertTrue(shears(shepherd).is(Items.SHEARS), "It keeps its shears");
        });
    }

    static void buildPen(GameTestHelper helper, int x) {
        GameTestHelpers.gatedPen(helper, x);
    }

    static Sheep sheep(GameTestHelper helper, int x, int z, DyeColor color) {
        Sheep sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(x, 1, z));
        sheep.setColor(color);
        return sheep;
    }

    private static int count(Villager villager, net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemStack stack : villager.getInventory().getItems()) if (stack.is(item)) count += stack.getCount();
        return count;
    }

    private static ItemStack shears(Villager shepherd) {
        return shepherd.getInventory().getItems().stream().filter(stack -> stack.is(Items.SHEARS)).findFirst().orElse(ItemStack.EMPTY);
    }

    public static void shepherdSpinsWoolIntoString(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.getInventory().addItem(new ItemStack(Items.WOOL.white(), 20));

        helper.succeedWhen(() -> helper.assertTrue(shepherd.getInventory().countItem(Items.STRING) > 0,
                "A shepherd spins its wool into string at the loom, for fishing rods and bows, has " + shepherd.getInventory().countItem(Items.STRING)));
    }

    static Villager spawnShepherd(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(LOOM, Blocks.LOOM);
        return GameTestHelpers.spawnEmployed(helper, LOOM.east(), LOOM, VillagerProfession.SHEPHERD, PoiTypes.SHEPHERD);
    }

    /** Sixteen craft entries make string, one per wool colour, but a player must see string offered once. */
    public static void shepherdOffersStringOnceHoweverManyWoolsMakeIt(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        shepherd.getInventory().addItem(new ItemStack(Items.STRING, 48));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(shepherd, InteractionHand.MAIN_HAND, shepherd.position());

        long stringOffers = shepherd.getOffers().stream().filter(o -> o.getCostA().is(Items.EMERALD) && o.getResult().is(Items.STRING)).count();
        helper.assertTrue(stringOffers == 1, "String is offered once, not once per wool colour, offers " + stringOffers);
        helper.succeed();
    }

    public static void shepherdSellsTheWoolItShearedInTheColourItHolds(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        shepherd.getInventory().addItem(new ItemStack(Items.WOOL.white(), 40));
        shepherd.getInventory().addItem(new ItemStack(Items.WOOL.red(), 40));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(shepherd, InteractionHand.MAIN_HAND, shepherd.position());

        String offers = shepherd.getOffers().stream().map(o -> o.getCostA() + " -> " + o.getResult()).toList().toString();
        helper.assertTrue(shepherd.getOffers().stream().anyMatch(o -> o.getCostA().is(Items.EMERALD) && o.getResult().is(Items.WOOL.white())), "It sells white wool, offers " + offers);
        helper.assertTrue(shepherd.getOffers().stream().anyMatch(o -> o.getCostA().is(Items.EMERALD) && o.getResult().is(Items.WOOL.red())), "...and red wool, offers " + offers);
        helper.succeed();
    }

    public static void aShepherdOnlyCountsSheepInAnEnclosedPen(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        buildPen(helper, 9);
        sheep(helper, 4, 4, DyeColor.WHITE);
        helper.assertTrue(ShearSheep.hasNoFlock(helper.getLevel(), shepherd), "A sheep running free in the open is no flock");
        sheep(helper, 12, 12, DyeColor.RED);
        helper.assertFalse(ShearSheep.hasNoFlock(helper.getLevel(), shepherd), "A sheep inside a fenced pen is a flock");
        helper.succeed();
    }
}

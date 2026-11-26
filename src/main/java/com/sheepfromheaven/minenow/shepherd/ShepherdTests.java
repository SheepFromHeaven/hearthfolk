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

    public static void aSheepInTheGatewayGoesBackIntoThePenWhenTheShepherdClosesTheGateBehindItOnTheWayIn(GameTestHelper helper) {
        Sheep sheep = sheepInOpenGateway(helper, 11);
        helper.succeedWhen(() -> helper.assertTrue(helper.relativePos(sheep.blockPosition()).getX() >= 10,
                "The shepherd walked into the pen; the sheep caught in the gateway goes back in, not out, it is at " + helper.relativePos(sheep.blockPosition()).toShortString()));
    }

    public static void aSheepInTheGatewayGoesBackIntoThePenWhenTheShepherdClosesTheGateBehindItOnTheWayOut(GameTestHelper helper) {
        Sheep sheep = sheepInOpenGateway(helper, 7);
        helper.succeedWhen(() -> helper.assertTrue(helper.relativePos(sheep.blockPosition()).getX() >= 10,
                "The shepherd walked out of the pen; the sheep caught in the gateway stays in, it is at " + helper.relativePos(sheep.blockPosition()).toShortString()));
    }

    public static void aSheepSteppingIntoAGatewayHeldOpenIsKeptInThePen(GameTestHelper helper) {
        spawnShepherd(helper);
        BlockPos gate = GameTestHelpers.gatedPen(helper, 9);
        helper.setBlock(gate, helper.getBlockState(gate).setValue(FenceGateBlock.OPEN, true));
        Sheep sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(9, 1, 12));
        sheep.setNoAi(true);
        com.sheepfromheaven.minenow.villager.OpenFenceGates.keepAnimalsIn(helper.getLevel(), helper.absolutePos(gate));
        helper.succeedWhen(() -> helper.assertTrue(helper.relativePos(sheep.blockPosition()).getX() >= 10,
                "While a villager holds the gate open, a sheep in the gateway is shooed back into the pen, it is at " + helper.relativePos(sheep.blockPosition()).toShortString()));
    }

    /** A shepherd at x {@code shepherdX} (pen inside is 10 to 14, the gate at 9) closes the gate on a sheep standing in the gateway. */
    private static Sheep sheepInOpenGateway(GameTestHelper helper, int shepherdX) {
        Villager shepherd = spawnShepherd(helper);
        BlockPos gate = GameTestHelpers.gatedPen(helper, 9);
        helper.setBlock(gate, helper.getBlockState(gate).setValue(FenceGateBlock.OPEN, true));
        shepherd.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(shepherdX + 0.5, 1, 12.5)));
        Sheep sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(9, 1, 12));
        sheep.setNoAi(true);
        com.sheepfromheaven.minenow.villager.OpenFenceGates.close(helper.getLevel(), shepherd, helper.absolutePos(gate));
        return sheep;
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

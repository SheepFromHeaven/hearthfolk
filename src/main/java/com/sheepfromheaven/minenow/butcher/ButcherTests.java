package com.sheepfromheaven.minenow.butcher;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ButcherTests {
    static final BlockPos SMOKER = new BlockPos(6, 1, 12);

    private ButcherTests() {}

    public static void butcherWithoutAnAxeOffersToBuyOne(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        GameTestHelpers.giveStartingPurse(helper, butcher);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(butcher, InteractionHand.MAIN_HAND, butcher.position());

        Optional<MerchantOffer> buyAxe = butcher.getOffers().stream().filter(o -> o.getCostA().is(Items.STONE_AXE)).findFirst();
        helper.assertTrue(buyAxe.isPresent() && buyAxe.get().getResult().is(Items.EMERALD),
                "A butcher without an axe should offer emeralds for one, offers: " + butcher.getOffers().size());
        helper.succeed();
    }

    public static void butcherNeverBuysMeat(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(butcher, InteractionHand.MAIN_HAND, butcher.position());

        List<String> meatBought = butcher.getOffers().stream()
                .filter(o -> o.getResult().is(Items.EMERALD) && o.getCostA().is(net.minecraft.tags.ItemTags.MEAT))
                .map(o -> o.getCostA().getItem().toString()).toList();
        helper.assertTrue(meatBought.isEmpty(), "A butcher produces meat and never buys it, offers to buy " + meatBought);
        helper.succeed();
    }

    public static void butcherKeepsItsRawMeatForTheSmoker(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.COOKED_PORKCHOP, 64));
        butcher.getInventory().addItem(new ItemStack(Items.PORKCHOP, 20));
        BlockPos chest = new BlockPos(4, 1, 18);
        helper.setBlock(chest, Blocks.CHEST);
        GameTestHelpers.giveHome(helper, butcher, new BlockPos(4, 1, 15));

        helper.succeedWhen(() -> {
            var container = helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int cooked = 0;
            for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.COOKED_PORKCHOP)) cooked += container.getItem(i).getCount();
            helper.assertTrue(cooked >= 64, "The butcher should unload its cooked meat, chest has " + cooked);
            helper.assertTrue(count(butcher, Items.PORKCHOP) == 20, "It keeps the raw meat to smoke, holds " + count(butcher, Items.PORKCHOP));
        });
    }

    public static void cullingTakesAsManySwingsAsTheAxeNeeds(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.WOODEN_AXE));
        GameTestHelpers.gatedPen(helper, 9);
        List<Animal> cows = herd(helper, EntityTypes.COW, ButcherHerd.BREEDING_STOCK + 2);
        List<Long> killedAt = new ArrayList<>();
        List<Integer> wearAtKill = new ArrayList<>();
        int[] aliveBefore = {cows.size()};
        helper.onEachTick(() -> {
            int now = alive(cows);
            for (int i = now; i < aliveBefore[0]; i++) {
                killedAt.add(helper.getTick());
                wearAtKill.add(butcher.getInventory().getItems().stream().filter(stack -> stack.is(Items.WOODEN_AXE)).findFirst().map(ItemStack::getDamageValue).orElse(-1));
            }
            aliveBefore[0] = now;
        });
        int swings = Butchering.swings(10.0F, 7.0);
        int swingTicks = Butchering.swingTicks(0.8);

        helper.succeedWhen(() -> {
            helper.assertTrue(killedAt.size() == 2, "The butcher should cull the two surplus cows, culled " + killedAt.size());
            long gap = killedAt.get(1) - killedAt.get(0);
            helper.assertTrue(gap >= (long) swings * swingTicks, "A wooden axe needs " + swings + " swings of " + swingTicks + " ticks per cow, took " + gap);
            ItemStack axe = butcher.getInventory().getItems().stream().filter(stack -> stack.is(Items.WOODEN_AXE)).findFirst().orElse(ItemStack.EMPTY);
            helper.assertTrue(axe.getDamageValue() == 2 * swings, "Every swing wears the axe, " + axe.getDamageValue() + " damage; kills at " + killedAt
                    + " with wear " + wearAtKill + ", cow health " + cows.stream().filter(Animal::isAlive).map(cow -> (int) cow.getHealth()).toList());
        });
    }

    public static void butcherCullsDownToItsBreedingStock(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        BlockPos gate = GameTestHelpers.gatedPen(helper, 9);
        List<Animal> pigs = herd(helper, EntityTypes.PIG, ButcherHerd.BREEDING_STOCK + 3);
        int[] fewest = {pigs.size()};
        helper.onEachTick(() -> fewest[0] = Math.min(fewest[0], alive(pigs)));

        helper.succeedWhen(() -> {
            helper.assertTrue(alive(pigs) == ButcherHerd.BREEDING_STOCK, "The butcher should cull the herd down to its " + ButcherHerd.BREEDING_STOCK + " breeding adults" + ", " + alive(pigs) + " alive at "
                    + pigs.stream().filter(Animal::isAlive).map(pig -> helper.relativePos(pig.blockPosition()).toShortString() + (pig.isBaby() ? " baby" : "")).toList()
                    + "; butcher at " + helper.relativePos(butcher.blockPosition()).toShortString() + " holding " + butcher.getMainHandItem().getItem()
                    + ", inventory " + butcher.getInventory().getItems().stream().filter(stack -> !stack.isEmpty()).map(ItemStack::toString).toList()
                    + ", running " + butcher.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).toList()
                    + ", walk target " + butcher.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(walk -> helper.relativePos(walk.getTarget().currentBlockPosition()).toShortString() + " speed " + walk.getSpeedModifier()).orElse("none")
                    + ", can't reach since " + butcher.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).orElse(-1L)
                    + ", reachable " + pigs.stream().filter(Animal::isAlive).map(pig -> { var path = butcher.getNavigation().createPath(pig, 1); return path != null && path.canReach(); }).toList()
                    + ", on ground " + butcher.onGround() + ", activity " + butcher.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-"));
            helper.assertTrue(fewest[0] == ButcherHerd.BREEDING_STOCK, "It never culls below its breeding stock, went down to " + fewest[0]);
            helper.assertTrue(count(butcher, Items.PORKCHOP) >= 3, "The meat goes into its inventory, has " + count(butcher, Items.PORKCHOP));
            helper.assertFalse(helper.getBlockState(gate).getValue(FenceGateBlock.OPEN), "It closes the gate behind it");
        });
    }

    public static void butchersAndShepherdsOpenFenceGatesButNotOthers(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager butcher = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        butcher.setVillagerData(butcher.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.BUTCHER));
        Villager shepherd = helper.spawn(EntityTypes.VILLAGER, new BlockPos(8, 1, 6));
        shepherd.setVillagerData(shepherd.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.SHEPHERD));
        Villager farmer = helper.spawn(EntityTypes.VILLAGER, new BlockPos(10, 1, 6));
        farmer.setVillagerData(farmer.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.FARMER));

        helper.assertTrue(com.sheepfromheaven.minenow.villager.OpenFenceGates.canOpen(butcher), "Butchers open fence gates to reach their herd");
        helper.assertTrue(com.sheepfromheaven.minenow.villager.OpenFenceGates.canOpen(shepherd), "Shepherds open fence gates to reach their flock");
        helper.assertFalse(com.sheepfromheaven.minenow.villager.OpenFenceGates.canOpen(farmer), "Other villagers leave fence gates alone");
        helper.succeed();
    }

    public static void butcherBreedsASmallHerd(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.WHEAT, 4));
        GameTestHelpers.gatedPen(helper, 9);
        List<Animal> cows = herd(helper, EntityTypes.COW, 2);

        helper.succeedWhen(() -> {
            helper.assertTrue(alive(cows) == 2, "A herd of two is breeding stock and never culled");
            var calves = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class, helper.getBounds(), Animal::isBaby);
            helper.assertTrue(!calves.isEmpty(), "The butcher should feed the cows wheat so they breed; cows "
                    + cows.stream().map(cow -> helper.relativePos(cow.blockPosition()).toShortString() + " age " + cow.getAge() + (cow.isInLove() ? " in love" : "")).toList()
                    + ", butcher at " + helper.relativePos(butcher.blockPosition()).toShortString() + " with " + count(butcher, Items.WHEAT) + " wheat"
                    + ", gate open " + helper.getBlockState(new BlockPos(9, 1, 12)).getValue(FenceGateBlock.OPEN)
                    + ", running " + butcher.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList());
        });
    }

    public static void butcherLeavesSheepAndNamedAnimalsAlone(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        GameTestHelpers.gatedPen(helper, 9);
        List<Animal> sheep = herd(helper, EntityTypes.SHEEP, 9);
        List<Animal> pets = herd(helper, EntityTypes.PIG, 8);
        pets.forEach(pig -> pig.setCustomName(Component.literal("Pet")));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(alive(sheep) == 9, "Sheep are the shepherd's, not livestock for the butcher");
            helper.assertTrue(alive(pets) == 8, "Named animals are pets and never culled");
            helper.succeed();
        });
    }

    public static void aButcherOutOfFuelSpendsItsToolMoneyOnCoal(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, com.sheepfromheaven.minenow.villager.Economy.reserve(butcher)));
        BlockPos cart = new BlockPos(18, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.COAL, 40));
        butcher.setData(com.sheepfromheaven.minenow.villager.ModVillagers.DEBTS, java.util.Map.of(miner.getUUID(), 3));

        helper.succeedWhen(() -> helper.assertTrue(count(butcher, Items.COAL) > 0,
                "A butcher with only its reserve, no fuel and no credit left buys coal with the part kept for a tool, since without fuel it can't work; has " + count(butcher, Items.EMERALD) + " emeralds"));
    }

    public static void aButcherWithoutFuelBuysNoFishItCannotSmoke(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        BlockPos barrel = new BlockPos(18, 1, 12);
        helper.setBlock(barrel, Blocks.BARREL);
        Villager fisherman = GameTestHelpers.spawnEmployed(helper, barrel.west(), barrel, VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN);
        fisherman.getInventory().addItem(new ItemStack(Items.COD, 40));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(count(butcher, Items.COD) == 0 && count(butcher, Items.EMERALD) == 5,
                    "A butcher with no fuel keeps its money for fuel rather than buying fish it can't smoke; has " + count(butcher, Items.COD) + " cod, " + count(butcher, Items.EMERALD) + " emeralds");
            helper.succeed();
        });
    }

    public static void butcherBuysTheFishermansCatchAndSmokesIt(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.COAL, 4));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        BlockPos barrel = new BlockPos(18, 1, 12);
        helper.setBlock(barrel, Blocks.BARREL);
        Villager fisherman = GameTestHelpers.spawnEmployed(helper, barrel.west(), barrel, VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN);
        fisherman.getInventory().addItem(new ItemStack(Items.COD, 40));

        helper.succeedWhen(() -> helper.assertTrue(count(butcher, Items.COOKED_COD) > 0,
                "Raw fish is nobody's meal, so the butcher buys the fisherman's catch and smokes it; has " + count(butcher, Items.COD) + " raw, " + count(butcher, Items.COOKED_COD) + " cooked"));
    }

    public static void butcherSmokesWithLogsWhenItHasNoCoal(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.BEEF, 3));
        butcher.getInventory().addItem(new ItemStack(Items.OAK_LOG, 4));

        helper.succeedWhen(() -> helper.assertTrue(count(butcher, Items.COOKED_BEEF) >= 3,
                "Without coal the butcher burns logs in its smoker, has " + count(butcher, Items.COOKED_BEEF) + " cooked, " + count(butcher, Items.OAK_LOG) + " logs"));
    }

    public static void butcherSmokesMeatInItsSmoker(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.BEEF, 4));
        butcher.getInventory().addItem(new ItemStack(Items.COAL, 2));

        helper.succeedWhen(() -> helper.assertTrue(count(butcher, Items.COOKED_BEEF) >= 4,
                "The butcher should smoke its beef in its smoker and take it out, has " + count(butcher, Items.COOKED_BEEF) + " cooked"));
    }

    public static void butcherUnloadsItsMeatAtHome(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.COOKED_PORKCHOP, 64));
        BlockPos chest = new BlockPos(4, 1, 18);
        helper.setBlock(chest, Blocks.CHEST);
        GameTestHelpers.giveHome(helper, butcher, new BlockPos(4, 1, 15));

        helper.succeedWhen(() -> {
            var container = helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int meat = 0;
            for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.COOKED_PORKCHOP)) meat += container.getItem(i).getCount();
            helper.assertTrue(meat >= 64, "The butcher should carry its meat to its home chest, chest has " + meat);
        });
    }

    private static <T extends Animal> List<Animal> herd(GameTestHelper helper, EntityType<T> type, int size) {
        List<Animal> herd = new ArrayList<>();
        for (int i = 0; i < size; i++) herd.add(helper.spawn(type, new BlockPos(10 + i % 5, 1, 10 + i / 5 * 2)));
        return herd;
    }

    private static int alive(List<Animal> animals) {
        return (int) animals.stream().filter(Animal::isAlive).count();
    }

    private static int count(Villager villager, Item item) {
        int count = 0;
        for (ItemStack stack : villager.getInventory().getItems()) if (stack.is(item)) count += stack.getCount();
        return count;
    }

    public static void butcherBuysWheatSeedsFromTheFarmerForItsChickens(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        BlockPos composter = new BlockPos(18, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 60));

        helper.succeedWhen(() -> helper.assertTrue(count(butcher, Items.WHEAT_SEEDS) > 0,
                "A farmer's spare seeds are the butcher's chicken feed, so it buys them; has " + count(butcher, Items.WHEAT_SEEDS)));
    }

    public static void butcherKillsNoMoreThanItsDailyLimit(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        GameTestHelpers.gatedPen(helper, 9);
        List<Animal> pigs = herd(helper, EntityTypes.PIG, ButcherHerd.BREEDING_STOCK + ButcherHerd.DAILY_KILLS + 3);
        int[] fewest = {pigs.size()};
        helper.onEachTick(() -> fewest[0] = Math.min(fewest[0], alive(pigs)));

        helper.runAfterDelay(2800, () -> {
            helper.assertTrue(fewest[0] == pigs.size() - ButcherHerd.DAILY_KILLS, "Surplus or not, a butcher kills " + ButcherHerd.DAILY_KILLS + " animals a day so the herd breeds faster than it is culled, "
                    + "lowest herd " + fewest[0] + " of " + pigs.size());
            helper.succeed();
        });
    }

    static Villager spawnButcher(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        return GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
    }
}

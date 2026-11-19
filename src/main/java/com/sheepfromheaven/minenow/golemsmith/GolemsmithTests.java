package com.sheepfromheaven.minenow.golemsmith;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class GolemsmithTests {
    private static final BlockPos WORKSHOP = new BlockPos(10, 1, 12);

    private GolemsmithTests() {}

    public static void sneakingWithACarvedPumpkinOnAnIronBlockMakesAWorkshop(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(WORKSHOP, Blocks.IRON_BLOCK);
        ServerPlayer player = player(helper);
        player.setShiftKeyDown(true);

        useOn(helper, player);

        helper.assertTrue(helper.getBlockState(WORKSHOP).is(Golemsmith.WORKSHOP.get()), "A carved pumpkin used while sneaking on an iron block makes a workshop, got " + helper.getBlockState(WORKSHOP));
        helper.assertTrue(player.getMainHandItem().isEmpty(), "The pumpkin goes into it, still holding " + player.getMainHandItem());
        helper.succeed();
    }

    public static void withoutSneakingTheIronBlockStaysAsItIs(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(WORKSHOP, Blocks.IRON_BLOCK);
        ServerPlayer player = player(helper);

        useOn(helper, player);

        helper.assertTrue(helper.getBlockState(WORKSHOP).is(Blocks.IRON_BLOCK), "Building a golem pattern must stay vanilla, got " + helper.getBlockState(WORKSHOP));
        helper.succeed();
    }

    public static void breakingAWorkshopGivesBackTheIronBlockAndThePumpkin(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(WORKSHOP, Golemsmith.WORKSHOP.get());

        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(WORKSHOP), helper.getLevel(), helper.absolutePos(WORKSHOP), null);

        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.IRON_BLOCK)) && drops.stream().anyMatch(stack -> stack.is(Items.CARVED_PUMPKIN)), "Both parts drop, got " + drops);
        helper.succeed();
    }

    public static void unemployedVillagerBecomesAGolemsmithAtAFreeWorkshop(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(WORKSHOP, Golemsmith.WORKSHOP.get());
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().profession().is(Golemsmith.PROFESSION),
                "An unemployed villager should take a free workshop and become a golemsmith"));
    }

    public static void aGolemsmithMendsAHurtGolemWithIngots(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 1);
        smith.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 4));
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(14, 1, 12));
        golem.setHealth(30);

        helper.succeedWhen(() -> {
            helper.assertTrue(golem.getHealth() >= 55, "An ingot mends a quarter of a golem, it has " + golem.getHealth());
        });
    }

    public static void aGolemsmithLeavesAHealthyGolemAlone(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 1);
        smith.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 4));
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(14, 1, 12));
        golem.setHealth(90);

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(golem.getHealth() == 90, "Ingots aren't wasted on a golem with scratches, it has " + golem.getHealth());
            helper.succeed();
        });
    }

    public static void aJourneymanGolemsmithBuildsAGolem(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 4));
        smith.getInventory().addItem(new ItemStack(Items.CARVED_PUMPKIN));

        helper.succeedWhen(() -> {
            helper.assertTrue(golems(helper) == 1, "It assembles a golem, there are " + golems(helper));
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_BLOCK) == 0 && smith.getInventory().countItem(Items.CARVED_PUMPKIN) == 0, "...from four iron blocks and a pumpkin");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(IronGolem.class, helper.getBounds()).stream().noneMatch(IronGolem::isPlayerCreated), "...that nobody calls the player's");
            helper.assertTrue(placed(helper, net.minecraft.world.level.block.Blocks.IRON_BLOCK) == 0 && placed(helper, Blocks.CARVED_PUMPKIN) == 0, "...and the blocks it stacked came to life");
        });
    }

    public static void aGolemsmithPlacesAnIronBlockAsSoonAsItHasOne(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(placed(helper, Blocks.IRON_BLOCK) == 1 && golems(helper) == 0, "One block is enough to start the golem, " + placed(helper, Blocks.IRON_BLOCK) + " placed");
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_BLOCK) == 0, "...from its own goods");
            helper.succeed();
        });
    }

    public static void aHalfBuiltGolemIsFinishedLater(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 2));
        boolean[] restocked = {false};

        helper.succeedWhen(() -> {
            if (!restocked[0]) {
                helper.assertTrue(placed(helper, Blocks.IRON_BLOCK) == 2, "Waiting for the first two blocks");
                restocked[0] = true;
                smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 2));
                smith.getInventory().addItem(new ItemStack(Items.CARVED_PUMPKIN));
                helper.assertTrue(false, "The half-built golem waits for more iron");
            }
            helper.assertTrue(golems(helper) == 1, "With more iron it finishes that golem, there are " + golems(helper));
            helper.assertTrue(placed(helper, Blocks.IRON_BLOCK) == 0, "...and starts no second one, " + placed(helper, Blocks.IRON_BLOCK) + " blocks standing");
        });
    }

    public static void breakingAHalfBuiltGolemCostsReputation(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK));
        ServerPlayer griefer = player(helper);
        boolean[] broken = {false};

        helper.succeedWhen(() -> {
            if (!broken[0]) {
                helper.assertTrue(smith.hasData(Golemsmith.SITE), "Waiting for the first block of the golem");
                BlockPos base = smith.getData(Golemsmith.SITE).base();
                helper.assertTrue(griefer.gameMode.destroyBlock(base), "Breaking it is allowed");
                broken[0] = true;
            }
            int reputation = smith.getGossips().getReputation(griefer.getUUID(), type -> true);
            helper.assertTrue(reputation < 0, "...but the golemsmith remembers, reputation " + reputation);
        });
    }

    public static void breakingYourOwnIronBlockCostsNothing(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        BlockPos mine = new BlockPos(20, 1, 20);
        helper.setBlock(mine, Blocks.IRON_BLOCK);
        ServerPlayer player = player(helper);

        player.gameMode.destroyBlock(helper.absolutePos(mine));

        helper.assertTrue(smith.getGossips().getReputation(player.getUUID(), type -> true) == 0, "An iron block that isn't the golemsmith's is the player's own business");
        helper.succeed();
    }

    public static void aPoppyMarksWhereTheGolemIsBuilt(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        BlockPos poppy = new BlockPos(16, 1, 12);
        helper.setBlock(poppy.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(poppy, Blocks.POPPY);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 4));
        smith.getInventory().addItem(new ItemStack(Items.CARVED_PUMPKIN));

        Vec3[] born = {null};

        helper.succeedWhen(() -> {
            List<IronGolem> built = helper.getLevel().getEntitiesOfClass(IronGolem.class, helper.getBounds());
            if (born[0] == null && !built.isEmpty()) born[0] = built.getFirst().position();
            helper.assertTrue(born[0] != null, "It builds a golem");
            double distance = born[0].distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(poppy)));
            helper.assertTrue(distance < 1.0, "...where the poppy marks it, " + distance + " away");
            helper.assertTrue(helper.getBlockState(poppy).is(Blocks.POPPY), "...and puts the poppy back for the next one");
        });
    }

    public static void aBlockedMarkIsNoReasonToBuildElsewhere(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        BlockPos poppy = new BlockPos(16, 1, 12);
        helper.setBlock(poppy.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(poppy, Blocks.POPPY);
        helper.setBlock(poppy.above(), Blocks.STONE);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 4));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(placed(helper, Blocks.IRON_BLOCK) == 0, "The player chose the place, it waits for room there, " + placed(helper, Blocks.IRON_BLOCK) + " placed");
            helper.succeed();
        });
    }

    public static void villagersNoLongerSummonGolems(GameTestHelper helper) {
        GameTestHelpers.fence(helper);

        var summoned = net.minecraft.util.SpawnUtil.trySpawnMob(EntityTypes.IRON_GOLEM, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED,
                helper.getLevel(), helper.absolutePos(new BlockPos(12, 1, 12)), 10, 8, 6, net.minecraft.util.SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false);

        helper.assertTrue(summoned.isEmpty() && golems(helper) == 0, "Golems come from the golemsmith now, not out of thin air, got " + golems(helper));
        helper.succeed();
    }

    public static void villagerGolemsCanBeSwitchedBackOn(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        boolean before = com.sheepfromheaven.minenow.MineNowConfig.villagersSummonGolems();
        try {
            com.sheepfromheaven.minenow.MineNowConfig.setVillagersSummonGolems(true);
            var summoned = net.minecraft.util.SpawnUtil.trySpawnMob(EntityTypes.IRON_GOLEM, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED,
                    helper.getLevel(), helper.absolutePos(new BlockPos(12, 1, 12)), 10, 8, 6, net.minecraft.util.SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false);
            helper.assertTrue(summoned.isPresent(), "With the switch on, villagers summon golems as in vanilla");
        } finally {
            com.sheepfromheaven.minenow.MineNowConfig.setVillagersSummonGolems(before);
        }
        helper.succeed();
    }

    public static void aPlayerBuiltGolemStillComesToLife(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos base = new BlockPos(12, 1, 12);
        helper.setBlock(base, Blocks.IRON_BLOCK);
        helper.setBlock(base.above(), Blocks.IRON_BLOCK);
        helper.setBlock(base.above().east(), Blocks.IRON_BLOCK);
        helper.setBlock(base.above().west(), Blocks.IRON_BLOCK);
        helper.setBlock(base.above(2), Blocks.CARVED_PUMPKIN);

        helper.succeedWhen(() -> helper.assertTrue(golems(helper) == 1, "Vanilla's iron and pumpkin still make a golem, there are " + golems(helper)));
    }

    public static void anApprenticeGolemsmithBuildsNothing(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 2);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 4));
        smith.getInventory().addItem(new ItemStack(Items.CARVED_PUMPKIN));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(golems(helper) == 0, "Building is journeyman work, there are " + golems(helper));
            helper.succeed();
        });
    }

    public static void aGolemsmithStopsBuildingWhenTheVillageHasEnoughGolems(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 4));
        smith.getInventory().addItem(new ItemStack(Items.CARVED_PUMPKIN));
        helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(16, 1, 12));
        helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(16, 1, 16));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(golems(helper) == 2, "A journeyman keeps two golems at most, there are " + golems(helper));
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_BLOCK) == 4, "...and keeps its iron");
            helper.succeed();
        });
    }

    public static void aGolemsmithKeepsItsIronWhenTheresNoRoomForAGolem(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 4));
        smith.getInventory().addItem(new ItemStack(Items.CARVED_PUMPKIN));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int reach = 1; reach <= 4; reach++) helper.setBlock(WORKSHOP.relative(direction, reach).above(), Blocks.STONE);
        }

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(golems(helper) == 0, "No golem fits beside the workshop, there are " + golems(helper));
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_BLOCK) == 4 && smith.getInventory().countItem(Items.CARVED_PUMPKIN) == 1, "...so it keeps its iron and pumpkin");
            helper.succeed();
        });
    }

    public static void aGolemsmithPressesIronBlocksAndCarvesPumpkinsWithShears(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        smith.getInventory().addItem(new ItemStack(Items.SHEARS));
        smith.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 64));
        smith.getInventory().addItem(new ItemStack(Items.PUMPKIN, 2));

        helper.succeedWhen(() -> {
            int blocks = smith.getInventory().countItem(Items.IRON_BLOCK) + placed(helper, Blocks.IRON_BLOCK);
            helper.assertTrue(blocks >= 1 || golems(helper) > 0, "It presses ingots into iron blocks, has " + blocks);
            helper.assertTrue(smith.getInventory().countItem(Items.CARVED_PUMPKIN) + placed(helper, Blocks.CARVED_PUMPKIN) >= 1 || golems(helper) > 0, "...and carves a pumpkin");
            helper.assertTrue(smith.getInventory().getItem(com.sheepfromheaven.minenow.villager.Tools.slot(smith)).getDamageValue() >= 1, "...with its shears, which wear");
        });
    }

    public static void withoutShearsAGolemsmithCarvesNoPumpkin(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 3);
        smith.getInventory().addItem(new ItemStack(Items.PUMPKIN, 2));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(smith.getInventory().countItem(Items.CARVED_PUMPKIN) == 0, "Carving takes shears");
            helper.succeed();
        });
    }

    public static void aNoviceGolemsmithWorksIronForALiving(GameTestHelper helper) {
        Villager smith = spawnGolemsmith(helper, 1);
        smith.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 20));

        helper.succeedWhen(() -> {
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_BARS) >= 16 && smith.getInventory().countItem(Items.BUCKET) >= 1,
                    "A novice makes iron bars and buckets to sell, has " + smith.getInventory().countItem(Items.IRON_BARS) + " bars, " + smith.getInventory().countItem(Items.BUCKET) + " buckets");
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_BLOCK) == 0, "...and no golem iron yet");
        });
    }

    public static void itsIronworkIsExported(GameTestHelper helper) {
        var exports = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath(com.sheepfromheaven.minenow.MineNow.MOD_ID, "exports"));
        for (var item : List.of(Items.IRON_BARS, Items.BUCKET, Items.CAULDRON)) {
            helper.assertTrue(new ItemStack(item).is(exports), item + " should draw travelling merchants, bringing new emeralds into the village");
        }
        helper.succeed();
    }

    private static int placed(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(0, 1, 0, 23, 5, 23)) {
            if (helper.getBlockState(pos).is(block)) count++;
        }
        return count;
    }

    private static int golems(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(IronGolem.class, helper.getBounds()).size();
    }

    static Villager spawnGolemsmith(GameTestHelper helper, int level) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(WORKSHOP, Golemsmith.WORKSHOP.get());
        Villager villager = GameTestHelpers.spawnEmployed(helper, WORKSHOP.east(2), WORKSHOP, Golemsmith.PROFESSION, Golemsmith.POI);
        villager.setVillagerData(villager.getVillagerData().withLevel(level));
        return villager;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARVED_PUMPKIN));
        return player;
    }

    private static void useOn(GameTestHelper helper, ServerPlayer player) {
        BlockPos absolute = helper.absolutePos(WORKSHOP);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }
}

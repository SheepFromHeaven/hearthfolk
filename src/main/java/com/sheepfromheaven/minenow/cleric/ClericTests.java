package com.sheepfromheaven.minenow.cleric;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.villager.Economy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

public final class ClericTests {
    private static final BlockPos STAND = new BlockPos(11, 1, 12);

    private ClericTests() {}

    private static Villager spawnCleric(GameTestHelper helper, int level) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        helper.setBlock(STAND.east(3).below(), Blocks.WATER);
        Villager cleric = GameTestHelpers.spawnEmployed(helper, STAND.west(), STAND, VillagerProfession.CLERIC, PoiTypes.CLERIC);
        cleric.setVillagerData(cleric.getVillagerData().withLevel(level));
        return cleric;
    }

    private static int count(GameTestHelper helper, Villager cleric, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        return PotionGoods.count(helper.getLevel(), cleric, Items.POTION, potion);
    }

    public static void everyBrewIsAVanillaBrewingRecipe(GameTestHelper helper) {
        helper.assertTrue(!Brews.all().isEmpty(), "The cleric has brews to make");
        for (Brews.Brew brew : Brews.all()) {
            var made = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.BREWING, new BrewingInput(brew.input().stack(), new ItemStack(brew.reagent())), helper.getLevel());
            helper.assertTrue(made.isPresent(), "Vanilla brews " + brew.reagent() + " into " + brew.output().potion().getRegisteredName() + " from " + brew.input().potion().getRegisteredName());
            ItemStack output = made.get().value().assemble(new BrewingInput(brew.input().stack(), new ItemStack(brew.reagent())));
            helper.assertTrue(PotionGoods.is(output, brew.output().form(), brew.output().potion()),
                    "...into exactly the potion the file says, got " + output);
        }
        helper.succeed();
    }

    public static void aClericFillsBottlesAtWaterAndBrewsAwkwardPotions(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 1);
        cleric.getInventory().addItem(new ItemStack(Items.GLASS_BOTTLE, 6));
        cleric.getInventory().addItem(new ItemStack(Items.NETHER_WART, 4));
        cleric.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER, 2));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, cleric, Potions.AWKWARD) >= 1, "The cleric brews awkward potions from water bottles and nether wart, has " + count(helper, cleric, Potions.AWKWARD));
            helper.assertTrue(cleric.getInventory().countItem(Items.NETHER_WART) < 4, "...using up its wart");
            helper.assertTrue(cleric.getInventory().countItem(Items.GLASS_BOTTLE) < 6, "...from bottles it filled at the water");
        });
    }

    public static void aClericWithoutWaterBrewsNothing(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 1);
        helper.setBlock(STAND.east(3).below(), Blocks.STONE);
        cleric.getInventory().addItem(new ItemStack(Items.GLASS_BOTTLE, 6));
        cleric.getInventory().addItem(new ItemStack(Items.NETHER_WART, 4));
        cleric.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER, 2));

        helper.runAfterDelay(1200, () -> {
            helper.assertTrue(count(helper, cleric, Potions.AWKWARD) == 0 && cleric.getInventory().countItem(Items.GLASS_BOTTLE) == 6,
                    "Nowhere to fill its bottles, it brews nothing, has " + count(helper, cleric, Potions.AWKWARD) + " awkward potions");
            helper.succeed();
        });
    }

    public static void aClericBrewsSwiftnessFromAwkwardPotionsAndSugar(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 1);
        cleric.getInventory().addItem(PotionGoods.stack(Items.POTION, Potions.AWKWARD));
        cleric.getInventory().addItem(new ItemStack(Items.SUGAR, 4));
        cleric.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER, 2));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, cleric, Potions.SWIFTNESS) >= 1, "Awkward potion and sugar make swiftness, has " + count(helper, cleric, Potions.SWIFTNESS));
            helper.assertTrue(cleric.getInventory().countItem(Items.SUGAR) == 3, "...with one sugar");
        });
    }

    public static void aClericDoesNotBrewWhatItsLevelDoesNotUnlock(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 1);
        cleric.getInventory().addItem(PotionGoods.stack(Items.POTION, Potions.AWKWARD));
        cleric.getInventory().addItem(new ItemStack(Items.RABBIT_FOOT, 2));
        cleric.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER, 2));

        helper.runAfterDelay(1000, () -> {
            helper.assertTrue(count(helper, cleric, Potions.LEAPING) == 0, "A novice does not brew leaping, has " + count(helper, cleric, Potions.LEAPING));
            helper.succeed();
        });
    }

    public static void aClericBrewsMoreBottlesAtOnceAsItLevelsUp(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 3);
        cleric.getInventory().addItem(new ItemStack(Items.GLASS_BOTTLE, 6));
        cleric.getInventory().addItem(new ItemStack(Items.NETHER_WART, 4));
        cleric.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER, 2));

        helper.succeedWhen(() -> helper.assertTrue(count(helper, cleric, Potions.AWKWARD) >= 3, "A journeyman loads three bottles into one brew, has " + count(helper, cleric, Potions.AWKWARD)));
    }

    public static void brewingSpendsBlazePowderAsFuel(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 1);
        cleric.getInventory().addItem(new ItemStack(Items.GLASS_BOTTLE, 3));
        cleric.getInventory().addItem(new ItemStack(Items.NETHER_WART, 4));
        cleric.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER, 2));

        helper.succeedWhen(() -> {
            BrewingStandBlockEntity stand = helper.getBlockEntity(STAND, BrewingStandBlockEntity.class);
            helper.assertTrue(cleric.getInventory().countItem(Items.BLAZE_POWDER) == 1 && stand.fuel > 0, "A brew burns one blaze powder, which fuels twenty, has " + cleric.getInventory().countItem(Items.BLAZE_POWDER) + " left, fuel " + stand.fuel);
        });
    }

    public static void aClericWithoutFuelBrewsNothing(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 1);
        cleric.getInventory().addItem(new ItemStack(Items.GLASS_BOTTLE, 3));
        cleric.getInventory().addItem(new ItemStack(Items.NETHER_WART, 4));

        helper.runAfterDelay(1000, () -> {
            helper.assertTrue(count(helper, cleric, Potions.AWKWARD) == 0, "No blaze powder, no brew, has " + count(helper, cleric, Potions.AWKWARD));
            helper.succeed();
        });
    }

    public static void aClericGainsExperienceFromBrewing(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 1);
        GameTestHelpers.allowWorkXp(cleric);
        cleric.getInventory().addItem(new ItemStack(Items.GLASS_BOTTLE, 3));
        cleric.getInventory().addItem(new ItemStack(Items.NETHER_WART, 4));
        cleric.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER, 2));

        helper.succeedWhen(() -> helper.assertTrue(cleric.getVillagerXp() > 0, "A cleric earns experience from brewing, xp " + cleric.getVillagerXp()));
    }

    public static void aClericSellsItsPotionsAsExactStacks(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 2);
        cleric.getInventory().addItem(new ItemStack(Items.IRON_INGOT));
        cleric.getInventory().addItem(PotionGoods.stack(Items.POTION, Potions.LEAPING));
        cleric.getInventory().addItem(PotionGoods.stack(Items.POTION, Potions.SWIFTNESS));
        cleric.getInventory().addItem(PotionGoods.stack(Items.POTION, Potions.SWIFTNESS));
        ServerPlayer player = player(helper);

        openTrades(player, cleric);
        var offers = cleric.getOffers().stream().filter(offer -> offer.getResult().is(Items.POTION)).toList();
        helper.assertTrue(offers.size() == 2, "One offer per kind of potion, not one for 'a potion', offers: " + offers);
        var swiftness = offers.stream().filter(offer -> PotionGoods.is(offer.getResult(), Items.POTION, Potions.SWIFTNESS)).findFirst().orElseThrow();
        helper.assertTrue(swiftness.getMaxUses() == 2 && swiftness.getCostA().is(Items.EMERALD) && swiftness.getCostA().getCount() == 2, "Swiftness: both for sale at 2 emeralds each, " + swiftness);

        ItemStack bought = trade(player, swiftness, new ItemStack(Items.EMERALD, 2));

        helper.assertTrue(PotionGoods.is(bought, Items.POTION, Potions.SWIFTNESS), "The player gets the potion they paid for, got " + bought);
        helper.assertTrue(count(helper, cleric, Potions.SWIFTNESS) == 1 && count(helper, cleric, Potions.LEAPING) == 1, "The cleric hands over a swiftness potion and keeps the leaping one");
        helper.assertTrue(Economy.emeralds(helper.getLevel(), cleric) == 2, "...and gets the emeralds");
        helper.succeed();
    }

    public static void aNovicesTradesLeaveOutPotionsItDoesNotBrewYet(GameTestHelper helper) {
        Villager cleric = spawnCleric(helper, 1);
        cleric.getInventory().addItem(new ItemStack(Items.IRON_INGOT));
        cleric.getInventory().addItem(PotionGoods.stack(Items.POTION, Potions.LEAPING));
        ServerPlayer player = player(helper);

        openTrades(player, cleric);

        helper.assertTrue(cleric.getOffers().stream().noneMatch(offer -> offer.getResult().is(Items.POTION)), "A novice doesn't sell leaping potions yet, offers: " + cleric.getOffers());
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    private static void openTrades(ServerPlayer player, Villager cleric) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(cleric, net.minecraft.world.InteractionHand.MAIN_HAND, cleric.position());
    }

    private static ItemStack trade(ServerPlayer player, net.minecraft.world.item.trading.MerchantOffer offer, ItemStack payment) {
        MerchantMenu menu = (MerchantMenu) player.containerMenu;
        menu.setSelectionHint(menu.getOffers().indexOf(offer));
        menu.getSlot(0).set(payment);
        menu.clicked(2, 0, ContainerInput.PICKUP, player);
        return menu.getCarried().copy();
    }
}

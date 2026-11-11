package com.sheepfromheaven.minenow.cartographer;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.villager.ProfessionTrades;
import com.sheepfromheaven.minenow.villager.VillageTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

public final class CartographerTests {
    private static final BlockPos TABLE = new BlockPos(5, 1, 12);
    private static final BlockPos BED = new BlockPos(3, 1, 3);
    private static final BlockPos BELL = new BlockPos(12, 1, 12);
    private static final int ROAD_Z = 18;

    private CartographerTests() {}

    public static void aCartographerRebuildsItsQuillFromFeatherInkAndBottle(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, false);
        cartographer.getInventory().addItem(new ItemStack(Items.FEATHER));
        cartographer.getInventory().addItem(new ItemStack(Items.INK_SAC));
        cartographer.getInventory().addItem(new ItemStack(Items.GLASS_BOTTLE));

        helper.succeedWhen(() -> {
            helper.assertTrue(cartographer.getInventory().countItem(Cartographer.INK_AND_QUILL.get()) == 1, "A feather, an ink sac and a glass bottle make an ink and quill");
            helper.assertTrue(cartographer.getInventory().countItem(Items.FEATHER) == 0 && cartographer.getInventory().countItem(Items.INK_SAC) == 0
                    && cartographer.getInventory().countItem(Items.GLASS_BOTTLE) == 0, "...using them up");
        });
    }

    public static void aCartographerWithoutAQuillDrawsNoMap(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, true);
        cartographer.getInventory().addItem(new ItemStack(Items.PAPER, 8));
        cartographer.getInventory().addItem(new ItemStack(Items.INK_SAC));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(cartographer.getInventory().countItem(Items.FILLED_MAP) == 0, "No quill, no map");
            helper.succeed();
        });
    }

    public static void aCartographerWithoutAVillageDrawsNoMap(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, false);
        stock(cartographer);

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(cartographer.getInventory().countItem(Items.FILLED_MAP) == 0, "A villager with no bell has no village to draw");
            helper.assertTrue(cartographer.getInventory().countItem(Items.PAPER) == 8, "...and keeps its paper");
            helper.succeed();
        });
    }

    public static void aCartographerDrawsAMapOfItsVillageFromPaperAndInk(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, true);
        GameTestHelpers.allowWorkXp(cartographer);
        stock(cartographer);

        helper.succeedWhen(() -> {
            MapItemSavedData map = drawn(helper, cartographer);
            helper.assertTrue(cartographer.getInventory().countItem(Items.PAPER) == 0 && cartographer.getInventory().countItem(Items.INK_SAC) == 0,
                    "A map takes 8 paper and an ink sac");
            helper.assertTrue(map.scale == 0 && map.locked, "A novice draws the closest zoom, as a snapshot that stays as drawn");
            BlockPos bell = helper.absolutePos(BELL);
            helper.assertTrue(Math.abs(map.centerX - bell.getX()) <= 1 && Math.abs(map.centerZ - bell.getZ()) <= 1, "The map is centred on the village's bell, at "
                    + map.centerX + " " + map.centerZ + " not " + bell.toShortString());
            helper.assertTrue(painted(map) > 0, "The land is painted");
            helper.assertTrue(count(map, MapPlan.ROAD_COLOR) == 0 && count(map, MapPlan.BORDER_COLOR) == 0, "A novice's map has no roads or borders");
            helper.assertTrue(cartographer.getInventory().getItem(quillSlot(cartographer)).getDamageValue() > 0, "Drawing wears the quill");
            helper.assertTrue(cartographer.getVillagerXp() > 0, "Drawing a map is work experience");
        });
    }

    public static void anApprenticesMapShowsTheRoads(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 2, true);
        layRoad(helper);
        stock(cartographer);

        helper.succeedWhen(() -> {
            MapItemSavedData map = drawn(helper, cartographer);
            helper.assertTrue(map.scale == 1, "An apprentice zooms out once");
            helper.assertTrue(map.colors[MapPlan.index(map, helper.absolutePos(new BlockPos(10, 0, ROAD_Z)))] == MapPlan.ROAD_COLOR, "The road is drawn, painted " + painted(map) + ", center " + map.centerX + "," + map.centerZ + " road abs " + helper.absolutePos(new BlockPos(10, 0, ROAD_Z)).toShortString() + " index " + MapPlan.index(map, helper.absolutePos(new BlockPos(10, 0, ROAD_Z))) + ", floor pixel " + map.colors[MapPlan.index(map, helper.absolutePos(new BlockPos(10, 0, 10)))] + ", table pixel " + map.colors[MapPlan.index(map, helper.absolutePos(TABLE))] + ", road pixels " + count(map, MapPlan.ROAD_COLOR) + ", pixel " + map.colors[MapPlan.index(map, helper.absolutePos(new BlockPos(10, 0, ROAD_Z)))] + " vs " + MapPlan.ROAD_COLOR + ", block " + helper.getBlockState(new BlockPos(10, 0, ROAD_Z)));
            helper.assertTrue(count(map, MapPlan.BORDER_COLOR) == 0, "...but not the village's border yet");
        });
    }

    public static void aJourneymansMapShowsTheVillageBorder(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 3, true);
        stock(cartographer);

        helper.succeedWhen(() -> {
            MapItemSavedData map = drawn(helper, cartographer);
            helper.assertTrue(map.scale == 2, "A journeyman zooms out twice");
            helper.assertTrue(count(map, MapPlan.BORDER_COLOR) > 0, "The border of the village is drawn");
            helper.assertTrue(map.colors[MapPlan.index(map, helper.absolutePos(TABLE))] != MapPlan.JOB_SITE_COLOR, "...but no job sites yet");
        });
    }

    public static void anExpertsMapShowsJobSitesAndHomes(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 4, true);
        stock(cartographer);

        helper.succeedWhen(() -> {
            MapItemSavedData map = drawn(helper, cartographer);
            helper.assertTrue(map.scale == 3, "An expert zooms out three times");
            helper.assertTrue(map.colors[MapPlan.index(map, helper.absolutePos(TABLE))] == MapPlan.JOB_SITE_COLOR, "The cartography table is a job site on the map");
            helper.assertTrue(map.colors[MapPlan.index(map, helper.absolutePos(BED))] == MapPlan.HOME_COLOR, "The bed is a home");
        });
    }

    public static void aMastersMapZoomsOutTheFurthestAndShowsEverything(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 5, true);
        layRoad(helper);
        stock(cartographer);

        helper.succeedWhen(() -> {
            MapItemSavedData map = drawn(helper, cartographer);
            helper.assertTrue(map.scale == 4, "A master draws the widest map");
            helper.assertTrue(count(map, MapPlan.BORDER_COLOR) > 0, "...with the border");
            helper.assertTrue(map.colors[MapPlan.index(map, helper.absolutePos(TABLE))] == MapPlan.JOB_SITE_COLOR, "...and its job sites");
        });
    }

    public static void aMapIsASnapshotOfTheVillageWhenItWasDrawn(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 2, true);
        layRoad(helper);
        stock(cartographer);

        helper.succeedWhen(() -> {
            MapItemSavedData map = drawn(helper, cartographer);
            byte[] before = map.colors.clone();
            helper.setBlock(10, 0, ROAD_Z, Blocks.STONE);
            helper.assertTrue(java.util.Arrays.equals(before, map.colors), "A map doesn't change when the land does");
        });
    }

    public static void aCartographerSellsItsMapToAPlayer(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, true);
        GameTestHelpers.giveStartingPurse(helper, cartographer);
        stock(cartographer);

        helper.succeedWhen(() -> {
            drawn(helper, cartographer);
            Optional<MerchantOffer> offer = ProfessionTrades.currentOffers(helper.getLevel(), cartographer).stream()
                    .filter(candidate -> candidate.getResult().is(Items.FILLED_MAP)).findFirst();
            helper.assertTrue(offer.isPresent(), "The cartographer offers its map, offers "
                    + ProfessionTrades.currentOffers(helper.getLevel(), cartographer).stream().map(o -> o.getResult().getItem() + " x" + o.getResult().getCount() + " uses " + (o.getMaxUses() - o.getUses())).toList()
                    + ", has " + cartographer.getInventory().countItem(Items.FILLED_MAP) + " maps");
            helper.assertTrue(offer.get().getResult().has(DataComponents.MAP_ID), "...the map it drew, not a blank one");
            helper.assertTrue(offer.get().getCostA().count() >= 1, "...for emeralds");
        });
    }

    public static void aCartographerBuysInkSacsFromTheFisherman(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, false);
        cartographer.getInventory().addItem(new ItemStack(Cartographer.INK_AND_QUILL.get()));
        cartographer.getInventory().addItem(new ItemStack(Items.PAPER, 8));
        cartographer.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        BlockPos barrel = new BlockPos(17, 1, 12);
        helper.setBlock(barrel, Blocks.BARREL);
        Villager fisherman = GameTestHelpers.spawnEmployed(helper, barrel.west(), barrel, VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN);
        fisherman.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        fisherman.getInventory().addItem(new ItemStack(Items.INK_SAC, 8));

        helper.succeedWhen(() -> {
            helper.assertTrue(cartographer.getInventory().countItem(Items.INK_SAC) + cartographer.getInventory().countItem(Items.FILLED_MAP) >= 1,
                    "The cartographer buys ink sacs from the fisherman, has " + cartographer.getInventory().countItem(Items.INK_SAC));
            helper.assertTrue(fisherman.getInventory().countItem(Items.EMERALD) >= 1, "The fisherman is paid");
        });
    }

    public static void aTravellingMerchantBuysTheCartographersMaps(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, true);
        stock(cartographer);
        boolean[] sent = {false};

        helper.onEachTick(() -> {
            if (!sent[0] && cartographer.getInventory().countItem(Items.FILLED_MAP) > 0) {
                sent[0] = com.sheepfromheaven.minenow.villager.Exports.sendTrader(helper.getLevel(), cartographer) != null;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sent[0], "A travelling merchant comes for the map");
            helper.assertTrue(cartographer.getInventory().countItem(Items.FILLED_MAP) == 0, "The merchant buys the cartographer's map to take away");
            helper.assertTrue(cartographer.getInventory().countItem(Items.EMERALD) >= 1, "...paying new emeralds into the village");
        });
    }

    public static void aTravellingMerchantSellsGlassBottles(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, false);
        cartographer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));

        var merchant = com.sheepfromheaven.minenow.villager.Exports.sendTrader(helper.getLevel(), cartographer);

        helper.assertTrue(merchant != null, "A travelling merchant comes");
        helper.assertTrue(merchant.getOffers().stream().anyMatch(offer -> offer.getResult().is(Items.GLASS_BOTTLE)), "...selling glass bottles for the cartographer's quills");
        helper.succeed();
    }

    public static void aCartographerKeepsItsOnlyQuillOutOfItsOffers(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, false);
        cartographer.getInventory().addItem(new ItemStack(Cartographer.INK_AND_QUILL.get()));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(ProfessionTrades.currentOffers(helper.getLevel(), cartographer).stream().noneMatch(offer -> offer.getResult().is(Cartographer.INK_AND_QUILL.get())),
                    "A cartographer never offers the quill it works with");
            helper.succeed();
        });
    }

    public static void aCartographerWithoutAQuillBuysOneFromAPlayer(GameTestHelper helper) {
        Villager cartographer = spawn(helper, 1, false);
        cartographer.getInventory().addItem(new ItemStack(Items.EMERALD, 6));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(ProfessionTrades.currentOffers(helper.getLevel(), cartographer).stream()
                    .anyMatch(offer -> offer.getCostA().is(Cartographer.INK_AND_QUILL.get()) && offer.getResult().is(Items.EMERALD)),
                    "A cartographer with no quill pays emeralds for one");
            helper.succeed();
        });
    }

    public static void inkAndQuillRecipesAreOffByDefault(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.INK_AND_QUILL_RECIPES.get(), "The switch is off unless asked for");
        helper.assertTrue(crafted(helper, Items.FEATHER, Items.INK_SAC, Items.GLASS_BOTTLE).isEmpty(), "Players can't craft a quill");
        helper.assertTrue(crafted(helper, Items.BOOK, Items.INK_SAC, Items.FEATHER).filter(stack -> stack.is(Items.WRITABLE_BOOK)).isPresent(), "A book and quill is vanilla's recipe");
        helper.assertTrue(crafted(helper, Items.BOOK, Cartographer.INK_AND_QUILL.get()).isEmpty(), "...and doesn't take the quill");
        helper.succeed();
    }

    public static void inkAndQuillRecipesCanBeSwitchedOn(GameTestHelper helper) {
        boolean before = MineNowConfig.INK_AND_QUILL_RECIPES.get();
        try {
            MineNowConfig.INK_AND_QUILL_RECIPES.set(true);
            helper.assertTrue(crafted(helper, Items.FEATHER, Items.INK_SAC, Items.GLASS_BOTTLE).filter(stack -> stack.is(Cartographer.INK_AND_QUILL.get())).isPresent(),
                    "A feather, an ink sac and a glass bottle make an ink and quill");
            helper.assertTrue(crafted(helper, Items.BOOK, Cartographer.INK_AND_QUILL.get()).filter(stack -> stack.is(Items.WRITABLE_BOOK)).isPresent(),
                    "A book and an ink and quill make a book and quill");
            helper.assertTrue(crafted(helper, Items.BOOK, Items.INK_SAC, Items.FEATHER).isEmpty(), "...and vanilla's recipe is gone");
        } finally {
            MineNowConfig.INK_AND_QUILL_RECIPES.set(before);
        }
        helper.succeed();
    }

    private static Optional<ItemStack> crafted(GameTestHelper helper, net.minecraft.world.item.Item... items) {
        java.util.List<ItemStack> stacks = java.util.Arrays.stream(items).map(ItemStack::new).toList();
        net.minecraft.world.item.crafting.CraftingInput input = net.minecraft.world.item.crafting.CraftingInput.of(3, 1, new java.util.ArrayList<>(stacks.size() == 3 ? stacks : java.util.stream.Stream.concat(stacks.stream(), java.util.stream.Stream.generate(() -> ItemStack.EMPTY).limit(3 - stacks.size())).toList()));
        return helper.getLevel().recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input));
    }

    private static Villager spawn(GameTestHelper helper, int level, boolean inAVillage) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(TABLE, Blocks.CARTOGRAPHY_TABLE);
        Villager cartographer = GameTestHelpers.spawnEmployed(helper, TABLE.east(), TABLE, VillagerProfession.CARTOGRAPHER, PoiTypes.CARTOGRAPHER);
        cartographer.setVillagerData(cartographer.getVillagerData().withLevel(level));
        if (inAVillage) {
            GameTestHelpers.giveHome(helper, cartographer, BED);
            VillageTests.claimBell(helper, cartographer, BELL);
        }
        return cartographer;
    }

    private static void stock(Villager cartographer) {
        cartographer.getInventory().addItem(new ItemStack(Cartographer.INK_AND_QUILL.get()));
        cartographer.getInventory().addItem(new ItemStack(Items.PAPER, 8));
        cartographer.getInventory().addItem(new ItemStack(Items.INK_SAC));
    }

    private static void layRoad(GameTestHelper helper) {
        for (int x = 6; x <= 18; x++) helper.setBlock(x, 0, ROAD_Z, Blocks.DIRT_PATH);
    }

    private static MapItemSavedData drawn(GameTestHelper helper, Villager cartographer) {
        for (int i = 0; i < cartographer.getInventory().getContainerSize(); i++) {
            ItemStack stack = cartographer.getInventory().getItem(i);
            if (!stack.is(Items.FILLED_MAP)) continue;
            MapItemSavedData data = MapItem.getSavedData(stack, helper.getLevel());
            helper.assertTrue(data != null, "The map has its picture");
            return data;
        }
        throw helper.assertionException("The cartographer has drawn no map yet");
    }

    private static int quillSlot(Villager cartographer) {
        for (int i = 0; i < cartographer.getInventory().getContainerSize(); i++) {
            if (cartographer.getInventory().getItem(i).is(Cartographer.INK_AND_QUILL.get())) return i;
        }
        return 0;
    }

    private static int count(MapItemSavedData map, byte color) {
        int found = 0;
        for (byte pixel : map.colors) if (pixel == color) found++;
        return found;
    }

    private static int painted(MapItemSavedData map) {
        int found = 0;
        for (byte pixel : map.colors) if (pixel != 0) found++;
        return found;
    }
}

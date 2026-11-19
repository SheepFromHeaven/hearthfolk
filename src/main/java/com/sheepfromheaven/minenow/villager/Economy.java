package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.List;
import java.util.Optional;

/** A villager's goods are what it carries plus what is in its home chest and its workshop chests. */
public final class Economy {
    public static final int STARTING_PURSE = 6;
    public static final int FOOD_MONEY = 2;

    private Economy() {}

    public static int emeralds(ServerLevel level, Villager villager) {
        return count(level, villager, Items.EMERALD);
    }

    public static int count(ServerLevel level, Villager villager, Item item) {
        int total = villager.getInventory().countItem(item);
        for (Container chest : chests(level, villager)) total += chest.countItem(item);
        return total;
    }

    public static java.util.Set<Item> items(ServerLevel level, Villager villager) {
        java.util.Set<Item> items = new java.util.LinkedHashSet<>();
        villager.getInventory().getItems().stream().filter(stack -> !stack.isEmpty()).forEach(stack -> items.add(stack.getItem()));
        for (Container chest : chests(level, villager)) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (!chest.getItem(i).isEmpty()) items.add(chest.getItem(i).getItem());
            }
        }
        return items;
    }

    /** Granted once, when the villager first takes a job, so a village's money grows with the villagers working in it. */
    public static void grantStartingPurseOnce(ServerLevel level, Villager villager) {
        if (villager.getExistingData(ModVillagers.PURSE_GRANTED).orElse(false)) return;
        villager.setData(ModVillagers.PURSE_GRANTED, true);
        give(level, villager, new ItemStack(Items.EMERALD, STARTING_PURSE));
    }

    /**
     * What a villager won't spend on what players sell it: its food money, plus the price of the cheapest tool of its
     * trade, so a player selling and never buying can't leave a village unable to eat or work.
     */
    public static int reserve(Villager villager) {
        return FOOD_MONEY + TradeTable.forVillager(villager)
                .flatMap(table -> table.tools().stream().map(TradeTable.Tool::price).min(Integer::compare))
                .orElse(0);
    }

    /** The emeralds a villager may pay a player for supplies. */
    public static int spendable(ServerLevel level, Villager villager) {
        return Math.max(0, emeralds(level, villager) - reserve(villager));
    }

    /**
     * Whether villagers pay each other ({@code villagersPayEachOther}). Off, they take what they need from each other
     * for free; players still pay villagers and are paid by them as always.
     */
    public static boolean villagersPay() {
        return com.sheepfromheaven.minenow.MineNowConfig.enabled(com.sheepfromheaven.minenow.MineNowConfig.Feature.VILLAGERS_PAY_EACH_OTHER);
    }

    /**
     * Pays {@code emeralds} for another villager's goods or work, to {@code to} (null: they leave the village with a
     * travelling merchant). Nothing moves while villagers don't pay each other. Returns what was paid.
     */
    public static int pay(ServerLevel level, Villager from, @org.jspecify.annotations.Nullable Villager to, int emeralds) {
        if (!villagersPay() || emeralds <= 0) return 0;
        int paid = take(level, from, Items.EMERALD, emeralds);
        if (to != null) give(level, to, new ItemStack(Items.EMERALD, paid));
        return paid;
    }

    /** What a villager may spend on another villager's goods out of {@code budget}: no limit while villagers don't pay each other. */
    public static int villagerBudget(int budget) {
        return villagersPay() ? budget : Integer.MAX_VALUE;
    }

    /** Chests a villager's goods may sit in: none while a merchant is on tour, since they're days away. */
    private static List<BlockPos> chestPositions(ServerLevel level, Villager villager) {
        return com.sheepfromheaven.minenow.merchant.Merchant.onTour(villager) ? List.of() : Workshop.allChests(level, villager);
    }

    public static int take(ServerLevel level, Villager villager, Item item, int amount) {
        int taken = takeFrom(villager.getInventory(), item, amount);
        for (Container pack : com.sheepfromheaven.minenow.merchant.Merchant.packs(level, villager)) taken += takeFrom(pack, item, amount - taken);
        for (BlockPos pos : chestPositions(level, villager)) {
            if (taken >= amount) break;
            Container chest = HomeStorage.chestContainer(level, pos);
            if (chest == null || chest.countItem(item) == 0) continue;
            StorageTheft.check(level, villager, pos);
            taken += takeFrom(chest, item, amount - taken);
            chest.setChanged();
            StorageTheft.recordContents(level, pos);
        }
        return taken;
    }

    /** A copy of one of the item as it is, from its inventory first, then its chests, without taking it: what it would hand over for a sale (a filled map is the picture it drew). */
    public static ItemStack peekOne(ServerLevel level, Villager villager, Item item) {
        for (ItemStack stack : villager.getInventory().getItems()) {
            if (stack.is(item)) return stack.copyWithCount(1);
        }
        for (Container chest : chests(level, villager)) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(item)) return chest.getItem(i).copyWithCount(1);
            }
        }
        return new ItemStack(item);
    }

    /** One of the item as it is (dye, wear and all), from its inventory first, then its chests; empty if it has none. */
    public static ItemStack takeOne(ServerLevel level, Villager villager, Item item) {
        ItemStack taken = takeOneFrom(villager.getInventory(), item);
        for (Container pack : com.sheepfromheaven.minenow.merchant.Merchant.packs(level, villager)) {
            if (taken.isEmpty()) taken = takeOneFrom(pack, item);
        }
        for (BlockPos pos : chestPositions(level, villager)) {
            if (!taken.isEmpty()) break;
            Container chest = HomeStorage.chestContainer(level, pos);
            if (chest == null || chest.countItem(item) == 0) continue;
            StorageTheft.check(level, villager, pos);
            taken = takeOneFrom(chest, item);
            chest.setChanged();
            StorageTheft.recordContents(level, pos);
        }
        return taken;
    }

    /** One item exactly like {@code template} (same components: this enchanted book, not any), from its inventory first, then its chests; empty if it has none. */
    public static ItemStack takeExact(ServerLevel level, Villager villager, ItemStack template) {
        ItemStack taken = takeExactFrom(villager.getInventory(), template);
        for (Container pack : com.sheepfromheaven.minenow.merchant.Merchant.packs(level, villager)) {
            if (taken.isEmpty()) taken = takeExactFrom(pack, template);
        }
        for (BlockPos pos : chestPositions(level, villager)) {
            if (!taken.isEmpty()) break;
            Container chest = HomeStorage.chestContainer(level, pos);
            if (chest == null || chest.countItem(template.getItem()) == 0) continue;
            StorageTheft.check(level, villager, pos);
            taken = takeExactFrom(chest, template);
            chest.setChanged();
            StorageTheft.recordContents(level, pos);
        }
        return taken;
    }

    /** Every stack of {@code item} it holds, inventory then chests, each as a copy, so goods that differ (enchanted books) can be told apart. */
    public static List<ItemStack> stacks(ServerLevel level, Villager villager, Item item) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        villager.getInventory().getItems().stream().filter(stack -> stack.is(item)).forEach(stack -> stacks.add(stack.copy()));
        for (Container chest : chests(level, villager)) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(item)) stacks.add(chest.getItem(i).copy());
            }
        }
        return stacks;
    }

    private static ItemStack takeExactFrom(Container container, ItemStack template) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameComponents(container.getItem(i), template)) return container.removeItem(i, 1);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack takeOneFrom(Container container, Item item) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) return container.removeItem(i, 1);
        }
        return ItemStack.EMPTY;
    }

    /** Into its inventory, then the chest the item belongs in (food and emeralds home, trade goods the workshop), then any. */
    public static void give(ServerLevel level, Villager villager, ItemStack stack) {
        if (Unloading.isTool(villager, stack) && !Tools.has(villager) && !Unloading.hasEmptySlot(villager)) makeRoom(level, villager);
        ItemStack rest = com.sheepfromheaven.minenow.merchant.Merchant.pack(level, villager, villager.getInventory().addItem(stack));
        putInChests(level, villager, rest);
    }

    /** Moves one stack (not a tool) from its pockets into its chests, so the tool of its trade it gets is carried, not stored where its work never looks. */
    private static void makeRoom(ServerLevel level, Villager villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || Unloading.isTool(villager, stack)) continue;
            inventory.setItem(i, ItemStack.EMPTY);
            ItemStack rest = stack;
            for (BlockPos pos : Workshop.allChests(level, villager)) {
                if (rest.isEmpty()) break;
                Container chest = HomeStorage.chestContainer(level, pos);
                if (chest == null) continue;
                StorageTheft.check(level, villager, pos);
                rest = HopperBlockEntity.addItem(null, chest, rest, null);
                StorageTheft.recordContents(level, pos);
            }
            if (rest.isEmpty()) return;
            inventory.setItem(i, rest);
        }
    }

    private static void putInChests(ServerLevel level, Villager villager, ItemStack rest) {
        List<BlockPos> chests = com.sheepfromheaven.minenow.merchant.Merchant.onTour(villager) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(Workshop.allChests(level, villager));
        boolean personal = Workshop.personal(rest);
        chests.sort(java.util.Comparator.comparing(pos -> Workshop.isWorkshopChest(level, villager, pos) == personal));
        for (BlockPos pos : chests) {
            if (rest.isEmpty()) break;
            Container chest = HomeStorage.chestContainer(level, pos);
            if (chest == null) continue;
            StorageTheft.check(level, villager, pos);
            rest = HopperBlockEntity.addItem(null, chest, rest, null);
            StorageTheft.recordContents(level, pos);
        }
        if (!rest.isEmpty()) villager.spawnAtLocation(level, rest);
    }

    private static int takeFrom(Container container, Item item, int amount) {
        int taken = 0;
        for (int i = 0; i < container.getContainerSize() && taken < amount; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.is(item)) continue;
            taken += container.removeItem(i, amount - taken).getCount();
        }
        return taken;
    }

    static List<Container> chests(ServerLevel level, Villager villager) {
        List<Container> chests = new java.util.ArrayList<>(com.sheepfromheaven.minenow.merchant.Merchant.packs(level, villager));
        if (!com.sheepfromheaven.minenow.merchant.Merchant.onTour(villager)) {
            Workshop.allChests(level, villager).stream().map(pos -> HomeStorage.chestContainer(level, pos)).filter(java.util.Objects::nonNull).forEach(chests::add);
        }
        return chests;
    }
}

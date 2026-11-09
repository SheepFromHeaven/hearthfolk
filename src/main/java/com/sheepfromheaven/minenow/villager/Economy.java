package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
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

    public static int take(ServerLevel level, Villager villager, Item item, int amount) {
        int taken = takeFrom(villager.getInventory(), item, amount);
        for (BlockPos pos : Workshop.allChests(level, villager)) {
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

    /** Into its inventory, then the chest the item belongs in (food and emeralds home, trade goods the workshop), then any. */
    public static void give(ServerLevel level, Villager villager, ItemStack stack) {
        ItemStack rest = villager.getInventory().addItem(stack);
        List<BlockPos> chests = new java.util.ArrayList<>(Workshop.allChests(level, villager));
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

    private static List<Container> chests(ServerLevel level, Villager villager) {
        return Workshop.allChests(level, villager).stream().map(pos -> HomeStorage.chestContainer(level, pos)).filter(java.util.Objects::nonNull).toList();
    }
}

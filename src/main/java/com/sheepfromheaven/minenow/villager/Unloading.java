package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.farmer.Crops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * What a working villager keeps on it and what it carries to its home chest: one tool of its trade, supplies up to
 * their {@code max}, seeds and villager food up to {@link #KEEP_PER_ITEM}; everything else is deposited.
 */
public final class Unloading {
    public static final int KEEP_PER_ITEM = 16;
    public static final int UNLOAD_AT = 64;

    private Unloading() {}

    public static boolean isTool(Villager villager, ItemStack stack) {
        return !stack.isEmpty() && TradeTable.forVillager(villager).map(table -> table.isToolItem(stack)).orElse(false);
    }

    public static boolean ownsTool(ServerLevel level, Villager villager) {
        if (villager.getInventory().hasAnyMatching(stack -> isTool(villager, stack))) return true;
        Container chest = HomeStorage.storageIn(level, villager).map(pos -> HomeStorage.chestContainer(level, pos)).orElse(null);
        if (chest == null) return false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (isTool(villager, chest.getItem(i))) return true;
        }
        return false;
    }

    public static int keepLimit(Villager villager, ItemStack stack) {
        if (isTool(villager, stack)) return 1;
        int supplyMax = TradeTable.forVillager(villager).flatMap(table -> table.supply(stack.getItem())).map(TradeTable.Supply::max).orElse(0);
        if (supplyMax > 0) return supplyMax;
        int keepMax = TradeTable.forVillager(villager).map(table -> table.keepMax(stack)).orElse(0);
        if (keepMax > 0) return keepMax;
        if (Crops.isSeed(stack) || stack.has(DataComponents.VILLAGER_FOOD)) return KEEP_PER_ITEM;
        return 0;
    }

    public static int depositableCount(Villager villager) {
        Map<Item, Integer> seen = new HashMap<>();
        int total = 0;
        for (ItemStack stack : villager.getInventory().getItems()) {
            if (stack.isEmpty()) continue;
            int limit = keepLimit(villager, stack);
            int before = seen.getOrDefault(stack.getItem(), 0);
            seen.put(stack.getItem(), before + stack.getCount());
            total += Math.max(0, before + stack.getCount() - Math.max(before, limit));
        }
        return total;
    }

    public static boolean hasEmptySlot(Villager villager) {
        return villager.getInventory().getItems().stream().anyMatch(ItemStack::isEmpty);
    }

    public static boolean shouldUnload(Villager villager) {
        int depositable = depositableCount(villager);
        return depositable > 0 && (depositable >= UNLOAD_AT || !hasEmptySlot(villager));
    }

    /** Deposits everything beyond the keep limits into the chest at {@code pos}, settling theft bookkeeping around it. */
    public static void deposit(ServerLevel level, Villager villager, BlockPos pos) {
        Container chest = HomeStorage.chestContainer(level, pos);
        if (chest == null) return;
        StorageTheft.check(level, villager, pos);
        SimpleContainer inventory = villager.getInventory();
        Map<Item, Integer> kept = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            int alreadyKept = kept.getOrDefault(stack.getItem(), 0);
            int keep = Math.min(stack.getCount(), Math.max(0, keepLimit(villager, stack) - alreadyKept));
            kept.put(stack.getItem(), alreadyKept + keep);
            int give = stack.getCount() - keep;
            if (give <= 0) continue;
            ItemStack rest = HopperBlockEntity.addItem(null, chest, stack.copyWithCount(give), null);
            stack.setCount(keep + rest.getCount());
            if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
        }
        StorageTheft.recordContents(level, pos);
        level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
    }
}

package com.sheepfromheaven.minenow.villager;

import net.minecraft.world.level.block.ChestBlock;

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
import java.util.WeakHashMap;
import java.util.Optional;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Map;

/**
 * What a working villager keeps on it and what it carries to its chests: one tool of its trade, supplies up to their
 * {@code max}, seeds and villager food up to {@link #KEEP_PER_ITEM}; everything else is deposited, trade goods in its
 * workshop chests and food and emeralds at home ({@link #unloadTarget}).
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
        for (BlockPos pos : Workshop.allChests(level, villager)) {
            Container chest = HomeStorage.chestContainer(level, pos);
            if (chest == null) continue;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (isTool(villager, chest.getItem(i))) return true;
            }
        }
        return false;
    }

    /**
     * Where to carry what it has to unload: trade goods to the nearest workshop chest with room, food and emeralds (and
     * trade goods with no workshop room) to its home chest; whichever it has, if it has only one kind of chest.
     */
    public static Optional<BlockPos> unloadTarget(ServerLevel level, Villager villager) {
        List<ItemStack> surplus = surplus(villager);
        if (surplus.isEmpty()) return Optional.empty();
        Optional<BlockPos> home = HomeStorage.storageIn(level, villager).filter(pos -> level.getBlockState(pos).getBlock() instanceof ChestBlock);
        List<ItemStack> trade = surplus.stream().filter(stack -> !Workshop.personal(stack)).toList();
        Optional<BlockPos> workshop = nearestWorkshopWithRoom(level, villager, trade);
        if (workshop.isPresent()) return workshop;
        if (home.isPresent()) return home;
        return nearestWorkshopWithRoom(level, villager, surplus);
    }

    /** Whether any of its chests has room for something it has to unload. */
    public static boolean chestHasRoomForSurplus(ServerLevel level, Villager villager) {
        List<ItemStack> surplus = surplus(villager);
        return Workshop.allChests(level, villager).stream()
                .filter(pos -> level.getBlockState(pos).getBlock() instanceof ChestBlock)
                .anyMatch(pos -> surplus.stream().anyMatch(stack -> hasRoom(HomeStorage.chestContainer(level, pos), stack)));
    }

    private static Optional<BlockPos> nearestWorkshopWithRoom(ServerLevel level, Villager villager, List<ItemStack> goods) {
        if (goods.isEmpty()) return Optional.empty();
        return Workshop.chestsOf(level, villager).stream()
                .filter(pos -> level.getBlockState(pos).getBlock() instanceof ChestBlock)
                .filter(pos -> goods.stream().anyMatch(stack -> hasRoom(HomeStorage.chestContainer(level, pos), stack)))
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(villager.position())));
    }

    private static boolean hasRoom(Container chest, ItemStack stack) {
        if (chest == null) return false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot.isEmpty() || (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize())) return true;
        }
        return false;
    }

    /** What it carries beyond its keep limits, as stacks to unload. */
    private static List<ItemStack> surplus(Villager villager) {
        List<ItemStack> surplus = new ArrayList<>();
        Map<Item, Integer> kept = new HashMap<>();
        for (ItemStack stack : villager.getInventory().getItems()) {
            if (stack.isEmpty()) continue;
            int alreadyKept = kept.getOrDefault(stack.getItem(), 0);
            int keep = Math.min(stack.getCount(), Math.max(0, keepLimit(villager, stack) - alreadyKept));
            kept.put(stack.getItem(), alreadyKept + keep);
            if (stack.getCount() > keep) surplus.add(stack.copyWithCount(stack.getCount() - keep));
        }
        return surplus;
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

    /** Villagers that started unloading at one chest and have goods left for another: they finish the round. */
    private static final Map<Villager, Long> UNLOADING = new WeakHashMap<>();
    private static final int ROUND_TICKS = 600;

    public static boolean shouldUnload(Villager villager) {
        int depositable = depositableCount(villager);
        if (depositable == 0) return false;
        Long since = UNLOADING.get(villager);
        if (since != null && villager.level().getGameTime() - since <= ROUND_TICKS) return true;
        return depositable >= UNLOAD_AT || !hasEmptySlot(villager);
    }

    /**
     * Deposits everything beyond the keep limits that belongs in the chest at {@code pos} (trade goods in a workshop
     * chest, food and emeralds at home, either where it has no chest of the other kind or no room there), settling
     * theft bookkeeping around it.
     */
    /**
     * Takes a tool of its trade back out of the chest when it has none on it, and supplies of its trade up to what it
     * keeps on it: they come in lots bigger than that
     * (sticks 32 to an emerald, the miner keeps 16), the rest is unloaded, and its work only uses what it carries.
     */
    private static void restock(Villager villager, Container chest) {
        if (!Tools.has(villager)) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (!isTool(villager, chest.getItem(i))) continue;
                ItemStack rest = villager.getInventory().addItem(chest.removeItem(i, 1));
                if (!rest.isEmpty()) chest.setItem(i, rest);
                break;
            }
        }
        TradeTable.forVillager(villager).ifPresent(table -> {
            for (TradeTable.Supply supply : table.suppliesUpTo(villager.getVillagerData().level())) {
                int want = supply.max() - villager.getInventory().countItem(supply.item());
                for (int i = 0; i < chest.getContainerSize() && want > 0; i++) {
                    if (!chest.getItem(i).is(supply.item())) continue;
                    ItemStack taken = chest.removeItem(i, want);
                    want -= taken.getCount();
                    ItemStack rest = villager.getInventory().addItem(taken);
                    if (!rest.isEmpty()) {
                        HopperBlockEntity.addItem(null, chest, rest, null);
                        return;
                    }
                }
            }
        });
    }

    public static void deposit(ServerLevel level, Villager villager, BlockPos pos) {
        Container chest = HomeStorage.chestContainer(level, pos);
        if (chest == null) return;
        boolean workshop = Workshop.isWorkshopChest(level, villager, pos);
        boolean hasHome = HomeStorage.storageIn(level, villager).isPresent();
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
            boolean personal = Workshop.personal(stack);
            boolean belongs = workshop ? !personal || !hasHome : personal || nearestWorkshopWithRoom(level, villager, List.of(stack)).isEmpty();
            if (!belongs) continue;
            ItemStack rest = HopperBlockEntity.addItem(null, chest, stack.copyWithCount(give), null);
            stack.setCount(keep + rest.getCount());
            if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
        }
        restock(villager, chest);
        StorageTheft.recordContents(level, pos);
        level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        if (depositableCount(villager) > 0 && unloadTarget(level, villager).filter(next -> !next.equals(pos)).isPresent()) UNLOADING.put(villager, level.getGameTime());
        else UNLOADING.remove(villager);
    }
}

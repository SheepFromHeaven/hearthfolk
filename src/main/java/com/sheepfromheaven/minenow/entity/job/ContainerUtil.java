package com.sheepfromheaven.minenow.entity.job;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Small helpers for jobs that need to move items into/out of world containers (barrels, chests,
 * ...). Every access is bracketed with {@link Container#startOpen}/{@code stopOpen} so barrels and
 * chests play their open/close sound and animate their lid, exactly as if a player had opened them
 * (containers without that behavior, like a smoker, simply no-op on the default implementation).
 */
public final class ContainerUtil {
    private ContainerUtil() {}

    /** Removes and returns one whole stack of the given item from the container, or {@link ItemStack#EMPTY} if none found. */
    public static ItemStack extractStack(Container container, ContainerUser user, Item item) {
        return extractFirst(container, user, stack -> stack.is(item));
    }

    /** Removes and returns the first stack matching the predicate, or {@link ItemStack#EMPTY} if none found. */
    public static ItemStack extractFirst(Container container, ContainerUser user, Predicate<ItemStack> matcher) {
        container.startOpen(user);
        try {
            for (int i = 0; i < container.getContainerSize(); i++) {
                var stack = container.getItem(i);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    return container.removeItem(i, stack.getCount());
                }
            }
            return ItemStack.EMPTY;
        } finally {
            container.stopOpen(user);
        }
    }

    /** Removes and returns a single item from the first stack matching the predicate, or {@link ItemStack#EMPTY} if none found. */
    public static ItemStack extractOne(Container container, ContainerUser user, Predicate<ItemStack> matcher) {
        container.startOpen(user);
        try {
            for (int i = 0; i < container.getContainerSize(); i++) {
                var stack = container.getItem(i);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    return container.removeItem(i, 1);
                }
            }
            return ItemStack.EMPTY;
        } finally {
            container.stopOpen(user);
        }
    }

    /** Inserts as much of the stack as fits, mutating it in place. Returns the leftover (possibly empty). */
    public static ItemStack insertItem(Container container, ContainerUser user, ItemStack stack) {
        container.startOpen(user);
        try {
            for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
                var existing = container.getItem(i);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                    int space = Math.min(container.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount();
                    if (space > 0) {
                        int move = Math.min(space, stack.getCount());
                        existing.grow(move);
                        stack.shrink(move);
                    }
                }
            }
            for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
                if (container.getItem(i).isEmpty()) {
                    int move = Math.min(stack.getCount(), container.getMaxStackSize(stack));
                    container.setItem(i, stack.split(move));
                }
            }
            container.setChanged();
            return stack;
        } finally {
            container.stopOpen(user);
        }
    }

    /** First position among {@code positions} whose container holds a stack matching {@code matcher}, without removing anything. */
    @Nullable
    public static BlockPos findContainerWith(ServerLevel level, Iterable<BlockPos> positions, Predicate<ItemStack> matcher) {
        for (var pos : positions) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                var stack = container.getItem(i);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    return pos;
                }
            }
        }
        return null;
    }

    /** First position among {@code positions} whose container has room for (at least part of) {@code stack}. */
    @Nullable
    public static BlockPos findContainerAccepting(ServerLevel level, Iterable<BlockPos> positions, ItemStack stack) {
        for (var pos : positions) {
            if (level.getBlockEntity(pos) instanceof Container container && canAccept(container, stack)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean canAccept(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            var existing = container.getItem(i);
            if (existing.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < Math.min(container.getMaxStackSize(existing), existing.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }
}

package com.sheepfromheaven.minenow.entity.job;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Small helpers for jobs that need to move items into/out of world containers (barrels, chests, ...). */
public final class ContainerUtil {
    private ContainerUtil() {}

    /** Removes and returns one whole stack of the given item from the container, or {@link ItemStack#EMPTY} if none found. */
    public static ItemStack extractStack(Container container, Item item) {
        return extractFirst(container, stack -> stack.is(item));
    }

    /** Removes and returns the first stack matching the predicate, or {@link ItemStack#EMPTY} if none found. */
    public static ItemStack extractFirst(Container container, Predicate<ItemStack> matcher) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (!stack.isEmpty() && matcher.test(stack)) {
                return container.removeItem(i, stack.getCount());
            }
        }
        return ItemStack.EMPTY;
    }

    /** Removes and returns a single item from the first stack matching the predicate, or {@link ItemStack#EMPTY} if none found. */
    public static ItemStack extractOne(Container container, Predicate<ItemStack> matcher) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (!stack.isEmpty() && matcher.test(stack)) {
                return container.removeItem(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Inserts as much of the stack as fits, mutating it in place. Returns the leftover (possibly empty). */
    public static ItemStack insertItem(Container container, ItemStack stack) {
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
    }
}

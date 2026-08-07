package com.sheepfromheaven.minenow.structure.inventory;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.storage.item.FilterData;
import com.sheepfromheaven.minenow.storage.item.FilterItem;
import com.sheepfromheaven.minenow.core.ModDataComponents;
import com.sheepfromheaven.minenow.storage.StorageItems;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Exposes a {@link DominionStructure}'s item filter as a single-slot {@link Container} for menu
 * use, mirroring {@link EquipmentSlotContainer}: there's no separate backing item list, the
 * "contents" are always a live encoding of the structure's current filter fields. Dropping a
 * {@link FilterItem} in applies its {@link FilterData} to the structure; taking it back out (or
 * clearing it) hands over a fresh Filter item pre-loaded with whatever was configured, and clears
 * the structure's filter.
 */
public final class StructureFilterContainer implements Container {
    private final DominionStructure structure;
    private final DominionSavedData data;

    public StructureFilterContainer(DominionStructure structure, DominionSavedData data) {
        this.structure = structure;
        this.data = data;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return structure.getFilterTemplates().isEmpty();
    }

    @Override
    public ItemStack getItem(int i) {
        return encodeFilterItem();
    }

    @Override
    public ItemStack removeItem(int i, int count) {
        var current = getItem(i);
        if (current.isEmpty()) return ItemStack.EMPTY;
        clearFilter();
        return current;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        var current = getItem(i);
        clearFilter();
        return current;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        if (stack.isEmpty()) {
            clearFilter();
            return;
        }
        var filterData = stack.getOrDefault(ModDataComponents.FILTER_DATA, FilterData.EMPTY);
        structure.setFilter(filterData.templates(), filterData.whitelist(), filterData.matchMetadata());
        data.setDirty();
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        clearFilter();
    }

    private void clearFilter() {
        structure.setFilter(List.of(), true, false);
        data.setDirty();
    }

    private ItemStack encodeFilterItem() {
        if (structure.getFilterTemplates().isEmpty()) return ItemStack.EMPTY;
        var stack = StorageItems.FILTER.get().getDefaultInstance();
        stack.set(ModDataComponents.FILTER_DATA, new FilterData(
                structure.getFilterTemplates(), structure.isFilterWhitelist(), structure.isFilterMatchMetadata()));
        return stack;
    }
}

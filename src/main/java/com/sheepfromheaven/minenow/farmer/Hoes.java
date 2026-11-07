package com.sheepfromheaven.minenow.farmer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class Hoes {
    private Hoes() {}

    public static boolean isHoe(ItemStack stack) {
        return stack.is(ItemTags.HOES);
    }

    private static final float HARVEST_WORK = 80.0F;
    private static final int MIN_HARVEST_TICKS = 5;

    /** Uses the hoe's own mining speed (wood 2 ... gold 12) plus Efficiency the way vanilla mining does. */
    static int harvestTicks(ItemStack hoe, int efficiencyLevel) {
        Tool tool = hoe.get(DataComponents.TOOL);
        float speed = tool == null ? 1.0F : tool.rules().stream()
                .flatMap(rule -> rule.speed().stream())
                .max(Float::compare)
                .orElse(tool.defaultMiningSpeed());
        if (efficiencyLevel > 0) speed += efficiencyLevel * efficiencyLevel + 1;
        return Math.max(MIN_HARVEST_TICKS, Math.round(HARVEST_WORK / speed));
    }

    static int harvestTicks(ServerLevel level, Villager villager) {
        int slot = slot(villager.getInventory());
        if (slot < 0) return MIN_HARVEST_TICKS;
        ItemStack hoe = villager.getInventory().getItem(slot);
        var efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        return harvestTicks(hoe, EnchantmentHelper.getItemEnchantmentLevel(efficiency, hoe));
    }

    public static boolean has(Villager villager) {
        return slot(villager.getInventory()) >= 0;
    }

    static int slot(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (isHoe(container.getItem(i))) return i;
        }
        return -1;
    }

    static void wear(ServerLevel level, Villager villager) {
        SimpleContainer inventory = villager.getInventory();
        int slot = slot(inventory);
        if (slot < 0) return;
        ItemStack hoe = inventory.getItem(slot);
        hoe.hurtAndBreak(1, level, villager, broken ->
                level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 1.0F));
        if (hoe.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        showInHand(villager, has(villager));
    }

    /** The real hoe stays in the inventory; vanilla's trade display overwrites the main hand, so only a copy is shown there. */
    static void showInHand(Villager villager, boolean show) {
        int slot = slot(villager.getInventory());
        if (show && slot >= 0) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, villager.getInventory().getItem(slot).copy());
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        } else if (isHoe(villager.getMainHandItem())) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }
}

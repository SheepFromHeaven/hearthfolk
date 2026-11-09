package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.armorer.Armorer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.List;

/**
 * A villager's own gear: armor it wears all the time (in its armor slots, so vanilla's protection applies) and a weapon
 * it keeps at its side ({@link ModVillagers#SIDEARM}) and only draws to fight ({@link PanicOrFight}): a sword, or for
 * the one in three who are archers ({@link ModVillagers#ARCHER}) a bow with arrows in its quiver ({@link ModVillagers#QUIVER}).
 * It buys gear from the crafting villagers with what it has saved above its reserve, keeping {@link #SAVINGS} to spare:
 * armor first, the most protective piece it can afford for an empty slot, its weapon only once all four slots are
 * filled, and an archer tops its quiver up to {@link #QUIVER_FULL} once it's down to {@link #QUIVER_LOW}. Armor wears
 * down when it's hit, as a player's does, and everything drops when it dies.
 */
public final class Gear {
    public static final int SAVINGS = 4;
    public static final int QUIVER_LOW = 8;
    static final int QUIVER_FULL = 32;
    private static final int ARCHER_ONE_IN = 3;
    static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.FEET);

    private Gear() {}

    public static ItemStack sidearm(Villager villager) {
        return villager.getData(ModVillagers.SIDEARM);
    }

    public static ItemStack quiver(Villager villager) {
        return villager.getData(ModVillagers.QUIVER);
    }

    /** About one villager in three is an archer, for life. */
    static boolean prefersBow(java.util.UUID uuid) {
        return Math.floorMod(Long.hashCode(uuid.getMostSignificantBits() * 31 + uuid.getLeastSignificantBits()), ARCHER_ONE_IN) == 0;
    }

    static boolean isArcher(Villager villager) {
        return villager.getData(ModVillagers.ARCHER);
    }

    static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BowItem;
    }

    /** Puts on a piece of armor, or keeps a sword at its side. */
    public static void wear(Villager villager, ItemStack stack) {
        EquipmentSlot slot = Armorer.armorSlot(stack);
        if (slot != null && ARMOR.contains(slot)) {
            villager.setItemSlot(slot, stack);
            villager.setGuaranteedDrop(slot);
        } else if (stack.is(ItemTags.SWORDS) || isBow(stack)) {
            villager.setData(ModVillagers.SIDEARM, stack);
        } else if (stack.is(Items.ARROW)) {
            ItemStack quiver = quiver(villager);
            villager.setData(ModVillagers.QUIVER, stack.copyWithCount(Math.min(stack.getMaxStackSize(), quiver.getCount() + stack.getCount())));
        }
    }

    /** Whether it would buy this now: armor for an empty slot, or a sword once it's fully armored and has none. */
    static boolean wants(Villager villager, ItemStack stack) {
        EquipmentSlot slot = Armorer.armorSlot(stack);
        boolean bare = ARMOR.stream().anyMatch(armor -> villager.getItemBySlot(armor).isEmpty());
        if (slot != null && ARMOR.contains(slot)) return villager.getItemBySlot(slot).isEmpty();
        if (bare) return false;
        if (!isArcher(villager)) return stack.is(ItemTags.SWORDS) && sidearm(villager).isEmpty();
        if (isBow(stack)) return sidearm(villager).isEmpty();
        return stack.is(Items.ARROW) && isBow(sidearm(villager)) && quiver(villager).getCount() < QUIVER_LOW;
    }

    /** How many of it it would take: arrows to fill its quiver, one of anything else. */
    static int room(Villager villager, ItemStack stack) {
        return stack.is(Items.ARROW) ? QUIVER_FULL - quiver(villager).getCount() : 1;
    }

    /** How much better a piece is than another of its kind. */
    static double worth(ItemStack stack) {
        if (stack.is(ItemTags.SWORDS)) return Weapons.attackDamage(stack);
        return isBow(stack) || stack.is(Items.ARROW) ? 1.0 : Armorer.protection(stack);
    }

    /** What it may spend on gear. */
    static int budget(ServerLevel level, Villager villager) {
        return Economy.spendable(level, villager) - SAVINGS;
    }

    /** A sword at its side, or a bow and arrows among its goods. */
    static boolean isArmed(Villager villager) {
        ItemStack sidearm = sidearm(villager);
        if (sidearm.is(ItemTags.SWORDS)) return true;
        boolean bow = isBow(sidearm) || villager.getInventory().hasAnyMatching(Gear::isBow);
        return bow && (!quiver(villager).isEmpty() || villager.getInventory().hasAnyMatching(stack -> stack.is(Items.ARROW)));
    }


    /** Armor wears down with the blows it takes, the way a player's does. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide() || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        int wear = (int) Math.max(1.0F, event.getOriginalDamage() / 4.0F);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack piece = villager.getItemBySlot(slot);
            if (!piece.isEmpty()) piece.hurtAndBreak(wear, villager, slot);
        }
    }

    /** Its sword or bow and its arrows drop with it; its armor already does (a guaranteed drop). */
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        for (ItemStack carried : takeCarried(villager)) {
            event.getDrops().add(new ItemEntity(villager.level(), villager.getX(), villager.getY(), villager.getZ(), carried));
        }
    }

    private static List<ItemStack> takeCarried(Villager villager) {
        List<ItemStack> carried = java.util.stream.Stream.of(sidearm(villager), quiver(villager)).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        villager.setData(ModVillagers.SIDEARM, ItemStack.EMPTY);
        villager.setData(ModVillagers.QUIVER, ItemStack.EMPTY);
        return carried;
    }

    /** A zombified villager keeps its armor (vanilla keeps equipment) but drops the weapon at its side and its arrows. */
    public static void onConversion(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        takeCarried(villager).forEach(carried -> villager.spawnAtLocation(level, carried));
    }
}

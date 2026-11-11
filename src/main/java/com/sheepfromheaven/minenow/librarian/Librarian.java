package com.sheepfromheaven.minenow.librarian;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Optional;

/** The vanilla librarian at its lectern: the enchanted books it binds. */
public final class Librarian {
    private Librarian() {}

    /** A plain book enchanted the way a table would at the librarian's power, from {@link LibrarianSkill#bindsTradeableEnchantments} on with what traders sell. */
    public static ItemStack enchantedBook(ServerLevel level, RandomSource random, int villagerLevel) {
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var allowed = LibrarianSkill.bindsTradeableEnchantments(villagerLevel) ? EnchantmentTags.TRADEABLE : EnchantmentTags.IN_ENCHANTING_TABLE;
        return EnchantmentHelper.enchantItem(random, new ItemStack(Items.BOOK), LibrarianSkill.power(villagerLevel), level.registryAccess(), Optional.of(enchantments.getOrThrow(allowed)));
    }
}

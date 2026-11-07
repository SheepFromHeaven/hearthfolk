package com.sheepfromheaven.minenow.butcher;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** What a butcher culls goes straight into its inventory, with the extra cuts its level earns ({@link ButcherSkill}). */
public final class ButcherDrops {
    private ButcherDrops() {}

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Villager butcher) || !butcher.getVillagerData().profession().is(VillagerProfession.BUTCHER)) return;
        if (!(butcher.level() instanceof ServerLevel level) || !event.getEntity().getType().builtInRegistryHolder().is(TendHerd.LIVESTOCK)) return;
        int extra = ButcherSkill.extraCuts(butcher.getVillagerData().level());
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem().copy();
            if (extra > 0) stack.grow(butcher.getRandom().nextInt(extra + 1));
            ItemStack rest = butcher.getInventory().addItem(stack);
            if (!rest.isEmpty()) Block.popResource(level, butcher.blockPosition(), rest);
        }
        event.setCanceled(true);
    }

}

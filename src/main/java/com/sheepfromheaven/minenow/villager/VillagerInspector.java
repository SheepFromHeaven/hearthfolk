package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** A debug item: right-click any villager for what {@code /minenow villager} says about it, and its whole inventory opens to look at or change. */
public final class VillagerInspector {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MineNow.MOD_ID);
    public static final DeferredItem<Item> ITEM = ITEMS.registerItem("villager_inspector", Item::new, properties -> properties.stacksTo(1));

    private VillagerInspector() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(ITEM.get());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, VillagerInspector::onEntityInteract);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(ITEM.get()) || !(event.getTarget() instanceof Villager villager)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && villager.level() instanceof ServerLevel level) {
            report(level, villager).forEach(player::sendSystemMessage);
            open(player, villager);
        }
    }

    public static List<Component> report(ServerLevel level, Villager villager) {
        return IdleReport.describe(level, villager);
    }

    private static void open(ServerPlayer player, Villager villager) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, new InventoryView(villager), 1),
                villager.getDisplayName()));
    }

    /** One row of nine over the villager's eight slots; the ninth is a dead slot that holds nothing. */
    private record InventoryView(Villager villager) implements Container {
        private static final int SIZE = 9;

        private Container inventory() {
            return villager.getInventory();
        }

        private boolean real(int slot) {
            return slot >= 0 && slot < inventory().getContainerSize();
        }

        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean isEmpty() {
            return inventory().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return real(slot) ? inventory().getItem(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return real(slot) ? inventory().removeItem(slot, count) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return real(slot) ? inventory().removeItemNoUpdate(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (real(slot)) inventory().setItem(slot, stack);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return real(slot);
        }

        @Override
        public void setChanged() {
            inventory().setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return villager.isAlive() && player.distanceToSqr(villager) < 64.0D;
        }

        @Override
        public void clearContent() {
            inventory().clearContent();
        }
    }
}

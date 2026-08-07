package com.sheepfromheaven.minenow.inventory;

import com.sheepfromheaven.minenow.item.FilterData;
import com.sheepfromheaven.minenow.item.FilterItem;
import com.sheepfromheaven.minenow.item.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecSlots;

import java.util.ArrayList;

/**
 * Real drag-and-drop menu for configuring a {@link FilterItem}: a 3x3 grid of template slots
 * (backed by a throwaway {@link SimpleContainer}, not the item's own data) plus the player's own
 * inventory, so items can be dragged in the normal Minecraft way to pick what goes in the filter.
 *
 * <p>The template slots never hold - or move - a real item: {@link #clicked} and
 * {@link #quickMoveStack} intercept every interaction with them and only ever copy the clicked
 * stack's identity into {@link #templates}, leaving the real stack (in hand or in the player's
 * inventory) completely untouched. On {@link #removed}, those identities are read off into the held
 * Filter item's {@link ModDataComponents#FILTER_DATA} component.
 */
public class FilterMenu extends AbstractContainerMenu {
    public static final int TEMPLATE_SLOTS = 9;
    public static final int BUTTON_TOGGLE_WHITELIST = 0;
    public static final int BUTTON_TOGGLE_MATCH_METADATA = 1;

    /** Server-safe (no ResourceManager dependency) - see {@link ScreenSpecLoader#fromClasspath}. */
    public static final ScreenSpec SPEC = ScreenSpecLoader.fromClasspath("minenow", "filter_screen");

    private final SimpleContainer templates = new SimpleContainer(TEMPLATE_SLOTS);
    private final Player owner;
    private final InteractionHand hand;
    private boolean whitelist;
    private boolean matchMetadata;

    /** Server-side: opened for {@code owner}'s Filter item in {@code hand}, seeded from its current data. */
    public FilterMenu(int containerId, Inventory playerInventory, Player owner, InteractionHand hand, FilterData initial) {
        super(ModMenus.FILTER_TYPE.get(), containerId);
        this.owner = owner;
        this.hand = hand;
        this.whitelist = initial.whitelist();
        this.matchMetadata = initial.matchMetadata();
        var initialTemplates = initial.templates();
        for (int i = 0; i < Math.min(TEMPLATE_SLOTS, initialTemplates.size()); i++) {
            templates.setItem(i, initialTemplates.get(i).copy());
        }
        addSlots(playerInventory);
    }

    /** Client-side reconstruction: whitelist/matchMetadata arrive via extra data; slot contents sync afterward as usual. */
    public FilterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.FILTER_TYPE.get(), containerId);
        this.owner = playerInventory.player;
        this.hand = InteractionHand.MAIN_HAND;
        this.whitelist = extraData.readBoolean();
        this.matchMetadata = extraData.readBoolean();
        addSlots(playerInventory);
    }

    private void addSlots(Inventory playerInventory) {
        var container = SPEC.container;
        SpecSlots.forArea(container.area("templates"), templates).forEach(this::addSlot);
        SpecSlots.forPlayerInventory(container.area("player_inv"), playerInventory).forEach(this::addSlot);
        SpecSlots.forPlayerHotbar(container.area("player_hotbar"), playerInventory).forEach(this::addSlot);
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public boolean isMatchMetadata() {
        return matchMetadata;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_WHITELIST) {
            whitelist = !whitelist;
            return true;
        }
        if (id == BUTTON_TOGGLE_MATCH_METADATA) {
            matchMetadata = !matchMetadata;
            return true;
        }
        return false;
    }

    /**
     * Every interaction with a template slot is intercepted so it can never transfer a real item:
     * {@link ClickType#PICKUP} copies whatever is on the cursor (or clears the template, if nothing
     * is) without touching the carried stack; {@link ClickType#SWAP} (number-key hotbar swap) copies
     * that hotbar slot's identity the same way; anything that would otherwise let vanilla move or
     * drop a real stack through this slot ({@link ClickType#THROW}, {@link ClickType#CLONE},
     * {@link ClickType#QUICK_CRAFT}) is a no-op. {@link ClickType#QUICK_MOVE} (shift-click) falls
     * through to {@link #quickMoveStack}, which is equally non-consuming.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < TEMPLATE_SLOTS) {
            switch (clickType) {
                case PICKUP -> templates.setItem(slotId, toTemplate(getCarried()));
                case SWAP -> templates.setItem(slotId, toTemplate(player.getInventory().getItem(button)));
                case THROW, CLONE, QUICK_CRAFT -> { /* never let vanilla touch a real stack here */ }
                default -> super.clicked(slotId, button, clickType, player);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Shift-click: capturing a player-inventory item's identity into the first empty template slot,
     * or clearing a template slot - either way, always returns {@link ItemStack#EMPTY} so vanilla
     * treats it as "nothing moved" and leaves the real stack exactly where it was.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < TEMPLATE_SLOTS) {
            templates.setItem(index, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        var slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            int emptyTemplateSlot = firstEmptyTemplateSlot();
            if (emptyTemplateSlot >= 0) {
                templates.setItem(emptyTemplateSlot, toTemplate(slot.getItem()));
            }
        }
        return ItemStack.EMPTY;
    }

    private int firstEmptyTemplateSlot() {
        for (int i = 0; i < TEMPLATE_SLOTS; i++) {
            if (templates.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    /** A template only ever remembers the item's identity (and, if {@link #matchMetadata} is on, its exact components) at count 1. */
    private ItemStack toTemplate(ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        return matchMetadata ? source.copyWithCount(1) : new ItemStack(source.getItem());
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;

        var heldStack = owner.getItemInHand(hand);
        if (heldStack.getItem() instanceof FilterItem) {
            var savedTemplates = new ArrayList<ItemStack>();
            for (int i = 0; i < templates.getContainerSize(); i++) {
                var stack = templates.getItem(i);
                if (!stack.isEmpty()) {
                    savedTemplates.add(stack.copy());
                }
            }
            heldStack.set(ModDataComponents.FILTER_DATA, new FilterData(savedTemplates, whitelist, matchMetadata));
        }
        templates.clearContent();
    }
}

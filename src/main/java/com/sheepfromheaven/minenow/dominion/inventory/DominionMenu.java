package com.sheepfromheaven.minenow.dominion.inventory;

import com.sheepfromheaven.minenow.storage.inventory.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SlotAreaVisibility;
import sheepfromheaven.screenspec.runtime.SpecSlots;
import sheepfromheaven.screenspec.runtime.TabAwareAreaHost;

import java.util.HashMap;
import java.util.Map;

/**
 * Backs the dominion screen's Banner tab: a single transient slot for a "template" banner - the
 * player drops any banner in, its design gets copied onto the dominion when they press Apply, and the
 * banner is handed straight back so it's never consumed. The rest of the dominion screen's data
 * (name, tier, inhabitants, milestone progress) rides along on this menu too, since opening a real
 * {@link AbstractContainerMenu} (instead of the old bare clientbound "open screen" packet) is what
 * gets us proper slot syncing and drag-and-drop for the banner slot.
 */
public class DominionMenu extends AbstractContainerMenu implements TabAwareAreaHost {
    public static final int BANNER_SLOT = 0;

    public static final ScreenSpec SPEC = ScreenSpecLoader.fromClasspath("minenow", "dominion_screen");

    private final Container bannerContainer = new SimpleContainer(1);
    private final Map<String, SlotAreaVisibility> areaVisibility = new HashMap<>();

    private final String dominionName;
    private final String tier;
    private final BlockPos bannerPos;
    private final String inhabitantNames;
    private final String milestoneProgressData;

    public DominionMenu(int containerId, Inventory playerInventory, String dominionName, String tier, BlockPos bannerPos,
                         String inhabitantNames, String milestoneProgressData) {
        super(ModMenus.DOMINION_TYPE.get(), containerId);
        this.dominionName = dominionName;
        this.tier = tier;
        this.bannerPos = bannerPos;
        this.inhabitantNames = inhabitantNames;
        this.milestoneProgressData = milestoneProgressData;
        addSlots(playerInventory);
    }

    public DominionMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readUtf(), extraData.readUtf(), extraData.readBlockPos(),
                extraData.readUtf(), extraData.readUtf());
    }

    private void addSlots(Inventory playerInventory) {
        var container = SPEC.container;

        var bannerArea = container.area("banner_slot");
        int firstIndex = slots.size();
        addSlot(new Slot(bannerContainer, BANNER_SLOT, bannerArea.slotX(0), bannerArea.slotY(0)) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BannerItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        areaVisibility.put("banner_slot", new SlotAreaVisibility(bannerArea, slots, firstIndex, 1));

        var invArea = container.area("player_inv");
        firstIndex = slots.size();
        SpecSlots.forPlayerInventory(invArea, playerInventory).forEach(this::addSlot);
        areaVisibility.put("player_inv", new SlotAreaVisibility(invArea, slots, firstIndex, invArea.viewport_rows));

        var hotbarArea = container.area("player_hotbar");
        firstIndex = slots.size();
        SpecSlots.forPlayerHotbar(hotbarArea, playerInventory).forEach(this::addSlot);
        areaVisibility.put("player_hotbar", new SlotAreaVisibility(hotbarArea, slots, firstIndex, hotbarArea.viewport_rows));
    }

    @Override
    public void setAreaVisible(String areaId, boolean visible) {
        var visibility = areaVisibility.get(areaId);
        if (visibility != null) visibility.setVisible(visible);
    }

    public String getDominionName() {
        return dominionName;
    }

    public String getTier() {
        return tier;
    }

    public BlockPos getBannerPos() {
        return bannerPos;
    }

    public String getInhabitantNames() {
        return inhabitantNames;
    }

    public String getMilestoneProgressData() {
        return milestoneProgressData;
    }

    public ItemStack getBannerStack() {
        return bannerContainer.getItem(BANNER_SLOT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var result = stack.copy();
        if (index == BANNER_SLOT) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, BANNER_SLOT, BANNER_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    /** Hands the template banner straight back rather than letting it vanish when the screen closes. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        var stack = bannerContainer.getItem(BANNER_SLOT);
        if (!stack.isEmpty()) {
            bannerContainer.setItem(BANNER_SLOT, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}

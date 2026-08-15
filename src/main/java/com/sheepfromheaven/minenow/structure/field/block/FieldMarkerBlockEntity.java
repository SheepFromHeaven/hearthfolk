package com.sheepfromheaven.minenow.structure.field.block;

import com.sheepfromheaven.minenow.structure.StructureBlocks;
import com.sheepfromheaven.minenow.structure.field.CropKind;
import com.sheepfromheaven.minenow.structure.field.inventory.FieldMarkerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Holds the field marker's 2x2 seed-template slots: whichever crop seeds a player templates here are
 * the crops a farmer is allowed to plant on this field, replacing the old right-click-to-toggle
 * mechanic. These are templates, not a real storage container - see {@link FieldMarkerMenu}, which
 * only ever copies a clicked stack's identity in, the same way {@code FilterMenu} does for a
 * {@code FilterItem} - the actual seeds a player places here are never consumed, and the farmer never
 * draws seeds from here either, it's read-only configuration (see
 * {@code FieldWorkScanner#findPlantTarget}).
 */
public class FieldMarkerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_COUNT = 4;

    private final NonNullList<ItemStack> templates = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public FieldMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(StructureBlocks.FIELD_MARKER_BE.get(), pos, state);
    }

    /** The distinct crop kinds currently templated into the seed slots. */
    public Set<CropKind> getAllowedCrops() {
        var allowed = EnumSet.noneOf(CropKind.class);
        for (var stack : templates) {
            if (stack.isEmpty()) continue;
            CropKind.fromSeedItem(stack.getItem()).ifPresent(allowed::add);
        }
        return allowed;
    }

    public ItemStack getTemplate(int slot) {
        return templates.get(slot);
    }

    /** Replaces all templates at once (see {@code FieldMarkerMenu#removed}) - each entry is expected to
     * already be identity-only (count 1, no extra data), not a real carried stack. */
    public void setTemplates(List<ItemStack> newTemplates) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            templates.set(i, i < newTemplates.size() ? newTemplates.get(i) : ItemStack.EMPTY);
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, templates);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, templates);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Field Seed Template");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FieldMarkerMenu(containerId, playerInventory, this);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }
}

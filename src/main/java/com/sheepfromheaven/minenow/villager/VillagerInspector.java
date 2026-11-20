package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A debug item: right-click any villager for what {@code /minenow villager} says about it, shown on a book screen
 * ({@link ReportBook}) rather than in the chat, and sneak-click it to open its whole inventory to look at or change.
 * Right-click a villager's chest or barrel and it opens however it is locked (the owner on the action bar), and right-click
 * a job block, bed or bell and a book says who claimed it, where that villager is and how it is doing, or who lives in
 * the village.
 */
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
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, VillagerInspector::onRightClickBlock);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(ITEM.get()) || !(event.getTarget() instanceof Villager villager)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && villager.level() instanceof ServerLevel level) {
            if (player.isShiftKeyDown()) open(player, villager);
            else ReportBook.show(player, event.getHand(), villager.getDisplayName().getString(), report(level, villager));
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        boolean inMainHand = player.getMainHandItem().is(ITEM.get());
        boolean inOffHand = player.getOffhandItem().is(ITEM.get());
        if (!inMainHand && !inOffHand) return;
        InteractionHand inspector = inMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.BELL)) {
            cancel(event);
            ReportBook.show(player, inspector, "Village", villageReport(level, pos).orElse(List.of()));
            return;
        }
        if (state.getBlock() instanceof BedBlock) {
            cancel(event);
            ReportBook.show(player, inspector, "Bed", claimReport(level, pos).orElse(List.of(Component.literal("Nobody has claimed this bed"))));
            return;
        }
        if (!inMainHand) return;
        Optional<Component> owner = HomeStorage.isStorageBlock(state) && level.getBlockEntity(pos) instanceof BaseContainerBlockEntity
                ? HomeStorage.ownerMessage(level, pos) : Optional.empty();
        Optional<List<Component>> claim = claimReport(level, pos);
        if (owner.isEmpty() && claim.isEmpty()) return;
        cancel(event);
        if (owner.isPresent()) {
            player.sendOverlayMessage(owner.get());
            openStorage(player, level, pos);
        } else {
            claim.ifPresent(lines -> ReportBook.show(player, inspector, "Job block", lines));
        }
    }

    /** Using a bed claims it and sets the spawn point, which the inspector must never do: it only looks, whichever half of the bed it clicks. */
    private static void cancel(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** The village a bell belongs to and its villagers, loaded ones only; a bell nobody claimed is no village. Ringing it would raise the alarm, so the inspector never does. */
    static Optional<List<Component>> villageReport(ServerLevel level, BlockPos bell) {
        Optional<Villages.Village> village = Villages.at(level, bell);
        if (village.isEmpty()) return Optional.of(List.of(Component.literal("This bell belongs to no village: no villager has claimed it")));
        List<Villager> members = village.get().villagers(level);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Village with " + village.get().bells().size() + (village.get().bells().size() == 1 ? " bell and " : " bells and ") + members.size() + (members.size() == 1 ? " villager" : " villagers")));
        for (Villager villager : members) {
            String profession = villager.getVillagerData().profession().unwrapKey().map(key -> key.identifier().getPath()).orElse("villager");
            lines.add(Component.literal("- " + villager.getDisplayName().getString() + ", " + profession + " level " + villager.getVillagerData().level()
                    + (villager.isBaby() ? " (child)" : "") + " at " + villager.blockPosition().toShortString()));
        }
        return Optional.of(lines);
    }

    /** Who claimed the job block or bed at {@code pos}, and how they are; empty for a block nobody could claim. */
    static Optional<List<Component>> claimReport(ServerLevel level, BlockPos clicked) {
        BlockPos pos = clicked;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) pos = pos.relative(state.getValue(BedBlock.FACING));
        GlobalPos target = GlobalPos.of(level.dimension(), pos);
        List<? extends Villager> villagers = level.getEntities(EntityTypes.VILLAGER, Villager::isAlive);
        List<Component> lines = new ArrayList<>();
        for (Villager villager : villagers) {
            if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(target::equals).isPresent()) lines.addAll(claimant(level, villager, "Job block claimed by "));
            if (villager.getBrain().getMemory(MemoryModuleType.HOME).filter(target::equals).isPresent()) lines.addAll(claimant(level, villager, "Bed claimed by "));
        }
        if (!lines.isEmpty()) return Optional.of(lines);
        boolean claimable = level.getPoiManager().getType(pos).filter(type -> !type.is(PoiTypes.MEETING)).isPresent();
        return claimable ? Optional.of(List.of(Component.literal("Nobody has claimed this block"))) : Optional.empty();
    }

    private static List<Component> claimant(ServerLevel level, Villager villager, String role) {
        List<Component> lines = new ArrayList<>();
        lines.add(ReportBook.heading("Claim"));
        lines.add(Component.literal(role + villager.getDisplayName().getString()));
        lines.add(Component.literal("Position: " + villager.blockPosition().toShortString()));
        villager.getBrain().getMemory(MemoryModuleType.HOME).ifPresent(home -> lines.add(Component.literal("Bed: " + home.pos().toShortString())));
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(site -> lines.add(Component.literal("Job block: " + site.pos().toShortString())));
        lines.add(Component.literal(Economy.emeraldEconomy() ? "Emeralds: " + Economy.emeralds(level, villager) + " (it starts a family at " + Wealth.required(level, villager) + ")" : "Emeralds: not counted (emerald economy is off)"));
        lines.add(Component.literal("Experience: " + villager.getVillagerXp()));
        lines.addAll(IdleReport.describe(level, villager));
        return lines;
    }

    private static void openStorage(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<BlockPos> halves = HomeStorage.halves(level, pos);
        List<Container> parts = halves.stream().map(level::getBlockEntity).filter(Container.class::isInstance).map(Container.class::cast).toList();
        if (parts.isEmpty()) return;
        Container container = parts.size() == 2 ? new CompoundContainer(parts.get(0), parts.get(1)) : parts.get(0);
        int rows = Math.max(1, container.getContainerSize() / 9);
        MenuType<ChestMenu> type = rows >= 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3;
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new ChestMenu(type, id, inventory, container, rows) {
            @Override
            public void removed(Player closing) {
                super.removed(closing);
                halves.forEach(half -> StorageTheft.recordContents(level, half));
            }
        }, level.getBlockState(pos).getBlock().getName()));
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

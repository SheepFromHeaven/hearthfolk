package com.sheepfromheaven.minenow.villager;

import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HomeStorage {
    public static final int RADIUS = 8;
    private static final int CHECK_INTERVAL = 40;
    private static final int OWNER_SEARCH_RADIUS = 48;

    private HomeStorage() {}

    /** A chest (our own point of interest) or a barrel (vanilla's fisherman workplace, every barrel is one). */
    public static boolean isStoragePoi(Holder<PoiType> type) {
        return isChestPoi(type) || isBarrelPoi(type);
    }

    static boolean isChestPoi(Holder<PoiType> type) {
        return type.value() == ModVillagers.HOME_STORAGE.get();
    }

    static boolean isBarrelPoi(Holder<PoiType> type) {
        return type.is(PoiTypes.FISHERMAN);
    }

    public static boolean isStorageBlock(BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock;
    }

    public static Optional<GlobalPos> storageOf(Villager villager) {
        return villager.getExistingData(ModVillagers.STORAGE);
    }

    public static Optional<BlockPos> storageIn(ServerLevel level, Villager villager) {
        return storageOf(villager).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
    }

    public static Optional<Component> ownerMessage(ServerLevel level, BlockPos chest) {
        boolean barrel = level.getBlockState(chest).getBlock() instanceof BarrelBlock;
        return ownerOf(level, chest).map(owner -> barrel
                ? Component.translatableWithFallback("minenow.barrel.owned_by", "This barrel belongs to %s", owner.getDisplayName())
                : Component.translatableWithFallback("minenow.chest.owned_by", "This chest belongs to %s", owner.getDisplayName()));
    }

    /** Replaces vanilla's "Chest is locked!" with who owns it. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isStorageBlock(level.getBlockState(event.getPos()))) return;
        if (!(level.getBlockEntity(event.getPos()) instanceof BaseContainerBlockEntity container) || container.canOpen(event.getEntity())) return;
        if (liftAbandonedLock(level, event.getPos())) return;
        ownerMessage(level, event.getPos()).ifPresent(message -> {
            event.getEntity().sendOverlayMessage(message);
            level.playSound(null, event.getPos(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        });
    }

    /** A lock of ours that no villager holds any more (its owner died unseen, or before this check existed) is lifted, so the click opens it. */
    private static boolean liftAbandonedLock(ServerLevel level, BlockPos pos) {
        if (ownerOf(level, pos).isPresent() || heldAsJobSite(level, pos)) return false;
        List<BlockPos> halves = halves(level, pos);
        halves.forEach(half -> setLocked(level, half, false));
        return level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container && container.collectComponents().get(DataComponents.LOCK) == null;
    }

    private static Optional<Villager> ownerOf(ServerLevel level, BlockPos chest) {
        List<BlockPos> halves = halves(level, chest);
        return level.getEntitiesOfClass(Villager.class, new AABB(chest).inflate(OWNER_SEARCH_RADIUS),
                villager -> storageIn(level, villager).filter(halves::contains).isPresent() || halves.stream().anyMatch(half -> Workshop.isWorkshopChest(level, villager, half))).stream().findFirst();
    }

    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if ((level.getGameTime() + villager.getId()) % CHECK_INTERVAL != 0) return;
        Optional<BlockPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME)
                .filter(pos -> pos.dimension() == level.dimension())
                .map(GlobalPos::pos);
        if (storageOf(villager).isPresent() && !stillValid(level, villager, home)) release(level, villager);
        if (storageOf(villager).isEmpty() && home.isPresent() && !villager.isBaby()) claim(level, villager, home.get());
        storageIn(level, villager).ifPresent(chest -> {
            takeAllHalves(level, chest);
            StorageTheft.check(level, villager, chest);
        });
        Workshop.tick(level, villager);
        JobLoss.tick(level, villager);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level) {
            release(level, villager);
            Workshop.release(level, villager);
            Belongings.release(level, villager);
        }
    }

    public static void onConversion(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level) {
            release(level, villager);
            Workshop.release(level, villager);
            Belongings.release(level, villager);
        }
    }

    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isStorageBlock(event.getState())) return;
        for (Villager owner : level.getEntitiesOfClass(Villager.class, new AABB(event.getPos()).inflate(OWNER_SEARCH_RADIUS),
                villager -> Workshop.isWorkshopChest(level, villager, event.getPos()))) {
            Workshop.broken(level, owner, event.getPos());
        }
        List<BlockPos> halves = halves(level, event.getPos());
        for (Villager owner : level.getEntitiesOfClass(Villager.class, new AABB(event.getPos()).inflate(OWNER_SEARCH_RADIUS),
                villager -> storageIn(level, villager).filter(halves::contains).isPresent())) {
            if (level.getBlockEntity(event.getPos()) instanceof Container container) Belongings.drop(level, event.getPos(), container, owner);
            Optional<BlockPos> remaining = halves.stream().filter(half -> !half.equals(event.getPos())).findFirst();
            if (remaining.isPresent()) {
                PoiManager pois = level.getPoiManager();
                if (isOccupiedStorage(level, event.getPos())) pois.release(event.getPos());
                storageIn(level, owner).ifPresent(old -> StorageTheft.forget(level, old));
                owner.setData(ModVillagers.STORAGE, GlobalPos.of(level.dimension(), remaining.get()));
            } else {
                release(level, owner);
            }
        }
    }

    private static boolean stillValid(ServerLevel level, Villager villager, Optional<BlockPos> home) {
        Optional<BlockPos> storage = storageIn(level, villager);
        if (storage.isEmpty() || home.isEmpty()) return false;
        BlockPos chest = storage.get();
        return Math.abs(chest.getX() - home.get().getX()) <= RADIUS
                && Math.abs(chest.getY() - home.get().getY()) <= RADIUS
                && Math.abs(chest.getZ() - home.get().getZ()) <= RADIUS
                && level.getPoiManager().exists(chest, HomeStorage::isStoragePoi);
    }

    /** A chest first; a barrel only when no chest is free, so a barrel meant for a fisherman isn't taken while a chest would do. */
    private static void claim(ServerLevel level, Villager villager, BlockPos home) {
        PoiManager pois = level.getPoiManager();
        pois.take(HomeStorage::isChestPoi, (type, pos) -> otherHalfIsFree(level, pos) && !StorageTheft.hasHopperBelow(level, pos) && !holdsItems(level, pos), home, RADIUS)
                .or(() -> pois.take(HomeStorage::isBarrelPoi, (type, pos) -> !StorageTheft.hasHopperBelow(level, pos) && !holdsItems(level, pos), home, RADIUS))
                .ifPresent(chest -> {
            takeAllHalves(level, chest);
            villager.setData(ModVillagers.STORAGE, GlobalPos.of(level.dimension(), chest));
        });
    }

    static void takeAllHalves(ServerLevel level, BlockPos chest) {
        PoiManager pois = level.getPoiManager();
        for (BlockPos half : halves(level, chest)) {
            if (!half.equals(chest) && !isOccupiedStorage(level, half)) {
                pois.take(HomeStorage::isStoragePoi, (type, pos) -> pos.equals(half), half, 1);
            }
            setLocked(level, half, true);
        }
    }

    /** Something is already in it (either half of a double chest): a chest the player has been using is theirs, never a villager's. */
    static boolean holdsItems(ServerLevel level, BlockPos chest) {
        for (BlockPos half : halves(level, chest)) {
            if (level.getBlockEntity(half) instanceof Container container && !container.isEmpty()) return true;
        }
        return false;
    }

    static boolean otherHalfIsFree(ServerLevel level, BlockPos chest) {
        return halves(level, chest).stream().allMatch(half -> half.equals(chest) || !isOccupiedStorage(level, half));
    }

    private static boolean isOccupiedStorage(ServerLevel level, BlockPos pos) {
        return level.getPoiManager().getCountInRange(HomeStorage::isStoragePoi, pos, 0, PoiManager.Occupancy.IS_OCCUPIED) > 0;
    }

    public static void release(ServerLevel level, Villager villager) {
        Optional<BlockPos> chest = storageIn(level, villager);
        villager.removeData(ModVillagers.STORAGE);
        chest.ifPresent(pos -> releaseChest(level, pos));
    }

    /** Frees and unlocks a chest (both halves) a villager no longer keeps goods in. */
    static void releaseChest(ServerLevel level, BlockPos chest) {
        StorageTheft.forget(level, chest);
        PoiManager pois = level.getPoiManager();
        boolean someonesWorkplace = heldAsJobSite(level, chest);
        for (BlockPos half : halves(level, chest)) {
            if (!someonesWorkplace && pois.exists(half, HomeStorage::isStoragePoi) && isOccupiedStorage(level, half)) pois.release(half);
            if (!someonesWorkplace) setLocked(level, half, false);
        }
    }

    /** A barrel's ticket may be a fisherman's job site rather than storage: that one stays taken and locked. */
    private static boolean heldAsJobSite(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof BarrelBlock && !level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(OWNER_SEARCH_RADIUS),
                villager -> villager.isAlive() && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(site -> site.dimension() == level.dimension() && site.pos().equals(pos)).isPresent()).isEmpty();
    }

    public static @Nullable Container chestContainer(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock block) return ChestBlock.getContainer(block, state, level, pos, true);
        return state.getBlock() instanceof BarrelBlock && level.getBlockEntity(pos) instanceof Container barrel ? barrel : null;
    }

    static List<BlockPos> halves(ServerLevel level, BlockPos pos) {
        List<BlockPos> halves = new ArrayList<>(2);
        halves.add(pos);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            halves.add(pos.relative(ChestBlock.getConnectedDirection(state)));
        }
        return halves;
    }

    static void lock(ServerLevel level, BlockPos pos) {
        setLocked(level, pos, true);
    }

    private static void setLocked(ServerLevel level, BlockPos pos, boolean locked) {
        if (!(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container)) return;
        LockCode villagerLock = new LockCode(ItemPredicate.Builder.item()
                .of(level.registryAccess().lookupOrThrow(Registries.ITEM), ModVillagers.NO_KEY).build());
        DataComponentPatch patch;
        if (locked) {
            patch = DataComponentPatch.builder().set(DataComponents.LOCK, villagerLock).build();
        } else if (villagerLock.equals(container.collectComponents().get(DataComponents.LOCK))) {
            patch = DataComponentPatch.builder().remove(DataComponents.LOCK).build();
        } else {
            return;
        }
        container.applyComponents(container.collectComponents(), patch);
        container.setChanged();
    }
}

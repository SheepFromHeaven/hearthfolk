package com.sheepfromheaven.minenow.villager;

import com.mojang.serialization.Codec;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Job blocks and chests a player places are the player's: no villager claims them until the player allows it with a
 * sneak-click holding an emerald, which toggles them back. Blocks the world generated, and anything placed before this
 * existed, stay free to claim, so natural villages and old worlds work as before. A private block's point of interest
 * ticket is held, the way {@link PlayerBeds} holds a used bed's, so every way a villager claims (vanilla's job search,
 * {@link ClaimFarJobSite}, {@link HomeStorage}, {@link Workshop}) passes it by.
 */
public final class PrivateBlocks extends SavedData {
    private static final int SWEEP_EVERY = 5;
    private static final int HOLDER_SEARCH = 64;

    private static final Codec<PrivateBlocks> CODEC = Codec.LONG.listOf().xmap(PrivateBlocks::new, blocks -> List.copyOf(blocks.positions));
    private static final SavedDataType<PrivateBlocks> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "private_blocks"), () -> new PrivateBlocks(List.of()), CODEC, null);

    private final Set<Long> positions;

    private PrivateBlocks(List<Long> positions) {
        this.positions = new HashSet<>(positions);
    }

    private static PrivateBlocks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static boolean isPrivate(ServerLevel level, BlockPos pos) {
        return of(level).positions.contains(pos.asLong());
    }

    /** A block villagers claim: a job block (any {@code #minecraft:acquirable_job_site} point of interest) or a chest or barrel they keep goods in. */
    static boolean claimable(BlockState state) {
        return PoiTypes.forState(state).filter(type -> type.is(PoiTypeTags.ACQUIRABLE_JOB_SITE) || HomeStorage.isStoragePoi(type)).isPresent();
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player) || !claimable(event.getPlacedBlock())) return;
        PrivateBlocks blocks = of(level);
        BlockPos pos = event.getPos().immutable();
        if (blocks.positions.add(pos.asLong())) blocks.setDirty();
        // Vanilla adds the point of interest in a server task queued while the block was set; this one runs right after it.
        level.getServer().execute(() -> hold(level, pos));
    }

    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PrivateBlocks blocks = of(level);
        if (blocks.positions.remove(event.getPos().asLong())) blocks.setDirty();
    }

    /** Sneak-click holding an emerald: a private block is opened to villagers, any other one becomes private. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (!player.isSecondaryUseActive() || !player.getMainHandItem().is(Items.EMERALD)) return;
        BlockState state = level.getBlockState(event.getPos());
        if (!claimable(state)) return;
        List<BlockPos> halves = HomeStorage.isStorageBlock(state) ? HomeStorage.halves(level, event.getPos()) : List.of(event.getPos().immutable());
        boolean makePrivate = !isPrivate(level, event.getPos());
        PrivateBlocks blocks = of(level);
        for (BlockPos half : halves) {
            if (makePrivate) {
                takeFromVillagers(level, half);
                blocks.positions.add(half.asLong());
                hold(level, half);
            } else {
                blocks.positions.remove(half.asLong());
                letGo(level, half);
            }
        }
        blocks.setDirty();
        Component name = state.getBlock().getName();
        player.sendOverlayMessage(makePrivate
                ? Component.translatableWithFallback("minenow.private.mine", "This %s is yours, villagers leave it alone", name)
                : Component.translatableWithFallback("minenow.private.allowed", "Villagers may claim this %s", name));
        level.sendParticles(makePrivate ? ParticleTypes.SMOKE : ParticleTypes.HAPPY_VILLAGER,
                event.getPos().getX() + 0.5, event.getPos().getY() + 1.0, event.getPos().getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, event.getPos(), makePrivate ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.6F, 1.0F);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Holds the ticket of every loaded private block: the point of interest of a block just placed only appears a tick later, and chunks load back in. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % SWEEP_EVERY != 0) return;
        PrivateBlocks blocks = of(level);
        boolean changed = false;
        for (Long packed : List.copyOf(blocks.positions)) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) continue;
            if (!claimable(level.getBlockState(pos))) {
                blocks.positions.remove(packed);
                changed = true;
                continue;
            }
            hold(level, pos);
        }
        if (changed) blocks.setDirty();
    }

    private static void hold(ServerLevel level, BlockPos pos) {
        PoiManager pois = level.getPoiManager();
        if (pois.getType(pos).isEmpty()) return;
        if (pois.getCountInRange(type -> true, pos, 0, PoiManager.Occupancy.HAS_SPACE) > 0) pois.take(type -> true, (type, at) -> at.equals(pos), pos, 0);
    }

    private static void letGo(ServerLevel level, BlockPos pos) {
        PoiManager pois = level.getPoiManager();
        if (pois.getType(pos).isPresent() && pois.getCountInRange(type -> true, pos, 0, PoiManager.Occupancy.IS_OCCUPIED) > 0) pois.release(pos);
    }

    /**
     * A villager holding the block gives it up: as its job site it keeps its trade and looks for another, as a chest its
     * goods come out as its belongings, the way they do when the chest is broken.
     */
    private static void takeFromVillagers(ServerLevel level, BlockPos pos) {
        List<Villager> near = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(HOLDER_SEARCH), Villager::isAlive);
        for (Villager villager : near) {
            for (var memory : List.of(MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE)) {
                Optional<GlobalPos> site = villager.getBrain().getMemory(memory);
                if (site.filter(at -> at.dimension() == level.dimension() && at.pos().equals(pos)).isEmpty()) continue;
                villager.releasePoi(memory);
                villager.getBrain().eraseMemory(memory);
            }
            if (Workshop.isWorkshopChest(level, villager, pos)) Workshop.broken(level, villager, pos);
            if (HomeStorage.storageIn(level, villager).filter(chest -> HomeStorage.halves(level, chest).contains(pos)).isPresent()) {
                for (BlockPos half : HomeStorage.halves(level, pos)) {
                    if (level.getBlockEntity(half) instanceof Container container) Belongings.drop(level, half, container, villager);
                }
                HomeStorage.release(level, villager);
            }
        }
    }
}

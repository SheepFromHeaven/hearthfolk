package com.sheepfromheaven.minenow.miner;

import com.google.common.collect.ImmutableSet;
import com.sheepfromheaven.minenow.MineNow;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The miner: a new profession whose job site is a furnace minecart parked off the rails ({@link FurnaceCartBlock}).
 * Using a furnace minecart on anything but a rail places it as that block.
 */
public final class Miner {
    public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "miner"));
    public static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "miner"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MineNow.MOD_ID);
    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MineNow.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MineNow.MOD_ID);

    public static final DeferredBlock<FurnaceCartBlock> FURNACE_CART = BLOCKS.registerBlock("furnace_minecart", FurnaceCartBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(FurnaceBlock.LIT) ? 13 : 0));

    static {
        POI_TYPES.register("miner", () -> new PoiType(ImmutableSet.copyOf(FURNACE_CART.get().getStateDefinition().getPossibleStates()), 1, 1));
        PROFESSIONS.register("miner", () -> new VillagerProfession(
                Component.translatable("entity.minenow.villager.miner"),
                poi -> poi.is(POI) && com.sheepfromheaven.minenow.MineNowConfig.professionEnabled(PROFESSION),
                poi -> poi.is(POI) && com.sheepfromheaven.minenow.MineNowConfig.professionEnabled(PROFESSION),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_TOOLSMITH,
                Int2ObjectMap.ofEntries()));
    }

    private Miner() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        modBus.addListener(Miner::onAddBlocksToBlockEntityTypes);
    }

    private static void onAddBlocksToBlockEntityTypes(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityTypes.FURNACE, FURNACE_CART.get());
    }

    /** A furnace minecart used on anything but a rail is parked there as a block instead of being placed as a minecart. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Level level = event.getLevel();
        if (!stack.is(Items.FURNACE_MINECART) || level.getBlockState(event.getPos()).is(BlockTags.RAILS)) return;
        BlockPlaceContext context = new BlockPlaceContext(event.getEntity(), event.getHand(), stack, event.getHitVec());
        if (!context.canPlace()) return;
        BlockPos pos = context.getClickedPos();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) return;
        BlockState state = FURNACE_CART.get().defaultBlockState().setValue(FurnaceBlock.FACING, context.getHorizontalDirection().getOpposite());
        level.setBlock(pos, state, 3);
        level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.gameEvent(event.getEntity(), GameEvent.BLOCK_PLACE, pos);
        stack.consume(1, event.getEntity());
    }

    /** Down in a mine, where buyers can't follow without falling in. */
    /** Whether the miner's pickaxe can't harvest something in the mine it digs, so a better one would pay for itself. */
    public static boolean pickaxeHoldsItBack(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.villager.Villager miner) {
        int slot = com.sheepfromheaven.minenow.villager.Tools.slot(miner);
        if (slot < 0) return false;
        return LadderClimb.ownCart(level, miner).filter(cart -> level.getBlockState(cart).is(FURNACE_CART.get()))
                .map(cart -> Quarry.around(level, cart).holdsBack(level, cart.getY() - MinerSkill.depth(miner.getVillagerData().level()), miner.getInventory().getItem(slot)))
                .orElse(false);
    }

    /** Down a mine: a miner in its own (checked once a second), or anyone in a worked shaft. Cheap, since buyers ask it of every seller. */
    /**
     * Whether the miner breaks a block in its ladder's column that its pickaxe can't harvest, wasting it: only when no
     * villager nearby sells a pickaxe that harvests it that it could pay for, with money or credit; else it waits and buys one.
     */
    public static java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> breaksThrough(net.minecraft.server.level.ServerLevel level,
                                                                                                              net.minecraft.world.entity.npc.villager.Villager miner) {
        return state -> !com.sheepfromheaven.minenow.villager.BuyFromVillagers.couldBuyTool(level, miner, pickaxe -> pickaxe.isCorrectToolForDrops(state));
    }

    public static boolean isDownAMine(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.villager.Villager villager) {
        return UNDERGROUND.containsKey(villager.getUUID())
                || SHAFTS.values().stream().anyMatch(shaft -> shaft.dimension() == level.dimension() && shaft.holds(villager.blockPosition()));
    }

    /** Where a miner's ladder is and what its column holds, for the soak trace. */
    public static String describeLadder(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.villager.Villager miner) {
        return miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).map(site -> {
            Quarry quarry = Quarry.around(level, site.pos());
            return quarry.ladder(level, site.pos()).map(ladder -> {
                StringBuilder column = new StringBuilder("ladder " + ladder.top().toShortString() + " facing " + ladder.facing() + ":");
                for (int y = ladder.top().getY() + 1; y >= ladder.top().getY() - 26; y--) {
                    column.append(' ').append(y).append('=').append(level.getBlockState(ladder.top().atY(y)).getBlock().getDescriptionId().replace("block.minecraft.", ""));
                }
                return column.toString();
            }).orElse("no ladder");
        }).orElse("no job site");
    }

    /** What the miner's next work is and whether it can stand to do it, for the soak report. */
    public static String describeWork(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.villager.Villager miner) {
        java.util.Optional<net.minecraft.core.BlockPos> cart = LadderClimb.ownCart(level, miner);
        int slot = com.sheepfromheaven.minenow.villager.Tools.slot(miner);
        if (cart.isEmpty() || slot < 0) return "no cart or pickaxe";
        Quarry quarry = Quarry.around(level, cart.get());
        int bottom = cart.get().getY() - MinerSkill.depth(miner.getVillagerData().level());
        int tunnels = MinerSkill.tunnelLength(miner.getVillagerData().level());
        java.util.List<net.minecraft.core.BlockPos> work = quarry.nextWork(level, bottom, miner.getInventory().getItem(slot), tunnels,
                MineLayout.deepestPlatform(quarry.top(), level.getBestNeighborSignal(cart.get())), pos -> false, breaksThrough(level, miner));
        java.util.Map<Integer, java.util.Set<net.minecraft.core.BlockPos>> floods = new java.util.HashMap<>();
        StringBuilder text = new StringBuilder(describeLadder(level, miner) + "; missing ladder " + quarry.missingLadder(level).map(p -> p.subtract(cart.get()).toShortString()).orElse("-")
                + ", at " + miner.blockPosition().subtract(cart.get()).toShortString() + ", seal spots " + quarry.sealSpots(level, miner.blockPosition(), bottom, tunnels).stream().map(p -> p.subtract(cart.get()).toShortString()).toList() + ", next work " + work.size() + ":");
        for (net.minecraft.core.BlockPos pos : work.stream().limit(6).toList()) {
            net.minecraft.core.BlockPos stand = DigQuarry.standFor(level, quarry, pos, floods);
            text.append(' ').append(pos.subtract(cart.get()).toShortString()).append('=').append(level.getBlockState(pos).getBlock().getDescriptionId().replace("block.minecraft.", ""))
                    .append(stand == null ? " (no stand)" : " (stand " + stand.subtract(cart.get()).toShortString() + ")");
        }
        return text.toString();
    }

    public static java.util.Optional<net.minecraft.core.BlockPos> ladderTop(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.villager.Villager miner) {
        return miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                .flatMap(site -> Quarry.around(level, site.pos()).ladder(level, site.pos()).map(Quarry.Ladder::top));
    }

    private static final java.util.Map<java.util.UUID, Boolean> UNDERGROUND = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Vanilla gives up a job site it has failed to reach for a minute, and it can't plan a path in or out of a mine, so
     * while a miner is down its mine those failures don't count against its cart (checked once a second, applied every tick).
     */
    public static void onEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.npc.villager.Villager villager)
                || !(villager.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !villager.getVillagerData().profession().is(PROFESSION)) return;
        LadderClimb.ownCart(level, villager).ifPresent(cart -> {
            if (villager.tickCount % 100 == 0 || !SHAFTS.containsKey(cart)) {
                Quarry quarry = Quarry.around(level, cart);
                if (quarry.isEmpty()) SHAFTS.remove(cart);
                else SHAFTS.put(cart, new Shaft(level.dimension(), quarry.outline().stream().map(cell -> net.minecraft.core.BlockPos.asLong(cell.getX(), 0, cell.getZ()))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()), quarry.top(), villager.getUUID()));
            }
        });
        if (villager.tickCount % 20 == 0) {
            if (LadderClimb.inQuarry(level, villager)) UNDERGROUND.put(villager.getUUID(), true);
            else UNDERGROUND.remove(villager.getUUID());
        }
        if (UNDERGROUND.containsKey(villager.getUUID())) {
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
    }

    /** A miner that lost its job no longer works its mine: the shaft is no longer one a miner keeps, and it is no longer down it as a miner. */
    public static void forgetMine(java.util.UUID miner) {
        SHAFTS.values().removeIf(shaft -> shaft.owner().equals(miner));
        UNDERGROUND.remove(miner);
    }

    private record Shaft(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, java.util.Set<Long> columns, int top, java.util.UUID owner) {
        boolean holds(net.minecraft.core.BlockPos pos) {
            return pos.getY() <= top && columns.contains(net.minecraft.core.BlockPos.asLong(pos.getX(), 0, pos.getZ()));
        }
    }

    /** The open shafts of the mines miners are working, by cart, so pathfinding can keep everyone else out of them. */
    private static final java.util.Map<net.minecraft.core.BlockPos, Shaft> SHAFTS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether a path for this villager (or wandering trader) may not step into (x, y, z): it lies down the open shaft of a
     * mine that isn't its own (a miner's cart) and that it isn't already down, where it couldn't climb out.
     */
    public static boolean shaftClosedTo(net.minecraft.world.entity.Mob mob, int x, int y, int z) {
        if (SHAFTS.isEmpty()) return false;
        net.minecraft.world.level.Level level = mob.level();
        long column = net.minecraft.core.BlockPos.asLong(x, 0, z);
        for (var entry : SHAFTS.entrySet()) {
            Shaft shaft = entry.getValue();
            if (y > shaft.top() || shaft.dimension() != level.dimension() || !shaft.columns().contains(column)) continue;
            if (shaft.holds(mob.blockPosition())) continue;
            if (mob instanceof net.minecraft.world.entity.npc.villager.Villager villager && villager.getVillagerData().profession().is(PROFESSION)
                    && villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                    .filter(site -> site.dimension() == level.dimension() && site.pos().equals(entry.getKey())).isPresent()) continue;
            return true;
        }
        return false;
    }

    /** A villager down an open shaft fell because our climbing let it, not by carelessness, so the fall doesn't hurt it. */
    public static void onLivingFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.npc.villager.Villager villager
                && SHAFTS.values().stream().anyMatch(shaft -> shaft.dimension() == villager.level().dimension() && shaft.holds(villager.blockPosition()))) {
            event.setDamageMultiplier(0.0F);
        }
    }
}

package com.sheepfromheaven.minenow.merchant;

import com.google.common.collect.ImmutableSet;
import com.sheepfromheaven.minenow.MineNow;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.Price;
import com.sheepfromheaven.minenow.villager.TradeTable;
import com.sheepfromheaven.minenow.villager.Villages;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import com.mojang.serialization.Codec;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The merchant: a new profession whose job site is a pack station, a saddle on a hay bale. Using a saddle on a hay bale
 * while sneaking makes one. A merchant keeps pack mules, as many as its level allows ({@link MerchantSkill#mules}), the
 * unclaimed untamed ones within {@link #MULE_REACH} blocks of its station, and carries village goods to nearby villages on them.
 */
public final class Merchant {
    public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "merchant"));
    public static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "merchant"));

    public static final int MULE_REACH = 8;
    private static final int CHECK_EVERY = 40;
    private static final int RELEASE_SEARCH = 64;
    private static final int PACK_SEARCH = 24;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MineNow.MOD_ID);
    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MineNow.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MineNow.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MineNow.MOD_ID);

    /** The merchant a mule belongs to. */
    public static final Supplier<AttachmentType<Optional<UUID>>> MULE_OWNER = ATTACHMENT_TYPES.register("mule_owner",
            () -> AttachmentType.<Optional<UUID>>builder(Optional::empty)
                    .serialize(UUIDUtil.CODEC.optionalFieldOf("merchant"))
                    .build());

    /** The tour a merchant is on, if any. */
    public static final Supplier<AttachmentType<Optional<Tour>>> TOUR = ATTACHMENT_TYPES.register("tour",
            () -> AttachmentType.<Optional<Tour>>builder(Optional::empty)
                    .serialize(Tour.CODEC.optionalFieldOf("tour"))
                    .build());

    /** The game time before which a merchant stays home: as long as its last tour lasted. */
    public static final Supplier<AttachmentType<Long>> RESTED_AT = ATTACHMENT_TYPES.register("rested_at",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG.fieldOf("time"))
                    .build());

    /** The head of the straw bed a merchant has laid down on the road, until it packs it up. */
    public static final Supplier<AttachmentType<Optional<BlockPos>>> CAMP = ATTACHMENT_TYPES.register("camp",
            () -> AttachmentType.<Optional<BlockPos>>builder(Optional::empty)
                    .serialize(BlockPos.CODEC.optionalFieldOf("head"))
                    .build());

    /**
     * Emeralds a merchant's market sales would have fetched while the emerald economy is off, kept apart from its goods,
     * since then only the player has a use for emeralds; it hands them over once it is home ({@link GiveEarnings}).
     */
    public static final Supplier<AttachmentType<Integer>> EARNINGS = ATTACHMENT_TYPES.register("earnings",
            () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("emeralds"))
                    .build());

    /** What a merchant adds to the price a village's trade sells a good for when it brings it from elsewhere. */
    public static final double IMPORT_MARKUP = 1.25;
    private static final int BED_WHEAT = 27;

    public static final DeferredBlock<Block> PACK_STATION = BLOCKS.registerBlock("pack_station", Block::new,
            properties -> properties.mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.GRASS));

    static {
        POI_TYPES.register("merchant", () -> new PoiType(ImmutableSet.copyOf(PACK_STATION.get().getStateDefinition().getPossibleStates()), 1, 1));
        PROFESSIONS.register("merchant", () -> new VillagerProfession(
                Component.translatable("entity.minenow.villager.merchant"),
                poi -> poi.is(POI) && com.sheepfromheaven.minenow.MineNowConfig.professionEnabled(PROFESSION),
                poi -> poi.is(POI) && com.sheepfromheaven.minenow.MineNowConfig.professionEnabled(PROFESSION),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_LEATHERWORKER,
                Int2ObjectMap.ofEntries()));
    }

    private Merchant() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
    }

    /** A saddle used while sneaking on a hay bale turns it into a pack station. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!event.getEntity().isShiftKeyDown() || !event.getItemStack().is(Items.SADDLE) || !level.getBlockState(pos).is(Blocks.HAY_BLOCK)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) return;
        level.setBlock(pos, PACK_STATION.get().defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.HORSE_SADDLE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(event.getEntity(), GameEvent.BLOCK_PLACE, pos);
        event.getItemStack().consume(1, event.getEntity());
    }

    /** Every two seconds a merchant claims the unclaimed untamed mules around its station, nearest first, up to what its level allows, and puts a chest it holds on each. */
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level) || villager.tickCount % CHECK_EVERY != 0) return;
        if (!isMerchant(villager) || villager.isBaby()) return;
        if (villager.getData(TOUR).isPresent()) Tours.resume(villager);
        Optional<BlockPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        if (site.isEmpty()) return;
        List<Mule> owned = ownedMules(level, villager, new AABB(site.get()).inflate(MULE_REACH + 2));
        int room = MerchantSkill.mules(villager.getVillagerData().level()) - owned.size();
        if (room > 0) {
            level.getEntitiesOfClass(Mule.class, new AABB(site.get()).inflate(MULE_REACH),
                            mule -> mule.isAlive() && !mule.isTamed() && mule.getData(MULE_OWNER).isEmpty())
                    .stream()
                    .sorted(Comparator.comparingDouble(mule -> mule.blockPosition().distSqr(site.get())))
                    .limit(room)
                    .forEach(mule -> {
                        mule.setData(MULE_OWNER, Optional.of(villager.getUUID()));
                        owned.add(mule);
                    });
        }
        boolean touring = onTour(villager);
        for (Mule mule : owned) {
            if (!touring) mule.setHomeTo(site.get(), MULE_REACH);
            if (!mule.hasChest() && Economy.take(level, villager, Items.CHEST, 1) == 1) {
                mule.getSlot(499).set(new ItemStack(Items.CHEST));
                level.playSound(null, mule.blockPosition(), SoundEvents.DONKEY_CHEST, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }
    }

    /** A merchant that dies leaves its mules to nobody, and its tour ends. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        ownedMules(level, villager, villager.getBoundingBox().inflate(RELEASE_SEARCH)).forEach(mule -> {
            mule.setData(MULE_OWNER, Optional.empty());
            mule.clearHome();
        });
        Tours.forget(villager);
    }

    /**
     * A merchant whose profession was switched off: its tour ends (and its chunk ticket goes), its mules are let go and
     * what their chests carried is given to it, and a night camp is packed up as at dawn. Called before it loses the trade,
     * while its mules still count as its goods.
     */
    public static void release(ServerLevel level, Villager villager) {
        Tours.forget(villager);
        villager.setData(TOUR, Optional.empty());
        if (villager.getData(CAMP).isPresent()) Camp.wake(level, villager);
        List<Mule> mules = ownedMules(level, villager, villager.getBoundingBox().inflate(RELEASE_SEARCH));
        List<ItemStack> cargo = new java.util.ArrayList<>();
        for (Mule mule : mules) {
            mule.setData(MULE_OWNER, Optional.empty());
            mule.clearHome();
            mule.dropLeash();
            if (!mule.hasChest()) continue;
            Container pack = mule.getInventory();
            for (int slot = 0; slot < pack.getContainerSize(); slot++) {
                ItemStack stack = pack.getItem(slot);
                if (stack.isEmpty()) continue;
                cargo.add(stack.copy());
                pack.setItem(slot, ItemStack.EMPTY);
            }
        }
        for (ItemStack stack : cargo) Economy.give(level, villager, stack);
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide()) Tours.forget(villager);
    }

    /** Keeps what a market sale would have fetched for the player, while the emerald economy is off. */
    public static void earn(Villager merchant, int emeralds) {
        merchant.setData(EARNINGS, merchant.getData(EARNINGS) + emeralds);
    }

    public static boolean isMerchant(Villager villager) {
        return villager.getVillagerData().profession().is(PROFESSION);
    }

    public static boolean hasTour(Villager villager) {
        return isMerchant(villager) && villager.getData(TOUR).isPresent();
    }

    /** On a tour and away from its home chests: its goods are what its mules carry. */
    public static boolean onTour(Villager villager) {
        return isMerchant(villager) && villager.getData(TOUR).filter(tour -> tour.phase() != Tour.Phase.SHOP).isPresent();
    }

    /** Waiting at a village's bell for its villagers to buy. */
    public static boolean isSelling(Villager villager) {
        return isMerchant(villager) && villager.getData(TOUR).filter(tour -> tour.phase() == Tour.Phase.MARKET).isPresent();
    }

    /** The merchants holding a market in {@code village}. */
    public static List<Villager> marketsIn(ServerLevel level, Villages.Village village) {
        return Tours.markets(level).stream().filter(merchant -> village.contains(merchant.blockPosition())).toList();
    }

    /** What an import costs: what the village that makes it sells it for, and a quarter more, in the smallest whole-emerald deal. */
    public static Optional<Price> importPrice(Item item) {
        return TradeTable.anySellPrice(item).map(price -> Price.of(price.perItem() * IMPORT_MARKUP));
    }

    /** The goods a merchant still has to buy for its tour, only while it is shopping at home. */
    public static List<Tour.Want> shoppingList(ServerLevel level, Villager villager) {
        if (!isMerchant(villager)) return List.of();
        if (villager.getData(TOUR).isEmpty()) return bedding(level, villager);
        return villager.getData(TOUR).filter(tour -> tour.phase() == Tour.Phase.SHOP).map(tour -> tour.wanted().stream()
                .map(want -> new Tour.Want(want.item(), want.amount() - carried(level, villager, want.item())))
                .filter(want -> want.amount() > 0)
                .toList()).orElse(List.of());
    }

    /** Wheat for a straw bed (3 hay bales of 9), bought from the farmer while it has no tour and no bed: the hay and bed are crafted at its pack station. */
    private static List<Tour.Want> bedding(ServerLevel level, Villager villager) {
        if (Economy.count(level, villager, Items.STRAW_BED) > 0) return List.of();
        int wheat = BED_WHEAT - Economy.count(level, villager, Items.HAY_BLOCK) * 9 - Economy.count(level, villager, Items.WHEAT);
        return wheat > 0 ? List.of(new Tour.Want(Items.WHEAT, wheat)) : List.of();
    }

    /** What it carries (pockets and mules), not what sits in its home chest. */
    public static int carried(ServerLevel level, Villager villager, Item item) {
        int total = villager.getInventory().countItem(item);
        for (Container pack : packs(level, villager)) total += pack.countItem(item);
        return total;
    }

    /** The chests of its mules. */
    public static List<Container> packs(ServerLevel level, Villager villager) {
        if (!isMerchant(villager)) return List.of();
        return ownedMules(level, villager, villager.getBoundingBox().inflate(PACK_SEARCH)).stream()
                .filter(Mule::hasChest).map(mule -> (Container) mule.getInventory()).toList();
    }

    /** Puts what it carries in the mules' chests; what doesn't fit is returned. */
    public static ItemStack pack(ServerLevel level, Villager villager, ItemStack stack) {
        ItemStack rest = stack;
        for (Container pack : packs(level, villager)) {
            if (rest.isEmpty()) break;
            rest = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, pack, rest, null);
        }
        return rest;
    }

    public static List<Mule> ownedMules(ServerLevel level, Villager merchant, AABB area) {
        return level.getEntitiesOfClass(Mule.class, area, mule -> mule.isAlive() && mule.getData(MULE_OWNER).filter(merchant.getUUID()::equals).isPresent());
    }
}

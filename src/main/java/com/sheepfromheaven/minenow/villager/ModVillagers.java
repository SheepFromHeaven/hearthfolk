package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.UUID;
import java.util.function.Supplier;

public final class ModVillagers {
    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MineNow.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MineNow.MOD_ID);

    public static final DeferredHolder<PoiType, PoiType> HOME_STORAGE = POI_TYPES.register("home_storage",
            () -> new PoiType(ImmutableSet.copyOf(Blocks.CHEST.getStateDefinition().getPossibleStates()), 1, 1));

    /** The archer posts the player marks ({@link HighGround#POSTS}, hay bales), so an alarm finds them anywhere in the village. */
    public static final DeferredHolder<PoiType, PoiType> ARCHER_POST = POI_TYPES.register("archer_post",
            () -> new PoiType(ImmutableSet.copyOf(Blocks.HAY_BLOCK.getStateDefinition().getPossibleStates()), 0, 1));

    public static final Supplier<AttachmentType<GlobalPos>> STORAGE = ATTACHMENT_TYPES.register("storage",
            () -> AttachmentType.builder(() -> GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO))
                    .serialize(GlobalPos.MAP_CODEC)
                    .build());

    public static final Supplier<AttachmentType<UUID>> PLACED_BY = ATTACHMENT_TYPES.register("placed_by",
            () -> AttachmentType.builder(() -> new UUID(0L, 0L))
                    .serialize(UUIDUtil.CODEC.fieldOf("player"))
                    .build());

    /** What a villager owes other villagers, in emeralds by creditor, for goods it got on credit (see {@link BuyFromVillagers}). */
    public static final Supplier<AttachmentType<java.util.Map<UUID, Integer>>> DEBTS = ATTACHMENT_TYPES.register("debts",
            () -> AttachmentType.<java.util.Map<UUID, Integer>>builder(() -> java.util.Map.of())
                    .serialize(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("debts"))
                    .build());

    public static final Supplier<AttachmentType<Boolean>> PURSE_GRANTED = ATTACHMENT_TYPES.register("purse_granted",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("granted"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<Integer>> WORK_XP_DAY = ATTACHMENT_TYPES.register("work_xp_day",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT.fieldOf("day"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<Double>> WORK_XP_TODAY = ATTACHMENT_TYPES.register("work_xp_today",
            () -> AttachmentType.builder(() -> 0.0)
                    .serialize(Codec.DOUBLE.fieldOf("xp"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<Double>> WORK_XP_PROGRESS = ATTACHMENT_TYPES.register("work_xp_progress",
            () -> AttachmentType.builder(() -> 0.0)
                    .serialize(Codec.DOUBLE.fieldOf("xp"))
                    .copyOnDeath()
                    .build());

    /** The sword a villager keeps at its side, drawn only when it fights (see {@link Gear}). */
    public static final Supplier<AttachmentType<net.minecraft.world.item.ItemStack>> SIDEARM = ATTACHMENT_TYPES.register("sidearm",
            () -> AttachmentType.builder(() -> net.minecraft.world.item.ItemStack.EMPTY)
                    .serialize(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.fieldOf("sword"))
                    .build());

    /** The arrows in a villager's quiver, for the bow at its side (see {@link Gear}). */
    public static final Supplier<AttachmentType<net.minecraft.world.item.ItemStack>> QUIVER = ATTACHMENT_TYPES.register("quiver",
            () -> AttachmentType.builder(() -> net.minecraft.world.item.ItemStack.EMPTY)
                    .serialize(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.fieldOf("arrows"))
                    .build());

    /** Whether a villager fights with a bow rather than a sword; fixed for life, decided from its UUID (see {@link Gear#prefersBow}). */
    public static final Supplier<AttachmentType<Boolean>> ARCHER = ATTACHMENT_TYPES.register("archer",
            () -> AttachmentType.builder(holder -> holder instanceof net.minecraft.world.entity.Entity entity && Gear.prefersBow(entity.getUUID()))
                    .serialize(Codec.BOOL.fieldOf("archer"))
                    .copyOnDeath()
                    .build());

    /** A wandering trader sent by {@link Exports}: the village's travelling merchant, not vanilla's wandering trader. */
    public static final Supplier<AttachmentType<Boolean>> TRAVELLING_MERCHANT = ATTACHMENT_TYPES.register("travelling_merchant",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("merchant"))
                    .build());

    /** A travelling merchant's pack mule, which leaves with it. */
    public static final Supplier<AttachmentType<Boolean>> PACK_MULE = ATTACHMENT_TYPES.register("pack_mule",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("pack"))
                    .build());

    /** On a dropped item: the villager whose goods it is (see {@link Belongings}). */
    public static final Supplier<AttachmentType<UUID>> BELONGS_TO = ATTACHMENT_TYPES.register("belongs_to",
            () -> AttachmentType.builder(() -> new UUID(0L, 0L))
                    .serialize(UUIDUtil.CODEC.fieldOf("villager"))
                    .build());

    /** The chests of a villager's workshop storage (see {@link Workshop}). */
    public static final Supplier<AttachmentType<java.util.List<net.minecraft.core.BlockPos>>> WORKSHOP = ATTACHMENT_TYPES.register("workshop",
            () -> AttachmentType.<java.util.List<net.minecraft.core.BlockPos>>builder(() -> java.util.List.of())
                    .serialize(net.minecraft.core.BlockPos.CODEC.listOf().fieldOf("chests"))
                    .build());

    public static final Supplier<AttachmentType<Long>> LAST_MEAL = ATTACHMENT_TYPES.register("last_meal",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG.fieldOf("time"))
                    .build());
    /** Whether the last meal was meat or fish, so the next one is bread or vegetables if it has some (see {@link Meals}). */
    public static final Supplier<AttachmentType<java.util.List<Commissions.Order>>> COMMISSIONS = ATTACHMENT_TYPES.register("commissions",
            () -> AttachmentType.<java.util.List<Commissions.Order>>builder(() -> java.util.List.of())
                    .serialize(Commissions.Order.CODEC.listOf().fieldOf("orders"))
                    .build());

    public static final Supplier<AttachmentType<java.util.List<Commissions.Placed>>> COMMISSIONED = ATTACHMENT_TYPES.register("commissioned",
            () -> AttachmentType.<java.util.List<Commissions.Placed>>builder(() -> java.util.List.of())
                    .serialize(Commissions.Placed.CODEC.listOf().fieldOf("placed"))
                    .build());

    /** Since when a villager with a profession has had no job site, {@code Long.MIN_VALUE} while it has one. */
    public static final Supplier<AttachmentType<Long>> NO_JOB_SINCE = ATTACHMENT_TYPES.register("no_job_since",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE)
                    .serialize(Codec.LONG.fieldOf("time"))
                    .build());

    /** Where a villager's job site last was, so a workstation moved close by goes back to the same villager. */
    public static final Supplier<AttachmentType<java.util.Optional<net.minecraft.core.BlockPos>>> LAST_JOB_SITE = ATTACHMENT_TYPES.register("last_job_site",
            () -> AttachmentType.<java.util.Optional<net.minecraft.core.BlockPos>>builder(() -> java.util.Optional.empty())
                    .serialize(net.minecraft.core.BlockPos.CODEC.optionalFieldOf("pos"))
                    .build());

    /** When a butcher last fed this young animal, -1 for never. */
    public static final Supplier<AttachmentType<Long>> FED_AT = ATTACHMENT_TYPES.register("fed_at",
            () -> AttachmentType.builder(() -> -1L)
                    .serialize(Codec.LONG.fieldOf("time"))
                    .build());

    public static final Supplier<AttachmentType<Boolean>> LAST_MEAL_MEAT = ATTACHMENT_TYPES.register("last_meal_meat",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("meat"))
                    .build());

    public static final TagKey<Item> NO_KEY = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "villager_storage_key"));

    private ModVillagers() {}

    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
    }
}

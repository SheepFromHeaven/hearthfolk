package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** A villager says what it needs in a speech bubble: the item icon of the first reason {@link IdleReason#of} finds. */
public final class NeedBubbles {
    public static final Identifier TOOL = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "need_tool");
    public static final Identifier FUEL = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "need_fuel");
    public static final Identifier MONEY = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "need_money");
    public static final Identifier STORAGE_FULL = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "need_storage_full");
    public static final Identifier BED = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "need_bed");
    public static final Identifier JOB_BLOCK = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "need_job_block");
    public static final Identifier WORK_AREA = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "need_work_area");
    public static final Identifier HUNGER = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "need_hunger");
    public static final Identifier ALARM = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "status_alarm");
    public static final Identifier SHELTER = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "status_shelter");
    public static final Identifier CHAT_JOB_BLOCK = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "chat_job_block");
    public static final Identifier FRIGHT = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "fright");
    private static final int INTERVAL = 80;
    static final int SHOWN_TICKS = 60;
    /** A villager without a bed only says so to a player this close. */
    static final double BED_RANGE = 6;
    static final int COOLDOWN_TICKS = 160;

    private static final Map<Villager, Long> NEXT_AT = new WeakHashMap<>();

    private NeedBubbles() {}

    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if ((level.getGameTime() + villager.getId()) % INTERVAL != 0) return;
        poll(level, villager);
    }

    /** Shows the bubble if one is due: the switch is on, it is awake, a player is near and its cooldown is over. Returns what it showed. */
    static Shown poll(ServerLevel level, Villager villager) {
        if (!MineNowConfig.enabled(MineNowConfig.Feature.SPEECH_BUBBLES) || villager.entityTags().contains(SpeechBubbles.SHOWCASE_TAG)) return Shown.NONE;
        if (!IdleCues.isAwake(villager) || level.getNearestPlayer(villager, SpeechBubbles.RANGE) == null) return Shown.NONE;
        long now = level.getGameTime();
        if (now < NEXT_AT.getOrDefault(villager, 0L)) return Shown.NONE;
        Shown shown = bubbleFor(level, villager);
        if (shown.isEmpty()) return Shown.NONE;
        show(villager, shown, SHOWN_TICKS);
        NEXT_AT.put(villager, now + SHOWN_TICKS + COOLDOWN_TICKS);
        return shown;
    }

    /** The one way a bubble is sent, for the game and for the playground showcase alike. */
    public static void show(Villager villager, Shown shown, int ticks) {
        if (shown.image() != null && !shown.item().isEmpty()) SpeechBubbles.show(villager, shown.item(), shown.image(), ticks);
        else if (shown.image() != null) SpeechBubbles.show(villager, shown.image(), ticks);
        else SpeechBubbles.show(villager, shown.item(), ticks, shown.need());
    }

    /** What a bubble shows: an item, or a picture of our own when no item fits. */
    public record Shown(ItemStack item, @Nullable Identifier image, boolean need) {
        static final Shown NONE = new Shown(ItemStack.EMPTY, null, false);

        Shown(ItemStack item, @Nullable Identifier image) {
            this(item, image, false);
        }

        public static Shown need(@Nullable Item item) {
            return item == null || item == Items.AIR ? NONE : new Shown(new ItemStack(item), null, true);
        }

        public static Shown item(Item item) {
            return new Shown(new ItemStack(item), null, false);
        }

        public boolean isEmpty() {
            return item.isEmpty() && image == null;
        }
    }

    /** A need wins over a status when both apply. */
    static Shown bubbleFor(ServerLevel level, Villager villager) {
        List<IdleReason> reasons = IdleReason.of(level, villager);
        if (!reasons.isEmpty()) {
            Shown need = needFor(level, reasons.contains(IdleReason.NO_MONEY) ? IdleReason.NO_MONEY : reasons.getFirst(), villager);
            if (!need.isEmpty()) return need;
        }
        return statusFor(level, villager);
    }

    /** What the villager is going through: panic, an alarm, walking out of the rain, hunger, in that order. */
    static Shown statusFor(ServerLevel level, Villager villager) {
        if (villager.getBrain().isActive(Activity.PANIC)) return frightened();
        if (Alarms.of(level, villager).isPresent()) return alarm();
        if (villager.getBrain().getRunningBehaviors().stream().anyMatch(SeekShelter.class::isInstance)) return sheltering();
        if (needsBed(level, villager)) return bed();
        if (Meals.isHungry(villager)) return hungry();
        return Shown.NONE;
    }

    /** An adult with no claimed bed, with a player close enough to hear it. */
    static boolean needsBed(ServerLevel level, Villager villager) {
        if (villager.isBaby()) return false;
        boolean hasBed = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).filter(home -> home.dimension() == level.dimension()).isPresent();
        return !hasBed && level.getNearestPlayer(villager, BED_RANGE) != null;
    }

    public static Shown bed() {
        return new Shown(ItemStack.EMPTY, BED, false);
    }

    /** A villager telling of the job it just took: the picture of its job block, plain. */
    public static Shown chatJobBlock(Villager villager) {
        return new Shown(new ItemStack(jobBlockItem(villager)), CHAT_JOB_BLOCK, false);
    }

    public static Shown frightened() {
        return new Shown(ItemStack.EMPTY, FRIGHT, false);
    }

    public static Shown alarm() {
        return new Shown(ItemStack.EMPTY, ALARM, false);
    }

    public static Shown sheltering() {
        return new Shown(ItemStack.EMPTY, SHELTER, false);
    }

    public static Shown hungry() {
        return new Shown(ItemStack.EMPTY, HUNGER, false);
    }

    /** The icon of the villager's first reason (broke beats out of fuel, since NO_MONEY is only ever listed after NO_MATERIALS), or empty when nothing is wrong or the reason has no icon. */
    public static ItemStack iconFor(ServerLevel level, Villager villager) {
        List<IdleReason> reasons = IdleReason.of(level, villager);
        if (reasons.isEmpty()) return ItemStack.EMPTY;
        return itemFor(level, reasons.contains(IdleReason.NO_MONEY) ? IdleReason.NO_MONEY : reasons.getFirst(), villager);
    }

    /** What the bubble shows for one reason, a lack: our own picture where one is drawn (a tool is the crossed pickaxe and axe with a red exclamation mark), else the item with a red cross. */
    public static Shown needFor(ServerLevel level, IdleReason reason, Villager villager) {
        if (reason == IdleReason.NO_TOOL && toolIcon(villager) != null) return new Shown(ItemStack.EMPTY, TOOL, false);
        if (reason == IdleReason.NO_JOB_SITE) return villager.getVillagerData().profession().is(VillagerProfession.NONE) ? Shown.NONE : new Shown(new ItemStack(jobBlockItem(villager)), JOB_BLOCK, false);
        if (reason == IdleReason.NO_WORK_AREA) {
            Item area = IdleReason.workAreaIcon(villager);
            return area == null ? Shown.NONE : new Shown(new ItemStack(area), WORK_AREA, false);
        }
        if (reason == IdleReason.STORAGE_FULL) return new Shown(ItemStack.EMPTY, STORAGE_FULL, false);
        if (reason == IdleReason.NO_MONEY) return new Shown(ItemStack.EMPTY, MONEY, false);
        if (reason == IdleReason.NO_MATERIALS) return new Shown(ItemStack.EMPTY, FUEL, false);
        return Shown.need(itemFor(level, reason, villager).getItem());
    }

    private static ItemStack itemFor(ServerLevel level, IdleReason reason, Villager villager) {
        Item item = switch (reason) {
            case NO_TOOL -> toolIcon(villager);
            case NO_MATERIALS -> Shortage.fuel(level, villager).map(TradeTable.Supply::item).orElse(null);
            case NO_MONEY -> Items.EMERALD;
            case STORAGE_FULL -> Items.CHEST_MINECART;
            case NO_JOB_SITE -> jobBlockIcon(villager);
            case NO_WORK_AREA -> IdleReason.workAreaIcon(villager);
        };
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static Item toolIcon(Villager villager) {
        return TradeTable.forVillager(villager).map(table -> {
            if (!table.tools().isEmpty()) return table.tools().getFirst().item();
            return table.toolTag().flatMap(tag -> {
                for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) return java.util.Optional.of(holder.value());
                return java.util.Optional.<Item>empty();
            }).orElse(null);
        }).orElse(null);
    }

    /** The block of the villager's job as an item: the one its profession holds, a flower pot for one whose block has no item (the forester's nursery). */
    public static Item jobBlockItem(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.is(com.sheepfromheaven.minenow.miner.Miner.PROFESSION)) return Items.FURNACE_MINECART;
        if (profession.is(com.sheepfromheaven.minenow.forester.Forester.PROFESSION)) return Items.FLOWER_POT;
        if (profession.is(com.sheepfromheaven.minenow.golemsmith.Golemsmith.PROFESSION)) return Items.CARVED_PUMPKIN;
        if (profession.is(com.sheepfromheaven.minenow.merchant.Merchant.PROFESSION)) return Items.SADDLE;
        if (!profession.is(VillagerProfession.NONE)) {
            for (Holder<PoiType> type : BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements().toList()) {
                if (!profession.value().heldJobSite().test(type)) continue;
                for (var state : type.value().matchingStates()) {
                    Item item = state.getBlock().asItem();
                    if (item != Items.AIR) return item;
                }
            }
        }
        return Items.FLOWER_POT;
    }

    public static Item jobBlockIcon(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.NONE)) return Items.LECTERN;
        Item tool = toolIcon(villager);
        return tool != null ? tool : Items.OAK_SIGN;
    }
}

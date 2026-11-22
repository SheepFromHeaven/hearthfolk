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
    public static final Identifier FRIGHT = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "fright");
    private static final int INTERVAL = 80;
    static final int SHOWN_TICKS = 60;
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
        if (shown.image() != null) SpeechBubbles.show(villager, shown.image(), SHOWN_TICKS);
        else SpeechBubbles.show(villager, shown.item(), SHOWN_TICKS);
        NEXT_AT.put(villager, now + SHOWN_TICKS + COOLDOWN_TICKS);
        return shown;
    }

    /** What a bubble shows: an item, or a picture of our own when no item fits. */
    record Shown(ItemStack item, @Nullable Identifier image) {
        static final Shown NONE = new Shown(ItemStack.EMPTY, null);

        boolean isEmpty() {
            return item.isEmpty() && image == null;
        }
    }

    /** A need wins over a status when both apply. */
    static Shown bubbleFor(ServerLevel level, Villager villager) {
        ItemStack need = iconFor(level, villager);
        if (!need.isEmpty()) return new Shown(need, null);
        return statusFor(level, villager);
    }

    /** What the villager is going through: panic, an alarm, walking out of the rain, hunger, in that order. */
    static Shown statusFor(ServerLevel level, Villager villager) {
        if (villager.getBrain().isActive(Activity.PANIC)) return new Shown(ItemStack.EMPTY, FRIGHT);
        if (Alarms.of(level, villager).isPresent()) return new Shown(new ItemStack(Items.BELL), null);
        if (villager.getBrain().getRunningBehaviors().stream().anyMatch(SeekShelter.class::isInstance)) return new Shown(new ItemStack(Items.WATER_BUCKET), null);
        if (Meals.isHungry(villager)) return new Shown(new ItemStack(Items.BREAD), null);
        return Shown.NONE;
    }

    /** The icon of the villager's first reason (broke beats out of fuel, since NO_MONEY is only ever listed after NO_MATERIALS), or empty when nothing is wrong or the reason has no icon. */
    public static ItemStack iconFor(ServerLevel level, Villager villager) {
        List<IdleReason> reasons = IdleReason.of(level, villager);
        if (reasons.isEmpty()) return ItemStack.EMPTY;
        IdleReason reason = reasons.contains(IdleReason.NO_MONEY) ? IdleReason.NO_MONEY : reasons.getFirst();
        Item item = switch (reason) {
            case NO_TOOL -> toolIcon(villager);
            case NO_MATERIALS -> Shortage.fuel(level, villager).map(TradeTable.Supply::item).orElse(null);
            case NO_MONEY -> Items.EMERALD;
            case STORAGE_FULL -> Items.CHEST;
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

    public static Item jobBlockIcon(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.NONE)) return Items.LECTERN;
        for (Holder<PoiType> type : BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements().toList()) {
            if (!profession.value().heldJobSite().test(type)) continue;
            for (var state : type.value().matchingStates()) return state.getBlock().asItem();
        }
        return null;
    }
}

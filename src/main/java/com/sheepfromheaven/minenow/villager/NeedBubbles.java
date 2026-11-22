package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNowConfig;
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
    static ItemStack poll(ServerLevel level, Villager villager) {
        if (!MineNowConfig.enabled(MineNowConfig.Feature.SPEECH_BUBBLES)) return ItemStack.EMPTY;
        if (!IdleCues.isAwake(villager) || level.getNearestPlayer(villager, SpeechBubbles.RANGE) == null) return ItemStack.EMPTY;
        long now = level.getGameTime();
        if (now < NEXT_AT.getOrDefault(villager, 0L)) return ItemStack.EMPTY;
        ItemStack icon = iconFor(level, villager);
        if (icon.isEmpty()) return ItemStack.EMPTY;
        SpeechBubbles.show(villager, icon, SHOWN_TICKS);
        NEXT_AT.put(villager, now + SHOWN_TICKS + COOLDOWN_TICKS);
        return icon;
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

    private static Item toolIcon(Villager villager) {
        return TradeTable.forVillager(villager).map(table -> {
            if (!table.tools().isEmpty()) return table.tools().getFirst().item();
            return table.toolTag().flatMap(tag -> {
                for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) return java.util.Optional.of(holder.value());
                return java.util.Optional.<Item>empty();
            }).orElse(null);
        }).orElse(null);
    }

    private static Item jobBlockIcon(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.NONE)) return Items.LECTERN;
        for (Holder<PoiType> type : BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements().toList()) {
            if (!profession.value().heldJobSite().test(type)) continue;
            for (var state : type.value().matchingStates()) return state.getBlock().asItem();
        }
        return null;
    }
}

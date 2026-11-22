package com.sheepfromheaven.minenow.villager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.Optional;

/**
 * Vanilla only announces the death of players and of pets to their owner. A villager with a name (every one has, from
 * {@link VillagerNames}, unless a name tag renamed it) gets the same message a player would, "Jim was slain by Zombie",
 * sent to the players within {@link #RANGE} blocks, unless the {@code showDeathMessages} rule is off. A baby villager
 * joining the world (not loaded from disk) is announced the same way: vanilla's breeding event is only fired for animals,
 * not villagers.
 */
public final class DeathMessages {
    public static final int RANGE = 128;

    private DeathMessages() {}

    static Optional<Component> message(Villager villager, DamageSource source) {
        if (!villager.hasCustomName()) return Optional.empty();
        return Optional.of(source.getLocalizedDeathMessage(villager));
    }

    static List<ServerPlayer> recipients(ServerLevel level, Villager villager) {
        return level.players().stream().filter(player -> player.distanceToSqr(villager) <= (double) RANGE * RANGE).toList();
    }

    static Optional<Component> birth(Villager villager) {
        if (!villager.isBaby() || !villager.hasCustomName()) return Optional.empty();
        return Optional.of(Component.translatableWithFallback("message.minenow.birth", "%s was born", villager.getDisplayName()));
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof Villager villager) || !villager.isBaby() || !(event.getLevel() instanceof ServerLevel level)) return;
        VillagerNames.nameIfMissing(villager);
        if (birth(villager).isPresent()) ChatBubbles.noteBirth(level, villager);
        birth(villager).ifPresent(message -> level.players().stream()
                .filter(player -> player.distanceToSqr(villager) <= (double) RANGE * RANGE)
                .forEach(player -> player.sendSystemMessage(message)));
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if (message(villager, event.getSource()).isPresent()) ChatBubbles.noteDeath(level, villager);
        if (!level.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)) return;
        message(villager, event.getSource()).ifPresent(message -> recipients(level, villager).forEach(player -> player.sendSystemMessage(message)));
    }
}

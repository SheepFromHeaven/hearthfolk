package com.sheepfromheaven.minenow.armorer;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.Economy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * Between crafts an armorer dresses the armor stands within {@link #RANGE} blocks of its blast furnace with the best
 * armor it holds, one piece at a time: an empty slot, or a piece of its own it now has better than. The piece it
 * replaces goes back into its goods. It never touches a stand the player dressed, nor another armorer's display.
 */
public class ShowArmor extends Behavior<Villager> {
    private static final int RANGE = 8;
    private static final int HEIGHT = 3;
    private static final float SPEED = 0.5F;
    private static final double REACH = 2.5;
    private static final int RESCAN = 200;
    private static final int GIVE_UP_AFTER_TICKS = 300;

    private @Nullable ArmorStand stand;
    private @Nullable EquipmentSlot slot;
    private int inventorySlot = -1;
    private long since;
    private long nextStartAllowed;

    private final net.minecraft.resources.ResourceKey<VillagerProfession> profession;
    private final java.util.function.ToDoubleFunction<ItemStack> worth;

    /** The armorer's displays: any armor, the most protective shown. */
    public ShowArmor() {
        this(VillagerProfession.ARMORER, Armorer::protection);
    }

    /** Another trade's displays: pieces worth something to {@code worth} (0 for what it doesn't show), the best shown. */
    public ShowArmor(net.minecraft.resources.ResourceKey<VillagerProfession> profession, java.util.function.ToDoubleFunction<ItemStack> worth) {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), GIVE_UP_AFTER_TICKS + 20);
        this.profession = profession;
        this.worth = worth;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(profession) || !body.getBrain().isActive(Activity.WORK)) return false;
        nextStartAllowed = level.getGameTime() + RESCAN;
        return pickDisplay(level, body);
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        since = timestamp;
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return stand != null && stand.isAlive() && body.getBrain().isActive(Activity.WORK) && !body.isTrading() && timestamp - since < GIVE_UP_AFTER_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (stand == null || slot == null) return;
        if (body.distanceTo(stand) > REACH) {
            if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) walk(body);
            return;
        }
        ItemStack piece = inventorySlot >= 0 ? body.getInventory().getItem(inventorySlot) : ItemStack.EMPTY;
        if (!piece.isEmpty() && Armorer.armorSlot(piece) == slot) {
            ItemStack old = stand.getItemBySlot(slot);
            stand.setItemSlot(slot, piece.split(1));
            stand.setData(Armorer.DISPLAYED_BY, body.getUUID());
            if (!old.isEmpty()) Economy.give(level, body, old);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            level.playSound(null, stand, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        nextStartAllowed = Math.min(nextStartAllowed, timestamp + 20);
        stand = null;
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        stand = null;
        slot = null;
        inventorySlot = -1;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    /** The nearest stand with a slot it can fill or improve, and the piece from its inventory to put there. */
    private boolean pickDisplay(ServerLevel level, Villager body) {
        stand = null;
        Optional<BlockPos> furnace = body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension()).map(GlobalPos::pos);
        if (furnace.isEmpty()) return false;
        for (ArmorStand candidate : level.getEntitiesOfClass(ArmorStand.class, new AABB(furnace.get()).inflate(RANGE, HEIGHT, RANGE), ArmorStand::isAlive).stream()
                .sorted(Comparator.comparingDouble(body::distanceToSqr)).toList()) {
            Villager owner = Armorer.owner(level, candidate);
            boolean mine = owner == body;
            if (owner != null && !mine) continue;
            if (!mine && EquipmentSlot.VALUES.stream().anyMatch(s -> s.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !candidate.getItemBySlot(s).isEmpty())) continue;
            double bestGain = 0.0;
            for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
                ItemStack piece = body.getInventory().getItem(i);
                EquipmentSlot target = Armorer.armorSlot(piece);
                if (target == null || worth.applyAsDouble(piece) <= 0) continue;
                double gain = worth.applyAsDouble(piece) - worth.applyAsDouble(candidate.getItemBySlot(target));
                if (gain > bestGain) {
                    bestGain = gain;
                    stand = candidate;
                    slot = target;
                    inventorySlot = i;
                }
            }
            if (stand != null) return true;
        }
        return false;
    }

    private void walk(Villager body) {
        if (stand == null) return;
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(stand, false), SPEED, 1));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(stand, true));
    }
}

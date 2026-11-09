package com.sheepfromheaven.minenow.weaponsmith;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.CraftSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The weaponsmith grinds two worn copies of a sword or axe it holds into one at its grindstone, the way a player can:
 * the life left in both plus vanilla's 5% bonus, and 5% more per level above novice, enchantments ground off. It buys
 * worn blades cheaply (trade file) and sells the restored ones.
 */
public class RestoreWorn extends Behavior<Villager> {
    private static final double REACH = 2.0;
    private static final float SPEED = 0.5F;
    private static final int RESCAN = 100;
    private static final int SWING_EVERY = 10;

    private int grinding;
    private long nextStartAllowed;

    public RestoreWorn() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 600);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.WEAPONSMITH) || !body.getBrain().isActive(Activity.WORK)) return false;
        nextStartAllowed = level.getGameTime() + RESCAN;
        return pair(body) != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        grinding = 0;
        grindstone(level, body).ifPresent(pos -> {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
        });
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return body.getBrain().isActive(Activity.WORK) && !body.isTrading() && pair(body) != null;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        Optional<BlockPos> grindstone = grindstone(level, body);
        if (grindstone.isEmpty() || body.distanceToSqr(Vec3.atBottomCenterOf(grindstone.get())) > REACH * REACH) {
            if (grindstone.isPresent() && !body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) start(level, body, timestamp);
            return;
        }
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (grinding % SWING_EVERY == 0) {
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            level.playSound(null, grindstone.get(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        if (++grinding < CraftSkill.ticks(body.getVillagerData().level())) return;
        grinding = 0;
        int[] slots = pair(body);
        if (slots == null) return;
        ItemStack first = body.getInventory().getItem(slots[0]);
        ItemStack second = body.getInventory().getItem(slots[1]);
        int max = first.getMaxDamage();
        int bonus = max * 5 / 100 * Math.clamp(body.getVillagerData().level(), 1, 5);
        int life = (max - first.getDamageValue()) + (max - second.getDamageValue()) + bonus;
        ItemStack restored = new ItemStack(first.getItem());
        restored.setDamageValue(Math.max(0, max - life));
        body.getInventory().setItem(slots[0], restored);
        body.getInventory().setItem(slots[1], ItemStack.EMPTY);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        grinding = 0;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    /** Two worn copies of the same sword or axe, as inventory slots, or null. */
    private static int[] pair(Villager body) {
        for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
            ItemStack one = body.getInventory().getItem(i);
            if (!blade(one) || !one.isDamaged()) continue;
            for (int j = i + 1; j < body.getInventory().getContainerSize(); j++) {
                ItemStack other = body.getInventory().getItem(j);
                if (other.is(one.getItem()) && other.isDamaged()) return new int[] {i, j};
            }
        }
        return null;
    }

    private static boolean blade(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
    }

    private static Optional<BlockPos> grindstone(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
    }
}

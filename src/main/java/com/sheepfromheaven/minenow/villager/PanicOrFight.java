package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla's panic trigger, except that a villager that is armed ({@link Gear#isArmed}) and not badly hurt fights the
 * hostile that threatens it instead of fleeing, in the FIGHT activity so neither panic, hiding nor a raid sends it
 * away. With a sword it draws it from its side and swings it as a player would ({@link Weapons}), wearing it; with a bow
 * (at its side, or a fletcher's among its goods) it shoots from where it stands, arrows from its quiver first. It chases no further than {@link #CHASE} blocks from where the fight
 * started, then goes back to its day and puts the weapon away. A fight only starts with an enemy it can see, so an
 * attacker it remembers but can't see doesn't draw it in as it goes about its day. A villager below a third of its health, or unarmed,
 * panics as in vanilla.
 */
public class PanicOrFight extends VillagerPanicTrigger {
    static final double CHASE = 8.0;
    private static final double REACH = 2.5;
    private static final double BOW_RANGE = 15.0;
    private static final int SHOT_EVERY = 30;
    private static final float SPEED = 0.7F;
    private static final float VELOCITY = 1.6F;
    private static final double DROP = 0.5 * 0.05 / (VELOCITY * VELOCITY);
    private static final float[] INACCURACY = {6.0F, 4.5F, 3.0F, 2.0F, 1.0F};

    private static final int REMEMBER_ANCHOR = 200;

    private @Nullable Vec3 anchor;
    private @Nullable Vec3 lastAnchor;
    private long endedAt;
    private long nextSwing;

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return super.canStillUse(level, body, timestamp) || anchor != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        LivingEntity enemy = enemy(body, origin(body, timestamp));
        if (enemy != null && body.hasLineOfSight(enemy) && ableToFight(body)) {
            beginFight(body, timestamp);
        } else {
            super.start(level, body, timestamp);
        }
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        super.tick(level, body, timestamp);
        LivingEntity enemy = enemy(body, origin(body, timestamp));
        if (enemy != null && anchor == null && !body.hasLineOfSight(enemy)) enemy = null;
        if (enemy == null || !ableToFight(body)) {
            if (anchor != null) {
                endFight(level, body, timestamp);
                if (isHurt(body) || hasHostile(body)) body.getBrain().setActiveActivityIfPossible(Activity.PANIC);
            }
            return;
        }
        if (anchor == null) beginFight(body, timestamp);
        if (!body.getBrain().isActive(Activity.FIGHT)) body.getBrain().setActiveActivityIfPossible(Activity.FIGHT);
        fight(level, body, enemy, timestamp);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        if (anchor != null) endFight(level, body, timestamp);
    }

    private static boolean ableToFight(Villager body) {
        return Gear.isArmed(body) && body.getHealth() > body.getMaxHealth() / 3.0F;
    }

    /** What attacked it, if that's a hostile, else the nearest hostile; only within reach of a chase from {@code from}. */
    private static @Nullable LivingEntity enemy(Villager body, Vec3 from) {
        Brain<?> brain = body.getBrain();
        LivingEntity attacker = brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).filter(entity -> entity instanceof Enemy).orElse(null);
        LivingEntity enemy = attacker != null && inReach(attacker, from) ? attacker : brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null);
        return enemy != null && enemy.isAlive() && inReach(enemy, from) ? enemy : null;
    }

    private static boolean inReach(LivingEntity enemy, Vec3 from) {
        return enemy.position().distanceTo(from) <= CHASE + REACH;
    }

    /** Where a chase is measured from: where this fight began, or the last one if it ended only moments ago, so fights that stop and start again can't walk it further and further away. */
    private Vec3 origin(Villager body, long timestamp) {
        if (anchor != null) return anchor;
        if (lastAnchor != null && timestamp - endedAt < REMEMBER_ANCHOR) return lastAnchor;
        return body.position();
    }

    private void beginFight(Villager body, long timestamp) {
        anchor = origin(body, timestamp);
        Brain<Villager> brain = body.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        brain.setActiveActivityIfPossible(Activity.FIGHT);
    }

    private void endFight(ServerLevel level, Villager body, long timestamp) {
        lastAnchor = anchor;
        endedAt = timestamp;
        anchor = null;
        ItemStack held = body.getMainHandItem();
        if (held.is(net.minecraft.tags.ItemTags.SWORDS) || held.is(Items.BOW)) body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        Brain<Villager> brain = body.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (brain.isActive(Activity.FIGHT)) brain.updateActivityFromSchedule(level.environmentAttributes(), level.getGameTime(), body.position());
    }

    private void fight(ServerLevel level, Villager body, LivingEntity enemy, long timestamp) {
        Brain<Villager> brain = body.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(enemy, true));
        ItemStack sidearm = Gear.sidearm(body);
        boolean shooting = !sidearm.is(net.minecraft.tags.ItemTags.SWORDS);
        ItemStack sword = sidearm;
        ItemStack weapon = shooting ? (Gear.isBow(sidearm) ? sidearm : new ItemStack(Items.BOW)) : sword;
        if (!ItemStack.isSameItemSameComponents(body.getMainHandItem(), weapon)) {
            body.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
            body.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        double distance = body.distanceTo(enemy);
        boolean inRange = (shooting ? distance <= BOW_RANGE : distance <= REACH) && body.hasLineOfSight(enemy);
        if (!inRange) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(enemy, false), SPEED, shooting ? 8 : 1));
            return;
        }
        if (shooting) brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        if (timestamp < nextSwing) return;
        if (shooting) {
            shoot(level, body, enemy);
            nextSwing = timestamp + SHOT_EVERY;
        } else {
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            enemy.hurtServer(level, body.damageSources().mobAttack(body), (float) Weapons.attackDamage(sword));
            sword.hurtAndBreak(1, level, body, broken ->
                    level.playSound(null, body.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 1.0F));
            if (sword.isEmpty()) body.setData(ModVillagers.SIDEARM, ItemStack.EMPTY);
            body.setItemSlot(EquipmentSlot.MAINHAND, sword.isEmpty() ? ItemStack.EMPTY : sword.copy());
            nextSwing = timestamp + Weapons.swingTicks(sword);
        }
    }

    private static void shoot(ServerLevel level, Villager body, LivingEntity enemy) {
        ItemStack arrows = !Gear.quiver(body).isEmpty() ? Gear.quiver(body)
                : body.getInventory().getItems().stream().filter(stack -> stack.is(Items.ARROW)).findFirst().orElse(ItemStack.EMPTY);
        if (arrows.isEmpty()) return;
        Arrow arrow = new Arrow(level, body, arrows.copyWithCount(1), new ItemStack(Items.BOW));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        Vec3 aim = enemy.getEyePosition().subtract(0, enemy.getBbHeight() * 0.3, 0).subtract(arrow.position());
        double flat = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        int skill = Math.clamp(body.getVillagerData().level(), 1, INACCURACY.length) - 1;
        arrow.shoot(aim.x, aim.y + DROP * flat * flat, aim.z, VELOCITY, INACCURACY[skill]);
        level.addFreshEntity(arrow);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F / (body.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
        arrows.shrink(1);
        if (Gear.quiver(body).isEmpty()) body.setData(ModVillagers.QUIVER, ItemStack.EMPTY);
    }
}

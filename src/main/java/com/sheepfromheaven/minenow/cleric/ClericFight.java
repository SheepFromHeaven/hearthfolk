package com.sheepfromheaven.minenow.cleric;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.villager.WorkXp;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/**
 * What a cleric does in a fight, in place of swinging a sword or shooting a bow ({@code PanicOrFight} calls
 * {@link #act}): with splash potions in its pockets it counts as armed. A friend (villager or iron golem) within
 * {@link #HEAL_RANGE} blocks below half health gets a splash potion of healing; otherwise it throws splash harming at
 * the hostile, or splash poison without any, but never at the undead (healing hurts them, poison does nothing) and never
 * when a friend or a player stands within {@link #SAFE} blocks of it.
 */
public final class ClericFight {
    public static final double THROW_RANGE = 10.0;
    public static final int CARRIED = 8;
    static final double HEAL_RANGE = 8.0;
    static final double SAFE = 4.0;
    private static final float VELOCITY = 0.75F;

    private ClericFight() {}

    public static boolean isCleric(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.CLERIC);
    }

    /** A cleric with a splash potion of healing, harming or poison on it. */
    public static boolean armed(Villager villager) {
        return isCleric(villager) && MineNowConfig.professionEnabled(villager.getVillagerData().profession()) && (has(villager, Potions.HEALING) || has(villager, Potions.HARMING) || has(villager, Potions.POISON));
    }

    private static boolean has(Villager villager, Holder<Potion> potion) {
        return villager.getInventory().hasAnyMatching(stack -> PotionGoods.is(stack, Items.SPLASH_POTION, potion));
    }

    /** Throws one potion if the moment calls for it; returns whether it did. */
    public static boolean act(ServerLevel level, Villager cleric, LivingEntity enemy) {
        LivingEntity friend = hurtFriend(level, cleric);
        if (friend != null && has(cleric, Potions.HEALING)) {
            throwAt(level, cleric, Potions.HEALING, friend);
            WorkXp.award(cleric, WorkXp.HEALED);
            return true;
        }
        if (enemy.is(EntityTypeTags.UNDEAD) || cleric.distanceTo(enemy) > THROW_RANGE || !cleric.hasLineOfSight(enemy) || friendsNear(level, cleric, enemy)) return false;
        Holder<Potion> offence = has(cleric, Potions.HARMING) ? Potions.HARMING : has(cleric, Potions.POISON) ? Potions.POISON : null;
        if (offence == null) return false;
        throwAt(level, cleric, offence, enemy);
        return true;
    }

    private static LivingEntity hurtFriend(ServerLevel level, Villager cleric) {
        return level.getEntitiesOfClass(LivingEntity.class, cleric.getBoundingBox().inflate(HEAL_RANGE),
                        other -> other != cleric && other.isAlive() && (other instanceof Villager || other instanceof IronGolem) && other.getHealth() < other.getMaxHealth() / 2.0F)
                .stream().min(Comparator.comparingDouble(cleric::distanceToSqr)).orElse(null);
    }

    private static boolean friendsNear(ServerLevel level, Villager cleric, LivingEntity enemy) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(enemy.blockPosition()).inflate(SAFE),
                other -> other != enemy && other.isAlive() && (other instanceof Villager || other instanceof IronGolem || other instanceof Player)).isEmpty();
    }

    private static void throwAt(ServerLevel level, Villager cleric, Holder<Potion> potion, LivingEntity target) {
        ItemStack thrown = ItemStack.EMPTY;
        for (int i = 0; i < cleric.getInventory().getContainerSize(); i++) {
            ItemStack stack = cleric.getInventory().getItem(i);
            if (PotionGoods.is(stack, Items.SPLASH_POTION, potion)) {
                thrown = stack.copyWithCount(1);
                stack.shrink(1);
                break;
            }
        }
        if (thrown.isEmpty()) return;
        ThrownSplashPotion entity = new ThrownSplashPotion(level, cleric, thrown);
        double dx = target.getX() - cleric.getX();
        double dz = target.getZ() - cleric.getZ();
        double dy = target.getEyeY() - 1.1 - entity.getY();
        Projectile.spawnProjectileUsingShoot(entity, level, thrown, dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, VELOCITY, 2.0F);
        cleric.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }
}

package com.sheepfromheaven.minenow.merchant;

import com.sheepfromheaven.minenow.cleric.Brews;
import com.sheepfromheaven.minenow.cleric.PotionGoods;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.Comparator;
import java.util.Optional;

/**
 * Nights on the road. A merchant on tour at night lays down a straw bed it carries (vanilla's, three hay bales make
 * four, and each falls apart after one night) and sleeps on it, and drinks an invisibility potion from the cleric so hostiles
 * don't notice it (also by day when one comes close). The bed is never its home: it sleeps on it directly, so
 * {@link Villages} doesn't take a bed in a meadow for a place of its village.
 */
final class Camp {
    static final long DUSK = 12500;
    static final long DAWN = 23000;
    private static final int POTION_PRICE = 4;
    private static final int FETCH_TICKS = 600;
    private static final double NEAR = 3.0;

    private Camp() {}

    static boolean isNight(ServerLevel level) {
        long time = level.getOverworldClockTime() % 24000;
        return time >= DUSK && time < DAWN;
    }

    /** True while the night (or the morning wake-up) takes its attention. */
    static boolean step(ServerLevel level, Villager merchant) {
        if (!isNight(level) && merchant.getData(Merchant.CAMP).isPresent()) {
            wake(level, merchant);
            return true;
        }
        if (merchant.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).isPresent() || isNight(level)) drinkInvisibility(level, merchant);
        if (!isNight(level)) return false;
        if (!merchant.isSleeping() && Economy.count(level, merchant, Items.STRAW_BED) > 0) layDown(level, merchant);
        return merchant.isSleeping();
    }

    private static void drinkInvisibility(ServerLevel level, Villager merchant) {
        if (merchant.hasEffect(MobEffects.INVISIBILITY)) return;
        ItemStack potion = PotionGoods.takeOne(level, merchant, Items.POTION, Potions.INVISIBILITY);
        if (potion.isEmpty()) return;
        PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
        if (contents != null) contents.forEachEffect(effect -> merchant.addEffect(new MobEffectInstance(effect)), 1.0F);
        level.playSound(null, merchant.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        Economy.give(level, merchant, new ItemStack(Items.GLASS_BOTTLE));
    }

    private static void layDown(ServerLevel level, Villager merchant) {
        BlockPos at = merchant.blockPosition();
        for (BlockPos foot : new BlockPos[] {at, at.relative(merchant.getDirection())}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos head = foot.relative(facing);
                if (!free(level, foot) || !free(level, head)) continue;
                if (Economy.take(level, merchant, Items.STRAW_BED, 1) < 1) return;
                BlockState bed = Blocks.STRAW_BED.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
                level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
                level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
                merchant.setData(Merchant.CAMP, Optional.of(head));
                merchant.startSleeping(head);
                return;
            }
        }
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** Vanilla's straw bed is used once: it falls apart when its sleeper gets up, so what's left of the camp is cleared and a new bed is needed tomorrow. */
    private static void wake(ServerLevel level, Villager merchant) {
        Optional<BlockPos> head = merchant.getData(Merchant.CAMP);
        merchant.setData(Merchant.CAMP, Optional.empty());
        if (merchant.isSleeping()) merchant.stopSleeping();
        if (head.isEmpty() || !level.getBlockState(head.get()).is(Blocks.STRAW_BED)) return;
        BlockState state = level.getBlockState(head.get());
        BlockPos foot = head.get().relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
        level.removeBlock(head.get(), false);
        if (level.getBlockState(foot).is(Blocks.STRAW_BED)) level.removeBlock(foot, false);
    }

    /**
     * Before it sets out, a merchant with no invisibility potion buys one from a cleric of its village that has one, at the
     * cleric's price, walking over to it. True while it is still on the way.
     */
    static boolean fetchInvisibility(ServerLevel level, Villager merchant, Tour tour, long now) {
        if (now - tour.since() > FETCH_TICKS || PotionGoods.count(level, merchant, Items.POTION, Potions.INVISIBILITY) > 0) return false;
        int price = Brews.all().stream().filter(brew -> brew.output().potion().equals(Potions.INVISIBILITY)).findFirst().flatMap(Brews.Brew::price).orElse(POTION_PRICE);
        if (Economy.villagerBudget(Economy.spendable(level, merchant)) < price) return false;
        Optional<Villages.Village> home = Villages.of(level, merchant);
        if (home.isEmpty()) return false;
        Optional<Villager> cleric = home.get().villagers(level).stream()
                .filter(v -> v != merchant && v.isAlive() && v.getVillagerData().profession().is(net.minecraft.world.entity.npc.villager.VillagerProfession.CLERIC)
                        && PotionGoods.count(level, v, Items.POTION, Potions.INVISIBILITY) > 0)
                .min(Comparator.comparingDouble(merchant::distanceToSqr));
        if (cleric.isEmpty()) return false;
        if (merchant.distanceTo(cleric.get()) > NEAR) {
            if (now % 10 == 0) BehaviorUtils.setWalkAndLookTargetMemories(merchant, cleric.get(), 0.5F, 2);
            return true;
        }
        ItemStack potion = PotionGoods.takeOne(level, cleric.get(), Items.POTION, Potions.INVISIBILITY);
        if (potion.isEmpty()) return false;
        Economy.pay(level, merchant, cleric.get(), price);
        Economy.give(level, merchant, potion);
        return false;
    }
}

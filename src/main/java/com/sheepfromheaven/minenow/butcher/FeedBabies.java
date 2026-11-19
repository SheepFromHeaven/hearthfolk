package com.sheepfromheaven.minenow.butcher;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.ModVillagers;
import com.sheepfromheaven.minenow.villager.WorkXp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * Once a day a butcher feeds each young animal of its pen one item of feed, which speeds its growing up the way a
 * player's feeding does, but only with feed left over after its herds' breeding ({@link TendHerd#feedNeededForBreeding}).
 */
public class FeedBabies extends Behavior<Villager> {
    private static final int DAY = 24000;
    private static final float SPEED = 0.6F;

    private @Nullable Animal young;
    private @Nullable Item feed;
    private long nextSearch;

    public FeedBabies() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), 400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextSearch || !body.getVillagerData().profession().is(VillagerProfession.BUTCHER) || body.isSleeping() || body.isBaby()) return false;
        nextSearch = level.getGameTime() + 40;
        Optional<BlockPos> smoker = TendHerd.jobSite(level, body);
        if (smoker.isEmpty()) return false;
        young = level.getEntitiesOfClass(Animal.class, TendHerd.pen(smoker.get()),
                        animal -> animal.isAlive() && animal.isBaby() && animal.getType().builtInRegistryHolder().is(TendHerd.LIVESTOCK) && !fedToday(level, animal) && leftover(level, body, animal) != null)
                .stream().min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
        feed = young == null ? null : leftover(level, body, young);
        return young != null;
    }

    private static @Nullable Item leftover(ServerLevel level, Villager body, Animal animal) {
        int reserved = TendHerd.feedNeededForBreeding(level, body);
        return body.getInventory().getItems().stream()
                .filter(stack -> !stack.isEmpty() && animal.isFood(stack) && Economy.count(level, body, stack.getItem()) > reserved)
                .map(ItemStack::getItem).findFirst().orElse(null);
    }

    private static boolean fedToday(ServerLevel level, Animal animal) {
        long fed = animal.getData(ModVillagers.FED_AT);
        return fed >= 0 && level.getGameTime() - fed < DAY;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return young != null && young.isAlive() && young.isBaby() && !fedToday(level, young) && leftover(level, body, young) != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        walk(body);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (young == null || feed == null) return;
        if (body.distanceToSqr(young) > 4.0) {
            walk(body);
            return;
        }
        if (Economy.take(level, body, feed, 1) == 0) return;
        young.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-young.getAge()));
        young.setData(ModVillagers.FED_AT, level.getGameTime());
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, young.getX(), young.getY() + 0.8, young.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
        WorkXp.award(body, 1);
        young = null;
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        young = null;
    }

    private void walk(Villager body) {
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(young, false), SPEED, 1));
    }
}

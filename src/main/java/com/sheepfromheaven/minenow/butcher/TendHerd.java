package com.sheepfromheaven.minenow.butcher;

import com.sheepfromheaven.minenow.villager.WorkXp;
import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.villager.FurnaceTending;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.RainShelter;
import com.sheepfromheaven.minenow.villager.Tools;
import com.sheepfromheaven.minenow.villager.Unloading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The butcher keeps the livestock ({@link #LIVESTOCK}) within {@link #RANGE} blocks of its smoker at a steady herd
 * ({@link ButcherHerd}): it culls surplus adults with its axe ({@link Butchering}), breeds small herds with the food it holds, smokes raw
 * meat in its smoker with the coal it holds and takes the cooked meat out, and carries its goods to its home chest.
 * Named or leashed animals are pets and never culled. Lives in CORE after MoveToTargetSink, like TendFields.
 */
public class TendHerd extends Behavior<Villager> {
    public static final TagKey<EntityType<?>> LIVESTOCK = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "butcher_livestock"));
    static final int RANGE = 8;
    private static final int HEIGHT = 3;
    private static final float SPEED = 0.5F;
    private static final double REACH = 2.0;
    private static final double MELEE_REACH = 3.0;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;
    private static final int SMOKER_INPUT = 0;

    private enum Task { CULL, BREED, SMOKE, DEPOSIT }

    private final Map<UUID, Long> unreachable = new HashMap<>();
    private final java.util.ArrayDeque<Long> kills = new java.util.ArrayDeque<>();
    private @Nullable Task task;
    private @Nullable Animal animal;
    private @Nullable BlockPos block;
    private long targetSince;
    private long nextStartAllowed;
    private int windUp;
    private final Map<UUID, Integer> swingsLanded = new HashMap<>();

    public TendHerd() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED
        ), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!com.sheepfromheaven.minenow.MineNowConfig.professionEnabled(body.getVillagerData().profession())) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.BUTCHER) || !body.getBrain().isActive(Activity.WORK)) return false;
        if (pickTarget(level, body)) return true;
        nextStartAllowed = level.getGameTime() + IDLE_RESCAN_DELAY;
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        targetSince = timestamp;
        Tools.showInHand(body, task == Task.CULL);
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return task != null && com.sheepfromheaven.minenow.MineNowConfig.professionEnabled(body.getVillagerData().profession()) && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && jobSite(level, body).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (task == null) return;
        body.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        if (animal != null && (!animal.isAlive() || !inPen(level, body, animal))) {
            advance(level, body, timestamp);
            return;
        }
        if (inReach(body)) {
            switch (task) {
                case CULL -> cull(level, body, timestamp);
                case BREED -> breed(level, body, timestamp);
                case SMOKE -> {
                    tendSmoker(level, body);
                    advance(level, body, timestamp);
                }
                case DEPOSIT -> {
                    Unloading.deposit(level, body, block);
                    advance(level, body, timestamp);
                }
            }
            return;
        }
        windUp = 0;
        if (timestamp - targetSince > GIVE_UP_AFTER_TICKS || (animal != null && pathCannotReach(body))) {
            if (animal != null && !swingsLanded.containsKey(animal.getUUID())) unreachable.put(animal.getUUID(), timestamp + UNREACHABLE_COOLDOWN);
            advance(level, body, timestamp);
        } else if (animal != null ? !body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) : block != null && !com.sheepfromheaven.minenow.villager.Walking.towards(body, block)) {
            walk(body);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        task = null;
        animal = null;
        block = null;
        windUp = 0;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        Tools.showInHand(body, false);
        nextStartAllowed = timestamp + 40;
    }

    private void cull(ServerLevel level, Villager body, long timestamp) {
        holdStill(body);
        if (!Tools.has(body)) {
            advance(level, body, timestamp);
            return;
        }
        ItemStack axe = body.getInventory().getItem(Tools.slot(body));
        if (++windUp < Butchering.swingTicks(Butchering.attackSpeed(axe))) return;
        windUp = 0;
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        Tools.wear(level, body);
        targetSince = timestamp;
        int landed = swingsLanded.merge(animal.getUUID(), 1, Integer::sum);
        if (landed < Butchering.swings(animal.getHealth(), Butchering.attackDamage(axe))) return;
        swingsLanded.remove(animal.getUUID());
        animal.hurtServer(level, level.damageSources().mobAttack(body), Float.MAX_VALUE);
        kills.addLast(level.getGameTime());
        WorkXp.award(body, WorkXp.CULLED);
        advance(level, body, timestamp);
    }

    private void breed(ServerLevel level, Villager body, long timestamp) {
        holdStill(body);
        Optional<ItemStack> food = food(body, animal);
        if (food.isPresent() && readyToBreed(animal)) {
            food.get().shrink(1);
            animal.setInLove(null);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        }
        advance(level, body, timestamp);
    }

    private void holdStill(Villager body) {
        animal.getNavigation().stop();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(animal, true));
    }

    private void tendSmoker(ServerLevel level, Villager body) {
        FurnaceTending.tend(level, body, block, RecipeType.SMOKING, stack -> true);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }

    private void advance(ServerLevel level, Villager body, long timestamp) {
        windUp = 0;
        if (pickTarget(level, body)) {
            targetSince = timestamp;
            Tools.showInHand(body, task == Task.CULL);
            walk(body);
        }
    }

    private int killsInTheLastDay(long now) {
        while (!kills.isEmpty() && kills.peekFirst() <= now - 24000) kills.removeFirst();
        return kills.size();
    }

    private boolean pickTarget(ServerLevel level, Villager body) {
        task = null;
        animal = null;
        block = null;
        Optional<BlockPos> smoker = jobSite(level, body);
        if (smoker.isEmpty()) return false;
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until < now);
        swingsLanded.keySet().removeIf(id -> level.getEntity(id) == null);
        if (Unloading.shouldUnload(body)) {
            Optional<BlockPos> home = Unloading.unloadTarget(level, body);
            if (home.isPresent()) return target(Task.DEPOSIT, null, home.get());
        }
        if (FurnaceTending.needsTending(level, body, smoker.get(), RecipeType.SMOKING, stack -> true)) return target(Task.SMOKE, null, smoker.get());

        Map<EntityType<?>, List<Animal>> herds = new HashMap<>();
        for (Animal candidate : level.getEntitiesOfClass(Animal.class, pen(smoker.get()),
                candidate -> candidate.isAlive() && candidate.getType().builtInRegistryHolder().is(LIVESTOCK))) {
            herds.computeIfAbsent(candidate.getType(), type -> new java.util.ArrayList<>()).add(candidate);
        }
        Comparator<Animal> nearest = Comparator.comparingDouble(body::distanceToSqr);
        for (List<Animal> herd : herds.values()) {
            int adults = (int) herd.stream().filter(member -> !member.isBaby()).count();
            int young = herd.size() - adults;
            if (Tools.has(body) && ButcherHerd.toCull(adults, young) > 0 && ButcherHerd.killsLeft(killsInTheLastDay(now)) > 0) {
                Comparator<Animal> woundedFirst = Comparator.<Animal, Boolean>comparing(member -> !swingsLanded.containsKey(member.getUUID())).thenComparing(nearest);
                Optional<Animal> victim = herd.stream().filter(member -> !member.isBaby() && !isPet(member) && reachable(member) && RainShelter.allows(level, member.blockPosition())).min(woundedFirst);
                if (victim.isPresent()) return target(Task.CULL, victim.get(), null);
            }
            if (ButcherHerd.wantsOffspring(adults, young)) {
                List<Animal> ready = herd.stream().filter(member -> readyToBreed(member) && food(body, member).isPresent() && reachable(member) && RainShelter.allows(level, member.blockPosition())).toList();
                int foodCount = ready.isEmpty() ? 0 : food(body, ready.getFirst()).map(ItemStack::getCount).orElse(0);
                boolean partnerWaiting = herd.stream().anyMatch(Animal::isInLove);
                if (!ready.isEmpty() && (partnerWaiting || (ready.size() >= 2 && foodCount >= 2))) {
                    return target(Task.BREED, ready.stream().min(nearest).get(), null);
                }
            }
        }
        return false;
    }

    private boolean target(Task task, @Nullable Animal animal, @Nullable BlockPos block) {
        this.task = task;
        this.animal = animal;
        this.block = block;
        return true;
    }

    private static boolean readyToBreed(Animal animal) {
        return !animal.isBaby() && animal.getAge() == 0 && animal.canFallInLove();
    }

    private static Optional<ItemStack> food(Villager body, Animal animal) {
        return body.getInventory().getItems().stream().filter(stack -> !stack.isEmpty() && animal.isFood(stack)).findFirst();
    }

    private static boolean isPet(Animal animal) {
        return animal.hasCustomName() || animal.isLeashed();
    }

    private boolean reachable(Animal animal) {
        return !unreachable.containsKey(animal.getUUID());
    }

    private static boolean inPen(ServerLevel level, Villager body, Animal animal) {
        return jobSite(level, body).map(smoker -> pen(smoker).contains(animal.position())).orElse(false);
    }

    /**
     * What the butcher begs for. It buys its axe and its fuel whatever it is doing, and does any task it holds what it needs for
     * (feed breeds, an axe culls, fuel smokes); only when it wants to do something and none of that is possible does it go to a
     * player, for the axe if there are animals to cull, for fuel if there is something to smoke.
     */
    public static void registerWants() {
        com.sheepfromheaven.minenow.villager.IdleReason.addWants(VillagerProfession.BUTCHER,
                new com.sheepfromheaven.minenow.villager.IdleReason.Wants(TendHerd::blocked, TendHerd::hasSurplus, TendHerd::hasSomethingToSmoke));
    }

    /** It wants to do something it lacks what it needs for, and has nothing else it wants and could do. */
    static boolean blocked(ServerLevel level, Villager body) {
        Optional<BlockPos> smoker = jobSite(level, body);
        if (smoker.isEmpty()) return false;
        boolean axe = Tools.has(body);
        boolean fuel = com.sheepfromheaven.minenow.villager.Economy.items(level, body).stream().anyMatch(item -> FurnaceTending.isFuel(new ItemStack(item)));
        boolean smoke = hasSomethingToSmoke(level, body);
        boolean cull = hasSurplus(level, body);
        boolean breed = false;
        boolean feed = false;
        Map<EntityType<?>, List<Animal>> herds = new HashMap<>();
        for (Animal candidate : level.getEntitiesOfClass(Animal.class, pen(smoker.get()), candidate -> candidate.isAlive() && candidate.getType().builtInRegistryHolder().is(LIVESTOCK))) {
            herds.computeIfAbsent(candidate.getType(), type -> new java.util.ArrayList<>()).add(candidate);
        }
        for (List<Animal> herd : herds.values()) {
            int adults = (int) herd.stream().filter(member -> !member.isBaby()).count();
            if (adults >= ButcherHerd.BREEDING_PAIR && ButcherHerd.wantsOffspring(adults, herd.size() - adults)) {
                breed = true;
                feed |= herd.stream().anyMatch(member -> food(body, member).isPresent());
            }
        }
        boolean possible = cull && axe || smoke && fuel || breed && feed;
        boolean wantedButImpossible = cull && !axe || smoke && !fuel || breed && !feed;
        return wantedButImpossible && !possible;
    }

    /** The feed its herds still need to breed: a pair's worth for each kind that has two adults and room to grow. */
    static int feedNeededForBreeding(ServerLevel level, Villager body) {
        Optional<BlockPos> smoker = jobSite(level, body);
        if (smoker.isEmpty()) return 0;
        Map<EntityType<?>, List<Animal>> herds = new HashMap<>();
        for (Animal candidate : level.getEntitiesOfClass(Animal.class, pen(smoker.get()), candidate -> candidate.isAlive() && candidate.getType().builtInRegistryHolder().is(LIVESTOCK))) {
            herds.computeIfAbsent(candidate.getType(), type -> new java.util.ArrayList<>()).add(candidate);
        }
        int needed = 0;
        for (List<Animal> herd : herds.values()) {
            int adults = (int) herd.stream().filter(member -> !member.isBaby()).count();
            if (adults >= ButcherHerd.BREEDING_PAIR && ButcherHerd.wantsOffspring(adults, herd.size() - adults)) needed += ButcherHerd.BREEDING_PAIR;
        }
        return needed;
    }

    /** Raw meat or fish in its goods, or in its smoker waiting for fuel. */
    static boolean hasSomethingToSmoke(ServerLevel level, Villager body) {
        boolean carried = com.sheepfromheaven.minenow.villager.Economy.items(level, body).stream().anyMatch(item -> smokable(level, new ItemStack(item)));
        return carried || jobSite(level, body).map(smoker -> level.getBlockEntity(smoker) instanceof AbstractFurnaceBlockEntity furnace
                && !furnace.getItem(SMOKER_INPUT).isEmpty()).orElse(false);
    }

    private static boolean smokable(ServerLevel level, ItemStack stack) {
        return level.recipeAccess().getRecipeFor(RecipeType.SMOKING, new net.minecraft.world.item.crafting.SingleRecipeInput(stack), level).isPresent();
    }

    /** Adult livestock beyond its breeding stock that aren't pets: the animals an axe is for. */
    static boolean hasSurplus(ServerLevel level, Villager body) {
        Optional<BlockPos> smoker = jobSite(level, body);
        if (smoker.isEmpty()) return false;
        Map<EntityType<?>, List<Animal>> herds = new HashMap<>();
        for (Animal candidate : level.getEntitiesOfClass(Animal.class, pen(smoker.get()), candidate -> candidate.isAlive() && candidate.getType().builtInRegistryHolder().is(LIVESTOCK))) {
            herds.computeIfAbsent(candidate.getType(), type -> new java.util.ArrayList<>()).add(candidate);
        }
        return herds.values().stream().anyMatch(herd -> {
            int adults = (int) herd.stream().filter(member -> !member.isBaby()).count();
            return ButcherHerd.toCull(adults, herd.size() - adults) > 0 && herd.stream().anyMatch(member -> !member.isBaby() && !isPet(member));
        });
    }

    /** No livestock within reach of its smoker. */
    public static boolean hasNoHerd(ServerLevel level, Villager body) {
        return jobSite(level, body).map(smoker -> level.getEntitiesOfClass(Animal.class, pen(smoker),
                animal -> animal.isAlive() && animal.getType().builtInRegistryHolder().is(LIVESTOCK)).isEmpty()).orElse(false);
    }

    static AABB pen(BlockPos smoker) {
        return new AABB(smoker).inflate(RANGE, HEIGHT, RANGE);
    }

    private boolean inReach(Villager body) {
        if (animal != null) return body.distanceTo(animal) <= (task == Task.CULL ? MELEE_REACH : REACH);
        return body.distanceToSqr(Vec3.atBottomCenterOf(block)) <= REACH * REACH && Math.abs(block.getY() - body.getY()) <= 1.5;
    }

    private boolean pathCannotReach(Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.PATH).filter(path -> path.isDone() && !path.canReach()).isPresent();
    }

    private void walk(Villager body) {
        if (animal != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(animal, false), SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(animal, true));
        } else if (block != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(block, SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(block));
        }
    }

    static Optional<BlockPos> jobSite(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}

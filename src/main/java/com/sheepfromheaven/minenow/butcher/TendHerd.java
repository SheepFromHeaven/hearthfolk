package com.sheepfromheaven.minenow.butcher;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.Tools;
import com.sheepfromheaven.minenow.villager.Unloading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;

    private enum Task { CULL, BREED, SMOKE, DEPOSIT }

    private final Map<UUID, Long> unreachable = new HashMap<>();
    private @Nullable Task task;
    private @Nullable Animal animal;
    private @Nullable BlockPos block;
    private long targetSince;
    private long nextStartAllowed;
    private int windUp;
    private int swings;

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
        return task != null && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && jobSite(level, body).isPresent();
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
        swings = 0;
        if (timestamp - targetSince > GIVE_UP_AFTER_TICKS || (animal != null && pathCannotReach(body))) {
            if (animal != null) unreachable.put(animal.getUUID(), timestamp + UNREACHABLE_COOLDOWN);
            advance(level, body, timestamp);
        } else if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            walk(body);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        task = null;
        animal = null;
        block = null;
        windUp = 0;
        swings = 0;
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
        if (++swings < Butchering.swings(animal.getHealth(), Butchering.attackDamage(axe))) return;
        animal.hurtServer(level, level.damageSources().mobAttack(body), Float.MAX_VALUE);
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
        if (!(level.getBlockEntity(block) instanceof AbstractFurnaceBlockEntity smoker)) return;
        ItemStack result = smoker.getItem(2);
        if (!result.isEmpty()) {
            ItemStack rest = body.getInventory().addItem(result.copy());
            smoker.setItem(2, rest);
        }
        for (ItemStack stack : body.getInventory().getItems()) {
            if (!smokable(level, stack)) continue;
            ItemStack input = smoker.getItem(0);
            if (input.isEmpty()) {
                smoker.setItem(0, stack.split(stack.getCount()));
            } else if (ItemStack.isSameItemSameComponents(input, stack)) {
                int moved = Math.min(stack.getCount(), input.getMaxStackSize() - input.getCount());
                input.grow(moved);
                stack.shrink(moved);
            }
        }
        if (smoker.getItem(1).isEmpty()) {
            for (ItemStack stack : body.getInventory().getItems()) {
                if (stack.is(ItemTags.COALS)) {
                    smoker.setItem(1, stack.split(stack.getCount()));
                    break;
                }
            }
        }
        smoker.setChanged();
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }

    private void advance(ServerLevel level, Villager body, long timestamp) {
        windUp = 0;
        swings = 0;
        if (pickTarget(level, body)) {
            targetSince = timestamp;
            Tools.showInHand(body, task == Task.CULL);
            walk(body);
        }
    }

    private boolean pickTarget(ServerLevel level, Villager body) {
        task = null;
        animal = null;
        block = null;
        Optional<BlockPos> smoker = jobSite(level, body);
        if (smoker.isEmpty()) return false;
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until < now);
        if (Unloading.shouldUnload(body)) {
            Optional<BlockPos> home = HomeStorage.storageIn(level, body).filter(pos -> level.getBlockState(pos).getBlock() instanceof ChestBlock);
            if (home.isPresent()) return target(Task.DEPOSIT, null, home.get());
        }
        if (smokerNeedsTending(level, body, smoker.get())) return target(Task.SMOKE, null, smoker.get());

        Map<EntityType<?>, List<Animal>> herds = new HashMap<>();
        for (Animal candidate : level.getEntitiesOfClass(Animal.class, pen(smoker.get()),
                candidate -> candidate.isAlive() && candidate.getType().builtInRegistryHolder().is(LIVESTOCK))) {
            herds.computeIfAbsent(candidate.getType(), type -> new java.util.ArrayList<>()).add(candidate);
        }
        Comparator<Animal> nearest = Comparator.comparingDouble(body::distanceToSqr);
        for (List<Animal> herd : herds.values()) {
            int adults = (int) herd.stream().filter(member -> !member.isBaby()).count();
            int young = herd.size() - adults;
            if (Tools.has(body) && ButcherHerd.toCull(adults, young) > 0) {
                Optional<Animal> victim = herd.stream().filter(member -> !member.isBaby() && !isPet(member) && reachable(member)).min(nearest);
                if (victim.isPresent()) return target(Task.CULL, victim.get(), null);
            }
            if (ButcherHerd.wantsOffspring(adults, young)) {
                List<Animal> ready = herd.stream().filter(member -> readyToBreed(member) && food(body, member).isPresent() && reachable(member)).toList();
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

    private boolean smokerNeedsTending(ServerLevel level, Villager body, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity smoker)) return false;
        if (!smoker.getItem(2).isEmpty()) return true;
        boolean fuelled = !smoker.getItem(1).isEmpty() || body.getInventory().hasAnyMatching(stack -> stack.is(ItemTags.COALS));
        if (!fuelled) return false;
        ItemStack input = smoker.getItem(0);
        return body.getInventory().hasAnyMatching(stack -> smokable(level, stack)
                && (input.isEmpty() || (ItemStack.isSameItemSameComponents(input, stack) && input.getCount() < input.getMaxStackSize())));
    }

    private static boolean smokable(ServerLevel level, ItemStack stack) {
        return !stack.isEmpty() && level.recipeAccess().getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(stack), level).isPresent();
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

    private static AABB pen(BlockPos smoker) {
        return new AABB(smoker).inflate(RANGE, HEIGHT, RANGE);
    }

    private boolean inReach(Villager body) {
        if (animal != null) return body.distanceTo(animal) <= REACH;
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

    private static Optional<BlockPos> jobSite(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}

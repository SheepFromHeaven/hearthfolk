package com.sheepfromheaven.minenow.shepherd;

import com.sheepfromheaven.minenow.villager.WorkXp;
import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.ToolCare;
import com.sheepfromheaven.minenow.villager.Tools;
import com.sheepfromheaven.minenow.villager.Unloading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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
 * The shepherd shears the sheep within {@link #RANGE} blocks of its loom: it walks up to a sheep, holds it still and
 * shears for as long as its level dictates ({@link ShepherdSkill}), keeping the wool it doesn't tear. With nothing to
 * shear it breeds its flock with wheat, shears or not, up to {@link ShepherdSkill#flockSize}; it never culls. When the
 * wool piles up it carries it to its home chest. Lives in CORE after MoveToTargetSink, like TendFields.
 */
public class ShearSheep extends Behavior<Villager> {
    static final int RANGE = 8;
    private static final int HEIGHT = 3;
    private static final float SPEED = 0.5F;
    private static final double SHEAR_REACH = 2.0;
    private static final int SWING_EVERY = 10;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;

    private final Map<UUID, Long> unreachableSheep = new HashMap<>();
    private @Nullable Sheep sheep;
    private boolean breeding;
    private @Nullable BlockPos chest;
    private long targetSince;
    private long nextStartAllowed;
    private int shearing;

    public ShearSheep() {
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
        if (!body.getVillagerData().profession().is(VillagerProfession.SHEPHERD) || !body.getBrain().isActive(Activity.WORK)) return false;
        if (pickTarget(level, body)) return true;
        nextStartAllowed = level.getGameTime() + IDLE_RESCAN_DELAY;
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        targetSince = timestamp;
        Tools.showInHand(body, true);
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return (sheep != null || chest != null) && com.sheepfromheaven.minenow.MineNowConfig.professionEnabled(body.getVillagerData().profession()) && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && jobSite(level, body).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        if (!Unloading.isTool(body, body.getMainHandItem())) Tools.showInHand(body, true);
        if (chest != null) {
            if (body.distanceToSqr(Vec3.atBottomCenterOf(chest)) <= 4.0 && Math.abs(chest.getY() - body.getY()) <= 1.5) {
                Unloading.deposit(level, body, chest);
                advance(level, body, timestamp);
            } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
                advance(level, body, timestamp);
            } else if (!com.sheepfromheaven.minenow.villager.Walking.towards(body, chest)) {
                walk(body);
            }
            return;
        }
        if (sheep == null) return;
        boolean stillWanted = breeding ? readyToBreed(sheep) && wheat(body).isPresent() : sheep.readyForShearing() && Tools.has(body);
        if (!sheep.isAlive() || !stillWanted || !inFlock(level, body, sheep)) {
            advance(level, body, timestamp);
            return;
        }
        if (body.distanceTo(sheep) <= SHEAR_REACH) {
            if (breeding) feed(level, body, timestamp);
            else shear(level, body, timestamp);
        } else {
            shearing = 0;
            if (pathCannotReach(body) || timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
                unreachableSheep.put(sheep.getUUID(), timestamp + UNREACHABLE_COOLDOWN);
                advance(level, body, timestamp);
            } else if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                walk(body);
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        sheep = null;
        breeding = false;
        chest = null;
        shearing = 0;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        Tools.showInHand(body, false);
        nextStartAllowed = timestamp + 40;
    }

    private void shear(ServerLevel level, Villager body, long timestamp) {
        sheep.getNavigation().stop();
        sheep.getLookControl().setLookAt(body);
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(sheep, true));
        if (shearing % SWING_EVERY == 0) body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        if (++shearing < ShepherdSkill.shearTicks(body.getVillagerData().level())) return;

        ItemStack shears = body.getInventory().getItem(Tools.slot(body));
        level.playSound(null, sheep, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
        sheep.gameEvent(GameEvent.SHEAR, body);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, sheep.position())
                .withParameter(LootContextParams.THIS_ENTITY, sheep)
                .withParameter(LootContextParams.TOOL, shears)
                .create(LootContextParamSets.SHEARING);
        float lossChance = ShepherdSkill.woolLossChance(body.getVillagerData().level());
        for (ItemStack wool : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SHEAR_SHEEP).getRandomItems(params)) {
            int kept = 0;
            for (int i = 0; i < wool.getCount(); i++) if (body.getRandom().nextFloat() >= lossChance) kept++;
            if (kept == 0) continue;
            ItemStack rest = body.getInventory().addItem(wool.copyWithCount(kept));
            if (!rest.isEmpty()) Block.popResource(level, body.blockPosition(), rest);
        }
        sheep.setSheared(true);
        if (body.getRandom().nextFloat() < ToolCare.wearChance(body.getVillagerData().level())) Tools.wear(level, body);
        WorkXp.award(body, WorkXp.SHEARED);
        advance(level, body, timestamp);
    }

    private void feed(ServerLevel level, Villager body, long timestamp) {
        sheep.getNavigation().stop();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(sheep, true));
        wheat(body).ifPresent(stack -> stack.shrink(1));
        sheep.setInLove(null);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        advance(level, body, timestamp);
    }

    private void advance(ServerLevel level, Villager body, long timestamp) {
        shearing = 0;
        if (pickTarget(level, body)) {
            targetSince = timestamp;
            walk(body);
        }
    }

    private boolean pickTarget(ServerLevel level, Villager body) {
        sheep = null;
        chest = null;
        breeding = false;
        Optional<BlockPos> loom = jobSite(level, body);
        if (loom.isEmpty()) return false;
        long now = level.getGameTime();
        unreachableSheep.values().removeIf(until -> until < now);
        if (Unloading.shouldUnload(body) || (!Tools.has(body) && Unloading.depositableCount(body) > 0)) {
            Optional<BlockPos> home = Unloading.unloadTarget(level, body);
            if (home.isPresent()) {
                chest = home.get();
                return true;
            }
        }
        List<Sheep> flock = flock(level, loom.get());
        Comparator<Sheep> nearest = Comparator.comparingDouble(body::distanceToSqr);
        if (Tools.has(body)) {
            sheep = flock.stream().filter(candidate -> candidate.readyForShearing() && workable(level, candidate)).min(nearest).orElse(null);
            if (sheep != null) return true;
        }
        if (canGrow(flock, body.getVillagerData().level())) {
            int wheat = wheat(body).map(ItemStack::getCount).orElse(0);
            List<Sheep> ready = flock.stream().filter(candidate -> readyToBreed(candidate) && workable(level, candidate)).toList();
            boolean partnerWaiting = flock.stream().anyMatch(Sheep::isInLove);
            if (!ready.isEmpty() && wheat > 0 && (partnerWaiting || (ready.size() >= 2 && wheat >= 2))) {
                sheep = ready.stream().min(nearest).get();
                breeding = true;
                return true;
            }
        }
        return false;
    }

    private boolean workable(ServerLevel level, Sheep candidate) {
        return !unreachableSheep.containsKey(candidate.getUUID()) && com.sheepfromheaven.minenow.villager.RainShelter.allows(level, candidate.blockPosition());
    }

    /** The penned sheep within reach of its loom, lambs included. */
    private static List<Sheep> flock(ServerLevel level, BlockPos loom) {
        return level.getEntitiesOfClass(Sheep.class, flockArea(loom), candidate -> candidate.isAlive() && com.sheepfromheaven.minenow.villager.Pens.isPenned(level, candidate));
    }

    /** Two adults to breed and room for a lamb under its level's flock size. */
    private static boolean canGrow(List<Sheep> flock, int villagerLevel) {
        return flock.stream().filter(member -> !member.isBaby()).count() >= 2 && flock.size() < ShepherdSkill.flockSize(villagerLevel);
    }

    /** Whether its flock can grow, so wheat to breed it is worth buying ({@link com.sheepfromheaven.minenow.villager.SupplyNeeds}). */
    public static boolean needsWheat(ServerLevel level, Villager body) {
        return jobSite(level, body).map(loom -> canGrow(flock(level, loom), body.getVillagerData().level())).orElse(false);
    }

    private static boolean readyToBreed(Sheep sheep) {
        return !sheep.isBaby() && sheep.getAge() == 0 && sheep.canFallInLove();
    }

    private static Optional<ItemStack> wheat(Villager body) {
        return body.getInventory().getItems().stream().filter(stack -> stack.is(Items.WHEAT)).findFirst();
    }

    private static boolean inFlock(ServerLevel level, Villager body, Sheep sheep) {
        return jobSite(level, body).map(loom -> flockArea(loom).contains(sheep.position()) && com.sheepfromheaven.minenow.villager.Pens.isPenned(level, sheep)).orElse(false);
    }

    /** No adult sheep within reach of its loom, shearable or not. */
    public static boolean hasNoFlock(ServerLevel level, Villager body) {
        return jobSite(level, body).map(loom -> level.getEntitiesOfClass(Sheep.class, flockArea(loom), sheep -> !sheep.isBaby() && com.sheepfromheaven.minenow.villager.Pens.isPenned(level, sheep)).isEmpty()).orElse(false);
    }

    private static AABB flockArea(BlockPos loom) {
        return new AABB(loom).inflate(RANGE, HEIGHT, RANGE);
    }

    private boolean pathCannotReach(Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.PATH).filter(path -> path.isDone() && !path.canReach()).isPresent();
    }

    private void walk(Villager body) {
        if (chest != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(chest, SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(chest));
        } else if (sheep != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(sheep, false), SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(sheep, true));
        }
    }

    private static Optional<BlockPos> jobSite(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}

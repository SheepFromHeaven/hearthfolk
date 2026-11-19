package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A villager's goods stay its own when the player moves its chest: they drop as items only it can pick up (vanilla's
 * item target keeps players off them) that never despawn, and it gathers them up again ({@link Gather}); its unloading
 * then puts them in its next home chest. When the villager dies or turns into a zombie they become ordinary items.
 */
public final class Belongings {
    private static final int SEARCH = 32;

    private static final Supplier<AttachmentType<UUID>> BELONGS_TO = ModVillagers.BELONGS_TO;

    private Belongings() {}

    /** Drops everything in {@code container} at {@code pos} as {@code owner}'s, leaving the container empty. */
    static void drop(ServerLevel level, BlockPos pos, Container container, Villager owner) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.removeItemNoUpdate(slot);
            if (stack.isEmpty()) continue;
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            item.setTarget(owner.getUUID());
            item.setUnlimitedLifetime();
            item.setData(BELONGS_TO, owner.getUUID());
            level.addFreshEntity(item);
        }
        container.setChanged();
    }

    /**
     * Gives the dropped goods within {@code radius} blocks of {@code pos} back to the players: no owner, no pickup lock, so
     * the villager doesn't gather them either. For items a villager claimed that were the player's, e.g. a chest it took
     * while it already held the player's things.
     */
    public static int freeNear(ServerLevel level, Vec3 pos, int radius) {
        int freed = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(pos)).inflate(radius), item -> item.hasData(BELONGS_TO))) {
            item.removeData(BELONGS_TO);
            item.setTarget(null);
            freed++;
        }
        return freed;
    }

    /** {@code /minenow free}: frees the villagers' dropped goods within {@link #SEARCH} blocks. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("minenow").then(Commands.literal("free").executes(context -> {
            int freed = freeNear(context.getSource().getLevel(), context.getSource().getPosition(), SEARCH);
            context.getSource().sendSuccess(() -> Component.literal(freed == 0 ? "No claimed items within " + SEARCH + " blocks" : "Freed " + freed + " claimed item stacks, anyone can pick them up now"), false);
            return freed;
        })));
    }

    /** A dead (or zombified) villager's goods are anyone's. */
    static void release(ServerLevel level, Villager owner) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(owner.blockPosition()).inflate(SEARCH * 2), item -> isOf(item, owner))) {
            item.removeData(BELONGS_TO);
            item.setTarget(null);
        }
    }

    /**
     * A villager's dropped goods vanishing into a hopper (or hopper minecart) are stolen by whoever placed it, like a
     * hopper draining its chest. Its own gathering unmarks an item before taking it, so that never counts. The owner comes
     * from vanilla's item target, since the attachment is gone by the time the item leaves the level.
     */
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level) || item.getRemovalReason() != Entity.RemovalReason.DISCARDED) return;
        UUID target = item.getTarget();
        if (target == null || !(level.getEntity(target) instanceof Villager owner) || !owner.isAlive()) return;
        Set<UUID> thieves = new LinkedHashSet<>();
        thieves.addAll(StorageTheft.hopperPlacersAt(level, item.blockPosition()));
        thieves.addAll(StorageTheft.hopperPlacersAt(level, item.blockPosition().below()));
        for (UUID thief : thieves) StorageTheft.punish(level, owner, thief, StorageTheft.BREAK_GOSSIP);
    }

    private static boolean isOf(ItemEntity item, Villager owner) {
        return item.hasData(BELONGS_TO) && item.getData(BELONGS_TO).equals(owner.getUUID());
    }

    /** Any adult villager walks over to its own dropped goods and takes them back while it's awake. */
    public static class Gather extends Behavior<Villager> {
        private static final float SPEED = 0.5F;
        private static final double REACH = 1.5;
        private static final int RESCAN = 40;
        private static final int GIVE_UP_AFTER_TICKS = 400;

        private @Nullable ItemEntity target;
        private long since;
        private long nextStartAllowed;

        public Gather() {
            super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), GIVE_UP_AFTER_TICKS);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
            if (level.getGameTime() < nextStartAllowed || body.isSleeping() || body.isTrading() || body.getBrain().isActive(Activity.REST)) return false;
            nextStartAllowed = level.getGameTime() + RESCAN;
            target = level.getEntitiesOfClass(ItemEntity.class, new AABB(body.blockPosition()).inflate(SEARCH), item -> isOf(item, body) && body.getInventory().canAddItem(item.getItem()))
                    .stream().min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
            return target != null;
        }

        @Override
        protected void start(ServerLevel level, Villager body, long timestamp) {
            since = timestamp;
            walk(body);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
            return target != null && target.isAlive() && !body.isTrading() && timestamp - since < GIVE_UP_AFTER_TICKS;
        }

        @Override
        protected void tick(ServerLevel level, Villager body, long timestamp) {
            if (target == null) return;
            if (body.position().distanceTo(target.position()) > REACH) {
                if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) walk(body);
                return;
            }
            ItemStack rest = body.getInventory().addItem(target.getItem().copy());
            body.take(target, target.getItem().getCount() - rest.getCount());
            if (rest.isEmpty()) {
                target.setTarget(null);
                target.discard();
            }
            else target.setItem(rest);
            target = null;
            nextStartAllowed = timestamp;
        }

        @Override
        protected void stop(ServerLevel level, Villager body, long timestamp) {
            target = null;
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }

        private void walk(Villager body) {
            if (target == null) return;
            Vec3 at = target.position();
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(at), SPEED, 0));
        }
    }
}

package com.sheepfromheaven.minenow.villager;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Players can't open an owned chest and villagers only add to it, so any drop in its contents between
 * two checks was taken by something else: a hopper or hopper minecart underneath. Its placer is the thief.
 */
public final class StorageTheft {
    public static final int BREAK_GOSSIP = 25;
    private static final int MAX_DRAIN_GOSSIP_PER_CHECK = 25;

    private record PendingCart(ResourceKey<Level> dimension, BlockPos rail, UUID player, long tick) {}

    private static final Map<GlobalPos, Object2IntMap<Item>> SNAPSHOTS = new HashMap<>();
    private static final List<PendingCart> PENDING_CARTS = new ArrayList<>();

    private StorageTheft() {}

    public static void punish(ServerLevel level, Villager owner, @Nullable UUID thief, int amount) {
        if (thief != null) owner.getGossips().add(thief, GossipType.MINOR_NEGATIVE, amount);
        level.broadcastEntityEvent(owner, (byte) 13);
    }

    public static void check(ServerLevel level, Villager owner, BlockPos chest) {
        Object2IntMap<Item> now = contents(level, chest);
        if (now == null) return;
        Object2IntMap<Item> before = SNAPSHOTS.put(GlobalPos.of(level.dimension(), chest), now);
        if (before == null) return;
        int stolen = 0;
        for (Object2IntMap.Entry<Item> entry : before.object2IntEntrySet()) {
            stolen += Math.max(0, entry.getIntValue() - now.getInt(entry.getKey()));
        }
        if (stolen == 0) return;
        Set<UUID> suspects = placersBelow(level, chest);
        int amount = Math.min(stolen, MAX_DRAIN_GOSSIP_PER_CHECK);
        if (suspects.isEmpty()) punish(level, owner, null, amount);
        for (UUID suspect : suspects) punish(level, owner, suspect, amount);
    }

    public static void recordContents(ServerLevel level, BlockPos chest) {
        Object2IntMap<Item> now = contents(level, chest);
        if (now != null) SNAPSHOTS.put(GlobalPos.of(level.dimension(), chest), now);
    }

    static void forget(ServerLevel level, BlockPos chest) {
        SNAPSHOTS.remove(GlobalPos.of(level.dimension(), chest));
    }

    static boolean hasHopperBelow(ServerLevel level, BlockPos chest) {
        return HomeStorage.halves(level, chest).stream().anyMatch(half -> level.getBlockState(half.below()).is(Blocks.HOPPER));
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlacedBlock().is(Blocks.HOPPER)
                && event.getEntity() instanceof Player player && level.getBlockEntity(event.getPos()) != null) {
            level.getBlockEntity(event.getPos()).setData(ModVillagers.PLACED_BY, player.getUUID());
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && event.getItemStack().is(Items.HOPPER_MINECART)) {
            PENDING_CARTS.add(new PendingCart(level.dimension(), event.getPos(), event.getEntity().getUUID(), level.getGameTime()));
        }
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || PENDING_CARTS.isEmpty()) return;
        long now = level.getGameTime();
        PENDING_CARTS.removeIf(pending -> now - pending.tick() > 1);
        if (!(event.getEntity() instanceof MinecartHopper cart)) return;
        PENDING_CARTS.stream()
                .filter(pending -> pending.dimension() == level.dimension() && cart.blockPosition().equals(pending.rail()))
                .findFirst()
                .ifPresent(pending -> {
                    cart.setData(ModVillagers.PLACED_BY, pending.player());
                    PENDING_CARTS.remove(pending);
                });
    }

    private static Set<UUID> placersBelow(ServerLevel level, BlockPos chest) {
        Set<UUID> placers = new LinkedHashSet<>();
        for (BlockPos half : HomeStorage.halves(level, chest)) placers.addAll(hopperPlacersAt(level, half.below()));
        return placers;
    }

    /** Who placed the hopper, or the hopper minecart, at {@code pos}. */
    static Set<UUID> hopperPlacersAt(ServerLevel level, BlockPos pos) {
        Set<UUID> placers = new LinkedHashSet<>();
        if (level.getBlockState(pos).is(Blocks.HOPPER) && level.getBlockEntity(pos) != null) {
            level.getBlockEntity(pos).getExistingData(ModVillagers.PLACED_BY).ifPresent(placers::add);
        }
        for (MinecartHopper cart : level.getEntitiesOfClass(MinecartHopper.class, new AABB(pos))) {
            cart.getExistingData(ModVillagers.PLACED_BY).ifPresent(placers::add);
        }
        return placers;
    }

    private static @Nullable Object2IntMap<Item> contents(ServerLevel level, BlockPos chest) {
        Container container = HomeStorage.chestContainer(level, chest);
        if (container == null) return null;
        Object2IntMap<Item> counts = new Object2IntOpenHashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) counts.mergeInt(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return counts;
    }
}

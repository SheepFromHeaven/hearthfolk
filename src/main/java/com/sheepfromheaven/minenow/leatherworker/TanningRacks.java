package com.sheepfromheaven.minenow.leatherworker;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.Walking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The leatherworker's tanning racks: item frames on the walls within {@link #RANGE} blocks of its cauldron (up to
 * {@link #HEIGHT} above it) holding leather or rabbit hide from its goods, as many as its level. It uses empty frames
 * the player hung there first and only hangs frames of its own when there aren't enough. A rack is locked while its
 * leatherworker lives (players can't take the hide, right-clicking says whose it is); breaking it costs nothing: the hide
 * goes back to the leatherworker, and a frame it hung itself too (a player's frame drops as usual).
 */
public class TanningRacks extends Behavior<Villager> {
    private static final int RANGE = 6;
    private static final int HEIGHT = 2;
    private static final double REACH = 3.0;
    private static final float SPEED = 0.5F;
    private static final int RESCAN = 200;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final List<Item> HIDES = List.of(Items.RABBIT_HIDE, Items.LEATHER);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MineNow.MOD_ID);
    static final Supplier<AttachmentType<UUID>> RACKED_BY = ATTACHMENT_TYPES.register("racked_by",
            () -> AttachmentType.builder(() -> new UUID(0L, 0L)).serialize(UUIDUtil.CODEC.fieldOf("leatherworker")).build());
    /** Set on a frame the leatherworker hung itself, which goes back to it when broken; a player's frame is the player's. */
    static final Supplier<AttachmentType<Boolean>> OWN_FRAME = ATTACHMENT_TYPES.register("own_frame",
            () -> AttachmentType.builder(() -> false).serialize(com.mojang.serialization.Codec.BOOL.fieldOf("own")).build());

    private @Nullable ItemFrame frame;
    private @Nullable BlockPos spot;
    private @Nullable Direction facing;
    private long since;
    private long nextStartAllowed;

    public TanningRacks() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), GIVE_UP_AFTER_TICKS + 20);
    }

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.LEATHERWORKER) || !body.getBrain().isActive(Activity.WORK)) return false;
        nextStartAllowed = level.getGameTime() + RESCAN;
        return pick(level, body);
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        since = timestamp;
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return (frame != null || spot != null) && body.getBrain().isActive(Activity.WORK) && !body.isTrading() && timestamp - since < GIVE_UP_AFTER_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        BlockPos at = frame != null ? frame.blockPosition() : spot;
        if (at == null) return;
        if (body.getEyePosition().distanceTo(Vec3.atCenterOf(at)) > REACH) {
            if (!Walking.towards(body, at)) walk(body);
            return;
        }
        Optional<Item> hide = HIDES.stream().filter(item -> body.getInventory().countItem(item) > 0).findFirst();
        if (hide.isPresent()) {
            if (frame != null && frame.isAlive() && frame.getItem().isEmpty()) {
                hang(level, body, frame, hide.get());
            } else if (spot != null && facing != null && body.getInventory().countItem(Items.ITEM_FRAME) > 0) {
                ItemFrame placed = new ItemFrame(level, spot, facing);
                if (placed.survives()) {
                    take(body, Items.ITEM_FRAME);
                    placed.setData(OWN_FRAME, true);
                    level.addFreshEntity(placed);
                    level.playSound(null, spot, SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    hang(level, body, placed, hide.get());
                }
            }
        }
        frame = null;
        spot = null;
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        frame = null;
        spot = null;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static void hang(ServerLevel level, Villager body, ItemFrame rack, Item hide) {
        take(body, hide);
        rack.setItem(new ItemStack(hide), false);
        rack.setData(RACKED_BY, body.getUUID());
        level.playSound(null, rack.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }

    private static void take(Villager body, Item item) {
        body.getInventory().getItems().stream().filter(stack -> stack.is(item)).findFirst().ifPresent(stack -> stack.shrink(1));
    }

    /** An empty rack of its own to refill, else an empty frame of the player's, else a free wall spot, while under its limit. */
    private boolean pick(ServerLevel level, Villager body) {
        frame = null;
        spot = null;
        Optional<BlockPos> cauldron = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        if (cauldron.isEmpty() || HIDES.stream().noneMatch(item -> body.getInventory().countItem(item) > 0)) return false;
        AABB area = new AABB(cauldron.get()).inflate(RANGE, 0, RANGE).expandTowards(0, HEIGHT, 0);
        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area, ItemFrame::isAlive);
        List<ItemFrame> mine = frames.stream().filter(rack -> owner(level, rack) == body).toList();
        Comparator<ItemFrame> nearest = Comparator.comparingDouble(rack -> rack.distanceToSqr(body));
        Optional<ItemFrame> refill = mine.stream().filter(rack -> rack.getItem().isEmpty()).min(nearest);
        if (refill.isPresent()) {
            frame = refill.get();
            return true;
        }
        if (mine.size() >= Math.clamp(body.getVillagerData().level(), 1, 5)) return false;
        Optional<ItemFrame> yours = frames.stream().filter(rack -> rack.getItem().isEmpty() && owner(level, rack) == null).min(nearest);
        if (yours.isPresent()) {
            frame = yours.get();
            return true;
        }
        if (body.getInventory().countItem(Items.ITEM_FRAME) == 0) return false;
        for (BlockPos pos : BlockPos.betweenClosed(cauldron.get().offset(-RANGE, 1, -RANGE), cauldron.get().offset(RANGE, HEIGHT, RANGE))) {
            if (!level.getBlockState(pos).isAir()) continue;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos wall = pos.relative(side.getOpposite());
                if (!level.getBlockState(wall).isFaceSturdy(level, wall, side)) continue;
                if (!level.getEntitiesOfClass(net.minecraft.world.entity.decoration.HangingEntity.class, new AABB(pos)).isEmpty()) continue;
                if (spot == null || pos.distSqr(cauldron.get()) < spot.distSqr(cauldron.get())) {
                    spot = pos.immutable();
                    facing = side;
                }
            }
        }
        return spot != null;
    }

    static @Nullable Villager owner(ServerLevel level, ItemFrame rack) {
        if (!rack.hasData(RACKED_BY)) return null;
        if (level.getEntity(rack.getData(RACKED_BY)) instanceof Villager villager && villager.isAlive()
                && villager.getVillagerData().profession().is(VillagerProfession.LEATHERWORKER)) {
            return villager;
        }
        rack.removeData(RACKED_BY);
        return null;
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ItemFrame rack) || !(rack.level() instanceof ServerLevel level)) return;
        Villager leatherworker = owner(level, rack);
        if (leatherworker == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.getEntity().sendOverlayMessage(Component.translatableWithFallback("minenow.rack.owned_by", "These are %s's tanning racks", leatherworker.getDisplayName()));
    }

    /** Breaking a rack costs nothing: the hide goes back to its leatherworker, and a frame it hung too. */
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof ItemFrame rack) || !(rack.level() instanceof ServerLevel level)) return;
        Villager leatherworker = owner(level, rack);
        if (leatherworker == null) return;
        event.setCanceled(true);
        if (!rack.getItem().isEmpty()) Economy.give(level, leatherworker, rack.getItem().copy());
        rack.setItem(ItemStack.EMPTY, false);
        if (rack.getData(OWN_FRAME)) {
            Economy.give(level, leatherworker, new ItemStack(Items.ITEM_FRAME));
            rack.discard();
        } else {
            rack.removeData(RACKED_BY);
            rack.spawnAtLocation(level, new ItemStack(Items.ITEM_FRAME));
            rack.discard();
        }
        level.playSound(null, rack.blockPosition(), SoundEvents.ITEM_FRAME_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void walk(Villager body) {
        BlockPos at = frame != null ? frame.blockPosition() : spot;
        if (at == null) return;
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(at, SPEED, 2));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(at));
    }
}

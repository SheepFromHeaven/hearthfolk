package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The villager inspector, left-clicked on a bell, toggles an outline of that bell's village on the client of the player
 * who clicked: the border of every column within {@link Villages#REACH} of one of the village's places
 * ({@link VillageBorder}). The server only sends the places, again every second while the outline is on since the
 * village follows what the player builds, and the client draws them ({@code client/VillageBorderRenderer}).
 */
public final class VillageBorders {
    private static final int RESEND = 20;

    /** The places of a village to outline; {@code on} false clears the outline. */
    public record Payload(List<BlockPos> anchors, List<BlockPos> claimable, boolean on) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "village_borders"));
        public static final StreamCodec<ByteBuf, Payload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), Payload::anchors,
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), Payload::claimable,
                ByteBufCodecs.BOOL, Payload::on,
                Payload::new);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    private record Watch(GlobalPos bell, List<BlockPos> sent, List<BlockPos> sentClaimable) {}

    private static final Map<UUID, Watch> SHOWING = new HashMap<>();

    private VillageBorders() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").playToClient(Payload.TYPE, Payload.CODEC,
                (payload, context) -> com.sheepfromheaven.minenow.client.VillageBorderRenderer.receive(payload)));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, VillageBorders::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(VillageBorders::onServerTick);
        NeoForge.EVENT_BUS.addListener(VillageBorders::onLogout);
    }

    public static boolean isShowing(ServerPlayer player) {
        return SHOWING.containsKey(player.getUUID());
    }

    /** The places of the village the bell belongs to; none for a bell no villager has claimed. */
    public static List<BlockPos> anchorsAround(ServerLevel level, BlockPos bell) {
        return Villages.at(level, bell).map(Villages.Village::anchors).orElse(List.of());
    }

    /** Free beds and job blocks outside the village's border that its villagers could still claim; the border would grow to take them in. */
    public static List<BlockPos> claimableAround(ServerLevel level, BlockPos bell) {
        return Villages.at(level, bell).map(village -> village.claimableOutside(level)).orElse(List.of());
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getItemStack().is(VillagerInspector.ITEM.get()) || !event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL)) return;
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) toggle(player, level, event.getPos());
    }

    static void toggle(ServerPlayer player, ServerLevel level, BlockPos bell) {
        Watch watch = SHOWING.get(player.getUUID());
        if (watch != null && watch.bell().dimension().equals(level.dimension()) && Villages.at(level, watch.bell().pos()).map(village -> village.contains(bell)).orElse(false)) {
            stop(player);
            player.sendOverlayMessage(Component.literal("Village borders off"));
            return;
        }
        List<BlockPos> anchors = anchorsAround(level, bell);
        if (anchors.isEmpty()) {
            player.sendOverlayMessage(Component.literal("This bell belongs to no village: no villager has claimed it"));
            return;
        }
        List<BlockPos> claimable = claimableAround(level, bell);
        SHOWING.put(player.getUUID(), new Watch(GlobalPos.of(level.dimension(), bell), anchors, claimable));
        send(player, new Payload(anchors, claimable, true));
        player.sendOverlayMessage(Component.literal("Village borders on, " + Villages.REACH + " blocks around its places, orange where free beds and job blocks would widen it"));
    }

    private static void stop(ServerPlayer player) {
        SHOWING.remove(player.getUUID());
        send(player, new Payload(List.of(), List.of(), false));
    }

    private static void send(ServerPlayer player, Payload payload) {
        if (player.connection != null && player.connection.hasChannel(Payload.TYPE)) PacketDistributor.sendToPlayer(player, payload);
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (SHOWING.isEmpty() || event.getServer().getTickCount() % RESEND != 0) return;
        for (Map.Entry<UUID, Watch> entry : Map.copyOf(SHOWING).entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                SHOWING.remove(entry.getKey());
                continue;
            }
            Watch watch = entry.getValue();
            if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(watch.bell().dimension())) {
                stop(player);
                continue;
            }
            List<BlockPos> anchors = anchorsAround(level, watch.bell().pos());
            List<BlockPos> claimable = claimableAround(level, watch.bell().pos());
            if (anchors.equals(watch.sent()) && claimable.equals(watch.sentClaimable())) continue;
            SHOWING.put(entry.getKey(), new Watch(watch.bell(), anchors, claimable));
            send(player, new Payload(anchors, claimable, true));
        }
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SHOWING.remove(event.getEntity().getUUID());
    }
}

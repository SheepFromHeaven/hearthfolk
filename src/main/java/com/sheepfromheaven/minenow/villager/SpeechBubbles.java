package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.client.SpeechBubbleRenderer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.function.BiConsumer;

/** A speech bubble with an item icon over a villager's head: the server says who shows what, the client draws it ({@code client/SpeechBubbleRenderer}). */
public final class SpeechBubbles {
    public static final double RANGE = 16;
    private static final double COMMAND_RANGE = 8;
    private static final int DEFAULT_SECONDS = 5;

    public record Payload(int entityId, ItemStack icon, int ticks) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "speech_bubble"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::entityId,
                ItemStack.OPTIONAL_STREAM_CODEC, Payload::icon,
                ByteBufCodecs.VAR_INT, Payload::ticks,
                Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Where payloads go; tests swap it to see who would get one. */
    static BiConsumer<ServerPlayer, Payload> sender = (player, payload) -> {
        if (player.connection.hasChannel(Payload.TYPE)) PacketDistributor.sendToPlayer(player, payload);
    };

    private SpeechBubbles() {}

    /** Shows {@code icon} over the villager for {@code ticks} to the players within {@link #RANGE} blocks of it; an empty icon clears the bubble. */
    public static void show(Villager villager, ItemStack icon, int ticks) {
        Payload payload = new Payload(villager.getId(), icon.copy(), icon.isEmpty() ? 0 : ticks);
        for (ServerPlayer player : recipients(villager)) sender.accept(player, payload);
    }

    static List<ServerPlayer> recipients(Villager villager) {
        if (!(villager.level() instanceof net.minecraft.server.level.ServerLevel level)) return List.of();
        return level.players().stream().filter(player -> player.distanceToSqr(villager) <= RANGE * RANGE).toList();
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Payload.TYPE, Payload.CODEC, SpeechBubbles::handle);
    }

    private static void handle(Payload payload, IPayloadContext context) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) context.enqueueWork(() -> SpeechBubbleRenderer.set(payload.entityId(), payload.icon(), payload.ticks()));
    }

    /** {@code /minenow bubble <item> [seconds]}: shows the bubble on the nearest villager within 8 blocks. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("minenow").then(Commands.literal("bubble")
                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                        .executes(context -> run(context, DEFAULT_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                .executes(context -> run(context, IntegerArgumentType.getInteger(context, "seconds")))))));
    }

    private static int run(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context, int seconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        ItemStack icon = ItemArgument.getItem(context, "item").createItemStack(1);
        Villager nearest = source.getLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(net.minecraft.core.BlockPos.containing(source.getPosition())).inflate(COMMAND_RANGE))
                .stream().filter(villager -> villager.position().distanceToSqr(source.getPosition()) <= COMMAND_RANGE * COMMAND_RANGE)
                .min(java.util.Comparator.comparingDouble(villager -> villager.position().distanceToSqr(source.getPosition()))).orElse(null);
        if (nearest == null) {
            source.sendFailure(Component.literal("No villager within " + (int) COMMAND_RANGE + " blocks"));
            return 0;
        }
        show(nearest, icon, seconds * 20);
        return 1;
    }
}

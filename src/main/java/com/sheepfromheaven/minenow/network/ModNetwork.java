package com.sheepfromheaven.minenow.network;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.Config;
import com.sheepfromheaven.minenow.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.block.TownBannerBlockEntity;
import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.dominion.RoomDetector;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public class ModNetwork {
    private static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "main"))
                .simpleChannel();

        CHANNEL.protocol(NetworkProtocol.PLAY)
                .serverbound(flow -> flow
                        .addMain(RenameDominionPacket.class, RenameDominionPacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            var data = DominionSavedData.get(level);
                            var dominion = data.getDominionAt(msg.bannerPos());
                            if (dominion != null && dominion.getOwnerUuid().equals(player.getUUID())) {
                                String name = msg.newName().trim();
                                if (!name.isEmpty() && name.length() <= 32) {
                                    dominion.setName(name);
                                    data.setDirty();
                                    if (level.getBlockEntity(msg.bannerPos()) instanceof TownBannerBlockEntity be) {
                                        be.setDominionData(name, dominion.getTier());
                                    }
                                }
                            }
                        })
                        .addMain(RenameStructurePacket.class, RenameStructurePacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            var data = DominionSavedData.get(level);
                            var dominion = data.findDominionContaining(msg.markerPos());
                            if (dominion != null && dominion.getOwnerUuid().equals(player.getUUID())) {
                                var structure = dominion.getStructureAt(msg.markerPos());
                                if (structure != null) {
                                    String name = msg.newName().trim();
                                    if (!name.isEmpty() && name.length() <= 32) {
                                        structure.setName(name);
                                        data.setDirty();
                                        if (level.getBlockEntity(msg.markerPos()) instanceof StructureMarkerBlockEntity be) {
                                            be.setStructureData(name, structure.getType(), dominion.getId(), structure.getId());
                                        }
                                    }
                                }
                            }
                        })
                        .addMain(RecalculateStructurePacket.class, RecalculateStructurePacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            var data = DominionSavedData.get(level);
                            var dominion = data.findDominionContaining(msg.markerPos());
                            if (dominion != null && dominion.getOwnerUuid().equals(player.getUUID())) {
                                var structure = dominion.getStructureAt(msg.markerPos());
                                if (structure != null) {
                                    var result = RoomDetector.detect(level, msg.markerPos(), Config.maxRoomSize);
                                    switch (result) {
                                        case RoomDetector.Result.Success(var blocks) -> {
                                            structure.setRoomBlocks(blocks);
                                            data.setDirty();
                                            var statuses = structure.checkRequirements(level);
                                            if (level.getBlockEntity(msg.markerPos()) instanceof StructureMarkerBlockEntity be) {
                                                be.setRequirementStatuses(statuses);
                                            }
                                            player.displayClientMessage(Component.literal(
                                                    "Structure recalculated! " + blocks.size() + " blocks detected."), false);
                                            if (level instanceof ServerLevel sl) {
                                                var particle = new net.minecraft.core.particles.DustParticleOptions(
                                                        (50 << 16) | (255 << 8) | 50, 1.5f);
                                                for (var bp : blocks) {
                                                    sl.sendParticles(particle, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 1, 0, 0, 0, 0);
                                                }
                                            }
                                        }
                                        case RoomDetector.Result.TooLarge ignored ->
                                            player.displayClientMessage(Component.literal("Room too large after rebuild!"), false);
                                        case RoomDetector.Result.NotEnclosed ignored ->
                                            player.displayClientMessage(Component.literal("Room is no longer enclosed! Check for gaps."), false);
                                    }
                                }
                            }
                        })
                        .addMain(SetStructureTypePacket.class, SetStructureTypePacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            var data = DominionSavedData.get(level);
                            var dominion = data.findDominionContaining(msg.markerPos());
                            if (dominion != null && dominion.getOwnerUuid().equals(player.getUUID())) {
                                var structure = dominion.getStructureAt(msg.markerPos());
                                if (structure != null) {
                                    try {
                                        var type = StructureType.valueOf(msg.typeName());
                                        structure.setType(type);
                                        data.setDirty();
                                        var statuses = structure.checkRequirements(level);
                                        if (level.getBlockEntity(msg.markerPos()) instanceof StructureMarkerBlockEntity be) {
                                            be.setStructureData(structure.getName(), type, dominion.getId(), structure.getId());
                                            be.setRequirementStatuses(statuses);
                                        }
                                    } catch (IllegalArgumentException ignored) {}
                                }
                            }
                        })
                        .addMain(GoHomePacket.class, GoHomePacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            if (!(level.getEntity(msg.entityId()) instanceof InhabitantEntity inhabitant)) return;
                            if (inhabitant.getDominionId() == null) return;
                            var data = DominionSavedData.get(level);
                            var dominion = data.getDominionById(inhabitant.getDominionId());
                            if (dominion != null && dominion.getOwnerUuid().equals(player.getUUID())) {
                                inhabitant.goHome();
                            }
                        })
                )
                .clientbound(flow -> flow
                        .addMain(OpenDominionScreenPacket.class, OpenDominionScreenPacket.CODEC, (msg, ctx) -> {
                            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                                var inhabitantNames = msg.inhabitantNames().isEmpty()
                                        ? List.<String>of()
                                        : List.of(msg.inhabitantNames().split(","));
                                net.minecraft.client.Minecraft.getInstance().setScreen(
                                        new com.sheepfromheaven.minenow.client.DominionScreen(
                                                msg.dominionName(), DominionTier.valueOf(msg.tier()),
                                                msg.bannerPos(), inhabitantNames));
                            });
                        })
                        .addMain(OpenInhabitantScreenPacket.class, OpenInhabitantScreenPacket.CODEC, (msg, ctx) -> {
                            net.minecraft.client.Minecraft.getInstance().execute(() ->
                                    net.minecraft.client.Minecraft.getInstance().setScreen(
                                            new com.sheepfromheaven.minenow.client.InhabitantScreen(
                                                    msg.entityId(), msg.name(), msg.dominionName(), msg.shelter(), msg.food(), msg.rest())));
                        })
                );
    }

    public static void sendRenameDominion(BlockPos bannerPos, String newName) {
        CHANNEL.send(new RenameDominionPacket(bannerPos, newName), PacketDistributor.SERVER.noArg());
    }

    public static void sendRenameStructure(BlockPos markerPos, String newName) {
        CHANNEL.send(new RenameStructurePacket(markerPos, newName), PacketDistributor.SERVER.noArg());
    }

    public static void sendSetStructureType(BlockPos markerPos, String typeName) {
        CHANNEL.send(new SetStructureTypePacket(markerPos, typeName), PacketDistributor.SERVER.noArg());
    }

    public static void sendRecalculateStructure(BlockPos markerPos) {
        CHANNEL.send(new RecalculateStructurePacket(markerPos), PacketDistributor.SERVER.noArg());
    }

    public static void sendOpenDominionScreen(ServerPlayer player, Dominion dominion, BlockPos bannerPos) {
        var names = dominion.getInhabitants().stream()
                .map(DominionInhabitant::getName)
                .collect(Collectors.joining(","));
        CHANNEL.send(new OpenDominionScreenPacket(
                dominion.getName(), dominion.getTier().name(), bannerPos, names
        ), PacketDistributor.PLAYER.with(player));
    }

    public static void sendOpenInhabitantScreen(ServerPlayer player, InhabitantEntity entity, Dominion dominion) {
        CHANNEL.send(new OpenInhabitantScreenPacket(
                entity.getId(), entity.getName().getString(), dominion.getName(),
                entity.getNeed(NeedType.SHELTER), entity.getNeed(NeedType.FOOD), entity.getNeed(NeedType.REST)
        ), PacketDistributor.PLAYER.with(player));
    }

    public static void sendGoHome(int entityId) {
        CHANNEL.send(new GoHomePacket(entityId), PacketDistributor.SERVER.noArg());
    }

    public record RenameDominionPacket(BlockPos bannerPos, String newName) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameDominionPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RenameDominionPacket::bannerPos,
                ByteBufCodecs.STRING_UTF8, RenameDominionPacket::newName,
                RenameDominionPacket::new
        );
    }

    public record RenameStructurePacket(BlockPos markerPos, String newName) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameStructurePacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RenameStructurePacket::markerPos,
                ByteBufCodecs.STRING_UTF8, RenameStructurePacket::newName,
                RenameStructurePacket::new
        );
    }

    public record RecalculateStructurePacket(BlockPos markerPos) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RecalculateStructurePacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RecalculateStructurePacket::markerPos,
                RecalculateStructurePacket::new
        );
    }

    public record SetStructureTypePacket(BlockPos markerPos, String typeName) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SetStructureTypePacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetStructureTypePacket::markerPos,
                ByteBufCodecs.STRING_UTF8, SetStructureTypePacket::typeName,
                SetStructureTypePacket::new
        );
    }

    public record OpenDominionScreenPacket(String dominionName, String tier, BlockPos bannerPos, String inhabitantNames) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDominionScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenDominionScreenPacket::dominionName,
                ByteBufCodecs.STRING_UTF8, OpenDominionScreenPacket::tier,
                BlockPos.STREAM_CODEC, OpenDominionScreenPacket::bannerPos,
                ByteBufCodecs.STRING_UTF8, OpenDominionScreenPacket::inhabitantNames,
                OpenDominionScreenPacket::new
        );
    }

    public record OpenInhabitantScreenPacket(int entityId, String name, String dominionName, float shelter, float food, float rest) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenInhabitantScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, OpenInhabitantScreenPacket::entityId,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::name,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::dominionName,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::shelter,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::food,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::rest,
                OpenInhabitantScreenPacket::new
        );
    }

    public record GoHomePacket(int entityId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GoHomePacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, GoHomePacket::entityId,
                GoHomePacket::new
        );
    }
}

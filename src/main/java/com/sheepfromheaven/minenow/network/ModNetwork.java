package com.sheepfromheaven.minenow.network;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.Config;
import com.sheepfromheaven.minenow.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.block.TownBannerBlockEntity;
import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.dominion.JobType;
import com.sheepfromheaven.minenow.dominion.RoomDetector;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import com.sheepfromheaven.minenow.inventory.EquipmentSlotContainer;
import com.sheepfromheaven.minenow.inventory.ModMenus;
import java.util.List;
import java.util.UUID;
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
                                            if (level.getBlockEntity(msg.markerPos()) instanceof StructureMarkerBlockEntity be) {
                                                be.refreshStatuses(structure, level);
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
                                        if (level.getBlockEntity(msg.markerPos()) instanceof StructureMarkerBlockEntity be) {
                                            be.setStructureData(structure.getName(), type, dominion.getId(), structure.getId());
                                            be.refreshStatuses(structure, level);
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
                        .addMain(ViewInventoryPacket.class, ViewInventoryPacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            if (!(level.getEntity(msg.entityId()) instanceof InhabitantEntity inhabitant)) return;
                            if (inhabitant.getDominionId() == null) return;
                            var data = DominionSavedData.get(level);
                            var dominion = data.getDominionById(inhabitant.getDominionId());
                            if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
                            player.openMenu(ModMenus.INHABITANT_INVENTORY.createView(
                                    inhabitant.getName(),
                                    new Container[] {new EquipmentSlotContainer(inhabitant, EquipmentSlot.MAINHAND)},
                                    inhabitant.getInventory()));
                        })
                        .addMain(AssignHousePacket.class, AssignHousePacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            var data = DominionSavedData.get(level);
                            var dominion = data.findDominionContaining(msg.markerPos());
                            if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
                            var structure = dominion.getStructureAt(msg.markerPos());
                            if (structure == null || structure.getType() != StructureType.HOUSE) return;

                            var inhabitantId = msg.inhabitantId().isEmpty() ? null : UUID.fromString(msg.inhabitantId());
                            dominion.assignHouse(structure, inhabitantId, level);
                            data.setDirty();
                        })
                        .addMain(AssignWorkerPacket.class, AssignWorkerPacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            var data = DominionSavedData.get(level);
                            var dominion = data.findDominionContaining(msg.markerPos());
                            if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
                            var structure = dominion.getStructureAt(msg.markerPos());
                            if (structure == null || structure.getType().getJobType().isEmpty()) return;

                            var inhabitantId = msg.inhabitantId().isEmpty() ? null : UUID.fromString(msg.inhabitantId());
                            dominion.assignWorker(structure, inhabitantId, level);
                            data.setDirty();
                        })
                        .addMain(SetWorkAreaCenterPacket.class, SetWorkAreaCenterPacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            var data = DominionSavedData.get(level);
                            var dominion = data.findDominionContaining(msg.markerPos());
                            if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
                            var structure = dominion.getStructureAt(msg.markerPos());
                            if (structure == null || !structure.getType().getJobType().map(t -> t.hasWorkArea()).orElse(false)) return;
                            if (!isWithinStructureRange(structure, msg.markerPos(), msg.center())) return;

                            structure.setWorkAreaCenter(msg.center());
                            data.setDirty();
                            if (level.getBlockEntity(msg.markerPos()) instanceof StructureMarkerBlockEntity be) {
                                be.setWorkArea(structure.getWorkAreaCenter(), structure.getWorkAreaRadius());
                            }
                            var center = structure.getWorkAreaCenter();
                            player.displayClientMessage(Component.literal(structure.getName() + "'s work area centered on "
                                    + center.getX() + ", " + center.getY() + ", " + center.getZ()), false);
                        })
                        .addMain(SetWorkAreaRadiusPacket.class, SetWorkAreaRadiusPacket.CODEC, (msg, ctx) -> {
                            var player = ctx.getSender();
                            if (player == null) return;
                            var level = player.level();
                            var data = DominionSavedData.get(level);
                            var dominion = data.findDominionContaining(msg.markerPos());
                            if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
                            var structure = dominion.getStructureAt(msg.markerPos());
                            if (structure == null || !structure.getType().getJobType().map(t -> t.hasWorkArea()).orElse(false)) return;

                            structure.setWorkAreaRadius(msg.radius());
                            data.setDirty();
                            if (level.getBlockEntity(msg.markerPos()) instanceof StructureMarkerBlockEntity be) {
                                be.setWorkArea(structure.getWorkAreaCenter(), structure.getWorkAreaRadius());
                            }
                        })
                )
                .clientbound(flow -> flow
                        .addMain(OpenDominionScreenPacket.class, OpenDominionScreenPacket.CODEC,
                                (msg, ctx) -> com.sheepfromheaven.minenow.client.ClientNetworkHandlers.openDominionScreen(msg))
                        .addMain(OpenInhabitantScreenPacket.class, OpenInhabitantScreenPacket.CODEC,
                                (msg, ctx) -> com.sheepfromheaven.minenow.client.ClientNetworkHandlers.openInhabitantScreen(msg))
                        .addMain(OpenStructureScreenPacket.class, OpenStructureScreenPacket.CODEC,
                                (msg, ctx) -> com.sheepfromheaven.minenow.client.ClientNetworkHandlers.openStructureScreen(msg))
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
        var inhabitant = dominion.getInhabitantByEntityUuid(entity.getUUID());
        var name = inhabitant != null ? inhabitant.getName() : entity.getName().getString();
        CHANNEL.send(new OpenInhabitantScreenPacket(
                entity.getId(), name, dominion.getName(),
                entity.getNeed(NeedType.SHELTER), entity.getNeed(NeedType.FOOD), entity.getNeed(NeedType.REST),
                entity.getJobStatus()
        ), PacketDistributor.PLAYER.with(player));
    }

    public static void sendGoHome(int entityId) {
        CHANNEL.send(new GoHomePacket(entityId), PacketDistributor.SERVER.noArg());
    }

    public static void sendViewInventory(int entityId) {
        CHANNEL.send(new ViewInventoryPacket(entityId), PacketDistributor.SERVER.noArg());
    }

    public static final String INHABITANT_FIELD_SEP = "\u001F";

    public static void sendOpenStructureScreen(ServerPlayer player, Dominion dominion,
                                                com.sheepfromheaven.minenow.dominion.DominionStructure structure) {
        var inhabitants = dominion.getInhabitants().stream()
                .map(i -> {
                    String jobLabel = "";
                    if (i.getWorkStructureId() != null) {
                        var workStructure = dominion.getStructureById(i.getWorkStructureId());
                        if (workStructure != null) {
                            jobLabel = workStructure.getType().getJobType().map(JobType::getDisplayName).orElse("");
                        }
                    }
                    boolean homed = i.getHomeStructureId() != null;
                    return String.join(INHABITANT_FIELD_SEP, i.getId().toString(), i.getEntityUuid().toString(),
                            i.getName(), jobLabel, homed ? "1" : "0");
                })
                .collect(Collectors.joining(","));
        var assignedId = structure.getAssignedInhabitantId();
        var assignedWorkerId = structure.getAssignedWorkerId();
        CHANNEL.send(new OpenStructureScreenPacket(
                structure.getName(), structure.getType().name(), structure.getMarkerPos(),
                inhabitants, assignedId != null ? assignedId.toString() : "",
                assignedWorkerId != null ? assignedWorkerId.toString() : "",
                structure.getWorkAreaCenter(), structure.getWorkAreaRadius()
        ), PacketDistributor.PLAYER.with(player));
    }

    public static void sendAssignHouse(BlockPos markerPos, String inhabitantId) {
        CHANNEL.send(new AssignHousePacket(markerPos, inhabitantId), PacketDistributor.SERVER.noArg());
    }

    public static void sendAssignWorker(BlockPos markerPos, String inhabitantId) {
        CHANNEL.send(new AssignWorkerPacket(markerPos, inhabitantId), PacketDistributor.SERVER.noArg());
    }

    public static void sendSetWorkAreaCenter(BlockPos markerPos, BlockPos center) {
        CHANNEL.send(new SetWorkAreaCenterPacket(markerPos, center), PacketDistributor.SERVER.noArg());
    }

    public static void sendSetWorkAreaRadius(BlockPos markerPos, int radius) {
        CHANNEL.send(new SetWorkAreaRadiusPacket(markerPos, radius), PacketDistributor.SERVER.noArg());
    }

    /**
     * The work area center must stay within the structure's fixed horizontal range around its marker
     * (not the work area itself). Height is intentionally unbounded - a marker placed high up (e.g. in a
     * building's roof) should still be able to center on ground level far below, or vice versa.
     */
    private static boolean isWithinStructureRange(com.sheepfromheaven.minenow.dominion.DominionStructure structure, BlockPos markerPos, BlockPos candidate) {
        int radius = structure.getType().getRangeRadius();
        int dx = candidate.getX() - markerPos.getX();
        int dz = candidate.getZ() - markerPos.getZ();
        return dx * dx + dz * dz <= radius * radius;
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

    public record OpenInhabitantScreenPacket(int entityId, String name, String dominionName, float shelter, float food, float rest,
                                              String jobStatus) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenInhabitantScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, OpenInhabitantScreenPacket::entityId,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::name,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::dominionName,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::shelter,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::food,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::rest,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::jobStatus,
                OpenInhabitantScreenPacket::new
        );
    }

    public record GoHomePacket(int entityId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GoHomePacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, GoHomePacket::entityId,
                GoHomePacket::new
        );
    }

    public record ViewInventoryPacket(int entityId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ViewInventoryPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, ViewInventoryPacket::entityId,
                ViewInventoryPacket::new
        );
    }

    public record AssignHousePacket(BlockPos markerPos, String inhabitantId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AssignHousePacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, AssignHousePacket::markerPos,
                ByteBufCodecs.STRING_UTF8, AssignHousePacket::inhabitantId,
                AssignHousePacket::new
        );
    }

    public record AssignWorkerPacket(BlockPos markerPos, String inhabitantId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AssignWorkerPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, AssignWorkerPacket::markerPos,
                ByteBufCodecs.STRING_UTF8, AssignWorkerPacket::inhabitantId,
                AssignWorkerPacket::new
        );
    }

    public record SetWorkAreaCenterPacket(BlockPos markerPos, BlockPos center) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SetWorkAreaCenterPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetWorkAreaCenterPacket::markerPos,
                BlockPos.STREAM_CODEC, SetWorkAreaCenterPacket::center,
                SetWorkAreaCenterPacket::new
        );
    }

    public record SetWorkAreaRadiusPacket(BlockPos markerPos, int radius) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SetWorkAreaRadiusPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetWorkAreaRadiusPacket::markerPos,
                ByteBufCodecs.VAR_INT, SetWorkAreaRadiusPacket::radius,
                SetWorkAreaRadiusPacket::new
        );
    }

    public record OpenStructureScreenPacket(String structureName, String structureType, BlockPos markerPos,
                                            String inhabitantData, String assignedInhabitantId, String assignedWorkerId,
                                            BlockPos workAreaCenter, int workAreaRadius) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenStructureScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::structureName,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::structureType,
                BlockPos.STREAM_CODEC, OpenStructureScreenPacket::markerPos,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::inhabitantData,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::assignedInhabitantId,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::assignedWorkerId,
                BlockPos.STREAM_CODEC, OpenStructureScreenPacket::workAreaCenter,
                ByteBufCodecs.VAR_INT, OpenStructureScreenPacket::workAreaRadius,
                OpenStructureScreenPacket::new
        );
    }
}

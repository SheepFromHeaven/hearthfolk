package com.sheepfromheaven.minenow.network;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.Config;
import com.sheepfromheaven.minenow.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.block.TownBannerBlockEntity;
import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.dominion.JobType;
import com.sheepfromheaven.minenow.dominion.RoomDetector;
import com.sheepfromheaven.minenow.dominion.StructureRequest;
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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MineNow.MOD_ID)
public class ModNetwork {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MineNow.MOD_ID);

        // serverbound
        registrar.playToServer(RenameDominionPacket.TYPE, RenameDominionPacket.CODEC, ModNetwork::handleRenameDominion);
        registrar.playToServer(RenameStructurePacket.TYPE, RenameStructurePacket.CODEC, ModNetwork::handleRenameStructure);
        registrar.playToServer(RecalculateStructurePacket.TYPE, RecalculateStructurePacket.CODEC, ModNetwork::handleRecalculateStructure);
        registrar.playToServer(SetStructureTypePacket.TYPE, SetStructureTypePacket.CODEC, ModNetwork::handleSetStructureType);
        registrar.playToServer(SetItemFilterPacket.TYPE, SetItemFilterPacket.CODEC, ModNetwork::handleSetItemFilter);
        registrar.playToServer(GoHomePacket.TYPE, GoHomePacket.CODEC, ModNetwork::handleGoHome);
        registrar.playToServer(ViewInventoryPacket.TYPE, ViewInventoryPacket.CODEC, ModNetwork::handleViewInventory);
        registrar.playToServer(RequestInhabitantDebugPacket.TYPE, RequestInhabitantDebugPacket.CODEC, ModNetwork::handleRequestInhabitantDebug);
        registrar.playToServer(AssignHousePacket.TYPE, AssignHousePacket.CODEC, ModNetwork::handleAssignHouse);
        registrar.playToServer(AssignWorkerPacket.TYPE, AssignWorkerPacket.CODEC, ModNetwork::handleAssignWorker);
        registrar.playToServer(SetWorkAreaCenterPacket.TYPE, SetWorkAreaCenterPacket.CODEC, ModNetwork::handleSetWorkAreaCenter);
        registrar.playToServer(SetWorkAreaRadiusPacket.TYPE, SetWorkAreaRadiusPacket.CODEC, ModNetwork::handleSetWorkAreaRadius);
        registrar.playToServer(RefreshInhabitantScreenPacket.TYPE, RefreshInhabitantScreenPacket.CODEC, ModNetwork::handleRefreshInhabitantScreen);
        registrar.playToServer(RefreshStructureScreenPacket.TYPE, RefreshStructureScreenPacket.CODEC, ModNetwork::handleRefreshStructureScreen);
        registrar.playToServer(RequestRequestsScreenPacket.TYPE, RequestRequestsScreenPacket.CODEC, ModNetwork::handleRequestRequestsScreen);

        // clientbound
        registrar.playToClient(OpenDominionScreenPacket.TYPE, OpenDominionScreenPacket.CODEC, ModNetwork::handleOpenDominionScreen);
        registrar.playToClient(OpenInhabitantScreenPacket.TYPE, OpenInhabitantScreenPacket.CODEC, ModNetwork::handleOpenInhabitantScreen);
        registrar.playToClient(OpenStructureScreenPacket.TYPE, OpenStructureScreenPacket.CODEC, ModNetwork::handleOpenStructureScreen);
        registrar.playToClient(OpenRequestsScreenPacket.TYPE, OpenRequestsScreenPacket.CODEC, ModNetwork::handleOpenRequestsScreen);
        registrar.playToClient(InhabitantDebugPacket.TYPE, InhabitantDebugPacket.CODEC, ModNetwork::handleInhabitantDebug);
        registrar.playToClient(TreeScanClearPacket.TYPE, TreeScanClearPacket.CODEC, ModNetwork::handleTreeScanClear);
        registrar.playToClient(TreeScanHighlightPacket.TYPE, TreeScanHighlightPacket.CODEC, ModNetwork::handleTreeScanHighlight);
    }

    // -------------------------------------------------------------------------
    // Serverbound handlers
    // -------------------------------------------------------------------------

    private static void handleRenameDominion(RenameDominionPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
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
    }

    private static void handleRenameStructure(RenameStructurePacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
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
    }

    private static void handleRecalculateStructure(RecalculateStructurePacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
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
    }

    private static void handleSetStructureType(SetStructureTypePacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
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
    }

    private static void handleSetItemFilter(SetItemFilterPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
        var level = player.level();
        var data = DominionSavedData.get(level);
        var dominion = data.findDominionContaining(msg.markerPos());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
        var structure = dominion.getStructureAt(msg.markerPos());
        if (structure == null) return;

        structure.setItemFilter(decodeItemFilter(msg.itemFilterData()));
        data.setDirty();
    }

    private static void handleGoHome(GoHomePacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
        var level = player.level();
        if (!(level.getEntity(msg.entityId()) instanceof InhabitantEntity inhabitant)) return;
        if (inhabitant.getDominionId() == null) return;
        var data = DominionSavedData.get(level);
        var dominion = data.getDominionById(inhabitant.getDominionId());
        if (dominion != null && dominion.getOwnerUuid().equals(player.getUUID())) {
            inhabitant.goHome();
        }
    }

    private static void handleViewInventory(ViewInventoryPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
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
    }

    private static void handleRequestInhabitantDebug(RequestInhabitantDebugPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
        if (!Config.debugInhabitantView) return;
        var level = player.level();
        if (!(level.getEntity(msg.entityId()) instanceof InhabitantEntity inhabitant)) return;

        var lines = inhabitant.getDebugLines();
        PacketDistributor.sendToPlayer(player, new InhabitantDebugPacket(msg.entityId(), String.join(INHABITANT_FIELD_SEP, lines)));

        if (level instanceof ServerLevel serverLevel) {
            var path = inhabitant.getNavigation().getPath();
            if (path != null) {
                var particle = new net.minecraft.core.particles.DustParticleOptions(
                        (255 << 16) | (220 << 8) | 0, 1.0f);
                for (int i = 0; i < path.getNodeCount(); i++) {
                    var pos = path.getNodePos(i);
                    serverLevel.sendParticles(player, particle, true, true,
                            pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static void handleAssignHouse(AssignHousePacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
        var level = player.level();
        var data = DominionSavedData.get(level);
        var dominion = data.findDominionContaining(msg.markerPos());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
        var structure = dominion.getStructureAt(msg.markerPos());
        if (structure == null || structure.getType() != StructureType.HOUSE) return;

        var inhabitantId = msg.inhabitantId().isEmpty() ? null : UUID.fromString(msg.inhabitantId());
        if (!dominion.assignHouse(structure, inhabitantId, level)) {
            player.displayClientMessage(Component.literal(
                    structure.getName() + " isn't ready to be lived in yet."), false);
            return;
        }
        data.setDirty();
    }

    private static void handleAssignWorker(AssignWorkerPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
        var level = player.level();
        var data = DominionSavedData.get(level);
        var dominion = data.findDominionContaining(msg.markerPos());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
        var structure = dominion.getStructureAt(msg.markerPos());
        if (structure == null || structure.getType().getJobType().isEmpty()) return;

        var inhabitantId = msg.inhabitantId().isEmpty() ? null : UUID.fromString(msg.inhabitantId());
        dominion.assignWorker(structure, inhabitantId, level);
        data.setDirty();
    }

    private static void handleSetWorkAreaCenter(SetWorkAreaCenterPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
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
    }

    private static void handleSetWorkAreaRadius(SetWorkAreaRadiusPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
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
    }

    private static void handleRefreshInhabitantScreen(RefreshInhabitantScreenPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
        var level = player.level();
        if (!(level.getEntity(msg.entityId()) instanceof InhabitantEntity inhabitant)) return;
        if (inhabitant.getDominionId() == null) return;
        var dominion = DominionSavedData.get(level).getDominionById(inhabitant.getDominionId());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
        sendOpenInhabitantScreen(player, inhabitant, dominion);
    }

    private static void handleRefreshStructureScreen(RefreshStructureScreenPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
        var level = player.level();
        var data = DominionSavedData.get(level);
        var dominion = data.findDominionContaining(msg.markerPos());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
        var structure = dominion.getStructureAt(msg.markerPos());
        if (structure == null) return;
        sendOpenStructureScreen(player, dominion, structure);
    }

    private static void handleRequestRequestsScreen(RequestRequestsScreenPacket msg, IPayloadContext ctx) {
        var player = (ServerPlayer) ctx.player();
        var level = player.level();
        var data = DominionSavedData.get(level);
        var dominion = data.getDominionAt(msg.bannerPos());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) return;
        sendOpenRequestsScreen(player, dominion, msg.bannerPos());
    }

    // -------------------------------------------------------------------------
    // Clientbound handlers
    // -------------------------------------------------------------------------

    private static void handleOpenDominionScreen(OpenDominionScreenPacket msg, IPayloadContext ctx) {
        com.sheepfromheaven.minenow.client.ClientNetworkHandlers.openDominionScreen(msg);
    }

    private static void handleOpenInhabitantScreen(OpenInhabitantScreenPacket msg, IPayloadContext ctx) {
        com.sheepfromheaven.minenow.client.ClientNetworkHandlers.openInhabitantScreen(msg);
    }

    private static void handleOpenStructureScreen(OpenStructureScreenPacket msg, IPayloadContext ctx) {
        com.sheepfromheaven.minenow.client.ClientNetworkHandlers.openStructureScreen(msg);
    }

    private static void handleOpenRequestsScreen(OpenRequestsScreenPacket msg, IPayloadContext ctx) {
        com.sheepfromheaven.minenow.client.ClientNetworkHandlers.openRequestsScreen(msg);
    }

    private static void handleInhabitantDebug(InhabitantDebugPacket msg, IPayloadContext ctx) {
        com.sheepfromheaven.minenow.client.ClientNetworkHandlers.updateInhabitantDebug(msg);
    }

    private static void handleTreeScanClear(TreeScanClearPacket msg, IPayloadContext ctx) {
        com.sheepfromheaven.minenow.client.ClientNetworkHandlers.clearTreeScanOutline();
    }

    private static void handleTreeScanHighlight(TreeScanHighlightPacket msg, IPayloadContext ctx) {
        com.sheepfromheaven.minenow.client.ClientNetworkHandlers.addTreeScanHighlight(msg);
    }

    // -------------------------------------------------------------------------
    // Public send helpers
    // -------------------------------------------------------------------------

    public static void sendRenameDominion(BlockPos bannerPos, String newName) {
        ClientPacketDistributor.sendToServer(new RenameDominionPacket(bannerPos, newName));
    }

    public static void sendRenameStructure(BlockPos markerPos, String newName) {
        ClientPacketDistributor.sendToServer(new RenameStructurePacket(markerPos, newName));
    }

    public static void sendSetStructureType(BlockPos markerPos, String typeName) {
        ClientPacketDistributor.sendToServer(new SetStructureTypePacket(markerPos, typeName));
    }

    public static void sendSetItemFilter(BlockPos markerPos, String itemFilterData) {
        ClientPacketDistributor.sendToServer(new SetItemFilterPacket(markerPos, itemFilterData));
    }

    /** Parses the comma-separated wire encoding of item ids produced by {@link #encodeItemFilter}, skipping unknown ids. */
    private static java.util.Set<net.minecraft.world.item.Item> decodeItemFilter(String data) {
        var items = new java.util.HashSet<net.minecraft.world.item.Item>();
        if (data == null || data.isEmpty()) return items;
        for (var key : data.split(",")) {
            var id = net.minecraft.resources.Identifier.tryParse(key.trim());
            if (id == null) continue;
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null) items.add(item);
        }
        return items;
    }

    /** Wire encoding for a structure's item filter: comma-separated item ids. */
    private static String encodeItemFilter(DominionStructure structure) {
        return structure.getItemFilter().stream()
                .map(item -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString())
                .collect(Collectors.joining(","));
    }

    public static void sendRecalculateStructure(BlockPos markerPos) {
        ClientPacketDistributor.sendToServer(new RecalculateStructurePacket(markerPos));
    }

    public static void sendOpenDominionScreen(ServerPlayer player, Dominion dominion, BlockPos bannerPos) {
        var names = dominion.getInhabitants().stream()
                .map(DominionInhabitant::getName)
                .collect(Collectors.joining(","));
        PacketDistributor.sendToPlayer(player, new OpenDominionScreenPacket(
                dominion.getName(), dominion.getTier().name(), bannerPos, names
        ));
    }

    public static void sendOpenInhabitantScreen(ServerPlayer player, InhabitantEntity entity, Dominion dominion) {
        var inhabitant = dominion.getInhabitantByEntityUuid(entity.getUUID());
        var name = inhabitant != null ? inhabitant.getName() : entity.getName().getString();
        PacketDistributor.sendToPlayer(player, new OpenInhabitantScreenPacket(
                entity.getId(), name, dominion.getName(),
                entity.getNeed(NeedType.SHELTER), entity.getNeed(NeedType.FOOD), entity.getNeed(NeedType.REST),
                entity.getJobStatus(), Config.debugInhabitantView, String.join(INHABITANT_FIELD_SEP, entity.getIssues())
        ));
    }

    public static void sendGoHome(int entityId) {
        ClientPacketDistributor.sendToServer(new GoHomePacket(entityId));
    }

    public static void sendViewInventory(int entityId) {
        ClientPacketDistributor.sendToServer(new ViewInventoryPacket(entityId));
    }

    public static void sendRequestInhabitantDebug(int entityId) {
        ClientPacketDistributor.sendToServer(new RequestInhabitantDebugPacket(entityId));
    }

    public static void sendTreeScanClear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new TreeScanClearPacket());
    }

    public static void sendTreeScanHighlight(ServerPlayer player, int category, List<BlockPos> positions) {
        PacketDistributor.sendToPlayer(player, new TreeScanHighlightPacket(category, positions));
    }

    public static final String INHABITANT_FIELD_SEP = "";

    public static void sendOpenStructureScreen(ServerPlayer player, Dominion dominion,
                                                DominionStructure structure) {
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
        PacketDistributor.sendToPlayer(player, new OpenStructureScreenPacket(
                structure.getName(), structure.getType().name(), structure.getMarkerPos(),
                inhabitants, assignedId != null ? assignedId.toString() : "",
                assignedWorkerId != null ? assignedWorkerId.toString() : "",
                structure.getWorkAreaCenter(), structure.getWorkAreaRadius(),
                encodeItemFilter(structure)
        ));
    }

    public static void sendAssignHouse(BlockPos markerPos, String inhabitantId) {
        ClientPacketDistributor.sendToServer(new AssignHousePacket(markerPos, inhabitantId));
    }

    public static void sendAssignWorker(BlockPos markerPos, String inhabitantId) {
        ClientPacketDistributor.sendToServer(new AssignWorkerPacket(markerPos, inhabitantId));
    }

    public static void sendSetWorkAreaCenter(BlockPos markerPos, BlockPos center) {
        ClientPacketDistributor.sendToServer(new SetWorkAreaCenterPacket(markerPos, center));
    }

    public static void sendSetWorkAreaRadius(BlockPos markerPos, int radius) {
        ClientPacketDistributor.sendToServer(new SetWorkAreaRadiusPacket(markerPos, radius));
    }

    /**
     * The work area center must stay within the structure's fixed horizontal range around its marker
     * (not the work area itself). Height is intentionally unbounded - a marker placed high up (e.g. in a
     * building's roof) should still be able to center on ground level far below, or vice versa.
     */
    private static boolean isWithinStructureRange(DominionStructure structure, BlockPos markerPos, BlockPos candidate) {
        int radius = structure.getType().getRangeRadius();
        int dx = candidate.getX() - markerPos.getX();
        int dz = candidate.getZ() - markerPos.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    public static void sendRefreshInhabitantScreen(int entityId) {
        ClientPacketDistributor.sendToServer(new RefreshInhabitantScreenPacket(entityId));
    }

    public static void sendRefreshStructureScreen(BlockPos markerPos) {
        ClientPacketDistributor.sendToServer(new RefreshStructureScreenPacket(markerPos));
    }

    public static void sendRequestRequestsScreen(BlockPos bannerPos) {
        ClientPacketDistributor.sendToServer(new RequestRequestsScreenPacket(bannerPos));
    }

    public static void sendOpenRequestsScreen(ServerPlayer player, Dominion dominion, BlockPos bannerPos) {
        PacketDistributor.sendToPlayer(player, new OpenRequestsScreenPacket(dominion.getName(), encodeRequests(dominion), bannerPos));
    }

    /**
     * Encodes all pending requests across a dominion as a flat string.
     * Format: rows separated by {@code ,}, fields separated by {@link #INHABITANT_FIELD_SEP}.
     * Fields per row: structureName, label, amount, urgent (1/0).
     */
    static String encodeRequests(Dominion dominion) {
        var sb = new StringBuilder();
        for (var structure : dominion.getStructures()) {
            var reqs = structure.getRequests();
            if (reqs.isEmpty()) continue;
            // Sort: urgent first, then by label
            var sorted = reqs.values().stream()
                    .sorted(java.util.Comparator
                            .comparing(StructureRequest::priority).reversed()
                            .thenComparing(StructureRequest::label))
                    .toList();
            for (var req : sorted) {
                if (sb.length() > 0) sb.append(',');
                sb.append(structure.getName()).append(INHABITANT_FIELD_SEP)
                  .append(req.label()).append(INHABITANT_FIELD_SEP)
                  .append(req.amount()).append(INHABITANT_FIELD_SEP)
                  .append(req.priority() == StructureRequest.Priority.URGENT ? "1" : "0");
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Packet records
    // -------------------------------------------------------------------------

    public record RenameDominionPacket(BlockPos bannerPos, String newName) implements CustomPacketPayload {
        public static final Type<RenameDominionPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "rename_dominion"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameDominionPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RenameDominionPacket::bannerPos,
                ByteBufCodecs.STRING_UTF8, RenameDominionPacket::newName,
                RenameDominionPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RenameStructurePacket(BlockPos markerPos, String newName) implements CustomPacketPayload {
        public static final Type<RenameStructurePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "rename_structure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameStructurePacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RenameStructurePacket::markerPos,
                ByteBufCodecs.STRING_UTF8, RenameStructurePacket::newName,
                RenameStructurePacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RecalculateStructurePacket(BlockPos markerPos) implements CustomPacketPayload {
        public static final Type<RecalculateStructurePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "recalculate_structure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RecalculateStructurePacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RecalculateStructurePacket::markerPos,
                RecalculateStructurePacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetStructureTypePacket(BlockPos markerPos, String typeName) implements CustomPacketPayload {
        public static final Type<SetStructureTypePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "set_structure_type"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetStructureTypePacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetStructureTypePacket::markerPos,
                ByteBufCodecs.STRING_UTF8, SetStructureTypePacket::typeName,
                SetStructureTypePacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** {@code itemFilterData} is a comma-separated list of item ids (e.g. "minecraft:coal,minecraft:iron_ingot"); empty means no filter. */
    public record SetItemFilterPacket(BlockPos markerPos, String itemFilterData) implements CustomPacketPayload {
        public static final Type<SetItemFilterPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "set_item_filter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetItemFilterPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetItemFilterPacket::markerPos,
                ByteBufCodecs.STRING_UTF8, SetItemFilterPacket::itemFilterData,
                SetItemFilterPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenDominionScreenPacket(String dominionName, String tier, BlockPos bannerPos, String inhabitantNames) implements CustomPacketPayload {
        public static final Type<OpenDominionScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "open_dominion_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDominionScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenDominionScreenPacket::dominionName,
                ByteBufCodecs.STRING_UTF8, OpenDominionScreenPacket::tier,
                BlockPos.STREAM_CODEC, OpenDominionScreenPacket::bannerPos,
                ByteBufCodecs.STRING_UTF8, OpenDominionScreenPacket::inhabitantNames,
                OpenDominionScreenPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenInhabitantScreenPacket(int entityId, String name, String dominionName, float shelter, float food, float rest,
                                              String jobStatus, boolean debugEnabled, String issuesData) implements CustomPacketPayload {
        public static final Type<OpenInhabitantScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "open_inhabitant_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenInhabitantScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, OpenInhabitantScreenPacket::entityId,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::name,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::dominionName,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::shelter,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::food,
                ByteBufCodecs.FLOAT, OpenInhabitantScreenPacket::rest,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::jobStatus,
                ByteBufCodecs.BOOL, OpenInhabitantScreenPacket::debugEnabled,
                ByteBufCodecs.STRING_UTF8, OpenInhabitantScreenPacket::issuesData,
                OpenInhabitantScreenPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record GoHomePacket(int entityId) implements CustomPacketPayload {
        public static final Type<GoHomePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "go_home"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GoHomePacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, GoHomePacket::entityId,
                GoHomePacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ViewInventoryPacket(int entityId) implements CustomPacketPayload {
        public static final Type<ViewInventoryPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "view_inventory"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ViewInventoryPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, ViewInventoryPacket::entityId,
                ViewInventoryPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RequestInhabitantDebugPacket(int entityId) implements CustomPacketPayload {
        public static final Type<RequestInhabitantDebugPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "request_inhabitant_debug"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestInhabitantDebugPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, RequestInhabitantDebugPacket::entityId,
                RequestInhabitantDebugPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record InhabitantDebugPacket(int entityId, String debugText) implements CustomPacketPayload {
        public static final Type<InhabitantDebugPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant_debug"));
        public static final StreamCodec<RegistryFriendlyByteBuf, InhabitantDebugPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, InhabitantDebugPacket::entityId,
                ByteBufCodecs.STRING_UTF8, InhabitantDebugPacket::debugText,
                InhabitantDebugPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AssignHousePacket(BlockPos markerPos, String inhabitantId) implements CustomPacketPayload {
        public static final Type<AssignHousePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "assign_house"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AssignHousePacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, AssignHousePacket::markerPos,
                ByteBufCodecs.STRING_UTF8, AssignHousePacket::inhabitantId,
                AssignHousePacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AssignWorkerPacket(BlockPos markerPos, String inhabitantId) implements CustomPacketPayload {
        public static final Type<AssignWorkerPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "assign_worker"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AssignWorkerPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, AssignWorkerPacket::markerPos,
                ByteBufCodecs.STRING_UTF8, AssignWorkerPacket::inhabitantId,
                AssignWorkerPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetWorkAreaCenterPacket(BlockPos markerPos, BlockPos center) implements CustomPacketPayload {
        public static final Type<SetWorkAreaCenterPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "set_work_area_center"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetWorkAreaCenterPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetWorkAreaCenterPacket::markerPos,
                BlockPos.STREAM_CODEC, SetWorkAreaCenterPacket::center,
                SetWorkAreaCenterPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetWorkAreaRadiusPacket(BlockPos markerPos, int radius) implements CustomPacketPayload {
        public static final Type<SetWorkAreaRadiusPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "set_work_area_radius"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetWorkAreaRadiusPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetWorkAreaRadiusPacket::markerPos,
                ByteBufCodecs.VAR_INT, SetWorkAreaRadiusPacket::radius,
                SetWorkAreaRadiusPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TreeScanClearPacket() implements CustomPacketPayload {
        public static final Type<TreeScanClearPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "tree_scan_clear"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TreeScanClearPacket> CODEC =
                StreamCodec.unit(new TreeScanClearPacket());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** {@code category}: 0 = trunk log, 1 = confirming/canopy leaf, 2 = blocking neighbor. */
    public record TreeScanHighlightPacket(int category, List<BlockPos> positions) implements CustomPacketPayload {
        public static final Type<TreeScanHighlightPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "tree_scan_highlight"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TreeScanHighlightPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TreeScanHighlightPacket::category,
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), TreeScanHighlightPacket::positions,
                TreeScanHighlightPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenStructureScreenPacket(String structureName, String structureType, BlockPos markerPos,
                                            String inhabitantData, String assignedInhabitantId, String assignedWorkerId,
                                            BlockPos workAreaCenter, int workAreaRadius, String itemFilterData) implements CustomPacketPayload {
        public static final Type<OpenStructureScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "open_structure_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenStructureScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::structureName,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::structureType,
                BlockPos.STREAM_CODEC, OpenStructureScreenPacket::markerPos,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::inhabitantData,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::assignedInhabitantId,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::assignedWorkerId,
                BlockPos.STREAM_CODEC, OpenStructureScreenPacket::workAreaCenter,
                ByteBufCodecs.VAR_INT, OpenStructureScreenPacket::workAreaRadius,
                ByteBufCodecs.STRING_UTF8, OpenStructureScreenPacket::itemFilterData,
                OpenStructureScreenPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RefreshInhabitantScreenPacket(int entityId) implements CustomPacketPayload {
        public static final Type<RefreshInhabitantScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "refresh_inhabitant_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RefreshInhabitantScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, RefreshInhabitantScreenPacket::entityId,
                RefreshInhabitantScreenPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RefreshStructureScreenPacket(BlockPos markerPos) implements CustomPacketPayload {
        public static final Type<RefreshStructureScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "refresh_structure_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RefreshStructureScreenPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RefreshStructureScreenPacket::markerPos,
                RefreshStructureScreenPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RequestRequestsScreenPacket(BlockPos bannerPos) implements CustomPacketPayload {
        public static final Type<RequestRequestsScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "request_requests_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestRequestsScreenPacket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RequestRequestsScreenPacket::bannerPos,
                RequestRequestsScreenPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenRequestsScreenPacket(String dominionName, String requestsData, BlockPos bannerPos) implements CustomPacketPayload {
        public static final Type<OpenRequestsScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "open_requests_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenRequestsScreenPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenRequestsScreenPacket::dominionName,
                ByteBufCodecs.STRING_UTF8, OpenRequestsScreenPacket::requestsData,
                BlockPos.STREAM_CODEC, OpenRequestsScreenPacket::bannerPos,
                OpenRequestsScreenPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}

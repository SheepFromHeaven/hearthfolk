package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;

import java.util.List;

// Kept separate from ModNetwork so that class's bytecode never references Minecraft/Screen
// directly; those references crash channel registration on a dedicated server.
public class ClientNetworkHandlers {
    public static void openDominionScreen(ModNetwork.OpenDominionScreenPacket msg) {
        Minecraft.getInstance().execute(() -> {
            var inhabitantNames = msg.inhabitantNames().isEmpty()
                    ? List.<String>of()
                    : List.of(msg.inhabitantNames().split(","));
            Minecraft.getInstance().setScreen(DominionScreen.create(
                    msg.dominionName(), DominionTier.valueOf(msg.tier()), msg.bannerPos(), inhabitantNames));
        });
    }

    public static void openInhabitantScreen(ModNetwork.OpenInhabitantScreenPacket msg) {
        Minecraft.getInstance().execute(() -> {
            var issues = msg.issuesData().isEmpty() ? List.<String>of()
                    : List.of(msg.issuesData().split(java.util.regex.Pattern.quote(ModNetwork.INHABITANT_FIELD_SEP), -1));
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof InhabitantScreen screen && screen.matchesEntity(msg.entityId())) {
                screen.update(msg.shelter(), msg.food(), msg.rest(), msg.jobStatus(), issues);
            } else {
                mc.setScreen(new InhabitantScreen(
                        msg.entityId(), msg.name(), msg.dominionName(), msg.shelter(), msg.food(), msg.rest(),
                        msg.jobStatus(), msg.debugEnabled(), issues));
            }
        });
    }

    public static void updateInhabitantDebug(ModNetwork.InhabitantDebugPacket msg) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof InhabitantScreen screen && screen.matchesEntity(msg.entityId())) {
                screen.setDebugLines(List.of(msg.debugText().split(ModNetwork.INHABITANT_FIELD_SEP, -1)));
            }
        });
    }

    public static void clearTreeScanOutline() {
        Minecraft.getInstance().execute(TreeScanOutlineTracker::clear);
    }

    public static void addTreeScanHighlight(ModNetwork.TreeScanHighlightPacket msg) {
        Minecraft.getInstance().execute(() -> TreeScanOutlineTracker.add(msg.category(), msg.positions()));
    }

    public static void updatePathNodes(ModNetwork.PathNodeSyncPacket msg) {
        Minecraft.getInstance().execute(() -> PathNodeTracker.set(msg.positions()));
    }

    public static void openStructureScreen(ModNetwork.OpenStructureScreenPacket msg) {
        Minecraft.getInstance().execute(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof StructureMarkerScreen screen && screen.matchesMarker(msg.markerPos())) {
                screen.update(msg.inhabitantData(), msg.assignedInhabitantId(), msg.assignedWorkerId(), msg.itemFilterData());
            } else {
                mc.setScreen(new StructureMarkerScreen(
                        msg.structureName(), StructureType.valueOf(msg.structureType()), msg.markerPos(),
                        msg.inhabitantData(), msg.assignedInhabitantId(), msg.assignedWorkerId(),
                        msg.workAreaCenter(), msg.workAreaRadius(), msg.itemFilterData()));
            }
        });
    }

    public static void openRequestsScreen(ModNetwork.OpenRequestsScreenPacket msg) {
        Minecraft.getInstance().execute(() -> {
            var entries = RequestsScreen.decode(msg.requestsData());
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof RequestsScreen screen) {
                screen.update(entries);
            } else {
                mc.setScreen(new RequestsScreen(mc.screen, msg.dominionName(), entries, msg.bannerPos()));
            }
        });
    }
}

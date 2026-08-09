package com.sheepfromheaven.minenow.network.client;

import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;

import java.util.List;
import com.sheepfromheaven.minenow.dominion.client.DominionScreen;
import com.sheepfromheaven.minenow.inhabitant.immigration.client.ImmigrantRequestScreen;
import com.sheepfromheaven.minenow.inhabitant.client.InhabitantScreen;
import com.sheepfromheaven.minenow.pathfinding.client.PathNodeTracker;
import com.sheepfromheaven.minenow.client.RequestsScreen;
import com.sheepfromheaven.minenow.structure.client.StructureMarkerScreen;
import com.sheepfromheaven.minenow.jobs.lumberjack.client.TreeScanOutlineTracker;

// Kept separate from ModNetwork so that class's bytecode never references Minecraft/Screen
// directly; those references crash channel registration on a dedicated server.
public class ClientNetworkHandlers {
    public static void openDominionScreen(ModNetwork.OpenDominionScreenPacket msg) {
        Minecraft.getInstance().execute(() -> {
            var inhabitantNames = msg.inhabitantNames().isEmpty()
                    ? List.<String>of()
                    : List.of(msg.inhabitantNames().split(","));
            Minecraft.getInstance().setScreen(DominionScreen.create(
                    msg.dominionName(), DominionTier.valueOf(msg.tier()), msg.bannerPos(), inhabitantNames,
                    msg.milestoneProgress()));
        });
    }

    public static void openInhabitantScreen(ModNetwork.OpenInhabitantScreenPacket msg) {
        Minecraft.getInstance().execute(() -> {
            var issues = msg.issuesData().isEmpty() ? List.<String>of()
                    : List.of(msg.issuesData().split(java.util.regex.Pattern.quote(ModNetwork.INHABITANT_FIELD_SEP), -1));
            var mc = Minecraft.getInstance();
            // The screen itself is always opened via the inhabitant inventory menu now; this packet
            // only ever refreshes an already-open one (both on first open and on periodic refresh).
            if (mc.screen instanceof InhabitantScreen screen && screen.matchesEntity(msg.entityId())) {
                screen.update(msg.name(), msg.dominionName(), msg.debugEnabled(),
                        msg.shelter(), msg.food(), msg.rest(), msg.jobStatus(), issues);
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
        Minecraft.getInstance().execute(() -> PathNodeTracker.set(msg.positions(), msg.impacts()));
    }

    public static void openStructureScreen(ModNetwork.OpenStructureScreenPacket msg) {
        Minecraft.getInstance().execute(() -> {
            var mc = Minecraft.getInstance();
            var info = msg.levelInfo();
            if (mc.screen instanceof StructureMarkerScreen screen && screen.matchesMarker(msg.markerPos())) {
                screen.update(msg.inhabitantData(), msg.assignedResidentData(), msg.assignedWorkerData(),
                        info.level(), info.levelCap(), info.requirementData(), info.levelUpRequirementData(),
                        info.inhabitantSlots(), info.functioningInhabitantSlots());
            } else {
                mc.setScreen(new StructureMarkerScreen(
                        msg.structureName(), StructureType.valueOf(msg.structureType()), msg.markerPos(),
                        msg.inhabitantData(), msg.assignedResidentData(), msg.assignedWorkerData(),
                        msg.workAreaCenter(), msg.workAreaRadius(),
                        info.level(), info.levelCap(), info.requirementData(), info.levelUpRequirementData(),
                        info.inhabitantSlots(), info.functioningInhabitantSlots()));
            }
        });
    }

    public static void openImmigrantRequestScreen(ModNetwork.OpenImmigrantRequestScreenPacket msg) {
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(ImmigrantRequestScreen.create(msg.entityId(), msg.candidateName())));
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

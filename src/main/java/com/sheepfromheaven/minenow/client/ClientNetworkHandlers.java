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
            Minecraft.getInstance().setScreen(new DominionScreen(
                    msg.dominionName(), DominionTier.valueOf(msg.tier()), msg.bannerPos(), inhabitantNames));
        });
    }

    public static void openInhabitantScreen(ModNetwork.OpenInhabitantScreenPacket msg) {
        Minecraft.getInstance().execute(() -> {
            var issues = msg.issuesData().isEmpty() ? List.<String>of() : List.of(msg.issuesData().split(ModNetwork.INHABITANT_FIELD_SEP));
            Minecraft.getInstance().setScreen(new InhabitantScreen(
                    msg.entityId(), msg.name(), msg.dominionName(), msg.shelter(), msg.food(), msg.rest(), msg.jobStatus(),
                    msg.debugEnabled(), issues));
        });
    }

    public static void updateInhabitantDebug(ModNetwork.InhabitantDebugPacket msg) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof InhabitantScreen screen && screen.matchesEntity(msg.entityId())) {
                screen.setDebugLines(List.of(msg.debugText().split(ModNetwork.INHABITANT_FIELD_SEP, -1)));
            }
        });
    }

    public static void openStructureScreen(ModNetwork.OpenStructureScreenPacket msg) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new StructureMarkerScreen(
                msg.structureName(), StructureType.valueOf(msg.structureType()), msg.markerPos(),
                msg.inhabitantData(), msg.assignedInhabitantId(), msg.assignedWorkerId(),
                msg.workAreaCenter(), msg.workAreaRadius())));
    }
}

package com.sheepfromheaven.minenow.pathfinding.client;

import com.sheepfromheaven.minenow.pathfinding.DominionPathNode;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

/** Client-side mirror of the path nodes the server has revealed to this player (see {@link PathNodeGizmoRenderer}). */
public class PathNodeTracker {
    private static Map<BlockPos, DominionPathNode.Impact> nodes = Map.of();

    public static void set(List<BlockPos> positions, List<Integer> impactOrdinals) {
        var builder = new java.util.HashMap<BlockPos, DominionPathNode.Impact>();
        var impacts = DominionPathNode.Impact.values();
        for (int i = 0; i < positions.size(); i++) {
            int ord = (i < impactOrdinals.size()) ? impactOrdinals.get(i) : DominionPathNode.Impact.MEDIUM.ordinal();
            builder.put(positions.get(i), impacts[Math.max(0, Math.min(ord, impacts.length - 1))]);
        }
        nodes = Map.copyOf(builder);
    }

    public static Map<BlockPos, DominionPathNode.Impact> get() {
        return nodes;
    }
}

package com.sheepfromheaven.minenow.dominion;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * A player-placed soft movement hint: no physical block, just a position inhabitants prefer to
 * walk near. Unordered - nodes form a preferred zone rather than an explicit path graph. The
 * {@link Kind} exists so the same node system can back both street routing and, later, guard
 * patrol zones without a second data model.
 */
public class DominionPathNode {
    private final UUID id;
    private final BlockPos pos;
    private final Kind kind;

    public enum Kind {
        STREET,
        PATROL
    }

    public DominionPathNode(BlockPos pos, Kind kind) {
        this(UUID.randomUUID(), pos, kind);
    }

    public DominionPathNode(UUID id, BlockPos pos, Kind kind) {
        this.id = id;
        this.pos = pos;
        this.kind = kind;
    }

    public UUID getId() {
        return id;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Kind getKind() {
        return kind;
    }
}

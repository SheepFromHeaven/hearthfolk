package com.sheepfromheaven.minenow.dominion;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * A player-placed soft movement hint: no physical block, just a position inhabitants prefer to
 * walk near. Unordered - nodes form a preferred zone rather than an explicit path graph. The
 * {@link Kind} exists so the same node system can back both street routing and, later, guard
 * patrol zones without a second data model.
 *
 * <p>Each node has an {@link Impact} level (LOW / MEDIUM / HIGH) that controls how strongly it
 * attracts inhabitants: high-impact nodes win selection even when geometrically farther away.
 * Shift+right-click with the path-node wand cycles through the three levels.
 */
public class DominionPathNode {
    private final UUID id;
    private final BlockPos pos;
    private final Kind kind;
    private Impact impact;

    public enum Kind {
        STREET,
        PATROL
    }

    /**
     * How close an inhabitant must get before it considers the node "reached" and stops walking.
     * HIGH = walks almost to the exact block (stop within 1.5 blocks).
     * MEDIUM = stops a few blocks away, path noticeably diverts toward the node.
     * LOW = stops well short; the path is nudged in the node's direction but the inhabitant
     *       doesn't commit to walking all the way there.
     */
    public enum Impact {
        LOW(10.0 * 10.0),
        MEDIUM(4.0 * 4.0),
        HIGH(1.5 * 1.5);

        private final double stopRadiusSq;

        Impact(double stopRadiusSq) {
            this.stopRadiusSq = stopRadiusSq;
        }

        /** Squared distance at which an inhabitant stops walking toward a node of this impact. */
        public double stopRadiusSq() {
            return stopRadiusSq;
        }

        public Impact next() {
            var values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public DominionPathNode(BlockPos pos, Kind kind) {
        this(UUID.randomUUID(), pos, kind, Impact.MEDIUM);
    }

    public DominionPathNode(BlockPos pos, Kind kind, Impact impact) {
        this(UUID.randomUUID(), pos, kind, impact);
    }

    public DominionPathNode(UUID id, BlockPos pos, Kind kind, Impact impact) {
        this.id = id;
        this.pos = pos;
        this.kind = kind;
        this.impact = impact;
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

    public Impact getImpact() {
        return impact;
    }

}

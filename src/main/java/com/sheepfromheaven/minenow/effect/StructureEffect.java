package com.sheepfromheaven.minenow.effect;

import com.sheepfromheaven.minenow.structure.DominionStructure;

/**
 * A bonus applied to a structure's {@link StructureStats}, e.g. "+1 inhabitant slot" or "+25%
 * productivity". Currently only granted by level-ups ({@link com.sheepfromheaven.minenow.structure.StructureType#getLevelEffects}),
 * but nothing here assumes that - any future source (an item upgrade, a seasonal buff, ...) can fold
 * more of these into {@link DominionStructure#getStats()}.
 */
public interface StructureEffect {
    void apply(DominionStructure structure, StructureStats stats);

    /**
     * Contributes any {@link com.sheepfromheaven.minenow.structure.workarea.WorkArea}s this effect grants
     * (a static area around the structure, its interior, ...). Kept separate from {@link #apply} so the
     * scalar fold in {@link DominionStructure#getStats()} stays cheap - area geometry is only materialised
     * off that hot path (see the foraging-area resolution in {@link DominionStructure}). No-op by default;
     * only area-granting effects override it.
     */
    default void contributeWorkAreas(DominionStructure structure,
                                     java.util.List<com.sheepfromheaven.minenow.structure.workarea.WorkArea> out) {
    }
}

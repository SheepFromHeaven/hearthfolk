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
}

package com.sheepfromheaven.minenow.effect;

import com.sheepfromheaven.minenow.structure.DominionStructure;

/**
 * Grows a structure's placement {@link com.sheepfromheaven.minenow.structure.Territory} by {@code x}/{@code y}/{@code z}
 * blocks along each axis. Stacks additively across levels (a Mine granting +21 at level 1 and +6 at level 2
 * ends up 27 wide). The structure marker stays centred: if an axis total comes out even, one block is added
 * automatically so a single centre block always exists (see {@link DominionStructure#getTerritory()}).
 */
public final class TerritoryEffect implements StructureEffect {
    private final int x;
    private final int y;
    private final int z;

    public TerritoryEffect(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void apply(DominionStructure structure, StructureStats stats) {
        stats.addTerritory(x, y, z);
    }
}

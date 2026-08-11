package com.sheepfromheaven.minenow.effect;

import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.workarea.CylinderArea;
import com.sheepfromheaven.minenow.structure.workarea.WorkArea;

import java.util.List;

/**
 * Grants a single fixed work area centred on the structure marker - the "one area around the structure"
 * shape used by jobs that forage a radius (lumberjack tree search, forester replanting) rather than working
 * player-placed markers. Non-editable: the radius comes from level config, so higher levels can widen it by
 * granting a larger {@code staticWorkArea} instead of the player dragging a slider.
 */
public final class StaticWorkAreaEffect implements StructureEffect {
    private final int radius;

    public StaticWorkAreaEffect(int radius) {
        this.radius = radius;
    }

    public int getRadius() {
        return radius;
    }

    @Override
    public void apply(DominionStructure structure, StructureStats stats) {
        // Contributes geometry, not a scalar stat - see contributeWorkAreas.
    }

    @Override
    public void contributeWorkAreas(DominionStructure structure, List<WorkArea> out) {
        out.add(new CylinderArea(structure.getMarkerPos(), radius, radius));
    }
}

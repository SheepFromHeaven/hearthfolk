package com.sheepfromheaven.minenow.effect;

import com.sheepfromheaven.minenow.structure.DominionStructure;

/** Multiplies a structure's productivity (e.g. 1.25 = +25%), stacking multiplicatively with other sources. */
public final class ProductivityMultiplierEffect implements StructureEffect {
    private final double multiplier;

    public ProductivityMultiplierEffect(double multiplier) {
        this.multiplier = multiplier;
    }

    @Override
    public void apply(DominionStructure structure, StructureStats stats) {
        stats.multiplyProductivity(multiplier);
    }
}

package com.sheepfromheaven.minenow.effect;

import com.sheepfromheaven.minenow.structure.DominionStructure;

/**
 * Grants extra inhabitant capacity to a structure - workers for a workplace, residents for a house. The
 * structure itself decides which role that capacity is used for; this effect just adds to the shared slot
 * count.
 */
public final class InhabitantSlotsEffect implements StructureEffect {
    private final int amount;

    public InhabitantSlotsEffect(int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public void apply(DominionStructure structure, StructureStats stats) {
        stats.addInhabitantSlots(amount);
    }
}

package com.sheepfromheaven.minenow.effect;

/**
 * Accumulated result of folding a structure's active {@link StructureEffect}s. Never persisted -
 * recomputed on demand. {@code inhabitantSlots} starts at 0 - even a level-1 structure only gets a slot if
 * its own level-1 definition grants one via {@link InhabitantSlotsEffect}, since some types may want more
 * than one from the start (workers for a workplace, residents for a house).
 */
public final class StructureStats {
    private int inhabitantSlots = 0;
    private double productivityMultiplier = 1.0;
    private int territorySizeX = 0;
    private int territorySizeY = 0;
    private int territorySizeZ = 0;

    public int getInhabitantSlots() {
        return inhabitantSlots;
    }

    public double getProductivityMultiplier() {
        return productivityMultiplier;
    }

    /** Accumulated territory extent along each axis (raw sum before odd-centring). 0 = no territory. */
    public int getTerritorySizeX() {
        return territorySizeX;
    }

    public int getTerritorySizeY() {
        return territorySizeY;
    }

    public int getTerritorySizeZ() {
        return territorySizeZ;
    }

    public void addInhabitantSlots(int amount) {
        inhabitantSlots += amount;
    }

    public void multiplyProductivity(double factor) {
        productivityMultiplier *= factor;
    }

    public void addTerritory(int x, int y, int z) {
        territorySizeX += x;
        territorySizeY += y;
        territorySizeZ += z;
    }
}

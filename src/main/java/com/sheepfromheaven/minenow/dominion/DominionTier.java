package com.sheepfromheaven.minenow.dominion;

import javax.annotation.Nullable;

public enum DominionTier {
    SETTLEMENT("Settlement", 3, 1.0),
    VILLAGE("Village", 8, 1.5),
    TOWN("Town", 20, 2.0),
    CITY("City", 50, 3.0);

    private final String displayName;
    private final int maxInhabitants;
    private final double radiusMultiplier;

    DominionTier(String displayName, int maxInhabitants, double radiusMultiplier) {
        this.displayName = displayName;
        this.maxInhabitants = maxInhabitants;
        this.radiusMultiplier = radiusMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxInhabitants() {
        return maxInhabitants;
    }

    /**
     * Multiplier applied to {@code Config.defaultDominionRadius} to get this tier's effective claimed
     * radius - higher tiers claim proportionally more land as they grow (see {@link Dominion#getEffectiveRadius()}).
     */
    public double getRadiusMultiplier() {
        return radiusMultiplier;
    }

    /**
     * Highest {@link com.sheepfromheaven.minenow.structure.DominionStructure} level any structure in a
     * dominion at this tier may reach - one level per tier reached so far (SETTLEMENT allows level 1,
     * VILLAGE allows level 2, ...), independent of each structure type's own {@code MAX_LEVEL} cap.
     */
    public int getMaxStructureLevel() {
        return ordinal() + 1;
    }

    /** The tier that follows this one, or {@code null} if already at the max tier ({@link #CITY}). */
    @Nullable
    public DominionTier next() {
        var values = DominionTier.values();
        int nextOrdinal = ordinal() + 1;
        return nextOrdinal < values.length ? values[nextOrdinal] : null;
    }

    /**
     * The milestone requirements a dominion at this tier must meet to advance to {@link #next()}.
     * {@code null} at {@link #CITY} - there is nothing further to advance to.
     */
    @Nullable
    public DominionMilestone getLevelUpMilestone() {
        return switch (this) {
            case SETTLEMENT -> new DominionMilestone(3, 3, 32);
            case VILLAGE -> new DominionMilestone(8, 8, 128);
            case TOWN -> new DominionMilestone(20, 20, 512);
            case CITY -> null;
        };
    }

    /** Requirements to advance from one tier to the next. */
    public record DominionMilestone(int minPopulation, int minStructures, int minStoredResources) {
    }
}

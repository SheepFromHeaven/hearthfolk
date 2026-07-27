package com.sheepfromheaven.minenow.dominion;

public enum DominionTier {
    SETTLEMENT("Settlement", 3),
    VILLAGE("Village", 8),
    TOWN("Town", 20),
    CITY("City", 50);

    private final String displayName;
    private final int maxInhabitants;

    DominionTier(String displayName, int maxInhabitants) {
        this.displayName = displayName;
        this.maxInhabitants = maxInhabitants;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxInhabitants() {
        return maxInhabitants;
    }
}

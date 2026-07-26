package com.sheepfromheaven.minenow.dominion;

public enum DominionTier {
    SETTLEMENT("Settlement"),
    VILLAGE("Village"),
    TOWN("Town"),
    CITY("City");

    private final String displayName;

    DominionTier(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

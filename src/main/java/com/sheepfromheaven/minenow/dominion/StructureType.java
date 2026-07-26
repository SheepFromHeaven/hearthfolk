package com.sheepfromheaven.minenow.dominion;

public enum StructureType {
    HOUSE("House"),
    FISHERY("Fishery");

    private final String displayName;

    StructureType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

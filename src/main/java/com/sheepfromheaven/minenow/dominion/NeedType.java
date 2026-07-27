package com.sheepfromheaven.minenow.dominion;

public enum NeedType {
    SHELTER("Shelter"),
    FOOD("Food"),
    REST("Rest");

    private final String displayName;

    NeedType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

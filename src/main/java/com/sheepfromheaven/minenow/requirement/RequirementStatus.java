package com.sheepfromheaven.minenow.requirement;

/** Progress toward a single {@link Requirement}: how many of {@code required} have been {@code found}. */
public record RequirementStatus(String name, int required, int found) {
    public boolean satisfied() {
        return found >= required;
    }
}

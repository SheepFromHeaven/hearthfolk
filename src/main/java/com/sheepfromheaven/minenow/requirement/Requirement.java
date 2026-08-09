package com.sheepfromheaven.minenow.requirement;

/**
 * Something that gates a build step or level-up: room space, a specific block being present, another
 * structure existing at a given level, etc. Evaluated fresh against live world/dominion state each time
 * (nothing here is cached), so a requirement can never go stale.
 */
public interface Requirement {
    String getDisplayName();

    RequirementStatus evaluate(RequirementContext ctx);
}

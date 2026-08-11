package com.sheepfromheaven.minenow.requirement;

import com.sheepfromheaven.minenow.structure.MarkerKind;

/**
 * Requires at least {@code count} work-area markers of a given {@link MarkerKind} to be assigned to the
 * structure and sitting within its {@link com.sheepfromheaven.minenow.structure.Territory}. This is how a
 * Mine/Farm turns "place your claim/field markers" into an actual work condition - unmet, the structure is
 * non-operational and its workers won't work, exactly like an unmet block requirement.
 *
 * <p>Reports unmet (found 0) when the structure isn't attached to a dominion yet or has no territory, per
 * the {@link RequirementContext} contract.
 */
public final class WorkAreaMarkerRequirement implements Requirement {
    private final MarkerKind kind;
    private final int count;
    private final String displayName;

    public WorkAreaMarkerRequirement(MarkerKind kind, int count, String displayName) {
        this.kind = kind;
        this.count = count;
        this.displayName = displayName;
    }

    public MarkerKind kind() {
        return kind;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public RequirementStatus evaluate(RequirementContext ctx) {
        int found = 0;
        var dominion = ctx.dominion();
        if (dominion != null) {
            var territory = ctx.structure().getTerritory();
            if (territory != null) {
                for (var pos : kind.assignedMarkerPositions(dominion, ctx.structure().getId())) {
                    if (territory.contains(pos)) found++;
                }
            }
        }
        return new RequirementStatus(displayName, count, found);
    }
}

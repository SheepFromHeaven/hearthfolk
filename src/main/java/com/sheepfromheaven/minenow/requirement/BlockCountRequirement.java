package com.sheepfromheaven.minenow.requirement;

import com.sheepfromheaven.minenow.structure.StructureType;

/** Requires at least {@code count} blocks matching a predicate to be present in the structure's interior/walls. */
public final class BlockCountRequirement implements Requirement {
    private final StructureType.Requirement raw;

    public BlockCountRequirement(StructureType.Requirement raw) {
        this.raw = raw;
    }

    /** The underlying block/tag + icon definition, e.g. for GUI slot rendering. */
    public StructureType.Requirement raw() {
        return raw;
    }

    @Override
    public String getDisplayName() {
        return raw.displayName();
    }

    @Override
    public RequirementStatus evaluate(RequirementContext ctx) {
        int found = 0;
        for (var pos : ctx.structure().getInteriorAndWalls()) {
            if (raw.matcher().test(ctx.level().getBlockState(pos))) {
                found++;
            }
        }
        return new RequirementStatus(raw.displayName(), raw.count(), found);
    }
}

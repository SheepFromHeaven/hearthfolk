package com.sheepfromheaven.minenow.requirement;

/** Requires the structure's interior room to contain at least {@code minSize} blocks. */
public final class RoomSizeRequirement implements Requirement {
    private final int minSize;

    public RoomSizeRequirement(int minSize) {
        this.minSize = minSize;
    }

    @Override
    public String getDisplayName() {
        return "Space";
    }

    @Override
    public RequirementStatus evaluate(RequirementContext ctx) {
        return new RequirementStatus(getDisplayName(), minSize, ctx.structure().getRoomBlocks().size());
    }
}

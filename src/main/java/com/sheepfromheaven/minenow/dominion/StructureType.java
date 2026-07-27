package com.sheepfromheaven.minenow.dominion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public enum StructureType {
    HOUSE("House", null),
    FISHERY("Fishery", JobType.FISHER),
    LUMBERYARD("Lumberyard", JobType.LUMBERJACK);

    private final String displayName;
    private final JobType jobType;

    StructureType(String displayName, JobType jobType) {
        this.displayName = displayName;
        this.jobType = jobType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Optional<JobType> getJobType() {
        return Optional.ofNullable(jobType);
    }

    public List<Requirement> getRequirements() {
        return StructureRequirementsConfig.getRequirements(this);
    }

    public int getMinSize() {
        return StructureRequirementsConfig.getMinSize(this);
    }

    public int getRangeRadius() {
        return StructureRequirementsConfig.getRangeRadius(this);
    }

    public int getRangeHeight() {
        return StructureRequirementsConfig.getRangeHeight(this);
    }

    public List<Requirement> getRangeRequirements() {
        return StructureRequirementsConfig.getRangeRequirements(this);
    }

    public record Requirement(String displayName, int count, Predicate<BlockState> matcher, Supplier<ItemStack> icon, int airAbove) {
        public Requirement(String displayName, int count, Predicate<BlockState> matcher, Supplier<ItemStack> icon) {
            this(displayName, count, matcher, icon, 0);
        }
    }
}

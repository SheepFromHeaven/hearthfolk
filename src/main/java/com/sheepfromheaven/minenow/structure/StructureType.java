package com.sheepfromheaven.minenow.structure;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import com.sheepfromheaven.minenow.jobs.JobType;

public enum StructureType {
    HOUSE("House", null),
    FISHERY("Fishery", JobType.FISHER),
    LUMBERYARD("Lumberyard", JobType.LUMBERJACK),
    TAVERN("Tavern", JobType.COOK),
    STOREHOUSE("Storehouse", JobType.COURIER),
    MINE("Mine", JobType.MINER),
    NURSERY("Nursery", JobType.FORESTER),
    CHARCOAL_KILN("Charcoal Kiln", JobType.CHARCOAL_BURNER),
    FARM("Farm", JobType.FARMER);

    /** Highest level any structure can reach, shared across all types and further capped by the dominion's tier. */
    public static final int MAX_LEVEL = 4;

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

    public int getRangeRadius() {
        return StructureRequirementsConfig.getRangeRadius(this);
    }

    public int getRangeHeight() {
        return StructureRequirementsConfig.getRangeHeight(this);
    }

    public List<Requirement> getRangeRequirements() {
        return StructureRequirementsConfig.getRangeRequirements(this);
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    /**
     * Everything required for a structure of this type to count as functional/built at {@code level}
     * (level 1 = the base build state): the merge of every level's own requirements from 1 up to
     * {@code level}, keyed by requirement name so a higher level's version of the same requirement (e.g.
     * more barrels) replaces the lower one instead of stacking with it. A requirement introduced at one
     * level and never mentioned again keeps applying forever. Backed entirely by {@link StructureLevelJsonConfig}.
     */
    public List<com.sheepfromheaven.minenow.requirement.Requirement> getRequirements(int level) {
        var byName = new LinkedHashMap<String, com.sheepfromheaven.minenow.requirement.Requirement>();
        for (int lvl = 1; lvl <= level; lvl++) {
            for (var requirement : StructureLevelJsonConfig.getRequirements(this, lvl)) {
                byName.put(requirement.getDisplayName(), requirement);
            }
        }
        return List.copyOf(byName.values());
    }

    /** Effects granted once a structure reaches exactly {@code level}; see {@link StructureLevelJsonConfig}. */
    public List<com.sheepfromheaven.minenow.effect.StructureEffect> getLevelEffects(int level) {
        return StructureLevelJsonConfig.getLevelEffects(this, level);
    }

    public record Requirement(String displayName, int count, Predicate<BlockState> matcher, Supplier<ItemStack> icon, int airAbove) {
        public Requirement(String displayName, int count, Predicate<BlockState> matcher, Supplier<ItemStack> icon) {
            this(displayName, count, matcher, icon, 0);
        }
    }
}

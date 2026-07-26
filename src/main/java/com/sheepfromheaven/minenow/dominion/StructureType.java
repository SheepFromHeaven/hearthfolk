package com.sheepfromheaven.minenow.dominion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    public List<Requirement> getRequirements() {
        return StructureRequirementsConfig.getRequirements(this);
    }

    public record Requirement(String displayName, int count, Predicate<BlockState> matcher, Supplier<ItemStack> icon) {}
}

package com.sheepfromheaven.minenow.dominion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A request from a structure for items — either URGENT (work is blocked, surfaces as an issue)
 * or SOFT (proactive restocking to keep the building running fluidly).
 *
 * {@code item} may be null for category requests (e.g. "any fuel", "any raw food"). In that case
 * {@code label} is the only human-readable description.
 */
public record StructureRequest(
        UUID structureId,
        @Nullable Item item,
        String label,
        int amount,
        Priority priority,
        long createdTick
) {
    public enum Priority { SOFT, URGENT }

    public static StructureRequest forItem(UUID structureId, Item item, int amount, Priority priority, long createdTick) {
        var name = item.getDefaultInstance().getHoverName().getString();
        return new StructureRequest(structureId, item, name, amount, priority, createdTick);
    }

    public static StructureRequest forCategory(UUID structureId, String label, int amount, Priority priority, long createdTick) {
        return new StructureRequest(structureId, null, label, amount, priority, createdTick);
    }

    /** Registry key of the requested item, or empty string for category requests. */
    public String itemKey() {
        return item != null ? BuiltInRegistries.ITEM.getKey(item).toString() : "";
    }

    @Nullable
    public static Item itemFromKey(String key) {
        return key.isEmpty() ? null : BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
    }
}

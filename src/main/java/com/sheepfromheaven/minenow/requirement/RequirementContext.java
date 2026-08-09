package com.sheepfromheaven.minenow.requirement;

import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Live state a {@link Requirement} evaluates against. {@code dominion} is nullable for structures not
 * (yet) attached to one - a requirement that needs it (e.g. one checking another structure elsewhere in
 * the dominion) should report unmet in that case rather than throwing.
 */
public record RequirementContext(Level level, @Nullable Dominion dominion, DominionStructure structure) {
}

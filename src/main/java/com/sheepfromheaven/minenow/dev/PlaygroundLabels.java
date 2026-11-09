package com.sheepfromheaven.minenow.dev;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/** Floating text over the gametest playground: a readable name per test (and its failure), plus an overview board. */
final class PlaygroundLabels {
    private static final int LINE_WIDTH = 260;
    private static final int BACKGROUND = 0x80000000;

    enum State { NOT_RUN, RUNNING, PASSED, FAILED }

    private PlaygroundLabels() {}

    static String humanize(String testName) {
        String words = testName.replace('_', ' ').trim();
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String summary(int passed, int failed, int notRun, int running) {
        return passed + " passed · " + failed + " failed · " + notRun + " not run · " + running + " running";
    }

    static State stateOf(TestInstanceBlockEntity block) {
        if (block.errorMessage().isPresent()) return State.FAILED;
        return switch (block.data.status()) {
            case CLEARED -> State.NOT_RUN;
            case RUNNING -> State.RUNNING;
            case FINISHED -> State.PASSED;
        };
    }

    /** The beacon beam on the test block already shows its status; the label adds the name and why it failed. */
    static Component testLabel(String testName, State state, Optional<Component> error) {
        MutableComponent text = Component.literal(humanize(testName)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        if (state == State.FAILED) {
            text.append("\n").append(error.orElse(Component.empty()).copy().withStyle(ChatFormatting.RED));
        }
        return text;
    }

    static Display.TextDisplay spawn(ServerLevel level, Vec3 at, Component text, float scale) {
        Display.TextDisplay label = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        label.snapTo(at.x, at.y, at.z);
        label.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        label.setLineWidth(LINE_WIDTH);
        label.setBackgroundColor(BACKGROUND);
        label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        label.setText(text);
        level.addFreshEntity(label);
        return label;
    }
}

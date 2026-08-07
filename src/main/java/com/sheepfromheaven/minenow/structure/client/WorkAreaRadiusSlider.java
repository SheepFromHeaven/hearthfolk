package com.sheepfromheaven.minenow.structure.client;

import com.sheepfromheaven.minenow.structure.DominionStructure;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** A radius slider bounded to {@link DominionStructure}'s work area min/max, following vanilla's AbstractSliderButton. */
public class WorkAreaRadiusSlider extends AbstractSliderButton {
    private static final int MIN = DominionStructure.MIN_WORK_AREA_RADIUS;
    private static final int MAX = DominionStructure.MAX_WORK_AREA_RADIUS;

    private final IntConsumer onChange;

    public WorkAreaRadiusSlider(int x, int y, int width, int height, int initialRadius, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), normalize(initialRadius));
        this.onChange = onChange;
        updateMessage();
    }

    private static double normalize(int radius) {
        return (double) (radius - MIN) / (MAX - MIN);
    }

    private int currentRadius() {
        return MIN + (int) Math.round(value * (MAX - MIN));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal("Radius: " + currentRadius()));
    }

    @Override
    protected void applyValue() {
        onChange.accept(currentRadius());
    }
}

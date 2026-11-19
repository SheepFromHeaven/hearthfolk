package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Feature;
import net.minecraft.gametest.framework.GameTestHelper;

public final class FeatureTests {
    private FeatureTests() {}

    public static void everyFeatureIsOnByDefault(GameTestHelper helper) {
        for (Feature feature : Feature.values()) {
            helper.assertTrue(MineNowConfig.enabled(feature), feature.getSerializedName() + " is on by default");
        }
        helper.succeed();
    }

    public static void aFeatureEnvironmentSwitchesOnlyItsFeatureOff(GameTestHelper helper) {
        for (Feature feature : Feature.values()) {
            boolean expected = feature != Feature.VILLAGERS_EAT_MEALS;
            helper.assertTrue(MineNowConfig.enabled(feature) == expected,
                    feature.getSerializedName() + (expected ? " stays on" : " is off in its environment"));
        }
        helper.succeed();
    }
}

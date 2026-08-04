package com.sheepfromheaven.minenow.client;

import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class Screen2 extends SpecScreen {

    private String inputValue = "";

    public Screen2(ScreenSpec spec) {
        super(Component.literal("Screen 2"), spec);
    }

    public static Screen2 create() {
        var mc = Minecraft.getInstance();
        ScreenSpec spec = ScreenSpecLoader.fromResource(mc.getResourceManager(), "minenow", "screen_2");
        return new Screen2(spec);
    }

    @Override
    protected void init() {
        super.init();
        on("input_1006", (id, s, value) -> inputValue = (String) value);
        on("minenow:test_action", (id, s, value) -> {
            var player = Minecraft.getInstance().player;
            if (player != null) player.connection.sendChat(inputValue);
        });
    }
}

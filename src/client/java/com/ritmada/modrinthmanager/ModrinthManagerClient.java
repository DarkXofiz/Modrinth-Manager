package com.ritmada.modrinthmanager;

import com.ritmada.modrinthmanager.gui.ModrinthManagerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModrinthManagerClient implements ClientModInitializer {

    public static final String MOD_ID = "modrinth-manager";
    public static final Logger LOGGER = LoggerFactory.getLogger("ModrinthManager");

    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.modrinth-manager.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.modrinth-manager.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ModrinthManagerScreen(client.currentScreen));
                }
            }
        });

        LOGGER.info("[modrinth-manager] initialized, press M in-game to open the store");
    }
}

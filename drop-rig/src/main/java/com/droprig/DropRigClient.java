package com.droprig;

import com.droprig.config.ConfigStore;
import com.droprig.config.DropRigConfig;
import com.droprig.core.KeyBindings;
import com.droprig.core.Side;
import com.droprig.gui.DropRigScreen;
import com.droprig.rig.DisplayRigger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drop Rig client entrypoint.
 *
 * <p>Drop Rig is <b>display-only</b>: it changes how the dropped items look on <em>your</em> screen.
 * Which item a dropper actually ejects is chosen by the server, and nothing here can or does
 * change that. Other players, the server, chat announcements and the items you pick up all see
 * the real result.
 */
public final class DropRigClient implements ClientModInitializer {
    public static final String MOD_ID = "droprig";
    public static final Logger LOG = LoggerFactory.getLogger("Drop Rig");

    private static ConfigStore store;
    private static DropRigConfig config = new DropRigConfig();
    private static DisplayRigger rigger;

    @Override
    public void onInitializeClient() {
        store = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("droprig.json"), LOG::warn);
        config = store.load();
        rigger = new DisplayRigger();
        rigger.applyConfig(config);

        ClientTickEvents.END_CLIENT_TICK.register(client -> rigger.tick(client));
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item) rigger.onLoad(item);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item) rigger.onUnload(item);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> rigger.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> rigger.reset(false));
        LOG.info("Drop Rig loaded (display-only). Rigging {}", config.enabled ? "ON" : "OFF");
    }

    public static DropRigConfig config() {
        return config;
    }

    public static DisplayRigger rigger() {
        return rigger;
    }

    public static void save() {
        if (store != null) store.save(config);
    }

    public static void setEnabled(boolean enabled) {
        if (config.enabled == enabled) return;
        config.enabled = enabled;
        if (rigger != null) rigger.applyConfig(config);
        save();
    }

    public static void setRigged(Side side) {
        if (config.rigged() == side) return;
        config.riggedDropper = side.number;
        if (rigger != null) rigger.applyConfig(config);
        save();
    }

    public static void applyKeyBindings(KeyBindings keys) {
        config.applyKeyBindings(keys);
        save();
    }

    /** Called from {@code KeyboardMixin} for every raw key event. */
    public static void onRawKey(long window, int key, int action) {
        if (action != GLFW.GLFW_PRESS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || window != client.getWindow().getHandle()) return;
        if (client.currentScreen != null || client.player == null) return; // never fire while typing in chat/menus
        KeyBindings.Action act = config.keyBindings().actionFor(key);
        if (act == null) return;
        if (act == KeyBindings.Action.TOGGLE_RIG) {
            setEnabled(!config.enabled);
            if (config.showToggleMessage) {
                client.player.sendMessage(Text.literal("Drop Rig  ").formatted(Formatting.AQUA)
                        .append(config.enabled
                                ? Text.literal("ON").formatted(Formatting.GREEN, Formatting.BOLD)
                                : Text.literal("OFF").formatted(Formatting.GRAY, Formatting.BOLD))
                        .append(Text.literal(config.enabled ? "  · Dropper " + config.riggedDropper + " shows higher" : "")
                                .formatted(Formatting.DARK_GRAY)), true);
            }
        } else {
            client.setScreen(new DropRigScreen());
        }
    }
}

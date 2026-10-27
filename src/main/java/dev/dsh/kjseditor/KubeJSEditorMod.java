package dev.dsh.kjseditor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dsh.kjseditor.client.EditorScreen;
import dev.dsh.kjseditor.data.RecipeLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(KubeJSEditorMod.MODID)
public class KubeJSEditorMod {
    public static final String MODID = "kubejs_editor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.kubejs_editor.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.kubejs_editor");

    public KubeJSEditorMod(IEventBus modBus) {
        RecipeLoader.load();
        LOGGER.info("KubeJS Visual Editor loaded");
        modBus.addListener(KubeJSEditorMod::registerKeys);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR);
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            while (OPEN_EDITOR.consumeClick()) {
                Minecraft.getInstance().setScreen(new EditorScreen());
            }
        }
    }
}

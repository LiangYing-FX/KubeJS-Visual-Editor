package dev.dsh.kjseditor.compat;

import dev.dsh.kjseditor.client.EditorScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/** Only loaded when JEI is present: JEI scans for @JeiPlugin itself. */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.parse("kubejs_editor:jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(EditorScreen.class, new EditorGhostHandler());
        // Tells JEI this screen has a "gui rect" so the ingredient list is overlaid
        // while the editor is open. Without it JEI hides itself on custom screens.
        registration.addGuiScreenHandler(EditorScreen.class, screen -> new IGuiProperties() {
            @Override
            public Class<? extends Screen> screenClass() {
                return EditorScreen.class;
            }

            @Override
            public int guiLeft() {
                return screen.guiLeft();
            }

            @Override
            public int guiTop() {
                return screen.guiTop();
            }

            @Override
            public int guiXSize() {
                return screen.guiWidth();
            }

            @Override
            public int guiYSize() {
                return screen.guiHeight();
            }

            @Override
            public int screenWidth() {
                return screen.width;
            }

            @Override
            public int screenHeight() {
                return screen.height;
            }
        });
    }
}

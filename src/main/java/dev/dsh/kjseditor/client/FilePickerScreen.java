package dev.dsh.kjseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AWT is unusable here (the client runs headless), so textures are picked from
 * folders instead: kubejs_editor/imports (drop your own pngs there) and the
 * existing textures already inside kubejs/assets.
 */
public class FilePickerScreen extends Screen {
    private static final int ROW = 18;
    private static final int MAX_FILES = 300;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<String[]> entries = new ArrayList<>();
    private int scroll;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    public FilePickerScreen(Screen parent, Consumer<String> onPick) {
        super(Lang.of("kubejs_editor.texture.title", "选择材质"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        listW = Math.min(this.width - 40, 480);
        listX = (this.width - listW) / 2;
        listY = 62;
        listH = this.height - listY - 40;

        if (entries.isEmpty()) {
            scan();
        }
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.back", "返回"), b -> this.minecraft.setScreen(parent))
                .bounds(listX, this.height - 32, 80, 18).build());
    }

    private void scan() {
        Path game = FMLPaths.GAMEDIR.get();
        List<Path> roots = List.of(
                game.resolve("kubejs_editor").resolve("imports"),
                game.resolve("kubejs").resolve("assets"));
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root, 8)) {
                stream.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".png"))
                        .limit(MAX_FILES)
                        .forEach(p -> entries.add(new String[]{game.relativize(p).toString(), p.toString()}));
            } catch (Exception ignored) {
            }
        }
        entries.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xF0101014);
        g.drawString(this.font, Lang.str("kubejs_editor.texture.title", "选择材质") + "  (" + entries.size() + ")", listX, 22, 0xFFFFFFFF, false);
        g.drawString(this.font, Lang.str("kubejs_editor.texture.hint", "把 png 放进 kubejs_editor/imports/ 就会出现在这里"), listX, 38, 0xFF888888, false);

        if (entries.isEmpty()) {
            g.drawString(this.font, Lang.str("kubejs_editor.texture.empty", "没有找到 png 文件"), listX, listY + 6, 0xFFFF8888, false);
        } else {
            int rows = listH / ROW;
            int start = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - rows)));
            for (int i = 0; i < rows; i++) {
                int idx = start + i;
                if (idx >= entries.size()) {
                    break;
                }
                int y = listY + i * ROW;
                boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW;
                g.fill(listX, y, listX + listW, y + ROW, hover ? 0x60FFFFFF : (i % 2 == 0 ? 0x30000000 : 0x20000000));
                String label = this.font.plainSubstrByWidth(entries.get(idx)[0], listW - 10);
                g.drawString(this.font, label, listX + 4, y + 5, hover ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int row = (int) ((mouseY - listY) / ROW);
            int idx = scroll + row;
            if (idx >= 0 && idx < entries.size()) {
                onPick.accept(entries.get(idx)[1]);
                this.minecraft.setScreen(parent);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = listH / ROW;
        int max = Math.max(0, entries.size() - rows);
        scroll = Math.max(0, Math.min(max, scroll + (scrollY < 0 ? 1 : -1)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package dev.dsh.kjseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Registers a KubeJS item and copies a local PNG in as its texture.
 * Writes kubejs/startup_scripts/editor_items.js and appends instead of replacing.
 */
public class ItemCreatorScreen extends Screen {
    private final Screen parent;
    private EditBox nsBox;
    private EditBox nameBox;
    private EditBox displayBox;
    private String texturePath = "";
    private String status = "";
    private int x0;
    private int y0;
    private int panelW;
    private int panelH;

    public ItemCreatorScreen(Screen parent) {
        super(Lang.of("kubejs_editor.item.title", "注册物品"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        if (status.isEmpty()) {
            status = Lang.str("kubejs_editor.status.texture_hint", "材质从 kubejs_editor/imports/ 或现有 assets 里选");
        }
        panelW = Math.min(this.width - 20, 480);
        panelH = Math.min(this.height - 20, 270);
        x0 = (this.width - panelW) / 2;
        y0 = (this.height - panelH) / 2;

        nsBox = new EditBox(this.font, x0 + 96, y0 + 36, 150, 16, Component.literal("命名空间"));
        nsBox.setMaxLength(64);
        nsBox.setValue("kubejs");
        this.addRenderableWidget(nsBox);

        nameBox = new EditBox(this.font, x0 + 96, y0 + 62, 150, 16, Component.literal("物品名"));
        nameBox.setMaxLength(64);
        nameBox.setValue("custom_item");
        this.addRenderableWidget(nameBox);

        displayBox = new EditBox(this.font, x0 + 96, y0 + 88, 260, 16, Component.literal("显示名"));
        displayBox.setMaxLength(128);
        displayBox.setValue("自定义物品");
        this.addRenderableWidget(displayBox);

        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.pick_png", "选择 PNG"), b -> pickTexture())
                .bounds(x0 + 96, y0 + 114, 90, 18).build());
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.save", "保存"), b -> save())
                .bounds(x0 + 96, y0 + panelH - 34, 100, 18).build());
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.back", "返回"), b -> this.minecraft.setScreen(parent))
                .bounds(x0 + 202, y0 + panelH - 34, 100, 18).build());
    }

    private void pickTexture() {
        // AWT is headless in this client, so pick from a folder listing instead.
        this.minecraft.setScreen(new FilePickerScreen(this, path -> texturePath = path));
    }

    private void save() {
        String ns = nsBox.getValue().trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
        String name = nameBox.getValue().trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
        String display = displayBox.getValue().trim();
        if (ns.isEmpty() || name.isEmpty()) {
            status = Lang.str("kubejs_editor.status.ns_required", "命名空间和物品名不能为空");
            return;
        }
        Path game = FMLPaths.GAMEDIR.get();
        try {
            if (!texturePath.isEmpty()) {
                Path dest = game.resolve("kubejs/assets/" + ns + "/textures/item/" + name + ".png");
                Files.createDirectories(dest.getParent());
                Files.copy(Path.of(texturePath), dest, StandardCopyOption.REPLACE_EXISTING);
            }

            Path script = game.resolve("kubejs/startup_scripts/editor_items.js");
            Files.createDirectories(script.getParent());
            String id = ns + ":" + name;
            String existing = Files.exists(script) ? Files.readString(script) : "";
            if (existing.contains("'" + id + "'")) {
                status = Lang.str("kubejs_editor.status.item_exists", "物品 %s 已存在", id);
                return;
            }

            String entry = "    event.create('" + id + "')\n"
                    + "        .displayName('" + display.replace("'", "") + "')\n"
                    + "        .texture('" + ns + ":item/" + name + "')\n";
            if (existing.isBlank()) {
                existing = "StartupEvents.registry('item', event => {\n" + entry + "})\n";
            } else {
                int close = existing.lastIndexOf("})");
                if (close < 0) {
                    status = Lang.str("kubejs_editor.status.script_broken", "脚本格式异常，请手动检查 editor_items.js");
                    return;
                }
                existing = existing.substring(0, close) + entry + existing.substring(close);
            }
            Files.writeString(script, existing);
            status = Lang.str("kubejs_editor.status.item_registered", "已注册 %s，重启游戏后生效", id);
        } catch (Exception e) {
            status = Lang.str("kubejs_editor.status.save_failed", "保存失败：%s", e.getMessage());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0101014);
        g.fill(x0, y0, x0 + panelW, y0 + panelH, 0xE6101014);
        g.fill(x0, y0, x0 + panelW, y0 + 1, 0xFF4A9EFF);
        g.drawString(this.font, Lang.str("kubejs_editor.item.title", "注册物品"), x0 + 8, y0 + 10, 0xFFFFFFFF, false);

        g.drawString(this.font, Lang.str("kubejs_editor.item.namespace", "命名空间"), x0 + 8, y0 + 40, 0xFFFFD479, false);
        g.drawString(this.font, Lang.str("kubejs_editor.item.name", "物品名"), x0 + 8, y0 + 66, 0xFFFFD479, false);
        g.drawString(this.font, Lang.str("kubejs_editor.item.display", "显示名"), x0 + 8, y0 + 92, 0xFFFFD479, false);
        g.drawString(this.font, Lang.str("kubejs_editor.item.texture", "材质"), x0 + 8, y0 + 118, 0xFFFFD479, false);
        if (!texturePath.isEmpty()) {
            String shortPath = this.font.plainSubstrByWidth(texturePath, panelW - 210);
            g.drawString(this.font, shortPath, x0 + 192, y0 + 118, 0xFF9E9E9E, false);
        }

        g.drawString(this.font, status, x0 + 8, y0 + panelH - 14, 0xFF9E9E9E, false);
        super.render(g, mouseX, mouseY, partialTick);
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

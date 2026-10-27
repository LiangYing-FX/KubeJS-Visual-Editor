package dev.dsh.kjseditor.client;

import dev.dsh.kjseditor.data.RecipeField;
import dev.dsh.kjseditor.data.RecipeKind;
import dev.dsh.kjseditor.gen.RecipeEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Edits one nested step of a sequenced recipe. */
public class StepEditorScreen extends Screen {
    private final Screen parent;
    private final List<RecipeKind> options;
    private final Consumer<RecipeEntry> onAdd;
    private final Map<String, String> values = new HashMap<>();

    private int kindIndex;
    private int x0;
    private int y0;
    private int panelW = 420;
    private int panelH = 250;

    public StepEditorScreen(Screen parent, List<RecipeKind> options, Consumer<RecipeEntry> onAdd) {
        super(Lang.of("kubejs_editor.step.title", "添加步骤"));
        this.parent = parent;
        this.options = options;
        this.onAdd = onAdd;
    }

    private RecipeKind kind() {
        return options.get(Math.max(0, Math.min(kindIndex, options.size() - 1)));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        panelW = Math.min(this.width - 20, 440);
        panelH = Math.min(this.height - 20, 260);
        x0 = (this.width - panelW) / 2;
        y0 = (this.height - panelH) / 2;

        int bx = x0 + 8;
        for (int i = 0; i < options.size(); i++) {
            int idx = i;
            RecipeKind k = options.get(i);
            String label = (i == kindIndex ? "> " : "") + k.name();
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                kindIndex = idx;
                values.clear();
                this.rebuildWidgets();
            }).bounds(bx, y0 + 30, 84, 16).build());
            bx += 86;
        }

        RecipeKind k = kind();
        int fy = y0 + 62;
        for (RecipeField f : k.fields()) {
            if (f.type() == dev.dsh.kjseditor.data.FieldType.NESTED) {
                continue;
            }
            EditBox box = new EditBox(this.font, x0 + 8 + 56, fy, panelW - 16 - 56, 16, Component.literal(f.label()));
            box.setMaxLength(256);
            box.setValue(values.computeIfAbsent(f.key(), kk -> f.def()));
            box.setResponder(s -> values.put(f.key(), s));
            this.addRenderableWidget(box);
            fy += 26;
        }

        int bottom = y0 + panelH - 26;
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.add_step", "添加步骤"), b -> {
            onAdd.accept(RecipeEntry.recipe(kind(), values));
            this.minecraft.setScreen(parent);
        }).bounds(x0 + 8, bottom, 120, 18).build());
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.cancel", "取消"), b -> this.minecraft.setScreen(parent))
                .bounds(x0 + 132, bottom, 100, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0101014);
        g.fill(x0, y0, x0 + panelW, y0 + panelH, 0xE6101014);
        g.fill(x0, y0, x0 + panelW, y0 + 1, 0xFF4A9EFF);
        g.drawString(this.font, Lang.str("kubejs_editor.step.type", "步骤类型") + ": " + kind().name(), x0 + 8, y0 + 12, 0xFFFFFFFF, false);

        int fy = y0 + 62;
        for (RecipeField f : kind().fields()) {
            if (f.type() == dev.dsh.kjseditor.data.FieldType.NESTED) {
                continue;
            }
            g.drawString(this.font, f.label(), x0 + 8, fy + 4, 0xFFFFD479, false);
            fy += 26;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 1.21.1 default would blur everything drawn before super.render(). */
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

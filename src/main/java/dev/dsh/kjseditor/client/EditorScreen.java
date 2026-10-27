package dev.dsh.kjseditor.client;

import dev.dsh.kjseditor.data.FieldType;
import dev.dsh.kjseditor.data.ModSupport;
import dev.dsh.kjseditor.data.RecipeField;
import dev.dsh.kjseditor.data.RecipeKind;
import dev.dsh.kjseditor.data.RecipeLoader;
import dev.dsh.kjseditor.gen.RecipeEntry;
import dev.dsh.kjseditor.gen.ScriptGen;
import dev.dsh.kjseditor.gen.ScriptImporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EditorScreen extends Screen {
    private static final int ROW = 20;
    private static final int VISIBLE_KINDS = 12;

    private static int modIndex;
    private static int kindIndex;
    private static final Map<String, String> values = new HashMap<>();
    private static final List<RecipeEntry> queue = new ArrayList<>();

    private final List<EditBox> boxes = new ArrayList<>();
    private final List<String> boxKeys = new ArrayList<>();
    private String status = "";
    private int gridX;
    private int gridY;
    private int outX;
    private int outY;
    private int formBottom;
    private static final String[] GRID_ORDER = {"a", "b", "c", "d", "e", "f", "g", "h", "i"};
    private static boolean jeiRefreshed;

    private static boolean isGridField(String key) {
        return java.util.Arrays.asList(GRID_ORDER).contains(key);
    }
    private static final int CELL = 20;
    private static final int CELL_GAP = 2;

    /** A region that accepts a dragged ingredient. */
    public record DropTarget(Rect2i area, Consumer<String> fill) {
    }
    private int kindScroll;
    private int x0;
    private int y0;
    private int panelW;
    private int panelH;

    public EditorScreen() {
        super(Component.literal("KubeJS Visual Editor"));
    }

    private List<ModSupport> mods() {
        return RecipeLoader.mods();
    }

    private ModSupport mod() {
        List<ModSupport> m = mods();
        if (m.isEmpty()) {
            return null;
        }
        modIndex = Math.max(0, Math.min(modIndex, m.size() - 1));
        return m.get(modIndex);
    }

    private RecipeKind kind() {
        ModSupport m = mod();
        if (m == null || m.kinds().isEmpty()) {
            return null;
        }
        kindIndex = Math.max(0, Math.min(kindIndex, m.kinds().size() - 1));
        return m.kinds().get(kindIndex);
    }

    private static List<String> shapelessInputs(RecipeKind k) {
        for (RecipeField f : k.fields()) {
            if (f.type() == FieldType.LIST) {
                return ScriptGen.list(k, f);
            }
        }
        return new ArrayList<>();
    }

    private String gridValue(int i) {
        RecipeKind k = kind();
        if (k == null) {
            return "";
        }
        if ("shaped".equals(k.id())) {
            return values.getOrDefault(GRID_ORDER[i], "");
        }
        List<String> list = shapelessInputs(k);
        return i < list.size() ? list.get(i) : "";
    }

    private void setGridValue(int i, String id) {
        RecipeKind k = kind();
        if (k == null) {
            return;
        }
        if ("shaped".equals(k.id())) {
            values.put(GRID_ORDER[i], id);
            return;
        }
        List<String> list = shapelessInputs(k);
        while (list.size() <= i) {
            list.add("");
        }
        list.set(i, id);
        while (!list.isEmpty() && list.get(list.size() - 1).isEmpty()) {
            list.remove(list.size() - 1);
        }
    }

    private static ItemStack stackOf(String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation loc = ResourceLocation.tryParse(id.trim());
            if (loc == null) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.get(loc);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** Panel rect, used by the JEI screen handler. */
    public int guiLeft() {
        return x0;
    }

    public int guiTop() {
        return y0;
    }

    public int guiWidth() {
        return panelW;
    }

    public int guiHeight() {
        return panelH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (gridX > 0) {
            for (int i = 0; i < 9; i++) {
                int gx = gridX + (i % 3) * (CELL + CELL_GAP);
                int gy = gridY + (i / 3) * (CELL + CELL_GAP);
                if (mouseX >= gx && mouseX < gx + CELL && mouseY >= gy && mouseY < gy + CELL) {
                    if (button == 1) {
                        setGridValue(i, "");
                    } else {
                        final int slot = i;
                        this.minecraft.setScreen(new ItemPickerScreen(this, picked -> setGridValue(slot, picked)));
                    }
                    return true;
                }
            }
            if (mouseX >= outX && mouseX < outX + 26 && mouseY >= outY && mouseY < outY + 26) {
                if (button == 1) {
                    values.put("out", "");
                } else {
                    this.minecraft.setScreen(new ItemPickerScreen(this, picked -> values.put("out", picked)));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Some recipe types live in a separate addon mod, not in the mod itself:
     * mekanism needs KubeJS-Mekanism, create needs KubeJS-Create, AE2 support comes
     * from Applied KubeJS. Mark the entry unavailable when the addon is missing,
     * otherwise scripts reference recipe types that were never registered.
     */
    private static boolean isAvailable(ModSupport m) {
        if (!ModList.get().isLoaded(m.id())) {
            return false;
        }
        return switch (m.id()) {
            case "mekanism" -> ModList.get().isLoaded("kubejs_mekanism");
            case "create" -> ModList.get().isLoaded("kubejs_create");
            case "ae2" -> ModList.get().isLoaded("appliedkubejs")
                    || ModList.get().isLoaded("applied_kubejs")
                    || ModList.get().isLoaded("appliedkjs");
            default -> true;
        };
    }

    private static List<RecipeKind> resolveSubKinds(RecipeField field) {
        List<RecipeKind> out = new ArrayList<>();
        for (String ref : field.subKindsOrEmpty()) {
            int c = ref.indexOf(':');
            String modId = c > 0 ? ref.substring(0, c) : "";
            String kindId = c > 0 ? ref.substring(c + 1) : ref;
            for (ModSupport m : RecipeLoader.mods()) {
                if (!modId.isEmpty() && !m.id().equals(modId)) {
                    continue;
                }
                for (RecipeKind k : m.kinds()) {
                    if (k.id().equals(kindId)) {
                        out.add(k);
                    }
                }
            }
        }
        return out;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.boxes.clear();
        this.boxKeys.clear();

        panelW = Math.min(this.width - 16, 660);
        panelH = Math.min(this.height - 16, 430);
        x0 = (this.width - panelW) / 2;
        y0 = (this.height - panelH) / 2;

        List<ModSupport> mods = mods();
        if (mods.isEmpty()) {
            status = Lang.str("kubejs_editor.status.no_recipes", "未找到内置 recipes.json");
            return;
        }

        for (int i = 0; i < mods.size(); i++) {
            ModSupport m = mods.get(i);
            boolean installed = isAvailable(m);
            String label = (i == modIndex ? "> " : "") + m.name() + (installed ? "" : " *");
            int idx = i;
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                modIndex = idx;
                kindIndex = 0;
                kindScroll = 0;
                values.clear();
                this.rebuildWidgets();
            }).bounds(x0 + 6, y0 + 32 + i * 20, 120, 18).build());
        }

        ModSupport current = mod();
        if (current != null) {
            int visible = Math.min(VISIBLE_KINDS, current.kinds().size());
            int listY = y0 + 32;
            for (int i = 0; i < visible; i++) {
                int idx = kindScroll + i;
                if (idx >= current.kinds().size()) {
                    break;
                }
                RecipeKind k = current.kinds().get(idx);
                String label = (idx == kindIndex ? "> " : "") + Lang.kind(current.id(), k.id(), k.name());
                int click = idx;
                this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    kindIndex = click;
                    values.clear();
                    this.rebuildWidgets();
                }).bounds(x0 + 132, listY + i * 18, 118, 16).build());
            }
            if (current.kinds().size() > VISIBLE_KINDS) {
                this.addRenderableWidget(Button.builder(Component.literal("^"), b -> {
                    kindScroll = Math.max(0, kindScroll - 1);
                    this.rebuildWidgets();
                }).bounds(x0 + 132, listY + VISIBLE_KINDS * 18, 20, 14).build());
                this.addRenderableWidget(Button.builder(Component.literal("v"), b -> {
                    kindScroll = Math.min(Math.max(0, current.kinds().size() - VISIBLE_KINDS), kindScroll + 1);
                    this.rebuildWidgets();
                }).bounds(x0 + 154, listY + VISIBLE_KINDS * 18, 20, 14).build());
            }
        }

        RecipeKind k = kind();
        if (k != null) {
            int formX = x0 + 258;
            int fieldW = panelW - 300;
            int formY = y0 + 36;
            gridX = 0;
            gridY = 0;
            outX = 0;
            outY = 0;
            boolean gridKind = "shaped".equals(k.id()) || "shapeless".equals(k.id());
            boolean gridPlaced = false;
            for (RecipeField f : k.fields()) {
                String key = f.key();
                if (gridKind && (isGridField(key) || "out".equals(key) || "count".equals(key) || f.type() == FieldType.LIST)) {
                    if (!gridPlaced) {
                        gridPlaced = true;
                        gridX = formX;
                        gridY = formY;
                        formY += 3 * (CELL + CELL_GAP);
                        outX = gridX + 3 * (CELL + CELL_GAP) + 26;
                        outY = gridY + CELL + CELL_GAP;
                        EditBox countBox = new EditBox(this.font, outX, outY + 32, 40, 14, Component.literal("数量"));
                        countBox.setMaxLength(4);
                        countBox.setValue(values.computeIfAbsent("count", kk -> "1"));
                        countBox.setResponder(s -> values.put("count", s));
                        this.addRenderableWidget(countBox);
                    }
                    continue;
                }
                if (f.type() == FieldType.NESTED) {
                    int count = ScriptGen.steps(k, f).size();
                    this.addRenderableWidget(Button.builder(Component.literal(Lang.str("kubejs_editor.button.steps", "步骤") + " (" + count + ")"), b -> {
                        List<RecipeKind> subs = resolveSubKinds(f);
                        if (!subs.isEmpty()) {
                            this.minecraft.setScreen(new StepEditorScreen(this, subs, entry -> {
                                ScriptGen.steps(kind(), f).add(entry);
                                this.rebuildWidgets();
                            }));
                        }
                    }).bounds(formX, formY, 80, 16).build());
                    this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.clear", "清空"), b -> {
                        ScriptGen.steps(kind(), f).clear();
                        this.rebuildWidgets();
                    }).bounds(formX + 84, formY, 44, 16).build());
                    formY += ROW;
                    continue;
                }
                if (f.type() == FieldType.LIST) {
                    int count = ScriptGen.list(k, f).size();
                    this.addRenderableWidget(Button.builder(Component.literal(Lang.str("kubejs_editor.button.entries", "条目") + " (" + count + ")"), b ->
                            this.minecraft.setScreen(new ItemPickerScreen(this, picked -> {
                                ScriptGen.list(kind(), f).add(picked);
                                this.rebuildWidgets();
                            }))
                    ).bounds(formX, formY, 80, 16).build());
                    this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.clear", "清空"), b -> {
                        ScriptGen.list(kind(), f).clear();
                        this.rebuildWidgets();
                    }).bounds(formX + 84, formY, 44, 16).build());
                    formY += ROW;
                    continue;
                }
                EditBox box = new EditBox(this.font, formX + 66, formY, fieldW - 66, 16, Component.literal(Lang.field(f.label())));
                box.setMaxLength(512);
                box.setValue(values.computeIfAbsent(key, kk -> f.def()));
                box.setResponder(s -> values.put(key, s));
                this.addRenderableWidget(box);
                this.boxes.add(box);
                this.boxKeys.add(key);

                if (f.type() == FieldType.ITEM || f.type() == FieldType.ITEM_LIST || f.type() == FieldType.FLUID) {
                    this.addRenderableWidget(Button.builder(Component.literal("..."), b ->
                            this.minecraft.setScreen(new ItemPickerScreen(this, picked -> {
                                String cur = values.getOrDefault(key, "");
                                String next = (f.type() == FieldType.ITEM_LIST && !cur.isBlank())
                                        ? cur + "," + picked
                                        : picked;
                                values.put(key, next);
                                box.setValue(next);
                            }))
                    ).bounds(formX + fieldW + 2, formY, 18, 16).build());
                }
                formY += ROW;
            }
            formBottom = formY;
        }

        // JEI only picks up the screen handler on the second Init; force one extra
        // setScreen so the ingredient list shows up on the first open as well.
        if (!jeiRefreshed) {
            jeiRefreshed = true;
            this.minecraft.execute(() -> {
                if (this.minecraft.screen == this) {
                    this.minecraft.setScreen(this);
                }
            });
        }

        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.import", "导入"), b -> importScripts())
                .bounds(x0 + panelW - 68, y0 + 4, 62, 14).build());
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.register_item", "注册物品"), b ->
                        this.minecraft.setScreen(new ItemCreatorScreen(this)))
                .bounds(x0 + panelW - 152, y0 + 4, 78, 14).build());
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.changes", "已更改"), b ->
                        this.minecraft.setScreen(new ChangesScreen(this)))
                .bounds(x0 + 6, y0 + panelH - 30, 64, 16).build());

        int bottom = y0 + panelH - 46;
        int bw = (panelW - 270) / 2;
        int fx = x0 + 258;
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.add_queue", "加入队列"), b -> {
            RecipeKind cur = kind();
            if (cur != null) {
                queue.add(RecipeEntry.recipe(cur, values));
                status = Lang.str("kubejs_editor.status.queued", "已加入队列 %s（共 %s）", cur.id(), queue.size());
            }
        }).bounds(fx, bottom, bw, 18).build());
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.save_all", "保存全部"), b -> save())
                .bounds(fx + bw + 4, bottom, bw, 18).build());
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.delete_recipe", "删除配方"), b -> {
            RecipeKind cur = kind();
            if (cur != null) {
                queue.add(RecipeEntry.removal(cur, values));
                status = Lang.str("kubejs_editor.status.queued_removal", "已加入删除：%s", cur.id());
            }
        }).bounds(fx, bottom + 22, bw, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(Lang.str("kubejs_editor.button.clear_queue", "清空队列") + " (" + queue.size() + ")"), b -> {
            queue.clear();
            status = Lang.str("kubejs_editor.status.cleared", "队列已清空");
            this.rebuildWidgets();
        }).bounds(fx + bw + 4, bottom + 22, bw, 18).build());
    }

    /** Field rectangles that JEI may drop an item onto. */
    public List<DropTarget> dropTargets() {
        List<DropTarget> out = new ArrayList<>();
        if (gridX > 0) {
            for (int i = 0; i < 9; i++) {
                int gx = gridX + (i % 3) * (CELL + CELL_GAP);
                int gy = gridY + (i / 3) * (CELL + CELL_GAP);
                final int slot = i;
                out.add(new DropTarget(new Rect2i(gx, gy, CELL, CELL), picked -> setGridValue(slot, picked)));
            }
            if (outX > 0) {
                out.add(new DropTarget(new Rect2i(outX, outY, 26, 26), picked -> values.put("out", picked)));
            }
        }
        for (int i = 0; i < boxes.size() && i < boxKeys.size(); i++) {
            EditBox box = boxes.get(i);
            String key = boxKeys.get(i);
            out.add(new DropTarget(new Rect2i(box.getX(), box.getY(), box.getWidth(), box.getHeight()), picked -> {
                values.put(key, picked);
                box.setValue(picked);
            }));
        }
        return out;
    }

    private void importScripts() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("server_scripts");
        if (!Files.isDirectory(dir)) {
            status = Lang.str("kubejs_editor.status.no_dir", "没有 kubejs/server_scripts 目录");
            return;
        }
        Map<String, RecipeKind> idx = ScriptImporter.index(allKinds());
        dev.dsh.kjseditor.KubeJSEditorMod.LOGGER.info("import: scanning {} ({} kinds indexed)", dir, idx.size());
        int total = 0;
        int files = 0;
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.filter(f -> f.toString().endsWith(".js")).toList()) {
                total += ScriptImporter.importText(Files.readString(p), idx, queue::add);
                files++;
            }
        } catch (Exception e) {
            status = Lang.str("kubejs_editor.status.import_failed", "导入失败：%s", e.getMessage());
            return;
        }
        dev.dsh.kjseditor.KubeJSEditorMod.LOGGER.info("import: {} files, {} recipes", files, total);
        status = Lang.str("kubejs_editor.status.imported", "从 %s 个文件导入 %s 条", files, total);
        this.rebuildWidgets();
    }

    private static List<RecipeKind> allKinds() {
        List<RecipeKind> out = new ArrayList<>();
        for (ModSupport m : RecipeLoader.mods()) {
            out.addAll(m.kinds());
        }
        return out;
    }

    /**
     * One file per recipe type, merged with what is already on disk.
     * Saving no longer wipes previous work; identical bodies are skipped.
     */
    private void save() {
        try {
            RecipeKind cur = kind();
            List<RecipeEntry> pending = new ArrayList<>(queue);
            if (cur != null) {
                pending.add(RecipeEntry.recipe(cur, values));
            }
            Path dir = FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("server_scripts").resolve("visual");
            Files.createDirectories(dir);

            // Removals all live in one file so they are easy to review and roll back.
            List<RecipeEntry> removals = new ArrayList<>();
            List<RecipeEntry> recipes = new ArrayList<>();
            for (RecipeEntry e : pending) {
                (e.remove() ? removals : recipes).add(e);
            }
            Map<String, RecipeKind> index0 = ScriptImporter.index(allKinds());
            int removed = 0;
            if (!removals.isEmpty()) {
                Path out = dir.resolve("removals.js");
                List<RecipeEntry> merged = new ArrayList<>();
                if (Files.exists(out)) {
                    ScriptImporter.importText(Files.readString(out), index0, merged::add);
                }
                for (RecipeEntry e : removals) {
                    String body = ScriptGen.buildRemove(e.kind(), e.values());
                    boolean dup = merged.stream().anyMatch(o -> o.remove()
                            && body.equals(ScriptGen.buildRemove(o.kind(), o.values())));
                    if (!dup) {
                        merged.add(e);
                        removed++;
                    }
                }
                Files.writeString(out, ScriptGen.buildFile(merged));
            }

            // One folder per mod, one file per recipe type, so a broken file only
            // takes down its own recipe type instead of the whole export.
            Map<String, List<RecipeEntry>> byKind = new java.util.LinkedHashMap<>();
            Map<String, String[]> paths = new java.util.LinkedHashMap<>();
            for (RecipeEntry e : recipes) {
                String mod = e.kind().modId() == null || e.kind().modId().isEmpty() ? "minecraft" : e.kind().modId();
                String type = e.kind().id().replace('/', '_').replace(':', '_');
                String key = mod + "/" + type;
                byKind.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
                paths.put(key, new String[]{mod, type});
            }

            Map<String, RecipeKind> index = ScriptImporter.index(allKinds());
            int added = 0;
            int skipped = 0;
            for (Map.Entry<String, List<RecipeEntry>> group : byKind.entrySet()) {
                String[] parts = paths.get(group.getKey());
                Path out = dir.resolve(parts[0]).resolve(parts[1] + ".js");
                Files.createDirectories(out.getParent());
                List<RecipeEntry> merged = new ArrayList<>();
                if (Files.exists(out)) {
                    ScriptImporter.importText(Files.readString(out), index, merged::add);
                }
                Path legacy = dir.resolve(parts[0] + "_" + parts[1] + ".js");
                if (Files.exists(legacy)) {
                    ScriptImporter.importText(Files.readString(legacy), index, merged::add);
                }
                for (RecipeEntry e : group.getValue()) {
                    String body = ScriptGen.buildBody(e.kind(), e.values());
                    boolean dup = merged.stream()
                            .anyMatch(o -> body.equals(ScriptGen.buildBody(o.kind(), o.values())));
                    if (dup) {
                        skipped++;
                    } else {
                        merged.add(e);
                        added++;
                    }
                }
                Files.writeString(out, ScriptGen.buildFile(merged));
            }
            String extra = skipped > 0
                    ? Lang.str("kubejs_editor.status.skipped", "，跳过重复 %s 条", skipped) : "";
            status = Lang.str("kubejs_editor.status.saved", "新增配方 %s 条，删除 %s 条%s", added, removed, extra)
                    + Lang.str("kubejs_editor.status.saved_to", " → kubejs/server_scripts/visual/");
        } catch (Exception e) {
            status = Lang.str("kubejs_editor.status.save_failed", "保存失败：%s", e.getMessage());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Same blur gotcha as ItemPickerScreen: renderBackground() would blur the
        // panel text drawn afterwards. Use a plain dim layer instead.
        g.fill(0, 0, this.width, this.height, 0xB0101014);
        g.fill(x0, y0, x0 + panelW, y0 + panelH, 0xE6101014);
        g.fill(x0, y0, x0 + panelW, y0 + 1, 0xFF4A9EFF);

        ModSupport m = mod();
        RecipeKind k = kind();
        if (m == null || k == null) {
            g.drawString(this.font, status, x0 + 8, y0 + 10, 0xFFFF5555, false);
            return;
        }

        g.drawString(this.font, Lang.str("kubejs_editor.title", "KubeJS Visual Editor"), x0 + 6, y0 + 6, 0xFFFFFFFF, false);
        g.drawString(this.font, m.name() + " / " + k.name(), x0 + 6, y0 + 18, 0xFF9E9E9E, false);
        g.drawString(this.font, Lang.str("kubejs_editor.col.mod", "mod"), x0 + 6, y0 + 26, 0xFF666666, false);
        g.drawString(this.font, Lang.str("kubejs_editor.col.recipe", "recipe"), x0 + 132, y0 + 26, 0xFF666666, false);

        super.render(g, mouseX, mouseY, partialTick);

        if (gridX > 0) {
            for (int i = 0; i < 9; i++) {
                int gx = gridX + (i % 3) * (CELL + CELL_GAP);
                int gy = gridY + (i / 3) * (CELL + CELL_GAP);
                boolean hover = mouseX >= gx && mouseX < gx + CELL && mouseY >= gy && mouseY < gy + CELL;
                g.fill(gx, gy, gx + CELL, gy + CELL, hover ? 0xFF3B3B48 : 0xFF24242C);
                g.fill(gx, gy, gx + CELL, gy + 1, hover ? 0xFF7A7AFF : 0xFF4A4A58);
                ItemStack stack = stackOf(gridValue(i));
                if (!stack.isEmpty()) {
                    g.renderItem(stack, gx + 2, gy + 2);
                } else {
                    g.drawString(this.font, "+", gx + 8, gy + 6, 0xFF555566, false);
                }
            }
            int ax = gridX + 3 * (CELL + CELL_GAP) + 8;
            int ay = gridY + CELL + CELL_GAP + 9;
            g.drawString(this.font, ">", ax, ay, 0xFF888888, false);

            boolean hoverOut = mouseX >= outX && mouseX < outX + 26 && mouseY >= outY && mouseY < outY + 26;
            g.fill(outX, outY, outX + 26, outY + 26, hoverOut ? 0xFF3B3B48 : 0xFF24242C);
            g.fill(outX, outY, outX + 26, outY + 1, hoverOut ? 0xFF7A7AFF : 0xFF4A4A58);
            ItemStack outStack = stackOf(values.getOrDefault("out", ""));
            if (!outStack.isEmpty()) {
                g.renderItem(outStack, outX + 5, outY + 5);
            } else {
                g.drawString(this.font, "+", outX + 10, outY + 9, 0xFF555566, false);
            }
            g.drawString(this.font, "数量", outX - 2, outY + 22, 0xFF999999, false);
        }

        int formX = x0 + 258;
        boolean gridKind = "shaped".equals(k.id()) || "shapeless".equals(k.id());
        int rowY = y0 + 36;
        for (RecipeField f : k.fields()) {
            if (gridKind && (isGridField(f.key()) || "out".equals(f.key())
                    || "count".equals(f.key()) || f.type() == FieldType.LIST)) {
                continue;
            }
            g.drawString(this.font, Lang.field(f.label()), formX, rowY + 4, 0xFFFFD479, false);
            rowY += ROW;
        }

        int pvY = (formBottom > 0 ? formBottom : y0 + 36 + k.fields().size() * ROW) + 10;
        g.drawString(this.font, Lang.str("kubejs_editor.queue.label", "队列") + " " + queue.size() + " + " + Lang.str("kubejs_editor.queue.current", "当前"), formX, pvY - 9, 0xFF666666, false);

        List<RecipeEntry> preview = new ArrayList<>(queue);
        preview.add(RecipeEntry.recipe(k, values));
        int ly = pvY;
        int maxY = y0 + panelH - 52;
        outer:
        for (RecipeEntry e : preview) {
            String line = e.remove() ? ScriptGen.buildRemove(e.kind(), e.values()) : ScriptGen.buildBody(e.kind(), e.values());
            for (FormattedCharSequence cs : this.font.split(Component.literal(line), panelW - 300)) {
                if (ly > maxY) {
                    break outer;
                }
                g.drawString(this.font, cs, formX, ly, e.remove() ? 0xFFFF8A8A : 0xFF8BE28B, false);
                ly += 9;
            }
        }

        if (!status.isEmpty()) {
            g.drawString(this.font, status, x0 + 6, y0 + panelH - 12, 0xFF9E9E9E, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ModSupport m = mod();
        if (m != null && m.kinds().size() > VISIBLE_KINDS) {
            kindScroll = Math.max(0, Math.min(m.kinds().size() - VISIBLE_KINDS, kindScroll + (scrollY < 0 ? 1 : -1)));
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }


    /**
     * 1.21.1: the default implementation asks GuiGraphics to blur everything that
     * was already drawn in the current frame, which is why our list text looked
     * washed out while EditBoxes (drawn later by super.render) stayed sharp.
     * Keep it empty and paint our own background with fill().
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package dev.dsh.kjseditor.client;

import dev.dsh.kjseditor.data.ModSupport;
import dev.dsh.kjseditor.data.RecipeKind;
import dev.dsh.kjseditor.data.RecipeLoader;
import dev.dsh.kjseditor.gen.RecipeEntry;
import dev.dsh.kjseditor.gen.ScriptGen;
import dev.dsh.kjseditor.gen.ScriptImporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Overview of everything the editor wrote, with per-row delete.
 * Deleting rewrites the file in place; empty files are removed.
 */
public class ChangesScreen extends Screen {
    private static final int ROW = 13;

    private record Row(String text, Path file, RecipeEntry entry, String itemId, boolean deletable, int[] range) {
    }

    private final Screen parent;
    private final List<Row> all = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private net.minecraft.client.gui.components.EditBox search;
    private String query = "";
    private String status = "";
    private int scroll;
    private int x0;
    private int y0;
    private int panelW;
    private int panelH;

    public ChangesScreen(Screen parent) {
        super(Lang.of("kubejs_editor.changes.title", "已更改的 KubeJS"));
        this.parent = parent;
    }

    private static Map<String, RecipeKind> index() {
        List<RecipeKind> kinds = new ArrayList<>();
        for (ModSupport m : RecipeLoader.mods()) {
            kinds.addAll(m.kinds());
        }
        return ScriptImporter.index(kinds);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        panelW = Math.min(this.width - 20, 560);
        panelH = Math.min(this.height - 20, 380);
        x0 = (this.width - panelW) / 2;
        y0 = (this.height - panelH) / 2;
        if (all.isEmpty()) {
            build();
        }
        applyFilter();
        search = new net.minecraft.client.gui.components.EditBox(this.font, x0 + 8, y0 + 30, panelW - 90, 16,
                Component.literal("search"));
        search.setHint(Lang.of("kubejs_editor.changes.search", "搜索配方 / 物品 / 文件..."));
        search.setResponder(q -> {
            query = q == null ? "" : q.toLowerCase();
            applyFilter();
            scroll = 0;
        });
        if (!query.isEmpty()) {
            search.setValue(query);
        }
        this.addRenderableWidget(search);
        this.addRenderableWidget(Button.builder(Lang.of("kubejs_editor.button.back", "返回"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(x0 + panelW - 70, y0 + panelH - 24, 62, 18).build());
    }

    private void build() {
        Path game = FMLPaths.GAMEDIR.get();
        Map<String, RecipeKind> index = index();

        Path itemsFile = game.resolve("kubejs/startup_scripts/editor_items.js");
        if (Files.exists(itemsFile)) {
            try {
                Matcher m = Pattern.compile("event\\.create\\('([^']+)'\\)").matcher(Files.readString(itemsFile));
                while (m.find()) {
                    all.add(new Row("§e" + m.group(1), itemsFile, null, m.group(1), true, null));
                }
            } catch (Exception ignored) {
            }
        }

        Path removalsFile = game.resolve("kubejs/server_scripts/visual/removals.js");
        if (Files.exists(removalsFile)) {
            List<RecipeEntry> removals = new ArrayList<>();
            try {
                ScriptImporter.importText(Files.readString(removalsFile), index, removals::add);
            } catch (Exception ignored) {
            }
            for (RecipeEntry e : removals) {
                String input = e.values().getOrDefault("in", "");
                String label = "§c" + Lang.kind(e.kind().modId(), e.kind().id(), e.kind().name())
                        + (input.isBlank() ? "" : "  §7<- " + itemLabel(input, null));
                all.add(new Row(label, removalsFile, e, null, true, null));
            }
        }

        Path scripts = game.resolve("kubejs/server_scripts");
        Path visual = scripts.resolve("visual");
        if (Files.isDirectory(scripts)) {
            try (var stream = Files.walk(scripts)) {
                for (Path p : stream.filter(f -> f.toString().endsWith(".js")).sorted().toList()) {
                    if ("removals.js".equals(p.getFileName().toString())) {
                        continue;
                    }
                    List<RecipeEntry> list = new ArrayList<>();
                    try {
                        ScriptImporter.importText(Files.readString(p), index, list::add);
                    } catch (Exception ignored) {
                    }
                    // only files this mod wrote can be rewritten safely
                    boolean owned = p.normalize().startsWith(visual.normalize());
                    String prefix = owned ? "§a" : "§9";
                    String name = scripts.relativize(p).toString().replace('\\', '/');
                    for (RecipeEntry e : list) {
                        all.add(new Row(prefix + name + "  §r" + describe(e), p, e, null, true, null));
                    }
                    // surface calls we cannot model, so addon-only content is still visible
                    try {
                        String text = Files.readString(p);
                        var parsed = ScriptImporter.parseLocated(text, index);
                        for (int[] range : ScriptImporter.allCallRanges(text)) {
                            boolean known = parsed.stream().anyMatch(l -> l.start() == range[0]);
                            if (known) {
                                continue;
                            }
                            String snippet = text.substring(range[0], Math.min(range[1] + 1, text.length()));
                            all.add(new Row("§8" + name + "  " + describeUnknown(snippet), p, null, null, true,
                                    new int[]{range[0], range[1]}));
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (rows.isEmpty()) {
            all.add(new Row("§7" + Lang.str("kubejs_editor.changes.none", "无"), null, null, null, false, null));
        }
    }

    private static String itemLabel(String id, String count) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        String name = id;
        try {
            ResourceLocation loc = ResourceLocation.tryParse(id.trim());
            Item item = loc == null ? null : BuiltInRegistries.ITEM.get(loc);
            if (item != null) {
                name = new ItemStack(item).getHoverName().getString();
            }
        } catch (Exception ignored) {
        }
        if (count != null && !count.isBlank() && !"1".equals(count.trim())) {
            name = name + " x" + count.trim();
        }
        return name;
    }

    /** "粉碎  钻石 x3  <- 煤炭 x2" instead of raw script text. */
    /** Best-effort readable text for calls we cannot model. */
    private static String describeUnknown(String snippet) {
        StringBuilder sb = new StringBuilder();
        Matcher t = Pattern.compile("type:\\s*'([^']+)'").matcher(snippet);
        if (t.find()) {
            sb.append(t.group(1)).append("  ");
        }
        Matcher m = Pattern.compile("'([a-z0-9_]+:[a-z0-9_/]+)'").matcher(snippet);
        List<String> parts = new ArrayList<>();
        while (m.find() && parts.size() < 4) {
            parts.add(itemLabel(m.group(1), null));
        }
        if (parts.isEmpty()) {
            String plain = snippet.replace("\n", " ").replace("\r", " ").trim();
            return plain.length() > 60 ? plain.substring(0, 60) + "..." : plain;
        }
        return sb + "§7" + String.join(", ", parts);
    }

    private static String describe(RecipeEntry e) {
        RecipeKind k = e.kind();
        String kindName = Lang.kind(k.modId(), k.id(), k.name());
        StringBuilder sb = new StringBuilder();
        sb.append("§e").append(kindName).append("  ");
        sb.append("§f").append(itemLabel(e.values().get("out"), e.values().get("outCount")));

        List<String> ins = new ArrayList<>();
        for (var f : k.fields()) {
            if (f.type() == dev.dsh.kjseditor.data.FieldType.ITEM && f.key().startsWith("in")) {
                String id = e.values().get(f.key());
                if (id != null && !id.isBlank()) {
                    ins.add(itemLabel(id, e.values().get(f.key() + "Count")));
                }
            }
        }
        if ("shaped".equals(k.id())) {
            for (String gk : new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i"}) {
                String id = e.values().get(gk);
                if (id != null && !id.isBlank()) {
                    ins.add(itemLabel(id, null));
                }
            }
        }
        if ("shapeless".equals(k.id())) {
            for (var f : k.fields()) {
                if (f.type() == dev.dsh.kjseditor.data.FieldType.LIST) {
                    for (String id : ScriptGen.list(k, f)) {
                        if (id != null && !id.isBlank()) {
                            ins.add(itemLabel(id, null));
                        }
                    }
                }
            }
        }
        if (!ins.isEmpty()) {
            String joined = String.join("§7, ", ins);
            sb.append("  §7<- ").append(joined);
        }
        return sb.toString();
    }

    private void applyFilter() {
        rows.clear();
        if (query == null || query.isEmpty()) {
            rows.addAll(all);
        } else {
            for (Row r : all) {
                if (r.text().toLowerCase().contains(query)) {
                    rows.add(r);
                }
            }
        }
    }

    private void delete(Row row) {
        if (row.file() == null || !row.deletable()) {
            return;
        }
        try {
            if (row.itemId() != null) {
                List<String> lines = new ArrayList<>(Files.readAllLines(row.file()));
                int start = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains("event.create('" + row.itemId() + "')")) {
                        start = i;
                        break;
                    }
                }
                if (start >= 0) {
                    int end = start + 1;
                    while (end < lines.size()
                            && !lines.get(end).contains("event.create(")
                            && !lines.get(end).trim().startsWith("})")) {
                        end++;
                    }
                    lines.subList(start, end).clear();
                    Files.writeString(row.file(), String.join("\n", lines) + "\n");
                }
            } else if (row.range() != null) {
                String text = Files.readString(row.file());
                int from = row.range()[0];
                int to = row.range()[1] + 1;
                while (to < text.length() && ",;\r\n ".indexOf(text.charAt(to)) >= 0) {
                    to++;
                }
                while (from > 0 && " \t".indexOf(text.charAt(from - 1)) >= 0) {
                    from--;
                }
                if (from > 0 && text.charAt(from - 1) == '\n') {
                    from--;
                }
                Files.writeString(row.file(), text.substring(0, from) + text.substring(to));
            } else if (row.entry() != null) {
                // text-level delete: keeps comments and layout of hand-written scripts
                String text = Files.readString(row.file());
                String body = ScriptGen.buildBody(row.entry().kind(), row.entry().values());
                int from = -1;
                int to = -1;
                for (var located : ScriptImporter.parseLocated(text, index())) {
                    if (body.equals(ScriptGen.buildBody(located.entry().kind(), located.entry().values()))) {
                        from = located.start();
                        to = located.end() + 1;
                        break;
                    }
                }
                if (from < 0) {
                    status = Lang.str("kubejs_editor.changes.not_found", "没找到该条，可能文件已被改动");
                    return;
                }
                while (to < text.length() && ",;\r\n ".indexOf(text.charAt(to)) >= 0) {
                    to++;
                }
                while (from > 0 && " \t".indexOf(text.charAt(from - 1)) >= 0) {
                    from--;
                }
                if (from > 0 && text.charAt(from - 1) == '\n') {
                    from--;
                }
                String out = text.substring(0, from) + text.substring(to);
                if (row.file().toString().contains("visual") && !out.contains("event.")) {
                    Files.deleteIfExists(row.file());
                } else {
                    Files.writeString(row.file(), out);
                }
            }
            status = Lang.str("kubejs_editor.changes.deleted", "已删除，可 /reload 生效");
            all.clear();
            rows.clear();
            build();
            applyFilter();
        } catch (Exception e) {
            status = Lang.str("kubejs_editor.status.save_failed", "保存失败：%s", e.getMessage());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0101014);
        g.fill(x0, y0, x0 + panelW, y0 + panelH, 0xE6101014);
        g.fill(x0, y0, x0 + panelW, y0 + 1, 0xFF4A9EFF);
        g.drawString(this.font, Lang.str("kubejs_editor.changes.title", "已更改的 KubeJS"),
                x0 + 8, y0 + 8, 0xFFFFFFFF, false);
        g.drawString(this.font, Lang.str("kubejs_editor.changes.hint", "点右侧 × 删除该条"),
                x0 + 8, y0 + 20, 0xFF888888, false);

        int rowsVisible = (panelH - 64) / ROW;
        int start = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - rowsVisible)));
        for (int i = 0; i < rowsVisible; i++) {
            int idx = start + i;
            if (idx >= rows.size()) {
                break;
            }
            int y = y0 + 50 + i * ROW;
            Row row = rows.get(idx);
            boolean canDelete = row.deletable();
            boolean hover = canDelete && mouseX >= x0 + 8 && mouseX < x0 + panelW - 8
                    && mouseY >= y && mouseY < y + ROW;
            if (hover) {
                g.fill(x0 + 6, y - 1, x0 + panelW - 6, y + ROW - 1, 0x30FFFFFF);
            }
            String text = this.font.plainSubstrByWidth(row.text(), panelW - 40);
            g.drawString(this.font, text, x0 + 8, y, 0xFFFFFFFF, false);
            if (canDelete) {
                g.drawString(this.font, "§c×", x0 + panelW - 16, y, 0xFFFF5555, false);
            }
        }
        if (status.isEmpty() && rows.size() > rowsVisible) {
            g.drawString(this.font, (start + 1) + "/" + rows.size(), x0 + 8, y0 + panelH - 20, 0xFF888888, false);
        } else if (!status.isEmpty()) {
            g.drawString(this.font, status, x0 + 8, y0 + panelH - 20, 0xFF9E9E9E, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int rowsVisible = (panelH - 50) / ROW;
        if (mouseX >= x0 + panelW - 24 && mouseX < x0 + panelW - 4) {
            int i = (int) ((mouseY - (y0 + 50)) / ROW);
            if (i >= 0 && i < rowsVisible) {
                int idx = scroll + i;
                if (idx < rows.size() && rows.get(idx).deletable()) {
                    delete(rows.get(idx));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rowsVisible = (panelH - 64) / ROW;
        int max = Math.max(0, rows.size() - rowsVisible);
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

package dev.dsh.kjseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Searchable registry item list. */
public class ItemPickerScreen extends Screen {
    private static final int ROW = 18;

    private final Screen parent;
    private final Consumer<String> onPick;
    private EditBox search;
    private List<ResourceLocation> all = List.of();
    private List<ResourceLocation> filtered = List.of();
    private int scroll;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Lang.of("kubejs_editor.picker.title", "选择物品"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        listW = Math.min(this.width - 40, 420);
        listX = (this.width - listW) / 2;
        listY = 60;
        listH = this.height - listY - 30;

        if (all.isEmpty()) {
            List<ResourceLocation> ids = new ArrayList<>(BuiltInRegistries.ITEM.keySet());
            ids.removeIf(id -> BuiltInRegistries.ITEM.get(id) == null);
            ids.sort(Comparator.comparing(ResourceLocation::toString));
            all = List.copyOf(ids);
            filtered = all;
        }

        search = new EditBox(this.font, listX, 36, listW, 18, Component.literal("search"));
        search.setHint(Lang.of("kubejs_editor.picker.search", "搜索物品 ID..."));
        search.setResponder(q -> applyFilter(q));
        this.addRenderableWidget(search);
        search.setFocused(true);
    }

    private void applyFilter(String q) {
        String s = q.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) {
            filtered = all;
        } else {
            List<ResourceLocation> out = new ArrayList<>();
            for (ResourceLocation id : all) {
                if (id.toString().contains(s)) {
                    out.add(id);
                }
            }
            filtered = out;
        }
        scroll = 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Do NOT call renderBackground: on 1.21.1 it requests the menu blur pass,
        // which also blurs everything drawn after it in this stratum. The list text
        // ended up unreadable while the EditBox (drawn by super.render) stayed crisp.
        g.fill(0, 0, this.width, this.height, 0xF0101014);
        g.drawString(this.font, Lang.str("kubejs_editor.picker.title", "选择物品") + "  (" + filtered.size() + ")", listX, 22, 0xFFFFFFFF, false);

        int rows = listH / ROW;
        int start = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - rows)));
        for (int i = 0; i < rows; i++) {
            int idx = start + i;
            if (idx >= filtered.size()) {
                break;
            }
            int y = listY + i * ROW;
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW;
            g.fill(listX, y, listX + listW, y + ROW, hover ? 0x60FFFFFF : (i % 2 == 0 ? 0x30000000 : 0x20000000));
            ResourceLocation id = filtered.get(idx);
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            g.renderItem(stack, listX + 2, y + 1);
            String name = this.font.plainSubstrByWidth(stack.getHoverName().getString(), 150);
            g.drawString(this.font, name, listX + 22, y + 5, hover ? 0xFFFFFFFF : 0xFFEEEEEE, false);
            String idText = this.font.plainSubstrByWidth(id.toString(), listW - 180);
            g.drawString(this.font, idText, listX + 176, y + 5, hover ? 0xFFAAAAAA : 0xFF777777, false);
        }

        if (filtered.size() > rows) {
            g.drawString(this.font, (start + 1) + "-" + Math.min(start + rows, filtered.size()),
                    listX + listW - 60, listY + listH + 4, 0xFF888888, false);
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
            if (idx >= 0 && idx < filtered.size()) {
                onPick.accept(filtered.get(idx).toString());
                this.minecraft.setScreen(parent);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = listH / ROW;
        int max = Math.max(0, filtered.size() - rows);
        scroll = Math.max(0, Math.min(max, scroll + (scrollY < 0 ? 1 : -1)));
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
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

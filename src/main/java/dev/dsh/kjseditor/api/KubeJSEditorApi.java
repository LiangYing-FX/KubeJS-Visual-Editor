package dev.dsh.kjseditor.api;

import dev.dsh.kjseditor.data.ModSupport;
import dev.dsh.kjseditor.data.RecipeField;
import dev.dsh.kjseditor.data.RecipeKind;
import dev.dsh.kjseditor.data.RecipeLoader;

import java.io.InputStream;
import java.util.List;

/**
 * Public extension point.
 *
 * <p>KubeJS usage:
 * <pre>
 * const Api = Java.loadClass('dev.dsh.kjseditor.api.KubeJSEditorApi')
 * Api.registerMod('thermal', 'Thermal', 0xFF8800, [
 *     Api.kind('smelter', '感应炉', "event.recipes.thermal.smelter('{out}', '{in}')", [
 *         Api.field('out', '输出', 'ITEM', 'minecraft:iron_ingot'),
 *         Api.field('in', '输入', 'minecraft:iron_ore')
 *     ])
 * ])
 * </pre>
 */
public final class KubeJSEditorApi {
    private KubeJSEditorApi() {
    }

    public static void registerMod(String id, String name, int color, List<RecipeKind> kinds) {
        RecipeLoader.registerMod(new ModSupport(id, name, color, kinds));
    }

    /** Extra JSON with the same shape as the built-in recipes.json. */
    public static void registerJson(InputStream in) {
        RecipeLoader.registerJson(in);
    }

    /** Re-read built-in + mod jars + config/kubejs_editor. */
    public static void reload() {
        RecipeLoader.reload();
    }

    public static List<ModSupport> mods() {
        return RecipeLoader.mods();
    }

    public static RecipeKind kind(String id, String name, String template, List<RecipeField> fields) {
        return new RecipeKind(id, name, template, fields, "");
    }

    public static RecipeField field(String key, String label, String type, String def) {
        return new RecipeField(key, label, dev.dsh.kjseditor.data.FieldType.valueOf(type), def, List.of(), List.of(), null);
    }
}

package dev.dsh.kjseditor.data;

import java.util.List;

/** One editable parameter of a recipe kind. */
public record RecipeField(
        String key,
        String label,
        FieldType type,
        String def,
        List<String> options,
        List<String> subKinds,
        String itemTemplate) {

    public static RecipeField of(String key, String label, FieldType type, String def) {
        return new RecipeField(key, label, type, def, List.of(), List.of(), null);
    }

    public List<String> optionsOrEmpty() {
        return options == null ? List.of() : options;
    }

    public List<String> subKindsOrEmpty() {
        return subKinds == null ? List.of() : subKinds;
    }

    /** Template for one LIST entry; {v} is replaced by the value. */
    public String itemTemplateOr(String fallback) {
        return itemTemplate == null || itemTemplate.isBlank() ? fallback : itemTemplate;
    }
}

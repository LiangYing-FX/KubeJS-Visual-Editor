package dev.dsh.kjseditor.gen;

import dev.dsh.kjseditor.data.RecipeKind;

import java.util.LinkedHashMap;
import java.util.Map;

/** One queued script line: either a recipe or a removal. */
public record RecipeEntry(RecipeKind kind, Map<String, String> values, boolean remove) {
    public static RecipeEntry recipe(RecipeKind kind, Map<String, String> values) {
        return new RecipeEntry(kind, new LinkedHashMap<>(values), false);
    }

    public static RecipeEntry removal(RecipeKind kind, Map<String, String> values) {
        return new RecipeEntry(kind, new LinkedHashMap<>(values), true);
    }
}

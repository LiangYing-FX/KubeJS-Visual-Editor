package dev.dsh.kjseditor.data;

import java.util.List;

/** A recipe type: template string plus its fields. */
public record RecipeKind(String id, String name, String template, List<RecipeField> fields, String modId) {
    public RecipeKind(String id, String name, String template, List<RecipeField> fields) {
        this(id, name, template, fields, "");
    }

    public RecipeKind withModId(String mod) {
        return new RecipeKind(id, name, template, fields, mod);
    }

    public String fullId() {
        return modId == null || modId.isEmpty() ? id : modId + ":" + id;
    }
}

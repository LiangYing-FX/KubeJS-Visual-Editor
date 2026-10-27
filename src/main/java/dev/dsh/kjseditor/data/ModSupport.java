package dev.dsh.kjseditor.data;

import java.util.List;

/** A supported mod family and its recipe kinds. */
public record ModSupport(String id, String name, int color, List<RecipeKind> kinds) {
}

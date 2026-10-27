package dev.dsh.kjseditor.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Thin wrapper: use the translation when it exists, otherwise the raw text. */
public final class Lang {
    private Lang() {
    }

    public static String str(String key, String fallback) {
        return I18n.exists(key) ? I18n.get(key) : fallback;
    }

    public static String str(String key, String fallback, Object... args) {
        if (I18n.exists(key)) {
            return I18n.get(key, args);
        }
        try {
            return String.format(fallback, args);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static Component of(String key, String fallback) {
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(fallback);
    }

    public static String kind(String modId, String kindId, String fallback) {
        return str("kubejs_editor.kind." + modId + "." + kindId, fallback);
    }

    public static String field(String label) {
        String norm = label.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return str("kubejs_editor.field." + norm, label);
    }
}

package dev.dsh.kjseditor.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.dsh.kjseditor.KubeJSEditorMod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loads recipe definitions from, in order:
 * 1. the built-in recipes.json
 * 2. kubejs_editor/recipes.json inside any other mod jar
 * 3. config/kubejs_editor/*.json
 * 4. runtime registrations through KubeJSEditorApi (e.g. from a KubeJS script)
 */
public final class RecipeLoader {
    public static final String RESOURCE_PATH = "kubejs_editor/recipes.json";

    private static volatile List<ModSupport> mods = List.of();
    private static final List<ModSupport> runtimeMods = new CopyOnWriteArrayList<>();

    private RecipeLoader() {
    }

    private record Root(List<ModSupport> mods) {
    }

    private static final class Merged {
        String id;
        String name;
        int color;
        final List<RecipeKind> kinds = new ArrayList<>();

        Merged(String id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    public static List<ModSupport> mods() {
        if (mods.isEmpty()) {
            load();
        }
        return mods;
    }

    public static synchronized void load() {
        Map<String, Merged> merged = new LinkedHashMap<>();

        try (InputStream in = RecipeLoader.class.getResourceAsStream("/" + RESOURCE_PATH)) {
            mergeJson(merged, in);
        } catch (Exception e) {
            KubeJSEditorMod.LOGGER.error("failed to read built-in recipes.json", e);
        }

        try {
            for (IModInfo info : ModList.get().getMods()) {
                if (KubeJSEditorMod.MODID.equals(info.getModId())) {
                    continue;
                }
                try {
                    Path path = info.getOwningFile().getFile().findResource(RESOURCE_PATH);
                    if (path != null && Files.exists(path)) {
                        try (InputStream in = Files.newInputStream(path)) {
                            mergeJson(merged, in);
                            KubeJSEditorMod.LOGGER.info("loaded extra definitions from {}", info.getModId());
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            KubeJSEditorMod.LOGGER.warn("could not scan mod jars for {}", RESOURCE_PATH);
        }

        Path configDir = FMLPaths.CONFIGDIR.get().resolve("kubejs_editor");
        if (Files.isDirectory(configDir)) {
            try (var stream = Files.list(configDir)) {
                for (Path p : stream.filter(x -> x.toString().endsWith(".json")).sorted().toList()) {
                    try (InputStream in = Files.newInputStream(p)) {
                        mergeJson(merged, in);
                        KubeJSEditorMod.LOGGER.info("loaded {} definitions from {}", "config", p.getFileName());
                    } catch (Exception e) {
                        KubeJSEditorMod.LOGGER.error("bad definition file " + p, e);
                    }
                }
            } catch (Exception e) {
                KubeJSEditorMod.LOGGER.error("could not list " + configDir, e);
            }
        }

        for (ModSupport m : runtimeMods) {
            mergeMod(merged, m);
        }

        List<ModSupport> out = new ArrayList<>();
        for (Merged m : merged.values()) {
            if (!m.kinds.isEmpty()) {
                out.add(new ModSupport(m.id, m.name, m.color, List.copyOf(m.kinds)));
            }
        }
        mods = List.copyOf(out);
        KubeJSEditorMod.LOGGER.info("loaded {} mod sections, {} recipe kinds", mods.size(),
                mods.stream().mapToInt(m -> m.kinds().size()).sum());
    }

    public static void registerMod(ModSupport mod) {
        runtimeMods.add(mod);
        if (!mods.isEmpty()) {
            load();
        }
    }

    public static void registerJson(InputStream in) {
        Map<String, Merged> merged = new LinkedHashMap<>();
        mergeJson(merged, in);
        for (Merged m : merged.values()) {
            registerMod(new ModSupport(m.id, m.name, m.color, List.copyOf(m.kinds)));
        }
    }

    public static void reload() {
        load();
    }

    private static void mergeJson(Map<String, Merged> merged, InputStream in) {
        if (in == null) {
            return;
        }
        Gson gson = new GsonBuilder().create();
        Root root = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Root.class);
        if (root == null || root.mods() == null) {
            return;
        }
        for (ModSupport m : root.mods()) {
            mergeMod(merged, m);
        }
    }

    private static void mergeMod(Map<String, Merged> merged, ModSupport mod) {
        Merged target = merged.computeIfAbsent(mod.id(), id -> new Merged(id, mod.name(), mod.color()));
        if (mod.name() != null && !mod.name().isBlank()) {
            target.name = mod.name();
        }
        if (mod.color() != 0) {
            target.color = mod.color();
        }
        for (RecipeKind k : mod.kinds()) {
            RecipeKind withMod = k.modId() == null || k.modId().isEmpty() ? k.withModId(mod.id()) : k;
            target.kinds.removeIf(existing -> existing.id().equals(withMod.id()));
            target.kinds.add(withMod);
        }
    }
}

package dev.dsh.kjseditor.gen;

import dev.dsh.kjseditor.data.FieldType;
import dev.dsh.kjseditor.data.RecipeField;
import dev.dsh.kjseditor.data.RecipeKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses existing KubeJS scripts back into recipe entries. */
public final class ScriptImporter {
    private static final Pattern MOD_CALL = Pattern.compile("event\\.recipes\\.([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\s*\\(");
    private static final Pattern VANILLA_CALL = Pattern.compile("event\\.(shaped|shapeless|smelting|blasting|smoking|campfireCooking|stonecutting|smithing)\\s*\\(");
    private static final Pattern REMOVE_CALL = Pattern.compile("event\\.remove\\(\\{\\s*type:\\s*'([^']+)'([^)]*)\\}\\)");

    private ScriptImporter() {
    }

    public static Map<String, RecipeKind> index(List<RecipeKind> kinds) {
        Map<String, RecipeKind> map = new HashMap<>();
        for (RecipeKind k : kinds) {
            map.put(k.id().toLowerCase(Locale.ROOT), k);
            if (k.modId() != null && !k.modId().isEmpty()) {
                map.put((k.modId() + "." + k.id()).toLowerCase(Locale.ROOT), k);
            }
        }
        return map;
    }

    /** @return number of recipes parsed from this file */
    public static int importText(String text, Map<String, RecipeKind> index, Consumer<RecipeEntry> sink) {
        int count = 0;
        Matcher m = MOD_CALL.matcher(text);
        while (m.find()) {
            String mod = m.group(1);
            String kind = m.group(2);
            int open = m.end() - 1;
            int close = matchParen(text, open);
            if (close < 0) {
                continue;
            }
            RecipeKind rk = index.get((mod + "." + kind).toLowerCase(Locale.ROOT));
            if (rk == null) {
                rk = index.get(kind.toLowerCase(Locale.ROOT));
            }
            if (rk == null) {
                continue;
            }
            RecipeEntry e = parseArgs(text.substring(open + 1, close), rk);
            if (e != null) {
                sink.accept(e);
                count++;
            }
        }
        Matcher rm = REMOVE_CALL.matcher(text);
        while (rm.find()) {
            String type = rm.group(1);
            RecipeKind rk = index.get(type.toLowerCase(Locale.ROOT));
            if (rk == null) {
                continue;
            }
            String input = "";
            Matcher im = Pattern.compile("input:\\s*'([^']*)'").matcher(rm.group(2));
            if (im.find()) {
                input = im.group(1);
            }
            Map<String, String> values = new HashMap<>();
            values.put("in", input);
            sink.accept(RecipeEntry.removal(rk, values));
            count++;
        }

        Matcher v = VANILLA_CALL.matcher(text);
        while (v.find()) {
            String kind = v.group(1);
            int open = v.end() - 1;
            int close = matchParen(text, open);
            if (close < 0) {
                continue;
            }
            RecipeKind rk = index.get(kind.toLowerCase(Locale.ROOT));
            if (rk == null) {
                continue;
            }
            RecipeEntry e = parseArgs(text.substring(open + 1, close), rk);
            if (e != null) {
                sink.accept(e);
                count++;
            }
        }
        return count;
    }

    /** Character ranges of every "event.xxx(" call, parsed or not. */
    public static List<int[]> allCallRanges(String text) {
        List<int[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("event\\.[a-zA-Z0-9_.]+\\s*\\(").matcher(text);
        while (m.find()) {
            int close = matchParen(text, m.end() - 1);
            if (close > 0) {
                out.add(new int[]{m.start(), close});
            }
        }
        return out;
    }

    /** A parsed recipe together with its character range in the source file. */
    public record Located(int start, int end, RecipeEntry entry) {
    }

    public static List<Located> parseLocated(String text, Map<String, RecipeKind> index) {
        List<Located> out = new ArrayList<>();
        Matcher m = MOD_CALL.matcher(text);
        while (m.find()) {
            int open = m.end() - 1;
            int close = matchParen(text, open);
            if (close < 0) {
                continue;
            }
            String mod = m.group(1);
            String kind = m.group(2);
            RecipeKind rk = index.get((mod + "." + kind).toLowerCase(Locale.ROOT));
            if (rk == null) {
                rk = index.get(kind.toLowerCase(Locale.ROOT));
            }
            if (rk == null) {
                continue;
            }
            RecipeEntry e = parseArgs(text.substring(open + 1, close), rk);
            if (e != null) {
                out.add(new Located(m.start(), close, e));
            }
        }
        Matcher rm = REMOVE_CALL.matcher(text);
        while (rm.find()) {
            RecipeKind rk = index.get(rm.group(1).toLowerCase(Locale.ROOT));
            if (rk == null) {
                continue;
            }
            String input = "";
            Matcher im = Pattern.compile("input:\\s*'([^']*)'").matcher(rm.group(2));
            if (im.find()) {
                input = im.group(1);
            }
            Map<String, String> values = new HashMap<>();
            values.put("in", input);
            out.add(new Located(rm.start(), rm.end() - 1, RecipeEntry.removal(rk, values)));
        }
        Matcher v = VANILLA_CALL.matcher(text);
        while (v.find()) {
            int open = v.end() - 1;
            int close = matchParen(text, open);
            if (close < 0) {
                continue;
            }
            RecipeKind rk = index.get(v.group(1).toLowerCase(Locale.ROOT));
            if (rk == null) {
                continue;
            }
            RecipeEntry e = parseArgs(text.substring(open + 1, close), rk);
            if (e != null) {
                out.add(new Located(v.start(), close, e));
            }
        }
        return out;
    }

    private static int matchParen(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                cur.append(c);
            } else if (c == '[' || c == '{' || c == '(') {
                depth++;
                cur.append(c);
            } else if (c == ']' || c == '}' || c == ')') {
                depth--;
                cur.append(c);
            } else if (c == ',' && depth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.toString().trim().isEmpty()) {
            out.add(cur.toString().trim());
        }
        return out;
    }

    private static RecipeEntry parseArgs(String argText, RecipeKind kind) {
        List<String> args = splitArgs(argText);
        if ("shaped".equals(kind.id())) {
            return parseShaped(args, kind);
        }
        if ("shapeless".equals(kind.id())) {
            return parseShapeless(args, kind);
        }
        if (args.isEmpty()) {
            return null;
        }
        // Pattern rows live inside array arguments, item ids are scalar/object values.
        // Keeping them apart stops `['ABA','CDC','EFE']` from being consumed as items.
        List<String> arrayStrings = new ArrayList<>();
        List<String> strings = new ArrayList<>();
        for (String a : args) {
            Matcher sm = Pattern.compile("'([^']*)'").matcher(a);
            boolean isArray = a.trim().startsWith("[");
            while (sm.find()) {
                (isArray ? arrayStrings : strings).add(sm.group(1));
            }
        }

        Map<String, String> values = new HashMap<>();
        int si = 0;
        int ai = 0;
        List<String> nums = new ArrayList<>();
        for (String a : args) {
            Matcher nm = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b").matcher(a.replaceAll("'[^']*'", ""));
            while (nm.find()) {
                nums.add(nm.group(1));
            }
        }
        int ni = 0;
        for (RecipeField f : kind.fields()) {
            if (f.type() == FieldType.NUMBER) {
                values.put(f.key(), ni < nums.size() ? nums.get(ni++) : f.def());
            } else if (f.type() == FieldType.TEXT || f.type() == FieldType.ENUM) {
                values.put(f.key(), ai < arrayStrings.size() ? arrayStrings.get(ai++) : f.def());
            } else if (f.type() == FieldType.NESTED || f.type() == FieldType.LIST) {
                continue;
            } else if (si < strings.size()) {
                values.put(f.key(), strings.get(si++));
            } else {
                values.put(f.key(), f.def());
            }
        }
        return RecipeEntry.recipe(kind, values);
    }

    private static String firstString(String s) {
        Matcher m = Pattern.compile("'([^']*)'").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static String firstNumber(String s) {
        Matcher m = Pattern.compile(",\\s*(\\d+)\\s*\\)").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> extractStrings(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("'([^']*)'").matcher(s);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** shaped: Item.of(out[, count]), [pattern rows], { A: id, ... } -> 9 cells */
    private static RecipeEntry parseShaped(List<String> args, RecipeKind kind) {
        Map<String, String> values = new HashMap<>();
        String out = args.isEmpty() ? "" : firstString(args.get(0));
        String count = args.isEmpty() ? null : firstNumber(args.get(0));
        String patternText = args.size() > 1 ? args.get(1) : "";
        String keyText = args.size() > 2 ? args.get(2) : "";

        Map<String, String> keys = new HashMap<>();
        Matcher km = Pattern.compile("([A-Za-z])\\s*:\\s*'([^']+)'").matcher(keyText);
        while (km.find()) {
            keys.put(km.group(1).toUpperCase(Locale.ROOT), km.group(2));
        }

        List<String> rows = extractStrings(patternText);
        for (int r = 0; r < 3; r++) {
            String row = r < rows.size() ? rows.get(r) : "   ";
            for (int c = 0; c < 3; c++) {
                char ch = c < row.length() ? row.charAt(c) : ' ';
                String id = ch == ' ' ? "" : keys.getOrDefault(String.valueOf(Character.toUpperCase(ch)), "");
                values.put(ScriptGen.GRID_KEYS[r * 3 + c], id);
            }
        }
        values.put("out", out);
        values.put("count", count == null ? "1" : count);
        return RecipeEntry.recipe(kind, values);
    }

    private static RecipeEntry parseShapeless(List<String> args, RecipeKind kind) {
        Map<String, String> values = new HashMap<>();
        String out = args.isEmpty() ? "" : firstString(args.get(0));
        String count = args.isEmpty() ? null : firstNumber(args.get(0));
        values.put("out", out);
        values.put("count", count == null ? "1" : count);

        List<String> items = args.size() > 1 ? extractStrings(args.get(1)) : List.of();
        for (RecipeField f : kind.fields()) {
            if (f.type() == FieldType.LIST) {
                List<String> target = ScriptGen.list(kind, f);
                target.clear();
                target.addAll(items);
                break;
            }
        }
        return RecipeEntry.recipe(kind, values);
    }
}

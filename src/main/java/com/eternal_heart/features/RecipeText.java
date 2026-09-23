package com.eternal_heart.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义配方的**简化语法** —— 把一行短文本翻译成原版配方 JSON。
 *
 * <p>目的：不必手写整段 JSON，常用配方一行即可。</p>
 *
 * <pre>
 * shapeless: diamond_block = 9x diamond                      无序合成
 * shapeless: diamond x9 = diamond_block                      产出带数量
 * shaped:    stick x4 = planks / planks                      有序合成（/ 分行，_ 空位）
 * shaped:    diamond_pickaxe = diamond diamond diamond / _ stick _ / _ stick _
 * smelting:  glass &lt;- sand                                   熔炼（blasting / smoking 同理）
 * { ...原版配方 JSON... }                                     直接用原 JSON（向后兼容）
 * </pre>
 *
 * <p>细节：物品 ID 可省略 {@code minecraft:} 前缀；{@code #} 前缀表示标签
 * （如 {@code #minecraft:planks}）；有序合成的 {@code _} 或 {@code -} 表示空位。</p>
 *
 * <p>解析失败返回 {@code null}，由调用方记录日志并跳过 —— 一条写错不影响其它配方。</p>
 */
public final class RecipeText {

    private static final Pattern LEADING_COUNT = Pattern.compile("^(\\d+)x\\s*(.+)$");
    private static final Pattern TRAILING_COUNT = Pattern.compile("^(.+?)\\s*x(\\d+)$");

    private RecipeText() {
    }

    /** 解析一行配置：简化语法或原版 JSON。无法识别返回 null。 */
    public static JsonObject parse(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;

        // 原版 JSON 原样使用（保持向后兼容：老配置、复杂配方仍可手写）
        if (text.startsWith("{")) return JsonParser.parseString(text).getAsJsonObject();

        int colon = text.indexOf(':');
        if (colon <= 0) return null;
        String type = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String body = text.substring(colon + 1).trim();

        return switch (type) {
            case "shapeless" -> shapeless(body);
            case "shaped" -> shaped(body);
            case "smelting" -> cooking(body, "minecraft:smelting");
            case "blasting" -> cooking(body, "minecraft:blasting");
            case "smoking" -> cooking(body, "minecraft:smoking");
            default -> null;
        };
    }

    // ============================================================
    //  各配方类型
    // ============================================================

    /** {@code <产出>[ xN] = <材料>[ xN] [+ <材料>[ xN]]...} */
    private static JsonObject shapeless(String body) {
        String[] sides = splitOnce(body, "=");
        if (sides == null) return null;
        ItemAmount output = itemAmount(sides[0]);
        if (output == null) return null;

        JsonArray ingredients = new JsonArray();
        for (String part : sides[1].split("\\+")) {
            ItemAmount material = itemAmount(part);
            if (material == null) return null;
            for (int i = 0; i < material.count(); i++) ingredients.add(ingredient(material.id()));
        }
        if (ingredients.isEmpty()) return null;

        JsonObject json = base("minecraft:crafting_shapeless");
        json.add("ingredients", ingredients);
        json.add("result", result(output));
        return json;
    }

    /** {@code <产出>[ xN] = <行> / <行> / <行>}（/ 分行，空格分格，_ 为空位） */
    private static JsonObject shaped(String body) {
        String[] sides = splitOnce(body, "=");
        if (sides == null) return null;
        ItemAmount output = itemAmount(sides[0]);
        if (output == null) return null;

        List<String> rows = new ArrayList<>();
        for (String row : sides[1].split("/")) rows.add(row.trim());
        if (rows.isEmpty() || rows.size() > 3) return null;

        int width = 0;
        for (String row : rows) width = Math.max(width, cells(row).length);
        if (width == 0 || width > 3) return null;

        // 按首次出现顺序分配 a、b、c… 作为 pattern 符号
        Map<String, Character> symbolByToken = new LinkedHashMap<>();
        JsonObject key = new JsonObject();
        JsonArray pattern = new JsonArray();
        char next = 'a';

        for (String row : rows) {
            String[] cells = cells(row);
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < width; column++) {
                String cell = column < cells.length ? cells[column] : "_";
                if (isBlank(cell)) {
                    line.append(' ');
                    continue;
                }
                Character symbol = symbolByToken.get(cell);
                if (symbol == null) {
                    if (next > 'z') return null; // 材料种类超过 26 种
                    symbol = next++;
                    symbolByToken.put(cell, symbol);
                    key.add(String.valueOf(symbol), ingredient(cell));
                }
                line.append(symbol);
            }
            pattern.add(line.toString());
        }

        JsonObject json = base("minecraft:crafting_shaped");
        json.add("pattern", pattern);
        json.add("key", key);
        json.add("result", result(output));
        return json;
    }

    /** {@code <产出> <- <材料>}（熔炼类的产出数量固定为 1，与 1.20.1 配方格式一致） */
    private static JsonObject cooking(String body, String type) {
        String[] sides = splitOnce(body, "<-");
        if (sides == null) return null;
        ItemAmount output = itemAmount(sides[0]);
        String material = sides[1].trim();
        if (output == null || material.isEmpty() || material.contains(" ")) return null;

        JsonObject json = base(type);
        json.add("ingredient", ingredient(material));
        json.addProperty("result", normalize(output.id()));
        json.addProperty("experience", 0.1);
        json.addProperty("cookingtime", 200);
        return json;
    }

    // ============================================================
    //  工具
    // ============================================================

    private static JsonObject base(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    private static JsonObject ingredient(String token) {
        JsonObject json = new JsonObject();
        String id = normalize(token.startsWith("#") ? token.substring(1) : token);
        if (token.startsWith("#")) json.addProperty("tag", id);
        else json.addProperty("item", id);
        return json;
    }

    /** 产出对象：1.20.1 的合成配方用 {@code {"item":…,"count":N}}。 */
    private static JsonObject result(ItemAmount output) {
        JsonObject json = new JsonObject();
        json.addProperty("item", normalize(output.id()));
        if (output.count() != 1) json.addProperty("count", output.count());
        return json;
    }

    /** 省略命名空间时补 {@code minecraft:}。 */
    private static String normalize(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** 材料单元：{@code 9x diamond} 与 {@code diamond x9} 两种写法都支持。 */
    private static ItemAmount itemAmount(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return null;

        Matcher leading = LEADING_COUNT.matcher(trimmed);
        if (leading.matches()) {
            return new ItemAmount(leading.group(2).trim(), positive(leading.group(1)));
        }
        Matcher trailing = TRAILING_COUNT.matcher(trimmed);
        if (trailing.matches()) {
            return new ItemAmount(trailing.group(1).trim(), positive(trailing.group(2)));
        }
        return new ItemAmount(trimmed, 1);
    }

    private static int positive(String digits) {
        int value = Integer.parseInt(digits);
        return Math.max(1, value);
    }

    private static String[] cells(String row) {
        return row.isEmpty() ? new String[0] : row.trim().split("\\s+");
    }

    private static boolean isBlank(String cell) {
        return cell.isEmpty() || cell.equals("_") || cell.equals("-");
    }

    /** 以首个出现的分隔符切分为两段；找不到返回 null。 */
    private static String[] splitOnce(String text, String separator) {
        int index = text.indexOf(separator);
        if (index < 0) return null;
        return new String[]{text.substring(0, index).trim(), text.substring(index + separator.length()).trim()};
    }

    private record ItemAmount(String id, int count) {
    }
}

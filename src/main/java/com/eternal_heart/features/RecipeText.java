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
 * ritual:    result=…; activation=…; ingredients=…; …         诡厄巫法暗黑祭坛仪式（Goety）
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

    /** Goety 非合成类仪式（召唤 / 转换等）使用的占位产物。 */
    private static final String DUMMY_RESULT = "goety:jei_dummy/none";

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
            case "ritual" -> ritual(body);
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

    /**
     * 诡厄巫法（Goety）暗黑祭坛仪式：{@code ritual: 键=值; 键=值; …} → {@code goety:ritual} 配方 JSON。
     *
     * <p>字段与 Goety 的 {@code RitualRecipe} 序列化器一一对应：</p>
     * <pre>
     * ritual: result=&lt;产出&gt;[ xN]; activation=&lt;激活物&gt;; ingredients=&lt;材料&gt;[ + &lt;材料&gt;…]
     *         [; type=&lt;goety:craft|summon|summon_tamed|convert|enchant|teleport|locate|…&gt;]
     *         [; craft=&lt;流派, 如 forge/deep/magic&gt;] [; soul=&lt;灵魂&gt;] [; time=&lt;秒&gt;]
     *         [; summon=&lt;实体ID&gt;] [; summon_life=&lt;tick&gt;]
     *         [; convert=&lt;实体tag&gt;] [; convert_name=&lt;显示名&gt;] [; convert_into=&lt;实体ID&gt;]
     *         [; enchant=&lt;附魔ID&gt;] [; xp=&lt;经验等级&gt;]
     *         [; sacrifice=&lt;实体tag&gt;] [; sacrifice_name=&lt;显示名&gt;]
     *         [; locate=&lt;结构tag&gt;] [; locate_name=&lt;显示名&gt;]
     *         [; research=&lt;研究&gt;] [; group=&lt;分组&gt;]
     * </pre>
     */
    private static JsonObject ritual(String body) {
        if (body.isEmpty()) return null;
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : body.split(";")) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;
            String[] pair = splitOnce(piece, "=");
            if (pair == null) return null;
            fields.put(pair[0].trim().toLowerCase(Locale.ROOT), pair[1].trim());
        }
        if (fields.isEmpty()) return null;

        // 仪式工厂类型：允许缩写（craft → goety:craft）
        String rawType = fields.getOrDefault("type", "craft");
        String type = rawType.isBlank() ? null : (rawType.contains(":") ? rawType.trim() : "goety:" + rawType.trim());
        if (type == null) return null;

        // 激活物（Ingredient，必填）
        String rawActivation = fields.get("activation");
        ItemAmount activation = rawActivation == null ? null : itemAmount(rawActivation);
        if (activation == null) return null;

        // 基座材料（Ingredient 数组，必填；xN 展开为多条）
        JsonArray ingredients = new JsonArray();
        String rawIngredients = fields.get("ingredients");
        if (rawIngredients != null) {
            for (String token : rawIngredients.split("\\+")) {
                ItemAmount amount = itemAmount(token);
                if (amount == null) return null;
                for (int i = 0; i < amount.count(); i++) ingredients.add(ingredient(amount.id()));
            }
        }
        if (ingredients.isEmpty()) return null;

        JsonObject result = ritualResult(fields.get("result"), type);
        if (result == null) return null;

        JsonObject json = base("goety:ritual");
        json.addProperty("ritual_type", type);
        json.add("activation_item", ingredient(activation.id()));
        json.add("ingredients", ingredients);
        json.add("result", result);

        String craft = fields.get("craft");
        if (craft != null && !craft.isBlank()) json.addProperty("craftType", craft.trim());
        json.addProperty("soulCost", integerOr(fields.get("soul"), 0));
        json.addProperty("duration", integerOr(fields.get("time"), 30));

        // 类型相关字段（缺省即不写入，保持与 Goety 自身 JSON 一致）
        addIfPresent(json, "entity_to_summon", fields.get("summon"));
        addIfPresent(json, "entity_to_convert_into", fields.get("convert_into"));
        addIfPresent(json, "enchantment", fields.get("enchant"));
        addIfPresent(json, "research", fields.get("research"));
        addIfPresent(json, "group", fields.get("group"));
        if (fields.containsKey("summon_life")) json.addProperty("summonLife", integerOr(fields.get("summon_life"), -1));
        if (fields.containsKey("xp")) json.addProperty("xpLevelCost", integerOr(fields.get("xp"), 0));
        // Goety 的 tag 对象内部 display_name 为必填，缺省时用标签名兜底，避免解析崩溃
        if (fields.containsKey("convert")) {
            json.add("entity_to_convert", tagObject(fields.get("convert"), fields.get("convert_name")));
        }
        if (fields.containsKey("sacrifice")) {
            json.add("entity_to_sacrifice", tagObject(fields.get("sacrifice"), fields.get("sacrifice_name")));
        }
        if (fields.containsKey("locate")) {
            json.add("structure_to_locate", tagObject(fields.get("locate"), fields.get("locate_name")));
        }
        return json;
    }

    /** Goety 的 tag 对象：{@code {"tag":…, "display_name":…}}，其中 display_name 为必填。 */
    private static JsonObject tagObject(String rawTag, String rawName) {
        String tag = tagId(rawTag);
        JsonObject json = new JsonObject();
        json.addProperty("tag", tag);
        json.addProperty("display_name", rawName == null || rawName.isBlank() ? tag : rawName.trim());
        return json;
    }

    /** 标签 ID：去掉可选的 {@code #} 前缀并补全命名空间。 */
    private static String tagId(String raw) {
        String value = raw == null ? "" : raw.trim();
        return normalize(value.startsWith("#") ? value.substring(1) : value);
    }

    /** 仪式产物：合成 / 附魔类必须给出产物，其余类型缺省填 Goety 的占位物品。 */
    private static JsonObject ritualResult(String raw, String type) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            if (type.equals("goety:craft") || type.equals("goety:enchant")) return null;
            value = DUMMY_RESULT;
        }
        ItemAmount amount = itemAmount(value);
        if (amount == null) return null;
        JsonObject result = new JsonObject();
        result.addProperty("item", normalize(amount.id()));
        if (amount.count() != 1) result.addProperty("count", amount.count());
        return result;
    }

    private static void addIfPresent(JsonObject json, String key, String value) {
        if (value != null && !value.isBlank()) json.addProperty(key, value.trim());
    }

    /** 整数解析：缺省或非法时返回 fallback（不因一个数字写错拖垮整条仪式）。 */
    private static int integerOr(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
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

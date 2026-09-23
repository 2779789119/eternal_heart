package com.eternal_heart.config;

import com.eternal_heart.EternalHeartConfig;
import com.google.gson.*;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** One schema shared by the editor and the authoritative server. */
public final class ConfigValues {
    /**
     * 配置快照 JSON 的长度上限。
     *
     * <p>取值需要容纳最长的自由文本列表（自定义配方 / 自定义属性）：
     * 每条配方 JSON 数百字符，十几条就能把旧的 32KB 上限撑爆，
     * 表现为「面板保存失败：invalid」。</p>
     */
    public static final int MAX_JSON_LENGTH = 1 << 18;

    /** 自由文本列表单个元素的长度上限（配方 JSON 远长于普通 ID）。 */
    private static final int MAX_FREE_FORM_TEXT = 1024;
    private static final Gson GSON = new Gson();
    private static final Map<String, ForgeConfigSpec.ConfigValue<?>> VALUES = collect();
    /**
     * ConfigValue → 键 的身份索引。
     *
     * <p>{@link #get} 是全局最热路径（各功能每 tick 调用数百次）；原先在联机环境下
     * 每次都要线性扫描 {@link #VALUES}（130+ 项），单次 tick 累计上万次比较。
     * 换成身份哈希后是 O(1)，几十纳秒降到个位数纳秒。</p>
     */
    private static final Map<ForgeConfigSpec.ConfigValue<?>, String> KEYS = invert(VALUES);
    private static volatile Map<String, Object> remoteValues = Map.of();
    private static final Set<String> SECONDS = Set.of("DEBUFF_CLEAR_INTERVAL", "EXTRA_HIT_COOLDOWN",
            "SPEED_BURST_DURATION", "DAMAGE_AURA_TICK_INTERVAL", "FURY_DECAY_TICKS", "AUTO_FISH_INTERVAL",
            "LETHAL_GUARD_COOLDOWN", "ITEM_GUARD_TICKS");
    /** 允许「自由格式」条目的列表配置（元素不是纯资源 ID，另有专门校验规则）。 */
    private static final Set<String> FREE_FORM_LISTS = Set.of("EXTRA_ATTRIBUTES", "CUSTOM_RECIPES");
    private static final Set<String> PERCENT = Set.of("KNOCKBACK_RESIST", "MOVEMENT_SPEED",
            "DAMAGE_REDUCTION", "FALL_DAMAGE_RATIO", "REFLECT_RATIO", "EXPLOSION_RESIST",
            "DAMAGE_CAP_RATIO", "PROJECTILE_RESIST", "MAGIC_RESIST", "DODGE_CHANCE",
            "REFLECT_PROJECTILE_CHANCE", "LOW_HP_THRESHOLD", "BOSS_DAMAGE_BONUS", "EXECUTE_THRESHOLD",
            "COUNTER_DAMAGE_RATIO", "AOE_SPLASH_RATIO", "GLOW_DAMAGE_BONUS", "IGNITE_CHANCE",
            "AUTO_POTION_THRESHOLD", "LIGHTNING_REFLECT_CHANCE", "KILL_HEAL_RATIO", "GROWTH_AURA_CHANCE",
            "FURY_DAMAGE_PER_STACK", "FURY_LIFESTEAL", "LETHAL_GUARD_RATIO",
            // 修复：tooltip 走 percent()（×100）但此前不在白名单 → 面板填 30 会存成 30（倍率 30×）而不是 0.3
            "COOLDOWN_RATIO");

    private ConfigValues() {}

    private static Map<String, ForgeConfigSpec.ConfigValue<?>> collect() {
        Map<String, ForgeConfigSpec.ConfigValue<?>> values = new LinkedHashMap<>();
        for (Field field : EternalHeartConfig.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) continue;
            try {
                values.put(field.getName(), (ForgeConfigSpec.ConfigValue<?>) field.get(null));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read config schema", e);
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static Map<ForgeConfigSpec.ConfigValue<?>, String> invert(Map<String, ForgeConfigSpec.ConfigValue<?>> values) {
        Map<ForgeConfigSpec.ConfigValue<?>, String> keys = new IdentityHashMap<>(values.size() * 2);
        values.forEach((key, cv) -> keys.put(cv, key));
        return keys;
    }

    public static Map<String, ForgeConfigSpec.ConfigValue<?>> entries() { return VALUES; }

    /** Remote snapshots never call ConfigValue.set: NightConfig may auto-save setters to disk. */
    public static void setRemote(Map<String, Object> values) { remoteValues = Map.copyOf(values); }
    public static void clearRemote() { remoteValues = Map.of(); }

    @SuppressWarnings("unchecked")
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value) {
        if (!remoteValues.isEmpty()) {
            String key = KEYS.get(value);
            if (key != null) {
                Object remote = remoteValues.get(key);
                if (remote != null) return (T) remote;
            }
        }
        return value.get();
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        VALUES.forEach((key, cv) -> result.put(key, get(cv) instanceof List<?> list ? List.copyOf(list) : get(cv)));
        return result;
    }

    public static String encode(Map<String, Object> values) { return GSON.toJson(values); }

    public static Map<String, Object> decode(String json) {
        if (json.length() > MAX_JSON_LENGTH) throw new IllegalArgumentException("Config packet too large");
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Expected config object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : parsed.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            var cv = VALUES.get(key);
            if (cv == null) throw new IllegalArgumentException("Unknown config key: " + key);
            JsonElement raw = entry.getValue();
            Object value;
            if (cv instanceof ForgeConfigSpec.BooleanValue) {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) throw invalid(key);
                value = raw.getAsBoolean();
            } else if (cv instanceof ForgeConfigSpec.IntValue || cv instanceof ForgeConfigSpec.DoubleValue) {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) throw invalid(key);
                double number = raw.getAsDouble();
                if (!Double.isFinite(number)) throw invalid(key);
                if (cv instanceof ForgeConfigSpec.IntValue) {
                    if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw invalid(key);
                    value = (int) number;
                } else value = number;
            } else if (cv instanceof ForgeConfigSpec.EnumValue<?>) {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) throw invalid(key);
                value = Arrays.stream(cv.getDefault().getClass().getEnumConstants())
                        .filter(e -> ((Enum<?>) e).name().equals(raw.getAsString())).findFirst().orElseThrow(() -> invalid(key));
            } else if (cv.getDefault() instanceof List<?>) {
                if (!raw.isJsonArray() || raw.getAsJsonArray().size() > 256) throw invalid(key);
                List<String> list = new ArrayList<>();
                for (JsonElement element : raw.getAsJsonArray()) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw invalid(key);
                    String text = element.getAsString();
                    if (FREE_FORM_LISTS.contains(key)) {
                        // 自由格式（自定义属性 "<id> <值> <操作>" / 配方 JSON）：
                        // 只做长度与非空校验，具体合法性交由各自的解析层处理
                        if (text.isBlank() || text.length() > MAX_FREE_FORM_TEXT) throw invalid(key);
                    } else if (!text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                        throw invalid(key);
                    }
                    list.add(text);
                }
                value = List.copyOf(list);
            } else throw invalid(key);
            validate(key, value);
            result.put(key, value);
        }
        return result;
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException("Invalid config value: " + key);
    }

    public static void validate(String key, Object value) {
        var cv = VALUES.get(key);
        if (cv == null) throw invalid(key);
        if (value instanceof Number n && !Double.isFinite(n.doubleValue())) throw invalid(key);
        // 类型必须与配置项严格匹配：Forge 的 ValueSpec 对装箱类型检查很宽松，
        // 类型不符的值（例如给整型项传 1.5）会被写入，随后在强转处崩溃 —— 必须在这里挡下
        if (!typeMatches(cv, value)) throw invalid(key);
        ForgeConfigSpec.ValueSpec spec = EternalHeartConfig.SPEC.getSpec().get(cv.getPath());
        if (spec == null || !spec.test(value)) throw invalid(key);
    }

    /** 值类型是否与配置项声明的类型一致（整数 / 浮点 / 布尔 / 枚举 / 列表各归各）。 */
    private static boolean typeMatches(ForgeConfigSpec.ConfigValue<?> cv, Object value) {
        if (cv instanceof ForgeConfigSpec.BooleanValue) return value instanceof Boolean;
        if (cv instanceof ForgeConfigSpec.IntValue) return value instanceof Integer;
        if (cv instanceof ForgeConfigSpec.DoubleValue) return value instanceof Double;
        if (cv instanceof ForgeConfigSpec.EnumValue<?>) {
            Object defaultValue = cv.getDefault();
            return defaultValue != null && defaultValue.getClass().isInstance(value);
        }
        if (cv.getDefault() instanceof List<?>) return value instanceof List<?>;
        return true;
    }

    /** Validate the whole patch before mutating any config value. Does not save. */
    public static void apply(Map<String, Object> values) {
        values.forEach(ConfigValues::validate);
        values.forEach(ConfigValues::set);
    }

    @SuppressWarnings("unchecked")
    private static void set(String key, Object value) {
        ((ForgeConfigSpec.ConfigValue<Object>) VALUES.get(key)).set(value);
    }

    public static double toDisplay(String key, Number value) {
        double raw = value.doubleValue();
        double display = SECONDS.contains(key) ? raw / 20.0
                : key.equals("TICK_REGEN") ? raw * 20.0
                : PERCENT.contains(key) ? raw * 100.0
                : raw; // total multipliers are displayed as ×, not as an additive percentage
        // 配置上限放开后 ×100 / ×20 可能溢出为 Infinity（无法格式化、无法输入）：退回有限最大值
        return Double.isFinite(display) ? display : Double.MAX_VALUE;
    }

    public static Number fromDisplay(String key, double value) {
        if (!Double.isFinite(value)) throw invalid(key);
        double raw = SECONDS.contains(key) ? value * 20.0
                : key.equals("TICK_REGEN") ? value / 20.0 : PERCENT.contains(key) ? value / 100.0 : value;
        Number result;
        if (VALUES.get(key) instanceof ForgeConfigSpec.IntValue) {
            double rounded = Math.rint(raw);
            if (Math.abs(raw - rounded) > 0.000001 || rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) throw invalid(key);
            result = (int) rounded;
        } else result = raw;
        validate(key, result);
        return result;
    }
}

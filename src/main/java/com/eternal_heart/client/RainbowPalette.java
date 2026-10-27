package com.eternal_heart.client;

/**
 * 彩虹配色工具 —— 头顶光环与炫彩物品名共用同一套色相换算，保证两处「彩虹」观感一致。
 *
 * <p>色相制的饱和度与明度都固定为 1（纯色、全亮），所以只需要一个色相参数：
 * 光环用它按角度铺色带，物品名用它按字符铺渐变。</p>
 */
public final class RainbowPalette {

    private RainbowPalette() {
    }

    /**
     * 色相 → 打包的 {@code 0xRRGGBB}。
     *
     * <p>标准六段线性插值：每 60° 一个色区，在相邻两个原色之间线性过渡，
     * 因此色带过渡是连续的，不会出现色阶断层。色相会自动归一到 [0,1)，
     * 因此传入负数或超过 1 的值（随时间累积的流动量）都能正常工作。</p>
     */
    public static int hueToRgb(float hue) {
        float wrapped = hue - (float) Math.floor(hue);   // 归一到 [0,1)
        float scaled = wrapped * 6.0F;
        int sector = (int) scaled;                       // 0~5，落在哪个 60° 色区
        if (sector > 5) sector = 5;                      // 浮点边界保护（wrapped 极接近 1 时）
        float fraction = scaled - sector;
        float rising = fraction;                         // 该色区里正在升到 1 的分量
        float falling = 1.0F - fraction;                 // 正在降到 0 的分量
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = 1.0F; g = rising; b = 0.0F; }
            case 1 -> { r = falling; g = 1.0F; b = 0.0F; }
            case 2 -> { r = 0.0F; g = 1.0F; b = rising; }
            case 3 -> { r = 0.0F; g = falling; b = 1.0F; }
            case 4 -> { r = rising; g = 0.0F; b = 1.0F; }
            default -> { r = 1.0F; g = 0.0F; b = falling; }
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }
}

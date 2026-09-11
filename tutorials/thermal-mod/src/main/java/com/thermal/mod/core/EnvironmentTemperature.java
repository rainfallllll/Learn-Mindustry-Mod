package com.thermal.mod.core;

import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.world.Tile;

/**
 * 环境温度 —— 双轨温度表（T_ground / T_air），进图预计算。
 *
 * <p>地表温度按 tile 存储在 FloatArray 中（按 tile.array() 索引），
 * 全局气温为单一 float 值。</p>
 *
 * <p>材质初始温度表：
 * <ul>
 *   <li>slag (熔岩): 1473.15K (1200°C)</li>
 *   <li>sand (沙地): 308.15K (35°C)</li>
 *   <li>water (浅水): 293.15K (20°C)</li>
 *   <li>ice (冰面): 268.15K (-5°C)</li>
 *   <li>其他: 293.15K (20°C)</li>
 * </ul>
 * </p>
 */
public class EnvironmentTemperature {

    /** 全局气温 (K)，默认 20°C */
    public static float airTemperatureK = 293.15f;

    /** 每格地温，按 tile.array() 索引存储 */
    private static FloatSeq groundTemps;

    /** 材质初始温度表（key = floor 名称） */
    private static final ObjectMap<String, Float> materialTemps = new ObjectMap<>();

    /** 地块最大热交换速率表（key = floor 名称），单位 J/tick */
    private static final ObjectMap<String, Float> maxHeatRateMap = new ObjectMap<>();

    static {
        // 材质初始温度 (K)
        materialTemps.put("slag", 1473.15f);     // 熔岩 1200°C
        materialTemps.put("sand", 308.15f);       // 沙地 35°C
        materialTemps.put("water", 293.15f);     // 浅水 20°C
        materialTemps.put("deepwater", 293.15f); // 深水 20°C
        materialTemps.put("ice", 268.15f);       // 冰面 -5°C
        materialTemps.put("snow", 268.15f);      // 雪地 -5°C
        materialTemps.put("stone", 298.15f);     // 石头 25°C
        materialTemps.put("grass", 298.15f);     // 草地 25°C
        materialTemps.put("dirt", 298.15f);      // 泥土 25°C

        // 地块最大热交换速率 (J/tick)
        maxHeatRateMap.put("slag", Float.POSITIVE_INFINITY);  // 岩浆不限
        maxHeatRateMap.put("water", 300f);                     // 浅水
        maxHeatRateMap.put("deepwater", 500f);                 // 深水
        maxHeatRateMap.put("sand", 400f);                      // 沙地
        maxHeatRateMap.put("stone", 500f);                     // 石头
        maxHeatRateMap.put("ice", 50f);                        // 冰面
        maxHeatRateMap.put("snow", 50f);                       // 雪地
    }

    /**
     * 进图预计算：遍历所有 tile，根据 floor 名称设置地温。
     * 在 WorldLoadEvent 中调用。
     */
    public static void precompute() {
        int w = Vars.world.width();
        int h = Vars.world.height();
        groundTemps = new FloatSeq(w * h);
        groundTemps.size = w * h;
        java.util.Arrays.fill(groundTemps.items, 293.15f);

        // 遍历所有 tile，按 floor 名称设置初始温度
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Tile tile = Vars.world.tiles.get(x, y);
                if (tile == null) continue;
                String floorName = tile.floor().name;
                float temp = materialTemps.get(floorName, 293.15f);
                groundTemps.set(tile.array(), temp);
            }
        }
    }

    /**
     * 查询某 tile 的地表温度。
     * @param tile 目标 tile
     * @return 地温 (K)
     */
    public static float getGroundTemp(Tile tile) {
        if (groundTemps == null) return 293.15f;
        int idx = tile.array();
        if (idx < 0 || idx >= groundTemps.size) return 293.15f;
        return groundTemps.get(idx);
    }

    /**
     * 查询全局气温。
     * @return 气温 (K)
     */
    public static float getAirTemp() {
        return airTemperatureK;
    }

    /**
     * 查询某 floor 的最大热交换速率。
     * @param floorName 地块名称
     * @return 最大热交换速率 (J/tick)，无记录返回无穷大
     */
    public static float getMaxHeatRate(String floorName) {
        return maxHeatRateMap.get(floorName, Float.POSITIVE_INFINITY);
    }

    /**
     * 重置（切图时调用）。
     */
    public static void reset() {
        groundTemps = null;
    }
}

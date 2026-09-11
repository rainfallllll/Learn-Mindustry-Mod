package com.thermal.mod.core;

import arc.struct.ObjectFloatMap;

/**
 * 热力组件 —— 所有参与热交换的实体持有此组件。
 *
 * <p>热量 Q 是唯一状态变量，温度 T = Q / C 是惰性计算的表观结果。</p>
 *
 * <p>核心设计：
 * <ul>
 *   <li>storedHeat 为实际存储热量（J）</li>
 *   <li>pendingDeltaQ 为双缓冲暂存区（本 tick 待应用的热流）</li>
 *   <li>getTemperatureK() 惰性求值，每 tick 最多一次除法</li>
 * </ul>
 * </p>
 */
public class ThermalComponent {

    // ═══════════════════════════════════════
    //  状态变量
    // ═══════════════════════════════════════

    /** 当前存储的热量 (J) */
    public float storedHeat;

    /** 本 tick 待应用的热流缓冲（双缓冲核心） */
    public float pendingDeltaQ;

    // ═══════════════════════════════════════
    //  配置参数
    // ═══════════════════════════════════════

    /** 热容 C (J/K) */
    public float heatCapacity;

    /** 温度下限（绝对零度保护）(K) */
    public float minTempK = 273f;

    /** 安全上限 (K) */
    public float maxTempK = 1000f;

    // ═══════════════════════════════════════
    //  传热系数
    // ═══════════════════════════════════════

    /** 与空气的基准传热系数 (J/tick·格·K) */
    public float airU;

    // ═══════════════════════════════════════
    //  地块效率系数
    // ═══════════════════════════════════════

    /**
     * key = 地块名称 / "air", value = 效率倍率
     * 实际 U = airU × 效率
     * 未列出的地块默认 1.0
     */
    public ObjectFloatMap<String> groundEfficiency = new ObjectFloatMap<>();

    // ═══════════════════════════════════════
    //  架空标志
    // ═══════════════════════════════════════

    /** true=悬空(仅空气换热), false=地面(空气+地面) */
    public boolean isFloating;

    // ═══════════════════════════════════════
    //  建筑间接触热阻
    // ═══════════════════════════════════════

    /** 建筑间接触热阻 R (K·tick/J)，与 airU 独立 */
    public float contactResistance = 0.05f;

    // ═══════════════════════════════════════
    //  缓存
    // ═══════════════════════════════════════

    private float cachedTempK;
    private boolean tempDirty = true;

    // ─────────────────────────────────────
    //  核心方法
    // ─────────────────────────────────────

    /**
     * 惰性温度求值：每 tick 最多一次除法。
     * @return 当前温度 (K)
     */
    public float getTemperatureK() {
        if (tempDirty) {
            cachedTempK = Math.max(minTempK, storedHeat / heatCapacity);
            tempDirty = false;
        }
        return cachedTempK;
    }

    /**
     * 纯加法注入/抽取热量，标记温度过期。
     * @param deltaQ 热量变化量（正=注入，负=抽取）
     */
    public void addHeat(float deltaQ) {
        storedHeat += deltaQ;
        if (storedHeat < 0f) storedHeat = 0f; // 防负热量兜底
        tempDirty = true;
    }

    /** 标记温度缓存失效（外部直接修改 storedHeat 后调用） */
    public void invalidate() {
        tempDirty = true;
    }

    /** 重置缓冲（Phase 2 结算后调用） */
    public void reset() {
        pendingDeltaQ = 0f;
    }

    /**
     * 查询地块效率系数，未列出的返回 1.0。
     * @param floorName 地块名称或 "air"
     * @return 效率倍率
     */
    public float getGroundEfficiency(String floorName) {
        return groundEfficiency.get(floorName, 1f);
    }

    /**
     * 设置地块效率系数。
     */
    public void setGroundEfficiency(String floorName, float efficiency) {
        groundEfficiency.put(floorName, efficiency);
    }
}

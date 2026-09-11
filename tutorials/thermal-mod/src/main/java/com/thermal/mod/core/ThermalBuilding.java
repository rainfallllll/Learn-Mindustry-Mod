package com.thermal.mod.core;

import mindustry.gen.Building;

/**
 * 热力建筑接口 —— 所有参与热交换的热力建筑 Build 类实现此接口。
 *
 * <p>每个热力建筑持有一个 {@link ThermalComponent}，并在每 tick 的 Phase 2 中
 * 调用 {@link #updateThermalLogic(float)} 执行自身的产热/耗热/热泵逻辑。</p>
 */
public interface ThermalBuilding {

    /**
     * @return 此建筑的热力组件
     */
    ThermalComponent getThermal();

    /**
     * Phase 2 中调用，执行建筑自身的产热/耗热/热泵逻辑。
     * 环境换热已在 ThermalSystem 中统一处理，此处只做主动热量注入/抽取。
     *
     * @param deltaTime tick 时间步长（通常为 1f）
     */
    void updateThermalLogic(float deltaTime);

    /**
     * @return 转为 Building 引用（用于访问 tile/proximity 等）
     */
    Building asBuilding();
}

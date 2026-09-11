package com.thermal.mod.core;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.world.Tile;

/**
 * 双缓冲热力系统管理器。
 *
 * <p>每 tick 执行两阶段更新：</p>
 * <ol>
 *   <li><strong>Phase 1</strong>：遍历所有热力建筑，对每个邻居只向低温传热，
 *       flow=dT/(R_A+R_B)*dt，防过冲 min(flow, storedHeat)，写入双方 pendingDeltaQ</li>
 *   <li><strong>Phase 2</strong>：遍历所有热力建筑，
 *       ① addHeat(pendingDeltaQ) 并清零
 *       ② 环境换热（对空气 + 对地面，查 EnvironmentTemperature）
 *       ③ 调用建筑自身 updateThermalLogic(dt)</li>
 * </ol>
 */
public class ThermalSystem {

    /** 活跃热力建筑列表 */
    private final Seq<ThermalBuilding> buildings = new Seq<>();

    /**
     * 注册热力建筑（在 Build.placed() 中调用）。
     */
    public void register(ThermalBuilding building) {
        if (!buildings.contains(building)) {
            buildings.add(building);
        }
    }

    /**
     * 注销热力建筑（在 Build.onRemoved() 中调用）。
     */
    public void unregister(ThermalBuilding building) {
        buildings.remove(building);
    }

    /**
     * 每 tick 主更新入口。
     * @param dt 时间步长（通常为 1f）
     */
    public void update(float dt) {
        if (buildings.size == 0) return;

        // ═══════════════════════════════════════
        //  Phase 1：接触热流计算（只读温度，写缓冲）
        // ═══════════════════════════════════════
        for (int i = 0; i < buildings.size; i++) {
            ThermalBuilding thermalBuilding = buildings.get(i);
            Building myBuilding = thermalBuilding.asBuilding();
            ThermalComponent myNode = thermalBuilding.getThermal();
            float myTemp = myNode.getTemperatureK();

            for (int j = 0; j < myBuilding.proximity.size; j++) {
                Building neighbor = myBuilding.proximity.get(j);
                if (neighbor == null) continue;
                if (neighbor instanceof ThermalBuilding thermalNeighbor) {
                    float neighborTemp = thermalNeighbor.getThermal().getTemperatureK();

                    // 【核心规则】只向低温传热，避免重复计算
                    if (myTemp > neighborTemp) {
                        float dT = myTemp - neighborTemp;
                        float resistance = myNode.contactResistance
                            + thermalNeighbor.getThermal().contactResistance;
                        if (resistance <= 0f) continue;

                        float flow = (dT / resistance) * dt;

                        // 【防过冲保护】传热量不超过自身存量
                        flow = Math.min(flow, myNode.storedHeat);

                        if (flow > 0f) {
                            // 【双缓冲】绝不直接修改 storedHeat
                            myNode.pendingDeltaQ -= flow;
                            thermalNeighbor.getThermal().pendingDeltaQ += flow;
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        //  Phase 2：统一结算 + 环境换热 + 自身逻辑
        // ═══════════════════════════════════════
        for (int i = 0; i < buildings.size; i++) {
            ThermalBuilding thermalBuilding = buildings.get(i);
            Building myBuilding = thermalBuilding.asBuilding();
            ThermalComponent node = thermalBuilding.getThermal();

            // 1. 结算接触热流
            node.addHeat(node.pendingDeltaQ);
            node.pendingDeltaQ = 0f;

            // 2. 环境换热
            float myTemp = node.getTemperatureK();
            float heatLoss = 0f;

            // 2a. 对空气散热——所有建筑都有
            float airEff = node.getGroundEfficiency("air");
            float uAir = node.airU * airEff;
            float tAir = EnvironmentTemperature.getAirTemp();
            heatLoss += uAir * (myTemp - tAir) * dt;

            // 2b. 对地面散热——仅非架空建筑
            if (!node.isFloating) {
                Tile tile = myBuilding.tile;
                if (tile != null) {
                    String floorName = tile.floor().name;
                    float groundEff = node.getGroundEfficiency(floorName);
                    float uGround = node.airU * groundEff;
                    float tGround = EnvironmentTemperature.getGroundTemp(tile);
                    float maxRate = EnvironmentTemperature.getMaxHeatRate(floorName);
                    float idealFlow = uGround * (myTemp - tGround) * dt;
                    heatLoss += Math.min(idealFlow, maxRate * dt);
                }
            }

            node.addHeat(-heatLoss);

            // 3. 建筑自身产热/耗热/热泵逻辑
            thermalBuilding.updateThermalLogic(dt);
        }
    }

    /**
     * @return 当前活跃热力建筑数量
     */
    public int getBuildingCount() {
        return buildings.size;
    }

    /**
     * 调试用：返回当前全部活跃热力建筑。
     * @return 热力建筑列表（只读遍历）
     */
    public Seq<ThermalBuilding> getBuildings() {
        return buildings;
    }
}

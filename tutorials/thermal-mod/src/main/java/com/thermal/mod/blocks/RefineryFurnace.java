package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;

/**
 * 精炼炉 —— 恒温耗热建筑，需要持续消耗热量才能工作。
 *
 * <p>行为模型：
 * <ul>
 *   <li>预热：温度 &lt; operatingMinK 时只升温不生产</li>
 *   <li>工作：温度达到要求后以 ratedHeat 速率消耗热量</li>
 *   <li>空载保温：无原料时仅消耗 idleLoss 维持温度</li>
 * </ul>
 * </p>
 *
 * <p>本轮为骨架实现：不实际产出物品，只做温度状态判断。</p>
 */
public class RefineryFurnace extends Wall {

    /** 工作温区下限 (K)，低于此温度不生产 */
    public float operatingMinK = 450f;  // 177°C

    /** 额定热耗 (J/tick)：生产时消耗 */
    public float ratedHeat = 80f;

    /** 空载损耗 (J/tick)：无原料时维持温度 */
    public float idleLoss = 10f;

    public RefineryFurnace(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    public class FurnaceBuild extends Building implements ThermalBuilding {

        public ThermalComponent thermal = new ThermalComponent();

        /** 运行状态：预热中 / 工作中 / 空载 */
        public enum FurnaceState {
            PREHEATING, WORKING, IDLING
        }
        public FurnaceState state = FurnaceState.PREHEATING;

        @Override
        public ThermalComponent getThermal() {
            return thermal;
        }

        @Override
        public Building asBuilding() {
            return this;
        }

        @Override
        public void placed() {
            super.placed();
            thermal.heatCapacity = 1500f;
            thermal.maxTempK = 700f;
            thermal.minTempK = 273f;
            thermal.airU = 0.08f;
            thermal.isFloating = false;
            thermal.contactResistance = 0.05f;
            thermal.storedHeat = thermal.heatCapacity * 293.15f;
            thermal.invalidate();

            ThermalModMain.thermalSystem.register(this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            ThermalModMain.thermalSystem.unregister(this);
        }

        @Override
        public void updateThermalLogic(float dt) {
            float temp = thermal.getTemperatureK();

            // 骨架版：不检查实际输入物品，模拟有输入
            boolean hasInput = true; // TODO: 后续接入物品检测

            if (hasInput && temp >= operatingMinK) {
                // 工作状态：消耗额定热量生产
                state = FurnaceState.WORKING;
                thermal.addHeat(-ratedHeat * dt);
            } else if (hasInput) {
                // 预热中：温度不够，不耗热
                state = FurnaceState.PREHEATING;
            } else {
                // 空载：消耗少量热量保温
                state = FurnaceState.IDLING;
                thermal.addHeat(-idleLoss * dt);
            }
        }

        @Override
        public void display(Table table) {
            super.display(table);

            table.row();
            table.left().label(() -> "[orange]精炼炉[]").padTop(4);
            table.row();
            table.left().label(() ->
                "温度: " + String.format("%.1f", thermal.getTemperatureK() - 273.15f) + "°C" +
                stateLabel()
            );
            table.row();
            table.left().label(() ->
                "温区要求: >= " + String.format("%.0f", operatingMinK - 273.15f) + "°C"
            );
            table.row();
            table.left().label(() ->
                "热耗: " + (state == FurnaceState.WORKING ? "-" + (int)ratedHeat : state == FurnaceState.IDLING ? "-" + (int)idleLoss : "0") + " J/t"
            );
            table.row();
            table.left().label(() ->
                "热容: " + thermal.heatCapacity + " J/K"
            );
        }

        private String stateLabel() {
            switch (state) {
                case WORKING: return " [green](工作中)";
                case PREHEATING: return " [yellow](预热中)";
                case IDLING: return " [gray](空载)";
                default: return "";
            }
        }
    }
}

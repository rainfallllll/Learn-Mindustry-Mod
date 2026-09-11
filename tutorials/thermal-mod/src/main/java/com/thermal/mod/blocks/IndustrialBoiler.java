package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;

/**
 * 工业锅炉 —— 持续产热建筑。
 *
 * <p>行为模型：
 * <ul>
 *   <li>每 tick 向自身注入 ratedPower 的热量</li>
 *   <li>温度达 maxTempK 时硬停机，不再产热</li>
 *   <li>温度回落到 maxTempK - restartHysteresis 时重新启动</li>
 *   <li>带回差防止高频启停震荡</li>
 * </ul>
 * </p>
 */
public class IndustrialBoiler extends Wall {

    /** 额定产热功率 (J/tick) */
    public float ratedPower = 100f;

    /** 重启回差 (K)：温度降到此阈值以下才重启 */
    public float restartHysteresis = 50f;

    public IndustrialBoiler(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    public class BoilerBuild extends Building implements ThermalBuilding {

        public ThermalComponent thermal = new ThermalComponent();
        public boolean isShutdown = false;

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
            // 初始化热容等参数
            thermal.heatCapacity = 2000f;
            thermal.maxTempK = 800f;       // 527°C
            thermal.minTempK = 273f;
            thermal.airU = 0.05f;
            thermal.isFloating = false;
            thermal.contactResistance = 0.05f;
            // 初始温度设为环境温度
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

            // 温度达上限 → 停机
            if (temp >= thermal.maxTempK) {
                isShutdown = true;
            }

            // 温度回落到阈值以下 → 重启
            if (isShutdown && temp < thermal.maxTempK - restartHysteresis) {
                isShutdown = false;
            }

            // 运行中产热
            if (!isShutdown) {
                thermal.addHeat(ratedPower * dt);
            }
        }

        @Override
        public void display(Table table) {
            super.display(table);

            table.row();
            table.left().label(() -> "[orange]工业锅炉[]").padTop(4);
            table.row();
            table.left().label(() ->
                "温度: " + String.format("%.1f", thermal.getTemperatureK() - 273.15f) + "°C" +
                (isShutdown ? " [red](停机)" : " [green](运行)")
            );
            table.row();
            table.left().label(() ->
                "存储热量: " + String.format("%.0f", thermal.storedHeat) + " J"
            );
            table.row();
            table.left().label(() ->
                "热容: " + thermal.heatCapacity + " J/K"
            );
            table.row();
            table.left().label(() ->
                "产热: " + (isShutdown ? "0" : (int)ratedPower) + " J/t"
            );
        }
    }
}

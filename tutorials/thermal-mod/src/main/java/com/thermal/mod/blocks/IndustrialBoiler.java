package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import arc.util.Strings;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.ui.Bar;

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
 *
 * <p>v0.2 迭代改动：
 * <ul>
 *   <li>预热优化：ratedPower 100→400, heatCapacity 2000→1000，升温速率约 17x</li>
 *   <li>官方热网桥接：Build implements HeatBlock，将热力学产热输出到官方热网</li>
 *   <li>温度状态条：setBars() 中 addBar("temperature")，超温红色闪烁</li>
 * </ul>
 * </p>
 */
public class IndustrialBoiler extends Wall {

    /** 额定产热功率 (J/tick) —— v0.2: 100→400 加速预热 */
    public float ratedPower = 400f;

    /** 重启回差 (K)：温度降到此阈值以下才重启 */
    public float restartHysteresis = 50f;

    /** 官方热网输出功率（HeatBlock.heat() 返回值）—— v0.2 新增 */
    public float officialHeatOutput = 15f;

    public IndustrialBoiler(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    @Override
    public void setBars() {
        super.setBars();
        // v0.2 迭代3：温度状态条
        addBar("temperature", (BoilerBuild entity) -> new Bar(
            () -> "温度 " + Strings.fixed(entity.thermal.getTemperatureK() - 273.15f, 1) + "°C",
            () -> {
                float frac = entity.thermal.getTemperatureK() / entity.thermal.maxTempK;
                // 接近上限 → 红色；正常 → 橙色
                if (frac > 0.9f) return Pal.health;
                if (frac > 0.75f) return Pal.lightOrange;
                return Pal.heal;
            },
            () -> Math.max(0f, Math.min(1f,
                (entity.thermal.getTemperatureK() - entity.thermal.minTempK)
                / (entity.thermal.maxTempK - entity.thermal.minTempK)))
        ));
    }

    public class BoilerBuild extends Building implements ThermalBuilding, HeatBlock {

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
            // v0.2 迭代1：热容 2000→1000 加速预热
            thermal.heatCapacity = 1000f;
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

        // ─── v0.2 迭代2：官方热网 HeatBlock 接口 ───

        @Override
        public float heat() {
            // 停机时不输出官方热；运行时输出固定值
            return isShutdown ? 0f : officialHeatOutput;
        }

        @Override
        public float heatFrac() {
            return officialHeatOutput > 0f ? heat() / officialHeatOutput : 0f;
        }

        @Override
        public void display(Table table) {
            super.display(table);

            table.row();
            table.left().label(() -> "[orange]工业锅炉[v0.2][]").padTop(4);
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
                "产热: " + (isShutdown ? "0" : (int)ratedPower) + " J/t | 官方热: " + (isShutdown ? "0" : officialHeatOutput)
            );
        }
    }
}

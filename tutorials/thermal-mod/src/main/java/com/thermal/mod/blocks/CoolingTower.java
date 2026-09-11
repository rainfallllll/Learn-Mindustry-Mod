package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;

/**
 * 散热塔 —— 高散热建筑，自身不产热不耗热，靠环境换热散热。
 *
 * <p>特性：
 * <ul>
 *   <li>高 airU（0.31），与空气换热强</li>
 *   <li>isFloating=false，同时与地面换热</li>
 *   <li>地面换热受地块效率系数和 maxHeatRate 上限约束</li>
 * </ul>
 * </p>
 */
public class CoolingTower extends Wall {

    public CoolingTower(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    public class TowerBuild extends Building implements ThermalBuilding {

        public ThermalComponent thermal = new ThermalComponent();

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
            thermal.heatCapacity = 500f;
            thermal.maxTempK = 600f;
            thermal.minTempK = 273f;
            thermal.airU = 0.31f;
            thermal.isFloating = false;
            thermal.contactResistance = 0.05f;
            thermal.storedHeat = thermal.heatCapacity * 293.15f;
            thermal.invalidate();

            // 地块效率系数
            thermal.setGroundEfficiency("air", 1.0f);
            thermal.setGroundEfficiency("sand", 1.2f);
            thermal.setGroundEfficiency("stone", 1.5f);
            thermal.setGroundEfficiency("water", 2.2f);
            thermal.setGroundEfficiency("slag", 3.0f);
            thermal.setGroundEfficiency("ice", 0.6f);

            ThermalModMain.thermalSystem.register(this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            ThermalModMain.thermalSystem.unregister(this);
        }

        @Override
        public void updateThermalLogic(float dt) {
            // 散热塔自身不产热不耗热，纯环境散热
        }

        @Override
        public void display(Table table) {
            super.display(table);

            table.row();
            table.left().label(() -> "[cyan]散热塔[]").padTop(4);
            table.row();
            table.left().label(() ->
                "温度: " + String.format("%.1f", thermal.getTemperatureK() - 273.15f) + "°C"
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
                "空气U: " + thermal.airU + " J/(t·格·K)"
            );
            table.row();
            table.left().label(() ->
                "状态: [blue]散热中[]"
            );
        }
    }
}

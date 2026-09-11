package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;

/**
 * 导热管 —— 架空保温管道，热量传输介质。
 *
 * <p>特性：
 * <ul>
 *   <li>isFloating=true，只与空气换热，不与地面换热</li>
 *   <li>低 airU（0.008），保温好，空气散热极小</li>
 *   <li>小 contactResistance（0.02），管道间传热快</li>
 *   <li>自身无产热/耗热逻辑，纯导热</li>
 * </ul>
 * </p>
 */
public class HeatConduit extends Wall {

    public HeatConduit(String name) {
        super(name);
        update = true;
        solid = true;
        noUpdateDisabled = true;
        rotate = false;
    }

    public class ConduitBuild extends Building implements ThermalBuilding {

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
            thermal.heatCapacity = 50f;
            thermal.maxTempK = 800f;
            thermal.minTempK = 200f;       // -73°C，防止过低
            thermal.airU = 0.008f;         // 极低 = 保温好
            thermal.isFloating = true;     // 架空，不接触地面
            thermal.contactResistance = 0.02f; // 小 R = 传热快
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
            // 导热管无自身逻辑，纯靠 ThermalSystem 双缓冲传热
        }

        @Override
        public void display(Table table) {
            super.display(table);

            table.row();
            table.left().label(() -> "[orange]导热管[]").padTop(4);
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
                "空气U: " + thermal.airU + " [gray](保温)"
            );
            table.row();
            table.left().label(() ->
                "状态: " + (thermal.isFloating ? "[gray]架空保温[]" : "[white]接地[]")
            );
        }
    }
}

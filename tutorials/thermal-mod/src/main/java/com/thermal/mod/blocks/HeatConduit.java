package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import arc.util.Strings;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
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
 *
 * <p>v0.2：新增温度状态条（setBars）。</p>
 */
public class HeatConduit extends Wall {

    public HeatConduit(String name) {
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
        addBar("temperature", (ConduitBuild entity) -> new Bar(
            () -> "温度 " + Strings.fixed(entity.thermal.getTemperatureK() - 273.15f, 1) + "°C",
            () -> {
                float frac = entity.thermal.getTemperatureK() / entity.thermal.maxTempK;
                if (frac > 0.9f) return Pal.health;
                if (frac > 0.75f) return Pal.lightOrange;
                return Pal.lightOrange;
            },
            () -> Math.max(0f, Math.min(1f,
                (entity.thermal.getTemperatureK() - entity.thermal.minTempK)
                / (entity.thermal.maxTempK - entity.thermal.minTempK)))
        ));
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

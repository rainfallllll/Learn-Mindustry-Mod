package com.thermal.mod.blocks;

import arc.scene.ui.layout.Table;
import arc.util.Strings;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import com.thermal.mod.core.ThermalComponent;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.world.blocks.production.HeatCrafter;
import mindustry.ui.Bar;

/**
 * 精炼炉 —— v0.2 正式集成 v160 HeatCrafter。
 *
 * <p>v0.2 核心改动：
 * <ul>
 *   <li>父类从 Wall 改为 HeatCrafter（core/src/mindustry/world/blocks/production/HeatCrafter.java:12）</li>
 *   <li>Build 类 extends HeatCrafterBuild（HeatCrafter.java:43），天然实现 HeatConsumer</li>
 *   <li>桥接设计：
 *     <ol>
 *       <li>updateTile() 先调 super.updateTile() → HeatCrafter 官方 calculateHeat(sideHeat) 扫描 HeatBlock 邻居</li>
 *       <li>再桥接：官方 heat 值注入 ThermalComponent.addHeat()（1 官方单位 = 50 J）</li>
 *       <li>efficiencyScale() 覆写：热力学温度比例与官方热比例联动缩放</li>
 *     </ol>
 *   </li>
 *   <li>温度状态条：setBars() 中 addBar("temperature")，超温红色闪烁</li>
 * </ul>
 * </p>
 *
 * <p>行为模型（保持不变）：
 * <ul>
 *   <li>预热：温度 &lt; operatingMinK 时只升温不生产</li>
 *   <li>工作：温度达到要求后以 ratedHeat 速率消耗热量</li>
 *   <li>空载保温：无原料时仅消耗 idleLoss 维持温度</li>
 * </ul>
 * </p>
 */
public class RefineryFurnace extends HeatCrafter {

    /** 工作温区下限 (K)，低于此温度不生产 */
    public float operatingMinK = 450f;  // 177°C

    /** 额定热耗 (J/tick)：生产时消耗 */
    public float ratedHeat = 80f;

    /** 空载损耗 (J/tick)：无原料时维持温度 */
    public float idleLoss = 10f;

    /** 官方热单位 → 热力学焦耳的换算系数 —— v0.2 桥接 */
    public float heatToJoule = 50f;

    public RefineryFurnace(String name) {
        super(name);
        // HeatCrafter 继承自 GenericCrafter，已设置 update=true, solid=true
        rotate = false;
        // 官方热需求：10（默认），锅炉 HeatBlock 输出 15 → 足够 shouldConsume
        heatRequirement = 10f;
        craftTime = 60f; // 1秒一个周期，仅用于 shouldConsume 联动测试
    }

    @Override
    public void setBars() {
        super.setBars();
        // v0.2 迭代3：温度状态条（与 HeatCrafter 自带的 "heat" 热条并存）
        addBar("temperature", (FurnaceBuild entity) -> new Bar(
            () -> "温度 " + Strings.fixed(entity.thermal.getTemperatureK() - 273.15f, 1) + "°C",
            () -> {
                float frac = entity.thermal.getTemperatureK() / entity.thermal.maxTempK;
                if (frac > 0.9f) return Pal.health;
                if (frac > 0.75f) return Pal.lightOrange;
                return Pal.heal;
            },
            () -> Math.max(0f, Math.min(1f,
                (entity.thermal.getTemperatureK() - entity.thermal.minTempK)
                / (entity.thermal.maxTempK - entity.thermal.minTempK)))
        ));
    }

    public class FurnaceBuild extends HeatCrafterBuild implements ThermalBuilding {

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

        /**
         * v0.2 迭代2：桥接官方 HeatCrafter 与热力学系统。
         *
         * <p>调用顺序：
         * <ol>
         *   <li>super.updateTile() → HeatCrafterBuild.updateTile()（HeatCrafter.java:49）：
         *       heat = calculateHeat(sideHeat) 扫描邻居 HeatBlock；
         *       再调 GenericCrafterBuild.updateTile() 做生产逻辑</li>
         *   <li>桥接：将官方热网的 heat 值注入 ThermalComponent</li>
         *   <li>热力学系统由 ThermalSystem.update() 统一驱动（见 ThermalModMain.init）</li>
         * </ol>
         * </p>
         */
        @Override
        public void updateTile() {
            // 1. 官方逻辑：calculateHeat(sideHeat) + GenericCrafter 生产逻辑
            super.updateTile();

            // 2. 桥接：官方热网 heat → 热力学系统
            //    官方 heat 单位（heatRequirement=10 为满效基准）映射为 J
            if (heat > 0f) {
                thermal.addHeat(heat * heatToJoule * delta());
            }
        }

        /**
         * v0.2 迭代2：覆写效率缩放，联动热力学温度。
         *
         * <p>官方 efficiencyScale()（HeatCrafter.java:88）仅基于 heat/heatRequirement。
         * 此处叠加热力学温度比例：温度未达工作点时降低效率，达工作点后使用官方缩放。
         * </p>
         */
        @Override
        public float efficiencyScale() {
            float officialScale = super.efficiencyScale();
            // 热力学温度比例：0 (293K) ~ 1 (450K+)
            float thermalFrac = Math.max(0f, Math.min(1f,
                (thermal.getTemperatureK() - 293.15f) / (operatingMinK - 293.15f)));
            // 温度太低时最低保留 10% 效率（避免完全停摆）
            return officialScale * (0.1f + 0.9f * thermalFrac);
        }

        /**
         * 热力学自身的产热/耗热逻辑（由 ThermalSystem.update() Phase 2 调用）。
         * 保持原有行为不变。
         */
        @Override
        public void updateThermalLogic(float dt) {
            float temp = thermal.getTemperatureK();

            // 骨架版：不检查实际输入物品，模拟有输入
            boolean hasInput = true;

            if (hasInput && temp >= operatingMinK) {
                state = FurnaceState.WORKING;
                thermal.addHeat(-ratedHeat * dt);
            } else if (hasInput) {
                state = FurnaceState.PREHEATING;
            } else {
                state = FurnaceState.IDLING;
                thermal.addHeat(-idleLoss * dt);
            }
        }

        @Override
        public void display(Table table) {
            super.display(table);

            table.row();
            table.left().label(() -> "[orange]精炼炉[v0.2/HeatCrafter][]").padTop(4);
            table.row();
            table.left().label(() ->
                "温度: " + String.format("%.1f", thermal.getTemperatureK() - 273.15f) + "°C" +
                stateLabel()
            );
            table.row();
            table.left().label(() ->
                "官方热: " + String.format("%.1f", heat) + "/" + heatRequirement +
                " | 效率: " + String.format("%.0f%%", efficiencyScale() * 100)
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

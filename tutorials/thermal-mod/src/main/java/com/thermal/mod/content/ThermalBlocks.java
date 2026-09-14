package com.thermal.mod.content;

import com.thermal.mod.blocks.CoolingTower;
import com.thermal.mod.blocks.HeatConduit;
import com.thermal.mod.blocks.IndustrialBoiler;
import com.thermal.mod.blocks.RefineryFurnace;
import mindustry.world.blocks.defense.Wall;

import static mindustry.type.ItemStack.with;
import static mindustry.type.Category.crafting;
import static mindustry.type.Category.defense;

/**
 * 注册所有热力建筑。
 *
 * <p>Block 子类在构造时自动注册到内容系统中（Content.java:20-23）。
 * 只需声明为 public static 字段并在 load() 方法中实例化即可。</p>
 *
 * <p>v0.2：RefineryFurnace 从 Wall 改为 HeatCrafter，分类仍为 crafting。</p>
 */
public class ThermalBlocks {

    /** 工业锅炉：持续产热 + 温度达上限停机 + 回差重启 + 官方 HeatBlock */
    public static IndustrialBoiler industrialBoiler;

    /** 散热塔：高 airU + 地面效率系数 + maxHeatRate 上限 */
    public static CoolingTower coolingTower;

    /** 导热管：架空保温管道，低 airU + 小接触热阻 */
    public static HeatConduit heatConduit;

    /** 精炼炉：v0.2 正式集成 v160 HeatCrafter，官方热网桥接 */
    public static RefineryFurnace refineryFurnace;

    public static void load() {
        // 工业锅炉
        industrialBoiler = new IndustrialBoiler("industrial-boiler");
        industrialBoiler.requirements(crafting, with(mindustry.content.Items.copper, 50));
        industrialBoiler.health = 300;

        // 散热塔
        coolingTower = new CoolingTower("cooling-tower");
        coolingTower.requirements(crafting, with(mindustry.content.Items.copper, 30));
        coolingTower.health = 200;

        // 导热管
        heatConduit = new HeatConduit("heat-conduit");
        heatConduit.requirements(defense, with(mindustry.content.Items.copper, 2));
        heatConduit.health = 100;

        // 精炼炉（v0.2: HeatCrafter，仍用 crafting 分类）
        refineryFurnace = new RefineryFurnace("refinery-furnace");
        refineryFurnace.requirements(crafting, with(mindustry.content.Items.copper, 40));
        refineryFurnace.health = 250;
    }
}

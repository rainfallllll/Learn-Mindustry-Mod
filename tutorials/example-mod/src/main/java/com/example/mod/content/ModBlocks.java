package com.example.mod.content;

import mindustry.world.blocks.defense.Wall;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.defense;

/**
 * 注册所有自定义方块。
 *
 * <p>与 Item 一样，Block 子类在构造时自动注册到内容系统中。
 * 只需声明为 public static 字段并在 load() 方法中实例化即可。</p>
 */
public class ModBlocks {

    /** 普通示例墙 —— 模仿铜墙（Copper Wall）的方式，直接使用 Wall 类 */
    public static Wall exampleWall;

    /** 自定义墙体 —— 每次最多受到 1 点伤害 */
    public static MaxOneDamageWall maxOneDamageWall;

    public static void load() {
        // 1. 普通示例墙：直接使用 Wall 类，模仿铜墙的注册方式
        //    名称 "example-wall" 对应资源文件 sprites/blocks/example-wall.png
        exampleWall = new Wall("example-wall");
        // 设置建造需求：需要 exampleItem × 2，分类为防御类
        exampleWall.requirements(defense, with(ModItems.exampleItem, 2));
        // 设置血量（1x1 方块，铜墙为 250，这里设为 200）
        exampleWall.health = 200;

        // 2. 自定义墙体：使用 MaxOneDamageWall 类
        //    名称 "max-one-damage-wall" 对应资源文件 sprites/blocks/max-one-damage-wall.png
        maxOneDamageWall = new MaxOneDamageWall("max-one-damage-wall");
        maxOneDamageWall.requirements(defense, with(ModItems.exampleItem, 5));
        maxOneDamageWall.health = 1000;
    }
}

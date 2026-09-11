package com.example.mod.content;

import arc.graphics.Color;
import mindustry.type.Item;

/**
 * 注册所有自定义物品。
 *
 * <p>Mindustry 的 Content 类（如 Item、Block）在构造时会自动注册到内容系统中，
 * 无需手动调用注册方法。只需声明为 public static 字段并在静态代码块或 load() 方法中实例化即可。</p>
 */
public class ModItems {

    /** 示例物品 —— 一颗闪亮的蓝色晶体 */
    public static Item exampleItem;

    public static void load() {
        // 创建物品：名称为 "example-item"，颜色为天蓝色
        // Item 构造函数自动将物品注册到游戏内容系统
        exampleItem = new Item("example-item", Color.valueOf("4fc3f7"));
        // cost 字段表示该物品在建造消耗中的"权重"（默认 1.0）
        exampleItem.cost = 1.0f;
    }
}

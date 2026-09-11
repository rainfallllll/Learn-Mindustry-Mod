package com.example.mod;

import arc.util.Log;
import mindustry.mod.Mod;
import com.example.mod.content.ModItems;
import com.example.mod.content.ModBlocks;

/**
 * 模组主类 —— Mindustry 模组的入口点。
 *
 * <p>Mindustry 通过 mod.json 中的 "main" 字段找到本类，并实例化它。
 * 本类必须继承 {@link mindustry.mod.Mod}。</p>
 *
 * <p>生命周期：
 * <ol>
 *   <li>{@link #loadContent()} —— 游戏加载内容时调用，在此注册物品、方块等</li>
 *   <li>{@link #init()} —— 所有内容加载完毕后调用，可在此进行初始化逻辑</li>
 * </ol>
 * </p>
 */
public class ExampleModMain extends Mod {

    @Override
    public void loadContent() {
        // 注册自定义物品和方块
        ModItems.load();
        ModBlocks.load();

        Log.info("ExampleJavaMod: 内容加载完成!");
    }

    @Override
    public void init() {
        // 所有内容加载完毕后的初始化逻辑
        Log.info("ExampleJavaMod: 模组初始化完成! 欢迎使用示例模组~");
    }
}

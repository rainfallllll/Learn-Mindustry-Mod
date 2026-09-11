package com.thermal.mod;

import arc.Events;
import arc.util.Log;
import com.thermal.mod.content.ThermalBlocks;
import com.thermal.mod.core.EnvironmentTemperature;
import com.thermal.mod.core.ThermalSystem;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;

/**
 * 热力模组主类 —— Mindustry 模组入口。
 *
 * <p>生命周期：
 * <ol>
 *   <li>{@link #loadContent()} —— 注册建筑内容</li>
 *   <li>{@link #init()} —— 初始化 ThermalSystem + 注册事件监听</li>
 * </ol>
 * </p>
 */
public class ThermalModMain extends Mod {

    public static ThermalSystem thermalSystem;

    @Override
    public void loadContent() {
        ThermalBlocks.load();
        Log.info("ThermalMod: 内容加载完成");
    }

    @Override
    public void init() {
        thermalSystem = new ThermalSystem();

        // 注册 WorldLoadEvent：进图时预计算环境温度
        Events.on(EventType.WorldLoadEvent.class, e -> {
            EnvironmentTemperature.precompute();
            Log.info("ThermalMod: 环境温度预计算完成");
        });

        // 每 tick 驱动热力系统更新（仅服务端运行）
        Events.run(EventType.Trigger.update, () -> {
            if (!Vars.net.client()) {
                thermalSystem.update(1f);
            }
        });

        // 调试模式：-Dthermal.debug=true 时自动放置测试链并周期性打印温度
        if (System.getProperty("thermal.debug") != null) {
            ThermalDebug.install();
            Log.info("ThermalMod: 调试模式已启用");
        }

        Log.info("ThermalMod: 模组初始化完成");
    }
}

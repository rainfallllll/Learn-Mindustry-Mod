package com.thermal.mod;

import arc.Events;
import arc.util.Log;
import com.thermal.mod.blocks.RefineryFurnace.FurnaceBuild;
import com.thermal.mod.blocks.IndustrialBoiler.BoilerBuild;
import com.thermal.mod.content.ThermalBlocks;
import com.thermal.mod.core.ThermalBuilding;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.world.Tile;

/**
 * 游戏内运行调试器（仅服务端/headless）。
 *
 * <p>启用方式：JVM 启动参数加 {@code -Dthermal.debug=true}。</p>
 *
 * <p>v0.2 增强日志：
 * <ol>
 *   <li>每 60 tick 打印所有热力建筑温度（迭代1 升温曲线验证）</li>
 *   <li>精炼炉额外打印官方 heat / shouldConsume 状态（迭代2 HeatCrafter 桥接验证）</li>
 *   <li>确认 setBars() 不抛异常（迭代3 温度条验证）</li>
 * </ol>
 * </p>
 */
public class ThermalDebug {

    private static int tickCounter = 0;
    private static boolean loggedFurnaceHeat = false;

    public static void install() {
        Events.on(EventType.WorldLoadEvent.class, e -> {
            Log.info("ThermalMod[debug]: 世界加载，开始放置测试链");
            Tile origin = findArea();
            if (origin == null) {
                Log.warn("ThermalMod[debug]: 未找到足够大的空地，跳过自动放置");
                return;
            }
            try {
                // 线性布局（全部 1x1 紧密相邻）：锅炉 + 导热管 + 导热管 + 精炼炉
                place(ThermalBlocks.industrialBoiler, origin);
                place(ThermalBlocks.heatConduit, origin.nearby(1, 0));
                place(ThermalBlocks.heatConduit, origin.nearby(2, 0));
                place(ThermalBlocks.refineryFurnace, origin.nearby(3, 0));
                // 迭代2验证：在精炼炉正上方放第二个锅炉，直接作为官方 HeatBlock 邻居
                place(ThermalBlocks.industrialBoiler, origin.nearby(3, 1));
                // 全部放置后统一刷新邻居关系
                for (ThermalBuilding tb : ThermalModMain.thermalSystem.getBuildings()) {
                    tb.asBuilding().updateProximity();
                }
                Log.info("ThermalMod[debug]: 测试链已放置 @" + origin.x + "," + origin.y
                    + " 锅炉build=" + (origin.build != null ? origin.build.block.name : "null")
                    + " 精炼炉=" + (Vars.world.build(origin.x + 3, origin.y) != null ? Vars.world.build(origin.x + 3, origin.y).block.name : "null")
                    + " 官方热源(精炼炉上方)=" + (Vars.world.build(origin.x + 3, origin.y + 1) != null ? Vars.world.build(origin.x + 3, origin.y + 1).block.name : "null"));

                // 验证 setBars() 不抛异常（迭代3）
                try {
                    ThermalBlocks.industrialBoiler.listBars();
                    ThermalBlocks.heatConduit.listBars();
                    ThermalBlocks.refineryFurnace.listBars();
                    Log.info("ThermalMod[debug]: setBars() 验证通过 — "
                        + "锅炉bars=" + countBars(ThermalBlocks.industrialBoiler)
                        + " 导管bars=" + countBars(ThermalBlocks.heatConduit)
                        + " 精炼炉bars=" + countBars(ThermalBlocks.refineryFurnace));
                } catch (Throwable t) {
                    Log.err("ThermalMod[debug]: setBars() 异常", t);
                }
            } catch (Throwable t) {
                Log.err("ThermalMod[debug]: 放置异常", t);
            }
        });

        Events.run(EventType.Trigger.update, () -> {
            if (Vars.net.client()) return;
            tickCounter++;
            if (tickCounter % 60 == 0) {
                var buildings = ThermalModMain.thermalSystem.getBuildings();
                StringBuilder sb = new StringBuilder();
                sb.append("[debug] t=").append(tickCounter).append(" 活跃热力建筑=").append(buildings.size).append(": ");
                for (ThermalBuilding tb : buildings) {
                    Building b = tb.asBuilding();
                    float temp = tb.getThermal().getTemperatureK();
                    sb.append(String.format("[%s@%d,%d %.1fK p=%d]", b.block.name, b.tile.x, b.tile.y, temp, b.proximity.size));

                    // 迭代2 验证：精炼炉打印官方 heat / shouldConsume
                    if (b instanceof FurnaceBuild fb) {
                        sb.append(String.format(" {officialHeat=%.2f req=%.1f shouldConsume=%s effScale=%.2f state=%s}",
                            fb.heat, fb.heatRequirement(), fb.shouldConsume(),
                            fb.efficiencyScale(), fb.state));
                    }
                    // 迭代2 验证：锅炉打印 HeatBlock.heat()
                    if (b instanceof BoilerBuild bb) {
                        sb.append(String.format(" {heatBlock.heat=%.2f}", bb.heat()));
                    }
                }
                Log.info(sb.toString());

                // 首次检测到精炼炉 heat>0 时记录
                for (ThermalBuilding tb : buildings) {
                    if (tb.asBuilding() instanceof FurnaceBuild fb && fb.heat > 0f && !loggedFurnaceHeat) {
                        loggedFurnaceHeat = true;
                        Log.info("ThermalMod[验证] 迭代2桥接生效! t=" + tickCounter
                            + " 精炼炉官方heat=" + fb.heat
                            + " shouldConsume=" + fb.shouldConsume()
                            + " 热力学温度=" + String.format("%.1fK", fb.thermal.getTemperatureK()));
                    }
                }
            }
        });
    }

    private static int countBars(mindustry.world.Block block) {
        int count = 0;
        for (var b : block.listBars()) count++;
        return count;
    }

    /** 放置方块（带团队与默认配置）。setBlock 不会调用 placed()，需手动触发注册。 */
    private static void place(mindustry.world.Block block, Tile tile) {
        if (tile != null && tile.block() == Blocks.air) {
            tile.setBlock(block, mindustry.game.Team.sharded, 0);
            if (tile.build != null) {
                tile.build.placed();
                tile.build.updateProximity();
            }
        }
    }

    /** 在世界中心附近搜索 4x2 的连续空地 */
    private static Tile findArea() {
        int cx = Vars.world.width() / 2, cy = Vars.world.height() / 2;
        int search = Math.min(Vars.world.width(), Vars.world.height()) / 2;
        for (int r = 0; r < search; r++) {
            for (int y = Math.max(3, cy - r); y <= cy + r && y < Vars.world.height() - 3; y++) {
                for (int x = Math.max(3, cx - r); x <= cx + r && x < Vars.world.width() - 3; x++) {
                    Tile t = Vars.world.tile(x, y);
                    if (t == null || t.block() != Blocks.air) continue;
                    boolean ok = true;
                    outer:
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 4; dx++) {
                            Tile tt = Vars.world.tile(x + dx, y + dy);
                            if (tt == null || tt.block() != Blocks.air) { ok = false; break outer; }
                        }
                    }
                    if (ok) return t;
                }
            }
        }
        return null;
    }
}

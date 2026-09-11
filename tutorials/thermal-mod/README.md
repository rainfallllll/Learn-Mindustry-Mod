# Thermal Mod — 热力学系统 Mod

> 基于《热力学系统设计手册》的 Mindustry 热量交换模组
> 兼容 Mindustry v159.7+

## 简介

本模组实现了一套以**热量 Q 为唯一状态变量、温度 T=Q/C 为表观结果**的模块化热交换系统。核心特性：

- **双缓冲结算**：Phase 1 写 pendingDeltaQ，Phase 2 统一结算，消除遍历顺序依赖
- **惰性温度**：tempDirty 缓存，每节点每 tick 最多一次除法
- **建筑间独立热阻 R**：R_AB = R_A + R_B（串联），与环境传热系数独立
- **双轨环境温度表**：进图预计算，运行时 O(1) 查表
- **地块效率系数**：groundEfficiency 查表 + maxHeatRate 上限

## 建筑列表

| 建筑 | 类型 | 说明 | 状态 |
|:----|:----|:----|:----|
| 工业锅炉 (Industrial Boiler) | 产热 | 持续产热 + 温度达上限停机 + 回差重启 | 完整实现 |
| 散热塔 (Cooling Tower) | 散热 | 高 airU + 地面效率系数 + maxHeatRate 上限 | 完整实现 |
| 导热管 (Heat Conduit) | 传输 | 架空保温管道，低 airU + 小接触热阻 | 完整实现 |
| 精炼炉 (Refinery Furnace) | 耗热 | 恒温耗热 + operatingMinK 温区 + idleLoss 空载 | 骨架实现 |
| 温差发电机 (Thermal Generator) | 发电 | 温差驱动发电 | 后续扩展 |
| 档位热泵 (Heat Pump) | 搬运 | 双端热量搬运 + COP | 后续扩展 |

## 核心架构

```
com.thermal.mod/
├── ThermalModMain.java          # 模组主类
├── core/
│   ├── ThermalComponent.java     # 热力组件（storedHeat/pendingDeltaQ/惰性温度）
│   ├── ThermalSystem.java        # 双缓冲管理器（Phase1+Phase2）
│   ├── ThermalBuilding.java      # 热力建筑接口
│   └── EnvironmentTemperature.java  # 环境温度（材质温度表 + FloatArray）
├── content/
│   ├── ThermalBlocks.java        # 建筑注册
│   └── ThermalItems.java         # 物品注册（预留）
└── blocks/
    ├── IndustrialBoiler.java      # 工业锅炉
    ├── CoolingTower.java          # 散热塔
    ├── HeatConduit.java          # 导热管
    └── RefineryFurnace.java       # 精炼炉
```

## 构建方法

```bash
# 需要 JDK 17+
./gradlew jar
# 产物：build/libs/thermal-mod-0.1.0.jar
# 复制到 Mindustry mods 文件夹即可
```

## 技术验证

- 源码核验基于 Mindustry v159.7 (HEAD b3317f3)
- 所有类名/方法名/字段名均在源码中验证
- Trigger.update 每 tick 驱动（EventType.java:47）
- Building.proximity 邻居数组（BuildingComp.java:69）
- WorldLoadEvent 环境温度预计算（EventType.java:107）

## 已知限制

1. 本版本为最小可载入 mod，建筑贴图需后续添加
2. 精炼炉的物品生产逻辑未完整实现（骨架）
3. 温差发电机和热泵仅为类骨架
4. 物品级热力系统未实现
5. 高斯-赛德尔温度场迭代未实现（直接使用材质温度）

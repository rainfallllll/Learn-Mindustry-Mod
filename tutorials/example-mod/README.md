# Example Java Mod

一个用于学习 Mindustry Java 模组开发的示例模组项目，作为教程的配套代码。

## 项目简介

本模组演示了 Mindustry Java 模组开发的核心流程：

- 模组主类的编写（继承 `Mod`，覆写 `loadContent()` 和 `init()`）
- 自定义物品的注册（`Item`）
- 自定义方块的注册（`Wall`）
- 通过覆写 `handleDamage()` 方法实现自定义伤害逻辑

## 包含的内容

| 类型 | 名称 | 说明 |
|------|------|------|
| 物品 | `example-item` | 一颗天蓝色的示例物品 |
| 方块 | `example-wall` | 普通示例墙（1x1，血量 200） |
| 方块 | `max-one-damage-wall` | 自定义墙：每次最多受 1 点伤害（1x1，血量 1000） |

## 构建步骤

### 前置要求

- JDK 17 或更高版本
- Gradle（或使用项目自带的 Gradle Wrapper）

### 构建命令

```bash
# 在项目根目录下执行
./gradlew jar
```

构建完成后，产物位于：

```
build/libs/example-java-mod-1.0.0.jar
```

## 安装方式

1. 构建模组，生成 jar 文件
2. 找到 Mindustry 的 mods 文件夹：
   - **Windows**: `%AppData%\Mindustry\mods\`
   - **Linux**: `~/.local/share/Mindustry/mods/`
   - **macOS**: `~/Library/Application Support/Mindustry/mods/`
   - **Android**: `Android/data/io.anuke.mindustry/files/mods/`
3. 将生成的 jar 文件复制到 mods 文件夹
4. 启动 Mindustry，在模组列表中启用本模组

## 功能说明

### 普通示例墙 (`example-wall`)

- 1x1 防御方块
- 建造需求：example-item × 2
- 血量：200
- 行为与普通墙一致，用于演示最基本的方块注册方式

### 每次最多受 1 点伤害的墙 (`max-one-damage-wall`)

- 1x1 防御方块
- 建造需求：example-item × 5
- 血量：1000
- **核心特性**：通过覆写 `handleDamage(float amount)` 方法，将每次受到的伤害截断为 `Math.min(amount, 1f)`
- 受击时会闪烁蓝色光效

#### 实现原理

Mindustry 的伤害流程中，`BuildingComp.handleDamage(float amount)` 是伤害值的"过滤器"。
传入的 `amount` 是原始伤害，返回值才是实际扣除的血量。覆写此方法即可实现自定义伤害逻辑。

## 目录结构

```
example-mod/
├── build.gradle                          # Gradle 构建脚本
├── settings.gradle                       # Gradle 项目设置
├── gradle.properties                     # Gradle 属性配置
├── mod.json                              # 模组元数据（游戏识别用）
├── README.md                             # 本文件
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── example/
        │           └── mod/
        │               ├── ExampleModMain.java          # 模组主类
        │               ├── content/
        │               │   ├── ModBlocks.java           # 注册自定义方块
        │               │   └── ModItems.java           # 注册自定义物品
        │               └── blocks/
        │                   └── MaxOneDamageWall.java   # 自定义墙体
        └── resources/
            └── (sprites 等资源文件放这里)
```

## 资源文件说明

方块和物品的贴图（sprites）需要放在 `src/main/resources/` 目录下，对应路径为：

- 物品贴图: `sprites/items/example-item.png`
- 方块贴图: `sprites/blocks/example-wall.png`
- 方块贴图: `sprites/blocks/max-one-damage-wall.png`

贴图尺寸建议：物品 32x32，方块随大小调整（1x1 为 32x32）。

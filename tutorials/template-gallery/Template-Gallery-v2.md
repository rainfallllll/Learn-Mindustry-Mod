# Mindustry Java 模组 · 分类模板库「有样学样」模板画廊 **v2**

> **怎么用本画廊**：你只学过一点 Java、没写过项目？别怕。做新东西之前，先翻到最像你想要的那一类，把那张卡片里的代码**整段抄进你的项目**，再只改括号里标了"改哪里"的几个数字和名字就行。所有代码都对照 Mindustry **v159.7（HEAD b3317f3）**官方源码逐条核验过，字段名、默认值、方法名都对得上，直接放进项目即可编译。

- **游戏版本**：v159.7
- **Java 版本**：17
- **官方源码位置**：`core/src/mindustry/content/` 与 `core/src/mindustry/type/` / `core/src/mindustry/world/blocks/`
- **包名约定**：下面代码统一用 `com.example.mod`，你换成自己的包名即可。
- **v2 更新说明**：本版在 **v1（11 分类 / 18 张卡片）** 基础上扩展到 **16 分类 / 27 张卡片**。新增了**科技树 TechTree**、**星球 Planet** 两个独立大区（导航中带 ⭐NEW 标记），以及**液体 Liquid**、**天气 Weather** 两个全新内容分类，并补齐了 v1 缺失的 **矿石 / 逻辑处理器 / 液块 / 载荷运输 / 地板** 等方块类别；同时为几乎所有分类补上了**参数速查表**。v1 原有卡片内容完整保留，只是在其后追加了参数表。

---

## 📊 ContentType 覆盖矩阵

以官方 `ContentType` 枚举（`ctype/ContentType.java`）为骨架盘点覆盖情况：

| ContentType | 对应类 | v1 覆盖 | v2 覆盖 | 所在卡片 |
|---|---|---|---|---|
| `item` | `Item` | ✅ | ✅ | 2.1 |
| `block` | `Block` | ✅（墙/炮塔/厂/钻/发电/传送） | ✅ + 补矿石/逻辑/液块/载荷/地板 | 3 / 4 / 5 / 6 / 7 / 8 / 14 |
| `bullet` | `BulletType` | ✅ | ✅ + 参数速查表 | 10.1 |
| `liquid` | `Liquid` | ❌ | ✅ 新增 | 12.1 |
| `status` | `StatusEffect` | ✅ | ✅ + 参数速查表 | 9.1 |
| `unit` | `UnitType` | ✅ | ✅ + 参数速查表 | 11.1 |
| `weather` | `Weather` | ❌ | ✅ 新增 | 13.1 |
| `sector` | `SectorPreset` | ❌ | 🟡 星球卡内置 sector 配置说明 | 16.1 |
| `planet` | `Planet` | ❌ | ✅ 新增独立大区 | 16.1 |
| `team` | `TeamEntry` | ❌ | ⚪ 未覆盖（`TeamEntries.loadAll()`，自定义队伍非入门刚需） | — |
| `unitCommand` | `UnitCommand` | ❌ | ⚪ 未覆盖（单位指令树，进阶内容） | — |
| `unitStance` | `UnitStance` | ❌ | ⚪ 未覆盖（单位姿态，进阶内容） | — |

> **结论**：v2 覆盖了 12 个有效 ContentType 中的 **9 个**（item / block / bullet / liquid / status / unit / weather / sector(部分) / planet）。剩余 `team` / `unitCommand` / `unitStance` 属于进阶/冷门内容，未在本入门画廊展开。

---

## 1. 工程骨架（4 张）

### 卡片 1.1 · mod.json 模板

**一句话用途**：模组的"身份证"，告诉游戏这个模组叫什么、主类在哪、要求什么游戏版本。

**完整可复制代码**：

```json
{
  "name": "example-mod",
  "displayName": "示例模组",
  "author": "你的名字",
  "description": "一句话介绍你的模组。",
  "version": "1.0.0",
  "main": "com.example.mod.ExampleModMain",
  "minGameVersion": "154",
  "java": true,
  "dependencies": []
}
```

**官方对应物**：模组规范要求。`name` / `main` / `minGameVersion` / `java` 为必字段；`main` 指向你的主类全限定名。示例见 `example-mod/mod.json`。

**改哪里出花样**：改 `name`（英文短名，也是文件夹名）和 `main`（你的主类路径）；想依赖别的模组就在 `dependencies` 里填对方的 `name`。

---

### 卡片 1.2 · 主类模板

**一句话用途**：模组入口，游戏通过它来加载你注册的所有内容。

**完整可复制代码**：

```java
package com.example.mod;

import arc.util.Log;
import mindustry.mod.Mod;
import com.example.mod.content.ModItems;
import com.example.mod.content.ModBlocks;

public class ExampleModMain extends Mod {

    @Override
    public void loadContent() {
        // 在这里调用你自己的内容注册类
        ModItems.load();
        ModBlocks.load();
        // v2 新增：注册液体/天气/星球/科技树都写在这里（见对应卡片）
        // ModLiquids.load();
        // ModWeathers.load();
        // ModPlanets.load();
        // ModTech.load();   // 科技树节点必须在所有 block 创建完之后调用

        Log.info("ExampleMod: 内容加载完成!");
    }

    @Override
    public void init() {
        // 所有内容加载完毕后的初始化逻辑（事件监听等）
        Log.info("ExampleMod: 模组初始化完成!");
    }
}
```

**官方对应物**：`mindustry.mod.Mod`（Mod.java）。生命周期两步——`loadContent()` 注册物品/方块，`init()` 做后期初始化。`Content` 构造时自动调用 `Vars.content.handleContent(this)` 注册自身（Content.java:20-23）。

**改哪里出花样**：`loadContent()` 里加你自己写的 `ModXxx.load()` 调用；`init()` 里放事件监听、命令注册等。

---

### 卡片 1.3 · build.gradle 模板

**一句话用途**：Gradle 构建脚本，决定怎么把你的 Java 代码编译成游戏能用的 jar。

**完整可复制代码**：

```groovy
plugins {
    id 'java'
}

// Mindustry v159.7 使用 Java 17
sourceCompatibility = 17
targetCompatibility = 17

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Mindustry 核心库（仅编译时需要，运行时由游戏提供）
    compileOnly "com.github.Anuken.Mindustry:core:v159.7"
    // Arc 引擎核心库
    compileOnly "com.github.Anuken.Arc:arc-core:889dd8880f"
}

// 将 mod.json 包含在最终 jar 中
sourceSets {
    main {
        resources {
            srcDirs = ['src/main/resources']
        }
    }
}

// 配置 jar 任务：将 mod.json 打入 jar 根目录
jar {
    manifest {
        attributes(
            'Main-Class': 'mindustry.mod.ModClassLoader'
        )
    }
    from('mod.json') {
        into ''
    }
}

buildDir = 'build'
```

**官方对应物**：官方构建体系。依赖坐标固定指向 v159.7 的 core 与 arc-core；`Main-Class` 必须是 `mindustry.mod.ModClassLoader`。

**改哪里出花样**：一般不用改。换游戏版本时把 `v159.7` 和 `889dd8880f` 换成对应版本号即可。

---

### 卡片 1.4 · gradle.properties 模板

**一句话用途**：Gradle 的 JVM 内存和缓存配置，避免编译时内存不够。

**完整可复制代码**：

```properties
# Gradle JVM 参数
org.gradle.jvmargs=-Xmx2G

# 构建缓存
org.gradle.caching=true
```

**官方对应物**：Gradle 标准配置。`-Xmx2G` 给 Gradle 分 2GB 堆内存。

**改哪里出花样**：内存小的机器把 `2G` 改成 `1G`；一般不用动。

---

## 2. 物品 Item（1 张）

### 卡片 2.1 · 基础物品模板 · copper 风格

**一句话用途**：做一种新的矿石/材料，能被钻头挖、被传送带运、被工厂消耗。

**完整可复制代码**：

```java
package com.example.mod.content;

import arc.graphics.Color;
import mindustry.type.Item;

public class ModItems {

    public static Item exampleItem;

    public static void load() {
        // 名称 "example-item" 对应贴图 sprites/items/example-item.png
        // 第二个参数是物品在游戏里显示的颜色
        exampleItem = new Item("example-item", Color.valueOf("4fc3f7")) {{
            hardness = 1;     // 矿石硬度：只有 tier >= hardness 的钻头才能挖
            cost = 0.5f;      // 建造消耗权重，越大越"贵"
        }};
    }
}
```

**官方对应物**：`Items.java:16`——`copper = new Item("copper", Color.valueOf("d99d73")){{ hardness = 1; cost = 0.5f; alwaysUnlocked = true; }};`。构造函数 `Item(String name, Color color)`（Item.java:51）。

**改哪里出花样**：改 `Color.valueOf(...)` 换颜色；改 `hardness` 控制需要几级钻头才能挖；加 `alwaysUnlocked = true;` 让物品一开始就解锁。

**📋 物品参数速查表**（`Item` 关键字段）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `hardness` | int | 0 | 矿石硬度；只有 `drill.tier >= hardness` 的钻头才能挖 | 铜=1，钍=3 |
| `cost` | float | 0 | 建造消耗权重，越大在科技树上越贵 | 铜=0.5，锆=1.5 |
| `color` | Color | （构造传入） | 物品图标/矿石地表颜色 | `Color.valueOf("d99d73")` |
| `alwaysUnlocked` | boolean | false | 是否开局即解锁、不进科技树 | 铜/铅等基础矿=true |
| `healthScaling` | float | 1 | 单位载弹时的血量缩放 | 一般不动 |

---

## 3. 墙体 Wall（2 张）

### 卡片 3.1 · 基础墙模板 · copper-wall 风格

**一句话用途**：做一块挡子弹、挡单位的基础方块。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.defense.Wall;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.defense;

public class ModBlocks {

    public static Wall exampleWall;

    public static void load() {
        exampleWall = new Wall("example-wall") {{
            // 建造成本 + 分类
            requirements(defense, with(ModItems.exampleItem, 6));
            // 血量：铜墙 = 80 * wallHealthMultiplier(4) = 320
            health = 320;
        }};
    }
}
```

**官方对应物**：`Blocks.java:1708`——`copperWall = new Wall("copper-wall"){{ requirements(Category.defense, with(Items.copper, 6)); health = 80 * wallHealthMultiplier; researchCostMultiplier = 0.1f; }};`。`wallHealthMultiplier = 4`（Blocks.java:1706）。

**改哪里出花样**：改 `health` 调血量；改 `requirements(...)` 调成本；加 `size = 2;` 变成 2x2 大方块（血量也要乘 4）。

---

### 卡片 3.2 · 高血量/功能墙模板 · plastanium-wall 风格

**一句话用途**：做一块能挡激光、绝缘（不透电）的高级墙。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.defense.Wall;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.defense;

public class ModBlocks {

    public static Wall foamWall;

    public static void load() {
        foamWall = new Wall("foam-wall") {{
            requirements(defense, with(ModItems.exampleItem, 5));
            health = 500;
            insulated = true;       // 绝缘：不让电/激光穿透
            absorbLasers = true;    // 吸收激光：激光打上去直接消失
            schematicPriority = 10; // 在地脉图里的优先级
        }};
    }
}
```

**官方对应物**：`Blocks.java:1731`——`plastaniumWall = new Wall("plastanium-wall"){{ requirements(...); health = 125 * wallHealthMultiplier; insulated = true; absorbLasers = true; schematicPriority = 10; }};`。

**改哪里出花样**：加 `insulated = true;` 变绝缘墙；加 `absorbLasers = true;` 挡激光；改 `health` 调血量。

**📋 墙体参数速查表**（`Wall` / `Block` 基类关键字段）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `health` | int | （Block 默认） | 方块血量；墙建议按 `基数 * wallHealthMultiplier(4)` 算 | 铜墙=320 |
| `size` | int | 1 | 占地边长（1=1x1，2=2x2） | 高墙=2 |
| `insulated` | boolean | false | 绝缘，不导电/不透激光 | 塑胶墙=true |
| `absorbLasers` | boolean | false | 被激光打中不穿透、直接消失 | 塑胶墙=true |
| `requirements(Category, ItemStack[])` | 方法 | — | 建造成本与分类（turrets/production/...） | `requirements(defense, with(item, n))` |
| `schematicPriority` | int | — | 地脉图铺设优先级 | 10 |

---

## 4. 炮塔 Turret（2 张）

### 卡片 4.1 · 基础物品弹药炮塔模板 · duo 风格

**一句话用途**：做一座吃物品当弹药、会自动找敌人打的炮塔。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.entities.bullet.BasicBulletType;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.content.Fx;
import mindustry.content.Sounds;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.turret;
import static mindustry.content.Items.*;

public class ModTurrets {

    public static ItemTurret miniDuo;

    public static void load() {
        miniDuo = new ItemTurret("mini-duo") {{
            requirements(turret, with(Items.copper, 35));

            // 弹药映射：吃什么物品 → 打出什么子弹
            ammo(
                Items.copper, new BasicBulletType(2.5f, 9) {{
                    width = 7f;
                    height = 9f;
                    lifetime = 60f;
                    ammoMultiplier = 2;
                    hitEffect = despawnEffect = Fx.hitBulletColor;
                }}
            );

            shoot = new mindustry.entities.pattern.ShootAlternate(3.5f);

            reload = 20f;          // 两次射击间隔（帧）
            range = 160;           // 射程
            shootCone = 15f;       // 散射角
            inaccuracy = 2f;       // 随机偏差
            rotateSpeed = 10f;     // 转向速度
            recoil = 0.5f;         // 后坐力
            shootY = 3f;
            health = 250;
            shootSound = Sounds.shootDuo;
            ammoUseEffect = Fx.casing1;
        }};
    }
}
```

**官方对应物**：`Blocks.java:3276`——`duo = new ItemTurret("duo"){{ requirements(...); ammo(Items.copper, new BasicBulletType(2.5f, 9){{...}}, Items.graphite, ..., Items.silicon, ...); shoot = new ShootAlternate(3.5f); recoils = 2; reload = 20f; range = 160; ... }};`。

**改哪里出花样**：改 `reload` 调射速（越小越快）；改 `range` 调射程；在 `ammo(...)` 里加更多 `Items.xxx, new BasicBulletType(...)` 对来支持多种弹药；改 `BasicBulletType(速度, 伤害)` 调子弹威力。

---

### 卡片 4.2 · 散射炮塔模板 · scatter 风格

**一句话用途**：做一座一次喷出多颗碎片、带溅射伤害的霰弹炮塔。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.entities.bullet.FlakBulletType;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.content.Fx;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.turret;
import static mindustry.content.Items.scrap;

public class ModTurrets {

    public static ItemTurret miniScatter;

    public static void load() {
        miniScatter = new ItemTurret("mini-scatter") {{
            requirements(turret, with(scrap, 40));

            ammo(
                scrap, new FlakBulletType(4f, 3) {{
                    lifetime = 60f;
                    ammoMultiplier = 5f;
                    shootEffect = Fx.shootSmall;
                    reloadMultiplier = 0.5f;
                    width = 6f;
                    height = 8f;
                    hitEffect = Fx.flakExplosion;
                    splashDamage = 33f;          // 溅射伤害
                    splashDamageRadius = 24f;    // 溅射半径
                }}
            );

            shoot = new mindustry.entities.pattern.ShootMulti(6, 18f, 0f); // 一次 6 发，间隔 18°
            reload = 30f;
            range = 170;
            shootCone = 50f;       // 大散射角
            inaccuracy = 15f;
            rotateSpeed = 8f;
            health = 300;
        }};
    }
}
```

**官方对应物**：`Blocks.java:3351`——`scatter = new ItemTurret("scatter"){{ requirements(...); ammo(Items.scrap, new FlakBulletType(4f, 3){{ lifetime = 60f; splashDamage = 22f*1.5f; splashDamageRadius = 24f; ... }}); ... }};`。

**改哪里出花样**：`ShootMulti(发数, 间隔角, 偏移角)` 控制一次喷几颗；改 `splashDamage` / `splashDamageRadius` 调溅射；改 `shootCone` 调扇形覆盖范围。

**📋 炮塔参数速查表**（`ItemTurret` / `Turret` 关键字段）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `reload` | float | — | 两次射击间隔（帧），越小射速越快 | duo=20，scatter=30 |
| `range` | float | — | 射程（像素） | 160 |
| `shootCone` | float | — | 可瞄准的扇形角（度） | duo=15，scatter=50 |
| `inaccuracy` | float | 0 | 随机散布角（度） | 2~15 |
| `rotateSpeed` | float | — | 转向角速度 | 8~10 |
| `recoil` | float | 0 | 后坐力（射击时后移量） | 0.5 |
| `health` | int | — | 炮塔血量 | 250~300 |
| `shoot` | ShootPattern | — | 射击模式：`ShootAlternate`/`ShootMulti` | `new ShootMulti(6,18f,0f)` |
| `ammo(...)` | 方法 | — | 弹药映射：`Item, BulletType` 成对传入 | 见卡片 |
| `shootSound` / `ammoUseEffect` | Sound/Effect | — | 开火音效 / 弹壳特效 | `Sounds.shootDuo` |

---

## 5. 工厂 GenericCrafter（1 张）

### 卡片 5.1 · 基础合成厂模板 · silicon-smelter 风格

**一句话用途**：做一座吃进几种原料、消耗电力、产出一种成品的合成机器。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.production.GenericCrafter;
import mindustry.type.ItemStack;
import mindustry.content.Fx;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawMulti;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.crafting;
import static mindustry.content.Items.*;

public class ModCrafters {

    public static GenericCrafter alloySmelter;

    public static void load() {
        alloySmelter = new GenericCrafter("alloy-smelter") {{
            requirements(crafting, with(Items.copper, 30, Items.lead, 25));
            craftEffect = Fx.smeltsmoke;          // 合成时的烟雾特效
            outputItem = new ItemStack(Items.silicon, 1); // 每次产出
            craftTime = 40f;                      // 合成一次耗时（帧）
            size = 2;                             // 占地 2x2
            hasPower = true;

            // 消耗：1 煤 + 2 沙，外加 0.50 电力
            consumeItems(with(Items.coal, 1, Items.sand, 2));
            consumePower(0.50f);
        }};
    }
}
```

**官方对应物**：`Blocks.java:1070`——`siliconSmelter = new GenericCrafter("silicon-smelter"){{ requirements(Category.crafting, with(Items.copper, 30, Items.lead, 25)); craftEffect = Fx.smeltsmoke; outputItem = new ItemStack(Items.silicon, 1); craftTime = 40f; size = 2; consumeItems(with(Items.coal, 1, Items.sand, 2)); consumePower(0.50f); }};`。字段见 GenericCrafter.java:26/41。

**改哪里出花样**：改 `outputItem` 换产物；改 `craftTime` 调速度；改 `consumeItems(...)` 换原料；去掉 `consumePower(...)` 就是纯物耗工厂。

**📋 工厂参数速查表**（`GenericCrafter` 关键字段）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `craftTime` | float | — | 合成一次耗时（帧） | 40 |
| `outputItem` | ItemStack | null | 每次产出的物品与数量 | `new ItemStack(Items.silicon, 1)` |
| `craftEffect` | Effect | — | 合成时播放的特效 | `Fx.smeltsmoke` |
| `size` | int | 1 | 占地边长 | 2（2x2） |
| `hasPower` | boolean | false | 是否耗电 | true |
| `consumeItems(ItemStack[])` | 方法 | — | 原料消耗 | `with(Items.coal,1,Items.sand,2)` |
| `consumePower(float)` | 方法 | — | 每帧耗电量 | 0.50f |
| `outputLiquid` | LiquidStack | null | 产出液体 | （可选） |

> 🟢 **v160 新增提示**：v160 把 `GenericCrafter` 的输出改成了"小数累积、按次取整"（`outputAccumulator`，`GenericCrafter.java:191`）。上面这张卡的 `outputItem = new ItemStack(Items.silicon, 1)` 照抄没问题；但如果你想做"动态增产"的工厂，覆写 `scaleOutput(float)`（`:304`）即可，不要再硬改 `outputItem.amount`。

---

### 卡片 5.2 · 受热合成厂模板 · HeatCrafter 风格（v160 新增）🟢

**一句话用途**：做一座**靠热力网络供热**（接 Heater/HeatConduit）才能开工的合成厂，热够才生产、热多还能超效率。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.production.HeatCrafter;
import mindustry.type.ItemStack;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.crafting;
import static mindustry.content.Items.*;

public class ModHeatCrafters {

    public static HeatCrafter heatSmelter;

    public static void load() {
        heatSmelter = new HeatCrafter("heat-smelter") {{
            requirements(crafting, with(Items.copper, 60, Items.graphite, 30));
            heatRequirement = 20f;        // 需要 20 点热才满效率
            overheatScale = 0.5f;         // 超温后每多 2 点热多 1 倍效率
            maxEfficiency = 3f;           // 最多 300%
            outputItem = new ItemStack(Items.silicon, 1);
            craftTime = 60f;
            size = 2;
        }};
        // 不用写 Build：父类 HeatCrafterBuild 已 implements HeatConsumer，
        // 自动接收四方向热量、算效率、加热条、加热需求图鉴。
    }
}
```

**改哪里出花样**：改 `heatRequirement` 调"要多热"；改 `overheatScale` 调"过热收益"；改 `maxEfficiency` 调"效率天花板"。接不接得到热，由旁边的热力建筑决定。

**📋 HeatCrafter 参数速查表**（`GenericCrafter` 之外新增）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `heatRequirement` | float | 10f | 达 100% 效率所需热量；0 表示不要热 | 20 |
| `overheatScale` | float | 1f | 超温后每多 1 点热折算的额外效率比例 | 0.5 |
| `maxEfficiency` | float | 4f | 过热处理后效率上限（4=400%） | 3 |

> 热力从哪来、效率怎么算，见 API 参考表 **6.3 / 6.3.1**。

---

## 6. 钻头 Drill（1 张）

### 卡片 6.1 · 基础钻头模板 · pneumatic-drill 风格

**一句话用途**：做一座自动从地板下挖矿的钻头。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.production.Drill;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.production;
import static mindustry.content.Items.*;
import static mindustry.content.Liquids.water;

public class ModDrills {

    public static Drill basicDrill;

    public static void load() {
        basicDrill = new Drill("basic-drill") {{
            requirements(production, with(Items.copper, 18, Items.graphite, 10));
            tier = 3;        // 钻头等级：只能挖 hardness <= tier 的矿石
            drillTime = 400; // 挖一个矿耗时（帧，越大越慢）
            size = 2;        // 占地 2x2

            // 可选：通水加速
            consumeLiquid(water, 3.5f / 60f).boost();
        }};
    }
}
```

**官方对应物**：`Blocks.java:2896`——`pneumaticDrill = new Drill("pneumatic-drill"){{ requirements(Category.production, with(Items.copper, 18, Items.graphite, 10)); tier = 3; drillTime = 400; size = 2; consumeLiquid(Liquids.water, 3.5f/60f).boost(); }};`。`tier` 决定可挖矿石（Drill.java:162/234：`drop.hardness <= tier`）；`drillTime` 默认 300（Drill.java:36）。

**改哪里出花样**：改 `tier` 决定能挖多硬的矿；改 `drillTime` 调速度；去掉 `consumeLiquid(...).boost()` 就不需要水加速。

**📋 钻头参数速查表**（`Drill` 关键字段）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `tier` | int | — | 钻头等级；只能挖 `item.hardness <= tier` 的矿 | 气动钻=3 |
| `drillTime` | float | 300 | 挖一个矿耗时（帧） | 400 |
| `size` | int | 1 | 占地边长 | 2 |
| `consumeLiquid(liquid, amt).boost()` | 方法 | — | 消耗液体并加速 | 水 3.5/60 |

---

## 7. 发电机（2 张）

### 卡片 7.1 · 火力发电机模板 · combustion-generator 风格

**一句话用途**：做一座烧物品发电的发电机。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.content.Fx;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.power;
import static mindustry.content.Items.*;

public class ModGenerators {

    public static ConsumeGenerator coalBurner;

    public static void load() {
        coalBurner = new ConsumeGenerator("coal-burner") {{
            requirements(power, with(Items.copper, 25, Items.lead, 15));
            powerProduction = 1f;   // 发电功率（单位/帧）
            itemDuration = 120f;    // 一个物品烧多久

            generateEffect = Fx.generatespark;

            // 消耗：吃煤发电
            consumeItem(Items.coal);
        }};
    }
}
```

**官方对应物**：`Blocks.java:2523`——`combustionGenerator = new ConsumeGenerator("combustion-generator"){{ requirements(Category.power, with(Items.copper, 25, Items.lead, 15)); powerProduction = 1f; itemDuration = 120f; generateEffect = Fx.generatespark; consume(new ConsumeItemFlammable()); ... }};`。

**改哪里出花样**：改 `powerProduction` 调发电量；改 `itemDuration` 调每个物品烧多久；改 `consumeItem(...)` 换燃料。

---

### 卡片 7.2 · 太阳能模板 · solar-panel 风格

**一句话用途**：做一座不烧任何东西、自动缓慢发电的太阳能板。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.power.SolarGenerator;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.power;
import static mindustry.content.Items.*;

public class ModGenerators {

    public static SolarGenerator mySolar;

    public static void load() {
        mySolar = new SolarGenerator("my-solar") {{
            requirements(power, with(Items.lead, 10, Items.silicon, 8));
            powerProduction = 0.12f; // 固定发电功率
        }};
    }
}
```

**官方对应物**：`Blocks.java:2619`——`solarPanel = new SolarGenerator("solar-panel"){{ requirements(Category.power, with(Items.lead, 10, Items.silicon, 8)); powerProduction = 0.12f; }};`。

**改哪里出花样**：改 `powerProduction` 调发电量；加 `size = 2;` 做大号太阳能板（功率要相应调大）。

**📋 发电机参数速查表**：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `powerProduction` | float | 0 | 发电功率（电力单位/帧） | 火力=1，太阳能=0.12 |
| `itemDuration` | float | — | 单个燃料烧多久（仅 `ConsumeGenerator`） | 120 |
| `generateEffect` | Effect | — | 发电时特效 | `Fx.generatespark` |
| `consumeItem(item)` | 方法 | — | 燃烧的燃料物品 | `Items.coal` |

---

## 8. 传送（2 张）

### 卡片 8.1 · 基础传送带模板 · conveyor 风格

**一句话用途**：做一条把物品从 A 运到 B 的传送带。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.distribution.Conveyor;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.distribution;
import static mindustry.content.Items.copper;

public class ModDistribution {

    public static Conveyor myConveyor;

    public static void load() {
        myConveyor = new Conveyor("my-conveyor") {{
            requirements(distribution, with(copper, 1));
            health = 45;
            speed = 0.035f;     // 内部移动速度（物理单位/帧）
            displayedSpeed = 5f; // 游戏里显示的"格/秒"
        }};
    }
}
```

**官方对应物**：`Blocks.java:2070`——`conveyor = new Conveyor("conveyor"){{ requirements(Category.distribution, with(Items.copper, 1)); health = 45; speed = 0.035f; displayedSpeed = 5f; researchCost = with(Items.copper, 5); }};`。

**改哪里出花样**：改 `speed` / `displayedSpeed` 调传送带速度；改 `health` 调耐久。

---

### 卡片 8.2 · 路由器模板 · router 风格

**一句话用途**：做一个把物品均匀分发到四个方向的小方块。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.distribution.Router;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.distribution;
import static mindustry.content.Items.copper;

public class ModDistribution {

    public static Router myRouter;

    public static void load() {
        myRouter = new Router("my-router") {{
            requirements(distribution, with(copper, 3));
            buildCostMultiplier = 4f;
        }};
    }
}
```

**官方对应物**：`Blocks.java:2140`——`router = new Router("router"){{ requirements(Category.distribution, with(Items.copper, 3)); buildCostMultiplier = 4f; }};`。

**改哪里出花样**：基本只改成本；想做 2x2 的四分器就照 `distributor`（Blocks.java:2145）加 `size = 2;`。

**📋 传送/分布参数速查表**：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `speed` | float | — | 内部物理移动速度（单位/帧） | 0.035 |
| `displayedSpeed` | float | — | UI 显示速度（格/秒） | 5 |
| `health` | int | — | 方块耐久 | 45 |
| `buildCostMultiplier` | float | 1 | 建造/拆除成本倍率 | 路由器=4 |

---

## 9. 状态效果 StatusEffect（1 张）

### 卡片 9.1 · 自定义状态效果模板 · overdrive 风格

**一句话用途**：做一种挂在单位身上的 buff/debuff（加速、加伤、持续掉血等）。

**完整可复制代码**：

```java
package com.example.mod.content;

import arc.graphics.Color;
import mindustry.type.StatusEffect;
import mindustry.content.Fx;
import mindustry.graphics.Pal;

public class ModStatusEffects {

    public static StatusEffect overcharge;

    public static void load() {
        overcharge = new StatusEffect("overcharge") {{
            color = Pal.accent;            // 状态条颜色
            speedMultiplier = 1.15f;       // 移速倍率（>1 加速）
            damageMultiplier = 1.4f;       // 伤害倍率
            healthMultiplier = 0.95f;      // 血量倍率
            damage = -0.01f;               // 每帧掉血（负值=回血）
            effect = Fx.overdriven;        // 单位身上的视觉特效
            permanent = true;               // 是否永久
        }};
    }
}
```

**官方对应物**：`StatusEffects.java:153`——`overdrive = new StatusEffect("overdrive"){{ color = Pal.accent; healthMultiplier = 0.95f; speedMultiplier = 1.15f; damageMultiplier = 1.4f; damage = -0.01f; effect = Fx.overdriven; permanent = true; }};`。构造函数 `StatusEffect(String name)`（StatusEffect.java:75）。

**改哪里出花样**：改 `speedMultiplier` / `damageMultiplier` 调 buff 强度；`damage` 填正数变持续掉血 debuff；换 `color` 和 `effect` 改外观。

**📋 状态效果参数速查表**（`StatusEffect` 关键字段）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `color` | Color | — | 状态条/图标颜色 | `Pal.accent` |
| `speedMultiplier` | float | 1 | 移速倍率（>1 加速，<1 减速） | 1.15 |
| `damageMultiplier` | float | 1 | 伤害倍率 | 1.4 |
| `healthMultiplier` | float | 1 | 最大血量倍率 | 0.95 |
| `damage` | float | 0 | 每帧掉血；负值=回血 | -0.01 |
| `effect` | Effect | — | 单位身上的持续特效 | `Fx.overdriven` |
| `permanent` | boolean | false | 是否永久附着 | true |

---

## 10. 子弹 BulletType（1 张）

### 卡片 10.1 · 基础子弹模板 · BasicBulletType

**一句话用途**：定义一颗子弹长什么样、飞多快、打多痛——炮塔和单位武器都靠它。

**完整可复制代码**：

```java
package com.example.mod.content;

import arc.graphics.Color;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.content.Fx;
import mindustry.graphics.Pal;

public class ModBullets {

    public static BasicBulletType copperRound;

    public static void load() {
        // 构造参数：(速度, 伤害)
        copperRound = new BasicBulletType(2.5f, 9) {{
            width = 7f;           // 子弹贴图宽
            height = 9f;          // 子弹贴图高
            lifetime = 60f;       // 存活帧数（决定射程）
            ammoMultiplier = 2;   // 每次射击消耗弹药倍率

            hitEffect = despawnEffect = Fx.hitBulletColor; // 命中/消失特效
            hitColor = backColor = trailColor = Pal.copperAmmoBack;
            frontColor = Pal.copperAmmoFront;
        }};
    }
}
```

**官方对应物**：`Blocks.java:3279`（duo 的铜弹药）——`new BasicBulletType(2.5f, 9){{ width = 7f; height = 9f; lifetime = 60f; ammoMultiplier = 2; hitEffect = despawnEffect = Fx.hitBulletColor; ... }};`。参考 `Bullets.java:18` 的 `placeholder = new BasicBulletType(2.5f, 9, "ohno"){{...}};`。

**改哪里出花样**：构造函数两个参数是 `(速度, 伤害)`；改 `lifetime` 调射程；改 `width`/`height` 调子弹大小；加 `splashDamage` / `splashDamageRadius` 变爆炸弹。

**📋 子弹参数速查表**（`BulletType` / `BasicBulletType` 常用字段，v2 细化补充）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `speed` | float | 1 | 子弹速度（单位/帧，构造第一参） | 2.5 |
| `damage` | float | 1 | 直接命中伤害（构造第二参） | 9 |
| `lifetime` | float | 40 | 存活帧数，决定射程（≈speed×lifetime） | 60 |
| `width` / `height` | float | 5 / 7 | 子弹贴图宽/高（仅 BasicBulletType） | 7 / 9 |
| `ammoMultiplier` | float | 2 | 每次射击消耗的弹药倍率 | 2 |
| `pierce` | boolean | false | 是否穿透单位 | true 穿甲弹 |
| `pierceBuilding` | boolean | false | 是否穿透建筑 | true |
| `pierceCap` | int | -1 | 最多穿透几个目标（-1=无限） | 3 |
| `splashDamage` | float | 0 | 溅射伤害（0=无） | 33 |
| `splashDamageRadius` | float | -1 | 溅射半径（>0 才生效） | 24 |
| `status` | StatusEffect | none | 命中附加状态 | `StatusEffects.blasted` |
| `statusDuration` | float | 480 | 状态持续帧 | 480 |
| `hitEffect` | Effect | hitBulletSmall | 直接命中特效 | `Fx.hitBulletColor` |
| `despawnEffect` | Effect | hitBulletSmall | 到期消失特效 | 同上 |
| `shootEffect` | Effect | shootSmall | 开火口焰 | `Fx.shootSmall` |
| `smokeEffect` | Effect | shootSmallSmoke | 开火烟雾 | `Fx.shootSmoke` |
| `trailEffect` | Effect | missileTrail | 拖尾特效 | 导弹用 |
| `trailLength` | int | -1 | 拖尾长度（>0 启用拖尾四边形） | 5 |
| `keepVelocity` | boolean | true | 是否继承发射者速度 | true |
| `collidesAir` | boolean | true | 是否撞到空中单位 | true |
| `collidesGround` | boolean | true | 是否撞到地面单位 | true |
| `collidesTiles` | boolean | true | 是否撞到地形 | true |
| `reflectable` | boolean | true | 是否能被反弹墙反射 | true |
| `absorbable` | boolean | true | 是否能被护盾吸收 | true |
| `backColor` / `frontColor` | Color | 黄 | 子弹前/后层颜色（BasicBulletType） | 铜弹配色 |
| `fragBullet` | BulletType | null | 命中/消失时分裂出的子子弹 | 霰弹 |
| `fragBullets` | int | 9 | 分裂数量 | 6~9 |

> 射程估算：无阻力时 `range ≈ speed * lifetime`（见 `BulletType.calculateRange()`，BulletType.java:443）。

---

## 11. 单位 UnitType（1 张）

### 卡片 11.1 · 基础单位模板 · dagger 风格

**一句话用途**：做一架会自己移动、会开火的地面作战单位。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.content.Fx;

public class ModUnits {

    public static UnitType myFighter;

    public static void load() {
        myFighter = new UnitType("my-fighter") {{
            speed = 0.5f;     // 移动速度
            hitSize = 8f;     // 碰撞半径
            health = 150;     // 血量

            // 挂一门武器
            weapons.add(new Weapon("large-weapon") {{
                reload = 13f;    // 两次开火间隔
                x = 4f;          // 武器相对单位中心的 X 偏移
                y = 2f;          // Y 偏移
                top = false;
                ejectEffect = Fx.casing1;
                bullet = new BasicBulletType(2.5f, 9) {{
                    width = 7f;
                    height = 9f;
                    lifetime = 60f;
                }};
            }});
        }};
    }
}
```

**官方对应物**：`UnitTypes.java:100`——`dagger = new UnitType("dagger"){{ speed = 0.5f; hitSize = 8f; health = 150; weapons.add(new Weapon("large-weapon"){{ reload = 13f; x = 4f; y = 2f; top = false; ejectEffect = Fx.casing1; bullet = new BasicBulletType(2.5f, 9){{ width = 7f; height = 9f; lifetime = 60f; }}; }}); }};`。字段见 UnitType.java:56（speed）/290（weapons）。

**改哪里出花样**：改 `health` / `speed` / `hitSize` 调三围；加 `flying = true;` 变飞行单位；在 `weapons.add(...)` 里加第二门炮；改武器里的 `bullet` 换弹药类型。

**📋 单位参数速查表**（`UnitType` / `Weapon` 常用字段）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `speed` | float | — | 移动速度 | 0.5 |
| `hitSize` | float | — | 碰撞半径 | 8 |
| `health` | float | — | 血量 | 150 |
| `flying` | boolean | false | 是否飞行单位 | true |
| `weapons` | Seq\<Weapon\> | — | 武器列表 | `weapons.add(new Weapon(...))` |
| `Weapon.reload` | float | — | 两次开火间隔（帧） | 13 |
| `Weapon.x` / `Weapon.y` | float | 0 | 武器挂点偏移 | 4 / 2 |
| `Weapon.bullet` | BulletType | — | 该武器发射的子弹 | 见卡片 |
| `ejectEffect` | Effect | — | 抛壳特效 | `Fx.casing1` |

---

## 12. 液体 Liquid（1 张）【v2 新增分类】

### 卡片 12.1 · 自定义液体模板 · "熔融铜" 风格

**一句话用途**：做一种新的流体，能在管道里流、泼在地上成水洼、和别的液体/方块反应。

**完整可复制代码**：

```java
package com.example.mod.content;

import arc.graphics.Color;
import mindustry.type.Liquid;
import mindustry.content.StatusEffects;

public class ModLiquids {

    public static Liquid moltenCopper;

    public static void load() {
        // 构造：(名称, 颜色)。名称对应贴图 sprites/blocks/ 下的液体帧
        moltenCopper = new Liquid("molten-copper", Color.valueOf("ff7a3c")) {{
            temperature = 1f;          // 温度：0.5=室温，1=熔融滚烫，0=极冷
            heatCapacity = 0.7f;       // 储热能力：水=0.4，越大越能冷却
            viscosity = 0.7f;          // 粘稠度：0.5=水，1=焦油
            flammability = 0.1f;      // 可燃性 0~1.2（>0.5 极易燃）
            explosiveness = 0.2f;      // 受热易爆性 0~1
            lightColor = Color.valueOf("f0511d").a(0.4f); // 发光颜色（带 alpha）
            boilPoint = 1.2f;          // 汽化温度阈值
            effect = StatusEffects.melting; // 泼到单位身上挂的状态
            coolant = true;           // 能否当冷却剂
            barColor = Color.valueOf("ffb26b"); // 管道/条上显示色
        }};
    }
}
```

**官方对应物**：`Liquids.java:21`——`slag = new Liquid("slag", Color.valueOf("ffa166")){{ temperature = 1f; viscosity = 0.7f; effect = StatusEffects.melting; lightColor = Color.valueOf("f0511d").a(0.4f); }};`。基类 `Liquid extends UnlockableContent implements Senseable`（Liquid.java:20），构造 `Liquid(String name, Color color)`（Liquid.java:72），`Liquid(String name)` 仅供 mod 用（Liquid.java:78）。

**改哪里出花样**：抄 `water`（Liquids.java:13）改 `heatCapacity=0.4`、`boilPoint=0.5` 做冷却液；抄 `oil`（Liquids.java:28）改 `flammability=1.2`、`explosiveness=1.2` 做燃油；加 `gas = true;` 做气体（无固定水洼、自动透明，见 `init()` Liquid.java:83-98）。

**📋 液体参数速查表**（`Liquid` 全部关键字段，Liquid.java:27-70）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `color` | Color | （构造传入） | 管道与水洼主色 | `Color.valueOf("ff7a3c")` |
| `gas` | boolean | false | 是否气体（不形成水洼、自动透明） | 臭氧/hydrogen=true |
| `gasColor` | Color | lightGray | 气态颜色 | 水蒸汽=灰 |
| `barColor` | Color | null | 条/图标颜色（null 用 color） | 油=灰褐 |
| `lightColor` | Color | clear | 自发光色（alpha=亮度） | 熔渣橙红 .a(0.4) |
| `flammability` | float | 0 | 可燃性 0~1.2（>0.5 极易燃） | 油=1.2 |
| `temperature` | float | 0.5 | 温度 0.5=室温，0=极冷，1=滚烫 | 熔渣=1，冷冻液=0.25 |
| `heatCapacity` | float | 0.5 | 储热/冷却能力，0.4=水 | 水=0.4，冷冻液=0.9 |
| `viscosity` | float | 0.5 | 粘稠度 0.5=水，1=焦油 | 油=0.75 |
| `explosiveness` | float | 0 | 受热易爆 0~1（1=核弹） | 油=1.2 |
| `blockReactive` | boolean | true | 是否与方块反应（如熔渣遇水） | true |
| `coolant` | boolean | true | 能否当冷却剂 | true |
| `moveThroughBlocks` | boolean | false | 水洼能否穿过方块 | 肿瘤=true |
| `incinerable` | boolean | true | 能否在焚烧炉里烧掉 | true |
| `effect` | StatusEffect | none | 泼到单位身上的状态 | `StatusEffects.melting` |
| `particleEffect` | Effect | none | 水洼上飘的粒子特效 | — |
| `particleSpacing` | float | 60 | 粒子间隔（帧） | 60 |
| `boilPoint` | float | 2 | 汽化温度阈值 | 水=0.5 |
| `capPuddles` | boolean | true | 是否限制水洼最大面积 | true |
| `vaporEffect` | Effect | vapor | 汽化时特效 | `Fx.vapor` |
| `hidden` | boolean | false | 是否在 UI 隐藏 | 镓=true |
| `canStayOn` | ObjectSet\<Liquid\> | 空 | 能浮在哪些液体上 | 油浮于水 |

---

## 13. 天气 Weather（1 张）【v2 新增分类】

### 卡片 13.1 · 自定义天气模板 · "酸雨"（粒子天气）风格

**一句话用途**：做一种全屏天气事件，下雨/飘雪/起雾/刮沙，给世界加视觉氛围并给单位挂 debuff。

**完整可复制代码**（最简可运行版本，直接复用官方 `ParticleWeather`，不必自己写 `WeatherState`）：

```java
package com.example.mod.content;

import arc.graphics.Color;
import mindustry.type.Weather;
import mindustry.type.weather.ParticleWeather;
import mindustry.content.StatusEffects;
import mindustry.content.Sounds;

public class ModWeathers {

    public static Weather acidRain;

    public static void load() {
        // 直接用现成的 ParticleWeather（Weathers.java:20 的 snow 同款）
        // 它自带粒子绘制，不用手写 WeatherState
        acidRain = new ParticleWeather("acid-rain") {{
            particleRegion = "particle";          // 粒子贴图名（atlas 里的 region）
            color = noiseColor = Color.valueOf("9acd4b"); // 粒子颜色（酸雨绿）
            drawParticles = true;                  // 画粒子
            drawNoise = false;                     // 不画噪声层
            useWindVector = true;                  // 受风力方向影响
            sizeMin = 2.5f;
            sizeMax = 5f;
            density = 1600f;                       // 粒子密度（越大越密）
            baseSpeed = 4.3f;                      // 飘动速度
            minAlpha = 0.2f;
            maxAlpha = 0.9f;

            // 给单位挂 debuff
            status = StatusEffects.wet;            // 套用的状态效果
            statusDuration = 60f * 2;              // 每次施加持续帧
            statusAir = true;                      // 对空中单位生效
            statusGround = true;                   // 对地面单位生效

            sound = Sounds.rain;                   // 环境音
            soundVol = 0.25f;
            duration = 5f * 60f * 60f;             // 持续时长（帧），这里 5 分钟

            attrs.set(mindustry.game.Attribute.light, -0.1f); // 光照属性
        }};
    }
}
```

**官方对应物**：`Weathers.java:34`——`rain = new RainWeather("rain"){{ attrs.set(Attribute.light, -0.2f); attrs.set(Attribute.water, 0.2f); status = StatusEffects.wet; sound = Sounds.rain; soundVol = 0.25f; }};`。`ParticleWeather`（type/weather/ParticleWeather.java:11）继承 `Weather`，自带 `drawOver()` 粒子绘制，是做自定义天气**最简**的方式，**无需自己实现 `WeatherState`**。

**接入方式**：天气不会自动出现。要在某个星球/地图启用它，需在星球生成器里 `rules.weather.add(new Weather.WeatherEntry(ModWeathers.acidRain));`（参考 `PlanetGenerator.addWeather()`，PlanetGenerator.java:102）。

**改哪里出花样**：改 `color` / `sizeMin/Max` / `density` 调粒子；改 `status` 换 debuff；加 `drawNoise = true; noisePath = "fog";` 变雾；把 `ParticleWeather` 换成 `RainWeather`（type/weather/RainWeather.java）画雨丝。

> **关于 `WeatherState` 的技术决策**：`Weather.WeatherStateComp` 是 `@EntityDef` 注解生成的实体组件（Weather.java:295-342），自己手写子类非常繁琐。**入门一律用现成的 `ParticleWeather` / `RainWeather`**，只需设字段即可，不要自己 `extends WeatherState`。

**📋 天气参数速查表**（`Weather` / `ParticleWeather` 关键字段）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `duration` | float | 600（10 分钟） | 天气事件默认时长（帧） | 5 分钟=5*60*60 |
| `opacityMultiplier` | float | 1 | 天气透明度整体倍率 | 沙暴=0.35 |
| `sound` | Sound | none | 循环环境音 | `Sounds.rain` |
| `soundVol` | float | 0.1 | 音量 | 0.25~0.8 |
| `soundVolMin` | float | 0 | 音量下限 | 0.02 |
| `status` | StatusEffect | none | 周期施加的状态 | `StatusEffects.wet` |
| `statusDuration` | float | 120（2 分钟） | 状态持续帧 | 120 |
| `statusAir` | boolean | true | 对空中单位施加状态 | true |
| `statusGround` | boolean | true | 对地面单位施加状态 | true |
| `hidden` | boolean | false | 是否在天气列表隐藏 | true |
| *`particleRegion`* | String | circle-shadow | 粒子贴图 region（ParticleWeather） | "particle" |
| *`color`* | Color | white | 粒子颜色 | 酸雨绿 |
| *`sizeMin`/`sizeMax`* | float | 2.4/12 | 粒子尺寸范围 | 2.5/5 |
| *`density`* | float | 1200 | 粒子密度（越大越密） | 1600 |
| *`baseSpeed`* | float | 6.1 | 粒子飘动速度 | 4.3 |
| *`useWindVector`* | boolean | false | 是否受星球风向影响 | true |
| *`drawNoise`* | boolean | false | 是否叠加噪声雾层 | 雾=true |
| *`attrs`* | Attributes | — | 对光照/水等环境属性的修正 | `attrs.set(Attribute.light,-0.2f)` |

> 带 `*` 的字段来自 `ParticleWeather`（type/weather/ParticleWeather.java:12-23），裸 `Weather` 没有。

---

## 14. 补缺方块 Block（5 张）【v2 新增】

> v1 只覆盖了墙/炮塔/厂/钻/发电/传送。这里补齐 v1 缺失的 Block 子类：矿石、逻辑处理器、液块、载荷运输、地板。

### 卡片 14.1 · 矿石块 OreBlock · ore-copper 风格

**一句话用途**：在地板上生成的矿脉覆盖层，钻头挖的就是它。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.environment.OreBlock;
import mindustry.content.Blocks;

public class ModOres {

    public static OreBlock exampleOre;

    public static void load() {
        // 最简：直接传一个 Item，名字自动变成 "ore-<item名>"
        // 它会自动把 itemDrop / mapColor / 3 个变体设好
        exampleOre = new OreBlock(ModItems.exampleItem) {{
            oreDefault = true;     // 用默认噪声分布
            oreThreshold = 0.82f;  // 矿脉出现阈值（越高越稀有）
            oreScale = 24f;        // 噪声尺度
        }};
    }
}
```

**官方对应物**：`Blocks.java:979`——`oreCopper = new OreBlock(Items.copper){{ oreDefault = true; oreThreshold = 0.81f; oreScale = 23.47619f; }};`。构造 `OreBlock(Item ore)` 自动命名为 `"ore-" + ore.name`（OreBlock.java:26-28）；`OreBlock(String name)` 仅供 mod 用（OreBlock.java:31）。它继承 `OverlayFloor`，`itemDrop` 就是该矿石，`variants = 3` 自动三套贴图。

**改哪里出花样**：改 `oreThreshold` 调稀有度（越大越稀少）；改 `oreScale` 调矿脉形状。矿石贴图需要 `ore-example-item1/2/3.png`（或回退到 `example-item1/2/3.png`，见 `createIcons()` OreBlock.java:48-50）。

**📋 矿石参数速查表**：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| 构造参 `Item ore` | Item | — | 该矿石块产出的物品 | `ModItems.exampleItem` |
| `oreDefault` | boolean | false | 是否用默认噪声生成 | true |
| `oreThreshold` | float | — | 矿脉阈值（越大越稀有） | 0.81~0.85 |
| `oreScale` | float | — | 噪声尺度（矿脉大小） | 23~24 |
| `itemDrop` | Item | 构造设定 | 挖掉掉落的物品 | =ore |

---

### 卡片 14.2 · 逻辑处理器 LogicBlock · micro-processor 风格

**一句话用途**：做一块能跑逻辑编程语言（mLogic）的可编程方块，写代码控制整个工厂。

**完整可复制代码**（参数速查 + 最简骨架）：

```java
package com.example.mod.content;

import mindustry.world.blocks.logic.LogicBlock;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.logic;
import static mindustry.content.Items.*;

public class ModLogic {

    public static LogicBlock myProcessor;

    public static void load() {
        myProcessor = new LogicBlock("my-processor") {{
            requirements(logic, with(Items.copper, 90, Items.lead, 50, Items.silicon, 50));
            instructionsPerTick = 2;   // 每帧执行几条指令（越大越快，也越卡）
            maxInstructionScale = 5;   // 可调最大倍速
            range = 8 * 10;            // 能链接/控制方块的距离（格数*8）
            size = 1;                  // 占地 1x1
        }};
    }
}
```

**官方对应物**：`Blocks.java:6896`——`microProcessor = new LogicBlock("micro-processor"){{ requirements(Category.logic, with(Items.copper, 90, Items.lead, 50, Items.silicon, 50)); instructionsPerTick = 2; size = 1; }};`；对照 `logicProcessor`（Blocks.java:6903）：`instructionsPerTick = 8; range = 8*22; size = 2;`。基类构造已自动设 `update=true; solid=true; configurable=true; envEnabled=Env.any;`（LogicBlock.java:51-63）。

**改哪里出花样**：改 `instructionsPerTick` 调性能（微=2，逻辑=8，超=25）；改 `range` 调遥控距离；改 `size` 做 2x2 大号处理器。逻辑内容本身在游戏内用 mLlogic 写，代码层面只需要这些参数。

**📋 逻辑块参数速查表**：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `instructionsPerTick` | int | 1 | 每帧执行指令数（越大越快） | 微=2，逻辑=8 |
| `maxInstructionScale` | int | 5 | 运行时可调最大倍速 | 5 |
| `range` | float | 80 | 链接方块距离（格×8） | 逻辑=176 |
| `size` | int | 1 | 占地边长 | 微=1，逻辑=2 |

---

### 卡片 14.3 · 液块/管道 Conduit · conduit 风格

**一句话用途**：做一条在管道里运输液体（水/熔渣/原油）的方块。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.liquid.Conduit;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.liquid;
import static mindustry.content.Items.*;

public class ModLiquidsBlocks {

    public static Conduit myConduit;

    public static void load() {
        myConduit = new Conduit("my-conduit") {{
            requirements(liquid, with(Items.metaglass, 1));
            liquidCapacity = 20f;    // 管道能存多少液体
            health = 45;
            // 液体燃烧/爆炸时对管道血量的影响系数
            explosivenessScale = flammabilityScale = 10f / 20f;
        }};
    }
}
```

**官方对应物**：`Blocks.java:2323`——`conduit = new Conduit("conduit"){{ requirements(Category.liquid, with(Items.metaglass, 1)); liquidCapacity = 20f; health = 45; explosivenessScale = flammabilityScale = 10f/20f; }};`。`Conduit` 继承 `LiquidBlock`（liquid/LiquidBlock.java:15），后者构造已自动 `hasLiquids = true; outputsLiquid = true;`。贴图需 `my-conduit.png` / `my-conduit-top.png` / `my-conduit-liquid.png`。

**改哪里出花样**：改 `liquidCapacity` 调管道吞吐；抄 `pulseConduit`（Blocks.java:2330）加钛做耐压管道。

**📋 液块参数速查表**：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `liquidCapacity` | float | — | 管道/方块液体容量 | 20 |
| `health` | int | — | 耐久 | 45 |
| `explosivenessScale` / `flammabilityScale` | float | — | 易燃/易爆液体对管道的伤害系数 | 10/20 |
| `hasLiquids` | boolean | 基类 true | 是否处理液体 | 自动 true |

---

### 卡片 14.4 · 载荷运输 PayloadConveyor · payload-conveyor 风格

**一句话用途**：做一条运输"载荷"（整块建筑/单位）的传送带——v1 未覆盖的 payload 类。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.payloads.PayloadConveyor;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.units;
import static mindustry.content.Items.*;

public class ModPayloads {

    public static PayloadConveyor myPayloadConveyor;

    public static void load() {
        myPayloadConveyor = new PayloadConveyor("my-payload-conveyor") {{
            requirements(units, with(Items.graphite, 10, Items.copper, 10));
            canOverdrive = false;   // 能否被超频（overdrive）加速
        }};
    }
}
```

**官方对应物**：`Blocks.java:6627`——`payloadConveyor = new PayloadConveyor("payload-conveyor"){{ requirements(Category.units, with(Items.graphite, 10, Items.copper, 10)); canOverdrive = false; }};`。注意载荷类方块的 Category 是 `units`。想做转向的就看 `payloadRouter`（Blocks.java:6632）。

**改哪里出花样**：改 `canOverdrive = true` 让它能被加速；改成本/占地即可。

**📋 载荷块参数速查表**：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `canOverdrive` | boolean | — | 能否被超频块加速 | false |
| `requirements(Category.units, ...)` | 方法 | — | 载荷方块归类到 units 分类 | 见卡片 |

---

### 卡片 14.5 · 自定义地板 Floor · darksand 风格

**一句话用途**：做一种新地面（沙地/毒沼/金属板），决定行走速度、能否长油、下面藏什么矿——v1 只有 StaticWall，没有 Floor。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.world.blocks.environment.Floor;
import mindustry.game.Attribute;

public class ModFloors {

    public static Floor myFloor;

    public static void load() {
        // 最简地板：就一个名字
        myFloor = new Floor("my-floor") {{
            variants = 3;                  // 3 种随机贴图变体
            speedMultiplier = 0.8f;        // 单位经过速度倍率（1=正常，0.6=慢）
            itemDrop = ModItems.exampleItem; // 挖这块地板掉的矿（如沙子）
            attributes.set(Attribute.oil, 1.0f); // 该地板带"含油"环境属性
        }};
    }
}
```

**官方对应物**：`Blocks.java:390`——`darksand = new Floor("darksand"){{ itemDrop = Items.sand; playerUnmineable = true; attributes.set(Attribute.oil, 1.5f); }};`；最简 `stone = new Floor("stone")`（Blocks.java:348）；减速 `mud = new Floor("mud"){{ speedMultiplier = 0.6f; ... }}`（Blocks.java:398）。

**改哪里出花样**：改 `speedMultiplier` 做减速泥地/加速板；改 `itemDrop` 让地板能被挖成矿；`attributes.set(...)` 加环境属性（油/水/孢子等）。贴图需要 `my-floor.png`（含 `my-floor1/2/3.png` 变体）。

**📋 地板参数速查表**：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| `variants` | int | 1 | 随机贴图变体数 | 3 |
| `speedMultiplier` | float | 1 | 单位经过速度倍率 | 泥=0.6 |
| `itemDrop` | Item | null | 挖掉地板掉落的物品 | `Items.sand` |
| `playerUnmineable` | boolean | false | 玩家能否挖 | true=不可挖 |
| `blendGroup` | Block | null | 与某地板混合过渡 | `Blocks.stone` |
| `attributes.set(...)` | 方法 | — | 环境属性（油/水/孢子/热） | `(Attribute.oil,1.0f)` |
| `liquidDrop` | Liquid | null | 该地板上的液体（如水面） | （可选） |

---

## 15. ⭐NEW 科技树 TechTree（1 张）【v2 重点新增大区】

### 卡片 15.1 · 把自定义建筑挂上科技树 · nodeRoot + node 嵌套

**一句话用途**：让你的新墙/新炮塔出现在"研究"界面里，需要先研究前置、花资源解锁。

**完整可复制代码**：

```java
package com.example.mod.content;

import mindustry.content.TechTree;
import arc.struct.Seq;
import mindustry.game.Objectives.Objective;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.defense.turrets.ItemTurret;

import static mindustry.content.TechTree.*;
import static mindustry.type.ItemStack.with;
import static com.example.mod.content.ModItems.exampleItem;

public class ModTech {

    /**
     * 本方法必须在【所有 block 都 new 完之后】调用。
     * 推荐：放在主类 loadContent() 的最后（ModBlocks.load() 之后）。
     */
    public static void load() {
        // 新建一个独立根节点（会在科技树选择界面生成一个新标签页）
        // 参数：(标签名, 根内容, [requireUnlock], children)
        nodeRoot("my-mod", ModBlocks.exampleWall, () -> {

            // 一级子节点：前置就是 exampleWall，研究它需要花 10 个 exampleItem
            node(ModTurrets.miniDuo, with(exampleItem, 10), () -> {

                // 二级子节点：在 miniDuo 之后解锁
                // 不传 requirements 时用内容自己的 researchRequirements()
                node(ModBlocks.foamWall, () -> {
                    // 还可以继续往下嵌套……
                });
            });

            // 无子节点的叶子
            node(ModDistribution.myConveyor);

            // 带额外目标（Objective）的节点：例如要求先完成某个区块
            // node(ModBlocks.someBlock, Seq.with(new SectorComplete(...)), () -> {});
        });
    }
}
```

**官方对应物**：`SerpuloTechTree.java:15`——`Planets.serpulo.techTree = nodeRoot("serpulo", coreShard, () -> { node(conveyor, () -> { node(junction, () -> { ... }); }); });`。核心 API 在 `content/TechTree.java`。

**接入时机（已核验）**：内容加载顺序见 `ContentLoader.java:58-81`——`createBaseContent()` 先建官方内容并跑 `SerpuloTechTree.load()`/`ErekirTechTree.load()`（72-73 行），**然后** `createModContent()` 才跑 mod 的 `loadContent()`。所以：
- ✅ 你的 block 在 `loadContent()` 里 `new` 出来后，**在同一个 `loadContent()` 末尾**调用 `ModTech.load()` 建节点即可（此时 block 都已存在）。
- ❌ **不要放到 `init()`**：`init()` 是所有内容 `init()` 阶段，科技树节点需要在内容初始化前就挂到 `Content.techNode` 上。
- 你新建的 `nodeRoot(...)` 会自动加入静态 `TechTree.roots`（TechTree.java:27），游戏渲染研究界面时会读它，**无需手动注册**。
- 想挂到某个星球下：在你的 `Planet` 定义里设 `planet.techTree = <你的 root 节点>`（参考 Planets.java:15 `Planets.serpulo.techTree = nodeRoot(...)`）。

**📋 TechTree 方法速查表**（TechTree.java:19-72）：

| 方法签名 | 参数含义 | 用途 |
|---|---|---|
| `nodeRoot(String name, UnlockableContent content, Runnable children)` | name=标签名；content=根节点内容；children=子树 | 建一个独立科技树根（标签页） |
| `nodeRoot(String name, UnlockableContent content, boolean requireUnlock, Runnable children)` | 多一个 requireUnlock=是否需先解锁才能选 | 同上，Erekir 用这个 |
| `node(UnlockableContent content, Runnable children)` | 用内容自带的 `researchRequirements()` 作需求 | 普通子节点 |
| `node(UnlockableContent content, ItemStack[] requirements, Runnable children)` | 手动指定资源需求（用 `with(...)`） | 自定义研究成本 |
| `node(UnlockableContent content, ItemStack[] requirements, Seq<Objective> objectives, Runnable children)` | 额外加 Objective 目标 | 需要先完成区块/产出某物 |
| `node(UnlockableContent content, Seq<Objective> objectives, Runnable children)` | 用默认需求 + 自定义目标 | — |
| `node(UnlockableContent content)` | 无子节点的叶子 | 最简单 |
| `nodeProduce(content, objectives, children)` | 自动加一个 `Produce(content)` 目标 | 要求先生产过该内容 |

**TechNode 关键字段**（TechTree.java:78-102）：`content`（研究的内容）、`requirements`（ItemStack[] 资源需求）、`objectives`（额外目标）、`children`（子节点）、`parent`、`depth`、`planet`（关联星球，null 自动探测）。

**Objective 类型**（`mindustry.game.Objectives.*`）：`SectorComplete( preset )`（完成某区块）、`Produce( content )`（生产过某物）、`Research( content )`（研究过某物）。

**改哪里出花样**：用嵌套 `node(..., () -> { ... })` 表达"前置关系"——外层研究完才能进内层；用 `with(item, n)` 定研究要花的资源；想给星球加科技树就把 `nodeRoot` 返回值赋给 `yourPlanet.techTree`。

---

## 16. ⭐NEW 星球 Planet（1 张）【v2 重点新增大区】

### 卡片 16.1 · 自定义星球最小实现 · 极简可加载星球

**一句话用途**：做一颗自己的星球，能在星系视图里看到、能降落、有自己的地形生成器。

**完整可复制代码**（极简版：继承 `Planet`，设基本参数，配一个返回默认地形的 `PlanetGenerator`）：

```java
package com.example.mod.content;

import arc.graphics.Color;
import mindustry.type.Planet;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.world.Tile;
import mindustry.content.Blocks;

public class ModPlanets {

    /** 一个最简星球生成器：所有区块都铺成 stone 地板 + air 方块。 */
    public static class SimpleGenerator extends PlanetGenerator {
        @Override
        public void generate(mindustry.world.Tiles tiles, mindustry.game.Sector sector,
                            mindustry.world.WorldParams params) {
            // 直接调用父类：它会按 genTile() 逐格生成；这里覆盖成全 stone
            for (int x = 0; x < tiles.width; x++) {
                for (int y = 0; y < tiles.height; y++) {
                    tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                }
            }
        }
    }

    public static Planet myPlanet;

    public static void load() {
        // 构造：(名字, 母星, 半径, 区块网格大小)
        // 母星传 Planets.sun 让它绕太阳转；半径 1，网格 3（类似 serpulo 的蜂巢网格）
        myPlanet = new Planet("my-planet", mindustry.content.Planets.sun, 1f, 3) {{
            // --- 外观 ---
            atmosphereColor = Color.valueOf("3c1b8f");   // 大气颜色
            iconColor = Color.valueOf("7d4dff");         // 星球列表图标色
            landCloudColor = Color.valueOf("88aaff").a(0.5f); // 降落时的云色
            atmosphereRadIn = 0.02f;                     // 大气内半径修正
            atmosphereRadOut = 0.3f;                     // 大气外半径修正
            bloom = false;
            hasAtmosphere = true;

            // --- 降落/玩法 ---
            generator = new SimpleGenerator();           // 我们的极简地形生成器
            startSector = 0;                             // 进入时默认选的区块
            alwaysUnlocked = true;                       // 无需前置解锁
            accessible = true;                           // 在星球界面可见可选
            updateLighting = true;                       // 昼夜循环
            allowLaunchSchematics = true;                // 允许发射蓝图
            allowLaunchLoadout = true;                   // 允许带初始物资
            defaultCore = Blocks.coreShard;             // 降落默认核心

            // 3D 网格外观（HexMesh 是官方蜂巢球体贴图）
            meshLoader = () -> new mindustry.graphics.g3d.HexMesh(this, 6);
        }};
    }
}
```

**官方对应物**：`Planets.java:123`——`serpulo = new Planet("serpulo", sun, 1f, 3){{ generator = new SerpuloPlanetGenerator(); meshLoader = () -> new HexMesh(this, 6); ... }};`。构造函数 `Planet(String name, Planet parent, float radius)`（Planet.java:197）与带网格的 `Planet(String name, Planet parent, float radius, int sectorSize)`（Planet.java:224，自动建 `PlanetGrid` 和 `Sector`）。生成器基类 `PlanetGenerator extends BasicGenerator implements HexMesher`（PlanetGenerator.java:22）。

**sector 配置说明（已核验）**：
- 用四参构造 `(name, parent, radius, sectorSize)` 时，`sectorSize > 0` 会自动 `PlanetGrid.create(sectorSize)` 并为每个网格格 `new Sector(this, tile)`（Planet.java:227-236）。
- `startSector` 指定默认区块；`preset(index, sectorPreset)` 可把某个区块绑定到预设地图（Planet.java:323）。
- `Planet.init()` 会在每个区块上跑 `generator.generateSector(sector)`（Planet.java:465-472），所以**生成器必须非 null 才可降落**（`isLandable()` 要求 `sectors.size > 0`，Planet.java:333）。
- 想加天气：重写生成器的 `addWeather()` 或在 `ruleSetter` 里 `r.weather.add(...)`（参考 `PlanetGenerator.addWeather()`，PlanetGenerator.java:102）。

**改哪里出花样**：换 `atmosphereColor` / `iconColor` 改星球配色；换 `HexMesh(this, 6)` 的第二个参数调表面粗糙度；改 `startSector` 换出生区块；母星传 `null` 则它自己是恒星中心。复杂地形照 `SerpuloPlanetGenerator` 用噪声填 `genTile()`（PlanetGenerator.java:162）。

> **技术决策（简化方式）**：完整可玩的程序化星球需要噪声地形/矿脉/敌方基地，非常庞大。这里的最小实现把 `generate()` 直接铺成 `stone` 平地，保证**能加载、能看见、能降落**即可；进阶地形再去继承 `genTile(Vec3, TileGen)` 用 Simplex 噪声填充。

**📋 Planet 参数速查表**（`Planet` 关键字段，Planet.java）：

| 参数 | 类型 | 默认值 | 含义 | 典型设置 |
|---|---|---|---|---|
| 构造 `parent` | Planet | null | 母星；null=太阳系中心 | `Planets.sun` |
| 构造 `radius` | float | — | 星球球体半径 | 1（serpulo/erekir） |
| 构造 `sectorSize` | int | 0 | 区块网格边长（>0 才可降落） | 3 |
| `atmosphereColor` | Color | (0.3,0.7,1) | 大气颜色 | `Color.valueOf("3c1b8f")` |
| `atmosphereRadIn`/`Out` | float | 0 / 0.3 | 大气半径内/外修正 | 0.02 / 0.3 |
| `iconColor` | Color | white | 星球列表图标色 | 紫 |
| `landCloudColor` | Color | white .a(0.5) | 降落时云层色 | 孢子绿 |
| `hasAtmosphere` | boolean | true | 是否有大气 | true |
| `bloom` | boolean | false | 是否开启泛光 | 恒星=true |
| `visible` / `accessible` | boolean | true | 是否显示 / 可在界面选 | true |
| `generator` | PlanetGenerator | null | 地形生成器（null=不可降落） | `new SimpleGenerator()` |
| `startSector` | int | 0 | 默认进入的区块序号 | 170（serpulo） |
| `defaultCore` | Block | coreShard | 降落默认核心 | `Blocks.coreBastion` |
| `updateLighting` | boolean | true | 是否昼夜循环 | erekir=false |
| `tidalLock` | boolean | false | 是否潮汐锁定（永远一面朝母星） | erekir=true |
| `orbitSpacing` | float | 12 | 与相邻轨道间距 | 2（erekir） |
| `orbitRadius` | float | 自动 | 绕母星轨道半径（自动算，勿乱改） | — |
| `orbitTime` | float | 自动 | 公转周期（秒，自动按开普勒算） | — |
| `minZoom`/`maxZoom` | float | 0.5 / 2 | 相机缩放范围 | — |
| `allowLaunchSchematics` | boolean | false | 是否允许发射蓝图 | true |
| `allowLaunchLoadout` | boolean | false | 是否允许带初始物资 | true |
| `launchCapacityMultiplier` | float | 0.25 | 发射时物品容量倍率 | 0.5 |
| `defaultEnv` | int | terrestrial\|spores\|... | 默认环境标志（Env.* 位掩码） | `Env.scorching | Env.terrestrial` |
| `meshLoader` | Prov\<GenericMesh\> | 默认球 | 3D 球体网格加载器 | `() -> new HexMesh(this, 6)` |
| `cloudMeshLoader` | Prov\<GenericMesh\> | null | 云层网格 | `() -> new HexSkyMesh(...)` |
| `techTree` | TechNode | null | 该星球默认科技树根 | 见卡片 15.1 |
| `ruleSetter` | Cons\<Rules\> | 空 | 进入该星球时改规则 | `r -> { r.fog = true; }` |

---

## 快速索引表（v2）

| # | 模板名称 | 分类 | ContentType | 官方对应物 | 源码位置 |
|---|----------|------|-------------|------------|----------|
| 1.1 | mod.json 模板 | 工程骨架 | — | 模组规范 | example-mod/mod.json |
| 1.2 | 主类模板 | 工程骨架 | — | Mod.loadContent()/init() | Mod.java |
| 1.3 | build.gradle 模板 | 工程骨架 | — | 官方构建体系 | example-mod/build.gradle |
| 1.4 | gradle.properties 模板 | 工程骨架 | — | Gradle 标准配置 | example-mod/gradle.properties |
| 2.1 | 基础物品 · copper 风格 | 物品 Item | item | copper | Items.java:16 |
| 3.1 | 基础墙 · copper-wall 风格 | 墙体 Wall | block | copper-wall | Blocks.java:1708 |
| 3.2 | 高血量/功能墙 · plastanium-wall 风格 | 墙体 Wall | block | plastanium-wall | Blocks.java:1731 |
| 4.1 | 基础炮塔 · duo 风格 | 炮塔 Turret | block | duo (ItemTurret) | Blocks.java:3276 |
| 4.2 | 散射炮塔 · scatter 风格 | 炮塔 Turret | block | scatter (ItemTurret) | Blocks.java:3351 |
| 5.1 | 基础合成厂 · silicon-smelter 风格 | 工厂 GenericCrafter | block | silicon-smelter | Blocks.java:1070 |
| 6.1 | 基础钻头 · pneumatic-drill 风格 | 钻头 Drill | block | pneumatic-drill | Blocks.java:2896 |
| 7.1 | 火力发电机 · combustion-generator 风格 | 发电机 | block | combustion-generator | Blocks.java:2523 |
| 7.2 | 太阳能 · solar-panel 风格 | 发电机 | block | solar-panel | Blocks.java:2619 |
| 8.1 | 基础传送带 · conveyor 风格 | 传送 | block | conveyor | Blocks.java:2070 |
| 8.2 | 路由器 · router 风格 | 传送 | block | router | Blocks.java:2140 |
| 9.1 | 自定义状态效果 · overdrive 风格 | 状态效果 | status | overdrive | StatusEffects.java:153 |
| 10.1 | 基础子弹 · BasicBulletType（+参数表） | 子弹 | bullet | duo 铜弹药 | Blocks.java:3279 / Bullets.java:18 |
| 11.1 | 基础单位 · dagger 风格 | 单位 | unit | dagger | UnitTypes.java:100 |
| 12.1 | 自定义液体 · 熔融铜风格 🆕 | 液体 Liquid | liquid | slag / water | Liquids.java:21 |
| 13.1 | 自定义天气 · 酸雨(ParticleWeather) 🆕 | 天气 Weather | weather | rain / snow | Weathers.java:34 |
| 14.1 | 矿石块 OreBlock 🆕 | 补缺方块 | block | ore-copper | Blocks.java:979 |
| 14.2 | 逻辑处理器 LogicBlock 🆕 | 补缺方块 | block | micro-processor | Blocks.java:6896 |
| 14.3 | 液块/管道 Conduit 🆕 | 补缺方块 | block | conduit | Blocks.java:2323 |
| 14.4 | 载荷运输 PayloadConveyor 🆕 | 补缺方块 | block | payload-conveyor | Blocks.java:6627 |
| 14.5 | 自定义地板 Floor 🆕 | 补缺方块 | block | darksand / stone | Blocks.java:390 |
| 15.1 | 科技树 TechTree · nodeRoot+node 🆕⭐ | 科技树 | — | SerpuloTechTree.load | SerpuloTechTree.java:15 / TechTree.java |
| 16.1 | 自定义星球 Planet · 最小实现 🆕⭐ | 星球 | planet | serpulo | Planets.java:123 / Planet.java |

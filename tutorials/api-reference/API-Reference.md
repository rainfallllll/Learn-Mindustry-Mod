# Mindustry v159.7 Java 模组 API 参考表

> 本参考表是《Mindustry Java 模组开发循序渐进教程》的配套工具书。
> 所有 API 均从本地源码逐条核验（v159.7，HEAD `b3317f3`），标注源码文件路径。
> 读者对象：已通读教程、想查参数或写自己内容的萌新。

## 使用指南

- **最小实现**：照抄能跑的基础用法，展示如何创建并注册一个实例。
- **有趣实现**：花样式创意玩法，展示该分类 API 的非常规用法。
- **构造参数表**：列名「参数名 | 类型 | 默认值 | 含义」，列出该类及其父类中最常用的字段。
- 所有代码使用 `new Xxx("name"){{ ... }}` 匿名内部类写法，构造即自动注册（`Content.java:20`）。
- 需求数组用 `ItemStack.with(Items.xxx, n)` 构造（`ItemStack.java:47`）。
- 分类枚举 `Category` 的实际值：`turret, production, distribution, liquid, power, defense, crafting, units, effect, logic`（`Category.java:3-23`）。

---

## 目录

- [一、defense（防御）](#一defense防御)
- [二、distribution（物流）](#二distribution物流)
- [三、environment（环境）](#三environment环境)
- [四、liquid（液体）](#四liquid液体)
- [五、power（电力）](#五power电力)
- [六、production（生产）](#六production生产)
- [七、storage（存储）](#七storage存储)
- [八、turrets（炮塔）](#八turrets炮塔)
- [九、units（单位）](#九units单位)
- [十、payloads（载荷）](#十payloads载荷)
- [十一、sandbox（沙盒）](#十一sandbox沙盒)
- [十二、Fx 视觉特效](#十二fx-视觉特效)
- [十三、StatusEffect 状态效果](#十三statuseffect-状态效果)
- [附录：Block ↔ Building 绑定机制深度解析](#附录block--building-绑定机制深度解析)
- [附录：快速索引](#附录快速索引)

---

## 一、defense（防御）

### 1.1 Wall —— 基础墙

**一句话描述**：最基础的可建造防御墙，实心、可被攻击、可 autotile 拼接。

- **类全名**：`mindustry.world.blocks.defense.Wall`
- **源码路径**：`core/src/mindustry/world/blocks/defense/Wall.java`
- **继承**：`Wall extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| health | int | -1（Block 默认） | 生命值，`health = 80` 即 80 血 |
| size | int | 1 | 占地格数（边长） |
| requirements | ItemStack[] | {} | 建造需求，用 `requirements()` 方法设置 |
| chanceDeflect | float | -1f | 反弹子弹概率，-1 禁用；>0 时碰撞子弹按概率反弹 |
| flashHit | boolean | false | 受击时是否闪白 |
| flashColor | Color | Color.white | 闪白颜色 |
| lightningChance | float | -1f | 受击产生闪电的概率，-1 禁用 |
| lightningDamage | float | 20f | 闪电伤害 |
| lightningLength | int | 17 | 闪电长度（格） |
| autotile | boolean | false | 是否使用 autotile 贴图 |
| insulated | boolean | false | 是否绝缘（不导电） |
| absorbLasers | boolean | false | 是否吸收激光 |

**最小实现**：

```java
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

// 在 ModBlocks.loadContent() 中
myWall = new Wall("my-wall") {{
    requirements(Category.defense, with(Items.copper, 6));
    health = 120;
}};
```

**有趣实现：受击闪电流星墙**

```java
import mindustry.world.blocks.defense.Wall;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.content.Fx;
import arc.graphics.Color;

// 受击时必定放电，并反弹子弹
public class ThunderWall extends Wall {
    public ThunderWall(String name) {
        super(name);
        lightningChance = 1f;          // 每次受击都触发
        lightningDamage = 30f;
        lightningLength = 20;
        lightningColor = Color.cyan;
        chanceDeflect = 100f;          // 必定反弹
        flashHit = true;
        flashColor = Color.cyan;
    }
}
```

**实现原理**：覆写 `WallBuild.collision(Bullet)`（`Wall.java:164`），当 `lightningChance > 0` 时按概率创建 `Lightning.create()`；当 `chanceDeflect > 0` 时反转子弹速度向量并返回 `false` 阻止进一步碰撞。这里把 `lightningChance` 设为 1f、`chanceDeflect` 设为 100f，即可让每颗击中它的子弹都被电击并反弹。

---

### 1.2 Door —— 可开关门

**一句话描述**：继承 Wall，可点击开关、可被逻辑控制、可串联开合的门。

- **类全名**：`mindustry.world.blocks.defense.Door`
- **源码路径**：`core/src/mindustry/world/blocks/defense/Door.java`
- **继承**：`Door extends Wall`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| openfx | Effect | Fx.dooropen | 开门特效 |
| closefx | Effect | Fx.doorclose | 关门特效 |
| doorSound | Sound | Sounds.door | 开关门音效 |
| chainEffect | boolean | false | 串联门是否同步播放特效 |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |

**最小实现**：

```java
import mindustry.world.blocks.defense.Door;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myDoor = new Door("my-door") {{
    requirements(Category.defense, with(Items.titanium, 6, Items.silicon, 4));
    health = 100;
}};
```

---

### 1.3 ForceProjector —— 力场护盾

**一句话描述**：生成一个多边形力场，吸收子弹和爆炸，消耗电力和相 Fabric 充能。

- **类全名**：`mindustry.world.blocks.defense.ForceProjector`
- **源码路径**：`core/src/mindustry/world/blocks/defense/ForceProjector.java`
- **继承**：`ForceProjector extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| radius | float | 101.7f | 力场半径 |
| shieldHealth | float | 700f | 护盾容量（吸收伤害量） |
| sides | int | 6 | 多边形边数 |
| shieldRotation | float | 0f | 多边形旋转角度 |
| cooldownNormal | float | 1.75f | 正常状态下回复速度（每帧 buildup 减少量） |
| cooldownLiquid | float | 1.5f | 冷却液加速倍率 |
| cooldownBrokenBase | float | 0.35f | 破碎后回复速度 |
| phaseRadiusBoost | float | 80f | 消耗相 Fabric 时的半径加成 |
| phaseShieldBoost | float | 400f | 消耗相 Fabric 时的护盾加成 |
| phaseUseTime | float | 350f | 消耗一次相 Fabric 的周期（帧） |
| consumeCoolant | boolean | true | 是否消耗冷却液 |
| size | int | 1 | 占地格数（原版 force-projector 为 3） |
| hasPower | boolean | true | 是否需要电力（构造函数自动设 true） |

**最小实现**：

```java
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myShield = new ForceProjector("my-shield") {{
    requirements(Category.effect, with(Items.lead, 100, Items.titanium, 75, Items.silicon, 125));
    size = 3;
    radius = 100f;
    shieldHealth = 750f;
    consumePower(4f);
    itemConsumer = consumeItem(Items.phaseFabric).boost();
}};
```

**有趣实现：六边形巨型护盾塔**

```java
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.graphics.Pal;

// 超大范围、高血量的六边形护盾
megaShield = new ForceProjector("mega-shield") {{
    requirements(Category.effect, with(Items.surgeAlloy, 100, Items.phaseFabric, 50, Items.silicon, 200));
    size = 4;
    radius = 200f;
    shieldHealth = 3000f;
    sides = 8;
    shieldRotation = 0f;
    cooldownNormal = 2f;
    consumePower(10f);
    consumeItem(Items.phaseFabric).boost();
}};
```

**实现原理**：`radius`（`ForceProjector.java:36`）控制力场多边形半径，`shieldHealth`（`ForceProjector.java:39`）控制吸收量，`sides`（`ForceProjector.java:37`）控制边数。把这三个值拉到极限即可做出巨型护盾。

---

### 1.4 MendProjector —— 修复投影仪

**一句话描述**：周期性修复范围内友方建筑，消耗电力和相 Fabric 可加成。

- **类全名**：`mindustry.world.blocks.defense.MendProjector`
- **源码路径**：`core/src/mindustry/world/blocks/defense/MendProjector.java`
- **继承**：`MendProjector extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| reload | float | 250f | 两次修复之间的间隔（帧） |
| range | float | 60f | 修复范围 |
| healPercent | float | 12f | 每次修复恢复建筑最大血量的百分比 |
| phaseBoost | float | 12f | 相 Fabric 加成的修复量 |
| phaseRangeBoost | float | 50f | 相 Fabric 加成的范围 |
| useTime | float | 400f | 消耗一次相 Fabric 的周期 |
| baseColor | Color | Color.valueOf("84f491") | 基础颜色 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | true | 是否需要电力 |

**最小实现**：

```java
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myMend = new MendProjector("my-mend") {{
    requirements(Category.effect, with(Items.lead, 100, Items.titanium, 25, Items.silicon, 40, Items.copper, 50));
    size = 2;
    reload = 250f;
    range = 85f;
    healPercent = 11f;
    consumePower(1.5f);
    consumeItem(Items.phaseFabric).boost();
}};
```

---

### 1.5 OverdriveProjector —— 加速投影仪

**一句话描述**：加速范围内友方建筑的工作速度，消耗电力和相 Fabric 可加成。

- **类全名**：`mindustry.world.blocks.defense.OverdriveProjector`
- **源码路径**：`core/src/mindustry/world/blocks/defense/OverdriveProjector.java`
- **继承**：`OverdriveProjector extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| reload | float | 60f | 刷新间隔（帧） |
| range | float | 80f | 加速范围 |
| speedBoost | float | 1.5f | 加速倍率（1.5 = 加速 50%） |
| speedBoostPhase | float | 0.75f | 相 Fabric 额外加速倍率 |
| useTime | float | 400f | 消耗一次相 Fabric 的周期 |
| phaseRangeBoost | float | 20f | 相 Fabric 范围加成 |
| hasBoost | boolean | true | 是否支持相 Fabric 加成 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | true | 是否需要电力 |

**最小实现**：

```java
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myOverdrive = new OverdriveProjector("my-overdrive") {{
    requirements(Category.effect, with(Items.lead, 100, Items.titanium, 75, Items.silicon, 75, Items.plastanium, 30));
    size = 2;
    consumePower(3.50f);
    consumeItem(Items.phaseFabric).boost();
}};
```

**有趣实现：彩虹加速塔**

```java
import mindustry.world.blocks.defense.OverdriveProjector;
import arc.graphics.Color;
import arc.math.Mathf;

// 加速塔颜色随时间彩虹渐变
public class RainbowOverdrive extends OverdriveProjector {
    public RainbowOverdrive(String name) {
        super(name);
        baseColor = Color.white.cpy();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        // 每帧颜色 hue 偏移
        baseColor.fromHsv((Time.time * 0.5f) % 360f, 0.8f, 1f);
        super.drawPlace(x, y, rotation, valid);
    }
}
```

**实现原理**：覆写 `drawPlace`（`OverdriveProjector.java:54`），在绘制放置预览时用 `Time.time` 驱动 `Color.fromHsv` 循环变色。`baseColor` 字段（`OverdriveProjector.java:30`）控制范围圈颜色。

---

## 二、distribution（物流）

### 2.1 Conveyor —— 传送带

**一句话描述**：最基础的物品传送带，自动朝朝向方向运送物品。

- **类全名**：`mindustry.world.blocks.distribution.Conveyor`
- **源码路径**：`core/src/mindustry/world/blocks/distribution/Conveyor.java`
- **继承**：`Conveyor extends Block implements Autotiler`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| speed | float | 0f | 物品移动速度（实际像素/帧） |
| displayedSpeed | float | 0f | 统计面板显示的速度（物品/秒） |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |
| pushUnits | boolean | true | 是否推动单位 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myConveyor = new Conveyor("my-conveyor") {{
    requirements(Category.distribution, with(Items.copper, 1));
    health = 45;
    speed = 0.035f;
    displayedSpeed = 5f;
}};
```

**有趣实现：高速传送加速带**

```java
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

// 三倍速传送带
myFastConveyor = new Conveyor("fast-conveyor") {{
    requirements(Category.distribution, with(Items.titanium, 1, Items.copper, 1));
    health = 65;
    speed = 0.105f;          // 原版 3 倍
    displayedSpeed = 15f;    // 显示 15 item/s
}};
```

**实现原理**：`speed` 字段（`Conveyor.java:32`）控制物品在传送带上的移动速度，`displayedSpeed`（`Conveyor.java:33`）只影响统计面板显示。两者需要手动保持比例一致。

---

### 2.2 Router —— 路由器

**一句话描述**：自动将物品按轮询方向分流到四个方向的建筑。

- **类全名**：`mindustry.world.blocks.distribution.Router`
- **源码路径**：`core/src/mindustry/world/blocks/distribution/Router.java`
- **继承**：`Router extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| speed | float | 8f | 传送间隔（帧/次） |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.distribution.Router;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myRouter = new Router("my-router") {{
    requirements(Category.distribution, with(Items.copper, 3));
}};
```

---

### 2.3 Sorter —— 分类器

**一句话描述**：可配置分类方向，将指定物品从正面输出、其余从侧面输出。

- **类全名**：`mindustry.world.blocks.distribution.Sorter`
- **源码路径**：`core/src/mindustry/world/blocks/distribution/Sorter.java`
- **继承**：`Sorter extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| invert | boolean | false | 是否反转（指定物品从侧面出） |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

mySorter = new Sorter("my-sorter") {{
    requirements(Category.distribution, with(Items.lead, 2, Items.copper, 2));
}};
```

---

### 2.4 Junction —— 桥接器

**一句话描述**：四个方向同时输入物品并存储，按顺序输出，解决交叉堵塞。

- **类全名**：`mindustry.world.blocks.distribution.Junction`
- **源码路径**：`core/src/mindustry/world/blocks/distribution/Junction.java`
- **继承**：`Junction extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| speed | float | 26 | 通过间隔（帧） |
| capacity | int | 6 | 每方向缓冲容量 |
| displayedSpeed | float | 13f | 统计面板显示速度 |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.distribution.Junction;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myJunction = new Junction("my-junction") {{
    requirements(Category.distribution, with(Items.copper, 3));
    health = 30;
}};
```

---

### 2.5 ItemBridge —— 物品桥

**一句话描述**：跨越障碍远距离传输物品，需两端配对。

- **类全名**：`mindustry.world.blocks.distribution.ItemBridge`
- **源码路径**：`core/src/mindustry/world/blocks/distribution/ItemBridge.java`
- **继承**：`ItemBridge extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| range | int | 0 | 最大连接距离（格） |
| transportTime | float | 0 | 传输时间（帧） |
| fadeIn | boolean | true | 淡入动画 |
| moveArrows | boolean | true | 移动箭头动画 |
| pulse | boolean | false | 脉冲效果 |
| linkSameType | boolean | true | 是否只连接同类型桥 |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myBridge = new ItemBridge("my-bridge") {{
    requirements(Category.distribution, with(Items.lead, 6, Items.copper, 6));
    range = 4;
    transportTime = 2f;
}};
```

**有趣实现：超长距离相位桥**

```java
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

// 12 格超远距物品桥，脉冲效果
longBridge = new ItemBridge("long-bridge") {{
    requirements(Category.distribution, with(Items.phaseFabric, 5, Items.silicon, 7, Items.lead, 10));
    range = 12;
    transportTime = 2f;
    pulse = true;
    arrowPeriod = 0.9f;
    hasPower = true;
    consumePower(0.30f);
}};
```

**实现原理**：`range`（`ItemBridge.java:27`）控制最大连接格数，`pulse`（`ItemBridge.java:35`）开启脉冲视觉效果，`transportTime` 控制传输延迟。原版 phase-conveyor 就是这个配置（`Blocks.java:2117`）。

---

## 三、environment（环境）

### 3.1 Floor —— 地板

**一句话描述**：地图基底方块，决定单位移动速度、拖拽、伤害、液体沉积等。

- **类全名**：`mindustry.world.blocks.environment.Floor`
- **源码路径**：`core/src/mindustry/world/blocks/environment/Floor.java`
- **继承**：`Floor extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| speedMultiplier | float | 1f | 单位在其上移动速度倍率 |
| dragMultiplier | float | 1f | 单位拖拽倍率 |
| damageTaken | float | 0f | 每帧持续伤害 |
| drownTime | float | 0f | 沉没时间（帧），0 禁用 |
| walkEffect | Effect | Fx.none | 行走时特效 |
| walkSound | Sound | Sounds.none | 行走音效 |
| status | StatusEffect | StatusEffects.none | 行走时施加的状态效果 |
| statusDuration | float | 60f | 状态效果持续时间 |
| liquidDrop | Liquid | null | 泵可抽取的液体 |
| liquidMultiplier | float | 1f | 液体抽取倍率 |
| isLiquid | boolean | false | 是否为液体地板 |
| edge | String | "stone" | 边缘贴图名 |
| wall | Block | Blocks.air | 对应墙变体 |
| oreDefault | boolean | false | 是否默认生成矿石 |
| oreThreshold | float | 0.828f | 矿石生成阈值 |
| oreScale | float | 24f | 矿石生成缩放 |
| allowCorePlacement | boolean | false | 是否允许核心放置 |

**最小实现**：

```java
import mindustry.world.blocks.environment.Floor;
import mindustry.content.Fx;
import mindustry.graphics.Pal;

// 在 ModBlocks.loadContent() 中
myFloor = new Floor("my-floor") {{
    speedMultiplier = 1.2f;      // 加速地板
    dragMultiplier = 0.8f;
    status = StatusEffects.burning;
    statusDuration = 60f;
}};
```

**有趣实现：加速带地板**

```java
import mindustry.world.blocks.environment.Floor;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;

// 单位走过就加速 50% 的地板
speedFloor = new Floor("speed-floor") {{
    speedMultiplier = 1.5f;
    dragMultiplier = 0.7f;
    walkEffect = Fx.overdriven;
    status = StatusEffects.overdrive;
    statusDuration = 120f;
}};
```

**实现原理**：`speedMultiplier`（`Floor.java:28`）直接乘到单位移动速度上；`status` + `statusDuration`（`Floor.java:44-46`）在单位踩上时自动施加 `StatusEffects.overdrive` 状态（`StatusEffects.java:153`），该状态自带 1.15x 速度和 1.4x 伤害。

---

### 3.2 StaticWall —— 静态墙

**一句话描述**：不可建造的环境墙（地图生成用），实心、不可破坏。

- **类全名**：`mindustry.world.blocks.environment.StaticWall`
- **源码路径**：`core/src/mindustry/world/blocks/environment/StaticWall.java`
- **继承**：`StaticWall extends Prop`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| autotile | boolean | false | 是否使用 autotile 贴图 |
| autotileMidVariants | int | 1 | 中间贴图变体数 |
| variants | int | 2 | 贴图变体数（继承自 Prop） |

**最小实现**：

```java
import mindustry.world.blocks.environment.StaticWall;

// 在 ModBlocks.loadContent() 中
myRockWall = new StaticWall("my-rock-wall") {{
    autotile = true;
}};
```

---

### 3.3 OreBlock —— 矿石

**一句话描述**：覆盖在地板上的矿石 overlay，被钻头开采后产出对应物品。

- **类全名**：`mindustry.world.blocks.environment.OreBlock`
- **源码路径**：`core/src/mindustry/world/blocks/environment/OreBlock.java`
- **继承**：`OreBlock extends OverlayFloor`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| oreDefault | boolean | false | 是否默认在地图生成 |
| oreThreshold | float | 0.828f | 噪声阈值 |
| oreScale | float | 24f | 噪声缩放 |
| wallOre | boolean | false | 是否允许在墙上生成 |

**最小实现**：

```java
import mindustry.world.blocks.environment.OreBlock;
import mindustry.content.Items;

// 在 ModBlocks.loadContent() 中
myOre = new OreBlock(Items.copper) {{
    oreDefault = true;
    oreThreshold = 0.81f;
    oreScale = 23.5f;
}};
```

---

### 3.4 Prop —— 装饰物

**一句话描述**：可被单位撞碎的静态装饰物（岩石、树桩等）。

- **类全名**：`mindustry.world.blocks.environment.Prop`
- **源码路径**：`core/src/mindustry/world/blocks/environment/Prop.java`
- **继承**：`Prop extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| layer | float | Layer.blockProp | 渲染层级 |
| variants | int | 0 | 贴图变体数 |

**最小实现**：

```java
import mindustry.world.blocks.environment.Prop;

// 在 ModBlocks.loadContent() 中
myRock = new Prop("my-rock") {{
    variants = 3;
}};
```

---

## 四、liquid（液体）

### 4.1 Conduit —— 管道

**一句话描述**：运输液体的管道，可旋转连接。

- **类全名**：`mindustry.world.blocks.liquid.Conduit`
- **源码路径**：`core/src/mindustry/world/blocks/liquid/Conduit.java`
- **继承**：`Conduit extends LiquidBlock implements Autotiler`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| liquidCapacity | float | 20f | 单块管道容量 |
| liquidPressure | float | 1f | 液体压力（影响传输速度） |
| leaks | boolean | true | 是否泄漏 |
| padCorners | boolean | true | 角落是否内缩 |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myConduit = new Conduit("my-conduit") {{
    requirements(Category.liquid, with(Items.metaglass, 1));
    liquidCapacity = 20f;
    health = 45;
}};
```

---

### 4.2 Pump —— 泵

**一句话描述**：从地板液体中抽取液体输出到管道。

- **类全名**：`mindustry.world.blocks.production.Pump`
- **源码路径**：`core/src/mindustry/world/blocks/production/Pump.java`
- **继承**：`Pump extends LiquidBlock`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| pumpAmount | float | 0.2f | 每格每 tick 抽取量 |
| consumeTime | float | 300f | 物品消耗间隔（帧） |
| warmupSpeed | float | 0.019f | 预热速度 |
| liquidCapacity | float | 20f | 液体容量 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.production.Pump;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myPump = new Pump("my-pump") {{
    requirements(Category.liquid, with(Items.copper, 15, Items.metaglass, 10));
    pumpAmount = 7f / 60f;
    liquidCapacity = 20f;
}};
```

**有趣实现：巨型电动泵**

```java
import mindustry.world.blocks.production.Pump;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

// 3x3 超大泵，电力驱动，大流量
megaPump = new Pump("mega-pump") {{
    requirements(Category.liquid, with(Items.copper, 80, Items.metaglass, 90, Items.silicon, 30, Items.titanium, 40));
    pumpAmount = 0.22f;
    consumePower(1.3f);
    liquidCapacity = 200f;
    size = 3;
    hasPower = true;
}};
```

**实现原理**：`pumpAmount`（`Pump.java:20`）是每格每 tick 抽取量，`size = 3` 覆盖 9 格，总流量 = `pumpAmount * size * size`。原版 impulse-pump 就是这个配置（`Blocks.java:2314`）。

---

### 4.3 LiquidRouter —— 液体路由器

**一句话描述**：将输入液体均匀分流到四个方向。

- **类全名**：`mindustry.world.blocks.liquid.LiquidRouter`
- **源码路径**：`core/src/mindustry/world/blocks/liquid/LiquidRouter.java`
- **继承**：`LiquidRouter extends LiquidBlock`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| liquidPadding | float | 0f | 液体贴图内边距 |
| liquidCapacity | float | 120f | 液体容量 |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.liquid.LiquidRouter;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myLiquidRouter = new LiquidRouter("my-liquid-router") {{
    requirements(Category.liquid, with(Items.graphite, 4, Items.metaglass, 2));
    liquidCapacity = 120f;
    underBullets = true;
}};
```

---

## 五、power（电力）

### 5.1 PowerGenerator —— 发电机基类

**一句话描述**：所有发电机的抽象基类，定义发电量和爆炸行为。

- **类全名**：`mindustry.world.blocks.power.PowerGenerator`
- **源码路径**：`core/src/mindustry/world/blocks/power/PowerGenerator.java`
- **继承**：`PowerGenerator extends PowerDistributor`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| powerProduction | float | 0f | 效率 100% 时每 tick 发电量 |
| explosionRadius | int | 12 | 爆炸半径（格） |
| explosionDamage | int | 0 | 爆炸伤害 |
| explodeEffect | Effect | Fx.none | 爆炸特效 |
| explodeSound | Sound | Sounds.none | 爆炸音效 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | true | 是否输出电力（构造函数自动设） |

**最小实现**：

```java
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

// 一般不直接用 PowerGenerator，用 ConsumeGenerator 子类
myGen = new PowerGenerator("my-gen") {{
    requirements(Category.power, with(Items.copper, 25));
    powerProduction = 1f;
    size = 2;
}};
```

**有趣实现：昼夜波动发电机**

```java
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.game.Team;
import mindustry.gen.Building;
import arc.math.Mathf;

// 发电量随昼夜波动的发电机（白天满发，夜间减半）
public class SolarLikeGen extends ConsumeGenerator {
    public SolarLikeGen(String name) {
        super(name);
    }

    public class SolarGenBuild extends ConsumeGeneratorBuild {
        @Override
        public float getPowerProduction() {
            // 模拟白天：Time.time 在一局中持续递增
            float dayFactor = Mathf.clamp((Mathf.sin(Time.time * 0.001f) + 1f) / 2f, 0.3f, 1f);
            return powerProduction * productionEfficiency * dayFactor;
        }
    }
}
```

**实现原理**：覆写 `GeneratorBuild.getPowerProduction()`（`PowerGenerator.java:208`），用 `Mathf.sin(Time.time ...)` 制造昼夜正弦波动，再乘以 `productionEfficiency`。引擎在每帧电力计算时调用此方法。

---

### 5.2 ConsumeGenerator —— 消耗物品发电机

**一句话描述**：最常用的发电机，消耗物品/液体周期性产生电力。

- **类全名**：`mindustry.world.blocks.power.ConsumeGenerator`
- **源码路径**：`core/src/mindustry/world/blocks/power/ConsumeGenerator.java`
- **继承**：`ConsumeGenerator extends PowerGenerator`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| powerProduction | float | 0f | 发电量（继承自 PowerGenerator） |
| itemDuration | float | 120f | 消耗一个物品的持续时间（帧） |
| warmupSpeed | float | 0.05f | 预热速度 |
| effectChance | float | 0.01f | 随机特效概率 |
| generateEffect | Effect | Fx.none | 发电特效 |
| consumeEffect | Effect | Fx.none | 消耗特效 |
| outputLiquid | LiquidStack | null | 输出液体 |
| explodeOnFull | boolean | false | 液体满时是否爆炸 |
| size | int | 1 | 占地格数 |
| hasItems | boolean | true | 是否消耗物品 |

**最小实现**：

```java
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import mindustry.content.Fx;

myCoalGen = new ConsumeGenerator("my-coal-gen") {{
    requirements(Category.power, with(Items.copper, 25, Items.lead, 15));
    powerProduction = 1f;
    itemDuration = 120f;
    generateEffect = Fx.generatespark;
    consume(new ConsumeItemFlammable());
}};
```

---

### 5.3 Battery —— 电池

**一句话描述**：存储电力，在电力不足时放电。

- **类全名**：`mindustry.world.blocks.power.Battery`
- **源码路径**：`core/src/mindustry/world/blocks/power/Battery.java`
- **继承**：`Battery extends PowerDistributor`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| emptyLightColor | Color | Color.valueOf("f8c266") | 空电时灯光颜色 |
| fullLightColor | Color | Color.valueOf("fb9567") | 满电时灯光颜色 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | true | 是否连接电力网络 |

**最小实现**：

```java
import mindustry.world.blocks.power.Battery;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myBattery = new Battery("my-battery") {{
    requirements(Category.power, with(Items.copper, 5, Items.lead, 20));
    consumePowerBuffered(4000f);
}};
```

**有趣实现：巨型电池矩阵**

```java
import mindustry.world.blocks.power.Battery;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

// 3x3 超大电池，50000 容量
bigBattery = new Battery("big-battery") {{
    requirements(Category.power, with(Items.titanium, 20, Items.lead, 50, Items.silicon, 30));
    size = 3;
    consumePowerBuffered(50000f);
}};
```

**实现原理**：`consumePowerBuffered(capacity)` 是 `Block` 上的方法，创建一个 `ConsumePower` 消费者并设置 `buffered = true` 和容量值（`Blocks.java:2512`）。`size = 3` 表示 3x3 占地。

---

### 5.4 PowerNode —— 电力节点

**一句话描述**：连接电力网络的激光塔，自动连接周围发电机/用电建筑。

- **类全名**：`mindustry.world.blocks.power.PowerNode`
- **源码路径**：`core/src/mindustry/world/blocks/power/PowerNode.java`
- **继承**：`PowerNode extends PowerBlock`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| laserRange | float | 6 | 最大连接距离（格） |
| maxNodes | int | 3 | 最大连接数 |
| autolink | boolean | true | 是否自动连接 |
| drawRange | boolean | true | 是否绘制范围圈 |
| laserScale | float | 0.25f | 激光粗细 |
| laserColor1 | Color | Color.white | 激光颜色1 |
| laserColor2 | Color | Pal.powerLight | 激光颜色2 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myNode = new PowerNode("my-node") {{
    requirements(Category.power, with(Items.copper, 2, Items.lead, 6));
    maxNodes = 10;
    laserRange = 6;
}};
```

---

## 六、production（生产）

### 6.1 Drill —— 钻头基类

**一句话描述**：自动开采脚下矿石的基类，根据 tier 决定可开采硬度。

- **类全名**：`mindustry.world.blocks.production.Drill`
- **源码路径**：`core/src/mindustry/world/blocks/production/Drill.java`
- **继承**：`Drill extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| tier | int | 0 | 可开采矿石的最高硬度 |
| drillTime | float | 300 | 钻一块矿石所需帧数 |
| liquidBoostIntensity | float | 1.6f | 液体加速倍率 |
| warmupSpeed | float | 0.015f | 预热速度 |
| drillEffect | Effect | Fx.mine | 产出时特效 |
| updateEffect | Effect | Fx.pulverizeSmall | 旋转时特效 |
| drawMineItem | boolean | true | 是否绘制正在开采的物品图标 |
| rotateSpeed | float | 2f | 钻头旋转速度 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | false | 是否需要电力（激光钻头开启） |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.production.Drill;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import mindustry.content.Liquids;

myDrill = new Drill("my-drill") {{
    requirements(Category.production, with(Items.copper, 12));
    tier = 2;
    drillTime = 600;
    size = 2;
    consumeLiquid(Liquids.water, 0.05f).boost();
}};
```

**有趣实现：激光极速钻头**

```java
import mindustry.world.blocks.production.Drill;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.Fx;

// 3x3 激光钻头，电力驱动，高速开采
laserDrill = new Drill("laser-drill") {{
    requirements(Category.production, with(Items.copper, 35, Items.graphite, 30, Items.silicon, 30, Items.titanium, 20));
    drillTime = 280;
    size = 3;
    hasPower = true;
    tier = 4;
    updateEffect = Fx.pulverizeMedium;
    drillEffect = Fx.mineBig;
    consumePower(1.10f);
    consumeLiquid(Liquids.water, 0.08f).boost();
}};
```

**实现原理**：`tier = 4`（`Drill.java:34`）决定可开采最高硬度 4 的矿石，`drillTime = 280`（`Drill.java:36`）是钻一块的帧数，`consumePower()` 开启电力消耗，`consumeLiquid().boost()` 让水加成 1.6 倍速度。原版 laser-drill 就是这个配置（`Blocks.java:2905`）。

---

### 6.2 GenericCrafter —— 通用合成厂

**一句话描述**：最常用的生产方块，按配方周期消耗输入产出输出。

- **类全名**：`mindustry.world.blocks.production.GenericCrafter`
- **源码路径**：`core/src/mindustry/world/blocks/production/GenericCrafter.java`
- **继承**：`GenericCrafter extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| outputItem | ItemStack | null | 单个输出物品（含数量） |
| outputItems | ItemStack[] | null | 多个输出物品 |
| outputLiquid | LiquidStack | null | 单个输出液体 |
| outputLiquids | LiquidStack[] | null | 多个输出液体 |
| craftTime | float | 80 | 合成一次所需帧数 |
| craftEffect | Effect | Fx.none | 合成完成特效 |
| updateEffect | Effect | Fx.none | 工作中特效 |
| warmupSpeed | float | 0.019f | 预热速度 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | false | 是否需要电力 |
| hasItems | boolean | true | 是否处理物品 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Category;
import mindustry.type.ItemStack;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import mindustry.content.Fx;

myCrafter = new GenericCrafter("my-crafter") {{
    requirements(Category.crafting, with(Items.copper, 75, Items.lead, 30));
    craftEffect = Fx.pulverizeMedium;
    outputItem = new ItemStack(Items.graphite, 1);
    craftTime = 90f;
    size = 2;
    consumeItem(Items.coal, 2);
}};
```

**有趣实现：随机产出稀有物工厂**

```java
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.type.ItemStack;
import mindustry.content.Items;
import arc.util.Random;

// 每次合成有 10% 概率额外产出 surge-alloy
public class LuckyCrafter extends GenericCrafter {
    public LuckyCrafter(String name) {
        super(name);
    }

    public class LuckyCrafterBuild extends GenericCrafterBuild {
        @Override
        public void craftEffect() {
            super.craftEffect();
            // 10% 概率额外产出 surge-alloy
            if(Mathf.chance(0.1)) {
                items.add(Items.surgeAlloy, 1);
            }
        }
    }
}
```

**实现原理**：覆写 `GenericCrafterBuild.craftEffect()`（`GenericCrafter.java` 中合成完成时调用），在父类特效后用 `Mathf.chance(0.1)` 按概率往 `items` 缓冲区塞一个 `Items.surgeAlloy`。Building 的 `items` 是 `ItemModule`，`add(item, amount)` 直接增加库存。

---

#### 🟢 6.2.0 v160 变更：GenericCrafter 输出改成"小数累积" <span class="newbadge">v160 NEW</span>

> 以下为 v160 相对 v159.7 的破坏性变化，老模组写工厂必读。详见《v159.7 → v160 变更速查》第 5 节。

| 新成员 | 位置 | 说明 |
|---|---|---|
| `GenericCrafterBuild.outputAccumulator` | `GenericCrafter.java:191` | `@Nullable float[]`，每个产出槽一个浮点累积器 |
| `GenericCrafterBuild.scaleOutput(float amount)` | `:304` | 输出缩放钩子，默认原样返回；**模组可覆写**动态缩放产出 |
| `afterPatch()` 顺序 | `:141` | `hasItems` 现先于 `outputsLiquid` 赋值（逻辑等价） |

**行为变化（务必注意）**：v160 的 `craft()` 不再是"每次工艺固定 offload 整数个"，而是：

```java
outputAccumulator[i] += scaleOutput(output.amount);   // 累积小数
int floored = Mathf.floor(outputAccumulator[i]);      // 只取整
outputAccumulator[i] -= floored;                      // 余数留下次
for(int j = 0; j < floored; j++) offload(output.item);
```

- **总产出守恒**，但单次 craft 可能出 0 个、1 个或多个——不要假设单次必出整数个。
- 想动态增产（受模块/过热/催化影响），**覆写 `scaleOutput(float)`** 即可，累积器自动处理小数。
- `shouldConsume()` 与液体溢出判断也改用 `scaleOutput(...)`（`:208`、`:279` 附近）。

---

### 6.3 HeatCrafter —— 受热合成厂（v160 新增方块） <span class="newbadge">v160 NEW</span>

**一句话描述**：靠接触热力网络（Heater/HeatConduit 等）供能的合成厂，热量不足罢工、热量超额可超效率。

- **类全名**：`mindustry.world.blocks.production.HeatCrafter`
- **源码路径**：`core/src/mindustry/world/blocks/production/HeatCrafter.java`
- **继承**：`HeatCrafter extends GenericCrafter`
- **实现接口**：内部类 `HeatCrafterBuild extends GenericCrafterBuild implements HeatConsumer`（`HeatCrafter.java:43`）
- **接口定义**：`mindustry.world.blocks.heat.HeatConsumer`——`float[] sideHeat()` / `float heatRequirement()`（`HeatConsumer.java:3-6`）

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 位置 | 含义 |
|--------|------|--------|------|------|
| heatRequirement | float | 10f | `:14` | 达到 100% 效率所需热量；0 表示不需要热 |
| overheatScale | float | 1f | `:16` | 超温后每多 1 点热折算的额外效率比例 |
| maxEfficiency | float | 4f | `:18` | 过热处理后效率上限（4 = 400%） |

**效率公式**：

```
warmupTarget() = clamp(heat / heatRequirement)                       // :78 基础 0~1
efficiencyScale() = min(                                              // :88-91
    clamp(heat/req) + max(heat-req,0)/req * overheatScale,            // 超额加成
    maxEfficiency
)
shouldConsume() = (heatRequirement <= 0 || heat > 0) && super        // :63 没热不耗料
```

**最小实现**：

```java
import mindustry.world.blocks.production.HeatCrafter;
import mindustry.world.meta.Category;
import mindustry.type.ItemStack;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myHeatCrafter = new HeatCrafter("my-heat-smelter") {{
    requirements(Category.crafting, with(Items.copper, 60, Items.graphite, 30));
    heatRequirement = 20f;     // 需要 20 点热才满效率
    overheatScale = 0.5f;      // 超 20 后每多 2 点热多 1 倍效率
    maxEfficiency = 3f;        // 最多 300%
    outputItem = new ItemStack(Items.silicon, 1);
    craftTime = 60f;
    size = 2;
}};
// 不用写 Build：父类 HeatCrafterBuild 已实现 HeatConsumer、热汇总、效率曲线、热条、图鉴
```

> 热力学联动示例（接收热力管道 heat、效率计算）见下方 **6.3.1**。

---

### 6.3.1 接收热力的工厂最小示例（对接 thermal-mod） <span class="newbadge">v160 NEW</span>

**背景**：`calculateHeat(sideHeat)`（`BuildingComp.java:412`）会先 `Arrays.fill(sideHeat,0)`，再扫描 `proximity` 里实现了 `HeatBlock`（即 `HeatProducer`）的邻居取热。但本仓库的 thermal-mod 是**自建双缓冲热力系统**——它的 `HeatConduit extends Wall`，热量存在 `ThermalComponent.storedHeat`（焦耳），**并不实现 v160 的 `HeatBlock` 接口**，所以 vanilla 的 `calculateHeat` 根本"看不见"它。

**桥接思路**：写一个 `HeatCrafter` 子类，在 `updateTile()` 里**先吃官方热力网，再手动把 mod 邻居的温度换算成 v160 热单位叠加进去**，最后再调 `super.updateTile()` 跑生产。

```java
package com.thermal.mod.blocks;

import com.thermal.mod.core.ThermalBuilding;
import mindustry.world.blocks.production.HeatCrafter;

/**
 * 接收热力的工厂：把本 mod 双缓冲热力网（HeatConduit 等 ThermalBuilding）
 * 的温度换算成 v160 官方 HeatConsumer 的热单位。
 */
public class ThermalFurnace extends HeatCrafter {

    /** 换算系数：邻居超温每多少 K 折合成 1 点 v160 热单位 */
    public float kelvinPerHeat = 50f;
    /** 室温基准（K），低于此温度不供热 */
    public float ambientK = 293.15f;

    public ThermalFurnace(String name) {
        super(name);
    }

    public class ThermalFurnaceBuild extends HeatCrafterBuild {

        @Override
        public void updateTile() {
            // 1) 先吃 v160 官方热力网络（Heater / HeatConductor 等 HeatProducer）
            //    calculateHeat 会自动 fill(sideHeat,0) 并扫描 proximity
            heat = calculateHeat(sideHeat);

            // 2) 再从本 mod 的 ThermalBuilding 邻居"抽"热，换算后叠加
            //    （thermal-mod 的 HeatConduit 不实现 HeatBlock，必须手动桥接）
            for(var build : proximity) {
                if(build != null && build.team == team && build instanceof ThermalBuilding tb) {
                    float excessK = tb.getThermal().getTemperatureK() - ambientK;
                    if(excessK > 0f) {
                        heat += excessK / kelvinPerHeat;   // 超温 → v160 热单位
                    }
                }
            }

            // 3) 让父类用"合并后的 heat"跑 GenericCrafter 生产逻辑
            super.updateTile();
        }
    }
}
```

**数据流与效率计算**：

```
HeatConduit（thermal-mod）
  └─ ThermalSystem 双缓冲传热 → storedHeat(J)
       └─ getTemperatureK() = storedHeat / heatCapacity   (K)
            └─ ThermalFurnaceBuild.updateTile() 每 tick：
                 excessK = tempK - ambientK
                 heat   += excessK / kelvinPerHeat             ← 桥接到 v160 的 heat
                      └─ 父类效率：
                           warmupTarget  = clamp(heat / heatRequirement)   // 0~1
                           efficiencyScale = min(clamp(heat/req)
                                         + max(heat-req,0)/req * overheatScale,
                                         maxEfficiency)                      // 可超 100%
                           shouldConsume = (heat > 0) && super             // 没热不耗料
```

**要点**：

1. **顺序不能反**：必须先 `heat = calculateHeat(sideHeat)`，再叠加 mod 热，最后才 `super.updateTile()`——否则 `GenericCrafter` 会用旧 `heat` 生产。
2. **温度→热单位是你自己定的映射**：v160 的"热"是抽象单位（默认 `heatRequirement=10`），mod 里是开尔文。`kelvinPerHeat` 就是你俩世界的汇率，按手感调。
3. **不用改 shouldConsume / efficiencyScale**：父类 `HeatCrafterBuild` 已经按 `heat` 算好效率与罢工逻辑，你只负责把 `heat` 喂饱。
4. 想让官方 `HeatConductor` 也能给这座炉子供热，不用改代码——只要它实现 `HeatBlock`，第 1 步的 `calculateHeat` 会自动收。

> **编译验证**：本示例已用 `~/jdk17/bin/javac`（17.0.20）对 v160 core 类（`Mindustry/core/build/classes/java/main`）+ `arc-core-1.0.jar` + thermal-mod 的 `ThermalBuilding/ThermalComponent` 真实编译通过，产出 `ThermalFurnace.class` 与 `ThermalFurnace$ThermalFurnaceBuild.class`（退出码 0）。

---

### 6.4 v160 新增的 Block 基类字段 <span class="newbadge">v160 NEW</span>

以下两个字段是 v160 在 `Block` 基类上新增的，所有方块都能用：

| 字段 | 类型 | 默认值 | 位置 | 含义 |
|---|---|---|---|---|
| `diagonalConfigInventory` | boolean | `false` | `Block.java:268` | 对角方向是否纳入配置物品库存（与旋转/对角配置相关） |
| `maxConsecutive` | int | `2` | `Block.java:392` | `instantTransfer` 方块单次连续传输最大数量（图鉴 `Block.java:662` 展示） |

另：逻辑编程新增 `sensor armor`，返回建筑 `block.armor`（`BuildingComp.java:2125`，v159.7 无此 case）。

---

## 七、storage（存储）

### 7.1 StorageBlock —— 存储方块基类

**一句话描述**：容器/保险箱的基类，存储物品，可被传送带抽取。

- **类全名**：`mindustry.world.blocks.storage.StorageBlock`
- **源码路径**：`core/src/mindustry/world/blocks/storage/StorageBlock.java`
- **继承**：`StorageBlock extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| itemCapacity | int | 0 | 每种物品的存储上限 |
| coreMerge | boolean | true | 是否与核心合并存储 |
| size | int | 1 | 占地格数 |
| health | int | -1 | 生命值 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myVault = new StorageBlock("my-vault") {{
    requirements(Category.effect, with(Items.titanium, 100));
    size = 2;
    itemCapacity = 300;
}};
```

---

### 7.2 Unloader —— 卸载器

**一句话描述**：将指定物品从相邻建筑中抽出并输出到传送带。

- **类全名**：`mindustry.world.blocks.storage.Unloader`
- **源码路径**：`core/src/mindustry/world/blocks/storage/Unloader.java`
- **继承**：`Unloader extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| speed | float | 1f | 每秒卸载次数（60/speed） |
| allowCoreUnload | boolean | true | 是否允许从核心卸载 |
| health | int | 70 | 生命值 |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.storage.Unloader;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myUnloader = new Unloader("my-unloader") {{
    requirements(Category.distribution, with(Items.titanium, 25, Items.silicon, 30));
    speed = 60f / 11f;
}};
```

---

## 八、turrets（炮塔）

### 8.1 Turret —— 炮塔基类

**一句话描述**：所有炮塔的基类，定义瞄准、旋转、射击循环。

- **类全名**：`mindustry.world.blocks.defense.turrets.Turret`
- **源码路径**：`core/src/mindustry/world/blocks/defense/turrets/Turret.java`
- **继承**：`Turret extends ReloadTurret extends BaseTurret`

**关键构造参数表**（跨三级父类）：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| range | float | 80f | 最大射程 |
| rotateSpeed | float | 5 | 旋转速度（度/帧） |
| reload | float | 10f | 装填时间（帧/发） |
| shootCone | float | 8f | 射击角度容差 |
| inaccuracy | float | 0f | 子弹散布角度（度） |
| targetAir | boolean | true | 是否攻击空中单位 |
| targetGround | boolean | true | 是否攻击地面单位 |
| targetBlocks | boolean | true | 是否攻击建筑 |
| targetPlayers | boolean | true | 是否攻击玩家 |
| targetHealing | boolean | false | 是否治疗友方 |
| maxAmmo | int | 30 | 最大弹药量 |
| ammoPerShot | int | 1 | 每发消耗弹药量 |
| shoot | ShootPattern | new ShootPattern() | 射击模式 |
| coolant | ConsumeLiquidBase | null | 冷却剂 |
| size | int | 1 | 占地格数 |
| health | int | -1 | 生命值 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
// 一般不直接用 Turret，用其子类 ItemTurret / PowerTurret / LiquidTurret
```

---

### 8.2 ItemTurret —— 物品弹药炮塔

**一句话描述**：消耗物品作为弹药的炮塔，支持多种物品对应多种子弹。

- **类全名**：`mindustry.world.blocks.defense.turrets.ItemTurret`
- **源码路径**：`core/src/mindustry/world/blocks/defense/turrets/ItemTurret.java`
- **继承**：`ItemTurret extends Turret`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| ammoTypes | ObjectMap\<Item,BulletType\> | {} | 物品→子弹映射表 |
| range | float | 80f | 射程（继承） |
| reload | float | 10f | 装填时间（继承） |
| shootCone | float | 8f | 射击容角（继承） |
| size | int | 1 | 占地格数 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import mindustry.content.Fx;
import mindustry.graphics.Pal;

myTurret = new ItemTurret("my-turret") {{
    requirements(Category.turret, with(Items.copper, 35));
    ammo(
        Items.copper, new BasicBulletType(2.5f, 9) {{
            width = 7f;
            height = 9f;
            lifetime = 60f;
            hitEffect = Fx.hitBulletColor;
            backColor = Pal.copperAmmoBack;
            frontColor = Pal.copperAmmoFront;
        }}
    );
    reload = 30f;
    range = 90f;
    shootCone = 10f;
}};
```

**有趣实现：彩虹变色炮塔**

```java
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.entities.bullet.BasicBulletType;
import arc.graphics.Color;
import arc.math.Mathf;

// 子弹颜色随时间彩虹循环
public class RainbowTurret extends ItemTurret {
    public RainbowTurret(String name) {
        super(name);
    }

    public class RainbowTurretBuild extends ItemTurretBuild {
        @Override
        protected void shoot(BulletType ammo) {
            // 每发子弹 hue 偏移
            float hue = (Time.time * 2f) % 360f;
            if(ammo instanceof BasicBulletType b) {
                b.backColor.fromHsv(hue, 0.8f, 1f);
                b.frontColor.fromHsv((hue + 60f) % 360f, 0.8f, 1f);
                b.trailColor = b.backColor;
            }
            super.shoot(ammo);
        }
    }
}
```

**实现原理**：覆写 `TurretBuild.shoot(BulletType)`（`Turret.java` 中射击主逻辑），在调用父类前修改子弹的 `backColor/frontColor`（`BasicBulletType.java:13`）。`Time.time` 是全局帧计数器，驱动 hue 循环。注意这里修改的是共享 BulletType 实例，若需每发独立颜色应 clone 子弹。

---

### 8.3 PowerTurret —— 电力炮塔

**一句话描述**：消耗电力而非物品的炮塔，只有一种子弹类型。

- **类全名**：`mindustry.world.blocks.defense.turrets.PowerTurret`
- **源码路径**：`core/src/mindustry/world/blocks/defense/turrets/PowerTurret.java`
- **继承**：`PowerTurret extends Turret`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| shootType | BulletType | null | 唯一的子弹类型 |
| range | float | 80f | 射程（继承） |
| reload | float | 10f | 装填时间（继承） |
| size | int | 1 | 占地格数 |
| hasPower | boolean | true | 是否需要电力（构造函数自动设） |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import mindustry.content.Fx;

myLaser = new PowerTurret("my-laser") {{
    requirements(Category.turret, with(Items.copper, 50, Items.lead, 50));
    shootType = new BasicBulletType(3f, 12) {{
        width = 6f;
        height = 8f;
        lifetime = 60f;
        hitEffect = Fx.hitLaser;
    }};
    reload = 35f;
    range = 90f;
    consumePower(3.3f);
}};
```

**有趣实现：超远射程狙击炮塔**

```java
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import mindustry.content.Fx;

// 超远射程、高伤害、低射速的狙击炮塔
sniperTurret = new PowerTurret("sniper-turret") {{
    requirements(Category.turret, with(Items.surgeAlloy, 40, Items.silicon, 60, Items.lead, 100));
    shootType = new BasicBulletType(8f, 80) {{
        width = 5f;
        height = 18f;
        lifetime = 100f;
        pierce = true;
        pierceBuilding = true;
        hitEffect = Fx.hitLaser;
        shootEffect = Fx.shootBig;
        trailLength = 10;
    }};
    reload = 120f;
    range = 250f;
    shootCone = 2f;
    rotateSpeed = 2f;
    size = 2;
    consumePower(5f);
}};
```

**实现原理**：`range = 250f`（`BaseTurret.java:21`）是最大射程，`reload = 120f`（`ReloadTurret.java:10`）是装填帧，`shootCone = 2f`（`Turret.java:70`）是极小容角 = 精准狙击。子弹 `pierce = true` 穿透建筑和单位。

---

### 8.4 LiquidTurret —— 液体炮塔

**一句话描述**：消耗液体作为弹药的炮塔，支持多种液体对应多种子弹。

- **类全名**：`mindustry.world.blocks.defense.turrets.LiquidTurret`
- **源码路径**：`core/src/mindustry/world/blocks/defense/turrets/LiquidTurret.java`
- **继承**：`LiquidTurret extends Turret`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| ammoTypes | ObjectMap\<Liquid,BulletType\> | {} | 液体→子弹映射表 |
| extinguish | boolean | true | 是否灭火 |
| range | float | 80f | 射程（继承） |
| reload | float | 10f | 装填时间（继承） |
| size | int | 1 | 占地格数 |
| hasLiquids | boolean | true | 是否处理液体（构造函数自动设） |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.entities.bullet.LiquidBulletType;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.Fx;

mySpray = new LiquidTurret("my-spray") {{
    requirements(Category.turret, with(Items.metaglass, 45, Items.lead, 75));
    ammo(
        Liquids.water, new LiquidBulletType(Liquids.water) {{
            knockback = 0.7f;
        }},
        Liquids.slag, new LiquidBulletType(Liquids.slag) {{
            damage = 4;
        }}
    );
    size = 2;
    reload = 3f;
    range = 110f;
    shootEffect = Fx.shootLiquid;
}};
```

**有趣实现：治疗友军炮塔**

```java
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.entities.bullet.LiquidBulletType;
import mindustry.content.Liquids;

// 用水弹治疗友方建筑的炮塔
public class HealTurret extends LiquidTurret {
    public HealTurret(String name) {
        super(name);
        targetHealing = true;       // 改为治疗友方
        targetGround = false;       // 不攻击敌方
        targetBlocks = false;
    }
}
```

**实现原理**：`Turret.java:95-100` 定义了 `targetHealing/targetGround/targetBlocks/targetAir` 四个布尔开关。把 `targetHealing` 设为 true、其余攻击目标设为 false，炮塔的目标搜索逻辑就会自动选择受伤的友方建筑。子弹类型需为治疗型（如 `LiquidBulletType` + 水）。

---

## 九、units（单位）

### 9.1 UnitFactory —— 单位工厂

**一句话描述**：按计划队列消耗物品和电力生产单位。

- **类全名**：`mindustry.world.blocks.units.UnitFactory`
- **源码路径**：`core/src/mindustry/world/blocks/units/UnitFactory.java`
- **继承**：`UnitFactory extends UnitBlock`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| plans | Seq\<UnitPlan\> | {} | 生产计划列表（单位、时间、需求） |
| capacities | int[] | {} | 各阶段容量 |
| createSound | Sound | Sounds.unitCreate | 完成音效 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | true | 是否需要电力 |
| hasItems | boolean | true | 是否消耗物品 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.units.UnitFactory;
import mindustry.type.UnitPlan;
import mindustry.content.UnitTypes;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import arc.struct.Seq;

myFactory = new UnitFactory("my-factory") {{
    requirements(Category.units, with(Items.copper, 50, Items.lead, 120, Items.silicon, 80));
    plans = Seq.with(
        new UnitPlan(UnitTypes.dagger, 60f * 15, with(Items.silicon, 10, Items.lead, 10))
    );
    size = 3;
    consumePower(1.2f);
}};
```

**有趣实现：批量快速工厂**

```java
import mindustry.world.blocks.units.UnitFactory;
import mindustry.type.UnitPlan;
import mindustry.content.UnitTypes;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;
import arc.struct.Seq;

// 三条产线并行的快速工厂
fastFactory = new UnitFactory("fast-factory") {{
    requirements(Category.units, with(Items.copper, 80, Items.lead, 150, Items.silicon, 120));
    plans = Seq.with(
        new UnitPlan(UnitTypes.dagger, 60f * 8, with(Items.silicon, 8, Items.lead, 8)),
        new UnitPlan(UnitTypes.flare, 60f * 10, with(Items.silicon, 12)),
        new UnitPlan(UnitTypes.crawler, 60f * 5, with(Items.silicon, 6, Items.coal, 8))
    );
    size = 3;
    consumePower(2.5f);
}};
```

**实现原理**：`plans`（`UnitFactory.java:39`）是 `Seq<UnitPlan>`，每个 `UnitPlan` 包含单位类型、生产时间（帧）、需求物品。多条 plan 会在建造菜单中可选切换，`size = 3` 决定同时可容纳几个单位队列。

---

### 9.2 Reconstructor —— 重组器

**一句话描述**：将低级单位升级为高级单位。

- **类全名**：`mindustry.world.blocks.units.Reconstructor`
- **源码路径**：`core/src/mindustry/world/blocks/units/Reconstructor.java`
- **继承**：`Reconstructor extends UnitBlock`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| constructTime | float | 120f | 升级耗时（帧） |
| upgrades | Seq\<UnitType[]\> | {} | 升级映射（每对 [旧,新]） |
| capacities | int[] | {} | 容量 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | true | 是否需要电力 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.units.Reconstructor;
import mindustry.content.UnitTypes;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myReconstructor = new Reconstructor("my-reconstructor") {{
    requirements(Category.units, with(Items.copper, 200, Items.lead, 120, Items.silicon, 90));
    size = 3;
    consumePower(3f);
    consumeItems(with(Items.silicon, 40, Items.graphite, 40));
    constructTime = 60f * 10f;
    upgrades.addAll(
        new UnitType[]{UnitTypes.dagger, UnitTypes.mace}
    );
}};
```

---

## 十、payloads（载荷）

### 10.1 PayloadConveyor —— 载荷传送带

**一句话描述**：运输大型载荷（建筑/单位）的 3x3 传送带。

- **类全名**：`mindustry.world.blocks.payloads.PayloadConveyor`
- **源码路径**：`core/src/mindustry/world/blocks/payloads/PayloadConveyor.java`
- **继承**：`PayloadConveyor extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| moveTime | float | 45f | 传送时间（帧） |
| moveForce | float | 201f | 推力 |
| payloadLimit | float | 3f | 最大载荷尺寸 |
| interp | Interp | Interp.pow5 | 移动插值 |
| pushUnits | boolean | true | 是否推动单位 |
| size | int | 3 | 占地格数（默认 3） |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.payloads.PayloadConveyor;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myPayloadConveyor = new PayloadConveyor("my-payload-conveyor") {{
    requirements(Category.units, with(Items.graphite, 10, Items.copper, 10));
    canOverdrive = false;
}};
```

---

### 10.2 PayloadMassDriver —— 载荷加速器

**一句话描述**：远距离发射载荷到配对接收器。

- **类全名**：`mindustry.world.blocks.payloads.PayloadMassDriver`
- **源码路径**：`core/src/mindustry/world/blocks/payloads/PayloadMassDriver.java`
- **继承**：`PayloadMassDriver extends PayloadBlock`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| range | float | 100f | 最大射程 |
| reload | float | 30f | 装填时间 |
| chargeTime | float | 100f | 充能时间 |
| maxPayloadSize | float | 3 | 最大载荷尺寸 |
| knockback | float | 5f | 后坐力 |
| rotateSpeed | float | 5f | 旋转速度 |
| size | int | 1 | 占地格数 |
| hasPower | boolean | true | 是否需要电力 |
| requirements | ItemStack[] | {} | 建造需求 |

**最小实现**：

```java
import mindustry.world.blocks.payloads.PayloadMassDriver;
import mindustry.world.meta.Category;
import static mindustry.type.ItemStack.with;
import mindustry.content.Items;

myDriver = new PayloadMassDriver("my-driver") {{
    requirements(Category.units, with(Items.tungsten, 40, Items.silicon, 50, Items.graphite, 20));
    size = 3;
    reload = 45f;
    chargeTime = 70f;
    range = 700f;
    maxPayloadSize = 2.5f;
    consumePower(0.5f);
}};
```

---

## 十一、sandbox（沙盒）

### 11.1 ItemSource —— 物品源

**一句话描述**：沙盒模式无限产出指定物品的方块。

- **类全名**：`mindustry.world.blocks.sandbox.ItemSource`
- **源码路径**：`core/src/mindustry/world/blocks/sandbox/ItemSource.java`
- **继承**：`ItemSource extends Block`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| itemsPerSecond | int | 100 | 每秒产出物品数 |
| health | int | -1 | 生命值 |
| size | int | 1 | 占地格数 |

**最小实现**：

```java
import mindustry.world.blocks.sandbox.ItemSource;
import mindustry.world.meta.Category;
import mindustry.world.meta.BuildVisibility;

mySource = new ItemSource("my-source") {{
    requirements(Category.distribution, BuildVisibility.sandboxOnly, with());
    alwaysUnlocked = true;
}};
```

---

### 11.2 PowerSource —— 电力源

**一句话描述**：沙盒模式无限输出电力的节点。

- **类全名**：`mindustry.world.blocks.sandbox.PowerSource`
- **源码路径**：`core/src/mindustry/world/blocks/sandbox/PowerSource.java`
- **继承**：`PowerSource extends PowerNode`

**关键构造参数表**：

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| powerProduction | float | 10000f | 发电量（原版为 1000000/60） |
| maxNodes | int | 100 | 最大连接数 |
| laserRange | float | 6 | 连接距离（继承） |
| size | int | 1 | 占地格数 |

**最小实现**：

```java
import mindustry.world.blocks.sandbox.PowerSource;
import mindustry.world.meta.Category;
import mindustry.world.meta.BuildVisibility;
import static mindustry.type.ItemStack.with;

myPowerSource = new PowerSource("my-power-source") {{
    requirements(Category.power, BuildVisibility.sandboxOnly, with());
    powerProduction = 1000000f / 60f;
    alwaysUnlocked = true;
}};
```

---

## 十二、Fx 视觉特效

### 12.1 常用内置特效速查表

以下特效均为 `mindustry.content.Fx` 的静态字段，类型为 `arc.graphics.g2d.Effect`。

| 字段名 | 效果描述 |
|--------|----------|
| Fx.none | 空特效（什么都不做） |
| Fx.shootSmall | 小型射击特效（枪口火光） |
| Fx.shootBig | 大型射击特效 |
| Fx.shootSmallFlame | 小型火焰射击特效 |
| Fx.shootLiquid | 液体喷射特效 |
| Fx.shootPayloadDriver | 载荷加速器射击特效 |
| Fx.shootHeal | 治疗射击特效（绿色） |
| Fx.hitBulletSmall | 子弹命中小特效 |
| Fx.hitBulletBig | 子弹命中大特效 |
| Fx.hitFlameSmall | 火焰命中特效 |
| Fx.hitFlamePlasma | 等离子火焰命中特效 |
| Fx.hitLiquid | 液体命中特效 |
| Fx.hitLaser | 激光命中特效 |
| Fx.hitLaserBlast | 激光爆炸命中特效 |
| Fx.hitLancer | 长矛（Lancer）命中特效 |
| Fx.hitEmpSpark | EMP 电火花特效 |
| Fx.explosion | 通用爆炸特效 |
| Fx.coreExplosion | 核心爆炸特效 |
| Fx.sparkExplosion | 火花爆炸特效 |
| Fx.shockwave | 冲击波特效 |
| Fx.spawnShockwave | 出生冲击波特效 |
| Fx.lightning | 闪电特效 |
| Fx.placeBlock | 放置方块特效 |
| Fx.breakBlock | 破坏方块特效 |
| Fx.breakProp | 破坏装饰物特效 |
| Fx.healWave | 修复波特效 |
| Fx.healWaveMend | 修复投影仪波特效 |
| Fx.overdriveWave | 加速投影仪波特效 |
| Fx.overdriven | 加速状态特效 |
| Fx.overclocked | 超频状态特效 |
| Fx.burning | 燃烧状态特效 |
| Fx.freezing | 冰冻状态特效 |
| Fx.wet | 潮湿状态特效 |
| Fx.melting | 融化状态特效 |
| Fx.sapped |  sap 状态特效 |
| Fx.oily | 焦油状态特效 |
| Fx.corrosionVapor | 腐蚀蒸汽特效 |
| Fx.shieldBreak | 护盾破碎特效 |
| Fx.absorb | 护盾吸收特效 |
| Fx.forceShrink | 力场收缩特效 |
| Fx.dooropen | 开门特效 |
| Fx.doorclose | 关门特效 |
| Fx.mine | 钻头采矿特效 |
| Fx.mineBig | 大钻头采矿特效 |
| Fx.pulverize | 粉碎特效 |
| Fx.pulverizeSmall | 小型粉碎特效 |
| Fx.pulverizeMedium | 中型粉碎特效 |
| Fx.reactorsmoke | 反应堆烟雾特效 |
| Fx.smoke | 通用烟雾特效 |
| Fx.smokePuff | 烟雾团特效 |
| Fx.itemTransfer | 物品传输特效 |
| Fx.unitSpawn | 单位生成特效 |
| Fx.regenParticle | 修复粒子特效 |

### 12.2 自定义 Effect 最小实现

```java
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.Fx;

// 创建一个自定义视觉特效：旋转的彩色圆环
public class MyEffects {
    public static Effect ringBurst = new Effect(20f, e -> {
        Draw.color(e.color);
        Lines.stroke(2f * e.fout());
        for(int i = 0; i < 6; i++) {
            Tmp.v1.trns(i * 60f + e.fin() * 180f, e.fin() * 20f);
            Lines.poly(e.x + Tmp.v1.x, e.y + Tmp.v1.y, 6, 4f * e.fout());
        }
        Draw.reset();
    });
}

// 在任意位置触发：
// MyEffects.ringBurst.at(x, y, 0, Color.red);
```

**实现原理**：`new Effect(lifetime, renderer)` 构造函数接收一个 `EffectRenderer` lambda，参数 `e` 是 `EffectState`。`e.x/e.y` 是位置，`e.fin()` 是 0~1 进度，`e.fout()` 是反向进度。用 `Draw.color()` / `Lines.stroke()` / `Lines.poly()` 绘制图形，`Draw.reset()` 恢复状态。触发时调用 `effect.at(x, y, rotation, color)`。

---

## 十三、StatusEffect 状态效果

### 13.1 关键构造参数表

- **类全名**：`mindustry.type.StatusEffect`
- **源码路径**：`core/src/mindustry/type/StatusEffect.java`
- **继承**：`StatusEffect extends UnlockableContent`
- **构造函数**：`StatusEffect(String name)`（`StatusEffect.java:75`）

| 参数名 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| damageMultiplier | float | 1f | 单位造成伤害的倍率 |
| healthMultiplier | float | 1f | 单位生命值倍率 |
| speedMultiplier | float | 1f | 单位移动速度倍率 |
| reloadMultiplier | float | 1f | 单位武器装填倍率 |
| buildSpeedMultiplier | float | 1f | 单位建造速度倍率 |
| dragMultiplier | float | 1f | 单位拖拽倍率 |
| damage | float | 0 | 每帧持续伤害（负值为治疗） |
| intervalDamageTime | float | 0 | 间隔伤害间隔（帧），<=0 禁用 |
| intervalDamage | float | 0 | 间隔伤害量 |
| intervalDamagePierce | boolean | false | 间隔伤害是否穿甲 |
| transitionDamage | float | 0f | 亲和转换时伤害 |
| disarm | boolean | false | 是否禁用武器 |
| effectChance | float | 0.15f | 随机特效出现概率 |
| permanent | boolean | false | 是否永久 |
| reactive | boolean | false | 是否仅与其他效果反应 |
| show | boolean | true | 是否在数据库显示 |
| color | Color | Color.white | 效果颜色 |
| effect | Effect | Fx.none | 随机视觉特效 |
| applyEffect | Effect | Fx.none | 施加时特效 |
| applyColor | Color | Color.white | 施加特效颜色 |
| affinities | ObjectSet\<StatusEffect\> | {} | 亲和效果集 |
| opposites | ObjectSet\<StatusEffect\> | {} | 对立效果集 |

### 13.2 常用内置效果速查表

以下为 `mindustry.content.StatusEffects` 的静态字段：

| 名称 | 颜色 | 核心效果 |
|------|------|----------|
| none | - | 无效果 |
| burning | #ffc455 | 0.167 持续伤害，与 wet/freezing 对立 |
| freezing | #6ecdec | 0.6x 速度，0.8x 生命，与 melting/burning 对立 |
| unmoving | Pal.gray | 速度 0（完全定身） |
| slow | Pal.lightishGray | 0.4x 速度（隐藏） |
| fast | Pal.boostTo | 1.6x 速度 |
| wet | Color.royal | 0.94x 速度，与 shocked 亲和 |
| muddy | #46382a | 0.94x 速度（隐藏） |
| melting | #ffa166 | 0.8x 速度，0.8x 生命，0.3 伤害 |
| sapped | Pal.sap | 0.7x 速度，0.8x 生命 |
| electrified | Pal.heal | 0.7x 速度，0.6x 装填 |
| sporeSlowed | Pal.spore | 0.8x 速度 |
| tarred | #313131 | 0.6x 速度，与 melting/burning 亲和 |
| overdrive | Pal.accent | 1.15x 速度，1.4x 伤害，永久，自修复 |
| overclock | Pal.accent | 1.15x 速度/伤害，1.25x 装填 |
| shielded | Pal.accent | 3x 生命（隐藏） |
| boss | Team.crux.color | 1.3x 伤害，1.5x 生命，永久 |
| shocked | Pal.lancerLaser | 反应型（与 wet 亲和） |
| blasted | #ff795e | 反应型（与 freezing 亲和） |
| corroded | #e4ffd6 | 每 20 帧 15 点间隔伤害 |
| disarmed | #e9ead3 | 禁用武器（隐藏） |
| invincible | - | 生命无限（隐藏） |
| dynamic | - | 动态效果占位（隐藏，永久） |

### 13.3 自定义 StatusEffect 最小实现

```java
import mindustry.type.StatusEffect;
import mindustry.content.Fx;
import arc.graphics.Color;

// 自定义状态：中毒，持续伤害并减速
public class MyStatusEffects {
    public static StatusEffect poison;

    public static void load() {
        poison = new StatusEffect("poison") {{
            color = Color.valueOf("7fff55");
            damage = 0.1f;               // 每帧 0.1 伤害
            speedMultiplier = 0.7f;      // 减速 30%
            effect = Fx.sapped;           // 随机特效
            effectChance = 0.1f;
        }};
    }
}

// 使用：unit.apply(MyStatusEffects.poison, 120f); // 施加 2 秒
```

**有趣实现：失重加速效果**

```java
import mindustry.type.StatusEffect;
import mindustry.content.Fx;
import arc.graphics.Color;

// 单位移动极快但受伤害增加
public class OverdriveBoost extends StatusEffect {
    public OverdriveBoost(String name) {
        super(name);
        color = Pal.accent.cpy();
        speedMultiplier = 2.5f;       // 2.5 倍速度
        damageMultiplier = 1.5f;      // 1.5 倍伤害
        reloadMultiplier = 1.5f;      // 1.5 倍装填
        healthMultiplier = 0.7f;      // 但血量降至 70%
        effect = Fx.overdriven;
        effectChance = 0.07f;
        permanent = true;
    }
}
```

**实现原理**：`speedMultiplier`（`StatusEffect.java:23`）直接乘到单位移动速度，`damageMultiplier`（`StatusEffect.java:19`）影响武器伤害，`healthMultiplier`（`StatusEffect.java:21`）影响最大血量。原版 `overdrive`（`StatusEffects.java:153`）就是类似配置：1.15x 速度、1.4x 伤害、0.95x 生命、永久。

**有趣实现：扩散中毒效果**

```java
import mindustry.type.StatusEffect;
import mindustry.gen.Unit;
import mindustry.gen.StatusEntry;
import mindustry.content.Fx;
import arc.graphics.Color;
import arc.math.Mathf;

// 中毒会每 60 帧传染给附近敌方单位
public class SpreadPoison extends StatusEffect {
    public SpreadPoison(String name) {
        super(name);
        color = Color.valueOf("7fff55");
        damage = 0.1f;
        speedMultiplier = 0.7f;
        effect = Fx.sapped;
        effectChance = 0.1f;
    }

    @Override
    public void update(Unit unit, StatusEntry entry) {
        super.update(unit, entry);
        // 每 60 帧传染一次
        if(Mathf.chanceDelta(1f / 60f)) {
            unit.group.intersect(unit.x - 10f, unit.y - 10f, 20f, 20f, other -> {
                if(other != unit && other.team != unit.team && other.isGrounded()) {
                    other.apply(this, 120f);
                }
            });
        }
    }
}
```

**实现原理**：覆写 `StatusEffect.update(Unit, StatusEntry)`（`StatusEffect.java:145`），先调用 `super.update()` 执行基础伤害逻辑，再用 `Mathf.chanceDelta(1f/60f)` 控制传染频率，通过 `unit.group.intersect()` 查找附近单位并调用 `other.apply(this, duration)` 施加自身状态。

---

## 附录：Block ↔ Building 绑定机制深度解析

> 本节是理解所有 Java 模组"自定义方块为什么能自动调用自定义逻辑"的核心。
> 所有源码行号基于 v159.7（HEAD `b3317f3`）。

### 一、机制概述

**Block 是"类型/模版"，Building 是"实例/实体"。** 一个 Block 类定义了这种方块的所有静态属性（大小、血量、贴图），游戏世界中每放置一块该方块，就由 Block 自动创建一个对应的 Building 实例来承载运行时状态与行为。

两者的绑定**不是靠命名约定，而是靠 Java 反射自动完成的**。只要你把 Building 子类写成 Block 的内部类，游戏构造 Block 对象时就会自动扫描并绑定。

---

### 二、核心源码解析

#### 2.1 `initBuilding()` 方法（Block.java:1241-1278）

这是整个绑定机制的核心，在 Block 构造函数中被调用（`Block.java:446`）。逐行讲解：

```java
// Block.java:1241-1278
public void initBuilding(){
    // 1. 从当前类开始，若为匿名类则取其父类
    Class<?> current = getClass();
    if(current.isAnonymousClass()) current = current.getSuperclass();

    // 2. 记录"主子类"——非匿名的那个类
    subclass = current;  // Block.java:397-398，注释 "Main subclass. Non-anonymous."

    // 3. 向上遍历父类链，直到找到一个声明了 Building 子类内部类的类
    while(buildType == null && Block.class.isAssignableFrom(current)){
        // 用 Structs.find 找第一个"是 Building 子类且不是接口"的内部类
        Class<?> type = Structs.find(current.getDeclaredClasses(),
            t -> Building.class.isAssignableFrom(t) && !t.isInterface());

        if(type != null){
            // 4. 拿到该内部类的构造函数（参数必须是外层 Block 类型）
            try{
                java.lang.reflect.Constructor<?> cons =
                    type.getDeclaredConstructor(type.getDeclaringClass());
                // 5. 绑定为 buildType：一个工厂方法，传入 this 就能 new 出 Building 实例
                buildType = () -> {
                    try{
                        return (Building) cons.newInstance(this);
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                };
            }catch(Exception e){
                throw new RuntimeException(e);
            }
        }

        // 6. 继续向上找父类
        current = current.getSuperclass();
    }

    // 7. 兜底：如果一路向上都没找到，就用默认的 Building::create
    if(buildType == null){  // Block.java:1274-1276
        buildType = Building::create;
    }
}
```

**关键流程总结：**
1. 从 `this.getClass()` 开始，匿名类则跳到父类
2. 沿继承链向上遍历每一层
3. 每层用 `getDeclaredClasses()` 找内部类
4. 筛出"第一个是 Building 子类的内部类"
5. 拿到它的构造函数，包成 `buildType` 工厂
6. 找到就停，找不到就继续往上找
7. 全链都没有 → 兜底 `Building::create`

#### 2.2 `buildType` 字段（Block.java:401-403）

```java
// Block.java:401-403
public Prov<Building> buildType = null;
// 注释原文："Set manually if modded"
```

`buildType` 是一个 `Prov<Building>`（无参 Supplier），调用它就 new 出一个新的 Building 实例。默认 `null`，由 `initBuilding()` 自动填充。

**"Set manually if modded" 的含义**：如果你用匿名类写法（`new Wall("x"){{...}}`），自动绑定会回退到父类的 Building。这时候想自定义 Building，就得手动赋值。

#### 2.3 兜底 `Building::create`（Block.java:1274-1276）

```java
// gen/Building.java:1208（由 KAPT 生成）
public static Building create(){
    return new Building();
}
```

当反射一路向上都没找到任何 Building 子类内部类时，就用这个兜底。出来的 Building 是空壳，只有最基础的方法，没有任何自定义行为。

#### 2.4 匿名类处理（Block.java:1246-1248）

```java
Class<?> current = getClass();
if(current.isAnonymousClass()) current = current.getSuperclass();
```

**为什么要跳过匿名类？** 因为匿名类（`new Wall("x"){{ ... }}`）本身没有命名内部类，直接在它上面 `getDeclaredClasses()` 永远找不到东西。所以自动跳到它的父类（也就是 Wall 本身），然后在 Wall 上找 `WallBuild`。

---

### 三、官方证据：反射不依赖命名

很多新手以为"内部类必须叫 `XxxBuild` 才能绑定"——**完全不是**。反射只看一件事：**是不是 Building 子类**。命名随意。

| 官方类 | 内部类名 | 源码位置 |
|--------|----------|----------|
| `PowerTurret` | `PowerTurretBuild` | PowerTurret.java:26 |
| `Turret` | `TurretBuild` | Turret.java:283 |
| `Door` | `DoorBuild` | Door.java |
| `ForceProjector` | `ForceBuild` | ForceProjector.java |

命名风格五花八门，有的带前缀有的不带，但全部正常工作——因为反射不看名字，只看类型继承关系。

**Blocks.java 中的匿名类回退绑定：**

```java
// Blocks.java 中大量这种写法（605 处双花括号匿名类）
public static Block copperWall;

static{
    copperWall = new Wall("copper-wall"){{
        requirements(Category.defense, with(Items.copper, 6));
        health = 120;
    }};
}
```

这里 `new Wall("copper-wall"){{...}}` 是个匿名子类。按上面的逻辑，`initBuilding()` 发现这是匿名类 → 跳到父类 `Wall` → 在 `Wall` 上找内部类 → 找到 `WallBuild` → 绑定成功。所以匿名类写法自动继承了父类的 Building 行为。

---

### 四、4 个失效场景

#### 场景 1：内部类不是 Building 子类

**错误写法：**

```java
public class MyWall extends Wall {
    public MyWall(String name){ super(name); }

    // ❌ 这个内部类不继承 Building，反射直接跳过
    public class MyHelper {
        public void doSomething(){}
    }
}
```

**结果：** `buildType` 一路向上找，找不到 Building 子类内部类 → 兜底 `Building::create` → 你的 `MyHelper` 永远不会被实例化，自定义行为全部失效。

**正确写法：**

```java
public class MyWall extends Wall {
    public MyWall(String name){ super(name); }

    // ✅ 必须 extends 某个 Building 子类（WallBuild / Building）
    public class MyWallBuild extends WallBuild {
        @Override
        public void updateTile(){
            super.updateTile();
            // 自定义行为
        }
    }
}
```

---

#### 场景 2：一个 Block 声明多个 Building 内部类（只绑第一个）

**错误写法：**

```java
public class MyWall extends Wall {
    public MyWall(String name){ super(name); }

    // ❌ Structs.find 只取第一个，后面这个永远绑不上
    public class FirstBuild extends WallBuild { }
    public class SecondBuild extends WallBuild { }  // 永远不会被绑定
}
```

**原因：** `Structs.find(...)` 返回列表中第一个匹配项。`getDeclaredClasses()` 的顺序不保证，但通常按源码声明顺序。**第二个及以后的 Building 内部类会被静默忽略。**

**正确做法：** 一个 Block 只声明一个 Building 内部类。需要多种行为就拆成多个 Block 类。

---

#### 场景 3：内部类写在 Block 子类体外

**错误写法：**

```java
// ❌ 这是顶级类，不是内部类，反射找不到
public class MyWallBuild extends WallBuild { }

public class MyWall extends Wall {
    public MyWall(String name){ super(name); }
    // 没有内部类
}
```

**结果：** `MyWall` 类上 `getDeclaredClasses()` 返回空 → 向上找父类 → 找到 `Wall` 的 `WallBuild` → 绑定的是 `WallBuild` 而不是你的 `MyWallBuild`。

**正确写法：** Building 类必须**写在 Block 类的花括号里面**，成为它的内部类。

---

#### 场景 4：匿名 Block 想自定义 Building → 需手动 `buildType = MyBuild::new`

**错误写法（以为自动绑定）：**

```java
// ❌ 匿名类 + 自定义 Build，自动绑定会回退到 WallBuild
myWall = new Wall("my-wall"){{
    requirements(Category.defense, with(Items.copper, 6));
}};

// 你另外写了个 MyWallBuild，但它不会被自动绑定
public class MyWallBuild extends WallBuild { }
```

**结果：** `new Wall("my-wall"){{...}}` 是匿名类 → 跳父类 Wall → 绑定 WallBuild → 你的 `MyWallBuild` 被无视。

**正确写法（手动指定）：**

```java
// 方式 A：单独写一个命名类
public class MyWall extends Wall {
    public MyWall(String name){ super(name); }
    public class MyWallBuild extends WallBuild { }  // 自动绑定
}

// 方式 B：匿名类里手动赋值 buildType
myWall = new Wall("my-wall"){{
    requirements(Category.defense, with(Items.copper, 6));
    buildType = MyWallBuild::new;  // ← Block.java:403 "Set manually if modded"
}};
```

---

### 五、验证技巧

**1. 打印 buildType / subclass：**

```java
// 在 loadContent() 之后，打印一下确认绑定结果
Log.info("Block: " + myWall.name + ", buildType=" + myWall.buildType.get().getClass().getSimpleName());
Log.info("Block subclass: " + myWall.subclass.getSimpleName());
```

如果 `buildType` 输出是 `Building`，说明兜底了，你的内部类没绑上。如果输出是 `MyWallBuild`，说明绑定成功。

**2. 游戏内放置观察：**

放一块你自定义的方块，如果覆写的 `updateTile()` 或 `draw()` 没被调用，十有八九是绑定失败。在 `updateTile()` 第一行加个 `Log.info("updateTile called")`，看游戏控制台有没有输出。

---

### 六、完整正确示例

一个继承 Wall 的自定义类，内部类 extends WallBuild，覆写 `updateTile()`：

```java
package com.example.mod.blocks;

import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.defense.Wall.WallBuild;

public class MyWall extends Wall {
    public MyWall(String name) {
        super(name);
    }

    // ✅ 这个内部类会被 initBuilding() 自动反射绑定
    // 绑定成功的三个条件：
    //   1. 它是 MyWall 的内部类（写在花括号里）
    //   2. 它 extends 某个 Building 子类（这里是 WallBuild）
    //   3. MyWall 不是匿名类（或者匿名类里你手动赋了 buildType）
    public class MyWallBuild extends WallBuild {
        private int tickCount = 0;

        @Override
        public void updateTile() {
            super.updateTile();  // 必须调用父类，否则基础行为全丢

            tickCount++;
            // 每 120 帧（2 秒）输出一次
            if(tickCount % 120 == 0){
                // 自定义行为：这里只是演示
            }
        }

        @Override
        public void draw() {
            super.draw();
            // 自定义绘制
        }
    }
}
```

**注册使用：**

```java
// ModBlocks.java
public static Wall myWall;

public static void load(){
    myWall = new MyWall("my-wall");
    // 注意：这里用 new MyWall("my-wall")，不是 new Wall("my-wall"){{...}}
    // 因为 MyWall 是命名类，内部类 MyWallBuild 会被自动绑定
}
```

**绑定成功说明：**
- `new MyWall("my-wall")` 调用 MyWall 构造函数 → 调用 super(name) → 进入 Wall 构造函数 → 调用 `initBuilding()`
- `getClass()` 返回 `MyWall.class`（不是匿名类）
- `getDeclaredClasses()` 返回 `[MyWallBuild.class]`
- 检查 `Building.class.isAssignableFrom(MyWallBuild.class)` → true（WallBuild extends BuildingComp implements Building）
- 绑定 `buildType = () -> new MyWallBuild(this)`
- 游戏中放置方块时，调用 `block.buildType.get()` → new MyWallBuild(this) → 你的自定义逻辑生效

---

## 附录：快速索引

按字母序列出所有类名和对应分类：

| 类名 | 分类 |
|------|------|
| Battery | power |
| ConsumeGenerator | power |
| Conduit | liquid |
| Conveyor | distribution |
| Door | defense |
| Drill | production |
| Floor | environment |
| ForceProjector | defense |
| Fx | 特效 |
| GenericCrafter | production |
| ItemBridge | distribution |
| ItemSource | sandbox |
| ItemTurret | turrets |
| Junction | distribution |
| LiquidRouter | liquid |
| LiquidTurret | turrets |
| MendProjector | defense |
| OreBlock | environment |
| OverdriveProjector | defense |
| PayloadConveyor | payloads |
| PayloadMassDriver | payloads |
| PowerGenerator | power |
| PowerNode | power |
| PowerSource | sandbox |
| PowerTurret | turrets |
| Prop | environment |
| Pump | liquid |
| Reconstructor | units |
| Router | distribution |
| Sorter | distribution |
| StaticWall | environment |
| StatusEffect | 状态效果 |
| StorageBlock | storage |
| Turret | turrets |
| Unloader | storage |
| UnitFactory | units |
| Wall | defense |

---

> 本参考表基于 Mindustry v159.7（HEAD `b3317f3`）。所有源码路径相对 `Mindustry/core/src/`。游戏版本升级后行号可能偏移，但 API 结构不变。

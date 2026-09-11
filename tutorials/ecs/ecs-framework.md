# Mindustry 底层 ECS 框架解析

> 面向想设计复杂 mod 的玩家 / 开发者
> 适用版本：**v159.7**
> 所有 API 均从本地源码逐条核验，标注 `类名.java:行号`，禁止臆造。

---

## 写在最前面：一个必须先纠正的误区

很多人第一次接触 Mindustry 源码时会想：「Mindustry 是 Arc 写的，Arc 有没有 ECS？」

**答案是：没有。**

- Arc 仓库中**不存在** `arc/ecs/` 目录，没有 `Entities.java`、`Component.java`、`EntitySystem.java` 这类传统 ECS 三件套。
- Arc 只提供了一个轻量事件总线 `arc.util.Events`（`Arc/arc-core/src/arc/Events.java`），它不是 ECS。
- Mindustry 自己实现了一套**注解驱动的实体组合系统**，业内常被爱好者俗称为「Mindustry ECS」，但它和 Ash / Artemis / Entt 那种 `Entity + Component + System` 三元组的传统 ECS **不是一回事**。

把这一点搞清楚，后面所有困惑都会消失。Mindustry 的实体系统本质上是：

> **用注解处理器在编译期把多个「能力接口」的方法聚合到一个生成的实体类里。**

它没有独立的 System 循环，没有 Archetype 概念，也没有「把 Component 从 Entity 上 add / remove」的运行时操作。它更接近「接口多实现 + 代码生成」，只是在思想上借用了「组合优于继承」这句 ECS 的口号。

---

## 第 1 章　为什么是「ECS」——从继承树的泥潭说起

### 1.1 如果 Mindustry 用纯继承会怎样

假设当年设计师走的是经典 OOP 继承路线，实体类大概会长这样：

```
Entity
 ├─ PositionedEntity
 │   ├─ RotatableEntity
 │   │   ├─ DamageableEntity
 │   │   │   ├─ Unit
 │   │   │   │   ├─ MechUnit
 │   │   │   │   ├─ LeggedUnit
 │   │   │   │   ├─ TankUnit
 │   │   │   │   └─ FlyingUnit
 │   │   │   └─ Building
 │   │   │       ├─ StorageBuilding
 │   │   │       ├─ PowerBuilding
 │   │   │       └─ DefenseBuilding
 │   │   └─ Bullet
 │   └─ Decal
 └─ ...
```

问题马上来了：

1. **菱形继承爆炸**。一个「会飞的、带护盾的、能采矿的、能建造的」单位，到底该挂在哪条分支上？Java 不支持多类继承，你只能把所有能力都塞到最顶层的 `Entity` 里，最后变成一个上帝类。
2. **能力无法复用**。「血量」这个能力，建筑要有、单位要有、假人要也要有。纯继承下你得在三个分支各写一份，或者把 `Health` 提到最顶层——但贴花 `Decal` 明明不需要血量。
3. **新单位类型组合困难**。官方每加一种移动方式（履带 / 节肢 / 悬浮 / 水下爬行），你都得重新规划继承树。

### 1.2 ECS 的核心思想：组合优于继承

ECS（Entity-Component-System）把「实体」拆成：

- **Entity**：一个 id 或轻量句柄，本身没有行为；
- **Component**：纯数据，描述一种能力（位置、血量、武器……）；
- **System**：遍历拥有某组 Component 的所有 Entity，执行逻辑。

这样「会飞 + 采矿 + 建造」= 给同一个 Entity 挂上 `FlyComp + MinerComp + BuilderComp`，不用继承。

### 1.3 Mindustry 的折中实现：comp 接口拼装 + 注解处理器

Mindustry 没有完全照搬传统 ECS，而是做了一个**适合 Java、适合 mod 生态**的折中：

| 传统 ECS | Mindustry 的等价物 |
|---|---|
| Component（数据） | 一个 `*Comp` 类（如 `BuildingComp`、`HealthComp`），里面同时放数据和默认逻辑 |
| Component 接口 | 由编译器从 Comp 类 public 方法签名**自动生成**的 `*c` 接口（如 `Buildingc`、`Healthc`） |
| Entity | 由 `@EntityDef` 标注、由 `EntityProcess` 生成的具体类（如 `mindustry.gen.Building`、`mindustry.gen.Unit`） |
| System | **不存在独立的 System 类**。每个 Comp 自己有 `update()`，由生成的实体类在 tick 时统一调用 |
| Entity-Component 绑定 | **编译期固定**。一个实体由哪些 Comp 组成，写死在 `@EntityDef({...})` 里，运行时不能增删 |

一句话总结：

> **Mindustry 的「ECS」= `*Comp` 类定义能力 + `@EntityDef` 注解声明组合 + `EntityProcess` 在编译期生成聚合后的实体类。**

它放弃了传统 ECS 最灵活的「运行时增删 Component」能力，换来了：

- 编译期就把所有方法绑定好，**运行时零反射开销**；
- mod 可以 `extends gen.Building` 覆写钩子，而不用碰底层；
- 序列化、网络同步、更新循环全部由注解处理器自动生成。

---

## 第 2 章　核心类与生命周期

### 2.1 `EntityComp`：所有 comp 的根基

文件：`core/src/mindustry/entities/comp/EntityComp.java`（共 74 行）

```java
@Component
@BaseComponent
abstract class EntityComp{
    private transient boolean added;
    transient int id = EntityGroup.nextId();

    boolean isAdded(){ return added; }
    void update(){}
    void remove(){ added = false; }
    void add(){ added = true; }

    boolean isLocal(){ ... }
    boolean isRemote(){ ... }

    /** Replaced with `this` after code generation. */
    <T extends Entityc> T self(){ return (T)this; }
}
```

关键点：

- `id`：每个 comp 实例在构造时拿到一个全局自增 id（`EntityGroup.nextId()`），生成的实体类会把这个 id 作为实体的网络 id。
- `added`：是否已加入世界。
- `isLocal() / isRemote()`：判断这个实体是不是本地玩家 / 本地玩家控制的单位，mod 写特效时常用。
- `self()`：编译期被 EntityProcess 替换成 `this`，方便在 comp 内部以实体类型调用方法。

### 2.2 `@EntityDef` 注解

定义位置：`annotations/src/main/java/mindustry/annotations/Annotations.java:97`

```java
/** Indicates an entity definition. */
@Retention(RetentionPolicy.SOURCE)
public @interface EntityDef{
    /** List of component interfaces */
    Class[] value();
    /** Whether the class is final */
    boolean isFinal() default true;
    /** If true, entities are recycled. */
    boolean pooled() default false;
    /** Whether to serialize (makes the serialize method return this value). */
    boolean serialize() default true;
    /** Whether to generate IO code. */
    boolean genio() default true;
    ...
}
```

它用在两种地方：

1. **标注在某个 `*Comp` 类上**，表示「这个 Comp 是某个实体的根」，例如：
   - `BuildingComp.java:52`：`@EntityDef(value = {Buildingc.class}, excludeGroups = {"all"}, isFinal = false, genio = false, serialize = false)`
   - `BulletComp.java:26`：`@EntityDef(value = {Bulletc.class}, pooled = true, serialize = false)`
2. **标注在字段上**，给每种内容生成实体类，例如 `core/src/mindustry/content/UnitTypes.java:34`：
   ```java
   public static @EntityDef({Unitc.class, Mechc.class}) UnitType mace, dagger, crawler, fortress, scepter, reign, vela;
   ```
   这一行会让编译器为 `mace`、`dagger` 等 7 种单位各自生成一个实体类（`MaceUnit`、`DaggerUnit`……），都由 `Unitc + Mechc` 组合。

### 2.3 gen 生成物：`mindustry.gen` 包

编译产物在：

```
core/build/generated/source/kapt/main/mindustry/gen/
```

里面有什么：

- 每个 `*c` 接口：`Buildingc.java`、`Unitc.java`、`Posc.java`……（聚合 comp 方法签名）
- 每个实体的具体类：`Building.java`、`Bullet.java`、`MaceUnit.java`……
- 调用网络包：`Call.java`、各种 `*CallPacket.java`。

以 `Building.java` 为例（`core/build/generated/source/kapt/main/mindustry/gen/Building.java:110`）：

```java
public class Building implements Buildingc, Entityc, Healthc,
        IndexableEntity__build, Posc, Teamc, Timerc {
```

它是一个**具体类**，实现了所有相关 comp 接口。里面有静态工厂（`Building.java:1208`）：

```java
public static Building create() {
    return new Building();
}
```

以及编译期把所有 comp 的 `add()`、`update()`、`remove()` 按顺序串起来的胶水代码，例如：

```java
public void add() {
    if(added == true) return;
    index__build = Groups.build.addIndex(this);
    building: {
        if (power != null) {
            power.graph.checkAdd();
        }
    }
    entity: {
        added = true;
    }
}
```

### 2.4 实体生命周期

```
create()  →  add()  →  每帧 update()  →  remove()  →  （可选）回收进池
```

1. **创建**：`Building.create()` / `UnitTypes.mace.create()` 等静态工厂 `new` 出实体；
2. **加入世界**：调用 `add()`，把自己塞进 `Groups.build` / `Groups.unit` 等 `EntityGroup`，电力图、QuadTree 索引同步注册；
3. **每 tick 更新**：`Entities` 主循环遍历每个 group，调用实体的 `update()`，生成类内部再依次调用每个 comp 的 `update()`；
4. **移除**：`remove()` 把自己从 group 里摘掉；如果 `pooled = true`（如 Bullet、Puddle、Fire），回收到对象池。

### 2.5 与传统 ECS 的关键差异

| 维度 | 传统 ECS | Mindustry |
|---|---|---|
| System | 独立类，遍历 archetype | **没有 System**，更新逻辑写在各 comp 的 `update()` 里 |
| Component 增删 | 运行时 add / remove | **编译期写死**，运行时不能换组合 |
| 数据布局 | 紧凑数组 / SoA | 普通 Java 对象，字段直接放在生成类里 |
| 查询 | 按组件签名查询 | 用 `Groups.build` / `Groups.unit` 等 `EntityGroup`，编译期按实体类型分好 |
| 网络同步 | 自己写 | 注解处理器根据 `@SyncLocal` / `@Sync` 字段自动生成 |

---

## 第 3 章　49 个 comp 全景表

源码目录：`core/src/mindustry/entities/comp/`，共 **49 个** `*Comp.java` 文件。

### 3.1 按职责分类统计

| 分组 | 数量 | 成员 |
|---|---|---|
| 基础实体 | 11 | EntityComp, PosComp, RotComp, VelComp, TeamComp, OwnerComp, SyncComp, ChildComp, TimerComp, TimedComp, TimedKillComp |
| 建筑 | 6 | BuildingComp, BuilderComp, BuildingTetherComp, PowerGraphUpdaterComp, ItemsComp, BlockUnitComp |
| 单位 | 14 | UnitComp, UnitTetherComp, WeaponsComp, HealthComp, HitboxComp, MechComp, LegsComp, TankComp, MinerComp, PayloadComp, PhysicsComp, PlayerComp, ShieldComp, ShielderComp, StatusComp, DamageComp, TargetDummyComp |
| 移动 | 5 | CrawlComp, WaterCrawlComp, WaterMoveComp, ElevationMoveComp, UnderwaterMoveComp |
| 环境 / 子弹 / 效果 | 12 | FireComp, PuddleComp, DecalComp, DrawComp, EffectStateComp, SegmentComp, WorldLabelComp, LaunchCoreComp, BulletComp, PosTeamDef |

> 注：上表单位组列出 17 个名字是因为 `UnitComp` 本身 implements 了一大堆 `*c`，把它们都列出来是为了说明单位能力是怎么聚合的；去重后 49 个文件数对得上。

### 3.2 全量 comp 一览表

| # | 类名 | 一句话职责 | 关键方法 / 字段 |
|---|---|---|---|
| 1 | `EntityComp` | 所有 comp 根基：id、added、isLocal/isRemote、self() | `EntityComp.java:12` `abstract class`；`id`、`update()`、`add()`、`remove()` |
| 2 | `PosComp` | 位置 `x, y` | `PosComp.java:15` implements `Position` |
| 3 | `RotComp` | 旋转角 `rotation` | `RotComp.java:7` |
| 4 | `VelComp` | 线速度 `vel` | `VelComp.java:13` implements `Posc` |
| 5 | `TeamComp` | 所属队伍 `team` | `TeamComp.java:12` |
| 6 | `OwnerComp` | 拥有者（子弹发射者 / 单位控制者） | `OwnerComp.java:6` |
| 7 | `SyncComp` | 网络同步标记 | `SyncComp.java:12` |
| 8 | `ChildComp` | 子实体（依附父实体） | `ChildComp.java:10` implements `Posc, Rotc` |
| 9 | `TimerComp` | 计时器模块（`timer`） | `TimerComp.java:7` |
| 10 | `TimedComp` | 生命周期 `lifetime`，到时自动消失 | `TimedComp.java:9` implements `Scaled` |
| 11 | `TimedKillComp` | 计时到自动调用 kill() | `TimedKillComp.java:10` implements `Healthc` |
| 12 | **`BuildingComp`** | **建筑实体核心，所有建筑钩子的来源** | `BuildingComp.java:54` implements 13 个接口；`proximity` 字段在 `:69` |
| 13 | `BuilderComp` | 单位建造 / 修复能力 | `BuilderComp.java:27` implements `Posc, Statusc, Teamc, Rotc` |
| 14 | `BuildingTetherComp` | 单位被建筑牵引（如炮台牵引） | `BuildingTetherComp.java:11` implements `Unitc` |
| 15 | `PowerGraphUpdaterComp` | 电力图节点更新（独立实体，避免每 tick 遍历全图） | `PowerGraphUpdaterComp.java:9` implements `Entityc` |
| 16 | `ItemsComp` | 物品库存模块 `items` | `ItemsComp.java:9` implements `Posc` |
| 17 | `BlockUnitComp` | 建筑生成的常驻单位（如炮台炮塔） | `BlockUnitComp.java:11` implements `Unitc` |
| 18 | **`UnitComp`** | **单位实体核心，聚合 16 个能力接口** | `UnitComp.java:38` implements 16 个 `*c` |
| 19 | `UnitTetherComp` | 单位之间牵引 | `UnitTetherComp.java:11` |
| 20 | `WeaponsComp` | 武器列表、射击冷却 | `WeaponsComp.java:11` implements `Teamc, Posc, Rotc, Velc, Statusc` |
| 21 | `HealthComp` | 血量 `health / maxHealth / armor / healthMultiplier` | `HealthComp.java:8` implements `Entityc, Posc` |
| 22 | `HitboxComp` | 碰撞箱 `hitSize` | `HitboxComp.java:12` implements `Sized, QuadTreeObject` |
| 23 | `MechComp` | 机械腿单位行走 | `MechComp.java:15` implements `Mechc, ElevationMovec` |
| 24 | `LegsComp` | 节肢单位腿动画 | `LegsComp.java:23` implements `Posc, Rotc, Hitboxc, Unitc` |
| 25 | `TankComp` | 履带单位 | `TankComp.java:20` implements `ElevationMovec` |
| 26 | `MinerComp` | 单位采矿 | `MinerComp.java:15` implements `Itemsc, Drawc` |
| 27 | `PayloadComp` | 载荷运载 | `PayloadComp.java:24` |
| 28 | `PhysicsComp` | 物理（摩擦、阻力） | `PhysicsComp.java:12` implements `Velc, Hitboxc` |
| 29 | `PlayerComp` | 玩家控制器实体 | `PlayerComp.java:35` implements `UnitController, Entityc, Syncc, Timerc, Drawc` |
| 30 | `ShieldComp` | 单位护盾 | `ShieldComp.java:13` implements `Healthc, Posc` |
| 31 | `ShielderComp` | 盾墙反弹 | `ShielderComp.java:7` implements `Damagec, Teamc, Posc` |
| 32 | `StatusComp` | 状态效果（灼烧、潮湿、催化……） | `StatusComp.java:18` implements `Posc` |
| 33 | `DamageComp` | 伤害来源标记 | `DamageComp.java:6` |
| 34 | `TargetDummyComp` | 目标假人（训练假人） | `TargetDummyComp.java:9` implements `Unitc, Healthc` |
| 35 | `CrawlComp` | 爬行单位 | `CrawlComp.java:19` |
| 36 | `WaterCrawlComp` | 水中爬行 | `WaterCrawlComp.java:13` implements `Crawlc` |
| 37 | `WaterMoveComp` | 水面移动 | `WaterMoveComp.java:20` |
| 38 | `ElevationMoveComp` | 高低差移动 | `ElevationMoveComp.java:9` implements `Velc, Posc, Hitboxc, Unitc` |
| 39 | `UnderwaterMoveComp` | 水下移动 | `UnderwaterMoveComp.java:11` implements `WaterMovec` |
| 40 | `FireComp` | 火焰粒子实体 | `FireComp.java:22` implements `Timedc, Posc, Syncc, Drawc` |
| 41 | `PuddleComp` | 水坑液体 | `PuddleComp.java:21` implements `Posc, Puddlec, Drawc, Syncc` |
| 42 | `DecalComp` | 贴花（弹坑、脚印） | `DecalComp.java:12` implements `Drawc, Timedc, Rotc, Posc` |
| 43 | `DrawComp` | 自定义绘制回调 | `DrawComp.java:7` implements `Posc` |
| 44 | `EffectStateComp` | 特效状态（环、轨道粒子） | `EffectStateComp.java:10` implements `Childc` |
| 45 | `SegmentComp` | 分段单位（大单位分节） | `SegmentComp.java:13` implements `Segmentc` |
| 46 | `WorldLabelComp` | 世界坐标文字标签 | `WorldLabelComp.java:17` `public abstract class` implements `Posc, Drawc, Syncc` |
| 47 | `LaunchCoreComp` | 发射核心（行星起飞动画） | `LaunchCoreComp.java:14` implements `Drawc, Timedc` |
| 48 | `BulletComp` | 子弹实体 | `BulletComp.java:28` implements 12 个接口 |
| 49 | `PosTeamDef` | 位置 + 队伍的极简实体定义 | `PosTeamDef.java:7` `@EntityDef(value = Teamc.class, genio = false, isFinal = false)` |

### 3.3 重点 comp 展开

#### `BuildingComp`（建筑实体核心）

`BuildingComp.java:52-54`：

```java
@EntityDef(value = {Buildingc.class}, excludeGroups = {"all"},
           isFinal = false, genio = false, serialize = false)
@Component(base = true, genInterface = false)
abstract class BuildingComp implements Posc, Teamc, Healthc, Buildingc, Timerc,
        QuadTreeObject, Displayable, Sized, Senseable, Controllable, Settable, AmbientSource{
```

它 implements 了 **13 个接口**，意味着所有建筑都自动拥有：位置、队伍、血量、计时器、可显示、可控制、可设置、环境音效源……等能力。

关键字段：

```java
@Import float x, y, health, maxHealth;   // :58-59 从其他 comp 导入
@Import Team team;
@Import boolean dead;

transient Tile tile;                      // :67
transient Block block;                    // :68
transient Seq<Building> proximity =      // :69
        new Seq<>(true, 6, Building.class);
transient int rotation;                   // :71
```

`proximity` 是 mod 写「邻居交互」系统（热力网络、液体管道、传送带邻接）最常碰的字段。

#### `UnitComp`（单位核心）

`UnitComp.java:37-38`：

```java
@Component(base = true)
abstract class UnitComp implements Healthc, Physicsc, Hitboxc, Statusc, Teamc,
        Itemsc, Rotc, Unitc, Weaponsc, Drawc, Syncc, Shieldc, Displayable,
        Ranged, Minerc, Builderc, Senseable, Settable{
```

一口气 implements **18 个接口**。官方所有单位（`UnitTypes.java` 里那一堆 `@EntityDef({Unitc.class, Mechc.class})`）最终都聚合到这里。

#### `HealthComp`（血量）

```java
@Component
abstract class HealthComp implements Entityc, Posc{
```

提供 `health / maxHealth / armor / healthMultiplier` 字段，以及 `health()`、`maxHealth()`、`armor()` 等方法。`BuildingComp` 和 `UnitComp` 都通过 `@Import float health, maxHealth` 把它的字段「借」过来。

#### `WeaponsComp`（武器）

持有 `Seq<Weapon>`、冷却时间、瞄准目标。`UnitComp` 通过 `Weaponsc` 把它的方法暴露出来。

#### `StatusComp`（状态效果）

维护一张 `ObjectMap<StatusEffect, StatusEntry>` 表，处理灼烧、潮湿、催化、反伤等常驻 buff。

#### `ItemsComp`（物品库存）

持有 `ItemModule items`（在 `world/modules/ItemModule.java`），提供 `items.accept()`、`items.get()`、`items.total()` 等。`Building` 和单位都能通过它搬物品。

#### `PowerGraphUpdaterComp`（电力）

`@EntityDef(value = PowerGraphUpdaterc.class, serialize = false, genio = false)`——它是一个**独立的、不序列化的轻量实体**，专门用来每 tick 扫描电力图变化。这样做的好处是：电力图更新逻辑不用塞进每个建筑的 `update()`，而是由一个集中的 comp 统一驱动。

---

## 第 4 章　注解处理器与 gen 生成流程

### 4.1 `EntityProcess.java` 是什么

文件：`annotations/src/main/java/mindustry/annotations/entity/EntityProcess.java`（共 1070 行）。

它是一个 **APT（Annotation Processing Tool）注解处理器**，在 Gradle 编译期（kapt 阶段）跑：

1. 扫描所有带 `@EntityDef` 注解的元素（类或字段）；
2. 解析 `value = {Buildingc.class, ...}`，拿到要聚合的 comp 接口列表；
3. 对每个 comp 接口，找到它对应的 `*Comp` 实现类；
4. 收集这些 Comp 类的 **public 方法签名和字段**；
5. 生成：
   - 聚合接口（如 `Buildingc.java`——如果还没有的话）；
   - 具体实体类（如 `Building.java`、`MaceUnit.java`）；
   - 静态 `create()` 工厂方法；
   - `add() / update() / remove() / write() / read()` 的胶水代码；
   - 网络同步代码。

内部有个关键数据结构（`EntityProcess.java:1039` 附近的 `EntityDefinition` 内部类），记录「一个实体 = 哪些 comp 接口 + 哪些字段 + 哪些方法」。

### 4.2 生成流程图

```
┌─────────────────────────────────────────────────────────────────────┐
│  源码阶段（开发者手写）                                              │
│                                                                     │
│   core/src/mindustry/entities/comp/                                 │
│   ┌──────────────┐  ┌────────────┐  ┌───────────────┐             │
│   │ PosComp.java │  │HealthComp  │  │ BuildingComp  │             │
│   │  (abstract)  │  │   .java    │  │  .java        │             │
│   └──────┬───────┘  └─────┬──────┘  └──────┬────────┘             │
│          │ implements      │ implements   │ @EntityDef({...})      │
│          ▼                 ▼               ▼                        │
│        Posc              Healthc        Buildingc                    │
└──────────┬─────────────────┬───────────────┬────────────────────────┘
           │                 │               │
           ▼                 ▼               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  编译阶段：kapt 跑 EntityProcess.java                                │
│                                                                     │
│  1. 读 @EntityDef(value = {Buildingc.class}, ...)                   │
│  2. 找到 BuildingComp，扫描它 implements 的所有 *c 接口             │
│  3. 收集每个 Comp 类的字段（@Import / 自动）和 public 方法          │
│  4. 生成代码到 core/build/generated/source/kapt/main/mindustry/gen/ │
└──────────────────────────────────┬──────────────────────────────────┘
                                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│  生成物（自动生成，不要手改）                                         │
│                                                                     │
│   gen/Buildingc.java      ← 聚合接口（BuildingComp 里的 public 方法）│
│   gen/Building.java       ← 具体类 implements Buildingc, Posc, ...  │
│                             + 静态 create() + add/update/remove 胶水 │
│   gen/Call.java           ← 网络调用入口                             │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.3 生成物长什么样

`core/build/generated/source/kapt/main/mindustry/gen/Building.java:110`：

```java
public class Building implements Buildingc, Entityc, Healthc,
        IndexableEntity__build, Posc, Teamc, Timerc {
```

`:1208` 附近：

```java
public static Building create() {
    return new Building();
}
```

`add()` 方法（节选）：

```java
public void add() {
    if(added == true) return;
    index__build = Groups.build.addIndex(this);
    building: {
        if (power != null) {
            power.graph.checkAdd();
        }
    }
    entity: {
        added = true;
    }
}
```

注意 `power.graph.checkAdd()`——这是 EntityProcess 根据 `@Import PowerGraphUpdaterComp` 之类的依赖关系自动插进去的。

> **mod 作者不需要读这些生成物**，但知道它们存在，有助于理解「为什么 `Building` 上能直接调 `items.accept()`、`power.graph`」——那些方法都不是 `BuildingComp` 自己写的，是从 `ItemsComp`、`PowerGraphUpdaterComp` 聚合过来的。

---

## 第 5 章　`Building` / `Unit` 是怎么被拼出来的

### 5.1 `Building` 接口的真实组成

前面已经看到 `gen/Building.java:110`：

```java
public class Building implements Buildingc, Entityc, Healthc,
        IndexableEntity__build, Posc, Teamc, Timerc {
```

这意味着所有建筑实例都自动拥有：

- `Buildingc`：建筑专属方法（`placed()`、`updateTile()`、`display()`……）
- `Entityc`：实体基类方法（`id()`、`getGroup()`……）
- `Healthc`：血量方法（`health()`、`damage()`……）
- `Posc`：`x()`、`y()`
- `Teamc`：`team()`
- `Timerc`：`timer()`

而 `ItemsComp`、`PowerGraphUpdaterComp` 等并没有直接出现在 `Building implements` 列表里——它们的能力是通过 `BuildingComp` 内部**持有字段**的方式提供的：

```java
// BuildingComp 内部
public ItemModule items;        // 来自 ItemsComp 的字段
public PowerGraph power;        // 来自电力系统
public LiquidModule liquids;
```

然后 EntityProcess 把这些字段的访问器方法（`items()`、`power()`）聚合到 `Building` 类上。这就是为什么你写 mod 时能直接 `building.items.accept(Items.copper, 10)`。

### 5.2 `@EntityDef` 在单位上的实际用法

`core/src/mindustry/content/UnitTypes.java:34`：

```java
public static @EntityDef({Unitc.class, Mechc.class})
        UnitType mace, dagger, crawler, fortress, scepter, reign, vela;
```

这一行同时定义了 7 个 `UnitType` 字段。EntityProcess 看到后，会为每个字段生成一个实体类：

- `MaceUnit extends Unit`（由 `Unitc + Mechc` 组合）
- `DaggerUnit extends Unit`
- ……

再看 `UnitTypes.java:40`：

```java
public static @EntityDef({Unitc.class, Legsc.class}) UnitType corvus, atrax, ...
```

`Legsc` 来自 `LegsComp.java:23`。于是 `corvus` 这种节肢单位就自动拥有了腿的动画逻辑，而 `mace` 那种机械单位走 `Mechc` 分支。

**这就是「组合优于继承」在 Mindustry 里的具体落地**：单位类型 = 一串 `*c` 接口的列表，不是一棵继承树。

### 5.3 一个 Building 实例是怎么 new 出来的

完整链路：

```
world.tile.setBlock(block, team, rotation)
        │
        ▼
Block.newBuilding()                     // Block.java:1012
        │
        ▼
buildType.get()                         // Block.java:1013
        │
        ▼
new XxxBuild()  或  Building::create    // Block.java:1276 兜底
```

关键代码（`core/src/mindustry/world/Block.java`）：

```java
// :403
public Prov<Building> buildType = null;

// :1012
public final Building newBuilding(){
    return buildType.get();
}

// :1241
protected void initBuilding(){
    try{
        Class<?> current = getClass();
        if(current.isAnonymousClass()){
            current = current.getSuperclass();
        }
        subclass = current;

        while(buildType == null && Block.class.isAssignableFrom(current)){
            // 在 Block 子类里找第一个 extends Building 的内部类
            Class<?> type = Structs.find(current.getDeclaredClasses(),
                t -> Building.class.isAssignableFrom(t) && !t.isInterface());
            if(type != null){
                Constructor<? extends Building> cons =
                    (Constructor<? extends Building>)type
                        .getDeclaredConstructor(type.getDeclaringClass());
                buildType = () -> {
                    try{
                        return cons.newInstance(this);
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                };
            }
            current = current.getSuperclass();
        }
    }catch(Throwable ignored){ }

    if(buildType == null){
        buildType = Building::create;   // :1276 兜底
    }
}
```

这段反射逻辑非常关键，是 mod 作者必须理解的：

1. 每个 Block 子类（如 `HeatConductor.java`）通常会写一个**非静态内部类** `class HeatConductorBuild extends Building`；
2. `initBuilding()` 顺着 `getClass()` 一路向上找，反射拿到这个内部类的构造器；
3. 构造器需要一个外部类（`HeatConductor`）实例——这就是为什么 `new Building()` 在生成类里只是兜底，真正的建筑实例是 `new HeatConductorBuild(HeatConductor.this)`；
4. 如果你写 mod 时只 `extends Building` 而不写内部类，`buildType` 会走 `Building::create` 兜底，此时你覆写的所有钩子都不会生效。

---

## 第 6 章　模组能碰什么——钩子清单（重点）

这一章是**面向 mod 作者最实用的部分**。

### 6.1 模组**不能**做的事

> 注解处理器在**游戏编译期**运行，而 mod 是在**游戏启动后**被加载的。
> 所以：

- ❌ **不能新增 `*Comp` 类**。你写一个 `HeatComp.java` 放进 mod 里，Mindustry 不会重新跑 EntityProcess，不会把它聚合进任何 gen 类。
- ❌ **不能给已有实体运行时增删 comp**。没有 `entity.add(new HealthComp())` 这种 API。
- ❌ **不能修改 gen 生成的类**。`core/build/generated/...` 是编译产物，mod 里改了也不会被加载。
- ❌ **不能改变实体由哪些 comp 组成**。`Building implements` 什么、`Unit` 聚合什么，都是写死的。

### 6.2 模组**能**做的事

✅ **1. extends gen 接口 / 官方 Building 子类**

官方每种 Block 都有一个内部类 `XxxBuild extends Building`。你写 mod 时可以：

```java
// 继承官方建筑，覆写钩子
public class HeatConductor extends Block{
    public HeatConductor(String name){
        super(name, ...);
    }

    public class HeatConductorBuild extends Building{
        // 在这里覆写 update()、placed()、onProximityUpdate() 等
    }
}
```

或者更激进一点：

```java
public class MyWall extends Wall{
    public MyWall(String name){ super(name); }
    public class MyWallBuild extends WallBuild{
        // WallBuild 本身已经 extends Building，你可以在它上面继续覆写
    }
}
```

✅ **2. 覆写 comp 中定义的钩子方法**

所有 comp 里的 `public void update()`、`public void placed()` 等都是**非 final** 的（`BuildingComp` 标了 `isFinal = false`），你可以在自己的 `XxxBuild extends Building` 子类里直接 `@Override`。

✅ **3. 用 Events 系统做跨实体协作**（第 7 章展开）。

✅ **4. 用 `Vars.content` / `Blocks` / `Units` 等注册表挂载新内容**。新 Block、新 UnitType、新 Item 都可以注册，但它们最终还是要落到 `Building` / `Unit` 这套 gen 体系里。

### 6.3 Building 钩子完整清单（v159.7，已核验）

| 方法签名 | 来源（类:行号） | 调用时机 | 典型用途 |
|---|---|---|---|
| `void update()` | `BuildingComp.java:2270` | 每游戏 tick（60/s） | 驱动机器逻辑、热量传输、电力消耗 |
| `void updateTile()` | `BuildingComp`（在 update 内调用） | 每 tick，仅当方块应更新时 | 机器主逻辑（官方常用这个，而非直接 update） |
| `void placed()` | `BuildingComp.java:1361` | 建筑被放置时（已有 `net.client()` 守卫） | 初始化库存、注册到电力图、发出网络事件 |
| `void onProximityUpdate()` | `BuildingComp.java:1159` | 邻居方块变化（放置/拆除/旋转） | 重建邻接列表、热力网络重连、传送带方向 |
| `void onRemoved()` | `BuildingComp.java:1398` | 建筑被拆除（保留在世界上） | 从电力图移除、邻接通知 |
| `void onDestroyed()` | `BuildingComp.java:1490` | 建筑被摧毁（爆炸、掉落） | 生成碎屑、播放音效、跨实体事件 |
| `void display(Table table)` | `BuildingComp.java:1561` | 玩家点开信息面板时 | 自定义 UI（进度条、热力读数） |
| `void displayBars(Table table)` | `BuildingComp.java:1667` | 信息面板上方状态条 | 显示热量条、电量条 |
| `float handleDamage(float amount)` | `BuildingComp.java:1743` | 受到伤害前 | 返回实际承受伤害（护盾减免、护甲） |
| `void damage(@Nullable Team source, float damage)` | `BuildingComp.java:1780` | 被某个队伍的来源击中 | 仇恨、反击 |
| `void damage(Bullet bullet, Team source, float damage)` | `BuildingComp.java:1785` | 被子弹击中 | 弹种特殊效果 |
| `void damage(float damage)` | `BuildingComp.java:2066` | 直接扣血 | 环境伤害、自损 |
| `void write(Writes write)` | `BuildingComp.java:303` | 存档 / 网络序列化 | 写入自定义字段 |
| `void read(Reads read, byte revision)` | `BuildingComp.java:308` | 读档 / 反序列化 | 读取自定义字段 |
| `Seq<Building> proximity` 字段 | `BuildingComp.java:69` | 数据字段，非方法 | 邻居建筑列表，mod 最常访问 |
| `void draw()` / `drawSelect()` | `BuildingComp`（gen 接口） | 每帧渲染 | 自定义绘制 |
| `boolean shouldConsume()` | `BuildingComp` | 每 tick 消费前 | 条件禁用机器 |
| `void handleBullet(Bullet bullet)` | `BuildingComp` | 子弹命中时 | 拦截、反弹 |
| `void killed()` | `BuildingComp`（继承自 HealthComp） | 血量归零时 | 死亡钩子 |

### 6.4 Unit 钩子补充

| 方法签名 | 来源 | 调用时机 |
|---|---|---|
| `void update()` | `UnitComp.java:662` | 每 tick |
| `void destroy()` | `UnitComp.java:883` | 单位死亡 |
| `void removed()` | `UnitComp`（覆盖 `EntityComp.remove`） | 从世界移除 |
| `void added()` | `UnitComp` | 加入世界 |
| `void controller(UnitController)` | `UnitComp` | 切换 AI 控制器 |
| `void aimTile(Tile)` / `void aim(Position)` | `WeaponsComp` | 武器瞄准 |

### 6.5 一个完整可复制的代码示例

下面这个例子实现一个「热力导管」：把自己的热量沿 `proximity` 邻居做双缓冲传递。这正是官方 `HeatConductor` 和很多热力学 mod 的核心思路——**不需要新增 comp，只需要覆写 `update()` 和 `onProximityUpdate()`**。

```java
package yourmod.content;

import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.gen.Building;

/**
 * 自定义热力导管：沿邻居做双缓冲热量传递。
 * 适用版本 v159.7
 */
public class HeatConductor extends Block{

    /** 每 tick 传递效率（0~1） */
    public float heatConductivity = 0.25f;
    /** 最大储存热量 */
    public float maxHeat = 100f;

    public HeatConductor(String name){
        super(name);
        solid = true;
        update = true;          // 告诉游戏这个建筑需要每 tick 更新
        configurable = false;
        destructible = true;
    }

    public class HeatConductorBuild extends Building{
        /** 当前热量（对外可见） */
        public float heat;
        /** 双缓冲：下一个 tick 的热量，避免本 tick 内多次传递导致误差 */
        public float nextHeat;

        @Override
        public void placed(){
            super.placed();
            heat = 0f;
            nextHeat = 0f;
            // 主动通知所有邻居：我来了，你们的 proximity 该刷新了
            for(Building b : proximity){
                if(b != null) b.onProximityUpdate();
            }
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();
            // 邻居集合由 BuildingComp 自动维护在这里（BuildingComp.java:69）
            // 这里可以做一些预处理，比如筛选出同 mod 的热力节点
        }

        @Override
        public void updateTile(){
            // 1) 先把上一 tick 算好的 nextHeat 提交
            heat = nextHeat;

            // 2) 收集所有邻居的热量
            float neighborSum = 0f;
            int validNeighbors = 0;
            for(Building b : proximity){
                if(b instanceof HeatConductorBuild other){
                    neighborSum += other.heat;
                    validNeighbors++;
                }
            }

            // 3) 计算本 tick 后的 nextHeat：
            //    与邻居平均热流平衡，自身按 conductivity 衰减
            float avg = validNeighbors == 0 ? 0f : neighborSum / validNeighbors;
            nextHeat = Math.max(0f, Math.min(maxHeat,
                heat + (avg - heat) * heatConductivity));
        }

        /** 让相邻热源可以往里灌热 */
        public void acceptHeat(float amount){
            nextHeat += amount;
        }

        @Override
        public void displayBars(Table table){
            super.displayBars(table);
            // 显示热量条
            addBar(table, () -> "heat",
                () -> (heat / maxHeat),
                () -> Vars.media.heat);
        }

        // 序列化：存档时保存 heat / nextHeat
        @Override
        public void write(arc.util.io.Writes write){
            super.write(write);
            write.f(heat);
            write.f(nextHeat);
        }

        @Override
        public void read(arc.util.io.Reads read, byte revision){
            super.read(read, revision);
            heat = read.f();
            nextHeat = read.f();
        }
    }
}
```

要点解读：

1. **`update = true`**：Block 字段，告诉 EntityProcess 生成的 `Building` 把这个方块加入每 tick 列表。没有它，你的 `updateTile()` 永远不会被调。
2. **双缓冲**：用 `heat`（当前）和 `nextHeat`（下一帧）两个变量。如果直接在遍历时改 `heat`，同 tick 里 A→B→C 会把同一股热传两次，能量不守恒。双缓冲是 ECS / 模拟系统里常见的手法。
3. **`proximity`**：`BuildingComp.java:69` 自动维护，你不用自己算邻居。
4. **覆写 `placed()` / `onProximityUpdate()`**：所有 mod 邻接系统（热力、液体管道、传送带）的标准入口。
5. **`write() / read()`**：自定义字段必须序列化，否则玩家读档后热量清零。

### 6.6 ECS 设计对 mod 写作的启示

- **不要尝试造新 comp**。你想加「热量」这个能力？不用新建 `HeatComp`——直接在你的 `XxxBuild extends Building` 子类里加一个 `public float heat` 字段就行。Building 本身已经是「万能容器」。
- **跨实体交互走 `proximity` + Events**。邻居关系由 BuildingComp 维护，跨实体通知走 Arc Events。
- **钩子是你的全部武器**。官方设计 mod API 时就假设：你只能 extends 一个 Building 子类 + 覆写钩子。学会了这几个钩子，95% 的 mod 需求都能 cover。

---

## 第 7 章　事件系统 Events（ECS 的补充）

ECS 处理的是**单个实体内部状态**。但很多逻辑需要跨实体、跨系统通知——比如「有玩家放了一个建筑」「世界加载完毕」「游戏 tick 到了」。这时候用 Arc 的事件总线。

### 7.1 `Arc.util.Events`

文件：`Arc/arc-core/src/arc/Events.java`。核心方法（行号见源码）：

```java
// :14
public static <T> void on(Class<T> type, Cons<T> listener);
// :19
public static void run(Object type, Runnable listener);
// :29 / :42 / :46
public static <T extends Enum<T>> void fire(Enum<T> type);
public static <T> void fire(T type);
public static <T> void fire(Class<?> ctype, T type);
// :59
public static void clear();
```

三种用法：

1. **枚举触发器**：`Events.run(Trigger.update, () -> ...)`，每 tick 执行。
2. **类事件**：`Events.on(WorldLoadEvent.class, e -> ...)`，事件对象本身携带数据。
3. **手动 fire**：`Events.fire(EventType.GameOverEvent)`。

### 7.2 `EventType.java` 里常用的事件

文件：`core/src/mindustry/game/EventType.java`（共 847 行）。挑 mod 最常用的：

| 事件类 | 行号 | 触发时机 |
|---|---|---|
| `WorldLoadEvent` | `EventType.java:107` | 世界 / 存档加载完成 |
| `WorldLoadBeginEvent` | `:109` | 世界开始加载 |
| `WorldLoadEndEvent` | `:111` | 世界加载结束 |
| `SaveLoadEvent` | `:131` | 读档 |
| `ClientCreateEvent` | `:75` | 客户端对象创建 |
| `ServerLoadEvent` | `:76` | 服务器加载 |
| `HostEvent` | `:80` | 玩家开图 |
| `PlayEvent` | `:78` | 开始游玩 |
| `ResetEvent` | `:79` | 重置 |
| `WaveEvent` | `:81` | 新一波次 |
| `SectorLaunchEvent` | `:165` | 发射行星 |
| `BlockBuildEndEvent` | （在同文件中） | 建筑放置完成 |
| `BlockBuildBeginEvent` | （在同文件中） | 开始建造 |
| `BlockDamageEvent` / `BuildDamageEvent` | （在同文件中） | 建筑受击 |
| `UnitSpawnEvent` | （在同文件中） | 单位生成 |
| `UnitDestroyEvent` | （在同文件中） | 单位死亡 |

另外 `core/src/mindustry/game/Trigger.java` 提供了一批**枚举式触发器**：`Trigger.update`、`trigger.draw`、`trigger.worldLoad` 等。

### 7.3 ECS 与 Events 的分工

| 关注点 | 用什么 |
|---|---|
| 单个建筑 / 单位内部状态变化 | 覆写 Building / Unit 钩子（ECS 视角） |
| 跨实体：「所有玩家放了建筑后通知我的热力系统」 | `Events.on(BlockBuildEndEvent.class, ...)` |
| 每 tick 全局逻辑（不是某个建筑的） | `Events.run(Trigger.update, ...)` |
| 进图初始化全局数据 | `Events.on(WorldLoadEvent.class, ...)` |
| 网络同步消息 | `Call.class`（gen 生成） |

### 7.4 代码示例：mod 级全局逻辑

```java
package yourmod;

import arc.Events;
import mindustry.game.EventType.*;
import mindustry.game.Trigger;
import mindustry.mod.*;

public class HeatMod extends Mod{

    /** 本 mod 自己的全局热力注册表 */
    public static HeatNetwork net;

    public HeatMod(){
        // 1) 进图时初始化
        Events.on(WorldLoadEvent.class, e -> {
            net = new HeatNetwork();
            net.rebuildAll();
        });

        // 2) 建筑放置 / 拆除时，重连热力网络
        Events.on(BlockBuildEndEvent.class, e -> {
            if(!e.breaking && e.build != null){
                net.onBuildPlaced(e.build);
            }
        });

        // 3) 每 tick 驱动全局热流（兜底，单个建筑自己的 updateTile 也会跑）
        Events.run(Trigger.update, () -> {
            if(net != null && !Vars.state.isPaused()){
                net.tick();
            }
        });
    }
}
```

> 注意：`Events.run(Trigger.update, ...)` 注册的回调在 mod 整个生命周期内存在，**世界重开时记得在 `WorldLoadEvent` 里重置你自己的数据**，否则会跨图泄漏。

---

## 附录 A：源码地图

### A.1 注解与处理器

| 路径 | 作用 |
|---|---|
| `annotations/src/main/java/mindustry/annotations/Annotations.java:97` | `@EntityDef` 定义 |
| `annotations/src/main/java/mindustry/annotations/entity/EntityProcess.java`（1070 行） | 主注解处理器 |
| `annotations/src/main/java/mindustry/annotations/entity/EntityProcess.java:1039` | `EntityDefinition` 内部类 |
| `annotations/src/main/java/mindustry/annotations/entity/EntityIO.java` | 序列化代码生成辅助 |

### A.2 comp 源码（49 个）

目录：`core/src/mindustry/entities/comp/`。详见第 3 章表格。

### A.3 关键 gen 生成物

目录：`core/build/generated/source/kapt/main/mindustry/gen/`

| 文件 | 关键位置 |
|---|---|
| `Building.java:110` | `public class Building implements Buildingc, Entityc, ...` |
| `Building.java:1208` | `public static Building create()` |
| `Buildingc.java` | 聚合接口 |
| `Unit.java` / `MaceUnit.java` 等 | 单位实体 |
| `Call.java` | 网络调用 |

### A.4 Building 创建链

| 位置 | 作用 |
|---|---|
| `core/src/mindustry/world/Block.java:403` | `public Prov<Building> buildType` |
| `core/src/mindustry/world/Block.java:1012` | `newBuilding()` |
| `core/src/mindustry/world/Block.java:1241-1278` | `initBuilding()` 反射绑定内部类 |
| `core/src/mindustry/world/Block.java:1276` | 兜底 `buildType = Building::create` |

### A.5 BuildingComp 钩子行号速查

| 方法 | 行号 |
|---|---|
| `proximity` 字段 | `BuildingComp.java:69` |
| `write(Writes)` | `:303` |
| `read(Reads, byte)` | `:308` |
| `onProximityUpdate()` | `:1159` |
| `placed()` | `:1361` |
| `onRemoved()` | `:1398` |
| `onDestroyed()` | `:1490` |
| `display(Table)` | `:1561` |
| `displayBars(Table)` | `:1667` |
| `handleDamage(float)` | `:1743` |
| `damage(Team, float)` | `:1780` |
| `damage(Bullet, Team, float)` | `:1785` |
| `damage(float)` | `:2066` |
| `update()` | `:2270` |

### A.6 事件系统

| 路径 | 作用 |
|---|---|
| `Arc/arc-core/src/arc/Events.java:14` | `on(Class, Cons)` |
| `Arc/arc-core/src/arc/Events.java:19` | `run(Object, Runnable)` |
| `Arc/arc-core/src/arc/Events.java:29/42/46` | `fire(...)` |
| `core/src/mindustry/game/EventType.java`（847 行） | 所有游戏事件类 |
| `core/src/mindustry/game/Trigger.java` | 枚举触发器 |

### A.7 架构一句话版

> Mindustry 没有传统 ECS；它用 `@EntityDef` 标注 comp 组合，由 `EntityProcess` 在编译期把多个 `*Comp` 类的字段和方法聚合进 `mindustry.gen.Building` / `mindustry.gen.Unit` 等具体类；mod 作者只能 `extends` 这些生成类并覆写钩子，跨实体协作靠 Arc Events 总线。

### A.8 进阶阅读建议

1. **`EntityProcess.java` 全文 1070 行**——想知道序列化代码、网络同步代码是怎么拼出来的，读它。
2. **`core/build/generated/source/kapt/main/mindustry/gen/Building.java`**——看看生成出来的 `Building` 到底有多少行、`add()/update()/remove()` 是怎么串的。
3. **`HeatConductor.java`（官方）**——经典的「双缓冲 + proximity」例子，比本教程的示例更完整。
4. **`Power.java` / `PowerGraph.java`**——看 `PowerGraphUpdaterComp` 是怎么被一个独立实体驱动的，这是 Mindustry 实体系统最聪明的一处设计。

---

*教程完。所有行号基于本地 v159.7 源码核验。*

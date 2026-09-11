# 自建 UI（信息类）教程 —— Mindustry Java 模组萌新任务驱动指南

> 适用版本：**Mindustry v159.7**（Java 源码版）
> 阅读对象：已经能写出一个自定义 `Block` + 内部 `Build` 类、但没碰过 UI 的模组萌新。
> 写法约定：每一块都按 **目标 → 完整可复制代码 → 只讲 2~3 个新 API → 坑 → 深入链接** 组织。
> 文中所有 API 均从本地源码逐条核验，标注 `类名.java:行号`，未臆造。

---

## 0. 先搞懂 Mindustry 的信息 UI 是怎么分层的

在动手前，记住这张调用链，后面四块全是它的局部扩展：

```
玩家点了一个建筑
  └─ BuildingComp.display(Table)            ← 第二块要覆写的就是它
       ├─ 顶部：建筑图标 + 名字
       ├─ displayBars(Table)                 ← 第一块的状态条从这里被画出来
       │     └─ 遍历 Block.listBars()
       │            └─ 每个 Bar 来自 Block.addBar(...)   ← 第一块注册
       └─ displayConsumption(...)           ← 默认的电/液/物品消耗

游戏内数据库（图鉴）里看一个建筑
  └─ Block.setStats()                        ← 第三块要覆写的就是它
       └─ stats.add(Stat, StatValue)         ← 用 StatValues.* 造值

玩家想看全局数据
  └─ 你 new 一个 Dialog                       ← 第四块
       └─ dialog.cont 里摆 Table
```

一句话：**状态条走 `addBar`，点建筑弹窗走 `display`，图鉴走 `setStats`，独立大窗口走 `Dialog`。**

---

## 第一块：建筑状态条自定义（温度条 / 热量条 / 效率条）

### 目标

给建筑脚下/信息面板里加一条**自己的进度条**。我们要做一条"温度条"：红色填充，按 `0 ~ maxTempK` 映射成 `0~1`。

### 完整可复制代码

先看一个**最小可运行**的例子（假设你已经有一个 `ExampleBlock extends Block` 和它的 `ExampleBuild extends Building`）：

```java
package com.example.blocks;

import arc.graphics.Color;
import mindustry.world.Block;
import mindustry.world.buildings.*;          // 你的 Build 类
import mindustry.world.meta.StatUnit;

public class ExampleBlock extends Block {

    /** 温度上限（K），0~1 映射的分母 */
    public float maxTempK = 800f;
    /** 当前温度（K），运行时由 Build 写入 */
    public float curTempK = 293f;

    public ExampleBlock(String name) {
        super(name);
        // 不要在这里 addBar！原因见"坑"。
    }

    // ★ 关键：覆写 setBars()，先调 super 再加自己的条
    @Override
    public void setBars() {
        super.setBars();   // 必须先调，否则血条/电条/物品条全没了

        addBar("temperature", (ExampleBuild entity) -> new Bar(
            // 条上的名字（用 bundle key 或裸字符串均可）
            () -> "温度",
            // 条颜色（Prov，可随温度变：冷蓝→热红）
            () -> Color.red,
            // ★ fraction 必须返回 0~1！
            () -> Mathf.clamp(entity.curTempK / entity.block.maxTempK)
        ));
    }

    public class ExampleBuild extends Building {
        // ... 你的 update/temperature 逻辑
        public float curTempK;
    }
}
```

> 上面 `entity.block.maxTempK` 里，`block` 是 `BuildingComp` 上指向所属 `Block` 的字段；如果你把 `maxTempK` 直接放在 Build 里，就写 `entity.maxTempK`。`Mathf.clamp` 来自 `arc.math.Mathf`，记得 `import arc.math.Mathf;`。

### 只讲 2~3 个新 API

**① `Block.addBar(String name, Func<T, Bar> sup)` —— `Block.java:677`**

```java
public <T extends Building> void addBar(String name, Func<T, Bar> sup){
    barMap.put(name, (Func<Building, Bar>)sup);
}
```

- 泛型 `<T extends Building>`，所以 lambda 里直接写成 `(YourBuild entity) -> ...`，编译器会替你把它转成 `Func<Building, Bar>`（强转在方法内部 `:678` 完成）。
- 第二个参数是个**懒求值的工厂**：每帧画条时才调一次 `sup.get(building)`，返回一个 `Bar`。返回 `null` 就不画这条。
- `name` 只是内部 key，重复会覆盖。

**② `Bar` 的两个构造函数 —— `Bar.java:23` 与 `Bar.java:31`**

```java
// 静态版：名字/颜色不变
public Bar(String name, Color color, Floatp fraction)            // :23

// 动态版：名字/颜色每帧可变（推荐，做温度变色用这个）
public Bar(Prov<CharSequence> name, Prov<Color> color, Floatp fraction)  // :31
```

- 第三个参数 `Floatp` 就是 `() -> float`，**必须返回 0~1**（源码 `:80` 会再 `Mathf.clamp` 一次，越界也不会崩，但最好自己先夹）。
- 链式方法：
  - `blink(Color color)` —— `Bar.java:71`，返回 `Bar`，条变短时闪烁该色（血条就用它闪白）。
  - `snap()` —— `Bar.java:57`，返回 `void`，把条瞬间拉到当前值（不要缓动动画时用）。
  - `outline(Color, float stroke)` —— `Bar.java:61`，给条加一圈描边。

**③ `BuildingComp.displayBars(Table)` —— `BuildingComp.java:1667`**

```java
public void displayBars(Table table){
    for(Func<Building, Bar> bar : block.listBars()){
        var result = bar.get(self());
        if(result == null) continue;
        table.add(result).growX();
        table.row();
    }
}
```

你**一般不用覆写它**，只要知道它在 `display()` 内部 `:1577` 被调用——也就是说：你 `addBar` 注册的所有条，会自动按顺序画在信息面板里。`listBars()` 来自 `Block.java:685`。

### 坑（萌新必踩）

1. **不要在 Block 构造函数里 `addBar`！** 正确位置是**覆写 `setBars()` 并先 `super.setBars()`**。
   原因（这是与早期教程说法不同的地方，已按 v159.7 源码修正）：`Block.afterPatch()` 在 `Block.java:769` 会先 `barMap.clear()` 再 `setBars()`（`:770`）；而 `Block.init()` 在 `:1475` 也调一次 `setBars()`。你在构造函数里 `addBar` 的东西，会被 `afterPatch()` 的 `clear()` 清掉。覆写 `setBars()` 则两次调用都会正确重建。
2. **`fraction` 必须是 `0~1`**，不是你的物理量本身。温度 800K 要自己除以 `maxTempK`。
3. **泛型 `T` 要匹配你的 Build 子类**。写 `(ExampleBuild entity) -> ...`，不要写成原始 `Building`，否则后面访问 `entity.thermal` 会编译不过。
4. **字段名是 `barMap`，不是 `bars`** —— `Block.java:422`（注释在 `:420`）。自己读源码时别找错。

### 深入链接

- 官方是怎么加条的：血条 `Block.java:707`、电条 `:713`、物品条 `:722`、液体条 `:690 / :699`。照抄它们的 `new Bar(...)` 写法最稳。
- `Bar` 全部方法：`Bar.java`（`reset():45`、`snap():57`、`outline():61`、`flash():67`、`blink():71`、`draw():77`）。

---

## 第二块：建筑信息面板自定义（点建筑显示温度/热流/自定义行）

### 目标

点击建筑，在默认的"图标+名字+状态条+消耗"下面，**追加**两行自己的数据：`温度: XXX K`、`热流: XXX J/t`。

### 完整可复制代码

```java
// 放在你的 Build 内部类里（注意是 Building 的子类，不是 Block）
@Override
public void display(Table table) {
    super.display(table);          // ★ 必须先调，否则默认信息全丢

    // 每加一行：先 row() 换到新行，再 add 内容
    table.row();
    table.left().label(() ->
        "温度: " + String.format("%.1f K", thermal.getTemperatureK())
    );

    table.row();
    table.left().label(() ->
        "热流: " + String.format("%.1f J/t", thermal.pendingDeltaQ)
    );
}
```

如果你想要更工整的"标签左、数值右"两列布局：

```java
@Override
public void display(Table table) {
    super.display(table);

    table.row();
    table.left().defaults().padRight(10);   // 默认单元格间距
    table.left().add("[gray]温度[]");
    table.right().label(() -> String.format("%.1f K", thermal.getTemperatureK()));
    table.row();
    table.left().add("[gray]热流[]");
    table.right().label(() -> String.format("%.1f J/t", thermal.pendingDeltaQ));
}
```

### 只讲 2~3 个新 API

**① `BuildingComp.display(Table table)` —— `BuildingComp.java:1561`**

这就是点建筑后弹出的信息面板的"绘制入口"。它内部干了这些事（`:1561~1580`）：

```java
table.table(t -> { /* 图标 + 显示名 */ }).growX().left();
table.row();
if(team == player.team()){
    table.table(bars -> { displayBars(bars); }).growX();  // ← 第一块的条在这
    table.row();
    table.table(this::displayConsumption).growX();        // 电/液/物消耗
}
```

你覆写它，就是在**这一切的末尾**接着画。

**② Table 的四件套：`add()` / `row()` / `left()` / `right()`**

- `table.add(...)`：往当前格子塞一个元素（文字、图片、甚至嵌套子表）。返回 `Cell`，可继续 `.pad(...)` `.width(...)` `.color(...)`。
- `table.row()`：**换行**。不调它，所有东西会挤在同一行。
- `table.left()` / `table.right()`：`Table.java:786 / :800`，返回 `Table` 本身，设置后续元素靠左/右对齐。
- 动态文字用 `table.label(Prov<CharSequence>)` —— `Table.java:285`，传 lambda，**每帧刷新**，温度会实时变。
- 文字里可以用 `[颜色]...[]` 富文本标签（如 `[red]停机[]`），Mindustry 内置解析。

### 坑（萌新必踩）

1. **必须 `super.display(table);` 放第一行**。漏掉的话，建筑图标、名字、状态条、消耗信息**全部消失**，只剩你手写的两行。
2. **`row()` 的时机**：`super.display(table)` 结束后光标已经在最后一行。你想新加一行，先 `table.row()` 再 `add/label`。直接 `add` 会接在上一行末尾。
3. **刷新用 `label(() -> ...)` 不要用 `add("固定字符串")`**。前者每帧重算，后者只画一次，温度不会动。
4. 覆写的是 **Build 类（内部类）的 `display`**，不是 Block 类。别写歪。

### 深入链接

- `display()` 完整逻辑：`BuildingComp.java:1561`（含 flow 物品流 `:1582~1616`、消耗 `displayConsumption():1656`）。
- 注意它 `:1573` 有个判断 `if(team == player.team())`——**别人队伍的建筑你看不到状态条和你的自定义行**。

---

## 第三块：数据库信息呈现自定义（图鉴页显示自定义统计）

### 目标

在游戏内**数据库/图鉴**里点开你的建筑，除了默认的"尺寸/血量/建造时间"，还能看到你自定义的静态条目，比如 `热容 C`、`airU`、`maxHeatRate`。

### 完整可复制代码

```java
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;

public class ExampleBlock extends Block {

    // ★ 自定义 Stat（类，不是枚举）。new 出来就自动注册，建议放 static final 复用
    public static final Stat heatCapacityStat = new Stat("thermalHeatCapacity");
    public static final Stat airUStat          = new Stat("thermalAirU");
    public static final Stat maxHeatRateStat   = new Stat("thermalMaxHeatRate");

    /** 这些是"块级"常量，图鉴要在还没造建筑时就显示，所以必须在 Block 上 */
    public float heatCapacity = 2000f;
    public float airU = 0.05f;
    public float maxHeatRate = 50f;

    public ExampleBlock(String name) {
        super(name);
    }

    // ★ 覆写 setStats()：内容加载时调用一次
    @Override
    public void setStats() {
        super.setStats();   // 必须先调，否则默认统计全没

        // 方式 A：直接传字符串（最省事）
        stats.add(heatCapacityStat, "%.0f J/K", heatCapacity);
        stats.add(airUStat, "%.3f J/(t·格·K)", airU);

        // 方式 B：用 StatValues 造一个带单位/格式化的值
        stats.add(maxHeatRateStat, StatValues.number(maxHeatRate, StatUnit.none));
    }
}
```

> 中文名显示：`Stat.localized()`（`Stat.java:126`）会去 bundle 找 `"stat." + name`。想让图鉴显示中文表头，在 `bundle.properties`（或你的汉化文件）里加：
> ```
> stat.thermalheatcapacity=热容 C
> stat.thermalairu=空气换热系数 airU
> stat.thermalmaxheatrate=最大换热率
> ```
> 注意 key 会被转小写（`:127` 用 `toLowerCase`）。不加也能跑，只是表头显示原始英文 key。

### 只讲 2~3 个新 API

**① `Block.setStats()` —— `Block.java:641`**

```java
@Override
public void setStats(){
    super.setStats();
    stats.useCategories = true;
    stats.add(Stat.size, "@x@", size, size);
    // ... 尺寸/血量/建造时间/消耗...
}
```

覆写它，`super.setStats()` 之后往里 `stats.add(...)` 即可。它在**内容加载时只跑一次**，不是运行时。

**② `Stat` —— `Stat.java`（注意：是类，不是枚举）**

很多萌新以为它是 `enum`，其实它是普通类，内置的 `health`/`size`/`powerCapacity` 等都是 `public static final` 静态实例：

```java
public Stat(String name, StatCat category){       // :115
    this.category = category;
    this.name = name;
    id = all.size;
    all.add(this);                                 // 构造即自动注册
}
public Stat(String name){ this(name, StatCat.general); }  // :122
```

所以自定义条目直接 `new Stat("thermalHeatCapacity")`，**会自动进 `Stat.all` 序列**，不用登记。

**③ `StatValues` 静态工厂 —— `StatValues.java`**

| 方法 | 行号 | 用途 |
|---|---|---|
| `string(String value, Object... args)` | `:35` | 纯文本（支持 `String.format`） |
| `bool(boolean value)` | `:40` | 是/否 |
| `number(float value, StatUnit unit)` | `:69` | 数字+单位 |
| `squared(float value, StatUnit unit)` | `:48` | 显示成 `NxN` |
| `liquid(Liquid, float, boolean perSecond)` | `:115` | 液体图标+量 |
| `percentModifier(...)` | `:94 / :107 / :111` | 百分比修正 |

往 `stats` 上加值有两条捷径（`Stats.java`）：
- `stats.add(Stat, String fmt, Object... args)` —— `Stats.java:93`，内部自动包成 `StatValues.string(...)`。
- `stats.add(Stat, StatValue)` —— `Stats.java:104`，传 `StatValues.xxx(...)` 的结果。

### 坑（萌新必踩）

1. **`setStats()` 在内容加载时跑一次，不是运行时**。你不能在里面读"当前建筑实例"的温度——那时连世界都没进。它只能放**块级常量**（Block 字段）。运行时数据请走第一块的条 / 第二块的 `display`。
2. **要显示的常量必须放在 Block 上，不能放在 Build 里**。图鉴在还没放建筑时就要读，Build 实例不存在。
3. **`Stat` 是类不是枚举**，自定义条目 `new Stat("xxx")` 即可，别去找 `Stat.values()`。
4. 同样要 `super.setStats();` 放第一行，否则默认的尺寸/血量/建造时间/消耗全没。

### 深入链接

- 内置 `Stat` 全部字段：`Stat.java:14~109`（已有 `heatCapacity`、`temperature`、`speed` 等，能复用就别 new）。
- `StatValues` 全部方法：`StatValues.java`（物品 `items():145`、序列 `content():332`、地形加成 `blocks():346` 等）。
- `StatUnit` 单位枚举：`StatUnit.java`（`none:39`、`percent`、`perSecond`、`blocks` 等）。

---

### 🟢 v160 新增：数据库统计这一层有三处变化（务必重读）

> 以下内容为 **v160 新增/修改**，原版第三块基于 v159.7。差异详见《v159.7 → v160 变更速查》。

**变化一：`useCategories` 现在自动开启，别再手写。**

- v159.7 里上面 ① 的示例写了一行 `stats.useCategories = true;`（旧 `Block.java:1467`，在 `afterPatch()` 里）。
- v160 把这一行**挪进了 `Block.setStats()`**（`Block.java:644`）。
- **结论**：你覆写 `setStats()` 时只要老老实实 `super.setStats();`，分类自动生效。**不要再在自己的 `afterPatch()` 里设 `stats.useCategories = true;`**——写了也无害，但已经是多余动作。

**变化二：`Stat` 新增两个枚举值可直接复用。**

| 新枚举 | 位置 | 分类 | 用法示例 |
|---|---|---|---|
| `Stat.meltdownTime` | `Stat.java:64` | `StatCat.power` | 电力类建筑显示熔毁时间：`stats.add(Stat.meltdownTime, meltdownSeconds, StatUnit.seconds);` |
| `Stat.status` | `Stat.java:93` | `StatCat.function` | 功能类建筑显示状态：`stats.add(Stat.status, StatValues.string("就绪"));` |

> 连同 `StatUnit` 也新增了 `worldUnits`（`StatUnit.java:17`）、`items`（`:40`）、`instant`（`:41`）三个单位，需要"世界距离/物品数/瞬时"展示时直接用。

**变化三：`StatValues` 多了一批好用的新方法。**

| 新方法 | 行号 | 一句话 | 用法示例 |
|---|---|---|---|
| `statusText(StatusEffect, float dur, float chance)` | `StatValues.java:229` | 把状态效果（概率+emoji+秒数）拼成本地化文本 | `stats.add(Stat.status, StatValues.string(StatValues.statusText(StatusEffects.burning, 60f, 1f)));` |
| `displayItem(Item, float amount, float timePeriod, boolean showName)` | `:317` | 显示**小数**产出（带 /秒） | 工厂图鉴显示"每次工艺出 0.5 个"：`stats.add(Stat.output, StatValues.displayItem(Items.copper, 0.5f, 90f, true));` |
| `blocks(Attribute, boolean, float sEff, float sAmt, Seq<ItemStack> out, float period, boolean startZero)` | `:350 / :399` | 按地形属性列出每方块的缩放产出+效率 | 钻机图鉴按湿度显示铜矿产出 |

```java
// v160 新写法示例：一个按地形属性显示产出的工厂
@Override
public void setStats(){
    super.setStats();   // ← useCategories 已自动开，不用手写
    // 小数产出直接显示
    stats.add(Stat.output, StatValues.displayItem(Items.copper, 0.5f, 90f, true));
    // 新 Stat 枚举
    stats.add(Stat.meltdownTime, 10f, StatUnit.seconds);
}
```

> 背后原因：v160 的 `GenericCrafter` 输出改成了"小数累积、按次取整"，所以 `StatValues` 必须支持 `float` 数量。这是一次配套重构，不是随便加的。

---

## 第四块：自定义窗口 / 悬浮面板（"热网总览" Dialog）

### 目标

弹出一个**独立的大窗口**，列出场上所有热力建筑的温度/状态。点击按钮 → `Dialog.show()` → 在 `cont` 里动态刷新列表。

### 完整可复制代码

```java
package com.thermal.mod.ui;

import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import com.thermal.mod.ThermalModMain;
import com.thermal.mod.core.ThermalBuilding;
import mindustry.gen.Building;
import mindustry.ui.Styles;
import arc.scene.ui.Dialog;

public class ThermalOverviewDialog extends Dialog {

    public ThermalOverviewDialog() {
        super("热网总览");          // Dialog(String title) —— Dialog.java:65

        // ★ 内容必须加在 this.cont 里，不是直接 add 到 this！
        cont.pad(14f);

        // 表头
        Table list = new Table();
        list.left();

        // 每帧重建/刷新内容（Dialog 打开时也会刷新）
        cont.top();
        cont.defaults().width(340f);
        cont.add(list).growX();
        cont.row();

        Runnable rebuild = () -> {
            list.clearChildren();
            list.left();

            var all = ThermalModMain.thermalSystem.allBuildings();  // 见下方说明
            if (all.isEmpty()) {
                list.add("[lightgray]场上还没有热力建筑。");
                return;
            }

            for (ThermalBuilding tb : all) {
                Building b = tb.asBuilding();
                float t = tb.getThermal().getTemperatureK();

                list.left().add(b.block.localizedName).padRight(10);
                list.left().label(() -> String.format("%.1f K", t));
                list.row();
            }
        };

        // 用 update 保证实时刷新；clearChildren 后重新挂
        cont.update(rebuild);

        // 底部按钮：关闭
        buttons.defaults().size(140f, 60f);
        buttons.button("关闭", () -> hide());
    }

    // 调用处：随便找个地方 new 出来 .show()
    //   new ThermalOverviewDialog().show();
}
```

配套地，给 `ThermalSystem` 加一个只读访问器（因为原 `buildings` 字段是 `private`）：

```java
// ThermalSystem.java
public arc.struct.Seq<ThermalBuilding> allBuildings() {
    return buildings;   // 直接返回内部 Seq，UI 只读遍历即可
}
```

**怎么把它唤出来**——最稳的萌新做法是把某个建筑设成 `configurable`，在 `buildConfiguration` 里放个按钮：

```java
// 某个 Block 的构造函数里
configurable = true;   // Block.java:258

// 它的 Build 内部类里
@Override
public void buildConfiguration(Table table) {
    table.button("热网总览", Styles.flatt, () -> {
        new ThermalOverviewDialog().show();   // Dialog.show() —— Dialog.java:514
    }).size(140f, 60f);
}
```

> `buildConfiguration(Table)` 空定义在 `BuildingComp.java:1678`，注释写明："configurable must be true for this to be called"。

### 只讲 2~3 个新 API

**① `arc.scene.ui.Dialog` —— `Dialog.java:29`**

`Dialog extends Table`（`:29`），所以它本身就是个表格。关键是它自带三块：

```java
public final Table cont, buttons;     // Dialog.java:57
public final Label title;              // :58
```

- 你写正文 → 加在 **`cont`** 里。
- 你写底部按钮 → 加在 **`buttons`** 里（也可以直接 `addCloseButton()`，`:453`）。
- 显示/关闭：`show()`（`:514`）、`hide()`（`:551`）。

**② Table 布局** —— 同第二块。窗口内容组织就是 `cont.add(...).row()` 的循环。

**③ `Styles` —— `mindustry/ui/Styles.java`**

现成样式常量，省得自己画：

- 按钮样式：`Styles.flatT`（文本按钮）、`Styles.defaulti`（图标按钮）—— `:32 / :61`。
- 背景面板：`Styles.grayPanel`、`Styles.black` —— `:27`。
- 标签样式：`Styles.outlineLabel` —— `:97`。

### 坑（萌新必踩）

1. **内容加错地方**：直接 `add(...)` 到 `this` 会跑到标题栏区域/被标题挤掉。**正文必须加在 `this.cont`**，底部按钮加在 `this.buttons`。
2. **`show()` / `hide()`**：`show()` 无参会自动挂到当前 `Core.scene`（`:514`）。反复 `new` 一个新 Dialog 没问题；想复用就持有单例。
3. **线程安全 / 主线程**：Mindustry 的 UI 只能在**渲染主线程**操作。`ThermalSystem.update` 是在 `Trigger.update` 里跑的，已经在主线程，安全；但如果你以后从网络回调/后台线程刷 UI，必须包到 `Core.app.post(() -> ...)` 里。
4. **动态列表要 `clearChildren()` 重建**：直接 `list.add(...)` 每次都会叠加。要么 `update(() -> { list.clearChildren(); /*重填*/ })`，要么只在数据变化时重建。
5. 别忘了 `configurable = true`（`Block.java:258`），否则 `buildConfiguration` 根本不会被调。

### 深入链接

- `Dialog` 全部：`Dialog.java`（`show():514`、`hide():551`、`addCloseButton():453`、`isMovable/isModal:47`）。
- `Styles` 全部样式字段：`Styles.java:27~98`。

---

## 综合案例：热力学 mod 四建筑完整信息 UI

> 基于 `thermal-mod` 的四个建筑：`IndustrialBoiler`（产热）、`CoolingTower`（散热）、`HeatConduit`（导热管）、`RefineryFurnace`（耗热温区）。
> 它们都实现了 `ThermalBuilding` 接口，有 `getThermal()` 返回 `ThermalComponent`（含 `getTemperatureK()`、`storedHeat`、`pendingDeltaQ`、`heatCapacity`、`airU`、`maxTempK`）。

目标：给四个建筑全部接上 ① 温度条 ② 信息面板 ③ 图鉴统计，再加一个全局"热网总览"窗口。

### 改造 0：给 ThermalSystem 加只读访问器

```java
// ThermalSystem.java —— 新增一个 public 方法
public arc.struct.Seq<ThermalBuilding> allBuildings() {
    return buildings;
}
```

### 改造 1：统一的自定义 Stat（放一个公共文件）

新建 `com/thermal/mod/core/ThermalStats.java`：

```java
package com.thermal.mod.core;

import mindustry.world.meta.Stat;

/** 热力模组共用的自定义图鉴统计项。new 出来即自动注册。 */
public class ThermalStats {
    public static final Stat heatCapacity = new Stat("thermalHeatCapacity"); // 热容 C (J/K)
    public static final Stat airU          = new Stat("thermalAirU");         // 空气换热系数
    public static final Stat maxHeatRate   = new Stat("thermalMaxHeatRate");  // 最大换热率
}
```

> 配套在 `bundle.properties` 加：
> ```
> stat.thermalheatcapacity=热容 C
> stat.thermalairu=空气换热系数 airU
> stat.thermalmaxheatrate=最大换热率
> ```

### 改造 2：四个建筑统一加 `setBars()`（温度条）

以 `IndustrialBoiler` 为例，其余三个把类型名/上限换掉即可。**在 Block 类里**加：

```java
// IndustrialBoiler.java —— 顶部 import 增补
import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;
import static com.thermal.mod.core.ThermalStats.*;

// IndustrialBoiler 类体内（Block 层）新增覆写：
@Override
public void setBars() {
    super.setBars();
    addBar("thermal-temp", (BoilerBuild entity) -> new Bar(
        () -> "温度",
        // 冷→热渐变：低于工作温区偏蓝，接近上限偏红
        () -> {
            float f = Mathf.clamp(
                (entity.thermal.getTemperatureK() - entity.thermal.minTempK)
                / (entity.thermal.maxTempK - entity.thermal.minTempK));
            return Color.royal.cpy().lerp(Color.scarlet, f);
        },
        () -> Mathf.clamp(
            (entity.thermal.getTemperatureK() - entity.thermal.minTempK)
            / (entity.thermal.maxTempK - entity.thermal.minTempK))
    ));
}
```

四个建筑的温度条**写法完全一样**，只是泛型类型不同：

| 建筑 | 泛型 Build 类型 | 上限来源 |
|---|---|---|
| `IndustrialBoiler` | `BoilerBuild` | `entity.thermal.maxTempK` |
| `CoolingTower` | `TowerBuild` | `entity.thermal.maxTempK` |
| `HeatConduit` | `ConduitBuild` | `entity.thermal.maxTempK` |
| `RefineryFurnace` | `FurnaceBuild` | `entity.thermal.maxTempK` |

### 改造 3：四个建筑统一加 `setStats()`（图鉴条目）

以 `IndustrialBoiler` 为例：

```java
// IndustrialBoiler 类体内新增覆写：
@Override
public void setStats() {
    super.setStats();
    // 注意：这里读的是 Block 层常量，不能读 Build 实例
    stats.add(heatCapacity, "%.0f J/K", 2000f);   // 与 placed() 里 heatCapacity=2000 对齐
    stats.add(airU,         "%.3f",      0.05f);
    stats.add(maxHeatRate,  StatValues.number(ratedPower, StatUnit.none)); // 额定功率当换热率
}
```

四个建筑对应数值（与各自 `placed()` 里写死的值保持一致）：

| 建筑 | 热容 C | airU | maxHeatRate |
|---|---|---|---|
| `IndustrialBoiler` | 2000 | 0.05 | `ratedPower`(100) |
| `CoolingTower` | 500 | 0.31 | 用 `airU` 示意 |
| `HeatConduit` | 50 | 0.008 | `airU`(保温) |
| `RefineryFurnace` | 1500 | 0.08 | `ratedHeat`(80) |

> 建议顺手把这些魔法数字提成 Block 层 `public` 字段（如 `public float heatCapacity = 2000f;`），然后 `placed()` 里 `thermal.heatCapacity = heatCapacity;`——这样 `setStats()` 和运行时读同一份常量，不会漂移。

### 改造 4：信息面板（display）—— 已经有了，做个对齐

四个建筑原本就覆写了 `display(Table)` 并调了 `super.display(table)`（见 `IndustrialBoiler.java:98`、`CoolingTower.java:80`、`HeatConduit.java:73`、`RefineryFurnace.java:105`），温度/热流/启停状态都在。**这块不用大改**，只需确认：

- 每段都以 `super.display(table);` 开头 ✅
- 每加一行前先 `table.row();` ✅
- 动态值用 `label(() -> ...)` lambda ✅

综合案例里我们**额外**给每个 Build 加一行"热流"实时值（原本只有存储热量），以 `RefineryFurnace` 为例，在 `display` 里追加：

```java
table.row();
table.left().label(() ->
    "热流: " + String.format("%+.1f J/t", thermal.pendingDeltaQ)
);
```

### 改造 5：全局"热网总览"窗口

新建 `com/thermal/mod/ui/ThermalOverviewDialog.java`（完整代码见第四块）。然后把 `HeatConduit` 设成配置入口：

```java
// HeatConduit 构造函数里加：
configurable = true;

// HeatConduit.ConduitBuild 内部类里加：
@Override
public void buildConfiguration(Table table) {
    table.button("热网总览", () -> new ThermalOverviewDialog().show())
        .size(160f, 60f);
}
```

点一根导热管 → 弹出按钮 → 打开总览，列出所有热力建筑实时温度。

### 综合案例改动文件清单

| 文件 | 改动 |
|---|---|
| `core/ThermalSystem.java` | 新增 `allBuildings()` 访问器 |
| `core/ThermalStats.java` | **新建**，3 个自定义 `Stat` |
| `blocks/IndustrialBoiler.java` | 覆写 `setBars()` + `setStats()` |
| `blocks/CoolingTower.java` | 覆写 `setBars()` + `setStats()` |
| `blocks/HeatConduit.java` | 覆写 `setBars()` + `setStats()`，`configurable=true` + `buildConfiguration()` |
| `blocks/RefineryFurnace.java` | 覆写 `setBars()` + `setStats()`，`display` 加"热流"行 |
| `ui/ThermalOverviewDialog.java` | **新建**，全局总览 Dialog |

---

## 附录 A：UI 源码地图（类 / 方法 / 行号汇总）

| 你要做的事 | 类:行号 | 说明 |
|---|---|---|
| 注册状态条 | `Block.java:677` `addBar(String, Func<T,Bar>)` | 覆写 `setBars()` 后调用 |
| 状态条存储字段 | `Block.java:422` `barMap` | 注释 `:420` |
| 移除/列出条 | `Block.java:681 / :685` | `removeBar` / `listBars` |
| 默认条注册 | `Block.java:706` `setBars()` | 血/电/物/液条 |
| `setBars` 调用时机 | `Block.java:1475`(init) / `:770`(afterPatch) | afterPatch 先 `clear():769` |
| 条构造/动态 | `Bar.java:23` / `:31` | 静态 / 动态 Prov 版 |
| 条闪烁/吸附/描边 | `Bar.java:71` blink / `:57` snap / `:61` outline | blink 返回 Bar 可链式 |
| 信息面板入口 | `BuildingComp.java:1561` `display(Table)` | 覆写，先 super |
| 状态条绘制 | `BuildingComp.java:1667` `displayBars(Table)` | 内部被 `:1577` 调 |
| 建筑配置入口 | `BuildingComp.java:1678` `buildConfiguration(Table)` | 需 `configurable=true` |
| 图鉴统计入口 | `Block.java:641` `setStats()` | 内容加载时一次 |
| 自定义统计类型 | `Stat.java:115 / :122` | 类，`new Stat("name")` 即注册 |
| 统计值工厂 | `StatValues.java:35/40/69/115` | string/bool/number/liquid |
| 往图鉴加值 | `Stats.java:93` / `:104` | add(Stat,fmt,args) / add(Stat,StatValue) |
| 弹窗基类 | `Dialog.java:29` | extends Table |
| 弹窗内容/按钮区 | `Dialog.java:57` `cont` / `buttons` | 正文加 cont，按钮加 buttons |
| 弹窗显示/关闭 | `Dialog.java:514` show / `:551` hide | |
| 关窗按钮 | `Dialog.java:453` `addCloseButton()` | |
| UI 样式 | `Styles.java:27/32/61/97` | 面板/文本按钮/图标按钮/标签 |

## 附录 B：Mindustry UI 体系架构一句话版

```
内容层（编辑期）
  Block.setStats()  ──►  Stats  map<StatCat, map<Stat, Seq<StatValue>>>
        │  （内容加载时跑一次，填图鉴）
        ▼
实例层（运行期，点建筑）
  BuildingComp.display(Table)
        ├─ displayBars() ──► 遍历 Block.barMap（addBar 注册的 Bar 工厂）
        └─ displayConsumption()
        ▼
舞台层（Scene2D/Arc）
  Table（单元格网格：add / row / left / right / label）
  Dialog extends Table（自带 titleTable / cont / buttons）
        ▼
样式层
  Styles.* （Mindustry 封装好的 Drawable / ButtonStyle / LabelStyle）
```

记住这条线，四块知识就能串成一张图：**`addBar` 喂数据给 `barMap` → `display` 把 `displayBars` 摆进 Table → `setStats` 在内容期填图鉴 → `Dialog` 是另一个独立 Table，挂到 `Core.scene` 上。**

---

*教程完。所有行号基于本地 v159.7 源码核验；若后续版本行号漂移，按方法名 `addBar / setBars / setStats / display / displayBars` 检索即可。*

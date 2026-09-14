# v159.7 → v160 变更速查（考古风格）

> 本专章面向模组作者，逐条核验 v160 相对 v159.7 的**破坏性 / 新增 API**。
> 所有行号均来自当前 v160 工作树（HEAD `0ac15152d8`）；旧版对照用 `git diff v159.7..HEAD -- <path>`。
> 变更类型标记：【新增】【修改】【删除】【未变】。

---

## 1. v160 概览

| 项目 | 数值 / 说明 |
|---|---|
| 提交数 | **499** 个提交（`git rev-list --count v159.7..HEAD`） |
| 文件变更 | **371** 个文件 |
| 增删行数 | **+33780 / −6695** |
| 当前 HEAD | `0ac15152d881f77dd4566a13c8d5f1225aee1068` |
| 对比基线 | tag `v159.7`（`c9686eb5d0…`） |
| 发布时间 | 2026-09-11（今天） |
| 与 v159.7 关系 | 同一主线的一次大版本跃迁：核心渲染/ECS 框架继续演进，**对模组作者最有体感的是新增 `HeatCrafter` 方块与 `HeatConsumer` 接口、`StatValues` 大改、`GenericCrafter` 分批输出** |

一句话总结：**写工厂的人要重新看 `GenericCrafter`；做热力联动的人迎来官方一等公民 `HeatConsumer`；玩图鉴 UI 的人多了一堆 `StatValues` 重载。**

---

## 2. 新方块 HeatCrafter 全解

源码：`core/src/mindustry/world/blocks/production/HeatCrafter.java`（全文件仅 93 行，非常小）。

### 2.1 类继承与字段

```java
public class HeatCrafter extends GenericCrafter{          // :12 继承自 GenericCrafter
    /** Base heat requirement for 100% efficiency. */
    public float heatRequirement = 10f;                   // :14  达到 100% 效率所需的基础热量
    /** After heat meets this requirement, excess heat will be scaled by this number. */
    public float overheatScale = 1f;                      // :16  超出部分的缩放系数
    /** Maximum possible efficiency after overheat. */
    public float maxEfficiency = 4f;                      // :18  过热处理后的效率上限
```

| 字段 | 行号 | 默认值 | 含义 |
|---|---|---|---|
| `heatRequirement` | `HeatCrafter.java:14` | `10f` | 100% 效率所需热量；0 表示不需要热量 |
| `overheatScale` | `:16` | `1f` | 热量超过需求后，每多 1 点热换算成的额外效率比例 |
| `maxEfficiency` | `:18` | `4f` | 过热处理后效率上限（4 = 400%） |

### 2.2 HeatConsumer 接口（【新增】）

源码：`core/src/mindustry/world/blocks/heat/HeatConsumer.java`（仅 6 行）。

```java
public interface HeatConsumer{
    float[] sideHeat();        // 返回 4 个方向（东南西北）各自接收到的热量数组
    float heatRequirement();   // 返回该消费者需要多少热量
}
```

- 这是 v160 把"热量消费"抽象成的接口。任何建筑只要 `implements HeatConsumer`，热力网络（Heater / HeatConduit 等）就会自动把热导进来。
- `sideHeat()` 返回长度为 4 的数组，索引对应方向；`HeatCrafterBuild` 内部用它接收四个方向传来的热。

### 2.3 HeatCrafterBuild 内部类（效率核心）

```java
public class HeatCrafterBuild extends GenericCrafterBuild implements HeatConsumer{  // :43
    public float[] sideHeat = new float[4];   // :45  四个方向的入热
    public float heat = 0f;                  // :46  当前总热

    @Override
    public void updateTile(){
        heat = calculateHeat(sideHeat);      // :50  每帧汇总四个方向的热
        super.updateTile();
    }

    @Override
    public boolean shouldConsume(){
        return (heatRequirement <= 0f || heat > 0) && super.shouldConsume();  // :63 没热就不消耗原料
    }

    @Override
    public float warmupTarget(){
        return Mathf.clamp(heat / heatRequirement);   // :78 基础效率 = clamp(热/需求)，封顶 1
    }

    @Override
    public float efficiencyScale(){                  // :88 过热处理
        float over = Math.max(heat - heatRequirement, 0f);            // :89 超出部分
        return Math.min(
            Mathf.clamp(heat / heatRequirement)                       // 基础 0~1
            + over / heatRequirement * overheatScale,                 // 超额部分按 overheatScale 加成
            maxEfficiency                                             // 封顶 maxEfficiency
        );
    }
}
```

**效率计算规则（背下来）：**

1. 基础效率 `warmupTarget = clamp(heat / heatRequirement)`：热不够时按比例给，最多 100%。
2. 超额效率 `efficiencyScale = min(clamp(heat/req) + (heat-req)/req * overheatScale, maxEfficiency)`：超过需求的热按 `overheatScale` 折算成额外效率，最终不超过 `maxEfficiency`。
3. `shouldConsume()`：只要 `heat <= 0`（且 `heatRequirement > 0`）就**不消耗原料、不生产**——即"没热就罢工"。
4. `sense(LAccess.heat)` 直接返回当前 `heat`（:82-85），逻辑编程可读。

### 2.4 setBars / setStats

```java
@Override
public void setBars(){                       // :25
    super.setBars();
    addBar("heat", (HeatCrafterBuild entity) -> new Bar(   // :28 加热条
        () -> Core.bundle.format("bar.heatpercent", (int)(entity.heat + 0.01f),
                                  (int)(entity.efficiencyScale() * 100 + 0.01f)),
        () -> Pal.lightOrange,
        () -> entity.heat / heatRequirement
    ));
}

@Override
public void setStats(){                      // :36
    super.setStats();
    stats.add(Stat.input, heatRequirement, StatUnit.heatUnits);   // :39 热需求
    stats.add(Stat.maxEfficiency, (int)(maxEfficiency * 100f), StatUnit.percent); // :40 最大效率
}
```

### 2.5 完整最小实现（可复制）

```java
package yourmod.blocks;

import mindustry.world.blocks.production.HeatCrafter;

public class HeatSmelter extends HeatCrafter{
    public HeatSmelter(String name){
        super(name);
        // 调参
        heatRequirement = 20f;     // 需要 20 点热才满效率
        overheatScale = 0.5f;      // 超 20 后，每多 2 点热多 1 倍效率
        maxEfficiency = 3f;        // 最多 300% 效率
        // 其余 outputItem / craftTime 等沿用 GenericCrafter 的字段
    }
    // 不需要写 Build！父类 HeatCrafterBuild 已经把热接收、效率、shouldConsume 全包了。
}
```

> 关键红利：**绝大多数情况下你不用碰内部类**，父类 `HeatCrafterBuild` 已经实现了 `HeatConsumer`、热汇总、效率曲线、热条、热需求图鉴。你只在需要自定义热感时才覆写。

---

## 3. 新字段 / 新枚举清单

### 3.1 Block 新字段【新增】

| 字段 | 位置 | 默认值 | 含义 |
|---|---|---|---|
| `Block.diagonalConfigInventory` | `Block.java:268` | `false` | 对角配置物品库存（与旋转配置库存相关） |
| `Block.maxConsecutive` | `Block.java:392` | `2` | `instantTransfer` 方块单次连续传输的最大数量（图鉴在 `Block.java:662` 用 `StatUnit.none` 展示） |

### 3.2 Stat 新枚举【新增】

| 枚举 | 位置 | 分类 | 用途 |
|---|---|---|---|
| `Stat.meltdownTime` | `Stat.java:64` | `StatCat.power` | 熔毁时间（与过热/熔毁机制相关的电力类统计） |
| `Stat.status` | `Stat.java:93` | `StatCat.function` | 状态类统计入口 |

> 注：`Stat.maxEfficiency`（`Stat.java:71`）并非 v160 新增，`HeatCrafter.setStats` 复用了它。

### 3.3 StatUnit 新枚举【新增】

| 枚举 | 位置 | 用途 |
|---|---|---|
| `StatUnit.worldUnits` | `StatUnit.java:17` | 世界单位（距离/范围类展示） |
| `StatUnit.items` | `StatUnit.java:40` | 物品数量单位 |
| `StatUnit.instant` | `StatUnit.java:41` | "瞬时"单位（`Block.java:661` 在 `instantTransfer` 时用于 `Stat.itemsMoved`） |

### 3.4 逻辑传感器新 case armor【新增】

- 位置：`core/src/mindustry/entities/comp/BuildingComp.java:2125` —— `case armor -> block.armor;`
- 已核验：`git show v159.7:.../BuildingComp.java` 中**不存在** `case armor`，确为 v160 新增。
- 配套：`LAccess.armor`（`LAccess.java:26`）早已存在且可 settable。
- 含义：现在逻辑编程可以直接读建筑自身装甲值 `sensor armor`，返回 `block.armor`。
- 另：单位侧 `UnitComp.java:295/385`、`UnitType.java:1433`、`Block.java:1675` 的 `case armor` 为旧有逻辑，本次仅 `BuildingComp` 侧新增。

---

## 4. StatValues 大改说明（+136 / −29）

源码：`core/src/mindustry/world/meta/StatValues.java`。这是图鉴（数据库）里所有数字/物品图标的绘制工具类。v160 围绕**"输出量可以是小数"**做了一轮重构。

### 4.1 新增方法一览

| 新方法 | 行号 | 一句话说明 | 用法示例 |
|---|---|---|---|
| `statusText(StatusEffect, float duration, float chance)` | `:229` | 把状态效果（含概率、emoji、持续秒数）拼成一段本地化解说字符串 | 子弹图鉴里自动用：`ammo()` 内部 `:889` 调用；模组可在 `stats.add(...)` 自定义行里复用 |
| `stack(TextureRegion, float amount, ...)` | `:244` | `stack` 私有底层重载，把"数量"从 int 改成 float；原 int 版本改为委托给它 | 间接支持小数输出展示 |
| `stack(UnlockableContent item, float amount, boolean tooltip)` | `:282` | `stack` 公开重载，接受**小数**数量 | 配合新 `displayItem(float amount)` 展示分批输出 |
| `displayItem(Item item, float amount, float timePeriod, boolean showName)` | `:317` | `displayItem` 重载，数量为 float，按时间周期算 /秒 | 工厂图鉴里显示"每次工艺产出 0.5 个"这种小数 |
| `blocks(Attribute, boolean, float s1, float s2, Seq<ItemStack> outputs, float timePeriod, boolean startZero)` | `:350` | 新 `blocks` 便捷重载（少一个 `checkFloors` 参数） | 直接转发到下面的完整版 |
| `blocks(Attribute, boolean, float scaleEff, float scaleAmount, Seq<ItemStack> outputs, float timePeriod, boolean startZero, boolean checkFloors)` | `:399` | **重量级新增**：按地形属性列出每个方块，并显示"缩放后的产出物品堆叠 + 效率百分比" | 见 4.3 示例 |

### 4.2 重构点（非新增方法，但影响写法）

1. **stack 数量格式化**（`:255`）：由 `amount >= 1000 ? formatAmount(amount) : amount+""` 改为 `amount >= 1000 || Mathf.equal(amount, (int)amount) ? formatAmount((int)amount) : amount+""`。整数直接显示，小数才保留——解决"0.5 个物品图标"的显示。
2. **weapons()**（`:658`、`:673`）：新增 `int index = i;` 并调用 `tableInfo(w, "unit." + name + ".weapon." + index + ".info")`，让单位武器可以挂 bundle 说明。
3. **ammo() 的 blockName 说明**（`:755-757`）：原本一大段内联 UI 代码，重构为统一调用 `tableInfo()` 私有助手。
4. **itemEffMultiplier / liquidEffMultiplier**：效率数字前加 `[stat]` 颜色标签（`:581`、`:596`）。
5. **闪电子弹图鉴**：`lightningLengthRand` 现在显示成区间 `[min]-[max]`（`:827-829`）。
6. 删了未使用的 `import arc.scene.style.*;`。

### 4.3 新 blocks() 重载用法示例

```java
// 例：某钻机按"湿润度(water)"属性，每方块显示缩放后的铜矿产出与效率
stats.add(Stat.tiles, StatValues.blocks(
    Attribute.water,          // 地形属性
    false,                    // 是否漂浮
    0.2f,                     // scaleEff：每点属性给 +20% 效率
    0.2f,                     // scaleAmount：每点属性给 +20% 产出量
    Seq.with(new ItemStack(Items.copper, 1)),  // 要显示的产出
    60f,                      // timePeriod（tick）：60 tick = 1 秒
    false                     // startZero
));
```

### 4.4 useCategories 自动分类的影响【关键行为变化】

- **v159.7**：`stats.useCategories = true;` 写在 `Block.afterPatch()`（旧 `Block.java:1467`）。
- **v160**：这一行挪进了 `Block.setStats()`（新 `Block.java:644`）。
- **影响**：现在**所有方块在 `setStats()` 里自动开启统计分类**，模组作者**不需要也不应该再在自己的 `afterPatch()` 里手动设 `stats.useCategories = true;`**。你覆写 `setStats()` 时只要 `super.setStats()`，分类自动生效。

---

## 5. GenericCrafter 变化（+23 / −6）

源码：`core/src/mindustry/world/blocks/production/GenericCrafter.java`。**这是对模组写工厂影响最大的一处。**

### 5.1 分批输出累积器 outputAccumulator【新增】

```java
// GenericCrafterBuild 内部，:191
public @Nullable float[] outputAccumulator =
    outputItems != null && outputItems.length > 0 ? new float[outputItems.length] : null;
```

- 每个产出物品槽位一个**浮点累积器**。

### 5.2 scaleOutput(float) 钩子【新增】

```java
/** Allows scaling the crafter's output dynamically. */
public float scaleOutput(float amount){   // :304
    return amount;
}
```

- 默认原样返回；**模组可覆写**来动态缩放产出（例如受速度模块、过热、催化液影响）。
- 凡是"要不要存得下""每 tick 液体会不会溢出"的判断，v160 都改用 `scaleOutput(...)`：
  - `shouldConsume()`：`items.get(output.item) + scaleOutput(output.amount) > itemCapacity`（:208 附近）
  - `updateTile()` 液体缩放：`(liquidCapacity - liquids.get(s.liquid)) / (scaleOutput(s.amount) * edelta())`（:279 附近）

### 5.3 afterPatch 顺序调整【修改】

```java
public void afterPatch(){                 // :141
    super.afterPatch();
    if(outputItems != null) hasItems = true;      // :144  先判 hasItems
    outputsLiquid = outputLiquids != null;         // :145  再判 outputsLiquid
    if(outputLiquids != null) hasLiquids = true;
}
```

- v159.7 里 `outputsLiquid` 在 `hasItems` 之前赋值；v160 把 `hasItems` 提到前面。逻辑等价，仅顺序整理，**对你无影响**，但如果你覆写了 `afterPatch()` 要注意调用顺序。

### 5.4 craft() 改为"累积后取整输出"【核心行为修改】

```java
public void craft(){
    consume();
    if(outputItems != null){
        // 在 craft() 里惰性创建，因为 outputItems 可能运行时变化（:313）
        if(outputAccumulator == null || outputAccumulator.length != outputItems.length){
            outputAccumulator = new float[outputItems.length];
        }
        for(int i = 0; i < outputItems.length; i++){
            ItemStack output = outputItems[i];
            outputAccumulator[i] += scaleOutput(output.amount);   // :319 累积小数
            int floored = Mathf.floor(outputAccumulator[i]);     // :320 取整
            outputAccumulator[i] -= floored;                     // :321 保留余数
            for(int j = 0; j < floored; j++){
                offload(output.item);                            // 按整数个 offload
            }
        }
    }
    ...
}
```

### 5.5 对模组写工厂的影响（务必注意）

| 旧写法（v159.7） | 新行为（v160） |
|---|---|
| `craft()` 里 `for(int i=0;i<output.amount;i++) offload(...)`，每次工艺固定出整数个 | 产出量经 `scaleOutput()` 后是**浮点数**，先累加到 `outputAccumulator`，**只把整数部分 offload，余数留到下次工艺** |
| 想让工厂动态增产，只能改 `outputItem.amount` | 现在覆写 `scaleOutput(float)` 即可，累积器自动处理小数 |
| 依赖"每次 craft 恰好出 N 个物品"的逻辑 | 连续多次工艺后**总产出不变**，但单次不再是整数——不要假设单次 craft 必出整数个 |

> 结论：**输出总量守恒，但输出从"整数/次"变成"小数累积、按次取整 offload"**。覆写 `craft()` 的老模组要检查自己是否硬编码了 `output.amount`。

---

## 6. 核心机制未变确认表

以下机制经 `git diff v159.7..HEAD` 核对，**v160 未改签名/未改流程**，老教程与老模组继续适用。括号内为 v159.7 参考位置（行号会漂移，按方法名检索即可）。

| 机制 | 状态 | v159.7 参考 |
|---|---|---|
| Block ↔ Building 绑定（`initBuilding` / `buildType` / `new Building()`） | **未变** | `Block.initBuilding` / `setBars` 链路 |
| `handleDamage` / `damage` 伤害入口 | **未变** | `BuildingComp.damage` / `Block.handleDamage` |
| `display` / `displayBars` / `addBar` / `setBars` 信息 UI 四件套 | **未变**（仅 `setStats` 内多了 `useCategories=true`） | `Block.addBar`、`BuildingComp.display` |
| `Wall` / `Turret` / `Conveyor` 等基础方块 | **未变** | 各自类无破坏性改动 |
| 建筑生命周期钩子（`update` / `placed` / `onProximityUpdate` / `onDestroyed` / `onRemoved`） | **未变** | `BuildingComp` 各钩子 |
| `consume` / `consumeBurn` / 消耗系统 | **未变**（仅工厂输出侧引入 `scaleOutput`） | `Consume` / `GenericCrafter.craft` |
| ECS 注解 / 生成机制 | **未变** | `entities.comp` / 注解处理器 |

> 唯一需要"补课"的信息层变化是第 4 节的 `useCategories` 自动开启与第 3 节的新 `Stat`/`StatUnit` 枚举；渲染与生命周期骨架零改动。

---

## 7. 对教程体系的影响矩阵

| 教程 | 受影响部分 | 需补的 v160 内容 |
|---|---|---|
| 主线教程（Mindustry-Java-Mod-Tutorial） | 无 | **不受影响**——讲的是 Block/Building 骨架，第 6 节已确认未变 |
| UI 教程（custom-ui-tutorial） | **第三块：数据库统计（setStats）** | `useCategories` 现已自动；`Stat.meltdownTime` / `Stat.status` 用法；`StatValues.statusText` / `displayItem(float)` / 新 `blocks()` 重载示例 |
| API 参考表（API-Reference） | **GenericCrafter 条目 + 新增 HeatCrafter 条目** | `outputAccumulator` / `scaleOutput` / `afterPatch` 顺序；新方块 `HeatCrafter` 全参数表 + 最小实现；`Block.diagonalConfigInventory` / `maxConsecutive` 标注 |
| 画廊 v2（Template-Gallery-v2） | **工厂类模板卡片** | GenericCrafter 输出行为变更加注；新增一张 HeatCrafter 热力工厂模板卡片（或在现有工厂卡补注） |
| 热力学 mod / 其他实战 | 正向利好 | 现有自建双缓冲热力系统可对接官方 `HeatConsumer` 接口，详见 API 参考表 HeatCrafter 条目 |

---

## 8. v160.1 → v160.3 热修复速查（2026-09 追加）

> anuke 在 v160 之后连续打了 3 个小版本：**v160.1（8 提交）/ v160.2（+15 提交）/ v160.3（+6 提交）**，合计 29 提交、28 文件（+287/−236）。绝大多数是 bugfix 与翻译；**对模组作者只有 1 处破坏性 API 变更**（见 8.1），其余均为新增字段或行为修复。

### 8.1 破坏性变更：`Block.canPickup` 废弃 → `allowedInPayloads`【必须迁移】

- **位置**：`Block.java`（v160.3）新增字段 `public boolean allowedInPayloads = true`；旧字段 `canPickup` 标记 `@Deprecated`
- **调用侧**：`BuildingComp.canPickup()`（BuildingComp.java:1843-1846）改为返回 `block.allowedInPayloads`
- **官方实例**：`BaseShield` / `TargetDummy` 已从 `canPickup = true` 改为 `allowedInPayloads = false`
- **迁移方式**：新代码一律写 `allowedInPayloads`；旧代码 `canPickup` 仍能编译运行（兼容），但会有弃用警告
- **配套修复**：禁止 dummy 进 payload（c6512c0b0）、dummy 值加强校验（c7945610e）——与本次重命名同批发布

### 8.2 新增字段 / 方法【不破坏，可选使用】

| 位置 | 新增内容 | 用途 |
|---|---|---|
| `UnitType`（UnitTypes.java:4647-4650） | `internal` / `internalGenerateSprites` | 标记内部单位（不占单位上限、不生成默认贴图） |
| `Rules`（Rules.java:343-347） | `isInfiniteResources(Team)` | 团队级无限资源判断（规则层，非 mod API） |
| `TargetDummy` | `canOverdrive = false` | 假人不可超频（防御类行为修正） |

### 8.3 行为修复【对 mod 运行有间接影响】

- **粒子效果尺寸修复**（e433d5f3，#11477）：`ParticleEffect` 粒子曾经尺寸×2 的 bug 已修——如果你的 mod 依赖粒子视觉参数，渲染大小会变为预期值
- **逻辑指令 fetch 坐标取整**（LExecutor.java:1583-1586）：`Mathf.round()` → `numi()`，坐标取整行为一致，无感知差异
- **逻辑画布紧凑模式**（LStatements）：逻辑编辑器 UI 布局调整，不影响运行时逻辑
- **声音系统**：SoundControl 崩溃修复 + 数据贴图声音改为一次性 wav 流（与 mod 自定义音效加载相关，若 mod 用 `DataAudioLoader` 请关注）

### 8.4 对教程体系的影响

| 教程 | 影响 | 处理 |
|---|---|---|
| 官方教程 v2（official-mod-tutorial-v2） | 字段表 `canPickup` | **已更新为 `allowedInPayloads`** 并标注 v160.3 废弃 |
| API 参考表 | 无直接条目 | `canPickup` 未出现在 35 类参数表中，无需改 |
| 主线 / UI / 画廊 / 热力学 mod | 无 | 未使用 `canPickup` |

---

## 附：本次核验用到的命令

```bash
cd Mindustry
git rev-list --count v159.7..HEAD                 # 499
git diff v159.7..HEAD --stat | tail -1           # 371 files, +33780 -6695
git diff v159.7..HEAD -- core/src/mindustry/world/meta/StatValues.java
git diff v159.7..HEAD -- core/src/mindustry/world/blocks/production/GenericCrafter.java
git show v159.7:core/src/mindustry/entities/comp/BuildingComp.java | grep "case armor"  # 空 → 新增
```

> 行号以 v160 HEAD `0ac15152d8` 为准；后续小版本漂移时请按方法名/字段名检索。

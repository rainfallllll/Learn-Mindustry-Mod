# Mindustry 官方 Modding 中文教程 v2

> 本文档基于 Mindustry 官方 Wiki（https://mindustrygame.github.io/wiki/modding/）整理。
> v2 完整抓取了 9 个 Wiki 页面 + 47 个 Modding Classes 字段文档，在 v1 基础上补全了被截断的字段表，并新增 Plugins、Scripting 现代 API、Spriting 完整指南、迁移指南等章节。
> 所有代码块与字段表忠实官方原文（英文），中文讲解围绕组织。官方 Wiki 未覆盖的内容在对应处注明缺口。

---

## 目录

1. [开始](#1-开始)
2. [HJSON 语法速成](#2-hjson-语法速成)
3. [mod.hjson 元数据](#3-modhjson-元数据)
4. [Content 内容体系](#4-content-内容体系)
5. [Types 类型与字段类型](#5-types-类型与字段类型)
6. [科技树 Tech Tree](#6-科技树-tech-tree)
7. [Spriting 贴图](#7-spriting-贴图)
8. [Sound 音效](#8-sound-音效)
9. [Dependencies 依赖](#9-dependencies-依赖)
10. [Bundles 翻译](#10-bundles-翻译)
11. [Markup 文本格式](#11-markup-文本格式)
12. [Schematic 蓝图](#12-schematic-蓝图)
13. [Scripting 脚本](#13-scripting-脚本)
14. [Plugins & JVM Mods（Java 插件）](#14-plugins--jvm-modsjava-插件)
15. [分享与 FAQ](#15-分享与-faq)
16. [迁移指南（v6/v7/v8）](#16-迁移指南v6v7v8)
17. [字段速查大区：Modding Classes](#17-字段速查大区modding-classes)
18. [三条路线对照表](#18-三条路线对照表)

---

## 1. 开始

Mindustry 的 mod 本质上就是一堆资源文件组成的目录。根据你想做的事情不同，modding API 有多种使用方式：

- **纯换皮**：只替换现有游戏内容的贴图；
- **HJSON/JSON 内容 mod**（本教程主线之一）：用简单的 HJSON 数据创建新游戏内容；
- **JS 脚本 mod**：用 Rhino JavaScript 编写特殊行为；
- **Java/JVM 插件 mod**：用 Java/Kotlin 等 JVM 语言编写完整 mod（详见第 14 章）；
- **自定义音效**：添加新音效或复用内置音效；
- **战役地图**：向战役模式添加地图。

分享你的 mod 只需把项目目录发给别人；mod 是跨平台的，支持该平台的任何系统都能运行。官方推荐使用 GitHub，并建议参考 Example Mod 仓库。制作 mod 只需要一台装有文本编辑器的电脑。

### 1.1 编辑器推荐

如果你使用 HJSON，推荐使用 **Visual Studio Code** + [Mindustry HJSON](https://marketplace.visualstudio.com/items?itemName=Anuken.mindustry-hjson) 扩展。该扩展提供自动补全、语法高亮和未知/无效字段警告。安装方式：从 release 页下载 `.vsix`，在 VSCode 扩展面板点右上角 `...` → "Install from VSIX..."。

### 1.2 目录结构

你的项目目录应当长这样：

```
project
├── mod.hjson
├── content
│   ├── items
│   ├── blocks
│   ├── liquids
│   ├── weather
│   └── units
├── maps
├── bundles
├── sounds
├── schematics
├── scripts
├── sprites-override
└── sprites
```

各目录含义：

| 路径 | 说明 |
|------|------|
| `mod.hjson`（必需） | mod 元数据文件 |
| `content/*` | 游戏内容数据 |
| `maps/` | 游戏内地图 |
| `bundles/` | 多语言翻译 |
| `sounds/` | 音效文件 |
| `schematics/` | 蓝图文件 |
| `scripts/` | Rhino JS 脚本 |
| `sprites-override/` | 覆盖游戏内原有贴图 |
| `sprites/` | 你自己内容的贴图 |

### 1.3 各平台 mod 放置路径

| 平台 | 路径 |
|------|------|
| Linux | `~/.local/share/Mindustry/mods/` |
| Steam | `steam/steamapps/common/Mindustry/saves/mods/` |
| Windows | `%appdata%/Mindustry/mods/` |
| macOS | `~/Library/Application Support/Mindustry/mods/` |

### 1.4 文件命名规范

文件名必须**全小写、用连字符分隔**（kebab-case）：

```
正确：my-custom-block.json
错误：My Custom Block.json
```

---

## 2. HJSON 语法速成

Mindustry 使用 [Hjson](https://hjson.github.io/)。对熟悉 JSON 的人来说，Hjson 就是 JSON 的超集——任何合法的 JSON 都能工作，但你得到了一些额外的便利特性：

```hjson
# single line comment
// single line comment
/* multiline comment */

key1: "single line string"
key2: '''
  multiline string
'''
key3: [
  // quotes and commas are optional for strings
  value1
  value2
  value3
]
key4: {
  key1: astring
  key2: 0
}
// 与官方 HJSON 规范不同，Mindustry 允许同一行放多个无引号字符串
arrayExample: [several, words, that, will, work]
```

**要点说明：**

- Hjson 允许 `#`、`//`、`/* */` 三种注释；
- 字符串可以不加引号；
- 数组/对象元素之间不需要逗号；
- `''' ... '''` 用于多行字符串；
- 因为 Hjson 是 JSON 超集，你也可以直接写标准 JSON；
- 注意：官方 HJSON 标准已停止维护，Mindustry 用的是修改版，允许无引号多行数组。

> 序列化语言（serialization language）就是把文本编码成程序数据结构的语言——在 Mindustry 中，就是把 HJSON 文本翻译成 Java 数据结构。

---

## 3. mod.hjson 元数据

在项目根目录必须有一个 `mod.hjson`（或 `mod.json`），定义 mod 的基本元数据：

```hjson
name: "mod-name"
displayName: "This isn't a mod."
author: Yourself
description: "A short description of your mod."
version: "1.0"
minGameVersion: "159.7"
dependencies: [ ]
hidden: false
```

各字段说明：

| 字段 | 说明 |
|------|------|
| `name` | 用于引用你的 mod，命名要谨慎；应为 kebab-case（全小写，空格用 `-` 代替），不要加颜色格式 |
| `displayName` | UI 中显示的名称，可以带 Markup 格式标签（如 `[red]...[]`） |
| `author` | 作者名 |
| `description` | 在游戏内 mod 管理器中显示的描述，保持简短扼要 |
| `version` | mod 版本号 |
| `minGameVersion` | 最低游戏构建版本号，**必须大于 136** |
| `dependencies` | 可选，依赖列表，详见 [Dependencies](#9-dependencies-依赖) |
| `hidden` | 是否为多人游戏必需的 mod，默认 false。材质包、JS 插件等应设为 true，避免服务器/客户端版本不匹配冲突。如果你的 mod 创建了内容，不应设为 hidden |

> Java mod 额外字段：`main: "mypackage.MyMod"` 指定全限定主类（须继承 `mindustry.mod.Mod`）。详见第 14 章。

---

## 4. Content 内容体系

项目根目录下可以有一个 `content/` 目录，所有 HJSON 数据都放这里。`content/` 内按内容类型分子目录：

| 子目录 | 内容类型 | 示例 |
|--------|----------|------|
| `content/items/` | 物品 | `copper`、`surge-alloy` |
| `content/blocks/` | 方块 | 炮塔、地板 |
| `content/liquids/` | 液体 | `water`、`slag` |
| `content/units/` | 单位 | `eclipse`、`dagger` |

### 4.1 文件名即引用名

文件路径的**主干名**（去掉扩展名的文件名）就是该内容的引用名。文件可以任意嵌套子目录来组织，例如：

```
content/items/metals/iron.hjson   →  创建名为 iron 的物品
```

### 4.2 内容文件基本结构

每个内容文件大致长这样：

```hjson
type: TypeOfThing
name: Name Of Thing
description: Description of thing.
# ... more fields here ...
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | String | 该对象的内容类型 |
| `name` | String | 内容显示名 |
| `description` | String | 内容描述 |

其余字段由 `type` 所指定的类型自身决定。

> 注意：`name` 和 `description` 不一定要写在 HJSON 里，你可以通过 [Bundles](#10-bundles-翻译) 为任意语言定义它们。如果两者都没有，名称默认为 `<type>.<modname>-<stemname>.name`，描述为空。

---

## 5. Types 类型与字段类型

### 5.1 类型继承

类型有大量字段，最重要的是 `type` 字段——它是内容解析器使用的特殊字段，决定你的对象是什么类型。`Router` 类型不能当 `Turret` 用，它们完全不同。

**类型之间会继承**：如果 `MissileBulletType extends BasicBulletType`，那么在 `MissileBulletType` 中你就能使用 `BasicBulletType` 的所有字段，如 `damage`、`lifetime`、`speed`。

**字段大小写敏感**：`hitSize` ≠ `hitsize`。

有些类型本身不做任何事，仅作为其他类型继承的基类——`Block` 就是这样一个类型。

示例——顶层对象类型是 `flying`（飞行单位），子弹的类型是 `BulletType`，因此可以使用 `MissileBulletType`（因为它继承自 `BulletType`）：

```hjson
type: flying
weapons: [
  {
    bullet: {
      type: MissileBulletType
      damage: 9000
    }
  }
]
```

其他单位类型包括：`mech`、`legs`、`naval`、`payload`、`tank`、`hover`、`crawl`、`missile`、`tether`。

### 5.2 字段类型参考（来自 5-types 页面）

> 注意：已弃用的内容类不在此列出，强烈不建议使用。迁移到非弃用替代。所有 JSON 示例来自 Exotic Mod（BlueWolf3682），仅作字段参考，不要直接复制粘贴到你的 mod。

#### BuildVisibility

控制方块可见性的标志，可选字符串：

```
hidden  shown  debugOnly  editorOnly  coreZoneOnly
worldProcessorOnly  sandboxOnly  campaignOnly
legacyLaunchPadOnly  notLegacyLaunchPadOnly  lightingOnly  fogOnly
```

#### BlockGroup

方块分组（同组可互相替换）：

```
none  walls  projectors  turrets  transportation
power  liquids  drills  units  logic  payloads  heat
```

#### ItemStack

物品堆，可以是字符串或对象：

字符串形式：`copper/5`

对象形式：

```hjson
item: copper
amount: 5
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `item` | string | 物品名 |
| `amount` | int | 数量 |

#### LiquidStack

液体堆，可以是字符串或对象：

字符串形式：`water/0.5`

对象形式：

```hjson
liquid: water
amount: 0.5
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `liquid` | string | 液体名 |
| `amount` | float | 量 |

#### Category

建造菜单分类：

| 分类 | 说明 |
|------|------|
| `turret` | 进攻性炮塔 |
| `production` | 生产资源的方块（如钻机） |
| `distribution` | 搬运物品的方块 |
| `liquid` | 搬运液体的方块 |
| `power` | 发电或输电 |
| `defense` | 墙和防御结构 |
| `crafting` | 合成方块 |
| `units` | 创建单位的方块 |
| `logic` | 逻辑操作相关 |
| `effect` | 存储或被动效果 |

#### Color

颜色是十六进制字符串 `<rr><gg><bb>`，例如：

- `ff0000` 红，`00ff00` 绿，`0000ff` 蓝，`ffff00` 黄，`00ffff` 青

#### CacheLayer

缓存渲染层：

- `normal` 普通层；
- `walls` 墙层；
- `water` 水层，加水着色器和波纹反射；
- `tar` 焦油层，加焦油着色器，变暗并有气泡反射。

#### TargetPriority

数值越大优先级越高，高优先级方块总会被优先瞄准（无视距离）：

1. `base`
2. `turret`

---

## 6. 科技树 Tech Tree

和 `type` 类似，还有一个"魔法字段"叫 `research`，可以放在任意内容对象的根部，把它放入科技树：

```hjson
research: duo
```

这会把你的方块放在 `duo` 之后。要放在你自己 mod 的方块后面，直接写你自己的方块名即可。**只有在引用其他 mod 的内容时才需要加 mod 名前缀。**

### 研究成本公式（v2 更新）

| 类型 | 成本公式 | 说明 |
|------|----------|------|
| 方块 | `60 * researchCostMultiplier + requirements ^ 1.11 * 20 * researchCostMultiplier` | `researchCostMultiplier` 可在方块上设置，默认 `1` |
| 单位 | `requirements * researchCostMultiplier` | UnitType 上默认 `researchCostMultiplier = 50` |

然后根据成本大小四舍五入到最近的 10、100、1k 或 100k（不总是向下取整）。其中 `requirements` 是你方块/单位的建造成本。

> 注意：v1 旧公式 `40 + round(requirements^1.25)*6` 已过时，以新版为准。

### 自定义研究需求对象

如果你想设置自定义研究需求，用这个对象代替纯名字：

```hjson
research: {
  parent: duo
  requirements: [
    copper/100
  ]
}
```

这可以覆盖方块/单位成本，或让资源需要被研究而不是直接生产。

---

## 7. Spriting 贴图

> 本章基于官方 4-spriting 页面完整整理。

贴图是 Mindustry modding 的重要部分——没有它，你做的所有东西都会显示为缩放过的"oh no"图片。Mindustry 的贴图风格简单但严格。

> **注意**：未经允许使用其他 modder 的贴图是不允许的，虽然可以用来参考或灵感。"mod 是开源的所以我可以随便用"这种理由不会被接受，你的 mod 会被 mod 浏览器拉黑。

### 7.1 贴图软件推荐

**桌面端：**

1. **Aseprite** — 黄金标准。有学习曲线但上手后很简单。付费软件（可自行编译源码，但建议购买支持开发者）。有镜像、调色板控制、动画、分层等功能。
2. **LibreSprite** — Aseprite 的 fork，不如原版新或强大，但够用。
3. **Piskel** — 简单像素艺术软件，有在线版和离线版，不能导出单独图层。
4. **Pixilart** — 在线工具，功能比 Piskel 多但缺镜像工具。
5. **Paint.NET** — 基础绘画软件，不推荐（缺像素画基本功能）。

**移动端：**

1. **Novix Pixel Editor** — Anuke 制作（已停止维护），简单无广告，支持镜像工具。
2. **Pixel Studio** — 流行像素艺术 app，可与 PC 版联动，有广告。
3. **Ibispaint X** — 非专门画像素画，需改设置，支持多种工具，有广告。

### 7.2 尺寸规范

**方块：** 最小 1×1 方块贴图为 `32×32`，每大一档增加 32px：

- `1×1`：`32×32`
- `2×2`：`64×64`
- `3×3`：`96×96`
- `4×4`：`128×128`
- `5×5`：`160×160`

游戏也能加载更大或更小的贴图，但可能导致独特外观或灾难。

**物品/液体/状态：** 最小 `32px`，更大的会被压缩到 32px，更小的不会被放大。

**单位：** 单位贴图尺寸更宽松，但尽量不低于 `48px`。单位越大，越需要调整 `hitSize`（碰撞箱大小）。

> 图片必须是 PNG、32 位 RGBA 格式。其他格式（如 16 位 RGBA）可能导致 "Pixmap decode error" 崩溃。可用 `file sprites/**.png` 检查。

### 7.3 放置与命名

贴图放进 `sprites/` 子目录（HJSON mod）或 `src/assets/sprites/`（Java mod）。内容解析器递归扫描。图片被打包进 "atlas" 以高效渲染。`sprites/` 下的第一级目录（如 `sprites/blocks`）决定贴图放在 atlas 的哪一页——把方块贴图放进 units 页会导致大量卡顿。

- 方块贴图放 `sprites/blocks`
- 单位贴图放 `sprites/units`
- 物品贴图放 `sprites/items`

游戏根据内容名字寻找贴图：`content/blocks/test-turret.json` 名为 `test-turret`，`sprites/test-turret.png` 就会被使用。

游戏会修改部分贴图：**炮塔和单位会被自动加上 3-4px 灰色边框**。制作时必须留出空间。默认描边半径和颜色可通过 `Block`/`UnitType` 的 `outlineRadius`/`outlineColor` 字段自定义。

**覆盖原版贴图：** 放 `sprites-override/` 即可，这会去掉 `<modname>-` 前缀，覆盖原版甚至其他 mod 的贴图。

### 7.4 贴图后缀

游戏可能为单个方块寻找多个贴图：

- 炮塔：`<name>-heat`（如 `test-turret-heat.png`）
- 方块/合成器/冶炼厂：`<name>-top`、`<name>-liquid`

具体可加载哪些贴图，查看对应方块类源码中 `@Load` 注解和 `load()` 方法。

### 7.5 调色板

Mindustry 有自己的调色板。新手强烈建议只用这些颜色，否则会显得格格不入甚至"异端"。方块调色板和环境调色板详见官方 Wiki（含色板图）。

### 7.6 风格与阴影

Mindustry 是 2D 游戏，通过"阴影"技巧营造立体感。光源来自**右上角**，阴影在**左下角**。

- **凸起**用**较亮色调**
- **平面**用**中间色调**
- **凹陷**用**较深色调**

方块通常有 3 种颜色类型，每种 3 个色调（亮/中/暗）：底色（Base Color）和贴花色（Decal Color）。炮塔还可选炮管口颜色。

**资源（物品）阴影：** 光源可来自顶角、从上到下或从右到左。资源贴图只用 2-3 个同色系色调，确保看起来立体。

**单位绘制 5 阶段**（Zhenьkotron 编写）：

1. 勾勒大致形状。用粗笔刷画深色轮廓，慢慢叠加线条形成基本形状，稍微变形或弯曲，确保形状好看——形状不好看后面怎么画都不行。
2. 细化形状，用 45 度角线条。调整边缘，不要太拘泥于草稿。
3. 添加贴花（decals）。贴花难画，但现在加好后面可以围绕它构建阴影。
4. 粗略标记明暗部分。光来自上方，标记最暗和最亮的区域。约 30-40% 应为深色，贴花周围留暗以增加对比。
5. "添加细节"——最复杂的部分。不确定加什么时，加单元格（cells）。不要加太多细节。

**轮廓：** 炮塔和单位贴图边缘留 4px 空间，游戏会自动加轮廓。

**环境贴图：** 与其他贴图不同，**45 度增量规则不适用**。环境贴图占游戏大多数画面，必须足够低调，平铺多次后仍好看。

- **地板**：只有 2 个色调，变体数量自定。
- **树**：画在大多数方块上方，单位可穿过，仅作地图装饰。树有阴影贴图，需手动制作。

---

## 8. Sound 音效

自定义音效通过把文件放进 `sounds/` 子目录来添加。支持两种格式：`.ogg` 和 `.mp3`。注意 `.mp3` 不能无缝循环，尽量用 `.ogg`。

和其他资源一样，通过文件名主干来引用。`pewpew.ogg` 和 `pewpew.mp3` 在 `Sound` 类型字段中用 `pewpew` 引用。

### 内置音效列表（v2 更新）

> v2 音效列表与 v1 差异巨大，以下为官方最新列表。

```
acceleratorCharge  acceleratorConstruct  acceleratorLaunch  acceleratorLightning1
acceleratorLightning2  beamHeal  beamLustre  beamMeltdown  beamParallax
beamPlasma  beamPlasmaSmall  blockBreak1  blockBreak2  blockBreak3
blockExplode1  blockExplode1Alt  blockExplode2  blockExplode2Alt  blockExplode3
blockExplodeElectric  blockExplodeElectricBig  blockExplodeExplosive
blockExplodeExplosiveAlt  blockExplodeFlammable  blockExplodeWall
blockHeal  blockPlace1  blockPlace2  blockPlace3  blockRepair  blockRotate
chargeCorvus  chargeLancer  chargeVela  click  coreLand  coreLaunch  door
drillCharge  drillImpact  explosion  explosionAfflict  explosionArtillery
explosionArtilleryShock  explosionArtilleryShockBig  explosionCleroi
explosionCore  explosionCrawler  explosionDull  explosionMissile  explosionNavanax
explosionObviate  explosionPlasmaSmall  explosionQuad  explosionReactor
explosionReactor2  explosionReactorNeoplasm  explosionTitan  healWave  loopBio
loopBuild  loopCircuit  loopCombustion  loopConveyor  loopCultivator  loopCutter
loopDifferential  loopDrill  loopElectricHum  loopExtract  loopFire  loopFlux
loopGlow  loopGrind  loopHover  loopHover2  loopHum  loopMachine  loopMachine2
loopMachineSpin  loopMalign  loopMineBeam  loopMissileTrail  loopPulse  loopRegen
loopShield  loopSmelter  loopSpray  loopSteam  loopTech  loopThoriumReactor
loopThruster  loopUnitBuilding  massdriver  massdriverReceive  mechStep
mechStepHeavy  mechStepSmall  padLand  padLaunch  payloadDrop1  payloadDrop2
payloadDrop3  payloadPickup  plantBreak  rain  rockBreak  shieldBreak
shieldBreakSmall  shieldHit  shieldWave  shipMove  shipMoveBig  shockBullet
shockwaveTower  shoot  shootAfflict  shootAlpha  shootArc  shootArtillery
shootArtillerySap  shootArtillerySapBig  shootArtillerySmall  shootAtrax
shootAvert  shootBeamPlasma  shootBeamPlasmaSmall  shootBreach
shootBreachCarbide  shootCleroi  shootCollaris  shootConquer  shootCorvus
shootCyclone  shootDiffuse  shootDisperse  shootDuo  shootEclipse  shootElude
shootEnergyField  shootFlame  shootFlamePlasma  shootForeshadow  shootFuse
shootHorizon  shootLancer  shootLaser  shootLocus  shootMalign  shootMeltdown
shootMerui  shootMissile  shootMissileLarge  shootMissileLong  shootMissilePlasma
shootMissilePlasmaShort  shootMissileShort  shootMissileSmall  shootNavanax
shootOmura  shootPayload  shootPulsar  shootQuad  shootReign  shootRetusa
shootRipple  shootSalvo  shootSap  shootScathe  shootScatter  shootScepter
shootScepterSecondary  shootSegment  shootSmite  shootSpectre  shootStell
shootSublimate  shootTank  shootToxopidShotgun  stepMud  stepWater  tankMove
tankMoveHeavy  tankMoveSmall  uiBack  uiButton  uiChat  uiFavorite  uiNotify
uiUnlock  unitCreate  unitCreateBig  unitExplode1  unitExplode2  unitExplode3
walkerStep  walkerStepSmall  walkerStepTiny  waveSpawn  wind  wind2  wind3
windHowl  wreckFall  wreckFallBig  none  unset
```

---

## 9. Dependencies 依赖

在 `mod.hjson` 中添加其他 mod 的名字即可声明依赖：

```hjson
dependencies: [
  other-mod-name
  not-a-mod
]
```

**依赖名规范化**：全部小写，空格替换为连字符。例如 `Other MOD NamE` 变成 `other-mod-name`。

**引用其他 mod 的资源**：必须在资源名前加上其他 mod 的名字作为前缀：

```
other-mod-name-not-copper     →  other-mod-name 中的 not-copper
other-mod-name-angry-dagger   →  other-mod-name 中的 angry-dagger
not-a-mod-angry-dagger        →  not-a-mod 中的 angry-dagger
```

---

## 10. Bundles 翻译

Bundles 是 mod 的可选组成部分，主要用途是给你的内容提供翻译。这些是纯文本文件，放在 `bundles/` 子目录，命名如 `bundle_ru.properties`（俄语）。内容格式非常简单：

```properties
block.example-mod-silver-wall.name = Серебряная Стена
block.example-mod-silver-wall.description = Стена из серебра.
```

### 10.1 命名规则

```
<content type>.<mod name>-<content name>.name
<content type>.<mod name>-<content name>.description
```

注意：mod/content 名全部小写、连字符分隔。自定义 bundle 键名（脚本用）可随意命名，如 `message.egg = Eat your eggs`、`randomline = Random Line`。

### 10.2 内容类型列表

```
item  block  bullet  liquid  status  unit  weather  sector
error  planet  team  unitCommand  unitStance
```

### 10.3 各语言文件名对照

| 语言 | 后缀 | 语言 | 后缀 |
|------|------|------|------|
| English | `en` | Korean | `ko` |
| Czech | `cs` | Lithuanian | `lt` |
| German | `de` | Dutch BE | `nl_BE` |
| Spanish | `es` | Dutch | `nl` |
| Estonian | `et` | Polish | `pl` |
| Basque | `eu` | Portuguese BR | `pt_BR` |
| Finnish | `fi` | Portuguese | `pt_PT` |
| Filipino | `fil` | Romanian | `ro` |
| French | `fr` | Russian | `ru` |
| Hungarian | `hu` | Serbian | `sr` |
| Indonesian | `id_ID` | Swedish | `sv` |
| Italian | `it` | Thai | `th` |
| Japanese | `ja` | Turkman | `tk` |
| | | Turkish | `tr` |
| | | Ukrainian | `uk_UA` |
| | | Vietnamese | `vi` |
| | | Chinese CN | `zh_CN` |
| | | Chinese TW | `zh_TW` |

---

## 11. Markup 文本格式

文本渲染器使用简单的标记语言来着色文本。

| 语法 | 说明 |
|------|------|
| `[name]` | 按名称设置颜色（有内置颜色名） |
| `[#rrggbb]` / `[#rrggbbaa]` | 按十六进制设置颜色，每位值 00–ff；rr=红，gg=绿，bb=蓝，aa=透明度 |
| `[]` | 恢复到上一个颜色 |
| `[[` | 转义左方括号，渲染为 `[`（写 `[[red]` 会显示为 `[red]`） |

> 注意：错误/未知颜色会被静默忽略。

示例：

```
[red]red
[#ff0000]full-red
[#ff000066]half-red
[#ff000033]half-half-red
[#00ff00]green
[]half-half-red
```

### 内置颜色（含十六进制值）

```
[clear]clear (#00000000)
[black]black (#000000FF)
[white]white (#FFFFFFFF)
[lightgray]lightgray (#BFBFBFFF)
[gray]gray (#7F7F7FFF)
[darkgray]darkgray (#3F3F3FFF)
[blue]blue (#0000FFFF)
[navy]navy (#00007FFF)
[royal]royal (#4169E1FF)
[slate]slate (#700090FF)
[sky]sky (#87CEEBFF)
[cyan]cyan (#00FFFFFF)
[teal]teal (#007F7FFF)
[green]green (#00FF00FF)
[acid]acid (#7FFF00FF)
[lime]lime (#32CD32FF)
[forest]forest (#228B22FF)
[olive]olive (#6B8E23FF)
[yellow]yellow (#FFFF00FF)
[gold]gold (#FFD700FF)
[goldenrod]goldenrod (#DAA520FF)
[orange]orange (#FFA500FF)
[brown]brown (#8B4513FF)
[tan]tan (#D2B48CFF)
[brick]brick (#B22222FF)
[red]red (#FF0000FF)
[scarlet]scarlet (#FF341CFF)
[coral]coral (#FF7F50FF)
[salmon]salmon (#FA8072FF)
[pink]pink (#FF69B4FF)
[magenta]magenta (#FF00FFFF)
[purple]purple (#8000FFFF)
[violet]violet (#EE82EEFF)
[maroon]maroon (#B03060FF)
```

---

## 12. Schematic 蓝图

需要 `Schematic` 类型的字段可以接受：

1. 内置 loadout；
2. 一个 base64 字符串；
3. `schematics/` 子目录中 `.msch` 文件的主干名。

目前蓝图的主要用途包括给 Zone 指定初始 loadout 等。

---

## 13. Scripting 脚本

> 本章基于官方 3-scripting 页面整理，为**现代 JS API**（与 v1 的旧 `extendContent` 风格不同）。

Mindustry 使用 **JavaScript** 进行 mod 脚本编写。脚本使用 `.js` 扩展名，放在 `scripts/` 子目录。

执行从 `main.js` 文件开始。其他脚本文件可以通过 `require("script_name")` 被主文件引入。典型结构：

*scripts/main.js*:

```javascript
require("blocks");
require("items");
```

*scripts/blocks.js*:

```javascript
const myBlock = extend(Conveyor, "terrible-conveyor", {
  // various overrides...
  size: 3,
  health: 200
  //...
});
```

*scripts/items.js*:

```javascript
const terribleium = Item("terribleium");
terribleium.color = Color.valueOf("ff0000");
//...
```

> **API 对照**：旧版用 `extendContent(Block, "name", {...})`，现代版用 `extend(Conveyor, "name", {...})` 直接引用类。物品用 `Item("name")` 构造函数。

### 13.1 监听事件

```javascript
// listen for the event where a unit is destroyed
Events.on(UnitDestroyEvent, event => {
  // display toast on top of screen when the unit was a player
  if(event.unit.isPlayer()){
    Vars.ui.hudfrag.showToast("Pathetic.");
  }
})
```

> 查找可监听事件最简单的方法是查看源码文件 `Mindustry/blob/master/core/src/mindustry/game/EventType.java`。

### 13.2 显示对话框

```javascript
const myDialog = new BaseDialog("Dialog Title");
// Add "go back" button
myDialog.addCloseButton();
// Add text to the main content
myDialog.cont.add("Goodbye.");
// Show dialog
myDialog.show();
```

### 13.3 播放自定义音效

把音效文件存为 `.mp3` 或 `.ogg` 放在 `/sounds` 目录。示例中我们把 `example.mp3` 存在 `/sounds/example.mp3`。

*scripts/alib.js*（加载音效的库，带缓存）:

```javascript
exports.loadSound = (() => {
    const cache = {};
    return (path) => {
        const c = cache[path];
        if (c === undefined) {
            return cache[path] = loadSound(path);
        }
        return c;
    }
})();
```

*scripts/main.js*:

```javascript
const lib = require("alib");

Events.on(WaveEvent, event => {
    // loads example.mp3
    const mySound = lib.loadSound("example");
    // engine will spawn this sound at this location (X,Y)
    mySound.at(1, 1);
})
```

### 13.4 v1 旧示例（extendContent 风格，供参考）

> 以下为 v1 文档中的旧版 `extendContent` 示例，展示 JS 如何扩展已有 Java 类型实现自定义行为。现代 API 用 `extend()` 代替。

```javascript
// create a simple shockwave effect
const siloLaunchEffect = newEffect(20, e => {
    Draw.color(Color.white, Color.lightGray, e.fin());
    Lines.stroke(e.fout() * 3);
    Lines.circle(e.x, e.y, e.fin() * 100);
});

const silo = extendContent(Block, "scatter-silo", {
    buildConfiguration(tile, table) {
        table.addImageButton(Icon.arrowUpSmall, Styles.clearTransi,
            run(() => tile.configure(0))
        ).size(50);
    },
    configured(tile, value) {
        if (tile.entity.cons.valid()) {
            Effects.effect(siloLaunchEffect, tile);
            for (var i = 0; i < 10; i++) {
                Calls.createBullet(
                    Bullets.flakExplosive,
                    tile.getTeam(),
                    tile.drawx(), tile.drawy(),
                    Mathf.random(360),
                    Mathf.random(0.5, 1.0),
                    Mathf.random(0.2, 1.0)
                );
            }
            tile.entity.cons.trigger();
        }
    }
});
```

---

## 14. Plugins & JVM Mods（Java 插件）

> 本章基于官方 2-plugins 页面整理，为官方对 Java mod 的说明。

Mindustry 支持在桌面端和 Android 上加载带 Java 字节码的 `jar` 文件。它们的功能与 JS mod 类似，但必须提供一个主类在 mod 创建时实例化。理论上所有 JVM 语言都支持。

### 14.1 mod.hjson 配置

Jar/JVM mod 使用与标准 mod 相同的 `mod.hjson` 元文件，额外增加：用 `main: "mypackage.MyMod"` 指定全限定主类。该类应继承 `mindustry.mod.Mod`。

如果不指定主类，默认为 `modnameinlowercase.ModName + "Mod"`。

一个 Java mod 的简单 `mod.hjson`：

```hjson
name: "Nothing"
author: "Yourself"
main: "nothing.NothingMod"
description: "..."
version: "99.99"
```

更多说明见 example Java mod 仓库和 example Kotlin mod 仓库。

### 14.2 Plugins（服务器插件）

Plugins 是**仅服务器端运行**的 Java mod，通常添加新命令或新游戏模式。所有插件主类应继承 `mindustry.mod.Plugin`。这使它们**隐式 hidden**——客户端不需要下载插件就能加入服务器。安装插件：把 JAR 放入 `<server directory>/config/mods/`。

Plugins 的元文件命名为 `plugin.[h]json`，文件结构与其他 Java mod 相同。

### 14.3 导入与分发

与 JS 或 JSON mod 不同，JAR mod 需要编译。这意味着它们不能直接从 GitHub 导入——而是用 **GitHub Releases**。

当用户尝试安装 JAR mod 时，Mindustry 会检查最新（且**只检查最新**）的 GitHub release 中的 `.jar` 产物。找到第一个产物就下载。注意 pre-release 会被忽略。

> 推荐用 GitHub Actions（或其他 CI）自动构建并上传 jar 产物到新 release。

### 14.4 多线程

除非特别说明，**Mindustry 代码都不是线程安全的**。从主线程以外的线程执行任何操作（发包、改 tile 等）会导致随机崩溃或网络错误。要在主线程运行，用 `Core.app.post(() => { /* code */ })`。

### 14.5 能力与安全

由于 jar mod 直接通过 `URLClassLoader` 加载，**没有沙箱**，因此没有任何安全限制：

- 所有 Java API 都可访问；
- 可用反射访问私有/隐藏属性；
- mod 有客户端电脑的完全访问权，可能被恶意利用；
- mod 可以修改游戏文件或重写核心字节码。

> **永远不要从不信任的来源导入 jar mod。** 这确实是安全风险，但没有好的替代方案——即使实现了 `SecurityManager` 也没用，Java 本质上不安全。作为对比，Minecraft 的 Forge 也不沙箱 mod。

---

## 15. 分享与 FAQ

### 15.1 分享 mod

做好 mod 后，用 **GitHub** 分享。项目放到 GitHub 后，有三种分享方式：

1. **endpoint 方式**：例如 `Anuken/MindustryJavaModTemplate`，在游戏内的 GitHub 界面输入即可下载；
2. **zip 方式**：例如 `https://github.com/Anuken/MindustryJavaModTemplate/archive/master.zip`，下载为 zip 后放进 mod 目录（无需解压）；
3. **添加标签**：给仓库打上 `mindustry-mod` 标签，会被 Mod scraper 收录。

### 15.2 FAQ

- **时间单位**：游戏内时间通过 tick 计算；tick 有时叫帧，假定为每秒 60 次（1/60 秒）。
- **瓦片尺寸**：内部 tilesize 为 8 世界单位；hitbox 等大多数值都以此为单位。
- **射程换算**：`lifetime * speed = range`。
- **NullPointerException**：表示某个不该为 null 的字段是 null——通常意味着某个必填字段缺失。
- **bleeding-edge（前沿版）**：Mindustry 最新开发版，特指 GitHub master 分支最新提交。前沿版上的更改通常会在下一个正式版本中进入游戏。

---

## 16. 迁移指南（v6/v7/v8）

> 本章合自官方 6/7/8-migrationv 页面，供从旧版本 mod 迁移时参考。

### 16.1 6.0 迁移指南

**通用变更：**
- 所有 mod 必须在 `mod.hjson` 中指定 `minGameVersion` 为 "105" 以上才会被加载。

**命名变更：**
- `ItemTurret`：`ammo` → `ammoTypes`；`reload` → `reloadTime`
- `ArtilleryTurret`、`BurstTurret`、`ChargeTurret`：已移除，用 `ItemTurret` 或 `PowerTurret` 代替，功能已合并到基类
- `BasicBulletType`：`bulletWidth` → `width`；`bulletHeight` → `height`；`bulletSprite` → `sprite`

**TileEntity → Building：**
- `TileEntity` 现在叫 `Building`。`Tile.entity` 改名 `Tile.build`，所有 `XxxEntity` 改名 `XxxBuild`。
- 许多函数（如 `draw()`、`placed()`）从 `Block` 移到 `Building`。`update(Tile tile)` 移到 `Building` 并改名 `updateTile()`。

**Array → Seq：**
- `arc.struct.Array` 改名 `arc.struct.Seq`（Sequence 缩写）。

**Plugin 包移动：**
- `mindustry.plugin.Plugin` → `mindustry.mod.Plugin`

**Call 方法去掉 "on" 前缀：**
- `onSnapshot` → `snapshot`；`onSetRules` → `setRules`；`onLabel` → `label`

**新玩家系统：**
- 玩家控制单位，不再以肉体存在（无生命值/武器）。所有动作由 `Unit` 执行。没有 `Mech` 类了，只有 `UnitType`。
- 每个单位有 `UnitController`（AI/逻辑/玩家）。检查玩家控制：`unit.isPlayer()`；获取玩家：`unit.getPlayer()`。设置玩家位置无效，要设单位位置。

### 16.2 7.0 迁移指南

**方块：**
- `Block#expanded` 已弃用为空操作，用 `Block#clipSize` 代替。
- `mindustry.world.meta.values.*` 类全部替换为 lambda，见 `StatValues`。
- `BlockForge` 移出实验包。
- `CacheLayer` 现在是可重写方法的类（不是枚举），`CacheLayer#add` 可注册新层。
- `variants`、`attributes` 等字段从 `Floor` 移到 `Block`。
- `Iconc` 及相关方法已移除，用 `UnlockableContent.uiIcon/fullIcon`。
- `Smelter`、`AttributeSmelter`、`Cultivator` 已弃用，用 `GenericCrafter` + `DrawSmelter`，属性支持用 `AttributeCrafter`。
- `ExtendingItemBridge`、`LiquidExtendingBridge` 合并到 `ItemBridge`/`LiquidBridge`。
- `PayloadAcceptor` 改名 `PayloadBlock`。
- 生成图标**必须**在 `createIcons` 中创建。
- `LiquidModule#total()` 弃用，用 `currentAmount()`。

**弹药：**
- 单位弹药相关代码全部失效。`ResupplyPoint` 移除。`AmmoType` 现在是接口。`AmmoTypes` 移除。弹药类移到 `mindustry.type.ammo` 包。`ContentType.ammo` 移除。

**Arc：**
- `Pixmap` API 完全改变。`SettingsDialog` 移到 Mindustry 代码库。TextureAtlas 用更小更快的 `aatls` 二进制格式。`Core.net` 移除，用 `arc.util.Http` 静态方法。`RidgedPerlin` 改名 `Ridged`。`Simplex`/`Ridged` 现在无状态，用静态方法。

**网络：**
- `Registrator` 移到 `Net`，注册方法公开。`InvokePacket` 移除，用生成的 packet 类。`RemoteRead{Server,Client}` 移除。`Packet` 现在是抽象类（不是接口）。

**杂项：**
- `BulletType#despawned` 不再被调用，用 `#removed` 监听所有移除事件。
- `Attribute` 现在是标准类（不是枚举），用 `Attribute.add` 注册。
- `Vars.miningRange` 移到 `UnitType`。
- `Tex` 中所有字段现在是 `Drawable`。

**贴图：**
- 单位/武器贴图自动生成轮廓（腿区域除外）。所有 mod 贴图在开启线性过滤时自动 alpha-bleed，无需手动处理。

### 16.3 8.0 迁移指南

**JSON mod：** 大概率不需要改。现有 JSON mod 仍能工作，但某些星球上内容显示方式有变化。见下面 Planets 部分。

**Java/JS mod 杂项：**
- `Binding` 键位值现在用 camelCase。
- 旧键位系统完全重做，支持自定义 mod 键位，见 `arc.input.KeyBind#add`。`Core.keybinds` 移除，用 `Keybind` 类。
- `createIcons` 之外不能调用 `Core.atlas.getPixmap`。需要生成图标就重写 `createIcons`。

**方块：**
- `Building` 大部分不必要的 getter 方法（`tile(Tile)`、`tile()`、`block()`）已移除，直接访问字段。
- 方块现在有单独的 `lightClipSize` 字段用于 `drawLight()` 裁剪。`emitLight` 必须为 true 才会调用此方法。
- `loopSound` 移除；循环必须在每个方块的 `Building` 中手动创建和更新（见 `Turret` 源码）。

**单位：**
- `Player#unit()` **现在可以为 null。** 访问单位前必须检查 `!player.dead()`。
- `Units.null` 移除。
- 命令现在是内容。`UnitCommand.all` 移除。单位命令现在是 `Seq`（不是数组）。

**星球：**
- 所有物品可见性相关字段（`itemWhitelist`、`hiddenItems`、`Rules.hiddenBuildItems`）已移除。要让内容在特定星球显示，修改它的 `shownPlanets` 字段包含该星球。如果设置了科技树，这会自动完成。

---

## 17. 字段速查大区：Modding Classes

> 本章为 v2 核心新增。所有字段表忠实官方 Modding Classes 页面原文（字段/类型/默认值/说明）。按继承层级和功能组织。
> 抓取说明：官方类页字段与 Java 源码同源，以官方类页呈现为准。`Turret` 基类官方页为空（仅目录），其字段需参考 Java 源码 `mindustry.world.blocks.defense.turrets.Turret`；`Planet` 官方链接失效。

### 17.1 Block 基类（extends UnlockableContent）

`Block` 是游戏中所有方块的基类。这是 v2 最完整的字段表（官方页 4705 token）。

| field | type | default | notes |
|---|---|---|---|
| hasItems | boolean | false | If true, buildings have an ItemModule. |
| hasLiquids | boolean | false | If true, buildings have a LiquidModule. |
| hasPower | boolean | false | If true, buildings have a PowerModule. |
| outputsLiquid | boolean | false | Flag for determining whether this block outputs liquid somewhere; used for connections. |
| consumesPower | boolean | true | Used by certain power blocks (nodes) to flag as non-consuming of power. |
| outputsPower | boolean | false | If true, this block is a generator that can produce power. |
| connectedPower | boolean | true | If false, power nodes cannot connect to this block. |
| conductivePower | boolean | false | If true, this block can conduct power like a cable. |
| outputsPayload | boolean | false | If true, this block can output payloads; affects blending. |
| acceptsUnitPayloads | boolean | false | If true, this block can input payloads. |
| acceptsPayload | boolean | false | If true, payloads will attempt to move into this block. |
| acceptsItems | boolean | false | Visual flag for blending of transportation blocks. |
| alwaysAllowDeposit | boolean | false | If true, not affected by the onlyDepositCore rule. |
| depositCooldown | float | -1.0 | Cooldown in seconds applied to player item depositing. |
| separateItemCapacity | boolean | false | If true, all item capacities are separate instead of pooled. |
| itemCapacity | int | 10 | maximum items this block can carry (usually per-type). |
| liquidCapacity | float | -1.0 | maximum total liquids if hasLiquids=true. Default 10, scales with max liquid consumption. |
| liquidPressure | float | 1.0 | higher numbers increase liquid output speed. |
| outputFacing | boolean | true | If true, outputs to facing direction. Used for blending. |
| noSideBlend | boolean | false | if true, does not accept input from sides (armored conveyors). |
| displayFlow | boolean | true | whether to display flow rate. |
| inEditor | boolean | true | whether this block is visible in the editor. |
| editorConfigurable | boolean | false | if true, buildEditorConfig called in editor. |
| lastConfig | Object | null | the last configuration value applied. |
| saveConfig | boolean | false | whether to save last config and apply to newly placed blocks. |
| copyConfig | boolean | true | whether to allow copying config through middle click. |
| clearOnDoubleTap | boolean | false | if true, double-tap clears configuration. |
| update | boolean | false | whether this block has a tile entity that updates. |
| destructible | boolean | false | whether this block has health and can be destroyed. note: setting false does nothing if update=true! |
| unloadable | boolean | true | whether unloaders work on this block. |
| isDuct | boolean | false | if true, acts as a duct, connects to armored ducts from side. |
| allowResupply | boolean | false | whether units can resupply by taking items. |
| solid | boolean | false | whether this is solid. |
| solidifes | boolean | false | whether this block CAN be solid. |
| teamPassable | boolean | false | if true, counts as non-solid block to this team. |
| underBullets | boolean | false | if true, cannot be hit by bullets unless explicitly targeted. |
| rotate | boolean | false | whether this is rotatable. |
| rotateDraw | boolean | true | if rotate and false, region won't rotate when drawing. |
| rotateDrawEditor | boolean | true | same, in editor. |
| visualRotationOffset | float | 0.0 | visual rotation offset used in broken plan rendering. |
| lockRotation | boolean | true | if rotate=false and true, rotation locked at 0 when placing. |
| ignoreLineRotation | boolean | false | if true, won't face line drag direction. |
| invertFlip | boolean | false | if true, schematic flips are inverted. |
| variants | int | 0 | number of different variant regions to use. |
| drawArrow | boolean | true | whether to draw a rotation arrow. |
| drawTeamOverlay | boolean | true | whether to draw the team corner by default. |
| saveData | boolean | false | for static blocks: if true, tile data() saved in world data. |
| breakable | boolean | false | whether you can break this with rightclick. |
| unitMoveBreakable | boolean | false | if true, broken by certain units stepping over it. |
| rebuildable | boolean | true | whether to add this block to brokenblocks. |
| privileged | boolean | false | logic-related block only usable with privileged processors. |
| requiresWater | boolean | false | whether only placeable on water. |
| placeableLiquid | boolean | false | whether placeable on any liquids. |
| placeablePlayer | boolean | true | whether placeable directly by player. |
| placeableOn | boolean | true | whether this floor can be placed on. |
| insulated | boolean | false | whether this block has insulating properties. |
| squareSprite | boolean | true | whether the sprite is a full square. |
| absorbLasers | boolean | false | whether this block absorbs laser attacks. |
| enableDrawStatus | boolean | true | if false, status is never drawn. |
| drawDisabled | boolean | true | whether to draw disabled status. |
| autoResetEnabled | boolean | true | whether to auto-reset enabled status after no logic interaction. |
| noUpdateDisabled | boolean | false | if true, stops updating when disabled. |
| updateInUnits | boolean | true | if true, updates when it's a payload in a unit. |
| alwaysUpdateInUnits | boolean | false | if true, updates in payloads regardless of game rule. |
| canPickup | boolean | true | if true, can be picked up in payloads. |
| deconstructDropAllLiquid | boolean | false | if false, only incinerable liquids dropped when deconstructing. |
| useColor | boolean | true | Whether to use this block's color in minimap. |
| itemDrop | Item | null | item that drops from this block, used for drills. |
| playerUnmineable | boolean | false | if true, cannot be mined by players. |
| attributes | Attributes | new Attributes() | Affinities for floors. |
| scaledHealth | float | -1.0 | Health per square tile; multiplied by size*size. If <0, default 40. |
| health | int | -1 | building health; -1 to use scaledHealth. |
| armor | float | 0.0 | damage absorption, similar to unit armor. |
| baseExplosiveness | float | 0.0 | base block explosiveness. |
| explosivenessScale | float | 1.0 | scaling of explosiveness based on items/liquids. |
| flammabilityScale | float | 1.0 | scaling of explosion flammability. |
| baseShake | float | 3.0 | base screen shake upon destruction. |
| destroyBullet | BulletType | null | bullet spawned when destroyed. |
| destroyBulletSameTeam | boolean | false | if true, destroyBullet spawned on block's team. |
| lightLiquid | Liquid | null | liquid used for lighting. |
| drawCracks | boolean | true | whether cracks are drawn when damaged. |
| createRubble | boolean | true | whether rubble is created when destroyed. |
| floating | boolean | false | whether placeable on edges of liquids. |
| size | int | 1 | multiblock size. |
| offset | float | 0.0 | multiblock offset. |
| sizeOffset | int | 0 | offset for iteration (internal). |
| clipSize | float | -1.0 | Clipping size. Should be as large as the block will draw. |
| lightClipSize | float | 0.0 | Clipping size for lights only. |
| placeOverlapRange | float | 50.0 | range checked for enemy blocks when placeRangeCheck enabled. |
| crushDamageMultiplier | float | 1.0 | Multiplier of damage dealt by tanks. Not for crawlers. |
| crushFragile | boolean | false | If true, instantly destroyed by tanks with crushFragile. |
| timers | int | 1 | Max of timers used. |
| cacheLayer | CacheLayer | normal | Cache layer for 'cached' rendering. |
| drawDynamic | boolean | true | If true, draw() called on the building. |
| drawCached | boolean | false | If enabled, drawCached() called. |
| buildingCacheLayer | BuildingCacheLayer | normal | |
| fillsTile | boolean | true | if false, floor drawn under this block even if cached. |
| forceDark | boolean | false | If true, can be covered by darkness/fog even if synthetic. |
| alwaysReplace | boolean | false | whether this block can be replaced in all cases. |
| replaceable | boolean | true | if false, never replaceable. |
| group | BlockGroup | none | blocks in same group can replace each other. |
| flags | EnumSet of BlockFlag | of() | List of block flags. Used for AI indexing. |
| priority | float | 0.0 | Targeting priority as seen by enemies. |
| unitCapModifier | int | 0 | How much this block affects unit cap by. Needs unitModifier flag. |
| configurable | boolean | false | Whether the block can be tapped and selected to configure. |
| configureSound | Sound | click | Sound when configured. |
| ignoreResizeConfig | boolean | false | If true, no pointConfig transform on map resize. |
| commandable | boolean | false | If true, selectable like a unit when commanding. |
| allowConfigInventory | boolean | true | If true, building inventory shown with config. |
| diagonalConfigInventory | boolean | false | If true, inventory placed diagonally top right. |
| selectionRows | int | 5 | how large selection menus (sorters) should be. |
| selectionColumns | int | 4 | how large selection menus should be. |
| logicConfigurable | boolean | false | If true, configurable by logic. |
| delayLandingConfig | boolean | false | If true, config delayed during landing buildup animation. |
| consumesTap | boolean | false | Whether consumes touchDown events when tapped. |
| drawLiquidLight | boolean | true | Whether to draw glow of liquid if it has one. |
| envRequired | int | 0 | Environmental flags ALL required. 0 = any. |
| envEnabled | int | 1 | environment flags it can function in. |
| envDisabled | int | 0 | environment flags it CANNOT function in. |
| sync | boolean | false | Whether to periodically sync across network. |
| conveyorPlacement | boolean | false | Whether uses conveyor-type placement mode. |
| allowDiagonal | boolean | true | If false, no diagonal placement (ctrl). |
| swapDiagonalPlacement | boolean | false | Whether to swap diagonal placement modes. |
| allowRectanglePlacement | boolean | false | Whether to allow rectangular placement. |
| schematicPriority | int | 0 | Build queue priority in schematics. |
| mapColor | Color | 000000ff | minimap/map preview color. Do not set manually! |
| hasColor | boolean | false | Whether this block has a minimap color. |
| targetable | boolean | true | Whether units target this block. |
| attacks | boolean | false | If true, attacks and is a turret in indexer. Must implement Ranged. |
| suppressable | boolean | false | If true, mending-related, suppressable by special units/missiles. |
| canOverdrive | boolean | true | Whether overdrive core affects this block. |
| outlineColor | Color | 404049ff | Outlined icon color. |
| outlineIcon | boolean | false | Whether any icon region has an outline added. |
| outlineRadius | int | 4 | Outline icon radius. |
| outlinedIcon | int | -1 | Which icon region gets outline. Uses last if <=0. |
| hasShadow | boolean | true | Whether has a shadow under it. |
| customShadow | boolean | false | If true, custom shadow (name-shadow) drawn. |
| placePitchChange | boolean | true | Should build sound change in pitch. |
| breakPitchChange | boolean | true | Should deconstruct sound change in pitch. |
| placeSound | Sound | unset | Sound when built. |
| breakSound | Sound | unset | Sound when deconstructed. |
| destroySound | Sound | unset | Sound when destroyed. |
| destroySoundVolume | float | 1.0 | Volume of destruction sound. |
| destroyPitchMin | float | 1.0 | Range of destroy sound. |
| destroyPitchMax | float | 1.0 | Range of destroy sound. |
| albedo | float | 0.0 | How reflective this block is. |
| lightColor | Color | ffffffff | Environmental passive light color. |
| emitLight | boolean | false | If true, drawLight() called. |
| obstructsLight | boolean | true | If true, obstructs light from other blocks. |
| lightRadius | float | 60.0 | Radius of emitted light. |
| fogRadius | int | -1 | How much fog uncovered, in tiles. <=0 to disable. |
| ambientSound | Sound | none | Idle sound. Uses one loop for all blocks. |
| ambientSoundVolume | float | 0.05 | Idle sound base volume. |
| requirements | ItemStack[] | [] | Cost of constructing this block. |
| category | Category | distribution | Category in place menu. |
| buildTime | float | -1.0 | Time to build in ticks. <0 = calculated dynamically. |
| buildVisibility | BuildVisibility | hidden | Whether visible and currently buildable. |
| buildCostMultiplier | float | 1.0 | Multiplier for build speed. |
| deconstructThreshold | float | 0.0 | Build completion at which deconstruction finishes. |
| instantDeconstruct | boolean | false | If true, deconstructs immediately (no refund). |
| instantBuild | boolean | false | If true, constructs immediately (no resource requirement). |
| ignoreBuildDarkness | boolean | false | If true, placeable in "dark" areas. Editor static walls only. |
| placeEffect | Effect | placeBlock | Effect for placing. Passes size as rotation. |
| breakEffect | Effect | breakBlock | Effect for breaking. |
| destroyEffect | Effect | dynamicExplosion | Effect for destroying. |
| researchCostMultiplier | float | 1.0 | Multiplier for research cost. |
| researchCostMultipliers | ObjectFloatMap of Item | new ObjectFloatMap<>() | Cost multipliers per-item. |
| researchCost | ItemStack[] | null | Override for research cost. |
| forceTeam | Team | null | If set, all blocks forced to this team. |
| instantTransfer | boolean | false | Whether has instant transfer. |
| maxConsecutive | int | 2 | Maximum consecutive instantTransfer blocks. |
| quickRotate | boolean | true | Whether you can rotate after placing. |
| allowDerelictRepair | boolean | true | If true, derelict block repairable by clicking. |
| selectScroll | float | 0.0 | Scroll position for certain blocks. |
| itemFilter | boolean[] | [] | Consumption filters. |
| liquidFilter | boolean[] | [] | Consumption filters. |
| consumers | Consume[] | [] | Array of consumers. Populated after init(). |
| hasConsumers | boolean | false | Set true if has any consumers. |
| consPower | ConsumePower | null | The single power consumer, if applicable. |
| dumpTime | int | 5 | How often to try dumping items in ticks (5 = 12/sec). |

**Sprites**：`<name>` 方块主贴图。

### 17.2 Defense 防御方块

#### Wall（extends Block）

| field | type | default | notes |
|---|---|---|---|
| lightningChance | float | -1.0 | Lighting chance. -1 to disable |
| lightningDamage | float | 20.0 | |
| lightningLength | int | 17 | |
| lightningColor | Color | f3e979ff | |
| lightningSound | Sound | shootArc | |
| chanceDeflect | float | -1.0 | Bullet deflection chance. -1 to disable |
| flashHit | boolean | false | |
| flashColor | Color | ffffffff | |
| deflectSound | Sound | none | |
| autotile | boolean | false | If true, uses autotiling; variants not supported. |

> 注：v1 中 SurgeWall/DeflectorWall 已合并入 Wall（lightning/deflect 字段直接在 Wall 上）。

#### Door（extends Wall）

| field | type | default | notes |
|---|---|---|---|
| timerToggle | int | 1 | |
| openfx | Effect | dooropen | |
| closefx | Effect | doorclose | |
| doorSound | Sound | door | |
| chainEffect | boolean | false | |
| openRegion | TextureRegion | null | |

#### ShieldWall（extends Wall）

| field | type | default | notes |
|---|---|---|---|
| shieldHealth | float | 900.0 | |
| breakCooldown | float | 600.0 | |
| regenSpeed | float | 2.0 | |
| glowColor | Color | ff75317f | |
| glowMag | float | 0.6 | |
| glowScl | float | 8.0 | |
| glowRegion | TextureRegion | null | |

#### StaticWall（extends Prop）

| field | type | default | notes |
|---|---|---|---|
| large | TextureRegion | null | |
| split | TextureRegion[][] | null | |
| autotile | boolean | false | If true, uses autotiling; variants not supported. |
| autotileMidVariants | int | 1 | If >1, middle region has random variants. |

#### ShockMine（extends Block）

| field | type | default | notes |
|---|---|---|---|
| timerDamage | int | 1 | |
| cooldown | float | 80.0 | |
| tileDamage | float | 5.0 | |
| damage | float | 13.0 | |
| length | int | 10 | |
| tendrils | int | 6 | |
| lightningColor | Color | a9d8ffff | |
| shots | int | 6 | |
| inaccuracy | float | 0.0 | |
| bullet | BulletType | null | |
| teamAlpha | float | 0.3 | |
| teamRegion | TextureRegion | null | |

#### ForceProjector（extends Block）

| field | type | default | notes |
|---|---|---|---|
| timerUse | int | 1 | |
| phaseUseTime | float | 350.0 | |
| phaseRadiusBoost | float | 80.0 | |
| phaseShieldBoost | float | 400.0 | |
| radius | float | 101.7 | |
| sides | int | 6 | |
| shieldRotation | float | 0.0 | |
| shieldHealth | float | 700.0 | |
| cooldownNormal | float | 1.75 | |
| cooldownLiquid | float | 1.5 | |
| cooldownBrokenBase | float | 0.35 | |
| coolantConsumption | float | 0.1 | |
| consumeCoolant | boolean | true | |
| crashDamageMultiplier | float | 2.0 | |
| breakSound | Sound | shieldBreak | |
| hitSound | Sound | shieldHit | |
| hitSoundVolume | float | 0.12 | |
| absorbEffect | Effect | absorb | |
| shieldBreakEffect | Effect | shieldBreak | |
| forceShrinkEffect | Effect | forceShrink | |

#### MendProjector（extends Block）

| field | type | default | notes |
|---|---|---|---|
| timerUse | int | 1 | |
| baseColor | Color | 84f491ff | |
| phaseColor | Color | 84f491ff | |
| reload | float | 250.0 | |
| range | float | 60.0 | |
| healPercent | float | 12.0 | |
| phaseBoost | float | 12.0 | |
| phaseRangeBoost | float | 50.0 | |
| useTime | float | 400.0 | |
| mendSound | Sound | healWave | |
| mendSoundVolume | float | 0.5 | |

#### OverdriveProjector（extends Block）

| field | type | default | notes |
|---|---|---|---|
| reload | float | 60.0 | |
| range | float | 80.0 | |
| speedBoost | float | 1.5 | |
| speedBoostPhase | float | 0.75 | |
| useTime | float | 400.0 | |
| phaseRangeBoost | float | 20.0 | |
| hasBoost | boolean | true | |
| baseColor | Color | feb380ff | |
| phaseColor | Color | ffd59eff | |

### 17.3 Turrets 炮塔

> **抓取缺口**：`Turret` 基类官方 Modding Classes 页为空（仅 12 token 目录），其字段需参考 Java 源码 `mindustry.world.blocks.defense.turrets.Turret`。以下子类字段来自官方页。

#### ItemTurret（extends Turret）

| field | type | default | notes |
|---|---|---|---|
| ammoTypes | ObjectMap | new ObjectMap<>() | 键为 Item 名，值为要发射的 BulletType（v6 前叫 ammo） |

#### PowerTurret（extends Turret）

| field | type | default | notes |
|---|---|---|---|
| shootType | BulletType | null | [必需] |

#### LiquidTurret（extends Turret）

| field | type | default | notes |
|---|---|---|---|
| ammoTypes | ObjectMap of Liquid, BulletType | {} | Liquid 名到子弹类型的映射 |
| extinguish | boolean | true | |

### 17.4 Crafting 合成方块

#### GenericCrafter（extends Block）

| field | type | default | notes |
|---|---|---|---|
| outputItem | ItemStack | null | Written to outputItems if outputItems is null. |
| outputItems | ItemStack[] | null | Overwrites outputItem if not null. |
| outputLiquid | LiquidStack | null | Written to outputLiquids if outputLiquids is null. |
| outputLiquids | LiquidStack[] | null | Overwrites outputLiquid if not null. |
| liquidOutputDirections | int[] | { -1 } | directions in same order as outputLiquids. -1 = every direction. |
| dumpExtraLiquid | boolean | true | if true, dump excess when space for at least one liquid type. |
| ignoreLiquidFullness | boolean | false | |
| craftTime | float | 80.0 | |
| craftEffect | Effect | none | |
| updateEffect | Effect | none | |
| updateEffectChance | float | 0.04 | |
| updateEffectSpread | float | 4.0 | |
| warmupSpeed | float | 0.019 | |
| legacyReadWarmup | boolean | false | Only for legacy cultivator blocks. |
| drawer | DrawBlock | new DrawDefault() | |

#### Drill（extends Block）

| field | type | default | notes |
|---|---|---|---|
| hardnessDrillMultiplier | float | 50.0 | |
| tier | int | 0 | Maximum tier of blocks this drill can mine. |
| drillTime | float | 300.0 | Base time to drill one ore, in frames. |
| liquidBoostIntensity | float | 1.6 | How many times faster when boosted by liquid. |
| warmupSpeed | float | 0.015 | Speed at which the drill speeds up. |
| blockedItem | Item | null | Special exemption item this drill can't mine. |
| blockedItems | Seq of Item | null | Special exemption items this drill can't mine. |
| drawMineItem | boolean | true | Whether to draw the item being mined. |
| drillEffect | Effect | mine | Effect when item is produced. Colored. |
| drillEffectRnd | float | -1.0 | Drill effect randomness. Block size by default. |
| drillEffectChance | float | 0.02 | Chance of displaying the effect. |
| rotateSpeed | float | 2.0 | Speed the drill bit rotates at. |
| updateEffect | Effect | pulverizeSmall | Effect randomly played while drilling. |
| updateEffectChance | float | 0.02 | Chance the update effect appears. |
| drillMultipliers | ObjectFloatMap of Item | new ObjectFloatMap<>() | Multipliers of drill speed per item. Defaults to 1. |
| drawRim | boolean | false | |
| drawSpinSprite | boolean | true | |
| heatColor | Color | ff5512ff | |

### 17.5 Power 电力方块

#### PowerGenerator（extends PowerDistributor）

| field | type | default | notes |
|---|---|---|---|
| powerProduction | float | 0.0 | power per tick at 100% efficiency. |
| generationType | Stat | basePowerGeneration | |
| drawer | DrawBlock | new DrawDefault() | |
| explosionRadius | int | 12 | |
| explosionDamage | int | 0 | |
| explodeEffect | Effect | none | |
| explodeSound | Sound | none | |
| explosionPuddles | int | 10 | |
| explosionPuddleRange | float | 16.0 | |
| explosionPuddleAmount | float | 100.0 | |
| explosionPuddleLiquid | Liquid | null | |
| explosionMinWarmup | float | 0.0 | |
| explosionShake | float | 0.0 | |
| explosionShakeDuration | float | 6.0 | |

#### ConsumeGenerator（extends PowerGenerator）

A generator that takes in certain items or liquids.

| field | type | default | notes |
|---|---|---|---|
| itemDuration | float | 120.0 | ticks during which a single item produces power. |
| warmupSpeed | float | 0.05 | |
| effectChance | float | 0.01 | |
| generateEffect | Effect | none | |
| consumeEffect | Effect | none | |
| generateEffectRange | float | 3.0 | |
| baseLightRadius | float | 65.0 | |
| outputLiquid | LiquidStack | null | |
| explodeOnFull | boolean | false | explodes when outputLiquid exceeds capacity. |
| filterItem | ConsumeItemFilter | null | |
| filterLiquid | ConsumeLiquidFilter | null | |
| itemDurationMultipliers | ObjectFloatMap of Item | new ObjectFloatMap<>() | Multiplies itemDuration for a given item. |

#### SolarGenerator（extends PowerGenerator）

> 官方页无额外字段（仅继承 PowerGenerator）。

#### NuclearReactor（extends PowerGenerator）

| field | type | default | notes |
|---|---|---|---|
| timerFuel | int | 1 | |
| lightColor | Color | 7f19eaff | |
| coolColor | Color | ffffff00 | |
| hotColor | Color | ff9575a3 | |
| itemDuration | float | 120.0 | ticks to consume 1 fuel |
| heating | float | 0.01 | heating per frame * fullness |
| smokeThreshold | float | 0.3 | threshold at which block starts smoking |
| flashThreshold | float | 0.46 | heat threshold at which lights start flashing |
| coolantPower | float | 0.5 | heat removed per unit of coolant |
| fuelItem | Item | thorium | |

#### PowerNode（extends PowerBlock）

| field | type | default | notes |
|---|---|---|---|
| laserRange | float | 6.0 | |
| maxNodes | int | 3 | |
| autolink | boolean | true | |
| drawRange | boolean | true | |
| sameBlockConnection | boolean | false | |
| laserScale | float | 0.25 | |
| useLod | boolean | true | |
| powerLayer | float | 70.0 | |
| laserColor1 | Color | ffffffff | |
| laserColor2 | Color | fbd367ff | |

#### Battery（extends PowerDistributor）

| field | type | default | notes |
|---|---|---|---|
| drawer | DrawBlock | null | |
| emptyLightColor | Color | f8c266ff | |
| fullLightColor | Color | fb9567ff | |

### 17.6 Distribution 物流方块

#### Conveyor（extends Block）

| field | type | default | notes |
|---|---|---|---|
| speed | float | 0.0 | |
| displayedSpeed | float | 0.0 | |
| pushUnits | boolean | true | |
| junctionReplacement | Block | null | |
| bridgeReplacement | Block | null | |

#### Router（extends Block）

| field | type | default | notes |
|---|---|---|---|
| speed | float | 8.0 | |

#### Junction（extends Block）

| field | type | default | notes |
|---|---|---|---|
| speed | float | 26.0 | frames to pass through |
| capacity | int | 6 | |
| displayedSpeed | float | 13.0 | |

#### Sorter（extends Block）

| field | type | default | notes |
|---|---|---|---|
| invert | boolean | false | 反转 |

#### ItemBridge（extends Block）

| field | type | default | notes |
|---|---|---|---|
| range | int | 0 | |
| transportTime | float | 0.0 | |
| fadeIn | boolean | true | |
| moveArrows | boolean | true | |
| pulse | boolean | false | |
| arrowSpacing | float | 4.0 | |
| arrowOffset | float | 2.0 | |
| arrowPeriod | float | 0.4 | |
| arrowTimeScl | float | 6.2 | |
| bridgeWidth | float | 6.5 | |

**Sprites**：`<name>-end`、`<name>-bridge`、`<name>-arrow`

#### MassDriver（extends Block）

| field | type | default | notes |
|---|---|---|---|
| range | float | 0.0 | |
| rotateSpeed | float | 5.0 | |
| translation | float | 7.0 | |
| minDistribute | int | 10 | |
| knockback | float | 4.0 | |
| reload | float | 100.0 | |
| bullet | MassDriverBolt | new MassDriverBolt() | |
| bulletSpeed | float | 5.5 | |
| bulletLifetime | float | 200.0 | |
| shootEffect | Effect | shootBig2 | |
| smokeEffect | Effect | shootBigSmoke2 | |
| receiveEffect | Effect | mineBig | |
| shootSound | Sound | massdriver | |
| receiveSound | Sound | massdriverReceive | |
| shootSoundVolume | float | 0.5 | |
| shake | float | 3.0 | |

**Sprites**：`<name>-base`

#### PayloadConveyor（extends Block）

| field | type | default | notes |
|---|---|---|---|
| moveTime | float | 45.0 | |
| moveForce | float | 201.0 | |
| interp | Interp | pow5 | |
| payloadLimit | float | 3.0 | |
| pushUnits | boolean | true | |

#### Unloader（extends Block）

| field | type | default | notes |
|---|---|---|---|
| speed | float | 1.0 | |
| allowCoreUnload | boolean | true | |

#### StorageBlock（extends Block）

| field | type | default | notes |
|---|---|---|---|
| coreMerge | boolean | true | |

### 17.7 Liquid 液体方块

#### Conduit（extends LiquidBlock）

| field | type | default | notes |
|---|---|---|---|
| timerFlow | int | 1 | |
| botColor | Color | 565656ff | |
| capRegion | TextureRegion | null | |
| padCorners | boolean | true | liquid region padded at corners. |
| leaks | boolean | true | |
| junctionReplacement | Block | null | |
| bridgeReplacement | Block | null | |

#### Pump（extends LiquidBlock）

| field | type | default | notes |
|---|---|---|---|
| pumpAmount | float | 0.2 | Pump amount per tile. |
| consumeTime | float | 300.0 | Interval between item consumptions. |
| warmupSpeed | float | 0.019 | |

#### LiquidRouter（extends LiquidBlock）

| field | type | default | notes |
|---|---|---|---|
| liquidPadding | float | 0.0 | |

### 17.8 Environment 环境方块

#### Floor（extends Block）

| field | type | default | notes |
|---|---|---|---|
| edge | String | "stone" | edge fallback, used mainly for ores. |
| speedMultiplier | float | 1.0 | Multiplies unit velocity when walked on. |
| dragMultiplier | float | 1.0 | Multiplies unit drag when walked on. |
| damageTaken | float | 0.0 | Damage taken per tick on this tile. |
| drownTime | float | 0.0 | How many ticks to drown. 0 to disable. |
| walkEffect | Effect | none | Effect when walking on this floor. |
| walkSound | Sound | none | Sound made when walking. |
| walkSoundVolume | float | 0.1 | Volume of walk sound. |
| walkSoundPitchMin | float | 0.8 | |
| walkSoundPitchMax | float | 1.2 | |
| drownUpdateEffect | Effect | bubble | Effect displayed when drowning. |
| status | StatusEffect | none | Status effect applied when walking on. |
| statusDuration | float | 60.0 | Intensity of applied status effect. |
| liquidDrop | Liquid | null | liquids dropped from this block (pumps). |
| liquidMultiplier | float | 1.0 | Multiplier for pumped liquids (deep water). |
| isLiquid | boolean | false | whether this block is liquid. |
| overlayAlpha | float | 0.65 | opacity of overlay for liquid floors. |
| supportsOverlay | boolean | false | whether this floor supports an overlay floor. |
| shallow | boolean | false | shallow water flag for generation. |
| blendGroup | Block | this | Group that this block does not draw edges on. |
| oreDefault | boolean | false | Whether this ore generates in maps by default. |
| oreScale | float | 24.0 | Ore generation params. |
| oreThreshold | float | 0.828 | Ore generation params. |
| wall | Block | air | Wall variant of this block. |
| decoration | Block | air | Decoration block. Usually a rock. |
| canShadow | boolean | true | Whether units can draw shadows over this. |
| forceDrawLight | boolean | false | If true, ignores obstructsLight of overlays. |
| needsSurface | boolean | true | Whether this overlay needs a surface. False for floating blocks. |
| allowCorePlacement | boolean | false | If true, cores can be placed on this floor. |
| wallOre | boolean | false | If true, this ore is allowed on walls. |
| autotile | boolean | false | If true, uses autotiling; variants not supported. |
| autotileMidVariants | int | 1 | If >1, middle region has random variants. |
| autotileVariants | int | 1 | Variants of main autotile sprite. |
| drawEdgeIn | boolean | true | If true, draws edges of other floors on itself. |
| drawEdgeOut | boolean | true | If true, draws its edges onto other floors. |

#### OreBlock（extends OverlayFloor）

> 官方页：An overlay ore for a specific item type. 无额外字段（继承 Floor/OverlayFloor）。

#### Prop（extends Block）

| field | type | default | notes |
|---|---|---|---|
| layer | float | 32.0 | |

### 17.9 Units 单位

#### UnitFactory（extends UnitBlock）

| field | type | default | notes |
|---|---|---|---|
| capacities | int[] | [] | |
| plans | Seq of UnitPlan | [] | |
| createSound | Sound | unitCreate | |
| createSoundVolume | float | 1.0 | |

#### Reconstructor（extends UnitBlock）

| field | type | default | notes |
|---|---|---|---|
| constructTime | float | 120.0 | |
| upgrades | Seq of UnitType[] | [] | |
| capacities | int[] | [] | |
| createSound | Sound | unitCreate | |
| createSoundVolume | float | 1.0 | |

#### UnitType（extends UnlockableContent）

> 官方页 5625 token，为最庞大的字段表之一。以下为关键分组字段。

**运动与属性：**

| field | type | default | notes |
|---|---|---|---|
| speed | float | 1.1 | movement speed (world units/t) |
| boostMultiplier | float | 1.0 | multiplier for speed when boosting |
| floorMultiplier | float | 1.0 | how affected by terrain |
| rotateSpeed | float | 5.0 | body rotation speed in degrees/t |
| drag | float | 0.3 | movement drag as fraction |
| accel | float | 0.5 | acceleration as fraction of speed |
| hitSize | float | 6.0 | size of one side of hitbox square |
| health | float | 200.0 | raw health amount |
| armor | float | 0.0 | incoming damage reduced by this amount |
| range | float | -1.0 | min range of any weapon (approach targets). Override if >0. |
| maxRange | float | -1.0 | max range of any weapon |
| mineRange | float | 70.0 | range at which can mine ores |
| buildRange | float | 220.0 | range at which can build |
| payloadCapacity | float | 8.0 | Payload capacity in world units^2 |
| buildSpeed | float | -1.0 | building speed multiplier; <0 to disable. |
| researchCostMultiplier | float | 50.0 | multiplier for research cost |

**布尔标志：**

| field | type | default | notes |
|---|---|---|---|
| flying | boolean | false | if true, always at elevation 1 |
| targetAir | boolean | true | whether tries to attack air units |
| targetGround | boolean | true | whether tries to attack ground units |
| canBoost | boolean | false | if true, can boost into air |
| logicControllable | boolean | true | if false, processors cannot control |
| playerControllable | boolean | true | if false, players cannot control |
| canDrown | boolean | true | if true, ground unit drowns in deep liquids |
| useUnitCap | boolean | true | if false, ignores unit cap |
| createWreck | boolean | true | if false, no falling corpse on death |
| createScorch | boolean | true | if false, no scorch marks on death |
| lowAltitude | boolean | false | drawn under effects/bullets (visual) |
| hovering | boolean | false | if true, not affected by floor under it |
| omniMovement | boolean | true | if true, can move any direction regardless of rotation |
| naval | boolean | false | detected as naval - do NOT assign manually! |
| mineFloor | boolean | true | whether can mine ores from floors |
| mineWalls | boolean | false | whether can mine ores from walls |

**武器与 AI：**

| field | type | default | notes |
|---|---|---|---|
| abilities | Seq of Ability | [] | list of "abilities" updating each frame |
| weapons | Seq of Weapon | [] | All weapons |
| immunities | ObjectSet of StatusEffect | [] | status effects that cannot be applied |
| aiController | Prov of UnitController | {code} | default AI controller |
| commands | Seq of UnitCommand | [] | RTS commands |
| stances | Seq of UnitStance | [] | unit stances |
| mineTier | int | -1 | max hardness of ore mineable (<0 to disable) |
| mineSpeed | float | 1.0 | mining speed |
| mineItems | Seq of Item | [Copper, Lead, Titanium, Thorium] | Target items to mine |

**声音与视觉：**

| field | type | default | notes |
|---|---|---|---|
| deathSound | Sound | unset | when unit explodes |
| loopSound | Sound | none | looped when around |
| stepSound | Sound | mechStepSmall | step sound |
| moveSound | Sound | none | looped when moving |
| outlineColor | Color | 565666ff | sprite outline color |
| outlineRadius | int | 3 | outline thickness |
| outline | boolean | true | if false, no sprite outlines |
| healColor | Color | 98ffa9ff | flash color when healed |

**腿部/机甲/履带（细分单位类型用）：**

| field | type | default | notes |
|---|---|---|---|
| legCount | int | 4 | number of legs |
| legGroupSize | int | 2 | size of groups legs move in |
| legLength | float | 10.0 | total leg length (both segments) |
| legSpeed | float | 0.1 | how fast legs move to destination |
| legMaxLength | float | 1.75 | max length as fraction |
| legMinLength | float | 0.0 | min length as fraction |
| mechSideSway | float | 0.54 | mech swaying animation |
| mechFrontSway | float | 0.1 | mech swaying animation |
| treadRects | Rect[] | [] | treads as rectangles in IMAGE COORDINATES |
| treadFrames | int | 18 | frames of movement in a tread |

**分段/导弹/坦克（蠕虫、导弹、坦克单位用）：**

| field | type | default | notes |
|---|---|---|---|
| segments | int | 0 | number of independent segments |
| segmentUnits | int | 1 | number of independent units spawned |
| segmentMaxRot | float | 30.0 | max difference between segment angles |
| crushDamage | float | 0.0 | damage to blocks under tank/crawler per frame |
| crushFragile | boolean | false | fragile blocks crushed in 1x1 |
| crawlSlowdown | float | 0.5 | speed multiplier at crawlSlowdownFrac |
| crawlSlowdownFrac | float | 0.55 | fraction of solids needed for crawlSlowdown |
| lifetime | float | 300.0 | lifetime of this missile |
| homingDelay | float | 10.0 | ticks before missile starts homing |

> 完整字段（含 engines、parts、trail、shadow 等约 200+ 字段）见官方 UnitType 类页。

### 17.10 Content 内容类型

#### Item（extends UnlockableContent）

| field | type | default | notes |
|---|---|---|---|
| color | Color | 000000ff | |
| explosiveness | float | 0.0 | how explosive this item is. |
| flammability | float | 0.0 | >0.3 makes eligible for item burners. |
| radioactivity | float | 0.0 | how radioactive this item is. |
| charge | float | 0.0 | how electrically potent. |
| hardness | int | 0 | drill hardness of the item |
| cost | float | 1.0 | base material cost (1 cost = 1 tick build time) |
| healthScaling | float | 0.0 | block default health multiplied by 1+scaling |
| lowPriority | boolean | false | if true, lowest priority to drills. |
| frames | int | 0 | If >0, animated. |
| transitionFrames | int | 0 | generated transition frames between each frame |
| frameTime | float | 5.0 | ticks between animation frames. |
| buildable | boolean | true | If false, incinerated in certain cores. |
| hidden | boolean | false | |

#### Liquid（extends UnlockableContent）

"A better name for this class would be 'fluid', but it's too late for that."

| field | type | default | notes |
|---|---|---|---|
| gas | boolean | false | If true, treated as gas (no puddles) |
| color | Color | 000000ff | Color in pipes and on ground. |
| gasColor | Color | bfbfbfff | Color in gas form. |
| barColor | Color | null | Color used in bars. |
| lightColor | Color | 00000000 | Color for lights (alpha = brightness). |
| flammability | float | 0.0 | 0-1. 0.5+ very flammable. |
| temperature | float | 0.5 | 0.5 = room temp, 0 = very cold, 1 = molten hot |
| heatCapacity | float | 0.5 | how much heat stored. 0.4=water. |
| viscosity | float | 0.5 | thickness. 0.5=water, 1=tar. |
| explosiveness | float | 0.0 | prone to explode when heated. 0=nothing, 1=nuke. |
| blockReactive | boolean | true | whether reacts in blocks (slag with water). |
| coolant | boolean | true | if false, cannot be coolant. |
| moveThroughBlocks | boolean | false | if true, moves through blocks as puddle. |
| incinerable | boolean | true | if true, can be incinerated. |
| effect | StatusEffect | none | Associated status effect. |
| particleEffect | Effect | none | Effect shown in puddles. |
| particleSpacing | float | 60.0 | particle effect rate spacing in ticks. |
| boilPoint | float | 2.0 | Temperature at which vaporizes. |
| vaporEffect | Effect | vapor | Effect when vaporizes. |
| hidden | boolean | false | if true, hidden in most UI. |
| canStayOn | ObjectSet of Liquid | new ObjectSet<>() | puddles this can stay on (oil on water). |

#### StatusEffect（extends UnlockableContent）

Built-in constants: `none` `burning` `freezing` `unmoving` `slow` `fast` `wet` `muddy` `melting` `sapped` `tarred` `overdrive` `overclock` `shielded` `shocked` `blasted` `corroded` `boss` `sporeSlowed` `disarmed` `electrified` `invincible` `dynamic`

| field | type | default | notes |
|---|---|---|---|
| damageMultiplier | float | 1.0 | Damage dealt by unit with effect. |
| healthMultiplier | float | 1.0 | Unit health multiplier. |
| speedMultiplier | float | 1.0 | Unit speed multiplier. |
| reloadMultiplier | float | 1.0 | Unit reload multiplier. |
| buildSpeedMultiplier | float | 1.0 | Unit build speed multiplier. |
| dragMultiplier | float | 1.0 | Unit drag multiplier. |
| transitionDamage | float | 0.0 | Damage upon transition to an affinity. |
| disarm | boolean | false | Unit weapon(s) disabled. |
| damage | float | 0.0 | Damage per frame. |
| intervalDamageTime | float | 0.0 | Spacing between interval damage. <=0 disable. |
| intervalDamage | float | 0.0 | Interval damage dealt. |
| intervalDamagePierce | boolean | false | If true, interval damage is armor piercing. |
| effectChance | float | 0.15 | Chance of visual effect appearing. |
| permanent | boolean | false | If true, never disappears. |
| reactive | boolean | false | Only reacts with other effects, cannot be applied. |
| show | boolean | true | Whether to show in database. |
| color | Color | ffffffff | Tint color of effect. |
| effect | Effect | none | Effect randomly on top of affected unit. |
| applyEffect | Effect | none | Effect when applied to a unit. |
| applyColor | Color | ffffffff | Tint color of apply effect. |
| affinities | ObjectSet of StatusEffect | [] | Affinity values for stat displays. |
| opposites | ObjectSet of StatusEffect | [] | Opposite values for stat displays. |
| outline | boolean | true | Set false to disable outline generation. |

### 17.11 BulletType 子弹类型

#### BasicBulletType（extends BulletType）

Built-in constants: `placeholder` `spaceLiquid` `damageLightning` `damageLightningGround` `damageLightningAir` `fireball`

"An extended BulletType for most ammo-based bullets shot from turrets and units. Draws 1-2 sprites that can spin or shrink."

| field | type | default | notes |
|---|---|---|---|
| backColor | Color | f9c27aff | |
| frontColor | Color | fff8e8ff | |
| mixColorFrom | Color | ffffff00 | |
| mixColorTo | Color | ffffff00 | |
| width | float | 5.0 | |
| height | float | 7.0 | |
| shrinkX | float | 0.0 | |
| shrinkY | float | 0.5 | |
| shrinkInterp | Interp | linear | |
| spin | float | 0.0 | |
| rotationOffset | float | 0.0 | |
| sprite | String | bullet | |
| backSprite | String | null | |

#### MissileBulletType（extends BasicBulletType）

> 官方页无额外字段（仅继承 BasicBulletType，无新字段列出）。

### 17.12 Weather 天气

#### Weather（extends UnlockableContent）

| field | type | default | notes |
|---|---|---|---|
| duration | float | 36000.0 | Default duration in ticks. |
| opacityMultiplier | float | 1.0 | |
| attrs | Attributes | new Attributes() | |
| sound | Sound | none | |
| soundVol | float | 0.1 | |
| soundVolMin | float | 0.0 | |
| soundVolOscMag | float | 0.0 | |
| soundVolOscScl | float | 20.0 | |
| hidden | boolean | false | |
| type | Prov of WeatherState | WeatherState::create | |
| status | StatusEffect | none | |
| statusDuration | float | 120.0 | |
| statusAir | boolean | true | |
| statusGround | boolean | true | |

#### ParticleWeather（extends Weather）

| field | type | default | notes |
|---|---|---|---|
| particleRegion | String | "circle-shadow" | |
| color | Color | ffffffff | |
| yspeed | float | -2.0 | |
| xspeed | float | 0.25 | |
| padding | float | 16.0 | |
| sizeMin | float | 2.4 | |
| sizeMax | float | 12.0 | |
| density | float | 1200.0 | |
| minAlpha | float | 1.0 | |
| maxAlpha | float | 1.0 | |
| force | float | 0.0 | |
| noiseScale | float | 2000.0 | |
| baseSpeed | float | 6.1 | |
| sinSclMin | float | 30.0 | |
| sinSclMax | float | 80.0 | |
| sinMagMin | float | 1.0 | |
| sinMagMax | float | 7.0 | |
| noiseColor | Color | ffffffff | |
| drawNoise | boolean | false | |
| drawParticles | boolean | true | |
| useWindVector | boolean | false | |
| randomParticleRotation | boolean | false | |
| noiseLayers | int | 1 | |
| noiseLayerSpeedM | float | 1.1 | |
| noiseLayerAlphaM | float | 0.8 | |
| noiseLayerSclM | float | 0.99 | |
| noiseLayerColorM | float | 1.0 | |
| noisePath | String | "noiseAlpha" | |

### 17.13 Logic 逻辑方块

#### LogicBlock（extends Block）

| field | type | default | notes |
|---|---|---|---|
| maxInstructionScale | int | 5 | |
| instructionsPerTick | int | 1 | |
| maxInstructionsPerTick | int | 40 | |
| range | float | 80.0 | |

### 17.14 TargetDummy

#### TargetDummy（extends Block）

| field | type | default | notes |
|---|---|---|---|
| dpsUpdateTime | int | 1 | |
| unitType | UnitType | dummy | |
| pullScale | float | 0.33 | |
| emptyStr | String | "---" | |

---

## 18. 三条路线对照表

> v2 将 v1 的"两条路线"扩充为"三条路线"：HJSON 内容 mod / JS 脚本 / Java 插件。

### 18.1 路线概述

| 维度 | HJSON 内容 mod | JS 脚本 mod | Java/JVM 插件 mod |
|------|---------------|-------------|-------------------|
| **内容定义方式** | HJSON/JSON 数据文件，声明式 | Rhino JS，`extend()`/`Item()` + `require()` | Java 类，继承 `mindustry.mod.Mod`，重写方法 |
| **脚本能力** | 无（纯数据） | 现代 JS API：`extend()`、`Events.on()`、`BaseDialog`、`loadSound()` | 完整 Java，可访问全部游戏类 |
| **学习曲线** | 低——懂 HJSON 就能加内容 | 中——需懂 JS + 游戏 API 概念 | 高——需懂 Java + 游戏源码结构 |
| **性能** | HJSON 解析有开销 | JS 解释执行，有开销 | 原生编译执行，性能最佳 |
| **分发** | 文件夹/zip，游戏内直接加载 | 文件夹/zip，游戏内直接加载 | 需编译为 jar，用 GitHub Releases 分发（只取最新 release） |
| **跨平台** | 天然跨平台 | 天然跨平台 | 天然跨平台（JVM），桌面/Android |
| **主类/入口** | 无（`content/` 自动加载） | `scripts/main.js` | `mod.hjson` 的 `main:` 指定全限定类 |
| **服务器端** | 客户端/服务器同内容 | 客户端/服务器同内容 | Plugin 可仅服务器端（继承 `mindustry.mod.Plugin`，隐式 hidden） |
| **安全沙箱** | 无风险（纯数据） | 有限 | **无沙箱**——可完全访问客户端电脑，勿从不信任来源导入 |

### 18.2 各自能力边界

**HJSON 内容 mod 能做的：**
- 添加新物品、液体、方块（炮塔、传送带、发电机等已有类型的变体）
- 修改已有方块的参数（伤害、射程、射速、消耗等）
- 添加新贴图、音效、翻译
- 添加战役 Planet 内容、蓝图

**HJSON 内容 mod 做不到的：**
- 全新的方块交互逻辑（全新合成机制、全新游戏模式）
- 修改游戏核心系统

**JS 脚本 mod 额外能做的：**
- 监听事件（`Events.on(UnitDestroyEvent, ...)`）
- 用 `extend()` 扩展已有方块/单位类型，重写少量方法
- 显示对话框（`BaseDialog`）
- 播放自定义音效
- 自定义特效（`newEffect`）

**Java/JVM 插件 mod 能做的：**
- 以上所有
- 全新的方块类型、单位 AI、游戏机制
- 修改/扩展核心游戏系统（渲染、网络、UI）
- 完整的自定义渲染、网络同步
- 服务器端命令/游戏模式（Plugin）
- 任何 Java 能做到的事（含反射、字节码修改——但有安全风险）

### 18.3 何时用哪个

| 场景 | 推荐路线 |
|------|----------|
| 只想加几个新炮塔/传送带/物品 | HJSON |
| 想调数值、换贴图、加音效 | HJSON |
| 想做简单自定义行为（事件监听、对话框、点击触发效果） | JS 脚本 |
| 想做全新游戏机制/模式 | Java 插件 |
| 想深度改造游戏核心 | Java 插件 |
| 服务器端加命令/游戏模式（客户端无需下载） | Java Plugin |
| 追求极致性能 | Java 插件 |

### 18.4 核心结论

三条路线是**递进关系**：

1. **HJSON** 是数据驱动的内容扩展——不写一行代码就能大量扩展游戏内容，适合快速原型和中小型 mod。
2. **JS 脚本**在 HJSON 之上叠加了事件和少量行为扩展——能做简单交互，但受限于白名单 API。
3. **Java 插件**是代码驱动的深度改造——能做任何事，但门槛最高、需编译分发、且无沙箱（信任风险）。

推荐工作流：先用 HJSON 快速验证内容想法 → 需要简单交互时加 JS → 需要深度定制时转 Java。

---

> 本文档由官方 Wiki 抓取整理而成。代码块与字段表忠实保留官方原文（英文），中文讲解围绕其组织。
> 抓取来源：9 个 Wiki 页面（1-modding / 2-plugins / 3-scripting / 4-spriting / 5-markup / 5-types / 6-migrationv6 / 7-migrationv7 / 8-migrationv8）+ 47 个 Modding Classes 字段页。
> 缺口：`Turret` 基类官方页为空（字段见 Java 源码 `mindustry.world.blocks.defense.turrets.Turret`）；`Planet` 官方链接失效；Spriting 页面的图像示例（调色板、各阶段画法图）未收录为文本。

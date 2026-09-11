# Mindustry Java 模组开发循序渐进教程

> **适用游戏版本：Mindustry v159.7**
> **读者对象：** 有 Java 基础（懂类、继承、匿名内部类、Gradle 概念），但从未写过 Mindustry 模组的读者。
> **阅读方式：** 四章循序渐进。第一章搭项目，第二章仿官方模版造内容，第三章继承改写一个"每次最多受 1 点伤害的墙"，第四章带源码框架全局观。每段代码都标注了对应版本 `v159.7`，且完整可复制。
>
> 本教程所有 API 结论均来自 v159.7 源码逐条核验，关键处标注了源码文件与行号（如 `BuildingComp.java:2066`），方便你打开本地源码自行对照。

---

## 目录

- [第一章：创建 Java 模组项目](#第一章创建-java-模组项目)
- [第二章：按模版创建游戏内容](#第二章按模版创建游戏内容)
- [第三章：自定义特殊 Java 内容——最大受伤为 1 的墙体](#第三章自定义特殊-java-内容最大受伤为-1-的墙体)
- [第四章：游戏内容基本逻辑——深入源码框架](#第四章游戏内容基本逻辑深入源码框架)
- [附录：快速参考卡（Cheat Sheet）](#附录快速参考卡cheat-sheet)
- [附录 B：Block ↔ Building 绑定机制](#附录-bblock--building-绑定机制)

---

# 第一章：创建 Java 模组项目

**目标：** 从零搭建一个可编译、可加载进游戏的 Java 模组项目。

## 1.1 前置准备

在动手前，请准备好以下工具：

| 工具 | 版本要求 | 为什么需要 |
|------|----------|------------|
| **JDK** | 17 或以上 | Mindustry v159.7 使用 Java 17 编译。低于 17 会直接编译失败。 |
| **Gradle** | 7.5+（或使用项目自带 wrapper） | 用来编译源码、打包成 jar。 |
| **IntelliJ IDEA**（推荐） | Community 版即可 | 官方推荐 IDE。内置 Gradle 支持，能直接点进 Mindustry 源码跳转。 |
| **Mindustry 游戏本体** | v159.7 | 用来测试模组。 |

**验证 JDK：**

```bash
java -version
# 期望输出类似：openjdk version "17.x.x"
```

> 为什么是 JDK 17？Mindustry v159.7 的 `build.gradle` 里写死了 `sourceCompatibility = 17`。我们写模组时也要保持一致，否则加载类时会报 `UnsupportedClassVersionError`。

Gradle 安装后，进入项目目录执行 `gradle wrapper --gradle-version 8.x` 即可生成 `gradlew` 脚本；之后一律用 `./gradlew`（Windows 用 `gradlew.bat`），不必依赖系统全局 Gradle 版本。

## 1.2 项目结构详解

一个最小可运行的 Java 模组项目长这样：

```text
example-mod/
├── settings.gradle          # Gradle 项目名（单模块项目，通常只写一行）
├── build.gradle            # 核心构建脚本：依赖、编译、打包
├── gradle.properties       # Gradle JVM 参数（内存、缓存）
├── mod.json                # 模组元信息（游戏靠它识别这是个模组）
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── mod/
│       │               ├── ExampleModMain.java        # 模组主类（入口）
│       │               ├── content/
│       │               │   ├── ModItems.java         # 注册物品
│       │               │   └── ModBlocks.java         # 注册方块
│       │               └── blocks/
│       │                   └── MaxOneDamageWall.java # 自定义方块逻辑
│       └── resources/
│           └── README.txt                     # 资源占位（sprites 放这里）
└── build/                  # 构建产物目录（自动生成）
    └── libs/
        └── example-java-mod-1.0.0.jar       # ← 最终放进游戏 mods 文件夹的就是它
```

**每个部分的作用：**

- `settings.gradle`：定义 Gradle 项目名。最终 jar 的文件名由它决定。
- `build.gradle`：告诉 Gradle 怎么编译、依赖哪些库、怎么把 `mod.json` 打进 jar。
- `gradle.properties`：给 Gradle 本身调内存。模组项目小，2G 足够。
- `mod.json`：游戏识别模组的"身份证"。没有它，游戏不知道这文件夹是模组。
- `src/main/java/...`：你的 Java 源码。包名建议反向域名式（如 `com.example.mod`），避免和别人的模组撞名。
- `src/main/resources/`：资源文件目录。**贴图**（`sprites/blocks/xxx.png`、`sprites/items/xxx.png`）就放在这里，最终会一起进 jar。
- `build/libs/*.jar`：构建产物，拷进游戏即可。

## 1.3 build.gradle 配置（逐行注释）

下面是完整可用的 `build.gradle`，对应 **v159.7**：

```groovy
// build.gradle  （v159.7）

// 应用 Gradle 的 java 插件：获得 compile / jar / test 等标准任务
plugins {
    id 'java'
}

// Mindustry v159.7 使用 Java 17 编译
sourceCompatibility = 17
targetCompatibility = 17

// 依赖仓库：
//  - mavenCentral：JDK / 通用库
//  - jitpack：Mindustry 和 Arc 都托管在 jitpack（com.github.Anuken.*）
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Mindustry 核心库（仅编译时需要，运行时由游戏本身提供）
    compileOnly "com.github.Anuken.Mindustry:core:v159.7"
    // Arc 引擎核心库（Mindustry 的底层框架）
    compileOnly "com.github.Anuken.Arc:arc-core:889dd8880f"
}

// 把 src/main/resources 纳入资源源目录（贴图等会自动打进 jar）
sourceSets {
    main {
        resources {
            srcDirs = ['src/main/resources']
        }
    }
}

// 配置 jar 任务：
jar {
    manifest {
        attributes(
            // 告诉游戏的类加载器用 ModClassLoader 来加载这个 jar
            'Main-Class': 'mindustry.mod.ModClassLoader'
        )
    }
    // 把项目根目录的 mod.json 复制到 jar 根目录（游戏启动时会去根目录找它）
    from('mod.json') {
        into ''
    }
}

// 构建产物输出目录（默认就是 build，这里显式写出）
buildDir = 'build'
```

**关键概念解释：**

1. **为什么依赖是 `compileOnly` 而不是 `implementation`？**
   因为 Mindustry、Arc 这些库在**游戏运行时已经存在**。模组 jar 里**绝对不能**再把它们打进去——否则游戏会检测到"模组把游戏本体代码又带了一份"并拒绝加载（源码 `Mods.java:1175-1182` 有这段校验）。`compileOnly` 的意思是："只在我写代码、编译时让它存在，别打进产物。"

2. **为什么仓库里要有 jitpack？**
   `com.github.Anuken.Mindustry:core:v159.7` 这种坐标是 jitpack 的格式——它把 GitHub 仓库 `Anuken/Mindustry` 的某个 tag 自动构建成 Maven 依赖。mavenCentral 上没有它。

3. **`jar` 任务为什么要 `from('mod.json')`？**
   游戏加载模组 jar 时，会去 jar 的**根目录**找 `mod.json`。如果不把它拷进去，jar 里只有 `.class` 文件，游戏不知道模组叫什么、主类是哪个。

配套的 `settings.gradle` 和 `gradle.properties` 也很简单：

```groovy
// settings.gradle  （v159.7）
rootProject.name = 'example-java-mod'
```

```properties
# gradle.properties  （v159.7）
# Gradle JVM 参数
org.gradle.jvmargs=-Xmx2G
# 构建缓存
org.gradle.caching=true
```

## 1.4 mod.json 配置

`mod.json` 是模组的元信息清单，游戏靠它决定怎么加载你。完整示例（**v159.7**）：

```json
{
  "name": "Example Java Mod",
  "displayName": "示例 Java 模组",
  "author": "Tutorial Author",
  "description": "一个用于学习 Mindustry Java 模组开发的示例模组",
  "version": "1.0.0",
  "main": "com.example.mod.ExampleModMain",
  "minGameVersion": "154",
  "java": true,
  "dependencies": []
}
```

**每个字段的含义：**

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | ✅ | 模组的**内部唯一标识**。游戏文件夹、内容名前缀都靠它。不要含空格或特殊字符更稳妥。 |
| `displayName` | 建议 | 游戏模组列表里显示的名字，可以用中文。 |
| `author` | 可选 | 作者名。 |
| `description` | 可选 | 模组简介。 |
| `version` | 可选 | 语义化版本号，如 `1.0.0`。 |
| `main` | ✅（Java 模组必填） | **主类的全限定名**。游戏会用 `Class.forName(main)` 找到它并实例化。必须继承 `mindustry.mod.Mod`。 |
| `minGameVersion` | 建议 | 支持的最低游戏版本。Java 模组最低填 **`"154"`**。 |
| `java` | Java 模组必填 | 设为 `true`，告诉游戏"这是 Java 模组，需要用类加载器加载 jar"。 |
| `dependencies` | 可选 | 依赖的其它模组 `name` 列表，如 `["another-mod"]`。 |

**为什么 `minGameVersion` 要填 154？**
因为 Java 模组的 API 是在游戏 **v154** 才正式引入的。源码 `Vars.java:55` 写死了：

```java
// Vars.java:53 / 55
public static final int minModGameVersion = 136;       // JSON 模组最低版本
public static final int minJavaModGameVersion = 154;   // Java 模组最低版本
```

低于 154 的游戏版本根本不认识 Java 模组，会直接报"版本过低"。本教程基于 v159.7，所以填 154 既安全又兼容一片较老的版本。

> **关于 `main` 的默认推断：** 如果你不写 `main`，游戏会按规则 `<小写name>.<大驼峰Name>Mod` 去猜（`Mods.java:1103`）。但**强烈建议显式写 `main`**，避免名字改动后找不到类。

## 1.5 主类骨架

主类是游戏进入你模组的入口。完整代码（**v159.7**）：

```java
// src/main/java/com/example/mod/ExampleModMain.java  （v159.7）
package com.example.mod;

import arc.util.Log;
import mindustry.mod.Mod;
import com.example.mod.content.ModItems;
import com.example.mod.content.ModBlocks;

/**
 * 模组主类 —— Mindustry 模组的入口点。
 * 游戏通过 mod.json 中的 "main" 字段找到本类并实例化（无参构造）。
 * 本类必须继承 mindustry.mod.Mod。
 */
public class ExampleModMain extends Mod {

    /**
     * 调用时机：游戏"创建内容"阶段，非常早。
     * 在这里 new 出你的物品、方块、单位等 Content。
     * 此时还不能访问贴图（TextureRegion），因为纹理更晚才加载。
     */
    @Override
    public void loadContent() {
        ModItems.load();   // 注册物品
        ModBlocks.load();  // 注册方块
        Log.info("ExampleJavaMod: 内容加载完成!");
    }

    /**
     * 调用时机：所有内容、模块、命令全部就绪之后（加载界面结束前）。
     * 适合做跨内容引用、注册事件监听、注册指令等。
     */
    @Override
    public void init() {
        Log.info("ExampleJavaMod: 模组初始化完成!");
    }
}
```

**`loadContent()` 与 `init()` 的区别（重要）：**

源码在 `Mod.java:20 / 25`：

```java
// Mod.java:20  Called after all plugins have been created and commands have been registered.
public void init(){ }

// Mod.java:25  Called on clientside mods. Load content here.
public void loadContent(){ }
```

| 方法 | 调用阶段 | 适合做什么 | 不能做什么 |
|------|----------|------------|------------|
| `loadContent()` | 内容创建阶段（`ContentLoader.createModContent()`） | `new Item(...)` / `new Block(...)` 注册内容 | 访问贴图 region、做 UI |
| `init()` | 资源全部就绪后（`ClientLauncher` 里 `mods.eachClass(Mod::init)`） | 跨内容引用、事件监听、注册指令 | —— |

一句话记忆：**`loadContent` 造内容，`init` 装逻辑。**

## 1.6 构建与测试

**构建：**

```bash
cd example-mod
./gradlew jar        # Windows: gradlew.bat jar
```

构建成功后，产物在：

```text
build/libs/example-java-mod-1.0.0.jar
```

**安装到游戏：**

把这个 jar 复制到 Mindustry 的 `mods` 文件夹。各系统路径：

- Windows：`%AppData%\Mindustry\mods\`
- Linux：`~/.local/share/Mindustry/mods/`
- macOS：`~/Library/Application Support/Mindustry/mods/`

也可以在游戏内：**设置 → 打开模组文件夹（Open Mods Folder）**。

**确认加载成功：**

1. 启动游戏，进 **模组（Mods）** 菜单，应能看到"示例 Java 模组"。
2. 进入一局游戏（创造模式）。
3. 游戏控制台（按开放控制台的快捷键，或日志里）应打印：
   ```text
   ExampleJavaMod: 内容加载完成!
   ExampleJavaMod: 模组初始化完成!
   ```
4. 如果方块/物品贴图缺失，游戏会显示成**紫黑相间的"缺贴图"方块**——这是正常的，说明代码加载成功，只是你还没放 `sprites/...png`。

> **贴图放哪？** 物品贴图放 `src/main/resources/sprites/items/example-item.png`；方块贴图放 `src/main/resources/sprites/blocks/example-wall.png`。文件名必须和 `new Item("example-item")` / `new Wall("example-wall")` 里的名字对应。没有贴图也能跑，只是难看。

## 1.7 常见问题

**Q1：`Could not resolve com.github.Anuken.Mindustry:core:v159.7`（依赖下载失败）**

- 检查是否加了 jitpack 仓库（`maven { url 'https://jitpack.io' }`）。
- jitpack 首次构建该 tag 可能较慢，等几分钟重试，或 `./gradlew --refresh-dependencies jar`。
- 网络问题：jitpack 在国外，必要时配置代理。

**Q2：游戏启动后提示 "requires game version ..." / 版本不兼容**

- 检查 `mod.json` 的 `minGameVersion` 是否合理；你编译用的 `core:v159.7` 必须 **小于等于** 你玩的游戏版本。
- 如果你玩的是 v146，而 mod 是按 v159.7 API 写的，就会报不兼容。

**Q3：`ClassNotFoundException: com.example.mod.ExampleModMain` / 类找不到**

- 检查 `mod.json` 的 `main` 是否写了**全限定名**（包名 + 类名）。
- 检查 jar 根目录里是否真的有 `mod.json` 和 `.class` 文件（用解压软件打开 jar 看一眼）。
- 检查包名和文件夹层级是否一致（`com/example/mod/ExampleModMain.class`）。

**Q4：游戏拒绝加载，提示 "Mindustry classes found in mod jar"**

- 你误把 Mindustry/Arc 打进了 jar。把 `build.gradle` 里依赖改成 `compileOnly`，重新 `./gradlew clean jar`。

---

# 第二章：按模版创建游戏内容

**目标：** 模仿官方 `Blocks.java` / `Items.java` 的写法，创建一个物品和一个墙。

## 2.1 Content 注册机制原理（为什么不用手动注册）

你可能习惯了"new 出来 → 调个 register()"。但 Mindustry **反过来了**：你 `new` 的瞬间，它就自动注册好了。

看源码 `Content.java:20`：

```java
// Content.java:20
public Content(){
    this.id = (short)Vars.content.getBy(getContentType()).size;
    Vars.content.handleContent(this);   // 构造函数里就完成注册
}
```

意思是：**所有 Content 的基类构造函数**会做两件事：
1. 给你分配一个 `id`（按当前已有数量）；
2. 调 `Vars.content.handleContent(this)` 把自己塞进内容表。

而 `handleContent` 在 `ContentLoader.java:175`：

```java
// ContentLoader.java:175
public void handleContent(Content content){
    this.lastAdded = content;
    contentMap[content.getContentType().ordinal()].add(content);
}
```

它按 `ContentType` 分桶存放。所以你只要在 `loadContent()` 里 `new MyBlock("xxx")`，它就自动进桶了。**不要**自己写 `register()`——没有这个方法，写了反而错。

**ContentType 枚举的所有值**（`ContentType.java`，注意顺序不能动）：

```java
// ContentType.java:11-28
public enum ContentType{
    item("items", Item.class),
    block("blocks", Block.class),
    mech_UNUSED,
    bullet("bullets", BulletType.class),
    liquid("liquids", Liquid.class),
    status("statuses", StatusEffect.class),
    unit("units", UnitType.class),
    weather("weather", Weather.class),
    effect_UNUSED,
    sector("sectors", SectorPreset.class),
    loadout_UNUSED,
    typeid_UNUSED,
    error,
    planet("planets", Planet.class),
    ammo_UNUSED(),
    team("teams", TeamEntry.class),
    unitCommand("unitCommands", UnitCommand.class),
    unitStance("unitStances", UnitStance.class);
}
```

实际可用的类型是：`item, block, bullet, liquid, status, unit, weather, sector, planet, team, unitCommand, unitStance`。那些 `*_UNUSED` 是历史占位，不要碰。

## 2.2 创建物品

**Item 类构造函数**（`Item.java:51 / 56`）：

```java
// Item.java:51
public Item(String name, Color color){
    super(name);
    this.color = color;
}
// Item.java:56
public Item(String name){
    this(name, new Color(Color.black));
}
```

**关键字段**（都在 `Item.java`）：

| 字段 | 含义 |
|------|------|
| `color` | 物品颜色（图标、传送带渲染用） |
| `cost` | 建造成本权重，`1 cost = 1 tick` 建造时间（`Item.java:35`） |
| `flammability` | 可燃度，>0.3 才能当燃料（`Item.java:24`） |
| `explosiveness` | 爆炸倾向 |
| `radioactivity` | 放射性 |
| `hardness` | 钻头开采所需等级（`Item.java:30`） |
| `buildable` | false 时不能当建材（如 coal、sand） |

**完整示例 `ModItems.java`（v159.7）：**

```java
// src/main/java/com/example/mod/content/ModItems.java  （v159.7）
package com.example.mod.content;

import arc.graphics.Color;
import mindustry.type.Item;

/**
 * 注册所有自定义物品。
 * Content 构造函数会自动注册，无需手动 register。
 * 只需声明 public static 字段并在 load() 里 new 出来。
 */
public class ModItems {

    /** 示例物品：一颗闪亮的蓝色晶体 */
    public static Item exampleItem;

    public static void load() {
        // new 的瞬间就自动注册了。名字 "example-item" 对应 sprites/items/example-item.png
        exampleItem = new Item("example-item", Color.valueOf("4fc3f7"));
        // cost 是建造权重（默认 1.0）
        exampleItem.cost = 1.0f;
    }
}
```

**官方对照（有样学样）：** 这正是官方 `Items.java` 的写法。看 `Items.java:16` 的铜：

```java
// Items.java:16  copper
copper = new Item("copper", Color.valueOf("d99d73")){{
    hardness = 1;
    cost = 0.5f;
    alwaysUnlocked = true;
}};
```

官方多用**双花括号初始化** `{{ ... }}`（匿名子类 + 实例初始化块）来一口气设字段，效果和我们分行设字段完全一样。

**JSON 方式 vs Java 方式对比：**

| | Java 方式 | JSON 方式 |
|---|---|---|
| 写法 | `new Item("x", color){{ cost=1; }}` | 在 `content/items/x.json` 写 HJSON |
| 能力 | 全部 API、可写逻辑、可继承 | 只能设字段，写不了复杂行为 |
| 适用 | 复杂方块、自定义 Build、特殊逻辑 | 简单物品/方块的快速增删 |
| 加载顺序 | **先**（`loadContent()` 阶段） | 后（见 2.5） |

> 本教程走 Java 路线，因为你要做的是"有逻辑的特殊方块"，JSON 做不到。

## 2.3 创建方块（墙）

**Wall 类的默认属性。** 墙直接继承 `Wall` 就能获得一整套合理默认值。看 `Wall.java` 构造函数：

```java
// Wall.java:35  Wall 构造函数
public Wall(String name){
    super(name);
    solid = true;          // 实心，单位不能穿过
    destructible = true;   // 可被摧毁
    group = BlockGroup.walls;
    buildCostMultiplier = 6f;
    canOverdrive = false;
    crushDamageMultiplier = 5f;
    priority = TargetPriority.wall;
    envEnabled = Env.any;  // 任何星球环境都能放
}
```

**有样学样——铜墙怎么写的？** 看 `Blocks.java:1708`：

```java
// Blocks.java:1708  copper-wall
copperWall = new Wall("copper-wall"){{
    requirements(Category.defense, with(Items.copper, 6));
    health = 80 * wallHealthMultiplier;
    researchCostMultiplier = 0.1f;
}};
```

**双花括号 `{{ ... }}` 是什么？**
外层 `new Wall("...")` 创建对象；内层一对 `{}` 是**匿名子类的实例初始化块**——在构造函数体之后自动执行。所以 `{{ requirements(...); health = ...; }}` 等价于"new 完立刻在对象上调这些方法/设这些字段"。官方内容几乎全用这个语法，简洁。

**`requirements()` 的三个重载**（`Block.java:1223 / 1228 / 1233`）：

```java
// Block.java:1223
public void requirements(Category cat, ItemStack[] stacks, boolean unlocked)
// Block.java:1228
public void requirements(Category cat, ItemStack[] stacks)
// Block.java:1233  ← 注释明确："Use only this method to set up requirements."
public void requirements(Category cat, BuildVisibility visible, ItemStack[] stacks)
```

建造需求用静态辅助 `ItemStack.with(...)` 构造（`ItemStack.java:47`）：

```java
// ItemStack.java:47
public static ItemStack[] with(Object... items){ ... }
```

用法：`with(Items.copper, 6)` 表示"铜 ×6"；`with(ModItems.exampleItem, 2, Items.silicon, 10)` 表示两种材料。

**Category 枚举全部 10 个值**（`Category.java`，**以源码为准**）：

```java
// Category.java
public enum Category{
    turret,        // 炮塔
    production,    // 生产（钻头等）
    distribution,  // 运输
    liquid,        // 液体
    power,         // 电力
    defense,       // 防御（墙、防御设施）
    crafting,      // 制造
    units,         // 单位建造
    effect,        // 储存/效果
    logic;         // 逻辑
}
```

**完整示例 `ModBlocks.java`（v159.7）——创建一个普通墙：**

```java
// src/main/java/com/example/mod/content/ModBlocks.java  （v159.7）
package com.example.mod.content;

import mindustry.world.blocks.defense.Wall;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.defense;

/**
 * 注册所有自定义方块。
 * Block 子类在构造时自动注册。
 */
public class ModBlocks {

    /** 普通示例墙 —— 直接用 Wall 类，模仿 copper-wall 的写法 */
    public static Wall exampleWall;

    public static void load() {
        // 1) 普通墙：直接 new Wall("example-wall")，名字对应 sprites/blocks/example-wall.png
        exampleWall = new Wall("example-wall");
        // 2) 设建造需求：exampleItem × 2，分类归到 defense（防御）标签页
        exampleWall.requirements(defense, with(ModItems.exampleItem, 2));
        // 3) 设血量（铜墙 1x1 是 80*multiplier，这里给个 200）
        exampleWall.health = 200;
    }
}
```

**为什么要先 `load()` 物品再 `load()` 方块？**
因为上面 `requirements(..., with(ModItems.exampleItem, 2))` 引用了 `ModItems.exampleItem`。如果物品还没 new，这个引用就是 null。所以 `ExampleModMain.loadContent()` 里必须先 `ModItems.load()` 再 `ModBlocks.load()`。

## 2.4 创建方块（功能性）：每 tick 产出物品的方块

光会做墙还不够。下面给一个"每过一段时间产出一个 exampleItem"的方块，演示**带 update 行为的方块**怎么写。关键是：**Block 类里声明一个继承 Building 的内部类**，覆写 `updateTile()`。

```java
// src/main/java/com/example/mod/blocks/CrystalProducer.java  （v159.7）
package com.example.mod.blocks;

import mindustry.world.Block;
import mindustry.world.building.*;            // 引入 Building（生成类 mindustry.gen.Building）

/**
 * 一个会自动产出 exampleItem 的功能性方块示例。
 * 重点看：① 构造函数里把 update=true；② 内部类 Build 覆写 updateTile()。
 */
public class CrystalProducer extends Block {

    public CrystalProducer(String name) {
        super(name);
        update = true;          // 关键：每 tick 调用 Build.updateTile()
        solid = true;           // 实心
        hasItems = true;        // 这个方块有物品栏
        itemCapacity = 20;      // 物品栏容量
        emitLiquid = false;
    }

    /** 非静态内部类：每放置一个方块就生成一个这样的实体 */
    public class CrystalProducerBuild extends Building {

        private float timer = 0f;

        @Override
        public void updateTile() {
            // 每 60 tick（1 秒）产出一个 exampleItem（如果格子没满）
            timer += edelta();                 // edelta() 是平滑后的时间增量
            if(timer >= 60f) {
                timer = 0f;
                if(items.get(ModItems.exampleItem) < itemCapacity) {
                    items.add(ModItems.exampleItem, 1);
                }
            }
        }
    }
}
```

> **说明：** `edelta()`、`items`、`itemCapacity` 都是 `BuildingComp`（生成出的 `Building`）上的成员，由引擎注入。这个示例主要是让你看清"Block 是模版、Build 是实例、逻辑写在 Build 里"的结构。第三章会把这个结构讲透。

## 2.5 内容加载顺序

很多人卡在"为什么我的方块引用了别的内容却报错"。看源码 `Mods.java:835` 的 `loadContent()`：

```java
// Mods.java:835 （简化）
public void loadContent(){
    // 第一步：先跑所有模组的 Java loadContent()
    for(LoadedMod mod : orderedMods()){
        if(mod.main != null && !mod.meta.hidden){
            content.setCurrentMod(mod);
            mod.main.loadContent();          // ← 你的 new Item/new Block 在这里
        }
    }
    // 第二步：扫描 content/*.json，按 contentOrder 排序后逐个解析
}
```

**结论：**
1. **Java 的 `loadContent()` 全部先跑完**，之后才加载 JSON 内容文件。
2. 因此 **JSON 可以引用 Java 已注册的内容**，但反过来不行（JSON 里引用一个还没 new 的 Java 方块会找不到）。
3. 同模组内的 Java 内容，按你 `load()` 调用的顺序注册——所以**被依赖的内容先 load**。

**`contentOrder` 字段的作用**（`Mods.java:1413`）：

```java
/** If set, load the mod content in this order by content names. */
public String[] contentOrder;
```

它只排序 **JSON 文件之间**的加载顺序。如果两个 JSON 内容有先后依赖，在 `mod.json` 里写：

```json
"contentOrder": ["base-liquid", "advanced-block"]
```

没排到的 JSON 按字母序排后面。Java 内容不受它影响。

## 2.6 在游戏中验证

1. 重新 `./gradlew jar`，把新 jar 拷进 `mods` 覆盖旧的。
2. 进**创造模式**，打开建造菜单。
3. 在 **防御（defense）** 分类下，能看到 `example-wall`。
4. 放下去，点它查看血量信息（需要 `requirements` 正常才会出现在菜单）。
5. 如果在菜单里**找不到**方块：
   - 检查 `requirements(...)` 是否被调用了（没调 `requirements` 的方块默认 `buildVisibility = hidden`，会藏起来）。
   - 检查 `Category` 是否填了。
   - 看控制台有没有报错（名字拼写、贴图缺失一般不影响出现）。

---

# 第三章：自定义特殊 Java 内容——最大受伤为 1 的墙体

**目标：** 做出一个"无论被多强的子弹打中，每次只掉 1 点血"的墙。这一章是本教程的核心，带你真正读懂 Building 实体。

## 3.1 Block 与 Building 的关系

这是 Mindustry 模组开发最容易绕晕的一点，务必先建立心智模型：

| | Block | Building |
|---|---|---|
| 性质 | **类型 / 模版**（单例） | **实例 / 实体**（每放一个方块一个对象） |
| 数量 | 全游戏一个 | 每个被放置的方块一个 |
| 存什么 | 名字、血量、尺寸、建造需求、贴图 | 当前血量、所在 tile、方向、物品、电力状态 |
| 类比 | 类（Class） | 对象（Object） |

**`mindustry.gen.Building` 是怎么来的？**
你在源码里找不到 `mindustry/gen/Building.java`——因为它是**注解处理器在编译期生成**的。真正的手写源码是 `mindustry/entities/comp/BuildingComp.java`（一个抽象组件类）：

```java
// BuildingComp.java:52-54
@EntityDef(value = {Buildingc.class}, excludeGroups = {"all"}, isFinal = false, genio = false, serialize = false)
@Component(base = true, genInterface = false)
abstract class BuildingComp implements Posc, Teamc, Healthc, Buildingc, Timerc, ... {
```

构建时，Arc 的实体注解处理器把 `BuildingComp` 和一堆 `@Import` 字段（`x, y, health, maxHealth` 等）合并，生成出具体的 `mindustry.gen.Building`。**你写模组时直接用 `mindustry.gen.Building` 这个类名即可**（它在编译产物里存在）。

**Block 怎么知道用哪个 Build 类？**
看 `Block.java` 构造函数（`Block.java:444`）里调的 `initBuilding()`：它通过**反射扫描 Block 子类里继承 Building 的内部类**。找到第一个就用它 `new 你的Build(this)`；找不到就回退默认：

```java
// Block.java:1274
if(buildType == null){
    buildType = Building::create;
}
```

这就是为什么我们写自定义墙时，要把 Build 写成**非静态内部类**——这样它能被反射扫到，并且天然持有外部 Block 实例。

## 3.2 伤害系统源码分析

我们的目标是"每次最多掉 1 血"，就得先搞清楚伤害是怎么流进 Building 的。

**完整调用链：**

```text
子弹撞击方块
  → BuildingComp.collision(Bullet)          BuildingComp.java:1761
      → damage(other, other.team, damage)   （算护甲后）
          → damage(float)                   BuildingComp.java:2066
              → health -= handleDamage(damage)   BuildingComp.java:2080  ← 关键过滤器
              → healthChanged()
              → 若 health <= 0：Call.buildDestroyed()
```

**`damage(float)` 源码**（`BuildingComp.java:2066`）：

```java
// BuildingComp.java:2066
public void damage(float damage){
    if(dead()) return;
    float dm = state.rules.blockHealth(team);     // 地图规则：建筑血量倍率
    lastDamageTime = Time.time;
    if(Mathf.zero(dm)){
        damage = health + 1;                       // 倍率为 0 → 一击必杀
    }else{
        damage /= dm;
    }
    if(!net.client()){
        health -= handleDamage(damage);            // 第 2080 行：先过 handleDamage 再扣血
    }
    healthChanged();
    if(health <= 0){ /* 致死处理：Call.buildDestroyed() */ }
}
```

注意两个细节：
- `net.client()` 判断：**只有服务端**才真正扣血（联机时客户端不自己改血量，避免不同步）。所以我们覆写的方法在服务端跑。
- `state.rules.blockHealth(team)`：地图规则里的建筑血量倍率。

**`handleDamage(float)` 默认实现**（`BuildingComp.java:1743`）：

```java
// BuildingComp.java:1743
public float handleDamage(float amount){
    return amount;      // 默认原样返回：来多少扣多少
}
```

**这就是钩子！** 它默认把伤害原样放行。只要我们覆写它，让它"来 100 也只放行 1"，就能限制每次受伤量。它返回的值才是真正从 `health` 里减掉的数。

**`collision(Bullet)` 源码**（`BuildingComp.java:1761`）：

```java
// BuildingComp.java:1761
public boolean collision(Bullet other){
    boolean wasDead = health <= 0;
    BulletType t = other.type;

    float damage = other.type.buildingDamage(other);
    if(!t.pierceArmor){
        damage = Damage.applyArmor(damage, block.armor * t.armorMultiplier * t.blockArmorMultiplier);
    }
    damage(other, other.team, damage);      // 最终走 damage(...) → handleDamage(...)

    if(health <= 0 && !wasDead){
        Events.fire(new BuildingBulletDestroyEvent(self(), other));
    }
    return true;                            // true = 阻挡子弹
}
```

## 3.3 完整实现：MaxOneDamageWall

逐行讲解。完整代码（**v159.7**）：

```java
// src/main/java/com/example/mod/blocks/MaxOneDamageWall.java  （v159.7）
package com.example.mod.blocks;

import arc.graphics.Color;
import mindustry.entities.bullet.Bullet;
import mindustry.world.blocks.defense.Wall;

/**
 * 自定义墙：每次受到的伤害最多只有 1 点。
 */
public class MaxOneDamageWall extends Wall {

    // ① 构造函数：继承 Wall，复用它的全部默认（solid、destructible、walls 分组等）
    public MaxOneDamageWall(String name) {
        super(name);
        // 额外调一点属性：受击时闪烁，给玩家"我打中它了"的反馈
        this.flashHit = true;
        this.flashColor = Color.valueOf("#4fc3f7");
    }

    /**
     * ② 非静态内部类：这是"放在地图上的那个墙"的实体类。
     *    Wall.WallBuild 是 Wall 的内部类（Wall.java:87：public class WallBuild extends Building），
     *    它被我们继承下来。
     */
    public class MaxOneDamageWallBuild extends WallBuild {

        /**
         * ③ 覆写 handleDamage：这就是伤害过滤器（BuildingComp.java:1743）。
         *    传进来 amount 是原始伤害，返回值才是真扣的血。
         *    Math.min(amount, 1f)：不管多大伤害，最多放行 1。
         */
        @Override
        public float handleDamage(float amount) {
            return Math.min(amount, 1f);
        }

        /**
         * ④ 覆写 collision：子弹撞上来时（BuildingComp.java:1761）。
         *    调 super 保留父类行为（闪烁等），返回 true 表示挡住子弹。
         */
        @Override
        public boolean collision(Bullet bullet) {
            return super.collision(bullet);
        }
    }
}
```

**逐点解释：**

1. **构造函数 `super(name)`**：先让 `Wall` 把 `solid=true`、`destructible=true`、`group=BlockGroup.walls` 都设好。我们只补自己想要的（`flashHit`）。

2. **内部类语法 `public class MaxOneDamageWallBuild extends WallBuild`**：
   - `Wall.WallBuild` 是个**非静态内部类**（`Wall.java:87`：`public class WallBuild extends Building`），它隐式持有一个外部 `Wall` 的引用。
   - 我们的 `MaxOneDamageWall extends Wall` 后，`WallBuild` 这个内部类"继承"到了我们类里。所以我们能直接 `extends WallBuild`。
   - 因为它是非静态内部类，反射扫到它后会用 `new MaxOneDamageWallBuild(this)` 实例化（`this` 就是那个 Block 模版对象）。**千万不要加 `static`**，否则它不持有外部 Block，反射会失败。

3. **`handleDamage` 覆写**：`return Math.min(amount, 1f);` —— 这一行就是整个功能的核心。哪怕子弹伤害 999，最后也只扣 1。

4. **`collision` 覆写**：这里只演示钩子，调了 `super`。第三章 3.4 会改它做更有意思的事。

**在 `ModBlocks.java` 里注册这个墙：**

```java
// src/main/java/com/example/mod/content/ModBlocks.java  （v159.7）
package com.example.mod.content;

import mindustry.world.blocks.defense.Wall;
import com.example.mod.blocks.MaxOneDamageWall;

import static mindustry.type.ItemStack.with;
import static mindustry.world.meta.Category.defense;

public class ModBlocks {

    public static Wall exampleWall;
    /** 自定义墙：每次最多受 1 点伤害 */
    public static MaxOneDamageWall maxOneDamageWall;

    public static void load() {
        // 普通墙
        exampleWall = new Wall("example-wall");
        exampleWall.requirements(defense, with(ModItems.exampleItem, 2));
        exampleWall.health = 200;

        // 自定义墙
        maxOneDamageWall = new MaxOneDamageWall("max-one-damage-wall");
        maxOneDamageWall.requirements(defense, with(ModItems.exampleItem, 5));
        maxOneDamageWall.health = 1000;
    }
}
```

> 注意 import：`MaxOneDamageWall` 在 `com.example.mod.blocks` 包下，要单独 import。`ModItems` 在 `content` 包下，和 `ModBlocks` 同包，不用 import。

## 3.4 扩展思路（举一反三）

`handleDamage` 和 `collision` 是两个万能钩子，照着改就能变出很多花样：

**① 无敌墙**——伤害直接清零：

```java
@Override
public float handleDamage(float amount) {
    return 0f;          // 永远不掉血
}
```

**② 按比例减伤墙**——只受 50% 伤害：

```java
@Override
public float handleDamage(float amount) {
    return amount * 0.5f;
}
```

**③ 吸血墙**——挨打时回点血：

```java
@Override
public float handleDamage(float amount) {
    // 受到伤害的 10% 转化为回血（注意别让血超过 maxHealth）
    health = Math.min(maxHealth, health + amount * 0.1f);
    return amount;      // 正常扣血
}
```

**④ 反弹墙**——不让子弹穿过去，而是把它弹回去。这需要改 `collision`：

```java
@Override
public boolean collision(Bullet bullet) {
    // 自己处理反弹：反转子弹速度方向，不调 super（即不扣自己血、不挡住）
    bullet.vel.rotate(180f);
    bullet.team = team;              // 反弹给原敌方
    return false;                    // false = 不阻挡子弹继续飞
}
```

> 这些都是"覆写钩子 + 返回不同值"的思路。理解了 `handleDamage`（过滤器）和 `collision`（是否阻挡）的返回值语义，就能自由发挥。

## 3.5 调试技巧

**看伤害数值**：在 `handleDamage` 里打日志：

```java
@Override
public float handleDamage(float amount) {
    arc.util.Log.info("受到原始伤害: @, 实际扣: @", amount, Math.min(amount, 1f));
    return Math.min(amount, 1f);
}
```

游戏内按 **开放控制台**（默认 `/` 键，或在设置里开）即可看到 `Log.info` 的输出。

**测试方法：**
- 创造模式放下墙，用不同伤害的炮塔打它。
- 用 `/kill`、或者直接看墙的血条（创造模式选中方块可见血量）。
- 确认：普通墙被一炮打穿，而你的 `maxOneDamageWall` 血条掉得非常慢（每炮只掉 1）。

---

# 第四章：游戏内容基本逻辑——深入源码框架

**目标：** 建立对 Mindustry 内容框架的整体理解。读完这章，你应该知道"我想做个新东西，该从哪个类下手"。

## 4.1 内容体系总览

**ContentType 与基类的关系**（文字版关系图）：

```text
Content（基类，mindustry.ctype.Content）
├── item      → mindustry.type.Item          （物品）
├── block     → mindustry.world.Block         （方块，最大的类家族）
├── bullet    → mindustry.entities.bullet.BulletType （子弹）
├── liquid    → mindustry.type.Liquid        （液体）
├── status    → mindustry.type.StatusEffect   （状态效果）
├── unit      → mindustry.type.UnitType       （单位）
├── weather   → mindustry.entities.Weather    （天气）
├── sector    → mindustry.type.SectorPreset   （区块预设）
├── planet    → mindustry.game.Planet         （星球）
├── team      → mindustry.game.TeamEntry      （队伍）
├── unitCommand → mindustry.type.UnitCommand
└── unitStance  → mindustry.type.UnitStance
```

其中 **Block 是最庞大的子类家族**，按用途分子包：
- `world/blocks/distribution/`（传送带、护盾）
- `world/blocks/production/`（钻头、发电机）
- `world/blocks/defense/`（墙、炮塔）
- `world/blocks/power/`（发电、输电）
- `world/blocks/units/`（单位工厂）
- `world/blocks/logic/`（逻辑处理器）

**ContentLoader 的角色**（`ContentLoader.java`）：

```java
// ContentLoader.java:175  注册
public void handleContent(Content content){ ... }
// ContentLoader.java:261  取某类型全部
public <T extends Content> Seq<T> getBy(ContentType type){ ... }
// ContentLoader.java:222  遍历全部
public void each(Cons<Content> cons){ ... }
// ContentLoader.java:228  按名字取（常用）
public <T extends MappableContent> T getByName(ContentType type, String name){ ... }
```

你在代码里常用的查找方式：

```java
import static mindustry.Vars.content;

// 按名字取一个已注册的方块
Block wall = content.getByName(ContentType.block, "example-mod-example-wall");
// 官方物品更简单，直接引用静态字段
Item copper = Items.copper;   // 来自 mindustry.content.Items
```

## 4.2 建筑生命周期

**重点澄清**：`update()`、`placed()`、`onProximityUpdate()` 这些**不是 Block 的方法，而是 Building（建筑实体）的方法**。Block 只声明"类型级配置"（如 `update = true` 这个开关）。

完整生命周期（方法都在 `BuildingComp.java`）：

```text
placed()            方块被放下去（服务端）          BuildingComp.java:1361
  ↓
onProximityAdded()  首次创建 / 邻近方块变化时       BuildingComp.java:1152
  ↓
update()            每 tick（内部调 updateTile()）   BuildingComp.java:2270
  ↓
onProximityUpdate() 邻近 8 格有方块被放/拆时        BuildingComp.java:1159
  ↓
onDestroyed()       方块被摧毁（tile 还在）         BuildingComp.java:1490
  ↓
onRemoved()         方块被移除（tile 已空）          BuildingComp.java:1398
```

**各方法调用时机与用途：**

| 方法 | 时机 | 典型用途 |
|------|------|----------|
| `placed()` | 放置瞬间（服务端） | 把自己注册到电力网络 |
| `onProximityAdded()` | 创建后 / 邻近方块增加 | 更新电力图 |
| `onProximityRemoved()` | 邻近方块被拆 | 断开电力图 |
| `onProximityUpdate()` | 邻近任何方块变动 | 传送带连接、autotile 重算 |
| `updateTile()` | 每 tick（覆写这个，不是 update） | 生产、攻击、逻辑 |
| `onDestroyed()` | 被摧毁（tile 仍在） | 爆炸、残骸 |
| `afterDestroyed()` | tile 已移除后 | 清理引用 |
| `onRemoved()` | 被拆除/移走 | 释放资源 |

> **易错点：** 没有 `broken()` / `removed()` 这种名字。被摧毁叫 `onDestroyed()`，被移除叫 `onRemoved()`。

**序列化（存档）：** 用 Arc 的 `Writes` / `Reads`（**不是** JDK 的 `DataOutput/DataInput`）：

```java
// BuildingComp.java:303 / 308
@CallSuper
public void write(Writes write){ }

@CallSuper
public void read(Reads read, byte revision){ }
```

如果你在 Build 里存了自定义字段（如进度条），就覆写这两个方法把它写进存档，否则读档后进度丢失：

```java
@Override
public void write(Writes write){
    super.write(write);
    write.f(timer);     // 把自己的进度写进去
}

@Override
public void read(Reads read, byte revision){
    super.read(read, revision);
    timer = read.f();
}
```

## 4.3 事件系统

Mindustry 用 Arc 的全局事件总线 `arc.Events`。三种用法：

```java
import arc.Events;
import mindustry.game.EventType;

// ① 监听带数据的事件类（用 Cons<T> 回调）
Events.on(EventType.BlockBuildEndEvent.class, e -> {
    if(!e.breaking){
        // 有人（重新）建成了一个方块
    }
});

// ② 监听无参 Trigger 枚举（用 Runnable）
Events.run(Trigger.update, () -> {
    // 每帧执行
});

// ③ 触发事件（引擎内部用，模组一般只读不触发）
Events.fire(new EventType.UnitDestroyEvent(someUnit));
```

**`Events` 源码**（`Arc/arc-core/src/arc/Events.java`）：

```java
// Events.java:14  按事件类监听
public static <T> void on(Class<T> type, Cons<T> listener){ ... }
// Events.java:19  按 Trigger 枚举监听
public static void run(Object type, Runnable listener){ ... }
// Events.java:42  触发类事件
public static <T> void fire(T type){ ... }
```

**EventType 常用事件**（`EventType.java`，至少 10 个）：

| 事件类 | 携带信息 | 说明 |
|--------|----------|------|
| `BlockBuildEndEvent` | tile, team, unit, breaking, config | 方块建造/拆除结束 |
| `BlockBuildBeginEvent` | tile, team, unit, breaking | 方块开始建造/拆除 |
| `BlockDestroyEvent` | tile | 方块被摧毁 |
| `BuildDamageEvent` | build, damage | 建筑受击 |
| `UnitDestroyEvent` | unit | 单位死亡 |
| `WorldLoadEvent` | 无 | 世界加载完成 |
| `PlayEvent` | 无 | 开始游玩 |
| `GameOverEvent` | team | 游戏结束 |
| `WaveEvent` | 无 | 一波敌人到来 |
| `ClientLoadEvent` | 无 | 客户端加载完成 |
| `UnlockEvent` | content | 解锁了某内容 |
| `ResearchEvent` | node | 研究了某科技 |

**实际示例：在 `init()` 里注册事件监听器**（监听方块建造 + 单位死亡）：

```java
// ExampleModMain.java  （v159.7）
@Override
public void init() {
    // 监听：任何方块被建造完成
    Events.on(EventType.BlockBuildEndEvent.class, e -> {
        if(!e.breaking && e.tile != null){
            arc.util.Log.info("建造了方块: @", e.tile.block().localizedName);
        }
    });

    // 监听：任何单位死亡
    Events.on(EventType.UnitDestroyEvent.class, e -> {
        arc.util.Log.info("单位死亡: @", e.unit.type.localizedName);
    });
}
```

> 为什么在 `init()` 里注册，而不是 `loadContent()`？因为事件监听是"逻辑"，且此时内容已就绪。`loadContent()` 阶段太早。

## 4.4 方块分类与消费系统

**Category 10 个分类**（见 2.3，再列一次方便记忆）：
`turret, production, distribution, liquid, power, defense, crafting, units, effect, logic`

**consumers 系统：** 一个方块"要吃什么、产出什么"，由一组 `Consume` 对象描述。常用方法（`Block.java`）：

```java
// Block.java:1189  消耗物品
public ConsumeItems consumeItem(Item item, int amount)
// Block.java:1145  消耗液体
public ConsumeLiquid consumeLiquid(Liquid liquid, float amount)
// Block.java:1158  消耗电力（每秒 N 电力）
public ConsumePower consumePower(float powerPerTick)
// Block.java:1205  通用注册
public <T extends Consume> T consume(T consume)
```

**用法**（在 Block 的构造或双花括号里）：

```java
producer = new CrystalProducer("crystal-producer"){{
    requirements(Category.production, with(Items.copper, 20));
    consumePower(2f);                          // 每秒吃 2 电力
    consumeItem(Items.coal, 1);                // 每个周期吃 1 煤
}};
```

**update 循环如何工作：** 当你把 Block 的 `update = true` 后，引擎每 tick 调 `Building.update()`（`BuildingComp.java:2270`）：

```java
// BuildingComp.java:2270
public void update(){
    ...
    updateConsumption();              // 先检查/扣除所有 consumer
    if(enabled || !block.noUpdateDisabled){
        updateTile();                 // 再调子类覆写的 updateTile()
    }
}
```

也就是说：**你在 `updateTile()` 里写的产出逻辑，只有在消费（电力、物品）满足时才会真的跑**——消费系统自动帮你"卡关"。

## 4.5 图块（Tile）与世界

**Tile 是什么？** 世界地图是一个二维网格，每个格子是一个 `Tile`。一个 Tile 记录：坐标、地板、覆盖在上面的方块、环境（水/沙）等。

**世界坐标系：**
- `Vars.tilesize = 8`（`Vars.java:135`）——一个 tile 边长 8 像素。
- **tile 坐标**：格子索引（整数），如 `(5, 10)` 表示第 5 列第 10 行。
- **世界坐标**：像素坐标（浮点），tile `(x, y)` 的中心像素约是 `(x * 8 + 4, y * 8 + 4)`。
- Building 实体的 `tile` 字段指向它所在的 Tile；Building 的 `x/y` 是世界像素坐标。

**邻近方块获取**（`Tile.java:552`）：

```java
// Tile.java:552
public @Nullable Tile nearby(int dx, int dy){ ... }
```

在 Build 里这样用：

```java
// 拿东边相邻格（注意大尺寸方块要乘 size）
Tile east = tile.nearby(1, 0);
if(east != null && east.build != null){
    // 处理相邻方块
}
```

> 2x2 大方块邻近时要乘 `size`，就像官方 `WallBuild.updateAutotileBits()` 里写的：`tile.nearby(dx * size, dy * size)`。

## 4.6 科技树与研究

**两种解锁方式：**

**方式一（最简单）：** 直接给方块设 `researchCost`（`Block.java:386`），它会挂到默认科技树末尾。但官方科技树结构复杂，新手通常用下面这种让它"造得出就行"。

**方式二：** 在代码里用 `TechTree.node(...)` 把新方块串进科技树。签名（`TechTree.java`）：

```java
// TechTree.java:35
public static TechNode node(UnlockableContent content, ItemStack[] requirements, Runnable children)
```

它用一个静态游标 `context` + 嵌套闭包来表达树：

```java
// 在某个 load 阶段（如 init 后）
TechTree.node(ModBlocks.maxOneDamageWall,
    with(ModItems.exampleItem, 10),
    () -> {
        // 这个闭包里的 node 都是 maxOneDamageWall 的子节点
        TechTree.node(ModBlocks.exampleWall, with(ModItems.exampleItem, 5), () -> {});
    }
);
```

> 对新手教程而言，**最简单的做法是让方块"默认可造"**：在 `requirements` 时把 `buildVisibility` 设为 `shown`，或者直接在创造模式测试。正式发版时再用 `TechTree.node` 接进树。`alwaysUnlocked = true` 也能让方块无需研究直接解锁。

## 4.7 模组开发进阶资源

**本地源码怎么读（重点包）：**

1. 先读 `mindustry/content/Blocks.java`——这是官方所有方块的"字典"，照着 `copperWall`、`conveyor`、`mechanicalDrill` 仿写最快。
2. 再读 `mindustry/content/Items.java`、`UnitTypes.java`、`Bullets.java`。
3. 遇到行为问题，去 `mindustry/entities/comp/BuildingComp.java` 找钩子方法。
4. 想知道某字段干嘛的，直接在 `Block.java` / `Item.java` 里搜字段名，注释很全。

**官方与社区资源：**
- 游戏内 **Mod Browser**：看别人的模组怎么写。
- Mindustry GitHub Wiki 的 Modding 页面。
- 官方示例模板 `Anuken/MindustryModTemplate`。

**版本兼容注意事项：**
- `minGameVersion` 设为你 API 依赖的下限（Java 模组 154）。
- `legacyCompatible`：是否兼容老版本存档，一般默认即可。
- 每次游戏大版本更新，API 可能变；发版前对照 changelog 检查你覆写的方法签名。

---

# 附录：快速参考卡（Cheat Sheet）

## 项目骨架

```text
mod.json                      # name / main / minGameVersion:"154" / java:true
build.gradle                  # compileOnly core:v159.7 + arc-core
src/main/java/<你的包>/
  ├─ XxxModMain.java          # extends Mod
  ├─ content/ModItems.java
  ├─ content/ModBlocks.java
  └─ blocks/MyBlock.java
src/main/resources/sprites/   # items/ blocks/ 贴图
```

## 主类（v159.7）

```java
public class XxxModMain extends Mod {
    @Override public void loadContent(){ ModItems.load(); ModBlocks.load(); } // 造内容
    @Override public void init(){ /* 事件、指令 */ }                            // 装逻辑
}
```

## 建物品（v159.7）

```java
exampleItem = new Item("example-item", Color.valueOf("4fc3f7"));
exampleItem.cost = 1f;
```

## 建普通墙（v159.7，仿 copper-wall）

```java
exampleWall = new Wall("example-wall"){{
    requirements(Category.defense, with(ModItems.exampleItem, 6));
    health = 200;
}};
```

## 自定义墙 + 内部 Build（v159.7）

```java
public class MyWall extends Wall {
    public MyWall(String name){ super(name); }
    public class MyWallBuild extends WallBuild {
        @Override public float handleDamage(float amount){
            return Math.min(amount, 1f);   // 每次最多受 1
        }
    }
}
```

## 事件（v159.7）

```java
Events.on(EventType.BlockBuildEndEvent.class, e -> { ... });
Events.on(EventType.UnitDestroyEvent.class, e -> { ... });
Events.run(Trigger.update, () -> { ... });
```

## 生命周期钩子（都在 Build/BuildingComp 上）

`placed()` → `onProximityAdded()` → `updateTile()`（每 tick）→ `onProximityUpdate()` → `onDestroyed()` → `onRemoved()`；存档用 `write(Writes)` / `read(Reads, byte)`。

## 常用查找

```java
Vars.content.getByName(ContentType.block, "modname-example-wall");
Vars.content.getBy(ContentType.item);
Items.copper; Blocks.conveyor;   // 官方内容直接引用静态字段
```

## 构建命令

```bash
./gradlew jar          # 编译打包
# 产物 build/libs/*.jar → 拷进游戏 mods 文件夹
```

---

# 附录 B：Block ↔ Building 绑定机制

> 本附录是理解"为什么你写了个内部类，游戏就自动用它"的核心。
> 所有源码行号基于 v159.7（HEAD `b3317f3`）。

## B.1 机制概述

**Block 是"类型/模版"，Building 是"实例/实体"。** 一个 Block 类定义了这种方块的所有静态属性（大小、血量、贴图），游戏世界中每放置一块该方块，就由 Block 自动创建一个对应的 Building 实例来承载运行时状态与行为。

两者的绑定**不是靠命名约定，而是靠 Java 反射自动完成的**。只要你把 Building 子类写成 Block 的内部类，游戏构造 Block 对象时就会自动扫描并绑定。

---

## B.2 核心源码解析

### B.2.1 `initBuilding()` 方法（Block.java:1241-1278）

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

### B.2.2 `buildType` 字段（Block.java:401-403）

```java
// Block.java:401-403
public Prov<Building> buildType = null;
// 注释原文："Set manually if modded"
```

`buildType` 是一个 `Prov<Building>`（无参 Supplier），调用它就 new 出一个新的 Building 实例。默认 `null`，由 `initBuilding()` 自动填充。

**"Set manually if modded" 的含义**：如果你用匿名类写法（`new Wall("x"){{...}}`），自动绑定会回退到父类的 Building。这时候想自定义 Building，就得手动赋值。

### B.2.3 兜底 `Building::create`（Block.java:1274-1276）

```java
// gen/Building.java:1208（由 KAPT 生成）
public static Building create(){
    return new Building();
}
```

当反射一路向上都没找到任何 Building 子类内部类时，就用这个兜底。出来的 Building 是空壳，只有最基础的方法，没有任何自定义行为。

### B.2.4 匿名类处理（Block.java:1246-1248）

```java
Class<?> current = getClass();
if(current.isAnonymousClass()) current = current.getSuperclass();
```

**为什么要跳过匿名类？** 因为匿名类（`new Wall("x"){{ ... }}`）本身没有命名内部类，直接在它上面 `getDeclaredClasses()` 永远找不到东西。所以自动跳到它的父类（也就是 Wall 本身），然后在 Wall 上找 `WallBuild`。

---

## B.3 官方证据：反射不依赖命名

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

## B.4 4 个失效场景

### 场景 1：内部类不是 Building 子类

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

### 场景 2：一个 Block 声明多个 Building 内部类（只绑第一个）

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

### 场景 3：内部类写在 Block 子类体外

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

### 场景 4：匿名 Block 想自定义 Building → 需手动 `buildType = MyBuild::new`

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

## B.5 验证技巧

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

## B.6 完整正确示例

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

> **本教程基于 Mindustry v159.7。** 所有标注的源码行号（如 `BuildingComp.java:2066`、`Content.java:20`、`Block.java:1228`）均对应该版本。游戏版本升级后行号可能偏移，但 API 思想不变。祝玩得开心。

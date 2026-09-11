# Mindustry 版本考古全记录

> 基于本地完整 git 仓库逐条核验：Mindustry@master（完整历史，197 个 tag，HEAD b3317f3 = v159.7-479）、Arc@master（889dd888，与构建所需 archash 一致）、UCore（v5 早期逻辑）。所有结论均可追溯至源码提交。
> 整理时间：2026-09-10

---

## 一、默认游戏数据目录与启动时修改

### 1.1 各平台默认数据目录（全版本）

| 平台 | 默认路径 | 版本范围 | 备注 |
|---|---|---|---|
| Windows | `%AppData%\Mindustry\`（即 `C:\Users\<你>\AppData\Roaming\Mindustry\`） | 全版本不变 | 源码 `env("AppData") + "\\" + appname` |
| macOS | `~/Library/Application Support/Mindustry/` | 全版本不变 | 基于 `user.home` |
| Linux | `~/.mindustry/` | v33–v103 | ucore / arc 早期 |
| Linux | `$XDG_DATA_HOME/Mindustry/`，未设置时 `~/.local/share/Mindustry/` | v104 起（现行） | arc 提交 fb10d061，2019-12-25 |
| Steam 版 | 游戏安装目录 `saves/` | v93 起 | f7eea51066，2019-09，与普通版分离 |

> 注：Windows 的 `%AppData%` 路径实现自 ucore 时代起从未改变（`env("AppData") + "\\" + appname`），这是"设置 %AppData% 会生效但目录被套一层 `\Mindustry`"的源码原因（用户实测 v88 验证）。

### 1.2 启动时修改数据目录（官方方式）

| 方式 | 起始版本 | 用法 | 优先级 |
|---|---|---|---|
| 环境变量 `MINDUSTRY_DATA_DIR` | v126（efa5c5db7b，2021-03） | `MINDUSTRY_DATA_DIR=/自定义/路径 java -jar Mindustry.jar` | 次 |
| JVM 参数 `-Dmindustry.data.dir` | v147（d65f226b3a，2024-11，现行推荐） | `java -Dmindustry.data.dir=/自定义/路径 -jar Mindustry.jar` | 高 |

当前（v159.7）源码：

```java
String dataDir = System.getProperty("mindustry.data.dir", OS.env("MINDUSTRY_DATA_DIR"));
```

优先级：**-D 参数 > 环境变量 > 系统默认**。

### 1.3 隐式覆盖（无需官方参数，v126 之前也能用）

路径实现基于环境变量 / 系统属性的平台，直接覆盖底层变量即可改目录。优先级：官方参数 > 本表隐式覆盖 > 系统默认。

| 平台 | 覆盖方式 | 结果路径 |
|---|---|---|
| Windows（全版本） | `set AppData=D:\自定义`（v88 实测有效） | `D:\自定义\Mindustry`（被套一层） |
| Linux v104+ | `XDG_DATA_HOME=/自定义` | `/自定义/Mindustry` |
| Linux v104 前 | `-Duser.home=/自定义` | `/自定义/.mindustry` |
| macOS | `-Duser.home=/自定义` | `/自定义/Library/Application Support/Mindustry` |

> 注意：`-Duser.home` 会影响所有基于家目录的路径，副作用较大；Windows `AppData` 变量只影响本应用读取的路径，副作用小。

---

## 二、像素风演进

| 阶段 | 版本 | 时间 | 事件 |
|---|---|---|---|
| 像素机制 | v33 前 | 2017-05（b8b3848e5e） | 渲染管线即像素风 |
| 「Pixelate Screen」设置 | v33 | 2017-11-20（aedf46257a） | 加入设置项，默认开启 |
| 设置移除 | v41–v48 | 2018-07/08（76d0285e3f、47af2e83f7） | 设置被移除，强制像素 |
| **传统像素风结束** | **v70** | 2019-04-02（67a12eecad） | pixelate 设置回归且**默认关闭** |

结论：v70 起游戏默认不再是传统像素风（pixelate 默认关）。

---

## 三、模组支持历史

### 3.1 模组系统时间线

| 版本 | 时间 | 事件 |
|---|---|---|
| v97 | 2019-10-24（70ab102d8c） | **模组系统诞生**；Java 模组从 v97 就支持（mod.json `main` 字段从 jar 加载 .class） |
| v101 | 2019-11-21（2c61fcdfa6） | `minGameVersion` 字段加入 |
| v122 | 2020-12-23（79423e4c60、8ac027af70） | 模组浏览器 + `Version.isAtLeast` 版本比较 |
| v124 | —（fc3352bcb1） | mod.json `java` 字段作为**可选标记** + 导入 UI（Java 模组支持本身 v97 已有，v124 只是声明与 UI 化） |
| v142 | —（0d2dfadba7） | `softDependencies`（软依赖） |
| v146 | —（1968da9409） | `internalName`（模组内部名） |
| v154.3 | —（8e7eeb2573） | `iosCompatible`（iOS 兼容标记） |
| v156 | —（16bbcb62e6） | `legacyCompatible`（旧版本兼容豁免） |

### 3.2 minGameVersion 阈值矩阵（逐版核查）

| 版本区间 | 阈值 | 说明 |
|---|---|---|
| v97–v100 | 无字段 | 模组系统刚诞生，尚无版本校验 |
| v101–v104 | 无阈值 | 字段存在但未做校验 |
| v105–v135 | **105** |  |
| v136–v146 | **136** | 切换提交 d92c9cfcf8（2022-03-05，与 v136 发布同步） |
| v147–v154.1 | 脚本 **136** / Java **147** | Java 模组单独门槛 |
| v154.2–v155 | Java 提升到 **154** | a552908d76（2025-12-17） |
| v156 起（当前 v159.7） | 脚本 **136** / Java **154**，标记 `legacyCompatible` 可豁免 | 16bbcb62e6 |

当前 Vars.java：`minModGameVersion = 136`、`minJavaModGameVersion = 154`（阈值常量已从旧代码移到 Vars）。

### 3.3 legacyCompatible 用法

mod.json 中声明 `"legacyCompatible": true`，共三处生效：

| 位置 | 作用 |
|---|---|
| Mods.java:1161 | 加载判定豁免（`|| meta.legacyCompatible`） |
| Mods.java:1311 | `isOutdated()` 版本检查豁免（`&& !meta.legacyCompatible`） |
| ModBrowserDialog.java:323 | 模组浏览器不显示"不兼容"提示 |

ModListing.java 数据类同步增加字段。

> 注意：`legacyCompatible` 只豁免**版本门禁**，不豁免 **API 兼容性**——旧模组仍可能因 API 变更而崩溃。

---

## 四、设置演进（v136 起为基准）

> v136（2022-07-15）是 v8 首版，也是设置面一次大扩张（+6 项）。以下先给出 **v136 的全部活跃设置项**，再列出它之后逐版本的增删。键名为源码原始名，平台限制标注于括号中（未标注即全平台）。

### 4.1 v136 完整活跃设置清单（53 项）

#### 声音（3 项）

| 键名 | 中文名 |
|---|---|
| `musicvol` | 音乐音量 |
| `sfxvol` | 音效音量 |
| `ambientvol` | 环境音量 |

#### 游戏（17 项）

| 键名 | 中文名 | 平台限制 |
|---|---|---|
| `saveinterval` | 自动保存间隔 |  |
| `playerlimit` | 房间人数上限 | Steam 专用 |
| `autotarget` | 自动瞄准 | 移动端 |
| `keyboard` | 外接键盘 | 移动端（非 iOS） |
| `crashreport` | 崩溃报告 | 桌面端 |
| `savecreate` | 自动创建新存档 |  |
| `blockreplace` | 方块替换 |  |
| `conveyorpathfinding` | 传送带寻路 |  |
| `hints` | 游戏提示 |  |
| `logichints` | 逻辑提示 |  |
| `backgroundpause` | 后台暂停 | 桌面端 |
| `buildautopause` | 建造自动暂停 | 桌面端 |
| `doubletapmine` | 双击采矿 |  |
| `commandmodehold` | 指令模式按住保持 |  |
| `modcrashdisable` | 禁用问题模组 | 非 iOS |
| `publichost` | 公开主机（Steam 大厅） | Steam（非 beta） |
| `console` | 控制台 | 桌面端 |

#### 图形（33 项）

| 键名 | 中文名 | 平台限制 |
|---|---|---|
| `uiscale` | 界面缩放 |  |
| `screenshake` | 屏幕震动 |  |
| `bloomintensity` | 泛光强度 |  |
| `bloomblur` | 泛光模糊 |  |
| `fpscap` | 帧数上限 |  |
| `chatopacity` | 聊天框透明度 |  |
| `lasersopacity` | 激光透明度 |  |
| `bridgeopacity` | 桥透明度 |  |
| `vsync` | 垂直同步 | 桌面端 |
| `fullscreen` | 全屏 | 桌面端 |
| `borderlesswindow` | 无边框窗口 | 桌面端 |
| `landscape` | 强制横屏 | 移动端（非 iOS） |
| `effects` | 粒子特效 |  |
| `atmosphere` | 大气效果 |  |
| `destroyedblocks` | 显示被摧毁的方块 |  |
| `blockstatus` | 方块状态显示 |  |
| `playerchat` | 显示玩家聊天 |  |
| `coreitems` | 核心物品统计 | 桌面端 |
| `minimap` | 小地图 |  |
| `smoothcamera` | 平滑相机 |  |
| `position` | 显示坐标 |  |
| `mouseposition` | 显示鼠标位置 | 桌面端 |
| `fps` | 显示帧数 |  |
| `playerindicators` | 玩家指示器 |  |
| `indicators` | 敌人指示器 |  |
| `showweather` | 显示天气 |  |
| `animatedwater` | 动态水 |  |
| `animatedshields` | 动画护盾 | 非移动端 |
| `bloom` | 泛光 |  |
| `pixelate` | 像素化滤镜 |  |
| `linear` | 线性过滤 | 非 iOS |
| `skipcoreanimation` | 跳过核心动画 |  |
| `hidedisplays` | 隐藏显示器 |  |

> 参考：`touchscreen`（触屏模式）曾为早期设置，v102（2019-12-25，提交 514d4817c8 "it is done"）起在桌面端被注释停用，不再计入活跃设置。

### 4.2 v136 之后的历史变化（逐版本）

| 版本 | 时间 | 新增 | 移除 |
|---|---|---|---|
| v143 | 2023-03 | `steampublichost` | `publichost`（被前者取代） |
| v145 | 2023-06 | `macnotch`（Mac 刘海屏适配） | — |
| v146 | 2023-09 | `drawlight`（光照开关） | — |
| v147 | 2025-04 | `alwaysmusic`、`communityservers`、`distinctcontrolgroups`、`maxmagnificationmultiplierpercent`、`minmagnificationmultiplierpercent`、`unitlaseropacity` | — |
| v150 | 2025-07 | `detach-camera`（视角分离） | — |
| v155.1→v155.4 | 2026-02 | `borderlesswindow` 恢复 | `borderlesswindow` 短暂移除（窗口重构波动） |
| v156 | 2026-03 | `showotherbuildplans`、`showpings`、`uiEdgePadding` | — |
| v157 | 2026-04 | — | `borderlesswindow`、`crashreport` |
| v157.3 | 2026-04 | — | `logichints` |
| v159 | 2026-07 | `drawhitboxes`、`showperformance` | `steampublichost` |

其余版本号（v136.1~v142、v144.x、v148~v149、v151~v155、v158.x、v159.x）设置面无变化。

### 4.3 当前状态（v159.7，63 项）

相对 v136：**新增 14 项、移除 4 项**。

新增（14）：`alwaysmusic`、`communityservers`、`detach-camera`、`distinctcontrolgroups`、`drawhitboxes`、`drawlight`、`macnotch`、`maxmagnificationmultiplierpercent`、`minmagnificationmultiplierpercent`、`showotherbuildplans`、`showperformance`、`showpings`、`uiEdgePadding`、`unitlaseropacity`

移除（4）：`borderlesswindow`、`crashreport`、`logichints`、`publichost`

最终 63 项清单：

```
alwaysmusic ambientvol animatedshields animatedwater atmosphere autotarget
backgroundpause blockreplace blockstatus bloom bloomblur bloomintensity
bridgeopacity buildautopause chatopacity commandmodehold communityservers console
conveyorpathfinding coreitems destroyedblocks detach-camera distinctcontrolgroups
doubletapmine drawhitboxes drawlight effects fps fpscap fullscreen hidedisplays
hints indicators keyboard landscape lasersopacity linear macnotch
maxmagnificationmultiplierpercent minimap minmagnificationmultiplierpercent modcrashdisable
mouseposition musicvol pixelate playerchat playerindicators playerlimit position
savecreate saveinterval screenshake sfxvol showotherbuildplans showperformance
showpings showweather skipcoreanimation smoothcamera uiEdgePadding uiscale
unitlaseropacity vsync
```

> 注：`detach-camera` 键名含连字符；`uiEdgePadding` 为驼峰命名。

---

## 附录：核验方法

- **数据目录**：Mindustry ClientLauncher.setup / Vars.loadSettings；Arc OS.getAppDataDirectoryString；关键提交 efa5c5db7b（v126 环境变量）、d65f226b3a（v147 -D 参数）、f7eea51066（v93 Steam）、fb10d061（Linux XDG）。
- **像素风**：git log -S 定位 `pixelate` / `enablePixelation` 引入与移除提交，git tag --contains 映射版本。
- **模组**：Mods.java / ModListing.java / ModBrowserDialog.java 源码 + git log -S。
- **设置**：逐 tag `git show <tag>:core/src/mindustry/ui/dialogs/SettingsMenuDialog.java`，剔除单行与块注释后提取全部 `checkPref` / `sliderPref` / `textPref` / `areaTextPref` 键名，`comm` 集合对比得增删；移除项均 grep 反查确认。覆盖 v120→v159.7 全部 tag，v136 起点为完整活跃清单。

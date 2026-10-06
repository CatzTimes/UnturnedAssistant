# UnturnedAssistant V4.0（Unturned ID 查找器）

扫描 Unturned 游戏本体与创意工坊的 `.dat`/`.asset` 资产文件，生成 **ID → 名称** 对照表。
本仓库是 [sandtechnology/UnturnedAssistant](https://github.com/sandtechnology/UnturnedAssistant) 的二次开发分支（V4.0 全面重写），资产发现/解析规则对齐官方源码（U3-SDK 的 `AssetsWorker`/`Assets`/`UnturnedNexus`）。

## 功能

- **自动检测目录**：启动即通过注册表 + `libraryfolders.vdf` 定位 Steam 库，自动填入游戏目录与创意工坊目录（`steamapps/workshop/content/304930`），不再硬编码盘符。
- **完整类别**：物品（EItemType 全部 50 种）、载具、物体、动物、资源、NPC角色、对话、任务、商人、特效、皮肤、神话、刷怪表、重定向、其他。
- **兼容 v1/v2 资产格式**：`<目录名>.dat`、`Asset.dat`、`*.asset`、`Metadata {}`/`Asset {}` 子字典、引号值、`//` 注释、UTF-8 BOM。
- **无语言文件兜底**：不再依赖 `English.dat` 才收录；无任何语言文件时用内部名兜底（与游戏行为一致）。
- **容错解析**：ID 带尾部空白、越界（>65535）等脏数据不再崩溃，非法值标注 `⚠ID非法`。
- **冲突与保留区间标注**：按官方规则（官方本体优先 → 工坊先到先得）标注 `⚠ID冲突`、`⚠GUID冲突`；非官方资产占用官方保留 ID 区间（如物品 <2000）时标注 `⚠官方保留ID区间`。
- **重定向解析**：`Redirector` 资产显示其 `TargetAsset` 指向的实际资产名。
- **导出 CSV**：列序对齐官方 `AssetIdListExporter`（Name, GUID, Type, Origin, Legacy ID, Legacy Category），UTF-8 带 BOM，Excel 直接打开不乱码。
- **多线程扫描**：按目录粒度并行解析（2~8 线程），官方本体 + 全部工坊约 1.2 万资产 ≈ 10 秒；支持取消，进度条为真实进度。

## 使用

1. 双击 `UnturnedAssistant-4.0.exe`（单文件绿色版，自带运行时，无需安装 Java；双击后有 1~2 秒解压启动时间属正常，个别杀软可能对自解压壳误报）。
2. 启动后自动填入检测到的游戏目录；检测失败可手动输入或点"选择游戏目录"（也可以直接选任意模组合集文件夹）。
3. 勾选"同时扫描创意工坊目录"后点"开始生成"；结果按类别分组展示，可点"导出 CSV"。

命令行模式（自动化验证用）：

```powershell
java -cp classes Start --scan "<游戏或模组目录>" [workshop]
```

## 构建（Windows）

前置：JDK 27（脚本默认 `C:\Program Files\Java\jdk-27`，可在 `build.ps1` 头部修改）、网络（首次需从 7-zip.org 下载 SFX 模块，之后缓存于 `tools/`）。

```powershell
.\build.ps1            # 完整构建：javac(27) → jar → jpackage app-image → 7z SFX 单 exe
.\build.ps1 -SkipSfx   # 只产出 dist\UnturnedAssistant\ 文件夹版
```

产物：

| 文件 | 说明 |
|---|---|
| `dist/UnturnedAssistant-4.0.exe` | 单文件绿色版（7zSD SFX 封装，双击即用，退出后临时文件自清理） |
| `dist/UnturnedAssistant/UnturnedAssistant.exe` | app-image 文件夹版（含自带运行时） |

## V4.0 相对 V3.9 的主要变化

- 重写解析器：v1/v2 兼容（旧版只能按空格切分，遇到引号/注释/子字典即错解），UTF-8 BOM 剥离（修复中文模组 `#未找到#`）。
- 资产发现从"只认 English.dat"改为目录驱动（对齐游戏 `AssetsWorker.FindAssets`），无语言文件资产不再漏项。
- Type 分类表对齐官方 `UnturnedNexus` 注册表 + `EItemType` 50 项，新增特效/皮肤/神话/刷怪表/商人/重定向等类别。
- 工坊扫描按 `.meta` 标记剪枝（跳过地图包与 UI 本地化包）。
- Java 8 → 27；扫描/解析多线程化；启动自动检测目录；路径输入容错（不再默认 "."、去引号、校验存在性）。
- 新增 CSV 导出、冲突/保留区间/重定向/非法 ID 标注。

## 许可

见 [LICENSE](LICENSE)（原项目许可）。

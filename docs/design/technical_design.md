# FloatBro 技术架构与设计规范白皮书

> **项目名称**：FloatBro (`Float` + `Browser`，致敬开源基石 `EinkBro` 并演进为全局悬浮形态 `Float Browser`)  
> **项目定位**：现代 Android 网页级全局悬浮、对等双视口分屏、纯 C++ 规则引擎拦截的高性能多任务浏览器  
> **交付状态**：已完成核心工程落地与多轮真实机型验证  
> **文档版本**：v1.1.0 (2026-10-13)

---

## 目录
1. [项目背景与核心愿景](#一-项目背景与核心愿景)
2. [需求定义矩阵 (PRD)](#二-需求定义矩阵-prd)
3. [全网竞品调研与基石选型论证](#三-全网竞品调研与基石选型论证)
4. [核心架构设计与工程改造蓝图](#四-核心架构设计与工程改造蓝图)
5. [关键技术攻坚方案](#五-关键技术攻坚方案)
6. [分步实施里程碑与落地路线图](#六-分步实施里程碑与落地路线图)
7. [开源合规与衍生致敬规范](#七-开源合规与衍生致敬规范)

---

## 一、 项目背景与核心愿景

### 1.1 痛点起源
在移动端多任务场景下，用户经常需要在不切出当前 App（如聊天、看视频、游戏、对照查阅资料）的前提下临时浏览网页或对照查看两份资料。现存方案存在严重的体验割裂：
* **传统悬浮浏览器**：交互尚可，但底层为原生简陋 WebView，去广告仅为基础 hosts/域名黑名单，现代网页留白与破图严重，且无法实现真正的双网页联动分屏；
* **手机厂商自带的“系统自由小窗”**：绝大多数厂商 ROM（如小米 HyperOS、OPPO ColorOS、vivo OriginOS）强制限制同屏只能开启 1 个活动小窗，无法多开网页，且必须先启动笨重的浏览器主应用；
* **工业级全屏浏览器（Brave / Kiwi / Firefox）**：去广告极其优秀，但均采用 200MB+ 的重型单体 Activity 架构，完全不支持作为全局系统悬浮窗运行。

### 1.2 项目愿景：FloatBro
**FloatBro** 旨在打造 Android 生态中首款将 **“网页即浮窗（零主界面包袱）”**、**“对等双视口真分屏”** 与 **“原生 C++ 纯净去广告”** 深度融合的极客级开源浏览器。

```
                    ┌──────────────────────────────────────────────┐
                    │               FloatBro 核心能力               │
                    └──────────────────────────────────────────────┘
                                           ▲
                                          / \
                                         /   \
                                        /     \
    【网页级原生悬浮】 ◄────────────────────────────────────────► 【对等双视口真分屏】
   (SYSTEM_ALERT_WINDOW)                                       (中线拖拽/比例自适应重排)
   • 零主应用外壳包袱                                            • 双独立对等视口
   • 自由手势无级缩放                                            • 左右/上下自由切换
   • 贴边收纳独立气泡                                            • 拒绝散装拼图
                                       \     /
                                        \   /
                                         \ /
                                          ▼
                             【原生 C++ 纯净去广告】
                              (:adblock-client C++ 内核)
                              • EasyList 规则匹配
                              • 原生 CSS :has() 元素隐藏
                              • 彻底消灭留白破图
```

---

## 二、 需求定义矩阵 (PRD)

| 需求编号 | 需求项 | 级别 | 详细描述与验收标准 |
| :--- | :--- | :---: | :--- |
| **FR-01** | **网页级原生全局悬浮** | **P0** | 基于 `SYSTEM_ALERT_WINDOW`，网页直接作为独立系统浮窗置顶在任意 App/桌面之上运行；无臃肿全屏主界面，点开即浮起，可输入并浏览任意 URL。 |
| **FR-02** | **自由手势无级缩放** | **P0** | 窗口右下角具备专属拖拽手柄（Resize Handle），支持手指自由拖动实时无级改变窗口尺寸与宽高比。 |
| **FR-03** | **贴边收纳与独立气泡** | **P0** | 拖动至屏幕边缘或点击最小化时，通过平滑属性动画将窗口收缩为贴边悬浮气泡（Bubble）；点击气泡秒级原地复原网页状态。 |
| **FR-04** | **对等联动真分屏** | **P0** | 具备几何对齐与可拖动中线分割条（Splitter Bar）；拖动中线两边网页同步联动重排；支持水平/垂直切分切换；支持从主屏定向把链接派发至辅屏。坚决排除两个散装乱飘浮窗的拼图方案。 |
| **FR-05** | **强力 C++ 规则去广告** | **P0** | 必须支持现代 EasyList 语法，具备 **Cosmetic Filtering（元素隐藏）** 与 Scriptlet 注入；必须支持 Chromium 原生 CSS `:has()` 伪类，拦截后不留空白框、不留破图。 |
| **FR-06** | **多浮窗/多气泡并存** | **P1** | 允许屏幕上同时弹出多个独立的网页浮窗或在边缘挂载多个气泡，打破手机系统单小窗名额限制。 |
| **NFR-01** | **现代系统全兼容** | **P0** | 适配 Android 8.0 ~ 16，符合 Android 15 强制要求的 16KB 内存分页对齐，支持预测性返回。 |
| **NFR-02** | **纯净无骚扰** | **P0** | 应用自身绝不内嵌任何商业广告 SDK、开屏弹窗或未经授权的隐私追踪。 |

---

## 三、 全网竞品调研与基石选型论证

### 3.1 竞品生态排查总结（15+ 款软件实测）
调研覆盖 GitHub、F-Droid、Google Play、酷安（CoolApk）与 XDA 社区：
1. **悬浮工具派（Float Browser, Floating Apps）**：有悬浮壳和气泡，但底层为原生简陋 WebView，去广告仅为基础 hosts/黑名单，留白严重；多开为散装无序浮窗，无中线联动；
2. **分屏工具派（各“双开/分屏浏览器”）**：100% 为传统全屏 Activity，退回桌面即最小化，无系统级悬浮；且多数自身内嵌全屏广告；
3. **插件扩展派（Kiwi / Firefox + Split 插件）**：技术死胡同。沙箱无法突破到系统浮窗，且 iframe 分屏会被各大主流站点的 `X-Frame-Options` 和 CSP 拦截直接白屏；
4. **重型大厂派（Brave, Vivaldi, Soul）**：Vivaldi 手机端无桌面 Tab Tiling；Soul 仅支持视频画中画，不支持网页全局悬浮。

### 3.2 选型的底层基石：EinkBro (plateaukao/einkbro)

| 审查维度 | EinkBro 现状 | 对 FloatBro 的改造价值 |
| :--- | :--- | :--- |
| **去广告引擎** | 独立的 `:ad-filter` 与 `:adblock-client` 模块，纯 C++ 算法（BloomFilter + Hash Set），支持原生 CSS `:has()` 元素隐藏 | **核心资产**。去广告能力极其出众，且已完全解耦为独立 Gradle 模块，开箱即用。 |
| **联动真分屏** | 自带 `TwoPaneLayout.kt` 自定义容器，内置 Splitter 拖拽中线、水平/垂直切换与左右平滑联动重排 | **核心资产**。已原生实现带中线联动的真分屏，省去 90% 的自定义布局手势调试。 |
| **架构现代化** | 纯 Kotlin 2.1 + Jetpack Compose 1.7.8，全库零传统 XML 布局，TargetSdk 36 | 架构干净前瞻，维护成本低。 |
| **编译成本** | CMake C++ 随 Gradle 自动编译，**零复杂外部工具链依赖** | 本地普通 Android Studio 开箱即编，无复杂交叉编译链。 |
| **墨水屏解耦** | 无文石/海信专属硬件依赖，专属代码仅占 < 5% | 剥离极其容易，普通 OLED/LCD 手机上天然支持 120Hz 全彩。 |
| **悬浮窗改造** | 原版以普通全屏 Activity 运行，缺少悬浮窗 Service | **核心攻坚目标**。 |

---

## 四、 核心架构设计与工程改造蓝图

### 4.1 总体分层架构图

```
+---------------------------------------------------------------------------------+
|                       FloatBro 系统级悬浮架构 (Android)                           |
+---------------------------------------------------------------------------------+
                                        │
                    [ WindowManager / TYPE_APPLICATION_OVERLAY ]
                                        │
┌───────────────────────────────────────▼────────────────────────────────────────┐
│ FloatBrowserService (前台服务 / 状态机核心)                                     │
│  ├── 状态 A: FloatingBubbleView (52dp 贴边小气泡 / 徽标指示 / 单击秒展开)         │
│  └── 状态 B: FloatingContainerView (悬浮主视口 / 阴影圆角外壳)                    │
│        ├── TopBar: 地址栏 / 搜索 / 最小化 / 关窗 / 横竖分屏切换                │
│        ├── TouchHandle: 右下角无级缩放抓手 (Resize Handle)                     │
│        └── ComposeOverlayView: 内嵌透明图层 (渲染设置、书签，杜绝 BadToken)    │
└───────────────────────────────────────┬────────────────────────────────────────┘
                                        │
┌───────────────────────────────────────▼────────────────────────────────────────┐
│ TwoPaneLayout (从 EinkBro 继承并升级的真分屏布局容器)                           │
│  ├── Pane 1: EBWebView (主视口，独立 Tab/历史栈)                                │
│  ├── DragHandle (可拖拽中线分割条，实时联动重新分配两边 LayoutParams.width)      │
│  └── Pane 2: EBWebView (辅视口，升级为对等独立的 Tab/历史栈)                    │
└───────────────────────────────────────┬────────────────────────────────────────┘
                                        │
┌───────────────────────────────────────▼────────────────────────────────────────┐
│ 强力内核层 (EinkBro 原生抽离)                                                   │
│  ├── :ad-filter (Kotlin): 请求拦截分发、EasyList 规则下载、CSS :has() 样式注入 │
│  └── :adblock-client (C++): libadblock-client.so, BloomFilter 亚毫秒级规则匹配  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 模块工程结构划分

```text
FloatBro/
├── app/                  # 悬浮窗宿主与业务 UI (Kotlin + Compose)
├── core/
│   ├── webview/          # 对等双视口容器 (TwoPaneLayout)、EBWebView 抽象与 InputFocusBridge
│   └── common/           # 跨模块通用常量、ThemeTokens、DisplayUtils 边界测量
├── ad-filter/            # 规则调度层、EasyList 规则解析与 CSS 注入
└── adblock-client/       # C++ BloomFilter 原生去广告 SO 库 (16KB 内存对齐)
```

---

## 五、 关键技术攻坚方案

### 5.1 攻坚一：悬浮窗环境下 Context 弹窗崩溃 (`BadTokenException`)
* **问题本质**：原版弹窗依赖 `DialogFragment` 或 `AlertDialog.Builder(activity)`。若在 `Service` 环境下直接弹 Dialog，系统会因缺少 Activity Window Token 抛出致命崩溃 `BadTokenException`。
* **攻坚方案**：
  * 在悬浮窗根布局顶层常驻透明 `ComposeView`（层级高于 WebView）；
  * 当用户点击“设置”、“菜单”或“书签”时，不弹出系统 Dialog，而是直接修改内部状态驱动 ComposeOverlay 在当前悬浮窗内部渲染半透明浮层，**彻底根除 Token 异常**。

### 5.2 攻坚二：软键盘与全局焦点争夺
* **问题本质**：
  * 若悬浮窗常驻带焦点，会导致底层宿主 App（如微信）的所有点击和按键被拦截；
  * 若悬浮窗加了 `FLAG_NOT_FOCUSABLE`，点击网页中的 `<input>` 输入框或顶部地址栏时，无法调起系统软键盘。
* **攻坚方案**：
  * 默认状态：窗口添加 `FLAG_NOT_FOCUSABLE`，底层 App 正常穿透操作；
  * 聚焦触发：在 `EBWebView` 监听到表单聚焦（或点击顶部地址栏）时，动态调用 `windowManager.updateViewLayout` 移除 `FLAG_NOT_FOCUSABLE`；
  * 失焦恢复：点击非可输入区域或软键盘收起时，自动重新加回 `FLAG_NOT_FOCUSABLE`。

### 5.3 攻坚三：辅屏从“从属翻译屏”升级为“对称独立视口”
* **问题本质**：EinkBro 原版的第二屏是专为翻译和查词设计的从属视图，没有独立的标签页管理器与历史栈。
* **攻坚方案**：
  * 将 `TwoPaneLayout` 中的翻译面板重构为对称的 `PaneContainer`；
  * 抽象 `PaneController`，让 Pane 1 与 Pane 2 各自持有独立的 `TabController` 与 `WebBackForwardList`；
  * 保留中线拖拽比例重排与“主屏长按链接在副屏打开（Open in other pane）”的联动机制。

### 5.4 攻坚四：文件选择器与运行时权限
* **问题本质**：Service 无法直接调用 `registerForActivityResult` 接收 `<input type="file">` 的文件选择结果。
* **攻坚方案**：引入透明轻量的 `TrampolineActivity`。当 WebChromeClient 触发 `onShowFileChooser` 时，拉起透明 Activity 代理处理系统文件选择或权限请求，拿到 Uri 后回传给 Service 并立即 finish。

---

## 六、 分步实施里程碑与落地路线图

### 6.1 阶段里程碑规划 (Milestones)

```text
Phase 1: 仓库初始化与多模块解耦
├── 确立 io.github.floatbro 独立包名与 Version Catalog 依赖统一管理
├── 移除 epub4j 电子书代码，减负工程体积
├── 开启 Material 3 全彩高刷主题适配
└── 验证 C++ BloomFilter 编译与 16KB Page Size 内存对齐

Phase 2: 对称双屏架构升级
├── 重构 TwoPaneLayout，副屏升级为支持独立 URL 输入与历史栈的对等视口
├── 实现可拖拽中线 (dragHandle) 实时联动重排
└── 验证双视口独立去广告与 CSS :has 注入

Phase 3: 系统级全局悬浮窗与手势物理动效
├── 挂载 FloatBrowserService (TYPE_APPLICATION_OVERLAY)
├── 封装 FloatingWindowView 顶层视口与右下角 48dp 缩放手柄
├── 实现贴边吸附与 52dp 磁吸小气泡 (FloatingBubbleView)
└── 接入 ComposeOverlayHost，彻底根除 BadTokenException

Phase 4: 焦点调优、稳定性加固与正式 Release 发布
├── 调试软键盘动态 Flag 切换与 BackNavigationDispatcher 四级返回调度
├── 落地 R8 混淆规则与 16KB Page Size 自动化检验
└── 配置官方正式 Release 签名密钥库与 GitHub Actions 自动化发布
```

---

## 七、 开源合规与衍生致敬规范

1. **许可证遵从**：
   * 本项目完整遵循 **GNU General Public License v3.0 (GPL-3.0-or-later)**；
   * 衍生的 FloatBro 源码必须以 GPLv3 协议保持开源。
2. **版权声明与致敬规范**：
   * 在项目根目录 `README.md`、`NOTICE` 与 App 内 `关于 (About)` 页面呈现致敬声明：
   > **FloatBro** is an open-source floating multi-tasking browser based on and inspired by **[EinkBro](https://github.com/plateaukao/einkbro)** (created by Plateau Kao), enhanced with native system overlay service, gesture-driven snap split-screen, and modern mobile UI.  
   > Licensed under **GPL-3.0**.
   * 保留原作者在各源码文件头部的 Copyright 信息，新增修改部分附注 FloatBro Contributors 版权。

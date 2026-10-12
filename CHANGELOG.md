# 变更日志 (Changelog)

本项目所有显著变更均记录于此文件。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，并遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- **Phase 1: 现代多模块骨架与 Brave 级去广告引擎移植**
  - 初始化 FloatBro 独立多模块工程骨架（`:app`, `:core:webview`, `:core:common`, `:ad-filter`, `:adblock-client`）；
  - 落地 Version Catalog 依赖统一管理（Kotlin 2.1.20, AGP 8.13.2, Compose 1.7.8, CompileSdk 36, TargetSdk 36, JDK 17, NDK r27c）；
  - 移植 C++ BloomFilter 引擎与 JNI 桥接，强制 `-Wl,-z,max-page-size=16384` 16KB Page Size 内存对齐；
  - 移植 EasyList 规则解析、CSS `:has()` 元素隐藏及 Scriptlets；
  - 引入 GPL-3.0 许可证与 NOTICE 溯源致敬声明；
  - 建立 GitHub Actions 自动化流水线 (`pr-checks.yml`, `release.yml`, `dependabot.yml`)。

- **Phase 2: 对称双视口分屏引擎 (Symmetric Dual-Pane Engine)**
  - 升级 `TwoPaneLayout`，副屏升级为支持自主输入 URL、独立历史栈的对等视口；
  - 实现 Windows Snap 式拖拽中线 (`dragHandle`)，支持 15%~85% 动态比例、双击 50:50 重置；
  - 封装硬件加速 `EBWebView`，挂载 `FloatWebViewClient` 与 `FloatWebChromeClient` 去广告拦截；
  - 落地跨视口联动调度与 `DualPaneDemoActivity` 全屏分屏验证。

- **Phase 3: 系统级悬浮窗驱动与手势交互 (Floating Overlay & Gestures)**
  - 挂载 `FloatBrowserService` 前台服务与 `TYPE_APPLICATION_OVERLAY` 顶层系统窗口；
  - 实现 `FloatingWindowView`：顶栏拖拽把手、分屏切换控制、右下角 `ResizeHandle` 无级缩放；
  - 实现 `FloatingBubbleView`：48dp/56dp 圆形小气泡，支持触控拖拽与边缘磁吸回弹动画 (Spring Physics)；
  - 实现 `InputFocusBridge`：网页表单输入时动态移除 `FLAG_NOT_FOCUSABLE` 唤起输入法，失焦恢复底层穿透；
  - 挂载 `ComposeOverlayHost`：在悬浮窗内嵌层渲染菜单与设置，彻底根除 `BadTokenException`。

- **Phase 4: 多级返回键分发、内存修剪与生产级 Release 终验**
  - 落地 `BackNavigationDispatcher` 纯领域返回决策器：四级优先级分发（浮层菜单关闭 -> 副屏网页后退 -> 主屏网页后退 -> 智能最小化为边缘气泡）；
  - 覆盖 `OverlayBackNavigationTest` 纯 JVM 毫秒级断言套件与反向保护；
  - 挂载 `onTrimMemory` 监听与临界内存缓存修剪 (`TRIM_MEMORY_RUNNING_CRITICAL`)；
  - 建立生产级 `app/proguard-rules.pro` 混淆规则，完成 R8 代码混淆与资源缩减（Release APK 瘦身至 2.23MB）；
  - 全架构（arm64-v8a, armeabi-v7a, x86, x86_64）完成 Android 15 16KB Page Size (`0x4000`) ELF 内存对齐终审；
  - 完成 Android 10 真机 ADB 悬浮窗启动、渲染与交互全流程实测验证。

- **Craftsmanship Infrastructure: 整洁工艺门禁建设**
  - 固化 `docs/ARCHITECTURE.md` 元架构契约与单向无环依赖图；
  - 搭建跨平台 L1 质量手套执行门禁 (`scripts/verify_gauntlet.ps1` & `scripts/verify_gauntlet.sh`)；
  - 建立领域层纯 JVM 毫秒级 TDD 单元测试套件与 PAMP 防偷懒变异存证。

- **Phase 5: 交互原型 100% 视觉还原与整洁工艺终验 (Clean Craftsmanship Tier 3)**
  - 彻底根除单字母方块 (`title.take(1)`) 与 emoji 调试按钮等非正式演示代码，重构为生产级探索主页；
  - 引入 Android (`#3DDC84`)、Wikipedia (`#2563EB`)、Bilibili (`#00AEEC`)、GitHub 官方品牌矢量资产与 SSL 盾牌安全指示；
  - 建立 Slate/Blue 设计令牌系统 (`Theme.kt`)，全量对齐 `interactive_prototype.html` 交互原型；
  - 彻底解决 86dp 双层顶栏堆叠缺陷：悬浮窗统一为单个 44dp 整合胶囊 Omni-bar，单视口模式自动抑制 `pane1` 内部冗余 Toolbar；
  - 实现全边沿拦截拖拽分发与无感右下角对角线缩放 (`WindowBoundsHelper` 0.45~2.20 纵横比边界保护)；
  - 悬浮球重构为 52dp 纯白正圆球体、2dp 品牌蓝描边，完整呈现双环品牌标识 (`#2563EB` 与 `#10B981`)；
  - 覆盖 `WindowBoundsHelperTest` 纯 JVM 单元测试，杀灭 `[OP-BOUND]` 与 `[OP-VOID]` 变异体；
  - 通过独立 PR Reviewer 审查全量通过 (PASS)，Gauntlet 质量流水线 100% 绿灯。

- **Phase 6: UI/UX 深度审计与 Phase 1 P0 缺陷整洁工艺治理 (Clean Craftsmanship Tier 2)**
  - 实施 `ThemeTokensTest` 纯 JVM 单元测试覆盖与 `[OP-BOUND]` 变异击杀存证；
  - 补全 Material 3 `FloatBroDarkColorScheme` 与 `FloatBroTypography` 排版令牌，支持深浅双模动态切换；
  - 适配 `FloatingWindowView` 窗体与胶囊顶栏深色主题感知，消除夜间模式刺眼大白块；
  - 彻底重构 `AppTabStrip` 标签关闭按钮，外层包裹无形触控热区并满足 WCAG 2.2 48dp 最小触控规范，添加无障碍语义 `Role.Button` 与动态标签名播报；
  - 升级 `InAppWebViewPane` 工具栏高度至 48dp，导航按键统一扩展至 48dp 并应用 8-Point Grid 间距体系；
  - 重绘 `ic_dock_app.xml` 矢量图标路径，纠正倒错的外跳 Popout 图标为正确的“向下沉降停靠至应用内”托盘图标。

- **Phase 7: UI/UX Phase 2 P1 缺陷整洁工艺治理与物理手势动效 (Clean Craftsmanship Tier 2)**
  - 实施 `OverlayPhysicsHelperTest` 纯 JVM 单元测试覆盖与 `[MUTANT-SNAP-BOUNDARY]` 变异击杀存证；
  - 纯领域抽离 `OverlayPhysicsHelper`：提供软键盘 IME 避让 Y 轴坐标计算、边缘吸附目标计算与碰撞判定；
  - 改造主控中心悬浮窗权限提示卡片，消除红色惊叹号刺眼警示，升级为“探索 FloatBro 悬浮核心能力”价值前置引导卡片；
  - 补全浮窗外跳点击拦截：未授权浮窗权限时弹出上下文 `AlertDialog` 引导直达系统权限设置；
  - 悬浮窗右下角缩放把手升级为 48dp WCAG 触控热区并绘制 3 槽微倾斜抓握条纹 (`ic_resize_grip.xml`)，按下时提供 1.15x 缩放与不透明度动效；
  - 悬浮窗拖拽边缘吸附升级为 200ms `DecelerateInterpolator` 减速插值平滑贴边动效与触感震动反馈 (`HapticFeedbackConstants`)；
  - 悬浮窗 Omnibar 展开编辑时引入输入法智能避让，退回只读态时恢复原位；
  - 打造高保真悬浮气泡拖拽丢弃区（Visual Bubble Dismiss Target）：
    - 引入 `ic_trash_bin.xml` 24dp 矢量垃圾桶资产；
    - 新增 `BubbleDismissTargetView` 64dp 半透明警示红圆形悬浮靶心，支持拖拽淡入/淡出；
    - 结合 `OverlayPhysicsHelper.isDropTargetHit` 实现 72dp 命中磁吸反馈：靶心 1.25x 放大、高饱和深红 `#DC2626` 变色与触感震动，气泡 0.82x 吸入暗示；
    - 命中释放安全销毁会话与 WebView，未命中释放执行 240ms 减速边缘磁吸贴靠。

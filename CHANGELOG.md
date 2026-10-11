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

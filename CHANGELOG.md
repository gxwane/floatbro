# 变更日志 (Changelog)

本项目所有显著变更均记录于此文件。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，并遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

## [0.1.6-beta] - 2026-10-15

### Added
- **单容器多窗口 0ms 聚焦切换架构 (Single-Host Desktop Container)**：
  - **彻底消灭多浮窗切换白屏闪烁**：引入 `FloatingDesktopContainer` 统一管理屏幕上所有 `FloatingWindowView` 实例，将多窗口聚合在同一个 WindowManager Surface 中；
  - **内存级 View 树瞬间置顶**：窗口聚焦与 Z-order 变更通过纯内存级 `bringChildWindowToFront` 完成，完全消除以往创建与销毁独立 Surface 导致的白闪与延迟；
  - **触控区域智能穿透代理**：依托 `InternalInsetsHelper` 计算所有子窗口的联合可见区域，空白区域 100% 穿透至底层宿主或桌面，底层 App 点击操作丝滑无阻碍。

### Fixed
- **Markdown 复杂富文本与嵌套排版引擎优化**：
  - **格式与排版加固**：增加对 `---`, `***`, `___` 水平分割线的原生渲染；支持连续引用块（Blockquote）合并呈现与富文本内联；支持粗体内部嵌套行内代码与链接解析；
  - **现代化组件迁移**：淘汰已废弃的 `ClickableText`，升级为现代 Compose `LinkAnnotation.Url`；
  - **完整呈现更新日志**：移除原有的 `take(4)` 强制截断规则，解决长篇更新日志无法展开阅读的问题。
- **界面微噪净化与弹性限高**：
  - 更新发布日志卡片增加 `heightIn(max = 520.dp)` 弹性自适应最大高度限制，防止对话框撑破屏幕；
  - 简化设置页文案，移除「本地诊断与日志」冗长副标题，将“非活动浮窗点击仅激活焦点”沉淀为底层引擎默认规范，减轻认知负担。

## [0.1.5-beta] - 2026-10-15

### Added
- **统一本地日志与诊断系统 (Unified Local Diagnostics & AdBlock Telemetry)**：
  - **常规运行态与致命崩溃临终急救**：接入全局 `DiagnosticTimberTree`，Release 模式不再静默；注册 `FloatBroCrashHandler`，未捕获异常通过零协程 Direct I/O 原子同步刷盘，避免进程强杀时日志丢失；记录 Service 状态、窗口 Z-order、聚焦互斥与拖拽手势轨迹；全面捕捉 Chromium WebView 渲染进程崩溃（区分 CPU Crash 与 OOM 强杀）及致命网络异常；
  - **广告拦截演进足迹与误杀溯源 (AdFilter Telemetry & Rule Evolution)**：通过 SPI 解耦挂载 `AdFilterTelemetryBridge`，仅记录阻断 (`BLOCKED`) 与白名单放行 (`ALLOWED_BY_EXCEPTION`) 关键事件（默认屏蔽常规海量 `PASSED` 事件），精准留存主站、资源目标域名、资源类型与命中规则明文，为广告规则演进提供可靠证据链；悬浮窗二级菜单新增「页面拦截诊断」一键复制当前站点过滤足迹；
  - **极致隐私脱敏引擎 (DiagnosticSanitizer)**：深度借鉴 PiliPalaZ 规范，保留 Host 与安全结构化参数，对 URL 搜索词（`q`, `wd`, `keyword` 等）、鉴权 Token（`token`, `auth`, `session`, `cookie` 等）、用户标识以及系统私有沙箱路径（`/data/user/0/...`）实施绝对清洗，坚决杜绝隐私泄露；
  - **用户完全知情与自决掌控 (Transparency & Review UX)**：设置页新增「本地诊断与日志」卡片，透明感知本地存储占用（如 `48 KiB / 2.0 MiB`，最长保留 7 天，自动 FIFO 衰减）；导出前弹出纯文本审阅弹窗（`DiagnosticReviewDialog`），用户逐行检视 Markdown 明文，绝不静默后台上传；设备兼容信息（机型、系统版本、WebView 内核）默认非选中（Opt-in），由用户自选附加；提供一键复制、系统分享与前往 GitHub 反馈。

### Added
- **“下一页恶意掉包 / 广告洗流量”四位一体纵深防御体系**：
  - **DOM 翻页脱敏器 (`pagination_sanitizer.js`)**：在捕获阶段拦截并剥离翻页按钮上的恶意 `touchstart`/内联 `onclick` 广告替换；正文与评论区硬排除；对 `/123/45.html` 自动推导并挂载下一页自愈链接；
  - **内核 PSL 四维豁免守卫 (`NavigationSecurityGuard.kt`)**：支持多级顶级域名（如 `.com.cn`, `.co.jp`）识别，建立 1500ms 翻页点击时间窗与广告指纹防御上下文，严防全网误杀；搜索引擎、社交登录与移动支付免检放行；单次消费即焚 Token；
  - **智能反解直达器 (`UrlTargetUnwrapper.kt`)**：智能反解中间跳转页与 Base64 真实目标链接，限制最大深度防死循环，依据 W3C 规范安全降级 Referer（跨域截断至 Origin，HTTPS->HTTP 严禁降级）；
  - **小窗极简微胶囊与自愈交互**：悬浮窗拦截伪装外链时顶栏微胶囊变异为 `[🛡️ 已拦截 | ↻ 重试 | ↗ 放行]`，极小尺寸自适应隐藏右侧按钮防挤压，自动直达绿光反馈，采用 In-Window 卡片彻底杜绝 `BadTokenException`。
- **页面关闭 0ms 瞬间响应性能优化**：
  - 彻底消灭悬浮窗叉掉页面时的卡顿感：关闭按钮触控热区由 28dp 扩充至 36dp；点击首行立即触发 `window.visibility = View.GONE` 实现 0ms 视觉消隐；移除高延迟的 `loadUrl("about:blank")` 异步 IPC 冲突；后台异步平滑释放 WebView 与 Session 资源。
- **元素标记器 (Element Zapper) 与隐形点击劫持深度加固**：
  - Zapper 激活时开启原生内核级「全局导航冻结 (Navigation Freeze)」，坚决阻断选元素时的流氓跳转；
  - 注入 Iframe 代理盾，支持跨域 `<iframe>` 广告容器直接选取消除；
  - 严密拦截全屏透明点击劫持蒙版（Invisible Full-Screen Click Trap）与非法后台弹窗。

## [0.1.3-beta] - 2026-10-14

### Added
- **悬浮视频全屏纯净沉浸**：彻底移除全屏视频上方常驻的半透明胶囊顶条（`videoFullscreenBar`），呈现 100% 满屏无遮挡的纯净视区；
- **全屏边框拖拽移动手势**：全屏视频模式下支持通过顶边及左右侧边（16dp）拖拽移动悬浮窗，底部边缘不拦截以保护播放器进度条操控；
- **多层级退出全屏与尺寸自由缩放**：保留右下角 36dp 缩放手柄，支持依靠播放器原生退出按钮及系统返回键（最高优先级分发）安全平滑退出。

### Fixed
- **在线检查更新 404 升级查不到**：修复 `AppUpdateManager` 中 GitHub 仓库所有者指向 `gxwane/floatbro`，彻底解决检查更新请求 404 的问题；
- **设置页关于版本显示同步**：设置页「关于」卡片动态绑定 `BuildConfig.VERSION_NAME`，与应用实际构建版本保持 100% 严格一致。

## [0.1.2-beta] - 2026-10-14

### Added
- **轻量网页元素标记消除器 (Element Zapper)**：移动小窗专属触控拾取交互，支持「扩大」与「缩小」DOM 层级栈导航回退，提供「临时消除」与「永久屏蔽」；
- **微型紧凑顶栏与极限尺寸缩放**：纵向最小高度下调至 160dp（横屏 140dp），顶栏 28dp 紧凑按钮集群释放 80dp+ 地址栏显示空间，极限尺寸下地址栏文字（11sp）清晰居中展示，彻底杜绝空白药丸圈；
- **统一二级操作菜单**：悬浮窗与应用内标签页导航栏统一配备常驻 `[⋮ 更多]` 菜单，平稳收敛刷新与标记广告入口；
- **工业级移动规则资产与规则包管理面板**：提供纯净轻量的「规则管理」面板，引入「基础过滤规则 (EasyList)」、「中文增强规则 (EasyList China + anti-AD)」与「隐私防追踪 (EasyPrivacy)」三大模块化离线规则包，支持独立热启停；
- **多语言文档与实机展示资源刷新**：提供中英文双语官方文档 ([README.md](README.md) 与 [README_EN.md](README_EN.md))，录入真实设备实测单浮窗、多浮窗同屏与贴边气泡展示截图。

### Fixed
- **广告规则持久化刷新失效彻底修复**：修复原生 C++ 匹配引擎（`ad_block_client.cc`）在处理纯自定义规则时尾随逗号导致 CSS 语法解析作废丢弃的根因，加入 Kotlin 样式表严格清洗与主域归一化，确保页面重载后自定义规则 100% 生效；
- **剪贴板智能防骚扰**：采用 `(URL + 时间戳)` 双元组事件流持久化抑制，关闭后新建标签页不再反复弹出，重新复制灵敏感知；
- **广告拦截设置持久化与菜单状态机纠偏**：修复设置页广告拦截开关与 `BrowserPreferences` 及 `AdFilter` 内核的双向持久化断链；彻底修复小窗菜单中全局关闭被误显为“已加白名单”及点击死循环的问题。

### Changed
- **标记消除交互去噪**：精炼 Toast 提示为纯粹的“已永久屏蔽”，轻点 `[✕]` 取消退出实现 100% 静默无弹窗打扰；
- **设置页极简去噪**：设置项正式重命名为「广告拦截」，彻底清除所有说教式副标题与微文案小字；
- **主页视觉净化与权限提示精简**：移除主页常驻的悬浮窗权限提示卡片，消除对极简浏览界面的干扰；悬浮窗权限弹窗文案大幅精简，直击“开启后支持悬浮浏览与贴边气泡”核心价值。

---

## [0.1.1-beta] - 2026-10-13

### Fixed
- **去广告规则净化**：移除过度泛化的子串选择器与未限定域名的全局样式隐藏规则，彻底解除对哔哩哔哩 (m.bilibili.com) 等主流站点顶层容器的误杀，恢复正常首屏渲染；
- **手势防误触优化**：移除将悬浮窗向上拖动直接收回为标签页的误触手势，改为标准的平滑贴顶停靠；标签页收回操作统一由工具栏“收回”按钮触发；
- **HTML5 视频全屏多任务控制**：网页视频进入全屏播放时，顶部浮现半透明居中悬浮控制条（支持拖动窗口与一键退出全屏），同时保持右下角缩放手柄常驻顶层，实现全屏视频小窗下自由拖拽与缩放。

### Changed
- **浏览历史响应式流同步**：引入 `StateFlow` 响应式数据流，浮窗关闭或网页跳转后，主控中心浏览历史记录卡片实现秒级自动刷新。

### Security
- **正式 Release 签名**：全面启用专有 Release 签名私钥，配置 GitHub Secrets 自动化流水线签名与 16KB Page Size 内存对齐检验，告别 Debug 证书；
- **分支安全保护**：GitHub 远端仓库启用 `main` 主分支保护策略，禁止强制推送与意外删除。

---

## [0.1.0-beta] - 2026-10-13

### Added
- **系统级独立悬浮窗体系**：基于 `SYSTEM_ALERT_WINDOW` 与前台服务构建独立悬浮窗宿主，摆脱主应用全屏外壳限制；
- **多任务独立悬浮窗**：支持多浮窗同屏独立并存与多气泡并发挂载；
- **手势交互与贴边动效**：实现右下角专属缩放手柄、贴边减速吸附、贴边磁吸小气泡折叠与秒级复原，以及屏幕底部拖拽销毁靶心；
- **原生 C++ 去广告内核**：集成 BloomFilter C++ 规则匹配引擎，支持 EasyList 与 Chromium CSS `:has()` 伪类元素隐藏，全架构强制执行 Android 15 16KB Page Size 内存对齐；
- **现代 Material 3 体验**：全套深浅双模主题，重构搜索主控中心，内嵌 Compose 浮层杜绝后台悬浮环境下的崩溃；
- **多级返回键调度**：`BackNavigationDispatcher` 纯领域决策器，实现“视频退出全屏 -> 网页历史后退 -> 智能最小化气泡”多级返回分发；
- **生产级构建工程化**：建立 R8 混淆规则、16KB 内存对齐校验流水线与全自动化 GitHub Actions 发布体系。

[Unreleased]: https://github.com/gxwane/floatbro/compare/v0.1.3-beta...HEAD
[0.1.3-beta]: https://github.com/gxwane/floatbro/compare/v0.1.2-beta...v0.1.3-beta
[0.1.2-beta]: https://github.com/gxwane/floatbro/compare/v0.1.1-beta...v0.1.2-beta
[0.1.1-beta]: https://github.com/gxwane/floatbro/compare/v0.1.0-beta...v0.1.1-beta
[0.1.0-beta]: https://github.com/gxwane/floatbro/releases/tag/v0.1.0-beta

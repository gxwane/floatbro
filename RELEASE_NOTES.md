# 🚀 FloatBro v0.1.5-beta

FloatBro v0.1.5-beta 正式引入了**统一本地日志与诊断子系统 (Unified Local Diagnostics & Logging Subsystem)**、**广告拦截规则演进与误杀溯源足迹 (AdFilter Telemetry)**，并以 **PiliPalaZ 级别隐私脱敏与透明审阅规范**保障用户数据安全与完全知情权。

---

### 🌟 本次关键功能与特性 (Changelog)

1. 🛡️ **统一本地诊断与致命崩溃急救 (Unified Diagnostics & Crash Recovery)**：
   - **全局 Timber 树无缝接管 (`DiagnosticTimberTree`)**：全工程 Timber 调用在 Debug/Release 双模式下均统一归流至本地环形缓冲，彻底终结 Release 模式“两眼一抹黑”的排障痛点；
   - **致命崩溃临终急救 (`FloatBroCrashHandler`)**：捕获未捕获异常并实施零协程 Direct I/O 原子同步刷盘（Emergency Flush），即便 UI Looper 挂起或进程即将被系统强杀，也能确保临终致命堆栈与运行现场 100% 留存；
   - **全景运行态轨迹记录**：实时记录 FloatBrowserService 悬浮窗生命周期、层级 Z-Order、聚焦抢占互斥与拖拽手势轨迹；捕捉 Chromium WebView 渲染进程崩溃（精准区分 CPU Crash 与 OOM 强杀）及致命网络异常。

2. 🔍 **广告拦截规则演进与误杀溯源 (AdFilter Telemetry & Rule Evolution)**：
   - **低开销 SPI 遥测桥接 (`AdFilterTelemetryBridge`)**：通过 SPI 接口解耦挂载到广告过滤内核，在亚毫秒级内完成匹配遥测事件分发；
   - **关键事件差分采样**：默认屏蔽高频常规放行 (`PASSED`) 请求，重点沉淀阻断 (`BLOCKED`) 与白名单放行 (`ALLOWED_BY_EXCEPTION`) 关键记录，完整保留主站、资源请求域名、资源类型与命中规则明文；
   - **悬浮窗二级菜单「页面拦截诊断」**：浏览任意网页时，可一键呼出当前页面拦截诊断弹窗并快速复制结构化脱敏足迹，为后续广告规则演进与误杀排查提供精准依据。

3. 🔒 **PiliPalaZ 级别隐私脱敏引擎 (Zero-Leakage DiagnosticSanitizer)**：
   - **严苛隐私清洗**：对所有日志与遥测中的 URL Query 敏感参数（如搜索词 `q`, `wd`, `keyword`、鉴权 `token`, `auth`, `session`, `cookie`、手机号、邮箱等）实施结构化剥离，仅保留 Host 与安全路由；
   - **系统私有沙箱路径抹平**：全面将 `/data/user/0/...` 替换为安全别名 `[APP_SANDBOX]`，杜绝任何本地账户或目录泄露。

4. 📋 **用户完全知情与自决掌控交互 (Transparency & Review UX)**：
   - **设置页容量透明感知**：新增「本地诊断与日志」卡片，实时展示诊断文件占用大小（如 `48 KiB / 2.0 MiB`，最多保留 7 天，FIFO 自动循环淘汰）；
   - **导出前纯文本逐行审阅 (`DiagnosticReviewDialog`)**：支持在分享或复制前，通过纯文本 Markdown 对话框逐行审阅日志明文，保障用户 100% 知情，坚决杜绝后台静默上传；
   - **设备信息默认非选中 (Opt-in)**：机型、Android 系统版本、Chromium 内核版本等硬件环境信息默认不勾选，由用户按需自决附加；
   - **多元导出闭环**：支持一键复制到剪贴板、系统原生分享面板、日志清空以及直达 GitHub Issues 快速提交反馈。

---

> 欢迎在 [GitHub Issues](https://github.com/gxwane/floatbro/issues) 或 [GitHub Discussions](https://github.com/gxwane/floatbro/discussions) 反馈交流您的宝贵体验！

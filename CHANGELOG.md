# 变更日志 (Changelog)

本项目所有显著变更均记录于此文件。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，并遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- **多语言文档支持**：提供中英文双语官方文档 ([README.md](README.md) 与 [README_EN.md](README_EN.md))；
- **实机展示资源刷新**：录入真实设备实测单浮窗、多浮窗同屏与贴边气泡展示截图。

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

[Unreleased]: https://github.com/gxwane/floatbro/compare/v0.1.1-beta...HEAD
[0.1.1-beta]: https://github.com/gxwane/floatbro/compare/v0.1.0-beta...v0.1.1-beta
[0.1.0-beta]: https://github.com/gxwane/floatbro/releases/tag/v0.1.0-beta

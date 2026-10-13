# FloatBro (浮窗兄弟)

> **下一代 Android 网页级原生全局悬浮、Windows Snap 式联动真分屏、Brave 级强力去广告的高效多任务浏览器**

[![Latest Release](https://img.shields.io/github/v/release/gxwane/floatbro?include_prereleases&color=orange&label=Latest%20Release)](https://github.com/gxwane/floatbro/releases)
[![Build Status](https://github.com/gxwane/floatbro/actions/workflows/pr-checks.yml/badge.svg)](https://github.com/gxwane/floatbro/actions)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android MinSdk](https://img.shields.io/badge/minSdk-26-green.svg)](https://developer.android.com/about/versions/oreo)
[![TargetSdk](https://img.shields.io/badge/targetSdk-36-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg)](https://developer.android.com/compose)
[![16KB Page Size](https://img.shields.io/badge/Android%2015-16KB%20Aligned-success.svg)](https://developer.android.com/guide/practices/page-sizes)

---

## ⚖️ 开源协议与致敬基石 (License & Attribution)

**FloatBro** 是一个遵循 **[GNU General Public License v3.0 (GPL-3.0-or-later)](LICENSE)** 的自由开源软件。

本项目是在杰出的开源墨水屏浏览器项目 **[EinkBro](https://github.com/plateaukao/einkbro)**（原作者：**[Plateau Kao](https://github.com/plateaukao)**）的基础上深度二次开发与演进而来。FloatBro 继承了 EinkBro 优秀的去广告内核与无缝分屏雏形，并针对通用 Android 智能机场景实现了突破性的升维重构：
* **网页级原生全局悬浮**：基于 `SYSTEM_ALERT_WINDOW` 与前台服务，实现无主界面包袱的全局置顶浮窗与贴边气泡；
* **Windows Snap 级真分屏**：将原有的从属翻译副屏升级为对等独立的双视口，支持中线无极拖拽重排与跨屏打开；
* **现代全彩 Material 3 体验**：彻底解耦黑白墨水屏刷新限制，为 120Hz 高刷屏打造现代 Compose 浮层交互；
* **Android 15/16 严苛对齐**：原生支持 16KB 内存分页对齐与 64 位高效运行。

我们向原作者 **Plateau Kao** 以及 EinkBro 社区的所有贡献者致以最崇高的敬意与感谢！

> **免责声明**：FloatBro 是一个独立的衍生项目，由 FloatBro 社区独立维护与演进，未经原作者官方背书，亦不代表 EinkBro 官方立场。

---

## 📥 下载体验 (Download)

最新版本安装包可在 GitHub Releases 页面直接下载体验：

* 🚀 **[前往 GitHub Releases 下载最新安装包](https://github.com/gxwane/floatbro/releases)**
* 📦 **包体积**：约 2.6 MB（C++ 原生优化，极致轻量）
* 🛡️ **安全保证**：全构架支持 Android 8.0 ~ Android 16，具备官方正式密钥签名，严格通过 Android 15 16KB Page Size 内存对齐检验。

---

## 🌟 核心三角特性

```
                    ┌──────────────────────────────────────────────┐
                    │               FloatBro 核心三角               │
                    └──────────────────────────────────────────────┘
                                           ▲
                                          / \
                                         /   \
                                        /     \
    【网页级原生悬浮】 ◄────────────────────────────────────────► 【Windows Snap 联动真分屏】
   (SYSTEM_ALERT_WINDOW)                                       (中线拖拽/比例自适应重排)
   • 零主应用外壳包袱                                            • 双独立对等视口
   • 自由手势无级缩放                                            • 左右/上下自由切换
   • 贴边收纳独立气泡                                            • 拒绝散装拼图
                                       \     /
                                        \   /
                                         \ /
                                          ▼
                             【Brave/uBlock 级纯净去广告】
                              (:adblock-client C++ 内核)
                              • EasyList / uBlock 规则
                              • 原生 CSS :has() 元素隐藏
                              • 彻底消灭留白与反屏蔽脚本
```

1. **网页即浮窗（零主界面包袱）**：
   - 告别笨重的全屏浏览器外壳，点击直接浮起；
   - 右下角专属拖拽抓手（Resize Handle），手指自由缩放尺寸与宽高比；
   - 贴边自动收缩为 48dp 磁吸小气泡（Floating Bubble），秒级原地复原网页。
2. **Windows Snap 联动真分屏**：
   - 双独立对等视口（各持独立 Tab 管理器与前进后退历史栈）；
   - 可拖拽中线分割条，实时 15%~85% 比例动态分配；
   - 支持主屏长按超链接“在对侧视口打开”；支持横屏（左右）与竖屏（上下）一键翻转。
3. **Brave 级强力去广告**：
   - 纯 C++ 算法引擎（`:adblock-client`），BloomFilter 亚毫秒级规则匹配；
   - 原生支持 Chromium CSS `:has()` 伪类元素隐藏与防反屏蔽脚本，告别白块破图。

---

## 🏗️ 工程多模块架构

```text
FloatBro/
├── app/                  # [应用模块] 悬浮窗宿主 (FloatBrowserService)、Bubble 气泡、内嵌 Compose 浮层
├── core/
│   ├── webview/          # [核心库] 对称双屏容器 (TwoPaneLayout)、EBWebView 封装、焦点桥接
│   └── common/           # [通用库] Material 3 基础 Token、DisplayUtils 边界测量、DataStore 配置
├── ad-filter/            # [拦截调度] EasyList 规则解析、CSS :has 注入、URL 拦截器 (Kotlin)
├── adblock-client/       # [C++ 内核] BloomFilter 算法、libadblock-client.so、16KB 分页对齐 (CMake)
└── gradle/
    └── libs.versions.toml # 统一依赖版本字典 (Version Catalog)
```

---

## 📚 核心文档索引

* [开源许可证 (GPL-3.0)](LICENSE)：GNU General Public License v3.0 协议全文；
* [变更日志](CHANGELOG.md)：版本演进与发布记录（Keep a Changelog 规范）；
* [第三方许可证声明](NOTICE)：开源许可证与原作者致敬溯源信息。


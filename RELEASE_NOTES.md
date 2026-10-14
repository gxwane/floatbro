# 🚀 FloatBro v0.1.4-beta

FloatBro v0.1.4-beta 带来了业内首创的**“下一页恶意掉包 / 广告洗流量”四位一体纵深防御体系**，并全面实现了**页面关闭 0ms 瞬间响应性能优化**以及**元素标记器 (Element Zapper) 导航冻结加固**。

---

### 🌟 本次关键功能与修复 (Changelog)

1. 🛡️ **“下一页变广告 / 业务流掉包”四位一体纵深防御体系 (Next-Page Hijack Defense)**：
   - **前端 DOM 翻页脱敏器 (`pagination_sanitizer.js`)**：在捕获期事件监听中彻底抹平流氓站长内联挂载在翻页按钮上的 `touchstart`/`onclick` 广告替换；精确硬排除正文与评论区；对形如 `/book/123/45.html` 的章节自动外推并挂载真实下一页自愈链接；
   - **内核 PSL 四维豁免守卫 (`NavigationSecurityGuard.kt`)**：支持识别 `.com.cn`、`.co.jp`、`.edu.hk` 等多级复合顶级域名；建立 1500ms 翻页点击时间窗与广告指纹防御上下文，严防全网通用浏览误杀；对百度、必应、谷歌等搜索引擎跳转、OAuth 社交登录与移动支付免检通畅放行；提供单次消费即焚的 BypassToken；
   - **智能反解直达器 (`UrlTargetUnwrapper.kt`)**：深度限制 $\le 2$ 防死循环重定向风暴，智能识别嵌套或 Base64 编码的真实下一页目标链接；遵循 W3C 规范安全处理 Referer（跨域截断至 Origin，严禁 HTTPS 向 HTTP 泄露）；
   - **悬浮小窗极简微胶囊与自愈闭环**：拦截到伪装广告时，顶栏药丸变异为 `[🛡️ 已拦截 | ↻ 重试 | ↗ 放行]`；窗口极小（< 300dp）时自动进入紧凑模式隐藏右侧多余图标防排版挤压；自动直达时展示微绿色光效反馈；放行确认卡片采用 In-Window 原生卡片覆盖，彻底杜绝 `WindowManager.BadTokenException`。

2. ⚡ **页面关闭 0ms 瞬间响应优化 (Instant Dismiss Performance)**：
   - 彻底修复关闭浮窗速度偏慢的问题：关闭按钮有效触控热区由 28dp 扩充至 36dp，告别点不准、偏离误触；
   - 触控触发瞬间立即执行 `window.visibility = View.GONE`，视觉上实现 0ms 瞬间消隐；
   - 剔除高延迟、易引发 IPC 导航竞争的 `loadUrl("about:blank")`，改由后台异步平滑释放 WebView、Cookie 与 Session 资源。

3. 🎯 **元素标记器 (Element Zapper) 导航冻结加固**：
   - 标记消除广告期间在原生内核层实施「全局导航冻结 (Navigation Freeze)」，彻底阻断点击图片广告时的流氓跳转；
   - 注入 Iframe 代理盾，支持一键点选消除跨域 `<iframe>` 广告容器；
   - 强力中和全屏透明蒙版（Invisible Full-Screen Click Trap）与非法后台弹窗。

---

> 欢迎在 [GitHub Issues](https://github.com/gxwane/floatbro/issues) 或 [GitHub Discussions](https://github.com/gxwane/floatbro/discussions) 反馈交流您的宝贵体验！

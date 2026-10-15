# FloatBro UI/UX 设计系统规范 (Design System & Tokens)

> **版本**：v1.0.0 (2026-10-15)  
> **设计哲学**：**Calm Productivity UI（克制、精准的高效工具风）**  
> **核心架构**：**Material 3 骨架 + Desktop-like 悬浮多视口 + 软阴影层级约束**  
> **定位宗旨**：零外壳包袱，网页即浮窗（Zero-Shell Overhead, Floating-First）

---

## 目录
1. [设计哲学与核心心智模型](#1-设计哲学与核心心智模型)
2. [色彩系统与语义规则 (Color Tokens)](#2-色彩系统与语义规则-color-tokens)
3. [三级圆角规范 (Radius Scale)](#3-三级圆角规范-radius-scale)
4. [三级光影与层级规范 (Elevation & Shadow Tokens)](#4-三级光影与层级规范-elevation--shadow-tokens)
5. [空间栅格与无障碍规范 (Spacing & Accessibility)](#5-空间栅格与无障碍规范-spacing--accessibility)
6. [字体排印阶梯 (Typography Scale)](#6-字体排印阶梯-typography-scale)
7. [悬浮视窗规范与 Affordance 设计 (Floating Windowing)](#7-悬浮视窗规范与-affordance-设计-floating-windowing)
8. [贴边气泡与手势物理规范 (Bubble & Physics)](#8-贴边气泡与手势物理规范-bubble--physics)
9. [主界面工作台化架构 (Workspace Console)](#9-主界面工作台化架构-workspace-console)

---

## 1. 设计哲学与核心心智模型

### 1.1 核心原则 (Design Principles)
* **克制高于华丽 (Restraint over Decoration)**：坚决抵制无意义的渐变、荧光、糖果色与过度动画，绝不破坏专业沉浸感；
* **信息层级自解释 (Self-Explanatory Affordance)**：所有可拖拽、可缩放、可折叠、可关闭的区域，必须具备清晰自然的视觉线索（Visual Cues），杜绝盲操；
* **工作流导向而非功能罗列 (Workflow over Feature Lists)**：从“我们支持什么功能”转变为“如何辅助用户快速处理多任务网页上下文”；
* **视口利用率极致化 (Viewport Efficiency)**：移动端屏幕物理空间寸土寸金，标题栏与控件必须高度克制，将 92%+ 的视区完整归还给网页内容。

---

## 2. 色彩系统与语义规则 (Color Tokens)

严格遵循 **60-30-10 空间分配法则**：

```
┌─────────────────────────────────────────────────────────────┐
│ 60% 基础底色 (Neutral Base)                                   │
│ • 浅色模式: 纯白 #FFFFFF + Slate-50 (#F8FAFC)                 │
│ • 深色模式: Slate-900 (#0F172A) + Slate-800 (#1E293B)        │
├─────────────────────────────────────────────────────────────┤
│ 30% 结构层与排印文字 (Slate Hierarchy)                        │
│ • 主标题 / 强文本: Slate-900 (浅) / Slate-50 (深)             │
│ • 正文内容: Slate-700 (浅) / Slate-200 (深)                  │
│ • 次要 / 辅助文本: Slate-500 (浅) / Slate-400 (深)            │
│ • 结构分割线 / 描边: Slate-200 (浅) / Slate-700 (深)          │
├─────────────────────────────────────────────────────────────┤
│ 10% 品牌核心色与状态反馈 (Accent & Semantic)                  │
│ • 主品牌强调色 (Brand Blue): #2563EB (浅) / #60A5FA (深)      │
│ • 辅助安全色 (Emerald Green): #10B981                         │
│ • 警示危险色 (Destructive Red): #EF4444                       │
└─────────────────────────────────────────────────────────────┘
```

### 2.1 颜色令牌对照表

| Token 名称 | 浅色模式 (Light) | 深色模式 (Dark) | 典型应用场景 |
| :--- | :--- | :--- | :--- |
| `colorBackground` | `#FFFFFF` | `#0F172A` (Slate-900) | 基础应用底层底色 |
| `colorSurface` | `#F8FAFC` (Slate-50) | `#1E293B` (Slate-800) | 卡片容器底色、悬浮窗底色 |
| `colorSurfaceElevated`| `#FFFFFF` | `#334155` (Slate-700) | 浮起的二级对话框、输入胶囊 |
| `colorPrimary` | `#2563EB` (Blue-600) | `#60A5FA` (Blue-400) | 活动边框、主按钮、进度条 |
| `colorPrimaryContainer`| `#DBEAFE` (Blue-100)| `#1E3A8A` (Blue-900) | 选中态胶囊、提示条底色 |
| `colorTextPrimary` | `#0F172A` (Slate-900)| `#F8FAFC` (Slate-50) | 主标题、关键数据 |
| `colorTextSecondary`| `#475569` (Slate-600)| `#CBD5E1` (Slate-300)| 描述正文、说明文字 |
| `colorTextTertiary` | `#94A3B8` (Slate-400)| `#64748B` (Slate-500)| 时间戳、占位符、小标 |
| `colorOutline` | `#E2E8F0` (Slate-200)| `#334155` (Slate-700)| 卡片边框、静止态窗口边框 |
| `colorDestructive` | `#EF4444` (Red-500) | `#F87171` (Red-400) | 底部销毁靶心、清除数据警告 |

---

## 3. 三级圆角规范 (Radius Scale)

彻底废除 10dp、14dp、18dp 等任意游离值，全项目收敛为 **3 级圆角体系 + 2 种特定形态**：

```
[  8dp  ] 小型组件 (Buttons, Tags, Badges)
[ 12dp  ] 交互与输入容器 (Input Fields, Cards, List Items)
[ 16dp  ] 视窗与主面板 (Floating Windows, Modals, Bottom Sheets)
( Pill  ) 完全胶囊 (Omnibar 17dp, Home Search 24dp)
( Circle) 纯圆几何 (Floating Bubble 52dp OVAL, Dismiss Target 64dp OVAL)
```

1. **`Radius.Small (8dp)`**：
   - 次级操作按钮（FilterChip、Tag 标签、微型图标底板）；
2. **`Radius.Medium (12dp)`**：
   - 常用输入框、设置项卡片、历史记录列表项、QuickDial 图标容器；
3. **`Radius.Large (16dp)`**：
   - 核心悬浮窗外壳、工作台大卡片、系统级对话框、BottomSheet 顶角；
4. **胶囊态 (`Full Capsule / Pill`)**：
   - 悬浮窗 Omnibar 胶囊（高 34dp，圆角 17dp）；
   - 主页搜索胶囊（高 48dp，圆角 24dp）；
5. **绝对纯圆 (`Circular / 52dp Oval`)**：
   - 贴边悬浮气泡（`FloatingBubbleView`）。

---

## 4. 三级光影与层级规范 (Elevation & Shadow Tokens)

坚决杜绝软塌塌的 Neumorphism（新拟态），仅采用克制的物理软阴影划分 Z 轴深度：

| 光影层级 | 投影高度 (Elevation) | 环境光透明度 | 适用对象与状态 |
| :--- | :--- | :--- | :--- |
| **Level 1 (Base)** | `0dp ~ 2dp` | `0.04 ~ 0.06` | 主界面扁平卡片、分割底槽、静止列表项 |
| **Level 2 (Float)**| `6dp ~ 8dp` | `0.10 ~ 0.12` | **静止态独立悬浮窗**、贴边气泡、弹出菜单（Dropdown） |
| **Level 3 (Focus)**| `12dp ~ 16dp`| `0.16 ~ 0.20` | **激活/聚焦悬浮窗**、正在被拖动的标签页、对话框 |

---

## 5. 空间栅格与无障碍规范 (Spacing & Accessibility)

### 5.1 8-Point Grid 间距规范
所有 Layout 的 `padding`、`margin`、`gap` 必须可被 4 或 8 整除：
* `Space.xxs` = **4dp** (微型元素间距、图标文字间距)
* `Space.xs`  = **8dp** (标准间距基准、组件内部小内边距)
* `Space.sm`  = **12dp** (卡片内部文本垂直间距、小列表间距)
* `Space.md`  = **16dp** (标准外边距、卡片主内边距)
* `Space.lg`  = **24dp** (模块分组间距、标题栏上下间距)
* `Space.xl`  = **32dp** (大型工作区区块间隔)

### 5.2 触摸目标无障碍约束 (Touch Targets)
* **`MinTouchTarget = 48.dp`**：所有交互控件（即使图标本体仅为 18dp~20dp），点击感应外包围矩形不得低于 48×48dp；
* **`ToolbarButtonTouchTarget = 48.dp`**：顶栏操作集群按钮强制分配 48dp 触摸包裹盒，杜绝防误触与多次漏触。

---

## 6. 字体排印阶梯 (Typography Scale)

| 类别 | 规格 (Size / LineHeight) | 字重 (Weight) | 字体类型 | 用途 |
| :--- | :--- | :--- | :--- | :--- |
| **Headline** | `18sp / 24sp` | SemiBold (600) | System Sans | 二级页面大标题、弹窗主提问 |
| **TitleMedium** | `15sp / 20sp` | SemiBold (600) | System Sans | 模块卡片标题、列表组标题 |
| **BodyLarge** | `14sp / 20sp` | Regular (400) | System Sans | 正文内容、设置项主文案 |
| **BodyMedium**| `12sp / 16sp` | Regular (400) | System Sans | 次级说明文本、时间戳、快捷标 |
| **Code/URL**  | `11.5sp / 16sp`| Medium (500) | **Monospace** | Omnibar 地址显示、诊断参数 |

---

## 7. 悬浮视窗规范与 Affordance 设计 (Floating Windowing)

为解决“窗口感弱、工具栏感过重”的现有缺陷，对悬浮窗顶部进行系统级重构：

### 7.1 强标题栏化 Header 架构 (40dp ~ 42dp)

```
┌─────────────────────────────────────────────────────────────┐
│ [←] [ 🔒 example.com/doc...  ]    [—] [⋮] [✕] │
└─────────────────────────────────────────────────────────────┘
  ▲              ▲                    ▲   ▲   ▲
  │              │                    │   │   └─ 关闭窗口 (36dp 热区)
  │              │                    │   └───── 更多折叠菜单 (刷新/分屏/工具)
  │              │                    └───────── 最小化收拢为贴边气泡
  │              └─ 紧凑胶囊地址栏 (可拖拽窗口区域 + 单击编辑)
  └─ 智能后退 / Favicon 状态徽标
```

* **顶部拖拽握手线 (Drag Grip Indicator)**：
  - 在 Header 正中央顶缘绘制一条轻量的握手线（宽 32dp，高 2dp，圆角 1dp，中性 Slate-300），直观暗示“按住此处可任意移动视窗”；
* **收敛二级操作 (More Overflow Menu)**：
  - 将非高频操作（强制刷新、分屏对开、切换桌面 UA、拦截诊断、复制链接）全数收敛至 `[⋮ 更多]` 下拉浮层中，杜绝顶栏小图标拥挤；
* **缩放手柄明晰化 (Clear Resize Grip)**：
  - 右下角缩放区域从单薄小斜线重构为具有明确触感的“高对比三角抓手”，配合手指触摸时的高亮微反馈。

---

## 8. 贴边气泡与手势物理规范 (Bubble & Physics)

### 8.1 气泡形态 (Floating Bubble)
* **规格**：`52dp` 纯圆（OVAL），浅色底搭配 `1.5dp BrandBlue` 外环，内嵌 28dp 网站 Favicon；
* **磁吸阻尼 (Magnetic Edge Docking)**：松手后基于屏幕左右两侧距离自适应计算，以 `DecelerateInterpolator` 贴合屏幕外边缘（保留 80% 露在屏幕内）；
* **多气泡层叠态 (Bubble Stack Badge)**：当屏幕存在多个折叠气泡时，边缘增加 `+N` 微型计数药丸，展现多任务上下文。

### 8.2 底部销毁靶心 (Dismiss Target)
* **规格**：屏幕底部居中 `64dp` 纯圆红晕靶心（`#EF4444`，带柔和径向渐变与垃圾桶图标）；
* **反馈机制**：
  - 气泡拖入吸附感应区（半径 80dp 内）时，触发设备触感轻微震动（`HapticFeedbackConstants.KEYBOARD_TAP`）；
  - 气泡自身缩放至 `0.82f`，释放即可瞬间完成安全销毁。

---

## 9. 主界面工作台化架构 (Workspace Console)

将 `LauncherActivity` 从垂直线性功能堆叠升级为**三层任务工作台结构**：

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 活跃任务与工作区看板 (Active Tasks & Workspace)            │
│    • 当前活跃中的悬浮视窗预览 (多窗口缩略卡片)                 │
│    • 贴边气泡状态与一键恢复工作区                              │
├─────────────────────────────────────────────────────────────┤
│ 2. 核心工作流与快速创建 (Action Hub)                        │
│    • 居中智能搜索胶囊 (Thumb Zone 优化)                      │
│    • 剪贴板链接智能嗅探卡片 (免粘贴直达)                      │
│    • 常用生产力入口 (QuickDial 网格)                         │
├─────────────────────────────────────────────────────────────┤
│ 3. 系统工具与辅助偏好 (Preferences & Utilities)              │
│    • 广告过滤规则引擎热启停面板                               │
│    • 历史记录与上下文存储                                    │
│    • 本地脱敏日志与诊断反馈                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 10. 落地实施路线图 (Implementation Roadmap)

1. **第一阶段 (Tokens 统一)**：重构 [`Theme.kt`](file:///E:/Documents/floatbro/app/src/main/java/io/github/floatbro/ui/theme/Theme.kt)，定义不可篡改的 `FloatBroRadius`、`FloatBroElevation`、`FloatBroSpacing`，全局替换离散硬编码数值；
2. **第二阶段 (悬浮窗标题栏重构)**：按“强标题化”规范重构 [`FloatingWindowView.kt`](file:///E:/Documents/floatbro/app/src/main/java/io/github/floatbro/overlay/FloatingWindowView.kt) 顶栏与右下角缩放手柄，强化拖拽与缩放的视觉自解释性；
3. **第三阶段 (主界面工作台重构)**：在 [`LauncherActivity.kt`](file:///E:/Documents/floatbro/app/src/main/java/io/github/floatbro/ui/LauncherActivity.kt) 中引入“活跃会话与工作区”看板，彻底实现工作流导向。

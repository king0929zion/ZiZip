# ZiZip Notion 风格设计规范

## 🎨 设计理念

ZiZip 采用 Notion 风格的极简主义设计语言，强调：
- **内容优先** - 让对话内容成为焦点
- **留白艺术** - 大量使用白色空间
- **排版至上** - 通过字体层次传达信息结构
- **克制用色** - 仅在关键操作使用强调色

---

## 🎯 色彩系统

### 基础色板

```
背景色 (Backgrounds)
├── 页面背景      #FFFFFF (纯白)
├── 卡片背景      #FFFFFF
├── 悬停背景      #F7F6F3 (温暖灰)
├── 选中背景      #E8F0FE (淡蓝)
└── 代码块背景    #F7F6F3

文字色 (Typography)
├── 主文字        #37352F (深棕黑)
├── 次要文字      #787774 (中灰)
├── 占位文字      #9B9A97 (浅灰)
└── 禁用文字      #CECFCD

边框色 (Borders)
├── 默认边框      #E9E9E7
├── 强调边框      #37352F
└── 分割线        #F0F0EE
```

### 强调色

```
品牌蓝 (Primary)
├── 默认         #2383E2
├── 悬停         #1B6FC2
└── 浅色背景     #E8F0FE

功能色
├── 成功色       #0F7B6C (青绿)
├── 警告色       #D9730D (橙色)
├── 错误色       #E03E3E (红色)
└── 信息色       #6940A5 (紫色)
```

---

## 📝 字体系统

### 字体家族

```kotlin
// 中文优先
val NotionFontFamily = "Inter, -apple-system, BlinkMacSystemFont, 
    'PingFang SC', 'Noto Sans SC', 'Microsoft YaHei', sans-serif"
```

### 字号层级

| 层级 | 大小 | 行高 | 字重 | 用途 |
|------|------|------|------|------|
| H1 | 40px | 1.2 | 700 | 页面标题 |
| H2 | 30px | 1.3 | 600 | 区块标题 |
| H3 | 24px | 1.35 | 600 | 卡片标题 |
| Body | 16px | 1.5 | 400 | 正文内容 |
| Caption | 14px | 1.5 | 400 | 辅助文字 |
| Small | 12px | 1.4 | 500 | 标签/徽章 |

---

## 📐 间距系统

```
基础单位: 4px

间距层级:
├── xs    4px   (微小间距)
├── sm    8px   (紧凑间距)
├── md    16px  (标准间距)
├── lg    24px  (宽松间距)
├── xl    32px  (区块间距)
└── 2xl   48px  (页面边距)
```

---

## 🧩 组件设计

### 消息气泡

```
用户消息:
├── 背景: #F7F6F3
├── 文字: #37352F
├── 圆角: 8px
├── 内边距: 12px 16px
└── 最大宽度: 80%

AI 消息:
├── 背景: transparent (无背景)
├── 文字: #37352F
├── 左边距: 8px (暗示层级)
└── 通过间距分隔
```

### 输入框

```
外观:
├── 背景: #FFFFFF
├── 边框: 1px solid #E9E9E7
├── 圆角: 8px
├── 高度: 40px (单行) / 自适应 (多行)
├── 内边距: 10px 12px

状态:
├── 聚焦: border-color: #2383E2
├── 悬停: background: #F7F6F3
└── 禁用: opacity: 0.5
```

### 按钮

```
主要按钮 (Primary):
├── 背景: #2383E2
├── 文字: #FFFFFF
├── 圆角: 6px
├── 高度: 32px
├── 内边距: 0 12px
└── 悬停: background: #1B6FC2

次要按钮 (Secondary):
├── 背景: transparent
├── 文字: #37352F
├── 边框: 1px solid #E9E9E7
└── 悬停: background: #F7F6F3

文字按钮 (Text):
├── 背景: transparent
├── 文字: #787774
└── 悬停: background: #F7F6F3
```

### 工具选择器 (Notion Style)

```
斜杠命令面板:
├── 背景: #FFFFFF
├── 阴影: 0 0 0 1px rgba(0,0,0,0.05), 
│         0 3px 6px rgba(0,0,0,0.1)
├── 圆角: 8px
├── 宽度: 320px

选项项:
├── 高度: 40px
├── 图标: 24x24, 颜色 #787774
├── 文字: #37352F
├── 描述: #9B9A97
└── 悬停: background: #F7F6F3
```

---

## 🎭 图标设计

使用 Outline 风格图标，笔画宽度 1.5px

```
推荐图标集:
├── Heroicons (Outline)
├── Lucide Icons
└── Feather Icons

图标尺寸:
├── 小图标: 16x16 (导航、标签)
├── 中图标: 20x20 (按钮、列表)
├── 大图标: 24x24 (标题、空状态)
└── 特大图标: 32x32 (功能入口)
```

---

## 📱 页面布局

### 首页 (对话界面)

```
┌────────────────────────────────────┐
│  ←  ZiZip               ⋮  ⚙️     │  顶栏 44px
├────────────────────────────────────┤
│                                    │
│  Good morning 👋                   │  问候语
│                                    │
│  ┌─────────────────────────────┐  │
│  │ 📝 写一篇文章               │  │  快捷入口
│  └─────────────────────────────┘  │
│  ┌─────────────────────────────┐  │
│  │ 🤖 Agent 执行任务            │  │
│  └─────────────────────────────┘  │
│                                    │
│  ──────────────────────────────   │  分割线
│                                    │
│  最近对话                          │  区块标题
│                                    │
│  [对话列表...]                     │
│                                    │
├────────────────────────────────────┤
│  / 输入消息或命令...         📎 ➤ │  输入区
└────────────────────────────────────┘
```

### 设置页面

```
┌────────────────────────────────────┐
│  ←  设置                           │
├────────────────────────────────────┤
│                                    │
│  模型                              │  区块
│  ─────────────────────────────     │
│  ◉ 对话模型                    →   │
│  ◉ Agent 模型                  →   │
│  ◉ OCR 模型                    →   │
│                                    │
│  权限                              │
│  ─────────────────────────────     │
│  ✓ 无障碍服务                  →   │
│  ○ 悬浮窗权限                  →   │
│                                    │
│  关于                              │
│  ─────────────────────────────     │
│  版本 1.0.0                        │
│  GitHub                        →   │
│                                    │
└────────────────────────────────────┘
```

---

## ✨ 动效规范

### 过渡动画

```
时长:
├── 微交互: 100ms (悬停、点击)
├── 标准过渡: 200ms (面板展开)
└── 页面切换: 300ms

缓动函数:
├── 默认: cubic-bezier(0.4, 0, 0.2, 1)
├── 进入: cubic-bezier(0, 0, 0.2, 1)
└── 退出: cubic-bezier(0.4, 0, 1, 1)
```

### 微交互

- **按钮悬停**: 背景色渐变
- **列表项悬停**: 左侧显示淡色竖条
- **输入聚焦**: 边框颜色变化 + 微弱阴影
- **加载状态**: 使用骨架屏而非旋转器

---

## 🌙 深色模式

```
深色背景:
├── 页面背景      #191919
├── 卡片背景      #202020
├── 悬停背景      #2D2D2D
└── 边框          #373737

深色文字:
├── 主文字        #E6E6E6
├── 次要文字      #9B9B9B
└── 占位文字      #6B6B6B

强调色:
├── 蓝色         #529CCA (调亮)
└── 选中背景     rgba(82, 156, 202, 0.15)
```

---

## 📋 Kotlin 实现示例

```kotlin
object NotionTheme {
    // Colors
    val Background = Color(0xFFFFFFFF)
    val BackgroundHover = Color(0xFFF7F6F3)
    val TextPrimary = Color(0xFF37352F)
    val TextSecondary = Color(0xFF787774)
    val TextPlaceholder = Color(0xFF9B9A97)
    val Border = Color(0xFFE9E9E7)
    val Accent = Color(0xFF2383E2)
    val AccentLight = Color(0xFFE8F0FE)
    
    // Typography
    val BodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        color = TextPrimary
    )
    
    val TitleMedium = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
    
    // Shapes
    val SmallShape = RoundedCornerShape(4.dp)
    val MediumShape = RoundedCornerShape(6.dp)
    val LargeShape = RoundedCornerShape(8.dp)
}
```

---

这个设计规范确保 ZiZip 应用具有：
- 🪶 **轻盈感** - 浅色、无阴影、细边框
- 📖 **可读性** - 优秀的字体层次
- 🎯 **专注度** - 最少的视觉干扰
- 🔧 **工具感** - 专业、高效的界面

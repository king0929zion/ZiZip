# ZiZip - Auto-GLM Android Native Port

## 1. 项目概述

**项目名称**: ZiZip  
**目标**: 创建 Auto-GLM-Android Flutter 应用的原生 Android 版本（1:1 复刻）  
**技术栈**: Kotlin + Jetpack Compose (Material3)  
**核心功能**: 无障碍服务、悬浮窗、AI Agent 自动化

### 当前状态
- ✅ 项目脚手架完成
- ✅ 设计系统已实现（颜色、字体、组件）
- ✅ 数据层模型和仓库
- ✅ ModelProvider 接口和 Mock 实现
- ✅ 主页面（Gemini 风格聊天 UI）
- ✅ 设置和历史记录页面
- ✅ 无障碍服务（点击/滑动/输入）
- ✅ GitHub Actions CI/CD
- ⏳ 虚拟屏幕功能
- ⏳ 真实 API 集成

---

## 2. 项目架构

```
com.autoglm.android/
├── MainActivity.kt              # 应用入口
├── ZiZipApplication.kt          # Application 类
│
├── data/                        # 数据层
│   ├── model/                   # 数据模型
│   │   ├── ChatMessage.kt      # 聊天消息、会话
│   │   ├── ModelConfig.kt      # 模型配置
│   │   └── TaskExecution.kt    # Agent 任务执行状态
│   └── repository/              # 数据仓库
│       ├── SettingsRepository.kt
│       ├── ModelConfigRepository.kt
│       └── HistoryRepository.kt
│
├── domain/                      # 领域层
│   └── model/
│       ├── ModelProvider.kt    # AI 模型接口
│       └── MockModelProvider.kt # Mock 实现
│
├── service/                     # 服务层
│   ├── accessibility/
│   │   └── AutoGLMAccessibilityService.kt  # 无障碍服务
│   └── overlay/
│       └── OverlayService.kt   # 悬浮窗服务
│
└── ui/                          # 表现层
    ├── theme/                   # 主题系统
    │   ├── Color.kt
    │   ├── Type.kt
    │   └── Theme.kt
    ├── components/              # 可复用组件
    │   ├── ZiZipButton.kt
    │   ├── ZiZipCard.kt
    │   ├── ZiZipInput.kt
    │   ├── ChatBubbles.kt      # 消息气泡
    │   ├── TaskExecutionCard.kt # 任务卡片
    │   └── ModelSelector.kt    # 模型选择器
    ├── navigation/              # 导航
    │   ├── Screen.kt
    │   └── NavGraph.kt
    └── screens/                 # 页面
        ├── home/
        │   ├── HomeScreen.kt   # Gemini 风格主页
        │   └── HomeViewModel.kt
        ├── settings/
        │   └── SettingsScreen.kt
        └── history/
            └── HistoryScreen.kt
```

---

## 3. 技术栈

| 类别 | 技术 |
|-----|------|
| 语言 | Kotlin 1.9.20 |
| UI | Jetpack Compose (Material3) |
| 状态管理 | ViewModel + StateFlow |
| 异步 | Coroutines + Flow |
| 导航 | Compose Navigation |
| 存储 | SharedPreferences + JSON |
| CI/CD | GitHub Actions |

---

## 4. 设计系统

### 🎨 颜色调色板
| 名称 | 色值 | 用途 |
|-----|------|------|
| `primaryBlack` | `#3D3A35` | 按钮、标题 |
| `primaryWhite` | `#FFFFFF` | 背景 |
| `accent` | `#8B7355` | 强调色 |
| `grey50` | `#FAF9F7` | 暖白背景 |
| `grey100` | `#F7F5F2` | 浅米色背景 |
| `grey700` | `#5C574F` | 正文文字 |
| `success` | `#6B9B7A` | 成功状态 |
| `error` | `#CB6B6B` | 错误状态 |

### 🔤 字体
- **字体族**: Resource Han Rounded CN
- **字重**: Regular (400), Medium (500), Bold (700)

### 🧩 组件规范
- **按钮**: 高度 52dp, 圆角 12dp, 无阴影
- **卡片**: 圆角 12dp, 1dp 边框, 无阴影
- **输入框**: 填充 grey50, 圆角 12dp

---

## 5. 核心功能

### 无障碍服务 (AutoGLMAccessibilityService)
- 屏幕内容获取 (`getScreenContent()`)
- 点击操作 (`performClick(x, y)`)
- 滑动操作 (`performSwipe(...)`)
- 文本输入 (`inputText(text)`)
- 全局导航 (`back()`, `home()`)

### ModelProvider 接口
```kotlin
interface ModelProvider {
    suspend fun processQuery(query: String, screenContext: ScreenContext?): ModelResponse
    val providerName: String
    val supportsAgentMode: Boolean
}
```

### 数据模型
- `ChatMessage`: 聊天消息
- `ChatSession`: 会话管理
- `ModelConfig`: 模型配置
- `TaskExecution`: 任务执行状态
- `ActionRecord`: 操作记录

---

## 6. UI 页面

### HomeScreen (主页)
- **顶部栏**: 历史记录 | 模型选择器 | 新建对话 | 设置
- **聊天区域**: 消息列表、空状态、Agent 横幅
- **输入栏**: Gemini 风格、图片/工具按钮、发送按钮

### SettingsScreen (设置)
- 权限管理（无障碍、悬浮窗）
- Agent 模式开关
- 模型配置入口

### HistoryScreen (历史)
- 对话列表
- 删除确认

---

## 7. CI/CD 配置

### GitHub Actions
- 自动构建 Release APK
- 自动发布到 GitHub Releases
- 触发条件: push 到 main 分支

### 签名配置
需要在 GitHub Secrets 中设置：
- `KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

---

## 8. 待完成功能

### 高优先级
- [ ] 权限设置引导页面
- [ ] 模型配置管理页面
- [ ] 真实 API 客户端实现

### 中优先级
- [ ] 虚拟屏幕功能
- [ ] 任务执行详情页面
- [ ] 多语言支持

### 低优先级
- [ ] 黑暗模式
- [ ] 数据导出/导入
- [ ] 性能优化

---

## 9. 开发指南

### 本地构建
```bash
./gradlew assembleDebug
```

### 创建 Release
```bash
./gradlew assembleRelease
```

### 代码规范
- 遵循 Kotlin 官方代码风格
- Compose 组件使用 `@Composable` 注解
- ViewModel 使用 `StateFlow` 管理状态
- 使用 `suspend` 函数处理异步操作

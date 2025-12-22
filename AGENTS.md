# Auto-GLM-Android Native Port Guidelines

## 1. Project Overview
**Goal**: Create a native Android application (Kotlin + Jetpack Compose) that is a 1:1 replica of the Auto-GLM-Android Flutter application.
**Core Constraint**: strictly reproduce UI/UX and feature logic (Accessibility, Overlay), but mock or abstract the actual AI Model logic.
**Target Directory**: `g:\Open-AutoGLM\ZiZip`

## 2. Project Architecture
Use **Modern Android Architecture** (MVVM + Clean Architecture principles).

### Package Structure (`com.autoglm.android`)
```
com.autoglm.android
├── app                 # Application class, DI setup
├── ui                  # Jetpack Compose UI Layer
│   ├── theme           # Theme, Color, Type definitions
│   ├── components      # Shared Composables (Buttons, Inputs, Cards)
│   ├── screens         # Screen-level Composables
│   │   ├── home        # Home Page & ViewModel
│   │   ├── chat        # Chat Page & ViewModel
│   │   ├── settings    # Settings Page & ViewModel
│   │   └── ...
│   └── navigation      # Navigation Graph
├── data                # Data Layer
│   ├── model           # Data classes
│   ├── repository      # Repositories (Settings, History)
│   └── source          # Local/Remote data sources
├── domain              # Domain Layer (Optional for now, strictly needed?)
│   └── model           # Business objects
├── service             # Android Services
│   ├── accessibility   # AutoGLMAccessibilityService (Core Engine)
│   └── overlay         # Floating Window Service
└── utils               # Extensions, Helpers
```

### Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material3)
- **State Management**: ViewModel + StateFlow
- **DI**: Manual or Hilt (Manual recommended for start)
- **Async**: Coroutines + Flow
- **Navigation**: Compose Navigation

## 3. Design System (1:1 Replication)
**Design Philosophy**: "Less is More" - low saturation, warm beige/brown tones.

### 🎨 Color Palette
| Token | Hex | Usage |
|-------|-----|-------|
| `primaryBlack` | `#3D3A35` | Main Buttons, Headings, Strong Emphasis |
| `primaryWhite` | `#FFFFFF` | Backgrounds, Cards |
| `accent` | `#8B7355` | Accent Color (Brown) |
| `grey50` | `#FAF9F7` | Warm White Background |
| `grey100` | `#F7F5F2` | Light Beige Background |
| `grey150` | `#ECE9E4` | Dividers |
| `grey200` | `#DDD9D2` | Borders |
| `grey400` | `#A39E94` | Hints, Placeholders |
| `grey700` | `#5C574F` | Body Text |
| `grey900` | `#3D3A35` | Primary Text (Same as Black) |
| `error` | `#CB6B6B` | Soft Red (Error) |
| `success` | `#6B9B7A` | Soft Green (Success) |

### 🔤 Typography
- **Font Family**: `ResourceHanRounded` (Must migrate font files from assets)
- **Weights**: Regular (400), Medium (500), Bold (700)
- **Sizes**:
    - Display: 36sp, 32sp
    - Headline: 24sp, 20sp
    - Body: 16sp, 14sp (Default)
    - Caption: 12sp, 10sp

### 🧩 UI Components
- **Buttons**:
    - Height: `52dp` (Standard), `40dp` (Small)
    - Radius: `12dp`
    - Style: Black background, White text, No shadow (Flat)
- **Cards**:
    - Radius: `12dp`
    - Border: 1dp solid `grey150`
    - Elevation: 0 (Flat)
- **Inputs**:
    - Fill: `grey50`
    - Radius: `12dp`
    - Active Border: 1.5dp `grey900`

## 4. Model Development Guidance
To support the "Model" (AI Agent) without implementing the proprietary logic:
1.  **Define Interface**: Create a `ModelProvider` interface.
    ```kotlin
    interface ModelProvider {
        suspend fun processQuery(query: String, screenContext: ScreenContext): ModelResponse
    }
    ```
2.  **Mock Implementation**: Create a `MockModelProvider` for UI development.
3.  **Data Structure**:
    - `ChatMessage`: (id, content, sender, timestamp, status)
    - `TaskStep`: (description, actionType, targetNode)

## 5. Functional Requirements
### Accessibility Service (`AutoGLMAccessibilityService`)
- Must extend `AccessibilityService`.
- **Capabilities**: Retrieve screen content, perform clicks/scrolls globally.
- **Config**: `accessibility_service_config.xml` must match the Flutter plugin's config.

### Overlay System
- Use `WindowManager` to draw views over other apps.
- **Views**:
    - `FloatingBall`: Collapsed state (if kept).
    - `TaskPanel`: Expanded state showing current AI thought process.

## 6. Development Workflow
1.  **Init**: Scaffold Project, copy assets (Fonts, Images).
2.  **Sys Setup**: Configure Theme, Typography, Base Components.
3.  **UI Dev**: Build Screens one by one (Home -> Settings -> Chat).
4.  **Service Dev**: Implement Accessibility connection (check logcat for node info).
5.  **Integration**: Connect Chat UI to Mock Model.

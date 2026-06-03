# Architecture

## Overview

UIPorter is structured around three layers: **UI**, **Core/Registry**, and
**Data Model**. All conversion work passes through a shared Intermediate
Representation (IR) so that no adapter ever needs to know about any other
adapter directly.

```
┌──────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│   App.java   PrimaryController.java   primary.fxml           │
│   JavaFX window, tabs, code editors, toolbar, preview        │
└─────────────────────────┬────────────────────────────────────┘
                          │ calls
┌─────────────────────────▼────────────────────────────────────┐
│                   Core / Registry Layer                      │
│   FrameworkRegistry  ──►  FrameworkAdapter (interface)       │
│   ConversionUtils  (shared color, font, name helpers)        │
└─────────────────────────┬────────────────────────────────────┘
                          │ produces / consumes
┌─────────────────────────▼────────────────────────────────────┐
│                     Data Model Layer                         │
│   AppMetadata  (root IR - title, size, root Node)            │
│   Node         (single UI element - type, props, children)   │
└──────────────────────────────────────────────────────────────┘
                          ▲
              populated by the three adapters:
         JavaFxAdapter   FxmlAdapter   WinFormsAdapter
```

---

## Conversion Pipeline

Every conversion follows the same two-step pipeline regardless of which
frameworks are involved:

```
Step 1 - Parse          Step 2 - Generate
─────────────────       ──────────────────
Source Code             AppMetadata (IR)
     │                       │
     │  Adapter.parse()       │  Adapter.generate()
     ▼                       ▼
AppMetadata (IR)        Target Code
```

**Step-by-step flow inside the application:**

1. User selects a source framework and a target framework from the dropdowns.
2. User pastes or opens source code into the input editor.
3. User clicks **Convert**.
4. `PrimaryController` asks `FrameworkRegistry` for the correct source adapter.
5. Source adapter's `parse(source)` is called → returns `AppMetadata`.
6. `PrimaryController` asks `FrameworkRegistry` for the correct target adapter.
7. Target adapter's `generate(app, options)` is called → returns a code string.
8. Output is displayed in the output editor with syntax highlighting.

---

## OOP Design Patterns Used

### 1. Strategy Pattern
**Where:** `FrameworkAdapter` interface + `JavaFxAdapter`, `FxmlAdapter`,
`WinFormsAdapter` implementations.

Each adapter is a swappable implementation for parsing or generating code.
`PrimaryController` picks the right one at runtime using
`FrameworkRegistry` without knowing which specific class it will use.

```
«interface»
FrameworkAdapter
─────────────────────────
+ parse(source: String): AppMetadata
+ generate(app: AppMetadata, opts: Map): String
+ getDisplayName(): String
        ▲
        │ implements
  ┌─────┴──────┬───────────────┐
JavaFxAdapter  FxmlAdapter  WinFormsAdapter
```

### 2. Singleton Pattern
**Where:** `FrameworkRegistry`

Only one registry instance exists for the lifetime of the application. Adapters
register themselves at startup (in `App.java`) and the registry is looked up
globally by display name.

### 3. Composite Pattern
**Where:** `Node` class in the data model

A `Node` can hold a list of child `Node` objects, forming a tree that mirrors
the hierarchy of the original UI layout. Container nodes (VBox, HBox, GridPane,
Panel) own their children just as they do in the original frameworks.

### 4. Facade Pattern
**Where:** `PrimaryController`

The controller hides the complexity of registry lookup, adapter selection,
error handling, and syntax highlighting behind simple button-click handlers.
The UI never directly references adapters.

### 5. Template Method (implicit)
**Where:** `JavaFxAdapter.generate()` and `WinFormsAdapter.generate()`

Both generators follow the same four-phase template:
1. Write class/file header and imports
2. Write field declarations
3. Write property setters / assignments
4. Write hierarchy (add children to parents)

---

## Class Responsibilities

| Class | Layer | Responsibility |
|-------|-------|---------------|
| `App` | UI | JavaFX entry point, adapter registration |
| `PrimaryController` | UI | Handles button clicks, connects UI to conversion logic |
| `AppSettings` | UI | Read/write `~/.uiporter/settings.properties` |
| `AIService` | UI | HTTP calls to Gemini API, response parsing |
| `PreviewLauncher` | UI | Compile + run generated JavaFX in subprocess |
| `ConvertCheck` | UI | Validate input before conversion |
| `FrameworkAdapter` | Core | Interface contract for all adapters |
| `FrameworkRegistry` | Core | Singleton map of display name → adapter |
| `ConversionUtils` | Core | Shared NAMED_COLORS map, font/size helpers |
| `AppMetadata` | Model | Root IR: title, width, height, root Node |
| `Node` | Model | IR element: standard JavaFX type name, properties, children |
| `JavaFxAdapter` | Adapter | Parse JavaFX Java source via JavaParser AST |
| `FxmlAdapter` | Adapter | Parse/generate JavaFX FXML via DOM XML |
| `WinFormsAdapter` | Adapter | Parse WinForms C# via ~55 regex patterns |

---

## Standard Type Names

All node types in the IR use **JavaFX names as the standard**. This means
`WinFormsAdapter.parse()` maps C# type names to JavaFX names before storing
them, and `WinFormsAdapter.generate()` maps them back when generating C# code.

| JavaFX (standard) | WinForms equivalent |
|-------------------|---------------------|
| Button | Button |
| Label | Label |
| TextField | TextBox |
| TextArea | RichTextBox |
| CheckBox | CheckBox |
| RadioButton | RadioButton |
| ComboBox | ComboBox |
| ListView | ListBox |
| TableView | DataGridView |
| TreeView | TreeView |
| VBox | FlowLayoutPanel (vertical) |
| HBox | FlowLayoutPanel (horizontal) |
| GridPane | TableLayoutPanel |
| TabPane | TabControl |
| SplitPane | SplitContainer |
| ToolBar | ToolStrip |

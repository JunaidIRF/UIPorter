# 📖 UIPorter - User Manual

## Table of Contents
1. [🚀 Getting Started](#1-getting-started)
2. [🖥️ Main Window Overview](#2-main-window-overview)
3. [🔄 Converting UI Code](#3-converting-ui-code)
4. [📂 File Operations](#4-file-operations)
5. [👁️ Live Preview](#5-live-preview)
6. [🤖 AI Assistant](#6-ai-assistant)
7. [⚙️ Settings](#7-settings)
8. [📋 Supported Frameworks & Controls](#8-supported-frameworks--controls)
9. [🛠️ Troubleshooting](#9-troubleshooting)

---

## 1. 🚀 Getting Started

### Launching the Application
1. Navigate to the `release/UIPorter/` folder
2. Double-click **UIPorter.exe**
3. The app opens instantly - no installation or Java setup required

> 💡 UIPorter is fully self-contained. The Java runtime is bundled inside the release folder.

---

## 2. 🖥️ Main Window Overview

![Main Window - Convert Tab](images/main-window.png)

| # | Element | Description |
|---|---|---|
| 1 | **Tab bar** (Convert / AI) | Switch between the converter and AI assistant |
| 2 | **From** dropdown | The framework of your input code |
| 3 | **To** dropdown | The target framework to convert to |
| 4 | **Open File** | Load a source file from disk |
| 5 | **Convert →** | Run the conversion |
| 6 | **⇄ Swap** | Swap source and target frameworks and re-convert |
| 7 | **Copy** | Copy the full output to the clipboard |
| 8 | **Save As** | Save the output to a file |
| 9 | **Preview** | Compile and launch the output as a live window |
| 10 | **Source Code Area** | Paste or type your input UI code here (syntax-highlighted) |
| 11 | **Output Code Area** | Converted code appears here (syntax-highlighted) |
| 12 | **Status bar** | Shows conversion status, errors, or progress messages |

---

## 3. 🔄 Converting UI Code

### Step-by-step

1. **Select the source framework** in the **From** dropdown:
   - `JavaFX (Java)` - a `.java` file containing a JavaFX `Application` with programmatic UI controls
   - `FXML` - a `.fxml` file (standard JavaFX XML markup)

2. **Select the target framework** in the **To** dropdown

3. **Paste your source code** into the left panel  
   *(or use **Open File** to load from disk)*

4. Click the **Convert →** button

5. The converted code appears in the right panel with syntax highlighting

### 💡 Tips
- The **⇄ Swap** button instantly exchanges source and target frameworks and re-converts
- If a conversion produces unexpected results, check the status bar for warnings
- Comments, helper methods, and data-binding lambdas in Java source are handled automatically

---

## 4. 📂 File Operations

| Button | Keyboard | Action |
|---|---|---|
| **Open File** | - | Opens a file chooser; loads the file into the source area |
| **Copy** | - | Copies the entire output to your clipboard |
| **Save As** | - | Saves the output to a file; suggests the correct extension automatically |

Supported file extensions for **Open**:  
`.java`, `.fxml`

---

## 5. 👁️ Live Preview

The **Preview** button compiles and launches the converted output as a real running window.

### How to Use
1. Convert your code to **JavaFX (Java)** or **FXML**
2. Click **Preview**
3. A new window opens showing the rendered UI

### Requirements
- The converted code must be syntactically valid JavaFX
- For FXML, UIPorter automatically wraps it in a minimal `FXMLLoader` application

> ⚠️ The preview runs in a **separate JVM process** - closing it does not affect UIPorter. If compilation fails, the error is shown in the status bar.

---

## 6. 🤖 AI Assistant

Click the **AI** tab at the top of the window to access the AI assistant.

![AI Assistant Tab](images/ai-tab.png)

### 🔑 Setup (required once)
1. Go to [Google AI Studio](https://aistudio.google.com/apikey) and create a free API key
2. In UIPorter's AI tab, paste your key into the **API Key** field
3. The key is saved automatically - you only need to do this once

### Modes

#### ✏️ Generate UI from Text Prompt
1. Select **"Generate UI from text prompt"**
2. Type a description, e.g.: *"A login form with username, password, and a blue login button"*
3. Click **Generate**
4. The generated JavaFX code appears - click **Use in Converter** to convert it further

#### 🖼️ Generate UI from Screenshot
1. Select **"Image upload to UI code"**
2. Click **Choose Image** and select a PNG/JPG screenshot or mockup
3. Optionally add extra instructions
4. Click **Generate**

#### 💻 Convert Console App to GUI
1. Select **"Console app to GUI app"**
2. Paste your console Java code in the text area
3. Click **Generate** - UIPorter asks the AI to produce a GUI equivalent

#### ✨ Improve Existing UI Code
1. Select **"Improve existing UI code"**
2. Paste your existing UI code
3. Optionally describe what to improve (e.g. *"Make it dark themed"*)
4. Click **Generate**

---

## 7. ⚙️ Settings

Settings are accessed via the checkboxes in the **Convert** tab toolbar and the AI key field in the **AI** tab.

| Setting | Default | Effect |
|---|---|---|
| **Match Default Font** | Off | When on: suppresses font re-emission in output if it equals the framework default font. Keeps generated code cleaner. |
| **AI API Key** | *(blank)* | Your Google Gemini API key. Stored in `~/.uiporter/settings.properties`. |
| **AI Model** | gemini-flash | The Gemini model used for AI generation. |

> 💾 Settings are saved automatically to `C:\Users\YourName\.uiporter\settings.properties`.

---

## 8. 📋 Supported Frameworks & Controls

### 🔄 Conversion Matrix

| From \ To | JavaFX (Java) | FXML |
|---|---|---|
| **JavaFX (Java)** | - | ✓ |
| **FXML** | ✓ | - |

### 🧩 Controls Preserved Across Conversions

| Category | Controls |
|---|---|
| **Basic** | Button, Label, TextField, TextArea, PasswordField |
| **Selection** | ComboBox, ChoiceBox, CheckBox, RadioButton (with groups) |
| **Display** | ListView, TableView (with columns + rows), TreeView |
| **Navigation** | TabPane/Tab, MenuBar/Menu/MenuItem |
| **Layout** | VBox, HBox, BorderPane, GridPane, StackPane, FlowPane, SplitPane, ScrollPane |
| **Range** | Slider, ProgressBar, ProgressIndicator, Spinner |
| **Media** | ImageView |
| **Shapes** | Rectangle, Circle, Ellipse, Line, Polygon |
| **Charts** | LineChart, BarChart, AreaChart, PieChart |
| **Other** | Accordion/TitledPane, Separator, Canvas, Tooltip |

### 🎨 Properties Preserved
- Text content, prompt text
- Width, height, position (layoutX/Y)
- Background colour, text colour
- Font family, size, bold, italic
- Text alignment
- Border (width, colour, style)
- Padding, spacing (hgap/vgap)
- Visible, enabled, editable flags
- Event handlers (onAction/Click)
- Inline CSS / style strings
- GridPane row/column positions
- Data lists (items), tree structures, table columns/rows
- Opacity, rotation

---

## 9. 🛠️ Troubleshooting

### ❌ "Nothing appears in the output area"
- Ensure the source code is not empty
- Check that the **From** and **To** dropdowns are set correctly
- If the source is FXML, it must start with `<?xml` or a root element tag

### ⚠️ "Conversion looks incomplete"
- Some complex code patterns cannot be parsed
- Simplify the source or use the **AI → Improve** mode on the output to fill gaps

### ⚠️ "Preview fails to compile"
- The status bar will show the Java compiler error
- Common cause: AI-generated code used a non-existent JavaFX API - use the **AI → Improve** mode to fix it
- Ensure the converted code targets JavaFX (Java) or FXML before clicking Preview

### ❌ "AI Generate returns an error"
- Verify the API key is correct (re-paste it in the AI tab)
- Check your internet connection
- The Gemini API has free-tier rate limits; wait a moment and try again

### ❌ "The app won't start"
- On Windows: right-click `UIPorter.exe` → **Properties** → uncheck **"Block"** if Windows blocked the file
- Ensure you are running from the `release/UIPorter/` folder, not moving just the `.exe`

# UIPorter - Features & Working Description

---

## 💡 What is UIPorter?

UIPorter is a **desktop developer tool** built with JavaFX that automatically converts UI source code between different frameworks. Instead of rewriting your entire UI by hand every time you switch formats, UIPorter does it in one click.

It supports conversion between **JavaFX Java**, **JavaFX FXML**, and **WinForms C#**, while preserving everything: control types, colors, fonts, sizes, layout, event handlers, and more.

---

## ✨ Features

### 🔄 Multi-Framework Code Conversion
Convert UI code between frameworks instantly - in either direction.

| From | To | Support |
|---|---|---|
| JavaFX Java | FXML | ✅ Full |
| FXML | JavaFX Java | ✅ Full |
| JavaFX Java | WinForms C# | ✅ Supported |
| FXML | WinForms C# | ✅ Supported |
| WinForms C# | JavaFX Java | ✅ Supported |
| WinForms C# | FXML | ✅ Supported |

> Converting between **JavaFX Java ↔ FXML** is the most accurate direction - same framework, just different syntax.

**What gets preserved:**
- Control types and text content
- Colors, fonts, sizes, and layout positions
- Padding, spacing, borders, and opacity
- Event handlers (onAction / onClick)
- Data lists, tree structures, table columns and rows
- GridPane row/column positions
- Inline CSS styles
- Tooltips, visibility, and enabled/disabled state

---

### 🤖 AI Assistant (Gemini API)
Four AI-powered modes accessible from the AI tab:

| Mode | What it does |
|---|---|
| ✏️ **Text Prompt → UI** | Describe a UI in plain text - get a full JavaFX layout back |
| 🖼️ **Image → UI** | Upload a screenshot or mockup - get matching JavaFX/FXML code |
| 💻 **Console → GUI** | Paste a console Java app - AI converts it to a GUI version |
| ✨ **Improve UI** | Paste existing UI code - AI enhances styling, layout, and structure |

The AI always produces **compilable, ready-to-use JavaFX code**.

---

### 👁️ Live Preview
Click **Preview** after a conversion to instantly compile and launch the output as a real running window.

- Works for both **JavaFX Java** and **FXML** output
- Runs in a **separate JVM process** - closing the preview does not affect UIPorter
- In standalone mode: uses the bundled `javax.tools.JavaCompiler`
- In dev mode: uses `mvn javafx:run` inside the preview-runner subproject

---

### 🎨 Syntax Highlighting
Both the source and output code areas are fully syntax-highlighted using **RichTextFX**:
- **Java**: keywords, strings, comments, annotations
- **FXML / XML**: tags, attributes, and values
- Highlighting updates live as you type or paste

---

### 📂 File I/O
| Action | Description |
|---|---|
| **Open File** | Load a `.java`, `.fxml`, or `.cs` file directly into the source area |
| **Save As** | Save the converted output with the correct file extension |
| **Copy** | Copy the full output to the clipboard in one click |

---

### ⇄ One-Click Swap
The **Swap** button instantly flips the source and target frameworks and you can re-run the conversion which is useful for testing round-trips.

---

### ⚙️ Persistent Settings
Settings are automatically saved to `~/.uiporter/settings.properties`:
- **AI API Key** - your Google Gemini key (entered once, saved forever)
- **AI Model** - choose which Gemini model to use
- **Match Default Font** - keeps generated code clean by skipping default font re-emission

---

### 📦 Self-Contained Portable App
UIPorter ships as a single portable folder with a bundled JVM runtime:
- Built with `jpackage`
- Just double-click `UIPorter.exe` - no Java installation needed to run the app

> **Live Preview requires a JDK.** The Preview feature compiles and runs real JavaFX code, so it needs a full JDK 25+ installed separately with `javac` on the system PATH. All other features work without it.

---

## ⚙️ How It Works

When you click **Convert**, UIPorter runs this pipeline:

```
Source Code
     |
     v
[Source Adapter].parse()
     |
     v
AppMetadata  (Intermediate Representation)
  - allNodes : list of every UI control as a Node
  - Node     : id, type, properties map, children list
  - metadata : title, scene size, stylesheets
     |
     v
[Target Adapter].generate()
     |
     v
Output Code
```

**The key design:** all adapters share the same IR (`AppMetadata`). This means:
- Each adapter only needs to know how to convert **to/from the IR** - not to/from every other framework
- Adding a new framework requires just **one new adapter class**
- The IR is plain Java data with no framework-specific dependencies

### 🔌 Adapters

| Adapter | How it parses |
|---|---|
| **JavaFxAdapter** | Multi-pass AST walk using the JavaParser library |
| **FxmlAdapter** | Secure XML DOM parse (`DocumentBuilder`) |
| **WinFormsAdapter** | ~55 regex patterns for C# Designer-generated code |

---

## 🧩 Supported Controls

| Category | Controls |
|---|---|
| **Basic** | Button, Label, TextField, TextArea, PasswordField, Hyperlink |
| **Selection** | ComboBox, ChoiceBox, CheckBox, RadioButton (with ToggleGroup) |
| **Display** | ListView, TableView (columns + rows), TreeView |
| **Navigation** | TabPane / Tab, MenuBar / Menu / MenuItem |
| **Layout** | VBox, HBox, BorderPane, GridPane, StackPane, FlowPane, SplitPane, ScrollPane, AnchorPane |
| **Range** | Slider, ProgressBar, ProgressIndicator, Spinner |
| **Media** | ImageView |
| **Shapes** | Rectangle, Circle, Ellipse, Line, Polygon |
| **Charts** | LineChart, BarChart, AreaChart, PieChart |
| **Other** | Accordion / TitledPane, Separator, Canvas, Tooltip |

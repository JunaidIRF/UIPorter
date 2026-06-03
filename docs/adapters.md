# Adapters

An **adapter** is a class that implements the `FrameworkAdapter` interface.
It has two responsibilities:

- `parse(String source)` - read source code and produce an `AppMetadata` IR object
- `generate(AppMetadata app, Map<String,String> options)` - read an IR object and
  produce target code as a string

UIPorter ships with three adapters.

---

## FrameworkAdapter Interface

```java
public interface FrameworkAdapter {
    String getDisplayName();   // e.g. "JavaFX (Java)", "WinForms (C#)"
    AppMetadata parse(String source) throws Exception;
    String generate(AppMetadata app, Map<String, String> options) throws Exception;
}
```

---

## JavaFxAdapter

**File:** `adapters/JavaFxAdapter.java`
**Handles:** JavaFX Java source code (`.java` files with a `start()` method)

### Parse - 4-Pass AST Walk

JavaFxAdapter uses the **JavaParser** library to build an Abstract Syntax Tree
(AST) from the source code, then walks it in four passes.

**PRE-PASS - Helper Method Collection**
Walks all methods that are not `start()`. Collects:
- `ObservableList` contents (for ComboBox/ListView items)
- `TreeItem` structure (text + children)
- `TableColumn` header names
- Factory method return IDs (for later cloning)

**PASS 1 - Node Registration**
Walks `VariableDeclarationExpr` and `ObjectCreationExpr` nodes in the AST.
For each `new Button(...)`, `new Label(...)`, etc., creates a `Node` entry in
the IR and stores it in a working map keyed by variable name.

**PASS 2 - Property Extraction**
Walks `MethodCallExpr` nodes in the AST, matching known setter patterns:
- `setText(...)` → `text` property
- `setStyle(...)` → parses inline CSS and splits it into individual properties
- `setPrefSize(w, h)` → `width` + `height`
- `setFont(Font.font(...))` → `fontFamily`, `fontSize`, `fontWeight`, etc.
- `setAlignment(...)` → `textAlign`
- `setOnAction(...)` → `hasClick = true`
- and more

**PASS 3 - Hierarchy Building**
Walks `MethodCallExpr` to find parent-child relationships:
- `container.getChildren().add(child)` - general containers
- `borderPane.setTop(node)`, `setCenter(node)`, etc. - BorderPane slots
- `tabPane.getTabs().add(tab)` - TabPane children
- `splitPane.getItems().add(node)` - SplitPane items

**PASS 4 - TableView Columns**
A dedicated pass resolves column assignments to their owning TableView nodes.

### Parse - FXML Branch
When the source contains `<?xml`, `JavaFxAdapter` switches to XML parsing.
It uses a `DocumentBuilder` with entity expansion disabled (XXE prevention)
to parse the FXML, then maps each FXML element to a `Node` object.

### Generate

Writes JavaFX Java code in four phases:
1. Class header, imports (`javafx.scene.control.*`, etc.)
2. Field declarations for each control
3. Property setters for each field
4. Hierarchy assembly (`root.getChildren().add(...)`, `scene = new Scene(root)`)

---

## FxmlAdapter

**File:** `adapters/FxmlAdapter.java`
**Handles:** JavaFX FXML (`.fxml` files)

### Parse

Uses `DocumentBuilder` (secure DOM - entity expansion disabled) to parse the
FXML XML. Recursively maps each XML element to a `Node`:
- Element tag name → standard JavaFX type name (via a lookup table)
- `fx:id` attribute → `Node.id`
- `text`, `prefWidth`, `style`, etc. attributes → `Node.properties`
- Child elements → `Node.children`

### Generate

Walks the `AppMetadata` node tree and writes FXML XML:
- Adds `<?xml ...?>` and `<?import ...?>` statements at the top
- Converts standard JavaFX type names to FXML element names
- Writes property attributes inline on each element
- Writes inline CSS for color/font into the `style="..."` attribute

---

## WinFormsAdapter

**File:** `adapters/WinFormsAdapter.java`
**Handles:** WinForms C# source code (Designer-style `InitializeComponent()` blocks)

### Approach

WinFormsAdapter does **not** build an AST. Instead it applies a set of
~55 compiled `Pattern` (regex) objects to the cleaned-up C# source, scanning
line-by-line to extract all control declarations and property assignments.

### normalizeCs()

Before matching, the source is cleaned up:
- Strips comments
- Collapses multi-line statements that end in `+` (string concatenation)
- Handles both standard C# and C++/CLI syntax variants

### Regex Pattern Groups

| Group | Example Pattern | What It Captures |
|-------|----------------|-----------------|
| Declaration | `WF_DECL` | `this.btn1 = new Button()` |
| Field declaration | `WF_FIELD_DECL` | `private Button btn1;` |
| Text | `WF_TEXT` | `this.btn1.Text = "Save"` |
| Window title | `WF_TITLE` | `this.Text = "My Form"` |
| Size | `WF_SIZE` | `this.btn1.Size = new Size(100, 30)` |
| Location | `WF_LOCATION` | `this.btn1.Location = new Point(10, 20)` |
| Back color | `WF_BACK_*` | `this.btn1.BackColor = Color.Red` |
| Fore color | `WF_FORE_*` | `this.btn1.ForeColor = Color.Blue` |
| Font | `WF_FONT` | `this.btn1.Font = new Font("Arial", 12)` |
| Alignment | `WF_ALIGN` | `this.lbl1.TextAlign = ContentAlignment.MiddleCenter` |
| Hierarchy | `WF_ADD` | `this.Controls.Add(this.btn1)` |
| Tab pages | `WF_TABPAGES_ADD` | `this.tabCtrl.TabPages.Add(this.tabPage1)` |
| Items | `WF_ITEMS_ADDRANGE` | `this.combo.Items.AddRange(new object[] {"A", "B"})` |
| DataGridView | `WF_DGV_COLS_RANGE` | Column header definitions |
| TreeView | `WF_TREE_*` | TreeNode structure |

### Type Mapping

WinForms type names are mapped to standard JavaFX names when parsing, and
back to WinForms names when generating, using two lookup maps:

```
WF_TO_CANONICAL:  "TextBox"      → "TextField"
                  "RichTextBox"  → "TextArea"
                  "ListBox"      → "ListView"
                  "TabControl"   → "TabPane"
                  ...
CANONICAL_TO_WF:  reverse of the above
```

### Generate

Writes a C# WinForms Designer-style class in four phases:
1. `using` statements and class/form field declarations
2. Constructor and `InitializeComponent()` method start
3. Control creation and property assignment (size, location, text, color, font)
4. `Controls.Add(...)` hierarchy calls and `ResumeLayout()`

Layout containers are mapped as:
- `VBox`/`HBox` → `FlowLayoutPanel`
- `GridPane` → `TableLayoutPanel`
- `SplitPane` → `SplitContainer`
- `ToolBar` children → `ToolStripButton`

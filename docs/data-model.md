# Data Model - AppMetadata and Node

The **Intermediate Representation (IR)** is the heart of UIPorter. Every
conversion operation converts source code **into** the IR and then converts
the IR **out** to the target format. The IR is framework-neutral - it does not
belong to JavaFX, FXML, or WinForms.

The IR is made up of two classes: `AppMetadata` and `Node`.

---

## AppMetadata

`AppMetadata` is the root object returned by every `parse()` call. It holds
top-level application-level properties and the root UI `Node`.

```java
public class AppMetadata {
    String title;            // Window/form title
    double sceneWidth;       // Window width in pixels
    double sceneHeight;      // Window height in pixels
    boolean resizable;       // Whether the window can be resized
    String formBackColor;    // Background color (hex #rrggbb)
    String formBackgroundImage; // Background image path (optional)
    String sceneRootId;      // Variable name of the root layout node

    Node rootNode;           // Root of the UI element tree
}
```

---

## Node

`Node` represents a single UI element. It maps to one control or container
in the original source code.

```java
public class Node {
    String id;                     // Variable/fx:id name
    String type;                   // JavaFX type name (e.g. "Button")
    Map<String, String> properties; // All style/layout properties
    List<Node> children;           // Child nodes (for containers)
}
```

### Property Keys

The `properties` map uses string keys. The table below lists all recognized
keys and what they store.

**Text and Appearance**

| Key | Stored Value |
|-----|-------------|
| `text` | Display text of the control |
| `backColor` | Background color as `#rrggbb` |
| `foreColor` | Text/foreground color as `#rrggbb` |
| `fontFamily` | Font family name (e.g. `Arial`) |
| `fontSize` | Numeric font size (e.g. `14`) |
| `fontSizeUnit` | `px` or `pt` |
| `fontWeight` | `BOLD` or absent |
| `fontPosture` | `ITALIC` or absent |
| `textAlign` | WinForms `ContentAlignment` string (e.g. `MiddleCenter`) |
| `wrapText` | `true` / `false` |
| `editable` | `true` / `false` (TextArea) |

**Layout**

| Key | Stored Value |
|-----|-------------|
| `width` | Preferred width in pixels |
| `height` | Preferred height in pixels |
| `layoutX` | Absolute X position |
| `layoutY` | Absolute Y position |
| `padding` | CSS-style padding (e.g. `10 5 10 5`) |
| `hgap` | Horizontal gap for grid containers |
| `vgap` | Vertical gap for grid containers |
| `orientation` | `HORIZONTAL` / `VERTICAL` for SplitPane |
| `gridCol` | Grid column index |
| `gridRow` | Grid row index |
| `colSpan` | Column span in a grid |
| `rowSpan` | Row span in a grid |

**Borders**

| Key | Stored Value |
|-----|-------------|
| `borderWidth` | Border thickness in pixels |
| `borderColor` | Border color as `#rrggbb` |
| `borderFixed` | `true` when WinForms `FixedSingle` border is set |

**Behaviour**

| Key | Stored Value |
|-----|-------------|
| `visible` | `true` / `false` |
| `enabled` | `true` / `false` |
| `hasClick` | `true` if an onClick/ActionEvent handler is wired |
| `imagePath` | Image source path for ImageView / PictureBox |

**TreeView-specific**

| Key | Stored Value |
|-----|-------------|
| `treeRootText` | Text of the root TreeItem |
| `treeRootExpanded` | `true` / `false` |
| `treeChildren` | Pipe-delimited child node text values |

**TableView-specific**

| Key | Stored Value |
|-----|-------------|
| `tableColumns` | Pipe-delimited column header names |
| `tableRows` | Semicolon-delimited rows, pipe-delimited cells |
| `items` | Pipe-delimited item strings (for ListView/ComboBox) |

---

## Node Tree Example

The following shows how a simple two-button form is represented in the IR:

```
AppMetadata
├── title = "My Form"
├── sceneWidth = 400
├── sceneHeight = 300
└── rootNode: Node
      ├── id = "root"
      ├── type = "VBox"
      ├── properties: { padding="10", spacing="5" }
      └── children:
            ├── Node { id="btn1", type="Button", text="Save" }
            └── Node { id="btn2", type="Button", text="Cancel" }
```

---

## Color Normalization

Both `JavaFxAdapter` and `WinFormsAdapter` normalize all colors to
`#rrggbb` hex format before storing them in `Node.properties`. This lets the
target adapter emit whatever color syntax its framework uses without needing
to re-parse raw color strings.

`ConversionUtils.NAMED_COLORS` contains a map of ~20 common color names
(e.g. `Red → #ff0000`, `Blue → #0000ff`) used by both adapters.

WinForms `SystemColors` (e.g. `SystemColors.ControlDark`) are approximated
to nearest-equivalent hex values via the `SYSTEM_COLOR_HEX` map in
`WinFormsAdapter`.

---

## Why a Separate IR?

Without a shared IR, adding a third framework (e.g. HTML/CSS) would require
writing conversion paths from every existing framework to the new one and
back - that is N² paths for N frameworks. With the IR, only one new adapter
is needed: one `parse()` and one `generate()`, giving automatic compatibility
with every existing framework.

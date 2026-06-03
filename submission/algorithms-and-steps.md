# UIPorter - Development Steps & Algorithms

---

## Development Steps

### 1. Analyze the Problem
- Developers sometimes had the need to switch between JavaFX Java code and FXML, but no automated tool existed to do this
- Researched the gap: all visual properties (colors, fonts, sizes, layout, event handlers) must be preserved across the conversion
- Defined scope: bidirectional conversion between JavaFX Java, JavaFX FXML, and WinForms C#

### 2. Design the Intermediate Representation (IR)
- Created `AppMetadata` as a framework-neutral data model that sits between all adapters
- Each UI control becomes a `Node` object with: `id`, `type` (standard JavaFX name), `properties` (String to String map), and a `children` list
- Window-level data (title, scene size, stylesheet URLs, root node ID) is stored directly on `AppMetadata`
- The IR has zero dependency on any UI framework - it is plain Java data

### 3. Define the Adapter Interface
- Designed `FrameworkAdapter` with two methods: `parse(source)` returns `AppMetadata` and `generate(app)` returns `String`
- Designed `FrameworkRegistry` as a Singleton holding the list of registered adapters
- This Open/Closed Principle design means adding a new framework only requires writing one new class

### 4. Implement the Adapters
- JavaFxAdapter uses the JavaParser library to build a full AST of the Java source, then walks it in multiple passes to extract every node and property
- FxmlAdapter uses DocumentBuilder for secure XML DOM parsing; a recursive writer handles generation
- WinFormsAdapter uses ~55 regex patterns to extract controls and properties from C# Designer-generated code

### 5. Build the UI and Controller
- Designed the main window in FXML with split code editors, framework dropdowns, and a toolbar
- PrimaryController wires all UI actions (Convert, Swap, Preview, AI) to the adapter pipeline
- Integrated RichTextFX for syntax-highlighted Java and XML editing

### 6. Add AI Integration
- Integrated Google Gemini API via java.net.http.HttpClient (no third-party AI SDK needed)
- Four AI modes: Text Prompt to UI, Image to UI, Console App to GUI, Improve Existing UI
- A shared system instruction enforces that all AI output is compilable JavaFX code

### 7. Add Live Preview
- PreviewLauncher compiles the generated JavaFX code using javax.tools.JavaCompiler and spawns a new JVM process to display the result
- FXML output is wrapped in a minimal FXMLLoader application before being passed to the compiler

### 8. Test and Validate
- Tested JavaFX Java to FXML to JavaFX Java round-trips to verify no data is lost
- Verified all supported control types, inline styles, and layout structures survive a full parse-generate cycle
- Used the live preview to visually validate converted output against the original source

### 9. Package and Release
- build-release.bat runs mvn package then jpackage to produce a self-contained portable release/UIPorter/ folder
- No Java installation is needed by the end user - the runtime is bundled inside the release folder

---

## Core Algorithms

### Algorithm 1: Conversion Pipeline

    INPUT:  sourceCode, sourceFramework, targetFramework

    1. Look up source adapter via FrameworkRegistry.get(sourceFramework)
    2. sourceAdapter.parse(sourceCode)  ->  AppMetadata (IR)
    3. Look up target adapter via FrameworkRegistry.get(targetFramework)
    4. targetAdapter.generate(AppMetadata)  ->  outputCode
    5. Display outputCode in the output editor with syntax highlighting

    OUTPUT: outputCode (String)

---

### Algorithm 2: JavaFxAdapter.parse() - Multi-Pass AST Walk

    INPUT:  source (Java source code string)

    If source starts with <?xml or <? :
        Route to FxmlAdapter.parseFxml() and return its result

    1. JavaParser.parse(source)  ->  CompilationUnit (AST)
       If parse error  ->  return empty AppMetadata

    2. PRE-PASS - collect extra data from the start() method body:
       - Scan ObservableList / String[] variable declarations  ->  list item data
       - Detect nested ObservableList types  ->  TableView row data
       - Scan TreeItem declarations and getChildren().addAll() calls  ->  tree structure
       - Scan ToggleGroup declarations  ->  radio button group data
       - Scan DropShadow declarations  ->  shadow effect metadata
       - Walk non-start() helper methods  ->  register their returned nodes

    3. PASS 1 - register all UI nodes:
       For each variable declaration in start() whose type is a known JavaFX control:
           Create Node(id = varName, type = declaredType)
           Extract the constructor string argument as node.text

    4. PASS 1.5 - unroll for-loops:
       For each for/forEach loop in start():
           Resolve the iterable to a list of items
           Create one synthetic node per item (foreach_var_0, foreach_var_1, ...)

    5. PASS 2 - extract properties:
       For each method call in start() (skip lambda bodies):
           Find the Node matching the call scope variable
           Map the method name to an IR property key, for example:
               setText(v)        ->  node.text
               setPrefSize(w, h) ->  node.width, node.height
               setStyle(css)     ->  parseFxStyle(css, node)
               setOnAction(...)  ->  node.hasClick, node.clickMethod
               setFont(...)      ->  node.fontFamily, node.fontSize
               setAlignment(v)   ->  node.textAlign
               setVisible(v)     ->  node.visible
               ... (50+ total mappings)

    6. PASS 3 - build parent-child hierarchy:
       For each method call in start():
           parent.getChildren().add(child)   ->  addChild(app, parent, child)
           GridPane.add(node, col, row)      ->  addChild + set gridCol / gridRow
           setTop / Bottom / Left / Right    ->  addChild with dock-region tag
           ListView/ComboBox.getItems().add  ->  capture as items property (not children)
       Post-pass: resolve expandedPaneId references for Accordion / TitledPane

    7. PASS 4 - scene and table metadata:
       new Scene(root, width, height)   ->  app.sceneRootId, sceneWidth, sceneHeight
       stage.setTitle(text)             ->  app.title
       tableView.getColumns().add(col)  ->  register column headers under table node

    OUTPUT: AppMetadata

---

### Algorithm 3: parseFxStyle() - Inline CSS Decoder

    INPUT:  css string e.g. "-fx-background-color: #fff; -fx-font-size: 14px;"
            node (target Node)

    For each key: value pair split by semicolons:
        -fx-background-color   ->  node.backColor
        -fx-text-fill          ->  node.foreColor
        -fx-font-family        ->  node.fontFamily
        -fx-font-size          ->  node.fontSize (strip unit)
        -fx-font-weight        ->  node.fontWeight
        -fx-font-style         ->  node.fontPosture
        -fx-border-color       ->  node.borderColor
        -fx-border-width       ->  node.borderWidth
        -fx-padding            ->  node.padding
        -fx-opacity            ->  node.opacity
        -fx-background-radius  ->  node.backgroundRadius
        anything else          ->  append to node.extraCss (pass-through)

---

### Algorithm 4: FxmlAdapter.generate() - Recursive XML Writer

    INPUT:  AppMetadata

    1. Collect all node types used  ->  emit <?import pkg.Type?> header lines
    2. Locate root node via app.sceneRootId

    3. writeXmlNode(node, isRoot):
       a. Open tag: <TypeName
       b. If root: add xmlns, xmlns:fx, and scene prefWidth / prefHeight
       c. Emit fx:id, dimensions, text, alignment, boolean, and style= attributes
          style= is built by reversing IR keys back to -fx-* CSS (buildStyleAttr)
       d. If node has children / items / padding: emit closing > and child elements:
              <padding><Insets /></padding>
              <items><FXCollections /></items>
              <root><TreeItem /></root>
              Recurse writeXmlNode() for each child node
       e. Otherwise emit self-closing />

    OUTPUT: FXML string

---

## OOP Design Patterns

| Pattern | Where Used | Purpose |
|---|---|---|
| **Strategy** | FrameworkAdapter interface | Each adapter is a swappable parse/generate strategy |
| **Singleton** | FrameworkRegistry | One central store for all registered adapters |
| **Composite** | AppMetadata + Node | Tree of nodes mirrors the UI control hierarchy |
| **Facade** | PrimaryController | Single entry point that hides all subsystem complexity |
| **Template Method** | writeXmlNode() recursion | Same structure per node, with type-specific variation |
| **Utility** | ConversionUtils, AIService, PreviewLauncher | Stateless helpers grouped by concern |
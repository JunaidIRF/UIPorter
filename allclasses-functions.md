# UIPorter - All Classes & Functions Used (Non-Standard Java)

This document lists every non-trivial class and function used in the UIPorter project, grouped by library/category.

---

## 1. Project's Own Classes

### `App` (App.java)
The JavaFX entry point. Extends `Application`, registers all adapters, and loads the primary FXML scene.
- `start(Stage stage)` - called by the JavaFX runtime; sets up the window, title, icon, and CSS.
- `setRoot(String fxml)` - swaps the scene's root to a different FXML file.
- `loadFXML(String fxml)` - loads and returns a `Parent` node from a named FXML resource.

---

### `PrimaryController` (PrimaryController.java)
The main UI controller. Wired to the primary FXML via `@FXML`. Handles all user interactions.
- `initialize()` - called by FXMLLoader; sets up combo boxes, code areas, listeners.
- `switchPage(Button, VBox)` - shows/hides pages in the main navigation.
- `handleOpenFile()` - opens a `FileChooser` and loads file text into the source code area.
- `handleSaveAs()` - saves the converted output to a user-chosen file.
- `handleConvert()` - reads source, calls the appropriate adapter parse + generate, shows result.
- `handleSwap()` - swaps source and target combo boxes and their text contents.
- `handleCopy()` - copies output text to the system clipboard.
- `buildOptionsMap()` - collects checkbox/field state into a `Map<String, Object>` for adapter options.
- `updatePreviewButtonVisibility()` - shows or hides the Preview button based on target language.
- `handlePreview(CodeArea, Label)` - sends generated code to `PreviewLauncher`.
- `showPreviewError(String, String, Label)` - displays compile/runtime errors in the UI.
- `runAI(String mode, ...)` - calls `AIService.callApi()` on a background thread; updates the UI.
- `applyHighlighting(CodeArea, String)` - triggers syntax highlight recomputation for a given language.
- `computeHighlighting(String text, String lang)` - builds a `StyleSpans` object using regex for Java/XML/C# keywords.

---

### `AIService` (AIService.java)
Wrapper around the Google Gemini REST API.
- `callApi(...)` - builds a JSON prompt payload and sends it via `HttpClient`; returns the AI response text.
- `buildPrompt(String mode, ...)` - constructs the prompt string for a given AI mode (image-to-UI, improve-UI, etc.).
- `escapeJson(String)` - escapes special characters so strings embed safely in JSON.
- `parseResponse(String)` - extracts the `text` field from the Gemini JSON response body.
- `extractJsonTextField(String)` - low-level helper that pulls a value from a JSON string without a full parser.
- `stripCodeFences(String)` - removes ` ```java ` / ` ```xml ` code fence markers from AI output.
- `looksLikeCode(String)` - heuristic check (braces, semicolons) to detect if AI output is code.
- `compressImage(File, int maxWidth)` - resizes an image and returns it as a compressed JPEG byte array for upload.

---

### `AppSettings` (AppSettings.java)
Persistent key-value settings stored in `~/.uiporter/settings.properties`.
- `load()` - reads the properties file from disk into memory.
- `save()` - writes the in-memory properties back to disk.
- `get(String key, String defaultValue)` - retrieves a setting string, falling back to a default.
- `getBool(String key, boolean defaultValue)` - retrieves a boolean setting.
- `set(String key, String value)` - stores a setting string.
- `setBool(String key, boolean value)` - stores a boolean setting.

---

### `PreviewLauncher` (PreviewLauncher.java)
Compiles and runs generated JavaFX/FXML code at runtime so the user can preview the output.
- `launch(String code)` - entry point; normalizes code, then delegates to `launchStandalone()`.
- `buildFxmlWrapper(String fxmlContent)` - wraps raw FXML in a minimal JavaFX `Application` stub.
- `extractAndStripPieChartData(String fxml, StringBuilder)` - pulls `PieChart.Data` entries out of FXML for inline code generation.
- `launchStandalone(String)` - writes source to a temp file, compiles it, then spawns a subprocess.
- `resolveStandaloneWorkDir()` - returns (or creates) the temp work directory under the preview-runner module.
- `compileStandalone(Path sourceFile, Path outputDir)` - calls `ToolProvider.getSystemJavaCompiler()` to compile the source.
- `runStandalone(Path outputDir)` - builds the `java` command with module path and launches the subprocess.
- `findJavaExe()` - locates the `java` executable on disk (checks `JAVA_HOME`, `PATH`).
- `findJavaFxModulePath()` - locates the bundled JavaFX modules in the release directory.
- `normalizeCode(String)` - ensures the source has the required class/method structure for compilation.

---

### `FrameworkAdapter` (core/FrameworkAdapter.java)
Interface every adapter must implement.
- `getDisplayName()` - returns the human-readable name shown in the UI combo box.
- `getFileExtensions()` - returns supported file extensions (e.g. `[".java"]`).
- `getSyntaxLanguage()` - returns the syntax highlight language key (e.g. `"java"`, `"xml"`, `"csharp"`).
- `parse(String source)` - converts source text into an `AppMetadata` IR object.
- `generate(AppMetadata app, Map<String, Object> options)` - converts IR back into target source text.
- `generate(AppMetadata app)` *(default)* - calls `generate` with an empty options map.

---

### `FrameworkRegistry` (core/FrameworkRegistry.java)
Singleton that holds all registered `FrameworkAdapter` instances.
- `getInstance()` - returns the single registry instance.
- `register(FrameworkAdapter)` - adds an adapter to the registry.
- `getAdapters()` - returns all registered adapters.
- `findByName(String)` - looks up an adapter by display name.

---

### `ConversionUtils` (core/ConversionUtils.java)
Shared static utilities used by every adapter.
- `rgbToHex(String r, String g, String b)` - converts three numeric strings to a `#rrggbb` hex color.
- `rgbToHex(int r, int g, int b)` - overload accepting integers.
- `simpleName(String fullType)` - strips a package prefix to get the bare class name.
- `quoted(String value)` - wraps a string in double quotes.
- `getOrMake(AppMetadata, String id)` - finds an existing `Node` by ID or creates a new one.
- `findById(AppMetadata, String id)` - searches all nodes for a matching ID; returns `null` if not found.
- `addChild(AppMetadata, Node parent, Node child)` - appends a child node and records the parent in the map.
- `computeRoots(AppMetadata)` - returns all nodes that have no parent (i.e., top-level roots).

---

### `AppMetadata` (model/AppMetadata.java)
The canonical Intermediate Representation (IR) of a UI form.
Stores: title, scene dimensions, resizable flag, background color/image, and the full node tree.

---

### `Node` (model/Node.java)
A single UI element in the IR tree. Holds: `id`, `type` (canonical JavaFX name), `properties` (`Map<String, String>`), and `children` (`List<Node>`).

---

### `JavaFxAdapter` (adapters/JavaFxAdapter.java)
Parses JavaFX Java code into IR and generates JavaFX Java or FXML.  Uses a 4-pass AST walk via JavaParser.
- `parse(String source)` - detects Java vs. FXML, delegates accordingly.
- `generate(AppMetadata, Map)` - emits JavaFX Java source or FXML depending on options.
- `walkHelperBody(...)` - walks a non-`start()` method to register nodes it declares.
- `deepCloneSubtreeWithMap(...)` - deep-clones an IR subtree for factory-method expansion.
- `reapplyParamsToClone(...)` - re-applies call-site arguments to a cloned subtree.

---

### `FxmlAdapter` (adapters/FxmlAdapter.java)
Parses FXML XML into IR using the W3C DOM API and generates FXML output.
- `parseFxml(String source)` - builds a W3C `Document`, walks elements recursively.
- `parseFxmlElement(Element, ...)` - maps an XML element + its attributes to an IR `Node`.
- `parseFxmlFontChild(...)` - extracts `<Font>` child element data into node properties.
- `parseFxmlPaddingChild(...)` - extracts `<Insets>` padding values.
- `parseFxmlEffectChild(...)` - handles `<DropShadow>` and similar effect elements.
- `isContainerWrapper(String tag)` - returns true for layout wrapper tags like `<padding>`, `<top>`, etc.

---

### `WinFormsAdapter` (adapters/WinFormsAdapter.java)
Parses C# WinForms code into IR using ~55 compiled `Pattern` (regex) objects, and generates C# WinForms output.
- `parse(String source)` - normalizes C# code, then applies regex patterns to extract controls and properties.
- `normalizeCs(String)` - handles C++/CLI and standard C# syntax differences.
- `generate(AppMetadata, Map)` - emits a WinForms C# class with `InitializeComponent()`.

---

## 2. JavaFX (`javafx.*`)

| Class | Used in | Short Description |
|---|---|---|
| `Application` | App.java | Base class for all JavaFX apps; `launch()` starts the runtime. |
| `Stage` | App.java | The top-level window (OS window). |
| `Scene` | App.java | The content graph attached to a `Stage`. |
| `Parent` | App.java | Abstract base for all layout containers loaded from FXML. |
| `FXMLLoader` | App.java | Loads `.fxml` files and wires `@FXML` fields in the controller. |
| `Image` | App.java | Loads image files (used for the window icon). |
| `Color` | App.java | Represents RGBA colors; used for scene background. |
| `StageStyle` | App.java | Enum: `TRANSPARENT`, `UNIFIED`, etc. - controls window chrome style. |
| `HeaderBar` | App.java, PrimaryController.java | Custom title bar widget for styled/borderless windows. |
| `HeaderButtonType` | PrimaryController.java | Enum for `HeaderBar` button types (minimize, maximize, close). |
| `Platform` | PrimaryController.java | `Platform.runLater(Runnable)` - schedules a task on the JavaFX Application Thread. |
| `FXCollections` | PrimaryController.java | Factory for `ObservableList`; used to populate `ComboBox` items. |
| `ComboBox<T>` | PrimaryController.java | Dropdown selector control. |
| `Button` | PrimaryController.java | Clickable button control. |
| `Label` | PrimaryController.java | Non-editable text display. |
| `TextArea` | PrimaryController.java | Multi-line editable text control. |
| `TextField` | PrimaryController.java | Single-line editable text control. |
| `PasswordField` | PrimaryController.java | Single-line text control that masks input. |
| `CheckBox` | PrimaryController.java | Toggle control with a checked/unchecked state. |
| `VBox` | PrimaryController.java | Vertical layout container. |
| `Priority` | PrimaryController.java | Enum (`ALWAYS`, `NEVER`) for `HBox`/`VBox` grow constraints. |
| `Insets` | PrimaryController.java | Represents padding/margins (top, right, bottom, left). |
| `Clipboard` | PrimaryController.java | System clipboard access. |
| `ClipboardContent` | PrimaryController.java | Data container placed on the clipboard (plain text, HTML, etc.). |
| `KeyCode` | PrimaryController.java | Enum of keyboard keys (e.g. `ENTER`, `ESCAPE`). |
| `KeyEvent` | PrimaryController.java | Event fired when a key is pressed/released. |
| `FileChooser` | PrimaryController.java | OS-native open/save file dialog. |
| `FileChooser.ExtensionFilter` | PrimaryController.java | Filters files by extension in the file dialog. |
| `@FXML` | PrimaryController.java | Annotation that injects a field or method from an FXML file. |

---

## 3. JavaParser (`com.github.javaparser.*`)

Used exclusively in `JavaFxAdapter` to parse JavaFX Java source code into an AST.

| Class | Short Description |
|---|---|
| `StaticJavaParser` | Entry point - `StaticJavaParser.parse(String)` parses Java source into a `CompilationUnit`. |
| `ParseProblemException` | Thrown when the source has syntax errors the parser cannot recover from. |
| `CompilationUnit` | Root AST node of a `.java` file; exposes `findAll()`, `getClassByName()`, etc. |
| `MethodDeclaration` | AST node for a method definition; provides name, parameters, and body. |
| `Parameter` | A single method parameter (name + type). |
| `VariableDeclarator` | The name and optional initializer inside a variable declaration. |
| `BlockStmt` | A `{ ... }` block; used to iterate statements inside a method body. |
| `MethodCallExpr` | AST node for `obj.method(args)` - the main node inspected to find setters. |
| `ObjectCreationExpr` | AST node for `new ClassName(args)` - used to detect UI widget instantiation. |
| `VariableDeclarationExpr` | AST node for `Type name = value` statements. |
| `NameExpr` | A simple identifier (variable name) used as an expression. |
| `AssignExpr` | An assignment expression `left = right`. |
| `StringLiteralExpr` | A string literal value `"..."` in the AST. |
| `BinaryExpr` | A binary operation (`+`, `-`, `&&`, etc.) in the AST. |

---

## 4. RichTextFX (`org.fxmisc.richtext.*`, `org.fxmisc.flowless.*`)

Used in `PrimaryController` to provide syntax-highlighted code editors.

| Class | Short Description |
|---|---|
| `CodeArea` | A code editor control from RichTextFX with per-character CSS class styling. Replaces plain `TextArea`. |
| `VirtualizedScrollPane<CodeArea>` | A scroll pane that virtualizes rendering for large documents (only renders visible lines). |
| `StyleSpans<Collection<String>>` | An immutable sequence of (length, CSS-classes) pairs that drives syntax highlighting. |
| `StyleSpansBuilder<Collection<String>>` | Builder used to append style regions; call `create()` to finish and produce a `StyleSpans`. |

Key method calls:
- `codeArea.replaceText(String)` - replaces entire content.
- `codeArea.setStyleSpans(int, StyleSpans)` - applies computed highlighting.
- `codeArea.textProperty().addListener(...)` - triggers re-highlight on every edit.
- `builder.add(Collection<String>, int length)` - adds one styled region.

---

## 5. W3C DOM / Java XML API (`org.w3c.dom.*`, `javax.xml.parsers.*`)

Used in `FxmlAdapter` (and `JavaFxAdapter` for FXML generation) to parse and build XML documents.

| Class / Method | Short Description |
|---|---|
| `DocumentBuilderFactory.newInstance()` | Factory method that creates a `DocumentBuilderFactory`. |
| `DocumentBuilderFactory.setFeature(...)` | Disables dangerous XML features (XXE protection). |
| `DocumentBuilder` | Created from the factory; can parse an XML string into a `Document`. |
| `DocumentBuilder.parse(InputSource)` | Parses XML bytes/string into a W3C `Document` tree. |
| `Document` | The root of a parsed XML tree; exposes `getDocumentElement()`. |
| `Element` | A single XML element (`<Button fx:id="btn">`); provides `getAttribute()`, `getChildNodes()`. |
| `NodeList` | An ordered list of `Node`/`Element` objects returned by `getElementsByTagName()`. |
| `DefaultHandler` | SAX event handler base class (used to suppress DTD/validation warnings). |
| `XMLConstants` | Holds constants like `FEATURE_SECURE_PROCESSING` for safe XML parsing. |

---

## 6. Java HTTP Client (`java.net.http.*`)

Used in `AIService` to call the Google Gemini REST API.

| Class / Method | Short Description |
|---|---|
| `HttpClient` | The main HTTP client. `HttpClient.newHttpClient()` creates a default instance. |
| `HttpClient.send(request, bodyHandler)` | Sends a request synchronously and returns an `HttpResponse`. |
| `HttpRequest` | Immutable HTTP request object (URL, method, headers, body). |
| `HttpRequest.newBuilder()` | Fluent builder for constructing an `HttpRequest`. |
| `HttpRequest.BodyPublishers.ofString(String)` | Wraps a `String` as the request body. |
| `HttpResponse<T>` | The server's response; `.body()` returns the parsed response body. |
| `HttpResponse.BodyHandlers.ofString()` | Handler that reads the response body as a `String`. |
| `URI.create(String)` | Parses a URL string into a `URI` for `HttpRequest`. |

---

## 7. Java Compiler API (`javax.tools.*`)

Used in `PreviewLauncher` to compile generated JavaFX code in memory.

| Class / Method | Short Description |
|---|---|
| `ToolProvider.getSystemJavaCompiler()` | Returns the JDK's built-in `JavaCompiler` (returns `null` in a JRE-only environment). |
| `JavaCompiler` | Programmatic interface to `javac`; used to compile `.java` source files. |
| `JavaCompiler.getTask(...)` | Creates a compilation task from a file list and options. |
| `DiagnosticCollector<JavaFileObject>` | Collects compile errors/warnings so they can be displayed to the user. |
| `JavaFileObject` | Represents a `.java` or `.class` file in the compiler API. |
| `StandardJavaFileManager` | Manages the file I/O for the compiler (source files, output directories). |

---

## 8. Java AWT & ImageIO (`java.awt.*`, `javax.imageio.*`)

Used in `AIService` to resize screenshot images before uploading to the Gemini API.

| Class / Method | Short Description |
|---|---|
| `BufferedImage` | An in-memory raster image; used as the canvas for resizing. |
| `ImageIO.read(File)` | Reads an image file (PNG, JPG, etc.) from disk into a `BufferedImage`. |
| `ImageIO.write(BufferedImage, format, stream)` | Encodes a `BufferedImage` to a byte stream (JPEG format used here). |
| `Graphics2D` | 2D rendering context; obtained from `BufferedImage.createGraphics()`. |
| `Graphics2D.drawImage(...)` | Draws the original image scaled to a new size onto the `BufferedImage`. |
| `Graphics2D.setRenderingHint(...)` | Sets quality hints (e.g. bilinear interpolation) for the resize operation. |
| `RenderingHints` | Constants class for rendering quality flags (`KEY_INTERPOLATION`, `VALUE_INTERPOLATION_BILINEAR`, etc.). |

---

## 9. Notable Standard Java Classes

These are standard library classes whose use is non-trivial in context.

| Class | Used in | Short Description |
|---|---|---|
| `java.util.Properties` | AppSettings.java | A key-value store that can load/store `.properties` files via `Reader`/`Writer`. |
| `java.util.Base64` | AIService.java | Encodes raw bytes to Base64 strings for embedding images in JSON. `Base64.getEncoder().encodeToString(byte[])`. |
| `java.util.regex.Pattern` | WinFormsAdapter.java, PrimaryController.java | Compiled regular expression. `Pattern.compile(String)` is called once; `matcher.find()` is used to scan text. |
| `java.util.regex.Matcher` | WinFormsAdapter.java, PrimaryController.java | Executes a `Pattern` against a specific input string. `.group(int)` extracts named capture groups. |
| `java.nio.file.Files` | Multiple files | Utility for reading (`Files.readString`), writing, and creating directories with one method call. |
| `java.nio.file.Path` | Multiple files | Represents a file-system path; used with `Files` methods instead of legacy `File`. |
| `java.io.ByteArrayOutputStream` | AIService.java | An in-memory output stream; used to capture the compressed image bytes before Base64 encoding. |
| `javax.tools.ToolProvider` | PreviewLauncher.java | Provides access to the JDK's `JavaCompiler` at runtime. |

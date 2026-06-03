# Plan: FrameworkAdapter Plugin Architecture (UIPorter)

## Context

`UIConverter.java` (~2200 lines) is monolithic - all parsers, generators, type maps, and helpers
live in one file. `PrimaryController` dispatches conversions with a hardcoded
`if (from.startsWith("JavaFX")...)`. Adding Tkinter or ImGUI today means editing both files
extensively.

The fix: split into a `FrameworkAdapter` interface + a registry, extract shared utilities,
and move each framework into its own class. After this:
**adding a new framework = one new file + one registration line.**

---

## Target Package Layout

```
com.jabcodex.uiporter/
  ├── App.java                   ← minor: register adapters
  ├── PrimaryController.java     ← 4 targeted changes
  ├── model/
  │   ├── Node.java
  │   ├── AppMetadata.java
  │   └── ConversionOptions.java
  ├── core/
  │   ├── FrameworkAdapter.java  ← NEW interface
  │   ├── FrameworkRegistry.java ← NEW singleton
  │   └── ConversionUtils.java  ← NEW: shared statics
  └── adapters/
      ├── JavaFxAdapter.java
      └── WinFormsAdapter.java
```

---

## Steps

### Phase 1 - Foundations *(all 4 steps parallel, all new files)*

**Step 1.1 - Extract model classes**
Copy `Node`, `AppMetadata`, `ConversionOptions` inner classes from `UIConverter.java` into
`model/` as top-level classes, add correct package declarations. Zero logic changes.

**Step 1.2 - `FrameworkAdapter` interface** (`core/FrameworkAdapter.java`)

```java
public interface FrameworkAdapter {
    String getDisplayName();          // "JavaFX (Java)"
    String[] getFileExtensions();     // {".java"}
    String getSyntaxLanguage();       // "java" or "cs"
    AppMetadata parse(String source);
    String generate(AppMetadata app, Map<String, Object> options);
    default String generate(AppMetadata app) { return generate(app, Map.of()); }
}
```

**Step 1.3 - `FrameworkRegistry`** (`core/FrameworkRegistry.java`)
Singleton. Holds a `List<FrameworkAdapter>`.
Methods: `register(FrameworkAdapter)`, `get(String displayName)`, `getDisplayNames()`.

**Step 1.4 - `ConversionUtils`** (`core/ConversionUtils.java`)
Move these shared statics verbatim from `UIConverter`:

- Maps: `NAMED_COLORS`, `POS_TO_ALIGN`, `ALIGN_TO_POS`
- Color helpers: `rgbToHex(String,String,String)`, `rgbToHex(int,int,int)`, `rgbFromArgb(Matcher,int)`, `isSystemColorName(String)`
- Font helpers: `isFontInstalled(String)`
- String helpers: `simpleName(String)`, `quoted(String)`, `toClassName(String)`
- Graph helpers: `getOrMake(AppMetadata,String)`, `findById(AppMetadata,String)`, `addChild(AppMetadata,Node,Node)`, `computeRoots(AppMetadata)`

---

### Phase 2 - Adapters *(parallel, depend on Phase 1)*

**Step 2.1 - `JavaFxAdapter`** (`adapters/JavaFxAdapter.java`)

- `getDisplayName()` → `"JavaFX (Java)"`
- `getFileExtensions()` → `{".java"}`
- `getSyntaxLanguage()` → `"java"`
- `parse(source)` - move `parseJavaFx()` body here; delegates to `ConversionUtils` for shared ops
- `generate(app, options)` - move `generateJavaFx()` body here
- Private helpers that stay inside: `parseFxStyle()`, `resolveJfxColor()`, `resolveFactoryMethod()`,
  `stringValue()`, `resolveStringWithParams()`, `resolveNumericWithParams()`, `writeJfxTree()`,
  `emitRootStyle()`
- Owns `TO_WINFORMS` component-name map (JavaFX → canonical WinForms names)

**Step 2.2 - `WinFormsAdapter`** (`adapters/WinFormsAdapter.java`)

- `getDisplayName()` → `"WinForms (C#)"`
- `getFileExtensions()` → `{".cs", ".cpp", ".h"}`
- `getSyntaxLanguage()` → `"cs"`
- `parse(source)` - move all WF_* regex patterns, `normalizeCs()`, `parseWinForms()` here
- `generate(app, options)` - move `generateWinForms()` here;
  reads `options.get("matchWinFormsDefaultFont")` and `options.get("skipFontIfNotInstalled")`
- Private helpers that stay inside: `wfFontStyle()`, `toWinForms()`, `toHorizontalAlignment()`,
  `writeWfTree()`, `splitFontWeight()`
- Owns `TO_JAVAFX` component-name map (WinForms → canonical JavaFX names)

---

### Phase 3 - Wire Up *(depends on Phase 2)*

**Step 3.1 - `App.java`**
Add before `launch()`:

```java
FrameworkRegistry r = FrameworkRegistry.getInstance();
r.register(new JavaFxAdapter());
r.register(new WinFormsAdapter());
```

**Step 3.2 - `PrimaryController.java`** - 4 targeted changes:

1. `initialize()` - populate ComboBoxes dynamically:
   ```java
   List<String> names = FrameworkRegistry.getInstance().getDisplayNames();
   fromCombo.setItems(FXCollections.observableArrayList(names));
   toCombo.setItems(FXCollections.observableArrayList(names));
   ```
   Remove static `<items>` from FXML (or keep as default; registry wins at runtime).

2. `handleConvert()` - replace hardcoded if/else:
   ```java
   FrameworkAdapter src = FrameworkRegistry.getInstance().get(from);
   FrameworkAdapter tgt = FrameworkRegistry.getInstance().get(to);
   Map<String, Object> opts = buildOptionsMap();  // reads checkboxes into map
   AppMetadata app = src.parse(code);
   String result   = tgt.generate(app, opts);
   ```

3. `langFor()` - replace hardcoded "cs"/"java" check:
   ```java
   FrameworkAdapter a = FrameworkRegistry.getInstance().get(combo.getValue());
   return a != null ? a.getSyntaxLanguage() : "java";
   ```

4. `handleOpenFile()` / `handleSaveAs()` - build `ExtensionFilter` from `getFileExtensions()`
   of the currently selected adapter instead of hardcoded strings.

**Step 3.3 - Delete `UIConverter.java`**
Nothing outside the package uses it. Delete cleanly.

---

### Phase 4 - `module-info.java` *(parallel with Phase 3)*

Open the three new sub-packages so JavaFX FXML reflection can reach the controller and
JavaParser can still be used by adapters:

```java
opens com.jabcodex.uiporter.model to javafx.base;
opens com.jabcodex.uiporter.core  to javafx.base;
opens com.jabcodex.uiporter.adapters to javafx.base;
```

---

## Relevant Files to Modify

| File | Change |
|---|---|
| `UIConverter.java` | Source for extraction; **deleted** at end |
| `App.java` | +3 registration lines |
| `PrimaryController.java` | 4 targeted changes |
| `module-info.java` | Open 3 new sub-packages |

## New Files to Create

| File | Description |
|---|---|
| `model/Node.java` | Extracted inner class |
| `model/AppMetadata.java` | Extracted inner class |
| `model/ConversionOptions.java` | Extracted inner class; kept for Settings tab wiring |
| `core/FrameworkAdapter.java` | Interface - 5 method contract |
| `core/FrameworkRegistry.java` | Singleton - register / lookup / list adapters |
| `core/ConversionUtils.java` | ~15 shared static helpers and 3 shared maps |
| `adapters/JavaFxAdapter.java` | Full JavaFX parse + generate logic |
| `adapters/WinFormsAdapter.java` | Full WinForms parse + generate logic |

---

## Verification Checklist

- [ ] `mvn clean compile` - no errors
- [ ] `mvn javafx:run` - app opens; both ComboBoxes show "JavaFX (Java)" and "WinForms (C#)"
- [ ] JavaFX → WinForms output **identical** to current output
- [ ] WinForms → JavaFX with checkbox options **identical** to current output
- [ ] Settings checkboxes still function (WinForms adapter reads its own option keys from the map)
- [ ] Open File dialog shows `.java` / `.cs` filters based on selected adapter
- [ ] Save As dialog shows correct extension for target adapter

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| `Node.type` stays as JavaFX names | Already works as a clean framework-agnostic vocabulary; each adapter maps to/from these internally |
| `Map<String,Object> options` at interface level | `ConversionOptions` fields become map entries; WinForms adapter reads its own keys; Settings FXML is **untouched** |
| `TO_WINFORMS` lives in `JavaFxAdapter` | It maps *from* JavaFX types - logically belongs to the JavaFX adapter |
| `TO_JAVAFX` lives in `WinFormsAdapter` | It maps *from* WinForms types |
| `NAMED_COLORS` / `POS_TO_ALIGN` / `ALIGN_TO_POS` go to `ConversionUtils` | Both adapters use them; no duplication |
| No backward-compat shim for `UIConverter` | Zero external callers; delete it cleanly |
| FXML `<items>` left in place | They serve as design-time defaults in Scene Builder; registry overrides at runtime |
| `Node.type` property key strings unchanged | These are the adapter contract (`"text"`, `"backColor"`, `"fontFamily"` etc.) - changing them would break both adapters |

---

## How to Add a New Framework After This Refactor

```java
// 1. Create adapters/TkinterAdapter.java
public class TkinterAdapter implements FrameworkAdapter {
    public String getDisplayName()      { return "Tkinter (Python)"; }
    public String[] getFileExtensions() { return new String[]{".py"}; }
    public String getSyntaxLanguage()   { return "python"; }
    public AppMetadata parse(String source) { /* regex/AST parse of .py */ }
    public String generate(AppMetadata app, Map<String,Object> opts) { /* emit Python */ }
}

// 2. In App.java - one line:
r.register(new TkinterAdapter());

// Done - appears in both dropdowns, correct file filters, correct syntax language
```

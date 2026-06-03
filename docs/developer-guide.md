# Developer Guide

This guide covers how to build UIPorter from source, run it in development,
and extend it by adding a new framework adapter.

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 21 or later | Compile and run the application |
| Apache Maven | 3.8+ | Build and dependency management |
| Git | any | Clone the repository |

---

## Building from Source

### 1. Clone the repository

```
git clone https://github.com/JunaidIRF/UIPorter.git
cd UIPorter
```

### 2. Run in development mode

```
mvn javafx:run
```

This compiles everything and launches the JavaFX window directly.

### 3. Build a release package

```
build-release.bat
```

This script calls `mvn package` and then `jpackage` to produce a
self-contained application in `release/UIPorter/` with a bundled JVM.
No separate Java installation is needed to run the release.

---

## Project Structure

```
UIPorter/
├── pom.xml                      Maven build configuration
├── build-release.bat            Release packaging script
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── module-info.java
│   │   │   └── com/jabcodex/uiporter/
│   │   │       ├── App.java
│   │   │       ├── PrimaryController.java
│   │   │       ├── AppSettings.java
│   │   │       ├── AIService.java
│   │   │       ├── PreviewLauncher.java
│   │   │       ├── ConvertCheck.java
│   │   │       ├── model/          AppMetadata.java, Node.java
│   │   │       ├── core/           FrameworkAdapter.java, FrameworkRegistry.java,
│   │   │       │                   ConversionUtils.java
│   │   │       └── adapters/       JavaFxAdapter.java, FxmlAdapter.java,
│   │   │                           WinFormsAdapter.java
│   │   └── resources/
│   │       └── com/jabcodex/uiporter/
│   │           ├── primary.fxml    Main window layout
│   │           └── *.css           Stylesheets
│   └── test/java/                  Unit tests (JUnit)
├── docs/                           This documentation
├── submission/                     Semester project submission files
└── release/                        Built release output
```

---

## Key Maven Dependencies

| Dependency | Version | Why |
|-----------|---------|-----|
| `org.openjfx:javafx-controls` | 25 | UI controls (Button, Label, etc.) |
| `org.openjfx:javafx-fxml` | 25 | FXML loader |
| `org.fxmisc.richtext:richtextfx` | 0.11.7 | Syntax-highlighted code editors |
| `com.github.javaparser:javaparser-core` | 3.x | AST parsing of JavaFX Java source |

---

## Adding a New Framework Adapter

To add support for a new framework (e.g. HTML/CSS, Qt, Swing):

### 1. Create the adapter class

```java
package com.jabcodex.uiporter.adapters;

import com.jabcodex.uiporter.core.FrameworkAdapter;
import com.jabcodex.uiporter.model.AppMetadata;
import java.util.Map;

public class MyFrameworkAdapter implements FrameworkAdapter {

    @Override
    public String getDisplayName() {
        return "MyFramework";
    }

    @Override
    public AppMetadata parse(String source) throws Exception {
        AppMetadata app = new AppMetadata();
        // TODO: parse source, populate app.rootNode tree
        return app;
    }

    @Override
    public String generate(AppMetadata app, Map<String, String> options) throws Exception {
        StringBuilder sb = new StringBuilder();
        // TODO: walk app.rootNode tree, emit target code
        return sb.toString();
    }
}
```

### 2. Register the adapter in App.java

```java
@Override
public void start(Stage stage) throws Exception {
    FrameworkRegistry reg = FrameworkRegistry.getInstance();
    reg.register(new JavaFxAdapter());
    reg.register(new FxmlAdapter());
    reg.register(new WinFormsAdapter());
    reg.register(new MyFrameworkAdapter());   // <-- add this line
    // ...
}
```

That is all. The dropdowns in the UI are built from the registry at runtime,
so the new framework will appear automatically in both the source and target
selectors.

### 3. Use standard JavaFX type names

All `Node.type` values **must** use standard JavaFX names (e.g. `"Button"`,
`"TextField"`, `"VBox"`). Use `ConversionUtils` and the existing type maps
in `WinFormsAdapter` as a reference for the correct names.

---

## AI Assistant Setup (Optional)

The AI tab uses the Google Gemini API. To enable it:

1. Get a free API key from [Google AI Studio](https://aistudio.google.com).
2. Open UIPorter → Settings tab → paste the key into the **Gemini API Key** field.
3. Click **Save**.

The key is stored in `~/.uiporter/settings.properties` on the local machine.
It is never sent anywhere except to the Gemini API endpoint.

---

## Preview Feature Requirements

The **Live Preview** button compiles and runs the generated JavaFX code in a
subprocess. This requires:

- **JDK 25 or later** (not just a JRE) - `javac` must be available on PATH
- JavaFX modules accessible at runtime

If running from the bundled release, the bundled JVM is JDK 21 and does not
include `javac`. Preview works only when a full JDK 25+ is installed separately
and is on the system PATH.

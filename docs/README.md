<h1 align="center">UIPorter - Documentation</h1>

<p align="center">
  <b>Technical documentation for the UIPorter semester project.</b><br>
  <sub>University Second Semester &nbsp;·&nbsp; Object-Oriented Programming</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/language-Java%2021-orange?style=flat-square&logo=openjdk" alt="Java">
  <img src="https://img.shields.io/badge/framework-JavaFX-2ecc71?style=flat-square" alt="JavaFX">
  <img src="https://img.shields.io/badge/build-Maven-C71A36?style=flat-square&logo=apachemaven" alt="Maven">
  <img src="https://img.shields.io/badge/AI-Gemini-8e44ad?style=flat-square&logo=googlegemini" alt="Gemini">
</p>

---

<p align="center">
  UIPorter converts UI source code between <b>JavaFX Java</b>, <b>JavaFX FXML</b>, and <b>WinForms C#</b><br>
  using a framework-neutral Intermediate Representation as the common exchange format.
</p>

---

## 📚 Documentation Index

| File | Contents |
|------|----------|
| [README.md](README.md) | This file - project overview and index |
| [architecture.md](architecture.md) | System layers, conversion pipeline, OOP design patterns |
| [data-model.md](data-model.md) | `AppMetadata` and `Node` - the Intermediate Representation |
| [adapters.md](adapters.md) | How each adapter parses and generates code |
| [developer-guide.md](developer-guide.md) | Build instructions and how to extend the project |

---

## 🔄 Conversion Matrix

| From \ To | JavaFX Java | FXML | WinForms C# |
|:---:|:---:|:---:|:---:|
| **JavaFX Java** | - | ✅ | ✅ |
| **FXML** | ✅ | - | ✅ |
| **WinForms C#** | ✅ | ✅ | - |

> All six direction combinations work because every adapter converts **to/from the same IR**, not directly to one another.

---

## 🛠️ Tech Stack

| Property | Value |
|----------|-------|
| Language | Java 21 |
| UI Framework | JavaFX 25 |
| Build Tool | Apache Maven 3 |
| Parser Library | JavaParser 3.x (AST for JavaFX Java source) |
| AI Integration | Google Gemini 1.5 Flash (free tier) |
| Code Editor | RichTextFX 0.11.7 (syntax highlighting) |
| Release Packaging | jpackage (self-contained runtime) |

---

## 🏗️ Source Structure

```
src/main/java/com/jabcodex/uiporter/
├── App.java                   Entry point - registers adapters, loads FXML
├── PrimaryController.java     UI event handling, connects buttons to converters
├── AppSettings.java           Persistent user settings (~/.uiporter/)
├── AIService.java             Gemini API client
├── PreviewLauncher.java       Compiles and runs generated JavaFX in a subprocess
├── ConvertCheck.java          Input validation helper
├── model/
│   ├── AppMetadata.java       Root IR object (title, size, root Node)
│   └── Node.java              Single UI element in the IR tree
├── core/
│   ├── FrameworkAdapter.java  Interface - parse() + generate()
│   ├── FrameworkRegistry.java Singleton adapter registry
│   └── ConversionUtils.java   Shared color/font/name utilities
└── adapters/
    ├── JavaFxAdapter.java     Handles JavaFX Java (.java) source
    ├── FxmlAdapter.java       Handles JavaFX FXML (.fxml) source
    └── WinFormsAdapter.java   Handles WinForms C# (.cs) source
```

---

## 🏗️ Architecture at a Glance

```
Source Code  →  Adapter.parse()  →  AppMetadata (IR)  →  Adapter.generate()  →  Target Code
```

**Design patterns used:** Strategy · Singleton · Composite · Facade · Template Method

> See [architecture.md](architecture.md) for the full breakdown.

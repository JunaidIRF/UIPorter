# UIPorter - Build & Usage Guide

## Table of Contents
1. [What is UIPorter?](#1-what-is-uiporter)
2. [Prerequisites](#2-prerequisites)
3. [Building the Standalone App](#3-building-the-standalone-app)
4. [Installing / Sharing the App](#4-installing--sharing-the-app)
5. [Using the App](#5-using-the-app)
6. [Preview Feature](#6-preview-feature)
7. [AI Assistant](#7-ai-assistant)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. What is UIPorter?

UIPorter converts UI layouts between frameworks - paste in a WinForms XML or JavaFX FXML definition and get the equivalent code for the other framework, with live preview support.

---

## 2. Prerequisites

### To Build
| Requirement | Version | Notes |
|---|---|---|
| **JDK** | 21 or newer (JDK 25 recommended) | Eclipse Adoptium / OpenJDK work fine |
| **Maven** | 3.9+ | NetBeans bundles Maven - no separate install needed if you use NetBeans |

> **JAVA_HOME must be set** to your JDK folder, e.g.:  
> `C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot`  
> To check: open Command Prompt → type `echo %JAVA_HOME%`

### To Run (end user)
No extra installs. The built app folder contains everything it needs, **including a bundled Java runtime**.

---

## 3. Building the Standalone App

### Step 1 - Open a Command Prompt in the project folder
Right-click the `UIPorter` project folder → **"Open in Terminal"**, or:
```
cd F:\Path\To\UIPorter
```

### Step 2 - (Optional) Add your app icon
If you have a custom `icon.png`:
- Copy it to `src\main\resources\com\jabcodex\uiporter\icons\icon.png`

If you also want a custom `.exe` icon (shown in File Explorer before the app starts):
- Convert `icon.png` to `icon.ico` using any free tool (e.g. https://convertio.co)
- Copy `icon.ico` to `src\main\resources\com\jabcodex\uiporter\icons\icon.ico`

### Step 3 - Run the build script
```
build-release.bat
```

The script will automatically:
1. Find Maven (checks PATH, M2_HOME, and common NetBeans locations)
2. Find `jpackage` via `JAVA_HOME`
3. Compile and package the project with Maven
4. Bundle everything into a self-contained app-image

### Step 4 - Build output
If successful you'll see:
```
 Done! Portable app is at: release\UIPorter\
 Run:  release\UIPorter\UIPorter.exe
```

The `release\UIPorter\` folder contains the complete portable app. **No installation required.**

---

## 4. Installing / Sharing the App

UIPorter is fully **portable** - the entire app is in one folder.

### To use it yourself
1. Copy the `release\UIPorter\` folder anywhere on your PC (e.g. Desktop, USB drive)
2. Double-click `UIPorter.exe` to launch

### To share with someone
1. Copy the `release\UIPorter\` folder to a USB drive or zip it
2. Send the folder / zip to the recipient
3. They extract it and double-click `UIPorter.exe` - **no Java install needed on their machine**

> The folder will be roughly 200-300 MB due to the bundled Java runtime.

---

## 5. Using the App

### Convert Tab
1. Select the **source framework** (e.g. WinForms) from the left dropdown
2. Select the **target framework** (e.g. JavaFX) from the right dropdown
3. Paste your UI code/XML into the **left editor**
4. Click **Convert** - the result appears in the right editor
5. Click **Copy** to copy the output, or **Save** to save it to a file

### Settings Tab
- **Match default font**: when enabled, attempts to preserve font sizes during conversion
- **WinForms panel nesting**: controls how nested panels are handled in WinForms output

---

## 6. Preview Feature

The **Preview** button (rocket icon) compiles and launches a live JavaFX window showing your converted UI.

**Requirements:**
- A JDK (not just JRE) must be installed and `JAVA_HOME` must point to it
- This is because the preview feature compiles generated code on the fly

**How it works:**
- UIPorter compiles the generated JavaFX code using the bundled compiler
- It launches a separate window showing the rendered UI

**If Preview does not work:**
1. Ensure `JAVA_HOME` is set to a JDK (not just a JRE)  
   `echo %JAVA_HOME%` should print something like `C:\Program Files\Eclipse Adoptium\jdk-25...`
2. Make sure the JDK version matches - JDK 21+ required

---

## 7. AI Assistant

The **AI tab** lets you describe a UI in natural language and have it generated, improved, or analyzed.

**Setup:**
1. Go to the **Settings tab**
2. Enter your **Gemini API key** in the API Key field  
   (Get one free at https://aistudio.google.com/app/apikey)
3. The key is saved locally at `%USERPROFILE%\.uiporter\settings.properties`

**Features:**
- **Generate from prompt** - describe a UI layout and get JavaFX code
- **Improve code** - paste in existing code to get improvements
- **AI Image** - describe a UI and get an image mockup (if supported)

---

## 8. Troubleshooting

### "JAVA_HOME is not set" or "jpackage not found"
Set JAVA_HOME to your JDK folder:
1. Press **Win + R** → type `sysdm.cpl` → **Advanced** tab → **Environment Variables**
2. Under **System variables**, click **New**
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot` (adjust path)
3. Click OK, close and reopen your terminal

### "Maven not found"
The build script checks several locations automatically. If it still fails:
- Add Maven's `bin` folder to your PATH, or
- Install NetBeans IDE (which bundles Maven)

### App crashes on launch
- Ensure you are running `UIPorter.exe` from inside the `UIPorter\` folder, not by moving just the `.exe`
- The entire folder must stay together

### Preview window doesn't appear
- Check `JAVA_HOME` points to a full JDK (not a JRE)
- Try running `%JAVA_HOME%\bin\java.exe -version` in Command Prompt - it should print the version

### App icon not showing (taskbar)
- Make sure `icon.png` is in `src\main\resources\com\jabcodex\uiporter\icons\` **before building**
- For the `.exe` file icon in File Explorer, also provide `icon.ico` in the same folder
- Rebuild with `build-release.bat` after adding the icon

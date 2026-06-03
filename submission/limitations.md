# UIPorter - Limitations

1. **Only JavaFX and WinForms are supported.**
   UIPorter is focused on JavaFX and WinForms. It does not currently support Swing,
   Android, Flutter, React, or other UI frameworks. Converting between FXML and JavaFX
   Java is the easiest and most accurate case since both are the same framework - just
   written in a different format. WinForms support covers the most commonly used controls
   and properties.

2. **Event handler logic is out of scope for conversion.**
   UIPorter converts the visual structure of a UI - layout, controls, colours, fonts,
   and sizes. The logic inside event handlers (what happens when a button is clicked) is
   application-specific and intentionally left for the developer to wire up after conversion.

3. **Some advanced UI features are not converted.**
   Features like animations, data bindings, and custom cell rendering go beyond static
   layout conversion and are not carried across. Common layout properties, colours, fonts,
   and control types are all fully supported - these advanced features represent a smaller
   subset of typical UI code.

4. **The AI assistant and live preview require external setup.**
   The AI tab needs an internet connection and a free Google API key. Live preview is
   available for JavaFX output only. These are dependencies on external services rather
   than limitations of the conversion engine itself.


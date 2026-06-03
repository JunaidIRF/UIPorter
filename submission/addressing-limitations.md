# UIPorter - How the Application Addresses Its Limitations

1. **Focused scope → IR design makes expanding to new frameworks straightforward.**
   All conversions go through one shared data model (`AppMetadata`), so no adapter talks
   to another directly. Adding a new framework only requires writing one new adapter class
   - every existing framework works with it automatically. The current focus on JavaFX and
   WinForms covers the two most common desktop UI platforms for Java and .NET developers.

2. **Event logic out of scope → handler stubs are preserved in the output.**
   Even though the logic inside handlers is not converted, UIPorter records that a handler
   exists and writes an empty method stub in the output. This means the developer does not
   have to re-add the wiring manually - just fill in the body. The AI "Improve UI" mode
   can also generate reasonable handler logic from a description.

3. **Advanced features → unknown styles are passed through and core features are complete.**
   Any CSS style that the app does not have a dedicated rule for is stored in an `extraCss`
   field and copied to the output unchanged, so styling is never silently lost. All standard
   properties - colours, fonts, sizes, padding, alignment, visibility, borders - are fully
   supported. Animations and bindings are the only area without a current solution.

4. **External dependencies → lightweight by design; AI works as a practical fallback.**
   Using the Google Gemini API (free tier) keeps the app small with no local model needed.
   When the converter output needs enhancement, the AI "Image → UI" mode lets you upload
   a screenshot of the original app and regenerate the layout from that image. Live preview
   is JavaFX-only to avoid shipping a full .NET runtime inside the package.


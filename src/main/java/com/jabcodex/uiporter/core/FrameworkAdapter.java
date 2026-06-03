package com.jabcodex.uiporter.core;

import com.jabcodex.uiporter.model.AppMetadata;
import java.util.Map;

public interface FrameworkAdapter {
    /** Human-readable name shown in the UI dropdowns, e.g. "JavaFX (Java)". */
    String getDisplayName();

    /** File extensions this framework uses, e.g. {".java"} or {".cs", ".cpp", ".h"}. */
    String[] getFileExtensions();

    /**
     * Syntax highlighting language key returned to the controller.
     * Recognised values: "java", "cs".  Unknown values fall back to Java highlighting.
     */
    String getSyntaxLanguage();

    /** Parse source code into a framework-agnostic AppMetadata model. */
    AppMetadata parse(String source);

    /** Generate source code from an AppMetadata model with optional settings. */
    String generate(AppMetadata app, Map<String, Object> options);

    /** Convenience overload - no options. */
    default String generate(AppMetadata app) {
        return generate(app, Map.of());
    }
}

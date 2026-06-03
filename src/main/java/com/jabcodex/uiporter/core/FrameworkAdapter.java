package com.jabcodex.uiporter.core;

import com.jabcodex.uiporter.model.AppMetadata;
import java.util.Map;

public interface FrameworkAdapter {
    String getDisplayName();

    String[] getFileExtensions();

    String getSyntaxLanguage();

    AppMetadata parse(String source);

    String generate(AppMetadata app, Map<String, Object> options);

    default String generate(AppMetadata app) {
        return generate(app, Map.of());
    }
}

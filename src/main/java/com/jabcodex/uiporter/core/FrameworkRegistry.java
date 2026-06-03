package com.jabcodex.uiporter.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FrameworkRegistry {

    private static final FrameworkRegistry INSTANCE = new FrameworkRegistry();
    private final List<FrameworkAdapter> adapters = new ArrayList<>();

    private FrameworkRegistry() {}

    public static FrameworkRegistry getInstance() {
        return INSTANCE;
    }

    public void register(FrameworkAdapter adapter) {
        adapters.add(adapter);
    }

    /** Returns the adapter whose {@link FrameworkAdapter#getDisplayName()} matches, or null. */
    public FrameworkAdapter get(String displayName) {
        if (displayName == null) return null;
        for (FrameworkAdapter a : adapters) {
            if (displayName.equals(a.getDisplayName())) return a;
        }
        return null;
    }

    public List<String> getDisplayNames() {
        List<String> names = new ArrayList<>();
        for (FrameworkAdapter a : adapters) names.add(a.getDisplayName());
        return Collections.unmodifiableList(names);
    }
}

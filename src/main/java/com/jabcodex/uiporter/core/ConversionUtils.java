package com.jabcodex.uiporter.core;

import com.jabcodex.uiporter.model.AppMetadata;
import com.jabcodex.uiporter.model.Node;

import java.util.*;

/**
 * Shared utilities used by every adapter: string helpers and AppMetadata graph operations.
 * Genuinely shared data (NAMED_COLORS, rgbToHex) lives here too.
 * Framework-specific maps and methods live in each adapter class.
 */
public final class ConversionUtils {

    private ConversionUtils() {}

    // ── Component maps ─────────────────────────────────────────────────────────


    /** Common System.Drawing.Color names → hex. */
    public static final Map<String, String> NAMED_COLORS = new LinkedHashMap<>();

    static {
        NAMED_COLORS.put("Red",       "#FF0000");
        NAMED_COLORS.put("Blue",      "#0000FF");
        NAMED_COLORS.put("Green",     "#008000");
        NAMED_COLORS.put("Purple",    "#800080");
        NAMED_COLORS.put("White",     "#FFFFFF");
        NAMED_COLORS.put("Black",     "#000000");
        NAMED_COLORS.put("Gray",      "#808080");
        NAMED_COLORS.put("Grey",      "#808080");
        NAMED_COLORS.put("Yellow",    "#FFFF00");
        NAMED_COLORS.put("Orange",    "#FFA500");
        NAMED_COLORS.put("Pink",      "#FFC0CB");
        NAMED_COLORS.put("Cyan",      "#00FFFF");
        NAMED_COLORS.put("Magenta",   "#FF00FF");
        NAMED_COLORS.put("Brown",     "#A52A2A");
        NAMED_COLORS.put("Navy",      "#000080");
        NAMED_COLORS.put("Teal",      "#008080");
        NAMED_COLORS.put("Silver",    "#C0C0C0");
        NAMED_COLORS.put("Transparent", "transparent");
    }

    // ── Color helpers ──────────────────────────────────────────────────────────

    /** Converts three decimal R,G,B strings to a lowercase hex color like #90c226. */
    public static String rgbToHex(String r, String g, String b) {
        try {
            return String.format("#%02x%02x%02x",
                Integer.parseInt(r.trim()),
                Integer.parseInt(g.trim()),
                Integer.parseInt(b.trim()));
        } catch (NumberFormatException e) {
            return "#000000";
        }
    }

    /** Overload for int values, clamped to 0-255. */
    public static String rgbToHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x",
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)));
    }

    // ── String helpers ─────────────────────────────────────────────────────────

    /** Strips package prefix and generics from a type name, e.g. "java.util.List<String>" → "List". */
    public static String simpleName(String fullType) {
        if (fullType == null || fullType.isBlank()) return "";
        int lt = fullType.indexOf('<');
        String base = lt >= 0 ? fullType.substring(0, lt) : fullType;
        int i = base.lastIndexOf('.');
        return i >= 0 ? base.substring(i + 1) : base;
    }

    /** Wraps a value in double quotes, escaping internal backslashes and quotes. */
    public static String quoted(String value) {
        String safe = value == null ? "" : value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
        return "\"" + safe + "\"";
    }

    // ── Graph helpers ──────────────────────────────────────────────────────────

    public static Node getOrMake(AppMetadata app, String id) {
        Node n = findById(app, id);
        if (n != null) return n;
        n = new Node();
        n.id = id;
        app.allNodes.add(n);
        return n;
    }

    public static Node findById(AppMetadata app, String id) {
        if (id == null) return null;
        for (Node n : app.allNodes) {
            if (id.equals(n.id)) return n;
        }
        return null;
    }

    public static void addChild(AppMetadata app, Node parent, Node child) {
        if (!parent.children.contains(child)) parent.children.add(child);
        app.childIds.add(child.id);
    }

    public static List<Node> computeRoots(AppMetadata app) {
        if (app.sceneRootId != null) {
            Node root = findById(app, app.sceneRootId);
            if (root != null) return Collections.singletonList(root);
        }
        List<Node> roots = new ArrayList<>();
        for (Node n : app.allNodes) {
            if (!app.childIds.contains(n.id)) roots.add(n);
        }
        return roots.isEmpty() && !app.allNodes.isEmpty()
            ? new ArrayList<>(app.allNodes) : roots;
    }
}

package com.jabcodex.uiporter.adapters;

import com.jabcodex.uiporter.core.ConversionUtils;
import com.jabcodex.uiporter.core.FrameworkAdapter;
import com.jabcodex.uiporter.model.AppMetadata;
import com.jabcodex.uiporter.model.Node;

import java.util.*;
import java.util.regex.*;

/**
 * Parses WinForms (C# / C++/CLI) source code and generates WinForms output
 * from any AppMetadata.
 */
public class WinFormsAdapter implements FrameworkAdapter {

    @Override
    public String getDisplayName() { return "WinForms (C#)"; }

    @Override
    public String[] getFileExtensions() { return new String[]{".cs", ".cpp", ".h"}; }

    @Override
    public String getSyntaxLanguage() { return "cs"; }

    // ── WinForms regex patterns ────────────────────────────────────────────────

    private static final Pattern WF_DECL      = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\s*=\\s*\\(?new\\s+([\\w.]+)\\s*\\([^)]*\\)\\)?;");
    private static final Pattern WF_TEXT      = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Text\\s*=\\s*\"([^\"]*)\";");
    private static final Pattern WF_TITLE     = Pattern.compile(
        "(?<![.\\w])(?:this\\.)?Text\\s*=\\s*\"([^\"]*)\";");
    private static final Pattern WF_CLICK     = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Click\\s*\\+=");
    private static final Pattern WF_ADD       = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Controls(?:->|\\.)Add\\((?:this\\.)?([A-Za-z_]\\w+)\\);");
    private static final Pattern WF_FORM_ADD  = Pattern.compile(
        "(?:this\\.)?Controls(?:->|\\.)Add\\((?:this\\.)?([A-Za-z_]\\w+)\\);");
    private static final Pattern WF_SIZE      = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Size\\s*=\\s*(?:\\(?new\\s+)?(?:[\\w.]+\\.)?Size\\((\\d+)\\s*,\\s*(\\d+)\\)");
    private static final Pattern WF_BACK_HTML = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.BackColor\\s*=\\s*(?:[\\w.]+\\.)?ColorTranslator\\.FromHtml\\(\"([^\"]+)\"\\);");
    private static final Pattern WF_BACK_NAME = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.BackColor\\s*=\\s*(?:[\\w.]+\\.)?Color\\.(\\w+);");
    private static final Pattern WF_BACK_RGB  = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.BackColor\\s*=\\s*(?:[\\w.]+\\.)?Color\\.FromArgb\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\);");
    private static final Pattern WF_FORE_HTML = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.ForeColor\\s*=\\s*(?:[\\w.]+\\.)?ColorTranslator\\.FromHtml\\(\"([^\"]+)\"\\);");
    private static final Pattern WF_FORE_NAME = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.ForeColor\\s*=\\s*(?:[\\w.]+\\.)?Color\\.(\\w+);");
    private static final Pattern WF_FORE_RGB  = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.ForeColor\\s*=\\s*(?:[\\w.]+\\.)?Color\\.FromArgb\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\);");
    private static final Pattern WF_FONT      = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Font\\s*=\\s*\\(?new\\s+(?:[\\w.]+\\.)?Font\\(\"([^\"]+)\"\\s*,\\s*([\\d.]+)[Ff]?(?:\\s*,\\s*(?:[\\w.]+\\.)?FontStyle\\.(\\w+(?:\\s*\\|\\s*(?:[\\w.]+\\.)?FontStyle\\.\\w+)*))?(?:\\s*,\\s*(?:[\\w.]+\\.)?GraphicsUnit\\.(\\w+))?[^)]*\\)\\)?;");
    private static final Pattern WF_ALIGN     = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.TextAlign\\s*=\\s*(?:[\\w.]+\\.)?(?:ContentAlignment\\.)?(\\w+);");
    private static final Pattern WF_LOCATION  = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Location\\s*=.*?Point\\((\\d+)\\s*,\\s*(\\d+)\\)");
    private static final Pattern WF_CLIENT_SIZE = Pattern.compile(
        "(?:this\\.)?ClientSize\\s*=.*?Size\\((\\d+)\\s*,\\s*(\\d+)\\)");
    private static final Pattern WF_BORDER_STYLE = Pattern.compile(
        "(?:this\\.)?FormBorderStyle\\s*=.*?FixedSingle");
    private static final Pattern WF_FORM_BACK_RGB = Pattern.compile(
        "(?<![.\\w])(?:this\\.)?BackColor\\s*=\\s*(?:[\\w.]+\\.)?Color\\.FromArgb\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\);");
    private static final Pattern WF_FORM_BACK_HTML = Pattern.compile(
        "(?<![.\\w])(?:this\\.)?BackColor\\s*=\\s*(?:[\\w.]+\\.)?ColorTranslator\\.FromHtml\\(\"([^\"]+)\"\\)");
    private static final Pattern WF_FORM_BACK_NAME = Pattern.compile(
        "(?<![.\\w])(?:this\\.)?BackColor\\s*=\\s*(?:[\\w.]+\\.)?Color\\.(\\w+);");
    private static final Pattern WF_FORM_BG_IMAGE = Pattern.compile(
        "(?<![.\\w])(?:this\\.)?BackgroundImage\\s*=.*?GetObject\\(\"([^\"]+)\"\\)");
    private static final Pattern WF_READONLY_PROP = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.ReadOnly\\s*=\\s*true");
    private static final Pattern WF_IMAGE     = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Image\\s*=.*?GetObject\\(\"([^\"]+)\"\\)");
    private static final Pattern WF_FIELD_DECL = Pattern.compile(
        "(?:private|protected|public)(?::|\\s)\\s*(?:[\\w.]+\\.)?([A-Z]\\w+)\\s+(\\w+)\\s*;");
    private static final Pattern WF_VISIBLE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Visible\\s*=\\s*false");
    private static final Pattern WF_ENABLED = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Enabled\\s*=\\s*false");
    private static final Pattern WF_MULTILINE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Multiline\\s*=\\s*true");
    private static final Pattern WF_PASSWORD_CHAR = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.PasswordChar\\s*=");
    private static final Pattern WF_INDIV_WIDTH  = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Width\\s*=\\s*(\\d+)");
    private static final Pattern WF_INDIV_HEIGHT = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Height\\s*=\\s*(\\d+)");
    private static final Pattern WF_LEFT  = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Left\\s*=\\s*(\\d+)");
    private static final Pattern WF_TOP   = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Top\\s*=\\s*(\\d+)");
    private static final Pattern WF_BOUNDS = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Bounds\\s*=.*?Rectangle\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\)");
    private static final Pattern WF_ADD_CELL = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Controls(?:->|\\.)Add\\((?:this\\.)?([A-Za-z_]\\w+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\)");
    private static final Pattern WF_SPLIT_ADD = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.(Panel[12])\\.Controls(?:->|\\.)Add\\((?:this\\.)?([A-Za-z_]\\w+)\\)");
    private static final Pattern WF_ADD_RANGE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Controls(?:->|\\.)AddRange\\(new[^{]*\\{([^}]*)\\}");
    private static final Pattern WF_FORM_ADD_RANGE = Pattern.compile(
        "(?<![.\\w])(?:this\\.)?Controls(?:->|\\.)AddRange\\(new[^{]*\\{([^}]*)\\}");
    private static final Pattern WF_TABPAGES_ADD = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.TabPages(?:->|\\.)Add\\((?:this\\.)?([A-Za-z_]\\w+)\\);");
    private static final Pattern WF_TABPAGES_ADDRANGE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.TabPages(?:->|\\.)AddRange\\(new[^{]*\\{([^}]*)\\}");
    private static final Pattern WF_SET_CELL_POS = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.SetCellPosition\\((?:this\\.)?([A-Za-z_]\\w+)\\s*,\\s*new[^(]*\\((\\d+)\\s*,\\s*(\\d+)\\)\\)");
    private static final Pattern WF_ITEMS_ADDRANGE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Items\\.AddRange\\(new object\\[\\]\\s*\\{([^}]*)\\}");
    private static final Pattern WF_LVI_DECL = Pattern.compile(
        "([A-Za-z_]\\w+)\\s*=\\s*new\\s+ListViewItem\\(\"([^\"]*)\"\\)");
    private static final Pattern WF_LV_ITEMS_RANGE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Items\\.AddRange\\(new\\s+ListViewItem\\[\\][^{]*\\{([^}]*)\\}");
    private static final Pattern WF_CONTROL_VALUE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Value\\s*=\\s*([\\d.]+)");
    private static final Pattern WF_DGV_HEADER = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.HeaderText\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern WF_DGV_COLS_RANGE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Columns\\.AddRange\\(new[^{]*\\{([^}]*)\\}");
    private static final Pattern WF_DGV_COL_ADD_SINGLE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Columns\\.Add\\(\"([^\"]*)\",\\s*\"([^\"]*)\"\\)");
    private static final Pattern WF_DGV_ROW_ADD = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Rows\\.Add\\(([^;)]+)\\)");
    private static final Pattern WF_CHECKED = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Checked\\s*=\\s*true");
    private static final Pattern WF_APPEARANCE_BUTTON = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Appearance\\s*=.*?Appearance\\.Button");
    private static final Pattern WF_SELECTED_INDEX = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.SelectedIndex\\s*=\\s*(\\d+)");
    private static final Pattern WF_SYSTEM_BACK = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.BackColor\\s*=\\s*(?:[\\w.]+\\.)?SystemColors\\.(\\w+)");
    private static final Pattern WF_SYSTEM_FORE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.ForeColor\\s*=\\s*(?:[\\w.]+\\.)?SystemColors\\.(\\w+)");
    // Sequential line-by-line TreeView node patterns
    private static final Pattern WF_TREE_VAR_ASSIGN = Pattern.compile(
        "([A-Za-z_]\\w+)\\s*=\\s*(?:this\\.)?([A-Za-z_]\\w+)\\.Nodes\\.Add\\(\"([^\"]*)\"");
    private static final Pattern WF_TREE_CHILD_ADD = Pattern.compile(
        "([A-Za-z_]\\w+)\\.Nodes\\.Add\\(\"([^\"]*)\"");
    private static final Pattern WF_TREE_ROOT_ADD = Pattern.compile(
        "(?:[A-Za-z_]\\w+\\s*=\\s*)?(?:this\\.)?([A-Za-z_]\\w+)\\.Nodes\\.Add\\(\"([^\"]*)\"\\)");
    private static final Pattern WF_TREE_VAR_ADD = Pattern.compile(
        "([A-Za-z_]\\w+)\\s*=\\s*(?:this\\.)?([A-Za-z_]\\w+)\\.Nodes\\.Add\\(\"([^\"]*)\"\\)");
    private static final Pattern WF_CTRL_BG_IMAGE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.BackgroundImage\\s*=.*?GetObject\\(\"([^\"]+)\"\\)");
    private static final Pattern WF_CTRL_BG_LAYOUT = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.BackgroundImageLayout\\s*=.*?ImageLayout\\.(\\w+)");
    private static final Pattern WF_PICTURE_SIZE_MODE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.SizeMode\\s*=.*?PictureBoxSizeMode\\.(\\w+)");
    private static final Pattern WF_SPLITTER_DIST = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.SplitterDistance\\s*=\\s*(\\d+)");
    private static final Pattern WF_SPLITTER_ORIENT = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Orientation\\s*=.*?Orientation\\.(\\w+)");
    private static final Pattern WF_CHECK_STATE_INDET = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.CheckState\\s*=.*?CheckState\\.Indeterminate");
    private static final Pattern WF_USE_SYS_PASSWORD = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.UseSystemPasswordChar\\s*=\\s*true");
    private static final Pattern WF_INDIV_MIN = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Minimum\\s*=\\s*([\\d.]+)");
    private static final Pattern WF_INDIV_MAX = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Maximum\\s*=\\s*([\\d.]+)");
    private static final Pattern WF_PROGRESS_MARQUEE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Style\\s*=.*?ProgressBarStyle\\.Marquee");
    private static final Pattern WF_ITEMS_ADD_SINGLE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Items\\.Add\\((?:this\\.)?([A-Za-z_]\\w+)\\)");
    private static final Pattern WF_DROPDOWN_ITEMS_ADD = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.DropDownItems(?:->|\\.)Add\\((?:this\\.)?([A-Za-z_]\\w+)\\)");
    private static final Pattern WF_CTRL_BORDER_FIXED = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.BorderStyle\\s*=.*?BorderStyle\\.FixedSingle");
    private static final Pattern WF_DOCK = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Dock\\s*=\\s*(?:[\\w.]+\\.)?DockStyle\\.(\\w+)");
    private static final Pattern WF_PADDING = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Padding\\s*=\\s*new[^(]*\\((\\d+)(?:\\s*,\\s*\\d+)*\\)");
    private static final Pattern WF_MARGIN = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.Margin\\s*=\\s*new[^(]*\\((\\d+)(?:\\s*,\\s*\\d+)*\\)");
    private static final Pattern WF_WRAP_CONTENTS_FALSE = Pattern.compile(
        "(?:this\\.)?([A-Za-z_]\\w+)\\.WrapContents\\s*=\\s*false");
    // ── WinForms type → canonical JavaFX type ──────────────────────────────────

    // WinForms type name → canonical (JavaFX) type name
    private static final Map<String, String> WF_TO_CANONICAL = new java.util.LinkedHashMap<>();
    // Canonical (JavaFX) type name → WinForms type name
    private static final Map<String, String> CANONICAL_TO_WF = new java.util.LinkedHashMap<>();
    static {
        WF_TO_CANONICAL.put("Button",           "Button");
        WF_TO_CANONICAL.put("Label",            "Label");
        WF_TO_CANONICAL.put("TextBox",          "TextField");
        WF_TO_CANONICAL.put("MaskedTextBox",    "PasswordField");
        WF_TO_CANONICAL.put("RichTextBox",      "TextArea");
        WF_TO_CANONICAL.put("CheckBox",         "CheckBox");
        WF_TO_CANONICAL.put("RadioButton",      "RadioButton");
        WF_TO_CANONICAL.put("ComboBox",         "ComboBox");
        WF_TO_CANONICAL.put("ListBox",          "ListView");
        WF_TO_CANONICAL.put("DataGridView",     "TableView");
        WF_TO_CANONICAL.put("TrackBar",         "Slider");
        WF_TO_CANONICAL.put("ProgressBar",      "ProgressBar");
        WF_TO_CANONICAL.put("NumericUpDown",    "Spinner");
        WF_TO_CANONICAL.put("DateTimePicker",   "DatePicker");
        WF_TO_CANONICAL.put("ColorDialog",      "ColorPicker");
        WF_TO_CANONICAL.put("LinkLabel",        "Hyperlink");
        WF_TO_CANONICAL.put("PictureBox",       "ImageView");
        WF_TO_CANONICAL.put("Separator",        "Separator");
        WF_TO_CANONICAL.put("MenuStrip",              "MenuBar");
        WF_TO_CANONICAL.put("ToolStrip",              "ToolBar");
        WF_TO_CANONICAL.put("ToolStripButton",         "Button");
        WF_TO_CANONICAL.put("ToolStripLabel",          "Label");
        WF_TO_CANONICAL.put("ToolStripSeparator",      "Separator");
        WF_TO_CANONICAL.put("ToolStripMenuItem",       "Menu");
        WF_TO_CANONICAL.put("ToolStripComboBox",       "ComboBox");
        WF_TO_CANONICAL.put("ToolStripDropDownButton", "Button");
        WF_TO_CANONICAL.put("ToolStripTextBox",        "TextField");
        WF_TO_CANONICAL.put("TabControl",       "TabPane");
        WF_TO_CANONICAL.put("TabPage",           "Tab");
        WF_TO_CANONICAL.put("Panel",            "Pane");
        WF_TO_CANONICAL.put("FlowLayoutPanel",  "FlowPane");
        WF_TO_CANONICAL.put("TableLayoutPanel", "GridPane");
        WF_TO_CANONICAL.put("SplitContainer",   "SplitPane");
        WF_TO_CANONICAL.put("GroupBox",         "TitledPane");
        WF_TO_CANONICAL.put("CheckedListBox",   "ListView");
        WF_TO_CANONICAL.put("DomainUpDown",     "Spinner");
        WF_TO_CANONICAL.put("HScrollBar",       "ScrollBar");
        WF_TO_CANONICAL.put("VScrollBar",       "ScrollBar");
        WF_TO_CANONICAL.put("ListView",         "TableView");
        WF_TO_CANONICAL.put("MonthCalendar",    "DatePicker");
        WF_TO_CANONICAL.put("PropertyGrid",     "TreeView");
        WF_TO_CANONICAL.put("TreeView",         "TreeView");

        CANONICAL_TO_WF.put("Button",       "Button");
        CANONICAL_TO_WF.put("Label",        "Label");
        CANONICAL_TO_WF.put("TextField",    "TextBox");
        CANONICAL_TO_WF.put("PasswordField","TextBox");  // TextBox + UseSystemPasswordChar=true
        CANONICAL_TO_WF.put("TextArea",     "RichTextBox");
        CANONICAL_TO_WF.put("CheckBox",     "CheckBox");
        CANONICAL_TO_WF.put("RadioButton",  "RadioButton");
        CANONICAL_TO_WF.put("ComboBox",     "ComboBox");
        CANONICAL_TO_WF.put("ListView",     "ListBox");
        CANONICAL_TO_WF.put("TableView",    "DataGridView");
        CANONICAL_TO_WF.put("Slider",       "TrackBar");
        CANONICAL_TO_WF.put("ProgressBar",  "ProgressBar");
        CANONICAL_TO_WF.put("Spinner",      "NumericUpDown");
        CANONICAL_TO_WF.put("DatePicker",   "DateTimePicker");
        CANONICAL_TO_WF.put("ColorPicker",  "Button");  // WinForms has no inline color picker; Button opens ColorDialog
        CANONICAL_TO_WF.put("Hyperlink",    "LinkLabel");
        CANONICAL_TO_WF.put("ToggleButton", "CheckBox");
        CANONICAL_TO_WF.put("ImageView",    "PictureBox");
        CANONICAL_TO_WF.put("Separator",    "Panel");  // WinForms has no standalone Separator; thin Panel used instead
        CANONICAL_TO_WF.put("MenuBar",      "MenuStrip");
        CANONICAL_TO_WF.put("ToolBar",      "ToolStrip");
    }

    /** Approximate CSS hex equivalents for common WinForms SystemColors. */
    private static final Map<String, String> SYSTEM_COLOR_HEX = new java.util.HashMap<>();
    static {
        SYSTEM_COLOR_HEX.put("ControlDark",       "#A0A0A0");
        SYSTEM_COLOR_HEX.put("ControlDarkDark",   "#696969");
        SYSTEM_COLOR_HEX.put("ControlLight",      "#E3E3E3");
        SYSTEM_COLOR_HEX.put("ControlLightLight", "#F5F5F5");
        SYSTEM_COLOR_HEX.put("Control",           "#F0F0F0");
        SYSTEM_COLOR_HEX.put("WindowFrame",       "#646464");
        SYSTEM_COLOR_HEX.put("ButtonFace",        "#F0F0F0");
        SYSTEM_COLOR_HEX.put("ButtonShadow",      "#A0A0A0");
        SYSTEM_COLOR_HEX.put("GrayText",          "#6D6D6D");
        SYSTEM_COLOR_HEX.put("Window",            "#FFFFFF");
        SYSTEM_COLOR_HEX.put("WindowText",        "#000000");
        SYSTEM_COLOR_HEX.put("ControlText",       "#000000");
        SYSTEM_COLOR_HEX.put("Highlight",         "#0078D7");
        SYSTEM_COLOR_HEX.put("HighlightText",     "#FFFFFF");
    }

    /** Types to use when a node is a direct child of a ToolBar (ToolStrip). */
    private static final Map<String, String> TOOLBAR_CHILD_WF = new java.util.HashMap<>();
    static {
        TOOLBAR_CHILD_WF.put("Button",    "ToolStripButton");
        TOOLBAR_CHILD_WF.put("Label",     "ToolStripLabel");
        TOOLBAR_CHILD_WF.put("Separator", "ToolStripSeparator");
        TOOLBAR_CHILD_WF.put("TextField", "ToolStripTextBox");
    }

    private static String toWinFormsCtx(String type, String parentType) {
        if ("ToolBar".equals(parentType)) {
            String ts = TOOLBAR_CHILD_WF.get(type == null ? "" : type);
            return ts != null ? ts : "ToolStripButton";
        }
        return toWinForms(type);
    }

    static {
        CANONICAL_TO_WF.put("TabPane",          "TabControl");
        CANONICAL_TO_WF.put("Tab",              "TabPage");
        CANONICAL_TO_WF.put("ScrollPane",       "Panel");
        CANONICAL_TO_WF.put("Pane",             "Panel");
        CANONICAL_TO_WF.put("VBox",             "TableLayoutPanel");
        CANONICAL_TO_WF.put("HBox",             "FlowLayoutPanel");
        CANONICAL_TO_WF.put("FlowPane",         "FlowLayoutPanel");
        CANONICAL_TO_WF.put("GridPane",         "TableLayoutPanel");
        CANONICAL_TO_WF.put("BorderPane",       "Panel");
        CANONICAL_TO_WF.put("StackPane",        "Panel");
        CANONICAL_TO_WF.put("AnchorPane",       "Panel");
        CANONICAL_TO_WF.put("TilePane",         "FlowLayoutPanel");
        CANONICAL_TO_WF.put("SplitPane",        "SplitContainer");
        CANONICAL_TO_WF.put("TitledPane",       "GroupBox");
        CANONICAL_TO_WF.put("GroupBox",         "GroupBox");
        CANONICAL_TO_WF.put("ScrollBar",        "HScrollBar");
        CANONICAL_TO_WF.put("TreeView",         "TreeView");
        CANONICAL_TO_WF.put("TreeTableView",    "TreeView");
        // Types added for full JavaFX coverage
        CANONICAL_TO_WF.put("MenuButton",       "Button");
        CANONICAL_TO_WF.put("SplitMenuButton",  "Button");
        CANONICAL_TO_WF.put("ButtonBar",        "FlowLayoutPanel");
        CANONICAL_TO_WF.put("ChoiceBox",        "ComboBox");
        CANONICAL_TO_WF.put("Accordion",        "Panel");
        CANONICAL_TO_WF.put("Pagination",       "Panel");
        CANONICAL_TO_WF.put("ProgressIndicator","ProgressBar");
        CANONICAL_TO_WF.put("Canvas",          "Panel");
        // Menu-related types
        CANONICAL_TO_WF.put("Menu",             "ToolStripMenuItem");
        CANONICAL_TO_WF.put("MenuItem",         "ToolStripMenuItem");
        CANONICAL_TO_WF.put("CheckMenuItem",    "ToolStripMenuItem");
        CANONICAL_TO_WF.put("RadioMenuItem",    "ToolStripMenuItem");
        CANONICAL_TO_WF.put("SeparatorMenuItem","ToolStripSeparator");
    }

    private static String toWinForms(String type) {
        return CANONICAL_TO_WF.getOrDefault(type, type == null ? "Panel" : type);
    }
    // ── Parse ──────────────────────────────────────────────────────────────────

    @Override
    public AppMetadata parse(String source) {
        String code = normalizeCs(source == null ? "" : source);
        AppMetadata app = new AppMetadata();

        // 1a. Register nodes from constructor assignments
        Matcher m = WF_DECL.matcher(code);
        while (m.find()) {
            String type = ConversionUtils.simpleName(m.group(2));
            if (!WF_TO_CANONICAL.containsKey(type)) continue;
            Node node = ConversionUtils.getOrMake(app, m.group(1));
            if (node.type == null) node.type = WF_TO_CANONICAL.get(type);
            if ("HScrollBar".equals(type)) node.addProperty("orientation", "HORIZONTAL");
            else if ("VScrollBar".equals(type)) node.addProperty("orientation", "VERTICAL");
            else if ("CheckedListBox".equals(type)) node.addProperty("checkable", "true");
        }

        // 1b. Register nodes from C++/CLI field declarations
        m = WF_FIELD_DECL.matcher(code);
        while (m.find()) {
            String type = m.group(1);
            String id   = m.group(2);
            if (!WF_TO_CANONICAL.containsKey(type)) continue;
            Node node = ConversionUtils.getOrMake(app, id);
            if (node.type == null) node.type = WF_TO_CANONICAL.get(type);
            if ("HScrollBar".equals(type)) node.addProperty("orientation", "HORIZONTAL");
            else if ("VScrollBar".equals(type)) node.addProperty("orientation", "VERTICAL");
            else if ("CheckedListBox".equals(type)) node.addProperty("checkable", "true");
        }

        // 2. Properties
        m = WF_TEXT.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                String rawText = m.group(2).trim().replace("&&", "&")
                    .replace("\\n", "\n").replace("\\t", "\t");
                n.addProperty("text", rawText);
            }
        }

        m = WF_TITLE.matcher(code);
        if (m.find()) app.title = m.group(1);

        m = WF_CLICK.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("hasClick", "true");
        }

        m = WF_SIZE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                n.addProperty("width",  m.group(2));
                n.addProperty("height", m.group(3));
            }
        }

        // BackColor: HTML hex, named color, RGB
        m = WF_BACK_HTML.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("backColor", m.group(2));
        }
        m = WF_SYSTEM_BACK.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                String hex = SYSTEM_COLOR_HEX.get(m.group(2));
                if (hex != null) n.addProperty("backColor", hex);
            }
        }
        m = WF_BACK_NAME.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                String colorName = m.group(2);
                String hex = ConversionUtils.NAMED_COLORS.get(colorName);
                if (hex != null) n.addProperty("backColor", hex);
                else if (!isSystemColorName(colorName))
                    n.addProperty("backColor", colorName.toLowerCase());
            }
        }
        m = WF_BACK_RGB.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("backColor", rgbFromArgb(m, 2));
        }

        // ForeColor: HTML hex, named color, RGB
        m = WF_FORE_HTML.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("foreColor", m.group(2));
        }
        m = WF_SYSTEM_FORE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                String hex = SYSTEM_COLOR_HEX.get(m.group(2));
                if (hex != null) n.addProperty("foreColor", hex);
            }
        }
        m = WF_FORE_NAME.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                String colorName = m.group(2);
                String hex = ConversionUtils.NAMED_COLORS.get(colorName);
                if (hex != null) n.addProperty("foreColor", hex);
                else if (!isSystemColorName(colorName))
                    n.addProperty("foreColor", colorName.toLowerCase());
            }
        }
        m = WF_FORE_RGB.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("foreColor", rgbFromArgb(m, 2));
        }

        m = WF_FONT.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                String rawFamily    = m.group(2);
                String fontSize     = m.group(3);
                String fontStyle    = m.group(4);
                String graphicsUnit = m.group(5);
                n.addProperty("fontFamily", rawFamily);
                n.addProperty("fontSize",   fontSize);
                if ("Pixel".equalsIgnoreCase(graphicsUnit))
                    n.addProperty("fontSizeUnit", "px");
                if (fontStyle != null) {
                    String fs = fontStyle.toUpperCase();
                    boolean familyHasWeight = (splitFontWeight(rawFamily)[1] != null);
                    if (fs.contains("BOLD") && !familyHasWeight)
                        n.addProperty("fontWeight", "BOLD");
                    if (fs.contains("ITALIC"))
                        n.addProperty("fontPosture", "ITALIC");
                }
            }
        }

        m = WF_ALIGN.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("textAlign", m.group(2));
        }

        m = WF_LOCATION.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                n.addProperty("layoutX", m.group(2));
                n.addProperty("layoutY", m.group(3));
            }
        }

        m = WF_LEFT.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && !n.properties.containsKey("layoutX"))
                n.addProperty("layoutX", m.group(2));
        }
        m = WF_TOP.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && !n.properties.containsKey("layoutY"))
                n.addProperty("layoutY", m.group(2));
        }

        m = WF_INDIV_WIDTH.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && !n.properties.containsKey("width"))
                n.addProperty("width", m.group(2));
        }
        m = WF_INDIV_HEIGHT.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && !n.properties.containsKey("height"))
                n.addProperty("height", m.group(2));
        }

        m = WF_BOUNDS.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                n.addProperty("layoutX", m.group(2));
                n.addProperty("layoutY", m.group(3));
                if (!n.properties.containsKey("width"))  n.addProperty("width",  m.group(4));
                if (!n.properties.containsKey("height")) n.addProperty("height", m.group(5));
            }
        }

        m = WF_READONLY_PROP.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("editable", "false");
        }

        m = WF_IMAGE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("imagePath", m.group(2));
        }

        m = WF_CTRL_BG_IMAGE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                n.addProperty("imagePath", m.group(2));
                n.addProperty("imageSource", "background");
            }
        }

        m = WF_CTRL_BG_LAYOUT.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("bgImageLayout", m.group(2));
        }

        m = WF_PICTURE_SIZE_MODE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("sizeMode", m.group(2));
        }

        m = WF_SPLITTER_DIST.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("splitterDistance", m.group(2));
        }
        m = WF_SPLITTER_ORIENT.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                // WinForms Horizontal = horizontal bar = top/bottom panels → JavaFX VERTICAL
                // WinForms Vertical   = vertical bar   = left/right panels → JavaFX HORIZONTAL
                String jfxOrient = "Horizontal".equals(m.group(2)) ? "VERTICAL" : "HORIZONTAL";
                n.addProperty("splitOrientation", jfxOrient);
            }
        }

        m = WF_VISIBLE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("visible", "false");
        }

        m = WF_ENABLED.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("enabled", "false");
        }

        m = WF_MULTILINE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && "TextField".equals(n.type)) n.type = "TextArea";
        }

        m = WF_PASSWORD_CHAR.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && "TextField".equals(n.type)) n.type = "PasswordField";
        }

        m = WF_USE_SYS_PASSWORD.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && "TextField".equals(n.type)) n.type = "PasswordField";
        }

        m = WF_CLIENT_SIZE.matcher(code);
        if (m.find()) {
            try { app.sceneWidth  = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
            try { app.sceneHeight = Integer.parseInt(m.group(2)); } catch (NumberFormatException ignored) {}
        }

        if (WF_BORDER_STYLE.matcher(code).find()) app.resizable = false;

        // Form-level BackColor
        m = WF_FORM_BACK_RGB.matcher(code);
        if (m.find()) app.formBackColor = rgbFromArgb(m, 1);
        if (app.formBackColor == null) {
            m = WF_FORM_BACK_HTML.matcher(code);
            if (m.find()) app.formBackColor = m.group(1);
        }
        if (app.formBackColor == null) {
            m = WF_FORM_BACK_NAME.matcher(code);
            if (m.find() && !"Transparent".equals(m.group(1))) {
                String hex = ConversionUtils.NAMED_COLORS.get(m.group(1));
                if (hex != null) app.formBackColor = hex;
            }
        }

        m = WF_FORM_BG_IMAGE.matcher(code);
        if (m.find()) app.formBackgroundImage = m.group(1);

        // 2b. Items for ComboBox, ListBox, CheckedListBox (new object[] { ... })
        m = WF_ITEMS_ADDRANGE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                List<String> items = new ArrayList<>();
                for (String part : m.group(2).split(",")) {
                    String item = part.trim();
                    if (item.startsWith("\"") && item.endsWith("\""))
                        item = item.substring(1, item.length() - 1);
                    if (!item.isEmpty()) items.add(item);
                }
                if (!items.isEmpty()) n.addProperty("items", String.join("|", items));
            }
        }

        // 2c. Items for WinForms ListView (ListViewItem[])
        Map<String, String> lviText = new java.util.HashMap<>();
        m = WF_LVI_DECL.matcher(code);
        while (m.find()) lviText.put(m.group(1), m.group(2));

        m = WF_LV_ITEMS_RANGE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) {
                List<String> items = new ArrayList<>();
                for (String part : m.group(2).split(",")) {
                    String varName = part.trim();
                    String text = lviText.get(varName);
                    if (text != null) items.add(text);
                }
                if (!items.isEmpty()) n.addProperty("items", String.join("|", items));
            }
        }

        // 2d. Value/Min/Max for ProgressBar, Spinner, Slider, ScrollBar
        m = WF_CONTROL_VALUE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n == null) continue;
            if ("ProgressBar".equals(n.type)) {
                try {
                    double val = Double.parseDouble(m.group(2));
                    n.addProperty("progress", String.format("%.4f", val / 100.0));
                } catch (NumberFormatException ignored) {}
            } else if ("Spinner".equals(n.type)) {
                n.addProperty("value", m.group(2));
            } else if ("Slider".equals(n.type) || "ScrollBar".equals(n.type)) {
                n.addProperty("value", m.group(2));
            }
        }

        m = WF_INDIV_MIN.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && ("Slider".equals(n.type) || "ScrollBar".equals(n.type) || "Spinner".equals(n.type)))
                n.addProperty("min", m.group(2));
        }

        m = WF_INDIV_MAX.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && ("Slider".equals(n.type) || "ScrollBar".equals(n.type) || "Spinner".equals(n.type)))
                n.addProperty("max", m.group(2));
        }

        // 2d2. ProgressBar marquee (indeterminate)
        m = WF_PROGRESS_MARQUEE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("indeterminate", "true");
        }

        // 2d3. Padding
        m = WF_PADDING.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && !n.properties.containsKey("padding")) n.addProperty("padding", m.group(2));
        }

        // 2d3b. Margin on child controls → cellMargin property (vgap derived after hierarchy is built)
        m = WF_MARGIN.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("cellMargin", m.group(2));
        }

        // 2d3c. WrapContents = false → nowrap property (FlowPane mapped to HBox in JavaFX output)
        m = WF_WRAP_CONTENTS_FALSE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("nowrap", "true");
        }

        // 2d4. BorderStyle.FixedSingle
        m = WF_CTRL_BORDER_FIXED.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("borderFixed", "true");
        }

        // 2e. DataGridView column headers
        Map<String, String> colHeaders = new java.util.HashMap<>();
        m = WF_DGV_HEADER.matcher(code);
        while (m.find()) colHeaders.put(m.group(1), m.group(2));

        m = WF_DGV_COLS_RANGE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && "TableView".equals(n.type)) {
                List<String> headers = new ArrayList<>();
                for (String part : m.group(2).split(",")) {
                    String header = colHeaders.get(part.trim());
                    if (header != null) headers.add(header);
                }
                if (!headers.isEmpty()) n.addProperty("columns", String.join("|", headers));
            }
        }

        // 2f. DGV Columns.Add(name, header) - single-column add
        m = WF_DGV_COL_ADD_SINGLE.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && "TableView".equals(n.type)) {
                String existing = n.properties.get("columns");
                String header = m.group(3); // second arg is the display header
                n.addProperty("columns", existing == null ? header : existing + "|" + header);
            }
        }

        // 2g. DGV Rows.Add(...) - capture row data
        m = WF_DGV_ROW_ADD.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && "TableView".equals(n.type)) {
                // extract quoted cell values
                List<String> cells = new ArrayList<>();
                java.util.regex.Matcher cellM = java.util.regex.Pattern.compile("\"([^\"]*)\"").matcher(m.group(2));
                while (cellM.find()) cells.add(cellM.group(1));
                if (!cells.isEmpty()) {
                    String rowStr = String.join(",", cells);
                    String existing = n.properties.get("tableRows");
                    n.addProperty("tableRows", existing == null ? rowStr : existing + ";" + rowStr);
                }
            }
        }

        // 2h. Checked = true on controls
        m = WF_CHECKED.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("selected", "true");
        }

        // 2h1b. CheckState = Indeterminate
        m = WF_CHECK_STATE_INDET.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("indeterminate", "true");
        }

        // 2h2. Appearance = Button on CheckBox → ToggleButton
        m = WF_APPEARANCE_BUTTON.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null && "CheckBox".equals(n.type)) n.type = "ToggleButton";
        }

        // 2h3. SelectedIndex
        m = WF_SELECTED_INDEX.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("selectedIndex", m.group(2));
        }

        // 2i. TreeView node population - sequential scan to handle _root reuse
        {
            String currentVar  = null;
            String currentCtrl = null;
            String currentText = null;
            List<String> currentChildren = new ArrayList<>();
            // Maps controlId → list of (rootText, children) segments
            Map<String, List<String[]>> treeSegments = new java.util.LinkedHashMap<>();

            for (String line : code.split("\n")) {
                java.util.regex.Matcher ma = WF_TREE_VAR_ASSIGN.matcher(line);
                if (ma.find()) {
                    // Flush previous segment if any
                    if (currentVar != null && currentCtrl != null && currentText != null) {
                        treeSegments.computeIfAbsent(currentCtrl, k -> new ArrayList<>())
                            .add(new String[]{ currentText, String.join(",", currentChildren) });
                    }
                    currentVar = ma.group(1);
                    currentCtrl = ma.group(2);
                    currentText = ma.group(3);
                    currentChildren = new ArrayList<>();
                } else {
                    java.util.regex.Matcher mc = WF_TREE_CHILD_ADD.matcher(line);
                    if (mc.find() && currentVar != null && mc.group(1).equals(currentVar)) {
                        currentChildren.add(mc.group(2));
                    }
                }
            }
            // Flush last segment
            if (currentVar != null && currentCtrl != null && currentText != null) {
                treeSegments.computeIfAbsent(currentCtrl, k -> new ArrayList<>())
                    .add(new String[]{ currentText, String.join(",", currentChildren) });
            }
            // Store first segment per control as treeItems
            for (Map.Entry<String, List<String[]>> entry : treeSegments.entrySet()) {
                Node n = ConversionUtils.findById(app, entry.getKey());
                if (n == null || entry.getValue().isEmpty()) continue;
                String[] seg = entry.getValue().get(0);
                String treeItems = seg[0] + (seg[1].isEmpty() ? "" : ":" + seg[1]);
                n.addProperty("treeItems", treeItems);
            }
        }

        // 3. Hierarchy
        // WF_ADD_CELL first (more specific)
        m = WF_ADD_CELL.matcher(code);
        while (m.find()) {
            Node parent = ConversionUtils.findById(app, m.group(1));
            Node child  = ConversionUtils.getOrMake(app, m.group(2));
            if (parent != null) {
                ConversionUtils.addChild(app, parent, child);
                child.addProperty("gridCol", m.group(3));
                child.addProperty("gridRow", m.group(4));
            }
        }

        m = WF_SPLIT_ADD.matcher(code);
        while (m.find()) {
            Node parent = ConversionUtils.findById(app, m.group(1));
            Node child  = ConversionUtils.getOrMake(app, m.group(3));
            if (parent != null) {
                ConversionUtils.addChild(app, parent, child);
                child.addProperty("splitPanel", m.group(2)); // "Panel1" or "Panel2"
            }
        }

        m = WF_ADD.matcher(code);
        while (m.find()) {
            Node parent = ConversionUtils.findById(app, m.group(1));
            if (parent != null)
                ConversionUtils.addChild(app, parent,
                    ConversionUtils.getOrMake(app, m.group(2)));
        }

        // Items.Add for ToolBar (ToolStrip), MenuBar, and Menu (sub-menu) children
        m = WF_ITEMS_ADD_SINGLE.matcher(code);
        while (m.find()) {
            Node parent = ConversionUtils.findById(app, m.group(1));
            Node child  = ConversionUtils.getOrMake(app, m.group(2));
            if (parent != null && ("ToolBar".equals(parent.type) || "MenuBar".equals(parent.type)
                    || "Menu".equals(parent.type)))
                ConversionUtils.addChild(app, parent, child);
        }

        // DropDownItems.Add for WinForms menu items (ToolStripMenuItem sub-items)
        m = WF_DROPDOWN_ITEMS_ADD.matcher(code);
        while (m.find()) {
            Node parent = ConversionUtils.findById(app, m.group(1));
            Node child  = ConversionUtils.getOrMake(app, m.group(2));
            if (parent != null && "Menu".equals(parent.type))
                ConversionUtils.addChild(app, parent, child);
        }

        m = WF_ADD_RANGE.matcher(code);
        while (m.find()) {
            Node parent = ConversionUtils.findById(app, m.group(1));
            if (parent != null) {
                for (String part : m.group(2).split(",")) {
                    String id = part.trim().replaceAll("(?i)^this\\.", "").trim();
                    if (!id.isEmpty())
                        ConversionUtils.addChild(app, parent,
                            ConversionUtils.getOrMake(app, id));
                }
            }
        }

        m = WF_FORM_ADD.matcher(code);
        while (m.find()) {
            ConversionUtils.getOrMake(app, m.group(1));
        }

        m = WF_FORM_ADD_RANGE.matcher(code);
        while (m.find()) {
            for (String part : m.group(1).split(",")) {
                String id = part.trim().replaceAll("(?i)^this\\.", "").trim();
                if (!id.isEmpty()) ConversionUtils.getOrMake(app, id);
            }
        }

        // TabPages.Add / TabPages.AddRange (emitted by WinFormsAdapter generator for TabControl)
        m = WF_TABPAGES_ADD.matcher(code);
        while (m.find()) {
            Node parent = ConversionUtils.findById(app, m.group(1));
            Node child  = ConversionUtils.getOrMake(app, m.group(2));
            if (parent != null) ConversionUtils.addChild(app, parent, child);
        }

        m = WF_TABPAGES_ADDRANGE.matcher(code);
        while (m.find()) {
            Node parent = ConversionUtils.findById(app, m.group(1));
            if (parent != null) {
                for (String part : m.group(2).split(",")) {
                    String id = part.trim().replaceAll("(?i)^this\\.", "").trim();
                    if (!id.isEmpty())
                        ConversionUtils.addChild(app, parent, ConversionUtils.getOrMake(app, id));
                }
            }
        }

        // SetCellPosition: fill in gridCol/gridRow for TLP children (emitted by WinFormsAdapter generator)
        m = WF_SET_CELL_POS.matcher(code);
        while (m.find()) {
            Node child = ConversionUtils.findById(app, m.group(2));
            if (child != null) {
                child.addProperty("gridCol", m.group(3));
                child.addProperty("gridRow", m.group(4));
            }
        }

        // Post-process: parse WinForms Dock assignments → store as wfDock property
        m = WF_DOCK.matcher(code);
        while (m.find()) {
            Node n = ConversionUtils.findById(app, m.group(1));
            if (n != null) n.addProperty("wfDock", m.group(2));
        }

        // Post-process: WinForms Dock=Top accordion panels are added in reverse visual order;
        // reverse children so JavaFX VBox displays them in the correct order.
        for (Node n : app.allNodes) {
            if (!n.children.isEmpty()
                    && n.children.stream().allMatch(c -> "TitledPane".equals(c.type))) {
                java.util.Collections.reverse(n.children);
            }
        }

        // cellMargin properties are emitted as GridPane.setMargin() / FlowPane.setMargin()
        // in the JavaFX output, so no vgap/hgap derivation is needed here.


        return app;
    }

    // ── Generate ───────────────────────────────────────────────────────────────

    @Override
    public String generate(AppMetadata app, Map<String, Object> options) {
        boolean matchDefaultFont = Boolean.TRUE.equals(options.get("matchSourceDefaultFont"));
        boolean wfPanelNest      = Boolean.TRUE.equals(options.get("wfPanelNest"));
        List<Node> roots = ConversionUtils.computeRoots(app);
        String className = (app.sourceClassName != null && !app.sourceClassName.isBlank()
                ? app.sourceClassName : toClassName(app.title)) + "Form";
        StringBuilder sb = new StringBuilder();

        // Build child→parentType map for context-sensitive WF type mapping
        Map<String, String> parentTypeLookup = new java.util.HashMap<>();
        for (Node n : app.allNodes) {
            for (Node child : n.children) {
                parentTypeLookup.put(child.id, n.type);
            }
        }

        sb.append("using System;\n");
        sb.append("using System.Drawing;\n");
        sb.append("using System.Windows.Forms;\n\n");
        sb.append("public partial class ").append(className).append(" : Form\n{\n");

        for (Node n : app.allNodes) {
            sb.append("    private ").append(toWinFormsCtx(n.type, parentTypeLookup.get(n.id)))
              .append(" ").append(n.id).append(";\n");
        }

        sb.append("\n    public ").append(className).append("()\n    {\n        InitializeComponent();\n    }\n");
        sb.append("\n    private void InitializeComponent()\n    {\n");

        for (Node n : app.allNodes) {
            sb.append("        this.").append(n.id)
              .append(" = new ").append(toWinFormsCtx(n.type, parentTypeLookup.get(n.id))).append("();\n");
        }
        sb.append("\n");

        for (Node n : app.allNodes) {
            String text = n.properties.get("text");
            if (text != null)
                sb.append("        this.").append(n.id).append(".Text = ")
                  .append(ConversionUtils.quoted(text.replace("&", "&&"))).append(";\n");

            if ("true".equals(n.properties.get("hasClick")))
                sb.append("        this.").append(n.id)
                  .append(".Click += new System.EventHandler(this.")
                  .append(n.id).append("_Click);\n");

            String back = n.properties.get("backColor");
            if (back != null)
                sb.append("        this.").append(n.id)
                  .append(".BackColor = System.Drawing.ColorTranslator.FromHtml(")
                  .append(ConversionUtils.quoted(back)).append(");\n");

            String fore = n.properties.get("foreColor");
            if (fore != null)
                sb.append("        this.").append(n.id)
                  .append(".ForeColor = System.Drawing.ColorTranslator.FromHtml(")
                  .append(ConversionUtils.quoted(fore)).append(");\n");

            String w = n.properties.get("width"), h = n.properties.get("height");
            boolean wMax = "Double.MAX_VALUE".equals(w);
            boolean hMax = "Double.MAX_VALUE".equals(h);
            if (w != null && h != null && !wMax && !hMax)
                sb.append("        this.").append(n.id)
                  .append(".Size = new System.Drawing.Size(").append(w)
                  .append(", ").append(h).append(");\n");
            else if (w != null && !wMax && h == null)
                sb.append("        this.").append(n.id).append(".Width = ").append(w).append(";\n");
            else if (h != null && !hMax && w == null)
                sb.append("        this.").append(n.id).append(".Height = ").append(h).append(";\n");
            else if (w != null && !wMax && hMax)
                sb.append("        this.").append(n.id).append(".Width = ").append(w).append(";\n");
            else if (h != null && !hMax && wMax)
                sb.append("        this.").append(n.id).append(".Height = ").append(h).append(";\n");

            String fam = n.properties.get("fontFamily"), sz = n.properties.get("fontSize");
            String injectedSzUnit = null;
            if (fam == null && matchDefaultFont) {
                // JavaFX system default: Segoe UI at ~9.75pt (13px at 96 dpi)
                fam = "Segoe UI";
                if (sz == null) { sz = "9.75"; injectedSzUnit = "Point"; }
            }
            if (fam != null && sz != null) {
                String fszUnit = injectedSzUnit != null ? injectedSzUnit
                    : ("pt".equals(n.properties.get("fontSizeUnit")) ? "Point" : "Pixel");
                String fsStyle = wfFontStyle(n);
                sb.append("        this.").append(n.id)
                  .append(".Font = new System.Drawing.Font(")
                  .append(ConversionUtils.quoted(fam)).append(", ").append(sz)
                  .append("F, System.Drawing.FontStyle.").append(fsStyle)
                  .append(", System.Drawing.GraphicsUnit.").append(fszUnit).append(");\n");
            } else if (sz != null) {
                String fszUnit = "pt".equals(n.properties.get("fontSizeUnit")) ? "Point" : "Pixel";
                String fsStyle = wfFontStyle(n);
                sb.append("        this.").append(n.id)
                  .append(".Font = new System.Drawing.Font(this.Font.FontFamily, ").append(sz)
                  .append("F, System.Drawing.FontStyle.").append(fsStyle)
                  .append(", System.Drawing.GraphicsUnit.").append(fszUnit).append(");\n");
            }

            String align = n.properties.get("textAlign");
            if (align != null) {
                String wfType = toWinForms(n.type);
                if ("TextBox".equals(wfType) || "MaskedTextBox".equals(wfType)
                        || "RichTextBox".equals(wfType)) {
                    // TextBox only supports HorizontalAlignment (Left/Center/Right)
                    sb.append("        this.").append(n.id)
                      .append(".TextAlign = System.Windows.Forms.HorizontalAlignment.")
                      .append(toHorizontalAlignment(align)).append(";\n");
                } else {
                    // Label and others: align is already stored as ContentAlignment name (e.g. MiddleRight)
                    sb.append("        this.").append(n.id)
                      .append(".TextAlign = System.Drawing.ContentAlignment.")
                      .append(align).append(";\n");
                }
            }

            if ("ToggleButton".equals(n.type))
                sb.append("        this.").append(n.id)
                  .append(".Appearance = System.Windows.Forms.Appearance.Button;\n");

            if ("true".equals(n.properties.get("selected")) &&
                    ("CheckBox".equals(toWinForms(n.type)) || "RadioButton".equals(toWinForms(n.type))
                     || "CheckMenuItem".equals(n.type) || "RadioMenuItem".equals(n.type)))
                sb.append("        this.").append(n.id).append(".Checked = true;\n");

            if ("true".equals(n.properties.get("indeterminate")) && "CheckBox".equals(toWinForms(n.type))) {
                sb.append("        this.").append(n.id).append(".ThreeState = true;\n");
                sb.append("        this.").append(n.id)
                  .append(".CheckState = System.Windows.Forms.CheckState.Indeterminate;\n");
            }

            // ChoiceBox → ComboBox with DropDownList style (read-only selection, no free text)
            if ("ChoiceBox".equals(n.type))
                sb.append("        this.").append(n.id)
                  .append(".DropDownStyle = System.Windows.Forms.ComboBoxStyle.DropDownList;\n");

            if ("ScrollPane".equals(n.type))
                sb.append("        this.").append(n.id).append(".AutoScroll = true;\n");

            // GroupBox (TitledPane): pad below title bar so content doesn't overlap it
            if ("TitledPane".equals(n.type))
                sb.append("        this.").append(n.id)
                  .append(".Padding = new System.Windows.Forms.Padding(5, 20, 5, 5);\n");

            // Accordion Panel: set explicit height (children use Dock=Top so we need a real height)
            if ("Accordion".equals(n.type)) {
                int accordionH = n.children.size() * 55;
                sb.append("        this.").append(n.id).append(".Height = ").append(accordionH).append(";\n");
            }

            // TrackBar (Slider) and HScrollBar/VScrollBar (ScrollBar): Minimum, Maximum, Value
            if ("Slider".equals(n.type) || "ScrollBar".equals(n.type)) {
                String minV = n.properties.get("min"), maxV = n.properties.get("max"), val = n.properties.get("value");
                if (minV != null) {
                    try { sb.append("        this.").append(n.id).append(".Minimum = ").append((int)Double.parseDouble(minV)).append(";\n"); }
                    catch (NumberFormatException ignored) {}
                }
                if (maxV != null) {
                    try { sb.append("        this.").append(n.id).append(".Maximum = ").append((int)Double.parseDouble(maxV)).append(";\n"); }
                    catch (NumberFormatException ignored) {}
                }
                if (val != null) {
                    try { sb.append("        this.").append(n.id).append(".Value = ").append((int)Double.parseDouble(val)).append(";\n"); }
                    catch (NumberFormatException ignored) {}
                }
            }

            // NumericUpDown (Spinner): Minimum, Maximum, Value
            if ("Spinner".equals(n.type)) {
                String minV = n.properties.get("min"), maxV = n.properties.get("max"), val = n.properties.get("value");
                if (minV != null) {
                    try { sb.append("        this.").append(n.id).append(".Minimum = ").append((int)Double.parseDouble(minV)).append("M;\n"); }
                    catch (NumberFormatException ignored) {}
                }
                if (maxV != null) {
                    try { sb.append("        this.").append(n.id).append(".Maximum = ").append((int)Double.parseDouble(maxV)).append("M;\n"); }
                    catch (NumberFormatException ignored) {}
                }
                if (val != null) {
                    try { sb.append("        this.").append(n.id).append(".Value = ").append((int)Double.parseDouble(val)).append("M;\n"); }
                    catch (NumberFormatException ignored) {}
                }
            }

            // Items for ComboBox (DropDownList=ChoiceBox or editable) and ListBox
            String itemsProp = n.properties.get("items");
            if (itemsProp != null && ("ComboBox".equals(n.type) || "ChoiceBox".equals(n.type)
                    || "ListView".equals(n.type))) {
                String[] its = itemsProp.split("\\|");
                sb.append("        this.").append(n.id).append(".Items.AddRange(new object[] { ");
                for (int ii = 0; ii < its.length; ii++) {
                    if (ii > 0) sb.append(", ");
                    sb.append(ConversionUtils.quoted(its[ii]));
                }
                sb.append(" });\n");
            }
            // ComboBox / ChoiceBox: initial selection from selectedValue (setValue("..."))
            String selVal = n.properties.get("selectedValue");
            if (selVal != null && itemsProp != null
                    && ("ComboBox".equals(n.type) || "ChoiceBox".equals(n.type))) {
                String[] itsArr = itemsProp.split("\\|");
                for (int ii = 0; ii < itsArr.length; ii++) {
                    if (selVal.equals(itsArr[ii])) {
                        sb.append("        this.").append(n.id).append(".SelectedIndex = ")
                          .append(ii).append(";\n");
                        break;
                    }
                }
            }
            // ComboBox / ChoiceBox with items: select first item by default
            String selIdx = n.properties.get("selectedIndex");
            if (selIdx != null && ("ComboBox".equals(n.type) || "ChoiceBox".equals(n.type))) {
                try {
                    sb.append("        this.").append(n.id).append(".SelectedIndex = ")
                      .append(Integer.parseInt(selIdx)).append(";\n");
                } catch (NumberFormatException ignored) {}
            }

            // ProgressBar / ProgressIndicator: Maximum=100, Value from 0-1 progress
            if ("ProgressBar".equals(n.type) || "ProgressIndicator".equals(n.type)) {
                String progV = n.properties.get("progress");
                if (progV == null) {
                    // No progress set → indeterminate (marquee)
                    sb.append("        this.").append(n.id)
                      .append(".Style = System.Windows.Forms.ProgressBarStyle.Marquee;\n");
                    sb.append("        this.").append(n.id).append(".MarqueeAnimationSpeed = 30;\n");
                } else {
                    sb.append("        this.").append(n.id).append(".Maximum = 100;\n");
                    try {
                        int wfVal = Math.min(100, Math.max(0, (int)(Double.parseDouble(progV) * 100)));
                        sb.append("        this.").append(n.id).append(".Value = ").append(wfVal).append(";\n");
                    } catch (NumberFormatException ignored) {}
                }
            }

            // SplitPane → SplitContainer: orientation and splitter distance
            if ("SplitPane".equals(n.type)) {
                // Orientation: prefer splitOrientation (from WinForms parse), fall back to
                // orientation (from JavaFX parse of setOrientation())
                String orient = n.properties.containsKey("splitOrientation")
                    ? n.properties.get("splitOrientation")
                    : n.properties.get("orientation");
                if ("VERTICAL".equals(orient)) {
                    // JavaFX VERTICAL = top/bottom panels → WinForms Horizontal bar
                    sb.append("        this.").append(n.id)
                      .append(".Orientation = System.Windows.Forms.Orientation.Horizontal;\n");
                } else if ("HORIZONTAL".equals(orient)) {
                    // JavaFX HORIZONTAL = left/right panels → WinForms Vertical bar
                    sb.append("        this.").append(n.id)
                      .append(".Orientation = System.Windows.Forms.Orientation.Vertical;\n");
                }
                // SplitterDistance: prefer pixel value from WinForms parse,
                // then compute from dividerPosition ratio if available.
                // IMPORTANT: SplitContainer.SplitterDistance is validated against the
                // control's CURRENT size. At InitializeComponent time the control has its
                // default size (~150px), so any distance > ~121 is clamped and the value
                // is lost once Dock=Fill expands the container at runtime.
                // Fix: emit a Size that is large enough to hold the desired distance first;
                // Dock=Fill will override the size at runtime, but the distance is stored
                // correctly and will be honoured when the layout finalises.
                boolean isVert = "VERTICAL".equals(orient);
                String sizeKey = isVert ? n.properties.get("height") : n.properties.get("width");
                int totalPx = isVert ? app.sceneHeight : app.sceneWidth;
                if (sizeKey != null) {
                    try { totalPx = (int) Double.parseDouble(sizeKey); }
                    catch (NumberFormatException ignored) {}
                }
                // Estimated usable dimension (subtract padding/margin/chrome ~60px)
                int mainDim = Math.max(totalPx - 60, 200);
                int crossDim = 80; // matches the 100px Absolute row minus 2×10px margin
                int szW = isVert ? crossDim : mainDim;
                int szH = isVert ? mainDim : crossDim;
                sb.append("        this.").append(n.id)
                  .append(".Size = new System.Drawing.Size(").append(szW).append(", ").append(szH).append(");\n");

                String distStr = n.properties.get("splitterDistance");
                if (distStr != null) {
                    try {
                        sb.append("        this.").append(n.id)
                          .append(".SplitterDistance = ")
                          .append((int) Double.parseDouble(distStr)).append(";\n");
                    } catch (NumberFormatException ignored) {}
                } else {
                    double ratio = 0.5; // JavaFX default divider position is 50%
                    String divPos = n.properties.get("dividerPosition");
                    if (divPos != null) {
                        try { ratio = Double.parseDouble(divPos); } catch (NumberFormatException ignored) {}
                    }
                    int dist = (int) (ratio * mainDim);
                    sb.append("        this.").append(n.id)
                      .append(".SplitterDistance = ").append(dist).append(";\n");
                }
            }

            boolean isToolbarChild = "ToolBar".equals(parentTypeLookup.get(n.id));

            String pad = n.properties.get("padding");
            if (pad != null)
                sb.append("        this.").append(n.id)
                  .append(".Padding = new System.Windows.Forms.Padding(").append(pad).append(");\n");

            String wfType = toWinForms(n.type);
            boolean isReadOnly = "false".equals(n.properties.get("editable"));

            // PasswordField: UseSystemPasswordChar
            if ("PasswordField".equals(n.type) && !isToolbarChild)
                sb.append("        this.").append(n.id).append(".UseSystemPasswordChar = true;\n");

            // wrapText TextBox needs Multiline=true to wrap text.
            // Read-only TextFields are single-line; vertical alignment is achieved via Anchor.
            if ("TextBox".equals(wfType) && "true".equals(n.properties.get("wrapText")))
                sb.append("        this.").append(n.id).append(".Multiline = true;\n");

            if (isReadOnly && !isToolbarChild)
                sb.append("        this.").append(n.id).append(".ReadOnly = true;\n");
            if ("false".equals(n.properties.get("visible")))
                sb.append("        this.").append(n.id).append(".Visible = false;\n");
            if ("false".equals(n.properties.get("enabled")))
                sb.append("        this.").append(n.id).append(".Enabled = false;\n");

            if ("Label".equals(wfType) && n.properties.get("width") != null)
                sb.append("        this.").append(n.id).append(".AutoSize = false;\n");
            if ("true".equals(n.properties.get("wrapText")))
                sb.append("        this.").append(n.id).append(".AutoSize = false;\n");

            // Separator rendered as a thin Panel (only when NOT a ToolStrip child)
            if ("Separator".equals(n.type) && !isToolbarChild) {
                String orient = n.properties.get("orientation");
                if ("VERTICAL".equals(orient)) {
                    sb.append("        this.").append(n.id).append(".Width = 2;\n");
                    sb.append("        this.").append(n.id).append(".Height = 20;\n");
                    // No Dock - FlowLayoutPanel (HBox) places it inline
                } else {
                    // Horizontal separator: height=2, fill cell width via Dock=Fill.
                    // Dock=Top inside a TLP stretches to full cell width (same as Dock=Fill for width),
                    // which is what we want - the separator fills the VBox width like JavaFX.
                    sb.append("        this.").append(n.id).append(".Height = 2;\n");
                    sb.append("        this.").append(n.id).append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                }
                sb.append("        this.").append(n.id).append(".BackColor = System.Drawing.SystemColors.ControlDark;\n");
            }

            // DataGridView: disable AllowUserToAddRows so the empty "new row" doesn't stretch height
            if ("TableView".equals(n.type))
                sb.append("        this.").append(n.id).append(".AllowUserToAddRows = false;\n");

            // Canvas: emit as a fixed-size Panel with a gray background + "Placeholder" label text
            if ("Canvas".equals(n.type)) {
                String cw = n.properties.get("width"), ch = n.properties.get("height");
                int canW = 100, canH = 100;
                if (cw != null) { try { canW = (int) Double.parseDouble(cw); } catch (NumberFormatException ignored) {} }
                if (ch != null) { try { canH = (int) Double.parseDouble(ch); } catch (NumberFormatException ignored) {} }
                sb.append("        this.").append(n.id)
                  .append(".Size = new System.Drawing.Size(").append(canW).append(", ").append(canH).append(");\n");
                sb.append("        this.").append(n.id)
                  .append(".BackColor = System.Drawing.Color.LightGray;\n");
                sb.append("        this.").append(n.id)
                  .append(".BorderStyle = System.Windows.Forms.BorderStyle.FixedSingle;\n");
            }

            // ColorPicker rendered as a Button that would open a ColorDialog
            if ("ColorPicker".equals(n.type) && n.properties.get("text") == null)
                sb.append("        this.").append(n.id).append(".Text = \"Pick Color...\";\n");

            if (!"Label".equals(wfType) && !isToolbarChild) {
                String borderWidth = n.properties.get("borderWidth");
                String borderColor = n.properties.get("borderColor");
                boolean isTextBoxTyp = "TextField".equals(n.type) || "TextArea".equals(n.type) || "PasswordField".equals(n.type);
                boolean zeroBorderW  = borderWidth != null && borderWidth.matches("0+(\\.0*)?");
                // In panelNest mode every text-input node gets wrapped - suppress FixedSingle here.
                // Mirrors the needsWrap condition in writeWfTree:
                //   panelNest=true  → all text-input nodes are wrapped (regardless of zeroBorder)
                //   panelNest=false → only those with explicit non-zero borderColor are wrapped
                boolean willBorderWrap = isTextBoxTyp
                    && (wfPanelNest || (!zeroBorderW && borderColor != null && !borderColor.isEmpty()));
                if (zeroBorderW) {
                    sb.append("        this.").append(n.id)
                      .append(".BorderStyle = System.Windows.Forms.BorderStyle.None;\n");
                } else if (borderColor != null && !willBorderWrap) {
                    if ("Button".equals(wfType) || "ToolStripButton".equals(wfType)) {
                        // Button has no BorderStyle property - use FlatStyle instead
                        sb.append("        this.").append(n.id)
                          .append(".FlatStyle = System.Windows.Forms.FlatStyle.Flat;\n");
                        sb.append("        this.").append(n.id)
                          .append(".FlatAppearance.BorderColor = System.Drawing.ColorTranslator.FromHtml(\"")
                          .append(borderColor).append("\");\n");
                    } else {
                        sb.append("        this.").append(n.id)
                          .append(".BorderStyle = System.Windows.Forms.BorderStyle.FixedSingle;\n");
                    }
                }
            }
        }
        sb.append("\n");

        // TableLayoutPanel / VBox layout setup
        for (Node n : app.allNodes) {
            // TLP / VBox children of a FlowLayoutPanel need AutoSize to shrink-wrap.
            String parentType = parentTypeLookup.get(n.id);
            boolean insideFlow = "HBox".equals(parentType) || "FlowPane".equals(parentType) || "ButtonBar".equals(parentType);
            if (insideFlow && ("VBox".equals(n.type) || "GridPane".equals(n.type))) {
                sb.append("        this.").append(n.id).append(".AutoSize = true;\n");
                sb.append("        this.").append(n.id)
                  .append(".AutoSizeMode = System.Windows.Forms.AutoSizeMode.GrowAndShrink;\n");
                // Signal to writeWfTree: children should use Anchor=Left|Right instead
                // of Dock=Fill to avoid the AutoSize + Fill circular-size dependency.
                n.properties.put("_autoSizeTlp", "true");
            }
            // HBox and ButtonBar never wrap; FlowPane wraps by default (WrapContents=true is the WinForms default)
            if ("HBox".equals(n.type) || "ButtonBar".equals(n.type)) {
                sb.append("        this.").append(n.id).append(".WrapContents = false;\n");
            }
            if ("GridPane".equals(n.type)) {
                int maxCol = 0, maxRow = 0;
                for (Node child : n.children) {
                    String gc = child.properties.get("gridCol"), gr = child.properties.get("gridRow");
                    try { if (gc != null) maxCol = Math.max(maxCol, Integer.parseInt(gc)); }
                    catch (NumberFormatException ignored) {}
                    try { if (gr != null) maxRow = Math.max(maxRow, Integer.parseInt(gr)); }
                    catch (NumberFormatException ignored) {}
                }
                sb.append("        this.").append(n.id).append(".ColumnCount = ").append(maxCol + 1).append(";\n");
                sb.append("        this.").append(n.id).append(".RowCount = ").append(maxRow + 1).append(";\n");
                // Detect layout pattern: if ALL children in col 0 are Labels → label/input
                // layout (AutoSize label col + Percent content col). Otherwise equal Percent.
                // Detect content-sized grid: if any direct VBox/GridPane child has an explicit-height
                // last child that is a data-view control (ListView/TreeView/TableView/TreeTableView)
                // with both width AND height set, the GridPane sizes to content in JavaFX and must be
                // AutoSize in WinForms. Only data-view children qualify - TitledPane panels with only
                // a height property (e.g. accordion panels with setPrefHeight(55)) must NOT trigger
                // this flag, or the outer TLP would incorrectly get AutoSize instead of Dock=Fill.
                boolean hasContentSizedVBoxChildren = false;
                for (Node child : n.children) {
                    boolean isContainerChild = "VBox".equals(child.type) || "GridPane".equals(child.type);
                    if (isContainerChild && !child.children.isEmpty()) {
                        Node lastVChild = child.children.get(child.children.size() - 1);
                        String lvt = lastVChild.type;
                        boolean isDataView = "ListView".equals(lvt) || "TreeView".equals(lvt)
                                || "TableView".equals(lvt) || "TreeTableView".equals(lvt);
                        // Require BOTH width and height to be present (data views have setPrefSize)
                        // so that controls with only a height (e.g. TitledPane with setPrefHeight)
                        // do not trigger content-sized detection.
                        boolean hasBothDimensions = lastVChild.properties.get("height") != null
                                && lastVChild.properties.get("width") != null;
                        if (isDataView && hasBothDimensions) {
                            hasContentSizedVBoxChildren = true;
                            break;
                        }
                    }
                }
                if (hasContentSizedVBoxChildren) {
                    n.properties.put("_autoSizeGrid", "true");
                    // Emit AutoSize for the outer TLP itself so it shrink-wraps its content.
                    sb.append("        this.").append(n.id).append(".AutoSize = true;\n");
                    sb.append("        this.").append(n.id)
                      .append(".AutoSizeMode = System.Windows.Forms.AutoSizeMode.GrowAndShrink;\n");
                }
                boolean col0AllLabels = true;
                for (Node child : n.children) {
                    String gc = child.properties.get("gridCol");
                    if ("0".equals(gc) && !"Label".equals(child.type)) { col0AllLabels = false; break; }
                }
                for (int c = 0; c <= maxCol; c++) {
                    if (hasContentSizedVBoxChildren) {
                        // Content-sized grid: AutoSize columns so they hug their VBox content.
                        sb.append("        this.").append(n.id)
                          .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.AutoSize));\n");
                    } else if (col0AllLabels && c < maxCol) {
                        sb.append("        this.").append(n.id)
                          .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.AutoSize));\n");
                    } else if (col0AllLabels) {
                        sb.append("        this.").append(n.id)
                          .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 100F));\n");
                    } else {
                        sb.append("        this.").append(n.id)
                          .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, ")
                          .append(String.format("%.1f", 100.0 / (maxCol + 1))).append("F));\n");
                    }
                }
                for (int r = 0; r <= maxRow; r++) {
                    // Content-sized grids use AutoSize rows (they size to VBox child content).
                    // Fill-parent grids (Tab children with no ScrollPane) use equal-Percent rows.
                    boolean fillsParent = !insideFlow && !hasContentSizedVBoxChildren
                        && ("Tab".equals(parentType) || "Pane".equals(parentType)
                            || "AnchorPane".equals(parentType) || "StackPane".equals(parentType));
                    // Find the child placed at this row (for explicit-height Absolute rows).
                    Node childAtRow = null;
                    for (Node ch : n.children) {
                        String gr = ch.properties.get("gridRow");
                        int cr = 0;
                        try { if (gr != null) cr = Integer.parseInt(gr); } catch (NumberFormatException ignored) {}
                        if (cr == r) { childAtRow = ch; break; }
                    }
                    String rowChildH = childAtRow == null ? null : childAtRow.properties.get("height");
                    boolean rowChildHMax = "Double.MAX_VALUE".equals(rowChildH);
                    if (fillsParent) {
                        sb.append("        this.").append(n.id)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, ")
                          .append(String.format("%.1f", 100.0 / (maxRow + 1))).append("F));\n");
                    } else if (rowChildH != null && !rowChildHMax) {
                        // Child has explicit preferred height: constrain the row to Absolute so
                        // Dock=Fill data-views are not able to expand beyond their natural size.
                        double absH;
                        try { absH = Double.parseDouble(rowChildH); } catch (NumberFormatException e) { absH = 150; }
                        // Add child's cellMargin*2 (top+bottom) so the control's rendered height
                        // matches its prefHeight after WinForms subtracts the margin inset.
                        String childMarginStr = childAtRow == null ? null : childAtRow.properties.get("cellMargin");
                        if (childMarginStr != null) {
                            try { absH += 2.0 * Double.parseDouble(childMarginStr); }
                            catch (NumberFormatException ignored) {}
                        }
                        sb.append("        this.").append(n.id)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, ")
                          .append(String.format("%.0f", absH)).append("F));\n");
                    } else if (childAtRow != null && "SplitPane".equals(childAtRow.type)) {
                        // SplitContainer with Dock=Fill in an AutoSize row causes a circular
                        // sizing dependency that results in excessive row height. Use a fixed
                        // Absolute height (100px) so the row stays compact.
                        sb.append("        this.").append(n.id)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 100F));\n");
                    } else {
                        sb.append("        this.").append(n.id)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.AutoSize));\n");
                    }
                }
            } else if ("VBox".equals(n.type)) {
                // If the last child has an explicit height, we need an extra Percent(100F)
                // absorber row so WinForms does not give the last Absolute row all extra space.
                boolean needAbsorber = !"true".equals(n.properties.get("_autoSizeTlp"))
                    && !n.children.isEmpty()
                    && n.children.get(n.children.size() - 1).properties.get("height") != null;
                sb.append("        this.").append(n.id).append(".ColumnCount = 1;\n");
                sb.append("        this.").append(n.id).append(".RowCount = ")
                  .append(n.children.size() + (needAbsorber ? 1 : 0)).append(";\n");
                // AutoSize VBoxes (inside FlowPane/HBox) use AutoSize column so GrowAndShrink
                // TLP measures content correctly without circular Percent dependency.
                if (insideFlow) {
                    sb.append("        this.").append(n.id)
                      .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.AutoSize));\n");
                } else {
                    sb.append("        this.").append(n.id)
                      .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 100F));\n");
                }
                // Compute the cell margin that writeWfTree will apply to each child.
                // Margin = ceil(vgap/2) so adjacent cells produce a ~vgap gap.
                // The absolute row height must include 2*margin (top+bottom) so the
                // control inside the row reaches the intended prefHeight after inset.
                int vgapN = 0;
                try { String vv = n.properties.get("vgap"); if (vv != null) vgapN = (int) Double.parseDouble(vv); }
                catch (NumberFormatException ignored) {}
                int cellMarginN = (vgapN + 1) / 2;
                for (int i = 0; i < n.children.size(); i++) {
                    Node child = n.children.get(i);
                    String h = child.properties.get("height");
                    boolean isLast = (i == n.children.size() - 1);
                    if (h != null) {
                        // Add 2*cellMargin so the control's rendered height equals prefHeight
                        double rowH;
                        try { rowH = Double.parseDouble(h) + 2 * cellMarginN; }
                        catch (NumberFormatException ignored) { rowH = 2 * cellMarginN; }
                        sb.append("        this.").append(n.id)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, ")
                          .append(String.format("%.0f", rowH)).append("F));\n");
                    } else if (isLast && !"true".equals(n.properties.get("_autoSizeTlp"))) {
                        sb.append("        this.").append(n.id)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 100F));\n");
                    } else {
                        // AutoSize TLP: all rows AutoSize so the TLP can shrink-wrap its height.
                        sb.append("        this.").append(n.id)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.AutoSize));\n");
                    }
                }
                if (needAbsorber) {
                    // Absorber row: soaks up any extra TLP height so the preceding Absolute
                    // row stays exactly at its specified size (WinForms gives leftover space
                    // to the last row when there is no Percent row).
                    sb.append("        this.").append(n.id)
                      .append(".RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 100F));\n");
                }
            }
        }
        sb.append("\n");

        // TreeView nodes, DataGridView columns+rows - set up before Controls.Add
        for (Node n : app.allNodes) {
            // TreeView (includes TreeTableView which maps to TreeView): build Nodes hierarchy
            if ("TreeView".equals(n.type) || "TreeTableView".equals(n.type)) {
                String rootText = n.properties.get("treeRootText");
                if (rootText != null) {
                    String childrenStr = n.properties.get("treeChildren");
                    boolean expanded  = "true".equals(n.properties.get("treeRootExpanded"));
                    sb.append("        {\n");
                    sb.append("            var _root = this.").append(n.id)
                      .append(".Nodes.Add(").append(ConversionUtils.quoted(rootText)).append(");\n");
                    if (childrenStr != null) {
                        for (String c : childrenStr.split("\\|"))
                            sb.append("            _root.Nodes.Add(").append(ConversionUtils.quoted(c)).append(");\n");
                    }
                    if (expanded) sb.append("            _root.Expand();\n");
                    sb.append("        }\n");
                }
            }
            // DataGridView: columns from tableColumns, rows from tableRows
            if ("TableView".equals(n.type)
                    && n.properties.get("tableColumns") != null) {
                String[] cols = n.properties.get("tableColumns").split("\\|");
                for (String col : cols)
                    sb.append("        this.").append(n.id).append(".Columns.Add(")
                      .append(ConversionUtils.quoted(col)).append(", ").append(ConversionUtils.quoted(col)).append(");\n");
                String rowsProp = n.properties.get("tableRows");
                if (rowsProp != null) {
                    for (String row : rowsProp.split("\\|")) {
                        String[] cells = row.split(",", -1);
                        sb.append("        this.").append(n.id).append(".Rows.Add(");
                        for (int ci = 0; ci < cells.length; ci++) {
                            if (ci > 0) sb.append(", ");
                            sb.append(ConversionUtils.quoted(cells[ci]));
                        }
                        sb.append(");\n");
                    }
                }
            }
        }

        for (Node root : roots) {
            sb.append("        this.").append(root.id)
              .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
            sb.append("        this.Controls.Add(this.").append(root.id).append(");\n");
            writeWfTree(sb, root, wfPanelNest);
        }

        sb.append("        this.ClientSize = new System.Drawing.Size(")
          .append(app.sceneWidth).append(", ").append(app.sceneHeight).append(");\n");
        sb.append("        this.Text = ").append(ConversionUtils.quoted(app.title)).append(";\n");
        if (!app.resizable) {
            sb.append("        this.FormBorderStyle = System.Windows.Forms.FormBorderStyle.FixedSingle;\n");
            sb.append("        this.MaximizeBox = false;\n");
        }
        if (app.formBackColor != null)
            sb.append("        this.BackColor = System.Drawing.ColorTranslator.FromHtml(")
              .append(ConversionUtils.quoted(app.formBackColor)).append(");\n");
        sb.append("    }\n");

        for (Node n : app.allNodes) {
            if ("true".equals(n.properties.get("hasClick")))
                sb.append("\n    private void ").append(n.id)
                  .append("_Click(object sender, EventArgs e)\n    {\n    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Returns true iff the given canonical type maps to a WinForms control that
     * exposes the {@code AutoSizeMode} property (Panel-derived and Button-derived
     * controls).  Controls like Label, ToolStrip, and SplitContainer do NOT have
     * AutoSizeMode even though many support AutoSize.
     */
    private static boolean wfSupportsAutoSizeMode(String canonicalType) {
        if (canonicalType == null) return false;
        switch (canonicalType) {
            case "GridPane": case "VBox": case "HBox": case "FlowPane":
            case "ButtonBar": case "TitledPane": case "Pane": case "AnchorPane":
            case "StackPane": case "Button": case "CheckBox": case "RadioButton":
            case "ToggleButton": case "Accordion":
                return true;
            default:
                return false;
        }
    }

    private static void writeWfTree(StringBuilder sb, Node parent, boolean panelNest) {
        boolean isGrid = "GridPane".equals(parent.type);
        boolean isVBox = "VBox".equals(parent.type);
        boolean isHBox = "HBox".equals(parent.type);
        boolean isTlp  = isGrid || isVBox;
        int hg = 0, vg = 0;
        try { String v = parent.properties.get("hgap"); if (v != null) hg = (int) Double.parseDouble(v); }
        catch (NumberFormatException ignored) {}
        try { String v = parent.properties.get("vgap"); if (v != null) vg = (int) Double.parseDouble(v); }
        catch (NumberFormatException ignored) {}

        // Accordion: children must be added in REVERSE order because Dock=Top
        // makes the LAST control added appear at the top. Reverse so first child
        // in source (e.g. Accordion Panel 1) ends up at the top.
        if ("Accordion".equals(parent.type)) {
            for (int i = parent.children.size() - 1; i >= 0; i--) {
                Node child = parent.children.get(i);
                sb.append("        this.").append(child.id)
                  .append(".Dock = System.Windows.Forms.DockStyle.Top;\n");
                String cH = child.properties.get("height");
                int gh = 55;
                if (cH != null) { try { gh = Math.max(40, (int) Double.parseDouble(cH) + 20); } catch (NumberFormatException ignored) {} }
                sb.append("        this.").append(child.id).append(".Height = ").append(gh).append(";\n");
                sb.append("        this.").append(parent.id)
                  .append(".Controls.Add(this.").append(child.id).append(");\n");
                writeWfTree(sb, child, panelNest);
            }
            return;
        }

        // BorderPane → Panel: children must dock to their assigned region in the
        // correct order (Top, Bottom, Left, Right, Fill/Center) so WinForms layout
        // resolves correctly. Return early after handling all children.
        if ("BorderPane".equals(parent.type)) {
            Map<String, Node> byRegion = new java.util.LinkedHashMap<>();
            for (Node child : parent.children) {
                String region = child.properties.getOrDefault("borderPaneRegion", "center");
                byRegion.putIfAbsent(region, child); // first child per region wins
            }
            // WinForms docking is processed in REVERSE Controls.Add order: the LAST
            // control added is docked first (taking its position), so earlier-added
            // controls get what remains. To get Top docking first and Fill last, add
            // Fill/Center FIRST, then Right, Left, Bottom, and Top LAST.
            String[] addOrder   = {"center", "right", "left", "bottom", "top"};
            String[] dockValues = {"Fill",   "Right",  "Left",  "Bottom", "Top"};
            for (int d = 0; d < addOrder.length; d++) {
                Node child = byRegion.get(addOrder[d]);
                if (child == null) continue;
                sb.append("        this.").append(child.id)
                  .append(".Dock = System.Windows.Forms.DockStyle.")
                  .append(dockValues[d]).append(";\n");
                sb.append("        this.").append(parent.id)
                  .append(".Controls.Add(this.").append(child.id).append(");\n");
                writeWfTree(sb, child, panelNest);
            }
            return;
        }

        for (int i = 0; i < parent.children.size(); i++) {
            Node child = parent.children.get(i);

            String childBorderColor = child.properties.get("borderColor");
            String childBorderWidth = child.properties.get("borderWidth");
            boolean isTextBoxType   = "TextField".equals(child.type) || "TextArea".equals(child.type) || "PasswordField".equals(child.type);
            boolean zeroBorder      = childBorderWidth != null && childBorderWidth.matches("0+(\\.0*)?");

            // needsWrap:
            //   panelNest=true  → wrap ALL text-input nodes (zeroBorder or not) for vertical alignment
            //   panelNest=false → legacy: only wrap nodes with an explicit non-zero borderColor
            boolean needsWrap = isTlp && isTextBoxType
                    && (panelNest || (!zeroBorder && childBorderColor != null && !childBorderColor.isEmpty()));

            if (needsWrap) {
                String childBgColor     = child.properties.get("backColor");
                String effectiveBgColor = (childBgColor != null && !childBgColor.isEmpty())
                        ? childBgColor : "#FFFFFF";

                // Only genuinely multi-line nodes (TextArea / wrapText) use Dock=Fill.
                // A read-only single-line TextField is NOT multiline - it still needs the
                // vertical-anchor technique so the text appears centred inside the bgPanel.
                boolean isMultilineNode = "TextArea".equals(child.type)
                        || "true".equals(child.properties.get("wrapText"));
                String alignStr  = child.properties.get("textAlign");
                boolean topAlign = alignStr != null && alignStr.contains("Top");
                boolean botAlign = alignStr != null && alignStr.contains("Bottom");

                // Horizontal padding inside the bgPanel to match JavaFX TextField's default
                // internal padding of ~0.583em (e.g. 8px at 14px font, 16px at 28px font).
                // Margin is on the TextBox so the bgPanel background colour fills the gap.
                int hPad = 5;
                String fsStr = child.properties.get("fontSize");
                if (fsStr != null) {
                    try { hPad = Math.max(4, (int) Math.round(Double.parseDouble(fsStr) * 0.583)); }
                    catch (NumberFormatException ignored) {}
                }

                // ── Determine the outermost container that gets added to the parent TLP ──
                // Three cases:
                //   A) panelNest + !zeroBorder  → borderPanel → bgPanel → TextBox
                //   B) panelNest +  zeroBorder  → bgPanel → TextBox  (no visible border frame)
                //   C) !panelNest (legacy)      → borderPanel → TextBox  (single layer)
                String outerContainerId;

                if (!panelNest) {
                    // ── Case C: legacy single-layer ───────────────────────────────────────
                    String effectiveBorderColor = (childBorderColor != null && !childBorderColor.isEmpty())
                            ? childBorderColor : "#ABADB3";
                    int borderPad = 1;
                    if (childBorderWidth != null) {
                        try { borderPad = Math.max(1, (int) Double.parseDouble(childBorderWidth)); }
                        catch (NumberFormatException ignored) {}
                    }
                    outerContainerId = child.id + "_borderPanel";
                    // Use a TLP as the border panel so that Anchor works for vertical centering
                    sb.append("        System.Windows.Forms.TableLayoutPanel ").append(outerContainerId)
                      .append(" = new System.Windows.Forms.TableLayoutPanel();\n");
                    sb.append("        ").append(outerContainerId)
                      .append(".BackColor = System.Drawing.ColorTranslator.FromHtml(")
                      .append(ConversionUtils.quoted(effectiveBorderColor)).append(");\n");
                    sb.append("        ").append(outerContainerId)
                      .append(".Padding = new System.Windows.Forms.Padding(").append(borderPad).append(");\n");
                    sb.append("        ").append(outerContainerId).append(".ColumnCount = 1;\n");
                    sb.append("        ").append(outerContainerId).append(".RowCount = 1;\n");
                    sb.append("        ").append(outerContainerId)
                      .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle("
                           + "System.Windows.Forms.SizeType.Percent, 100F));\n");
                    sb.append("        ").append(outerContainerId)
                      .append(".RowStyles.Add(new System.Windows.Forms.RowStyle("
                           + "System.Windows.Forms.SizeType.Percent, 100F));\n");
                    sb.append("        ").append(outerContainerId)
                      .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                    if (hg > 0 || vg > 0) {
                        int m = Math.max((hg + 1) / 2, (vg + 1) / 2);
                        sb.append("        ").append(outerContainerId)
                          .append(".Margin = new System.Windows.Forms.Padding(").append(m).append(");\n");
                    }
                    sb.append("        this.").append(child.id)
                      .append(".Margin = new System.Windows.Forms.Padding(0);\n");
                    sb.append("        this.").append(child.id)
                      .append(".BorderStyle = System.Windows.Forms.BorderStyle.None;\n");
                    {
                        boolean cIsMultiline = "TextArea".equals(child.type)
                                || "true".equals(child.properties.get("wrapText"));
                        if (cIsMultiline) {
                            sb.append("        this.").append(child.id)
                              .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                        } else {
                            String cAlign = child.properties.get("textAlign");
                            boolean cTop = cAlign != null && cAlign.contains("Top");
                            boolean cBot = cAlign != null && cAlign.contains("Bottom");
                            String cAnchor = cTop
                                    ? "System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                    : cBot
                                    ? "System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                    : "System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right";
                            sb.append("        this.").append(child.id)
                              .append(".Anchor = ").append(cAnchor).append(";\n");
                        }
                    }
                    sb.append("        ").append(outerContainerId)
                      .append(".Controls.Add(this.").append(child.id).append(");\n");

                } else {
                    // ── Cases A & B: full panel nesting ───────────────────────────────────
                    String bgPanelId = child.id + "_bgPanel";

                    if (!zeroBorder) {
                        // Case A: visible border frame
                        String effectiveBorderColor = (childBorderColor != null && !childBorderColor.isEmpty())
                                ? childBorderColor : "#ABADB3";
                        int borderPad = 1;
                        if (childBorderWidth != null) {
                            try { borderPad = Math.max(1, (int) Double.parseDouble(childBorderWidth)); }
                            catch (NumberFormatException ignored) {}
                        }
                        String borderPanelId = child.id + "_borderPanel";
                        outerContainerId = borderPanelId;
                        sb.append("        System.Windows.Forms.Panel ").append(borderPanelId)
                          .append(" = new System.Windows.Forms.Panel();\n");
                        sb.append("        ").append(borderPanelId)
                          .append(".BackColor = System.Drawing.ColorTranslator.FromHtml(")
                          .append(ConversionUtils.quoted(effectiveBorderColor)).append(");\n");
                        sb.append("        ").append(borderPanelId)
                          .append(".Padding = new System.Windows.Forms.Padding(").append(borderPad).append(");\n");
                        sb.append("        ").append(borderPanelId)
                          .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                        if (hg > 0 || vg > 0) {
                            int m = Math.max((hg + 1) / 2, (vg + 1) / 2);
                            sb.append("        ").append(borderPanelId)
                              .append(".Margin = new System.Windows.Forms.Padding(").append(m).append(");\n");
                        }
                        // bgPanel goes inside borderPanel
                        sb.append("        System.Windows.Forms.TableLayoutPanel ").append(bgPanelId)
                          .append(" = new System.Windows.Forms.TableLayoutPanel();\n");
                        sb.append("        ").append(bgPanelId)
                          .append(".BackColor = System.Drawing.ColorTranslator.FromHtml(")
                          .append(ConversionUtils.quoted(effectiveBgColor)).append(");\n");
                        sb.append("        ").append(bgPanelId)
                          .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                        sb.append("        ").append(bgPanelId).append(".ColumnCount = 1;\n");
                        sb.append("        ").append(bgPanelId).append(".RowCount = 1;\n");
                        sb.append("        ").append(bgPanelId)
                          .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle("
                               + "System.Windows.Forms.SizeType.Percent, 100F));\n");
                        sb.append("        ").append(bgPanelId)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle("
                               + "System.Windows.Forms.SizeType.Percent, 100F));\n");
                        // TextBox props
                        sb.append("        this.").append(child.id)
                          .append(".BorderStyle = System.Windows.Forms.BorderStyle.None;\n");
                        sb.append("        this.").append(child.id)
                          .append(".BackColor = System.Drawing.ColorTranslator.FromHtml(")
                          .append(ConversionUtils.quoted(effectiveBgColor)).append(");\n");
                        if (isMultilineNode) {
                            sb.append("        this.").append(child.id)
                              .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                        } else {
                            sb.append("        this.").append(child.id)
                              .append(".Multiline = false;\n");
                            String heightStrA = child.properties.get("height");
                            if (heightStrA != null) {
                                try {
                                    int prefH = (int) Math.round(Double.parseDouble(heightStrA));
                                    int bgH = prefH - 2 * borderPad;
                                    int fs = 20;
                                    if (fsStr != null) { try { fs = (int) Math.round(Double.parseDouble(fsStr)); } catch (NumberFormatException ex2) {} }
                                    int textBoxH = fs + 4;
                                    int topPadA = botAlign ? 0 : (topAlign ? 0 : Math.max(0, (bgH - textBoxH) / 2));
                                    sb.append("        ").append(bgPanelId)
                                      .append(".Padding = new System.Windows.Forms.Padding(")
                                      .append(hPad).append(", ").append(topPadA).append(", ").append(hPad).append(", 0);\n");
                                    sb.append("        this.").append(child.id)
                                      .append(".Margin = new System.Windows.Forms.Padding(0);\n");
                                    String anchorA = botAlign
                                            ? "System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                            : "System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right";
                                    sb.append("        this.").append(child.id)
                                      .append(".Anchor = ").append(anchorA).append(";\n");
                                } catch (NumberFormatException ignored) {
                                    sb.append("        this.").append(child.id)
                                      .append(".Margin = new System.Windows.Forms.Padding(")
                                      .append(hPad).append(", 1, ").append(hPad).append(", 0);\n");
                                    String anchorFbA = topAlign
                                            ? "System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                            : botAlign
                                            ? "System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                            : "System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right";
                                    sb.append("        this.").append(child.id)
                                      .append(".Anchor = ").append(anchorFbA).append(";\n");
                                }
                            } else {
                                sb.append("        this.").append(child.id)
                                  .append(".Margin = new System.Windows.Forms.Padding(")
                                  .append(hPad).append(", 1, ").append(hPad).append(", 0);\n");
                                String anchorFbA = topAlign
                                        ? "System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                        : botAlign
                                        ? "System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                        : "System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right";
                                sb.append("        this.").append(child.id)
                                  .append(".Anchor = ").append(anchorFbA).append(";\n");
                            }
                        }
                        sb.append("        ").append(bgPanelId)
                          .append(".Controls.Add(this.").append(child.id).append(");\n");
                        sb.append("        ").append(borderPanelId)
                          .append(".Controls.Add(").append(bgPanelId).append(");\n");

                    } else {
                        // Case B: zeroBorder - bgPanel directly (no border frame, still centres text)
                        outerContainerId = bgPanelId;
                        sb.append("        System.Windows.Forms.TableLayoutPanel ").append(bgPanelId)
                          .append(" = new System.Windows.Forms.TableLayoutPanel();\n");
                        sb.append("        ").append(bgPanelId)
                          .append(".BackColor = System.Drawing.ColorTranslator.FromHtml(")
                          .append(ConversionUtils.quoted(effectiveBgColor)).append(");\n");
                        sb.append("        ").append(bgPanelId)
                          .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                        sb.append("        ").append(bgPanelId).append(".ColumnCount = 1;\n");
                        sb.append("        ").append(bgPanelId).append(".RowCount = 1;\n");
                        sb.append("        ").append(bgPanelId)
                          .append(".ColumnStyles.Add(new System.Windows.Forms.ColumnStyle("
                               + "System.Windows.Forms.SizeType.Percent, 100F));\n");
                        sb.append("        ").append(bgPanelId)
                          .append(".RowStyles.Add(new System.Windows.Forms.RowStyle("
                               + "System.Windows.Forms.SizeType.Percent, 100F));\n");
                        // Gap margin between cells (driven by hgap/vgap), separate from padding.
                        if (hg > 0 || vg > 0) {
                            int m = Math.max((hg + 1) / 2, (vg + 1) / 2);
                            sb.append("        ").append(bgPanelId)
                              .append(".Margin = new System.Windows.Forms.Padding(").append(m).append(");\n");
                        }
                        // TextBox props
                        sb.append("        this.").append(child.id)
                          .append(".BorderStyle = System.Windows.Forms.BorderStyle.None;\n");
                        sb.append("        this.").append(child.id)
                          .append(".BackColor = System.Drawing.ColorTranslator.FromHtml(")
                          .append(ConversionUtils.quoted(effectiveBgColor)).append(");\n");
                        if (isMultilineNode) {
                            sb.append("        this.").append(child.id)
                              .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                        } else {
                            sb.append("        this.").append(child.id)
                              .append(".Multiline = false;\n");
                            String heightStrB = child.properties.get("height");
                            if (heightStrB != null) {
                                try {
                                    int prefH = (int) Math.round(Double.parseDouble(heightStrB));
                                    int fs = 20;
                                    if (fsStr != null) { try { fs = (int) Math.round(Double.parseDouble(fsStr)); } catch (NumberFormatException ex2) {} }
                                    int textBoxH = fs + 4;
                                    int topPadB = botAlign ? 0 : (topAlign ? 0 : Math.max(0, (prefH - textBoxH) / 2));
                                    sb.append("        ").append(bgPanelId)
                                      .append(".Padding = new System.Windows.Forms.Padding(")
                                      .append(hPad).append(", ").append(topPadB).append(", ").append(hPad).append(", 0);\n");
                                    sb.append("        this.").append(child.id)
                                      .append(".Margin = new System.Windows.Forms.Padding(0);\n");
                                    String anchorB = botAlign
                                            ? "System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                            : "System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right";
                                    sb.append("        this.").append(child.id)
                                      .append(".Anchor = ").append(anchorB).append(";\n");
                                } catch (NumberFormatException ignored) {
                                    sb.append("        this.").append(child.id)
                                      .append(".Margin = new System.Windows.Forms.Padding(")
                                      .append(hPad).append(", 1, ").append(hPad).append(", 0);\n");
                                    String anchorFbB = topAlign
                                            ? "System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                            : botAlign
                                            ? "System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                            : "System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right";
                                    sb.append("        this.").append(child.id)
                                      .append(".Anchor = ").append(anchorFbB).append(";\n");
                                }
                            } else {
                                sb.append("        this.").append(child.id)
                                  .append(".Margin = new System.Windows.Forms.Padding(")
                                  .append(hPad).append(", 1, ").append(hPad).append(", 0);\n");
                                String anchorFbB = topAlign
                                        ? "System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                        : botAlign
                                        ? "System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right"
                                        : "System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right";
                                sb.append("        this.").append(child.id)
                                  .append(".Anchor = ").append(anchorFbB).append(";\n");
                            }
                        }
                        sb.append("        ").append(bgPanelId)
                          .append(".Controls.Add(this.").append(child.id).append(");\n");
                    }
                }

                // ── Add outerContainerId to parent TLP + set grid/vbox position ──────────
                sb.append("        this.").append(parent.id)
                  .append(".Controls.Add(").append(outerContainerId).append(");\n");
                if (isGrid) {
                    String gc = child.properties.get("gridCol"), gr = child.properties.get("gridRow");
                    if (gc != null && gr != null)
                        sb.append("        this.").append(parent.id)
                          .append(".SetCellPosition(").append(outerContainerId)
                          .append(", new System.Windows.Forms.TableLayoutPanelCellPosition(")
                          .append(gc).append(", ").append(gr).append("));\n");
                    String cs = child.properties.get("colSpan");
                    if (cs != null) {
                        try {
                            if (Integer.parseInt(cs) > 1)
                                sb.append("        this.").append(parent.id)
                                  .append(".SetColumnSpan(").append(outerContainerId)
                                  .append(", ").append(cs).append(");\n");
                        } catch (NumberFormatException ignored) {}
                    }
                } else if (isVBox) {
                    sb.append("        this.").append(parent.id)
                      .append(".SetCellPosition(").append(outerContainerId)
                      .append(", new System.Windows.Forms.TableLayoutPanelCellPosition(0, ")
                      .append(i).append("));\n");
                }
                writeWfTree(sb, child, panelNest);
                continue;
            }

            if (isTlp) {
                boolean isSingleLineInput = "TextField".equals(child.type) || "PasswordField".equals(child.type);
                boolean isTextArea = "TextArea".equals(child.type);
                boolean isAutoSizeTlp = "true".equals(parent.properties.get("_autoSizeTlp"));
                boolean parentIsAutoSizeGrid = "true".equals(parent.properties.get("_autoSizeGrid"));
                if (isSingleLineInput) {
                    // Limit width to match JavaFX TextField preferred width; no horizontal stretch.
                    if (child.properties.get("width") == null)
                        sb.append("        this.").append(child.id).append(".Width = 250;\n");
                } else if (isTextArea) {
                    // TextArea: fixed width (matches JavaFX preferred width) + explicit height.
                    // Anchor=Top|Left keeps it top-anchored without horizontal stretch.
                    sb.append("        this.").append(child.id)
                      .append(".Anchor = System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left;\n");
                    if (child.properties.get("width") == null)
                        sb.append("        this.").append(child.id).append(".Width = 300;\n");
                    String h = child.properties.get("height");
                    int ht = 80;
                    if (h != null) { try { ht = (int) Double.parseDouble(h); } catch (NumberFormatException ignored) {} }
                    sb.append("        this.").append(child.id).append(".Height = ").append(ht).append(";\n");
                } else if (parentIsAutoSizeGrid && wfSupportsAutoSizeMode(child.type)) {
                    // VBox sub-TLP inside a content-sized GridPane: use AutoSize instead of
                    // Dock=Fill so the sub-TLP wraps its content (label + data view).
                    // Only applies to container types that expose AutoSizeMode (Panel-derived);
                    // labels, ToolStrip, SplitContainer etc. fall through to Dock=Fill below.
                    child.properties.put("_autoSizeTlp", "true");
                    sb.append("        this.").append(child.id).append(".AutoSize = true;\n");
                    sb.append("        this.").append(child.id)
                      .append(".AutoSizeMode = System.Windows.Forms.AutoSizeMode.GrowAndShrink;\n");
                } else if (!isAutoSizeTlp && "TitledPane".equals(child.type) && !isVBox) {
                    // Standalone TitledPane (GroupBox) in a GridPane row: use AutoSize so the
                    // AutoSize row measures the GroupBox content height rather than creating a
                    // circular Dock=Fill dependency that results in an over-large row.
                    child.properties.put("_autoSizeTlp", "true");
                    sb.append("        this.").append(child.id).append(".AutoSize = true;\n");
                    sb.append("        this.").append(child.id)
                      .append(".AutoSizeMode = System.Windows.Forms.AutoSizeMode.GrowAndShrink;\n");
                    sb.append("        this.").append(child.id)
                      .append(".Anchor = System.Windows.Forms.AnchorStyles.Left"
                            + " | System.Windows.Forms.AnchorStyles.Right"
                            + " | System.Windows.Forms.AnchorStyles.Top;\n");
                } else if (!isAutoSizeTlp && !"Accordion".equals(child.type)
                        && !"Separator".equals(child.type)
                        && !"ScrollBar".equals(child.type)
                        && !(child.properties.get("width") != null && child.properties.get("height") != null
                             // Data views have explicit prefSize in JavaFX but should still fill
                             // their TLP cell via Dock=Fill (their row is Absolute-height so the
                             // cell height is already constrained; Dock=Fill only stretches width).
                             // Canvas is also explicit-size but must keep its exact dimensions.
                             && !"ListView".equals(child.type) && !"TreeView".equals(child.type)
                             && !"TableView".equals(child.type) && !"TreeTableView".equals(child.type))) {
                    // For horizontal containers (HBox/FlowPane/ButtonBar) inside a VBox TLP:
                    // VBox rows are AutoSize, so Dock=Fill creates a circular sizing dependency
                    // (row tries to shrink to child height, child tries to fill row) → WinForms
                    // resolves it by giving the panel the leftover parent height (~100-200px).
                    // Fix: AutoSize=true+GrowAndShrink lets the panel shrink to its content height;
                    // Anchor=Left|Right gives horizontal stretch. Apply for all TLP parents (VBox
                    // or GridPane) because GridPane rows are also often AutoSize and Dock=Fill would
                    // create the same circular sizing dependency there.
                    boolean isHorizontalContainer = "HBox".equals(child.type)
                            || "FlowPane".equals(child.type)
                            || "ButtonBar".equals(child.type);
                    if (isHorizontalContainer && isTlp) {
                        sb.append("        this.").append(child.id)
                          .append(".Anchor = System.Windows.Forms.AnchorStyles.Left"
                                + " | System.Windows.Forms.AnchorStyles.Right;\n");
                        sb.append("        this.").append(child.id).append(".AutoSize = true;\n");
                        sb.append("        this.").append(child.id)
                          .append(".AutoSizeMode = System.Windows.Forms.AutoSizeMode.GrowAndShrink;\n");
                    } else {
                        sb.append("        this.").append(child.id)
                          .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                        // TitledPane (GroupBox) inside VBox (accordion TLP): override the WinForms
                        // default 3px margin so the GroupBox fills its absolute-height row fully.
                        if ("TitledPane".equals(child.type) && isVBox) {
                            sb.append("        this.").append(child.id)
                              .append(".Margin = new System.Windows.Forms.Padding(0);\n");
                        }
                    }
                } else if (!isAutoSizeTlp && "ScrollBar".equals(child.type)) {
                    // ScrollBar: give it a reasonable fixed width rather than Dock=Fill (full tab width).
                    String sbW = child.properties.get("width");
                    int scrollW = 300;
                    if (sbW != null) { try { scrollW = (int) Double.parseDouble(sbW); } catch (NumberFormatException ignored) {} }
                    sb.append("        this.").append(child.id).append(".Width = ").append(scrollW).append(";\n");
                } else if (!isAutoSizeTlp && "Accordion".equals(child.type)) {
                    // Accordion in non-AutoSize TLP: Anchor stretches it horizontally
                    // while its explicit Height (set in the properties loop) is preserved.
                    sb.append("        this.").append(child.id)
                      .append(".Anchor = System.Windows.Forms.AnchorStyles.Top"
                            + " | System.Windows.Forms.AnchorStyles.Left"
                            + " | System.Windows.Forms.AnchorStyles.Right;\n");
                }
                // AutoSize TLP children: no Dock/Anchor - let them size to their natural
                // preferred size so the AutoSize column can measure content width correctly.
                // Add AutoSize=true so Labels and Buttons expand to fit their text.
                if (isAutoSizeTlp && !isSingleLineInput && !isTextArea) {
                    boolean supportsAutoSize =
                            "Button".equals(child.type)
                         || "Label".equals(child.type)
                         || "CheckBox".equals(child.type)
                         || "RadioButton".equals(child.type)
                         || "Hyperlink".equals(child.type)
                         || "ToggleButton".equals(child.type)
                         || "MenuButton".equals(child.type)
                         || "SplitMenuButton".equals(child.type);
                    if (supportsAutoSize)
                        sb.append("        this.").append(child.id).append(".AutoSize = true;\n");
                }
                {
                    String cmStr = child.properties.get("cellMargin");
                    int cm = 0;
                    if (cmStr != null) { try { cm = Integer.parseInt(cmStr); } catch (NumberFormatException ignored) {} }
                    int margin = (hg > 0 || vg > 0) ? Math.max((hg + 1) / 2, (vg + 1) / 2) : cm;
                    if (margin > 0) {
                        sb.append("        this.").append(child.id)
                          .append(".Margin = new System.Windows.Forms.Padding(").append(margin).append(");\n");
                    }
                }
            } else if (isHBox) {
                String cmStr = child.properties.get("cellMargin");
                int cm = 0;
                if (cmStr != null) { try { cm = Integer.parseInt(cmStr); } catch (NumberFormatException ignored) {} }
                int margin = (hg > 0 || vg > 0) ? Math.max((hg + 1) / 2, (vg + 1) / 2) : cm;
                if (margin > 0) {
                    sb.append("        this.").append(child.id)
                      .append(".Margin = new System.Windows.Forms.Padding(").append(margin).append(");\n");
                }
                // Label-like controls in HBox must AutoSize so text isn't truncated and
                // the FlowLayoutPanel row doesn't become tall due to text wrapping.
                boolean hboxAutoSize =
                        "Label".equals(child.type)
                     || "Hyperlink".equals(child.type)
                     || "Button".equals(child.type)
                     || "CheckBox".equals(child.type)
                     || "RadioButton".equals(child.type)
                     || "ToggleButton".equals(child.type)
                     || "MenuButton".equals(child.type)
                     || "SplitMenuButton".equals(child.type);
                if (hboxAutoSize)
                    sb.append("        this.").append(child.id).append(".AutoSize = true;\n");
            } else if ("Tab".equals(parent.type)
                    || "ScrollPane".equals(parent.type)
                    || "StackPane".equals(parent.type)
                    || "Pane".equals(parent.type)
                    || "AnchorPane".equals(parent.type)) {
                // Children of fill-containers must expand to fill their parent.
                // Skip MenuBar: MenuStrip handles its own docking to the top.
                // Skip controls with explicit size: they should retain their preferred size.
                boolean hasExplicitSize = child.properties.get("width") != null
                        && child.properties.get("height") != null;
                // Content-sized grids (_autoSizeGrid) must NOT get Dock=Fill: they already
                // emit AutoSize=true in generateStyles and should float at their natural size.
                boolean childIsAutoSizeGrid = "true".equals(child.properties.get("_autoSizeGrid"));
                if (!"MenuBar".equals(child.type) && !hasExplicitSize && !childIsAutoSizeGrid) {
                    sb.append("        this.").append(child.id)
                      .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                }
            }
            if ("TabPane".equals(parent.type)) {
                sb.append("        this.").append(parent.id).append(".TabPages.Add(this.").append(child.id).append(");\n");
            } else if ("SplitPane".equals(parent.type)) {
                String panel = i == 0 ? ".Panel1" : ".Panel2";
                // child fills its SplitContainer panel
                sb.append("        this.").append(child.id).append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                sb.append("        this.").append(parent.id).append(panel)
                  .append(".Controls.Add(this.").append(child.id).append(");\n");
            } else if ("ToolBar".equals(parent.type)) {
                sb.append("        this.").append(parent.id).append(".Items.Add(this.").append(child.id).append(");\n");
            } else if ("MenuBar".equals(parent.type)) {
                // MenuStrip children (Menu → ToolStripMenuItem) go into .Items
                sb.append("        this.").append(parent.id).append(".Items.Add(this.").append(child.id).append(");\n");
            } else if ("Menu".equals(parent.type) || "CheckMenuItem".equals(parent.type) || "RadioMenuItem".equals(parent.type)) {
                // ToolStripMenuItem children go into .DropDownItems
                sb.append("        this.").append(parent.id).append(".DropDownItems.Add(this.").append(child.id).append(");\n");
            } else if ("MenuButton".equals(parent.type) || "SplitMenuButton".equals(parent.type)) {
                // MenuItem children map to ToolStripMenuItem which cannot be added to
                // Button.Controls - skip the Controls.Add entirely.
            } else if ("TitledPane".equals(parent.type)) {
                // GroupBox content: fill the box below the title bar.
                // BUT when the GroupBox itself is AutoSize+GrowAndShrink (_autoSizeTlp),
                // Dock=Fill creates a circular sizing dependency and collapses the box.
                // In that case, use AutoSize=true so the child measures itself and the
                // GroupBox sizes to fit it.
                boolean groupBoxAutoSize = "true".equals(parent.properties.get("_autoSizeTlp"));
                if (groupBoxAutoSize) {
                    sb.append("        this.").append(child.id).append(".AutoSize = true;\n");
                    // Position below GroupBox title bar (Padding top=20) to avoid overlapping the title text
                    sb.append("        this.").append(child.id)
                      .append(".Location = new System.Drawing.Point(5, 20);\n");
                } else {
                    sb.append("        this.").append(child.id)
                      .append(".Dock = System.Windows.Forms.DockStyle.Fill;\n");
                }
                sb.append("        this.").append(parent.id)
                  .append(".Controls.Add(this.").append(child.id).append(");\n");
            } else if ("Accordion".equals(parent.type)) {
                // This branch is unreachable: Accordion returns early above.
                // Kept only as a no-op fallback.
            } else {
                sb.append("        this.").append(parent.id).append(".Controls.Add(this.").append(child.id).append(");\n");
            }
            if (isGrid) {
                String gc = child.properties.get("gridCol"), gr = child.properties.get("gridRow");
                if (gc != null && gr != null)
                    sb.append("        this.").append(parent.id)
                      .append(".SetCellPosition(this.").append(child.id)
                      .append(", new System.Windows.Forms.TableLayoutPanelCellPosition(")
                      .append(gc).append(", ").append(gr).append("));\n");                String cs = child.properties.get("colSpan");
                if (cs != null) {
                    try {
                        if (Integer.parseInt(cs) > 1)
                            sb.append("        this.").append(parent.id)
                              .append(".SetColumnSpan(this.").append(child.id)
                              .append(", ").append(cs).append(");\n");
                    } catch (NumberFormatException ignored) {}
                }            } else if (isVBox) {
                sb.append("        this.").append(parent.id)
                  .append(".SetCellPosition(this.").append(child.id)
                  .append(", new System.Windows.Forms.TableLayoutPanelCellPosition(0, ")
                  .append(i).append("));\n");
            }
            writeWfTree(sb, child, panelNest);
        }
    }

    private static String wfFontStyle(Node n) {
        boolean bold   = "BOLD".equals(n.properties.get("fontWeight"));
        boolean italic = "ITALIC".equals(n.properties.get("fontPosture"));
        if (bold && italic) return "Bold | System.Drawing.FontStyle.Italic";
        if (bold)   return "Bold";
        if (italic) return "Italic";
        return "Regular";
    }

    private static String toHorizontalAlignment(String contentAlignment) {
        if (contentAlignment == null) return "Left";
        String ca = contentAlignment.toLowerCase();
        if (ca.contains("right"))  return "Right";
        if (ca.contains("center")) return "Center";
        return "Left";
    }

    private static String[] splitFontWeight(String fullName) {
        java.util.LinkedHashMap<String, String> weights = new java.util.LinkedHashMap<>();
        weights.put("thin",        "THIN");
        weights.put("extra light", "EXTRA_LIGHT");
        weights.put("extralight",  "EXTRA_LIGHT");
        weights.put("ultra light", "EXTRA_LIGHT");
        weights.put("ultralight",  "EXTRA_LIGHT");
        weights.put("light",       "LIGHT");
        weights.put("semi bold",   "SEMI_BOLD");
        weights.put("semibold",    "SEMI_BOLD");
        weights.put("demi bold",   "SEMI_BOLD");
        weights.put("demibold",    "SEMI_BOLD");
        weights.put("extra bold",  "EXTRA_BOLD");
        weights.put("extrabold",   "EXTRA_BOLD");
        weights.put("ultra bold",  "EXTRA_BOLD");
        weights.put("ultrabold",   "EXTRA_BOLD");
        weights.put("black",       "BLACK");
        weights.put("heavy",       "BLACK");
        weights.put("medium",      "MEDIUM");
        weights.put("bold",        "BOLD");
        String lower = fullName.toLowerCase();
        for (java.util.Map.Entry<String, String> e : weights.entrySet()) {
            String suffix = e.getKey();
            if (lower.endsWith(" " + suffix) || lower.equals(suffix)) {
                String base = fullName.substring(0, fullName.length() - suffix.length()).trim();
                return new String[]{base.isEmpty() ? fullName : base, e.getValue()};
            }
        }
        return new String[]{fullName, null};
    }

    private static String rgbFromArgb(Matcher m, int firstGroup) {
        try {
            String g4 = m.group(firstGroup + 3);
            if (g4 != null)
                return ConversionUtils.rgbToHex(m.group(firstGroup + 1), m.group(firstGroup + 2), g4);
        } catch (IndexOutOfBoundsException ignored) {}
        return ConversionUtils.rgbToHex(m.group(firstGroup), m.group(firstGroup + 1), m.group(firstGroup + 2));
    }

    private static boolean isSystemColorName(String name) {
        if (name == null) return true;
        switch (name) {
            case "Empty": case "Transparent":
            case "Control": case "ControlDark": case "ControlDarkDark":
            case "ControlLight": case "ControlLightLight": case "ControlText":
            case "Window": case "WindowText": case "WindowFrame":
            case "ButtonFace": case "ButtonShadow": case "ButtonHighlight":
            case "AppWorkspace": case "Desktop": case "GrayText":
            case "Highlight": case "HighlightText": case "HotTrack":
            case "InactiveBorder": case "InactiveCaption": case "InactiveCaptionText":
            case "Info": case "InfoText": case "Menu": case "MenuText":
            case "ScrollBar": case "ActiveBorder": case "ActiveCaption":
            case "ActiveCaptionText": case "GradientActiveCaption":
            case "GradientInactiveCaption": case "MenuBar": case "MenuHighlight":
                return true;
            default:
                return false;
        }
    }

    private static String toClassName(String title) {
        if (title == null || title.isBlank()) return "Converted";
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : title.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(cap ? Character.toUpperCase(c) : c);
                cap = false;
            } else {
                cap = true;
            }
        }
        String result = sb.toString();
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) result = "_" + result;
        return result.isEmpty() ? "Converted" : result;
    }

    /**
     * Normalizes both C# and C++/CLI WinForms source before regex matching.
     */
    private static String normalizeCs(String source) {
        String s = source == null ? "" : source;

        s = s.replaceAll("(?m)^\\s*#[^\n]*", "");
        s = s.replaceAll("(?m)^[^\n]*using\\s+namespace[^\n]*", "");
        s = s.replaceAll("L\"", "\"");
        for (int pass = 0; pass < 6; pass++)
            s = s.replaceAll("static_cast\\s*<[^>]+>\\s*\\(([^()]+)\\)", "$1");
        for (int pass = 0; pass < 4; pass++)
            s = s.replaceAll("cli\\s*::\\s*safe_cast\\s*<[^>]+>\\s*\\(([^()]+)\\)", "$1");
        s = s.replaceAll("\\bgcnew\\b\\s*", "new ");
        s = s.replaceAll("(\\w)\\s*\\^", "$1");
        s = s.replaceAll("::", ".");
        s = s.replaceAll("->", ".");
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("//[^\n]*", "");

        String[] lines = s.split("\n");
        StringBuilder out = new StringBuilder();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (current.length() > 0) {
                    out.append(current.toString().replaceAll("\\s+", " ").trim()).append("\n");
                    current.setLength(0);
                }
                continue;
            }
            if (current.length() > 0) current.append(' ');
            current.append(trimmed);
            if (trimmed.endsWith(";") || trimmed.endsWith("{") || trimmed.endsWith("}")) {
                out.append(current.toString().replaceAll("\\s+", " ").trim()).append("\n");
                current.setLength(0);
            }
        }
        if (current.length() > 0)
            out.append(current.toString().replaceAll("\\s+", " ").trim()).append("\n");
        return out.toString();
    }
}

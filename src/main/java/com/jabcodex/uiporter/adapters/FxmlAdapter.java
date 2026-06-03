package com.jabcodex.uiporter.adapters;

import com.jabcodex.uiporter.core.ConversionUtils;
import com.jabcodex.uiporter.core.FrameworkAdapter;
import com.jabcodex.uiporter.model.AppMetadata;
import com.jabcodex.uiporter.model.Node;

import java.util.*;

public class FxmlAdapter implements FrameworkAdapter {

    @Override
    public String getDisplayName() { return "JavaFX (FXML)"; }

    @Override
    public String[] getFileExtensions() { return new String[]{".fxml"}; }

    @Override
    public String getSyntaxLanguage() { return "xml"; }

    @Override
    public AppMetadata parse(String source) {
        if (source == null || source.isBlank()) return new AppMetadata();
        return parseFxml(source);
    }

    @Override
    public String generate(AppMetadata app, Map<String, Object> options) {
        return generateFxml(app, options);
    }

    private static final Set<String> KNOWN_TYPES = new HashSet<>(Arrays.asList(
        "Button", "Label", "TextField", "PasswordField", "TextArea",
        "CheckBox", "RadioButton", "ComboBox", "ChoiceBox", "ListView", "TableView",
        "Slider", "ProgressBar", "ProgressIndicator", "Spinner", "DatePicker", "ColorPicker",
        "Hyperlink", "ToggleButton", "MenuButton", "SplitMenuButton", "ButtonBar",
        "ImageView", "Separator",
        "MenuBar", "ToolBar", "TabPane", "Tab", "ScrollPane", "ScrollBar", "TreeView",
        "Menu", "MenuItem", "SeparatorMenuItem", "CheckMenuItem", "RadioMenuItem",
        "VBox", "HBox", "GridPane", "BorderPane", "FlowPane", "Pane",
        "StackPane", "AnchorPane", "TilePane", "SplitPane", "TitledPane", "GroupBox",
        "Accordion", "Pagination", "TreeTableView", "Canvas", "TableColumn", "TreeItem",
        "Region", "Circle", "Rectangle", "Line", "Ellipse", "Polygon",
        "Text", "TextFlow", "Group",
        "PieChart", "BarChart", "LineChart", "AreaChart"
    ));

    private static final Map<String, String> TYPE_PACKAGE = new LinkedHashMap<>();
    static {
        for (String t : new String[]{
            "Button","Label","TextField","PasswordField","TextArea","CheckBox","RadioButton",
            "ComboBox","ChoiceBox","ListView","TableView","TableColumn","TreeView","TreeItem",
            "TreeTableView","Slider","ProgressBar","ProgressIndicator","Spinner","DatePicker",
            "ColorPicker","Hyperlink","ToggleButton","MenuButton","SplitMenuButton","ButtonBar",
            "Separator","MenuBar","ToolBar","TabPane","Tab","ScrollPane","ScrollBar",
            "Menu","MenuItem","SeparatorMenuItem","CheckMenuItem","RadioMenuItem",
            "Accordion","Pagination","TitledPane","SeparatorMenuItem"
        }) TYPE_PACKAGE.put(t, "javafx.scene.control");

        for (String t : new String[]{
            "VBox","HBox","GridPane","BorderPane","FlowPane","Pane",
            "StackPane","AnchorPane","TilePane"
        }) TYPE_PACKAGE.put(t, "javafx.scene.layout");
        // SplitPane is a control, not a layout
        TYPE_PACKAGE.put("SplitPane", "javafx.scene.control");

        TYPE_PACKAGE.put("ImageView",  "javafx.scene.image");
        TYPE_PACKAGE.put("Canvas",     "javafx.scene.canvas");
        TYPE_PACKAGE.put("GroupBox",   "javafx.scene.layout");
        TYPE_PACKAGE.put("Region",     "javafx.scene.layout");

        for (String t : new String[]{"Circle", "Rectangle", "Line", "Ellipse", "Polygon"})
            TYPE_PACKAGE.put(t, "javafx.scene.shape");

        for (String t : new String[]{"Text", "TextFlow"})
            TYPE_PACKAGE.put(t, "javafx.scene.text");

        TYPE_PACKAGE.put("Group", "javafx.scene");

        for (String t : new String[]{"PieChart", "BarChart", "LineChart", "AreaChart"})
            TYPE_PACKAGE.put(t, "javafx.scene.chart");

        TYPE_PACKAGE.put("FXCollections",          "javafx.collections");
        TYPE_PACKAGE.put("TreeItem",               "javafx.scene.control");
        TYPE_PACKAGE.put("ToggleGroup",            "javafx.scene.control");
        TYPE_PACKAGE.put("SpinnerValueFactory",    "javafx.scene.control");
        TYPE_PACKAGE.put("Tooltip",                "javafx.scene.control");
    }

    private static final Map<String, String> POS_TO_ALIGN = new LinkedHashMap<>();
    private static final Map<String, String> ALIGN_TO_POS = new LinkedHashMap<>();
    static {
        POS_TO_ALIGN.put("CENTER_RIGHT", "MiddleRight");
        POS_TO_ALIGN.put("CENTER_LEFT",  "MiddleLeft");
        POS_TO_ALIGN.put("CENTER",       "MiddleCenter");
        POS_TO_ALIGN.put("TOP_RIGHT",    "TopRight");
        POS_TO_ALIGN.put("TOP_LEFT",     "TopLeft");
        POS_TO_ALIGN.put("TOP_CENTER",   "TopCenter");
        POS_TO_ALIGN.put("BOTTOM_RIGHT", "BottomRight");
        POS_TO_ALIGN.put("BOTTOM_LEFT",  "BottomLeft");
        POS_TO_ALIGN.put("BOTTOM_CENTER","BottomCenter");
        for (Map.Entry<String, String> e : POS_TO_ALIGN.entrySet())
            ALIGN_TO_POS.put(e.getValue(), e.getKey());
    }

    private AppMetadata parseFxml(String source) {
        AppMetadata app = new AppMetadata();
        org.w3c.dom.Document doc;
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            try {
                factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (javax.xml.parsers.ParserConfigurationException ignored) {}
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            doc = builder.parse(new java.io.ByteArrayInputStream(
                source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            app.parseError = true;
            app.parseErrorMessage = "FXML parse error: " + e.getMessage();
            return app;
        }

        Map<String, int[]> idCounters = new HashMap<>();
        org.w3c.dom.Element root = doc.getDocumentElement();

        String rootW = root.getAttribute("prefWidth");
        String rootH = root.getAttribute("prefHeight");
        if (!rootW.isEmpty()) {
            try { app.sceneWidth  = (int) Double.parseDouble(rootW); }
            catch (NumberFormatException ignored) {}
        }
        if (!rootH.isEmpty()) {
            try { app.sceneHeight = (int) Double.parseDouble(rootH); }
            catch (NumberFormatException ignored) {}
        }

        String ctrl = root.getAttribute("fx:controller");
        if (ctrl == null || ctrl.isEmpty())
            ctrl = root.getAttributeNS("http://javafx.com/fxml", "controller");
        if (ctrl != null && !ctrl.isEmpty()) app.fxController = ctrl;

        Node rootNode = parseFxmlElement(root, app, idCounters, null);
        if (rootNode != null) app.sceneRootId = rootNode.id;
        return app;
    }

    private Node parseFxmlElement(org.w3c.dom.Element el, AppMetadata app,
                                  Map<String, int[]> idCounters, String parentBpRegion) {
        String tagName = el.getLocalName();
        if (tagName == null) tagName = el.getNodeName();
        int colon = tagName.indexOf(':');
        if (colon >= 0) tagName = tagName.substring(colon + 1);

        if (!KNOWN_TYPES.contains(tagName)) return null;

        String fxId = el.getAttributeNS("http://javafx.com/fxml", "id");
        if (fxId == null || fxId.isEmpty()) fxId = el.getAttribute("fx:id");
        if (fxId == null || fxId.isEmpty()) {
            String base = Character.toLowerCase(tagName.charAt(0)) + tagName.substring(1);
            int[] counter = idCounters.computeIfAbsent(base, k -> new int[]{0});
            counter[0]++;
            fxId = base + counter[0];
        }

        Node node = ConversionUtils.getOrMake(app, fxId);
        node.type = tagName;
        if (parentBpRegion != null) node.addProperty("borderPaneRegion", parentBpRegion);

        mapAttr(el, "text",        node, "text");
        mapAttr(el, "promptText",  node, "promptText");

        fxmlDimProp(el, "prefWidth",  node, "width");
        fxmlDimProp(el, "prefHeight", node, "height");
        // Shape-specific dimension attributes (these types don't use prefWidth/prefHeight)
        fxmlDimProp(el, "width",   node, "width");    // Rectangle
        fxmlDimProp(el, "height",  node, "height");   // Rectangle
        fxmlDimProp(el, "radiusX", node, "width");    // Ellipse
        fxmlDimProp(el, "radiusY", node, "height");   // Ellipse
        fxmlDimProp(el, "radius",  node, "radius");   // Circle
        fxmlDimProp(el, "startX",  node, "startX");   // Line
        fxmlDimProp(el, "startY",  node, "startY");   // Line
        fxmlDimProp(el, "endX",    node, "endX");     // Line
        fxmlDimProp(el, "endY",    node, "endY");     // Line
        fxmlDimProp(el, "layoutX",   node, "layoutX");
        fxmlDimProp(el, "layoutY",   node, "layoutY");
        fxmlDimProp(el, "maxWidth",  node, "maxWidth");
        fxmlDimProp(el, "maxHeight", node, "maxHeight");
        fxmlDimProp(el, "minWidth",  node, "minWidth");
        fxmlDimProp(el, "minHeight", node, "minHeight");

        fxmlDimProp(el, "hgap",    node, "hgap");
        fxmlDimProp(el, "vgap",    node, "vgap");
        String spacing = el.getAttribute("spacing");
        if (!spacing.isEmpty()) {
            if (!node.properties.containsKey("hgap")) node.addProperty("hgap", spacing);
            if (!node.properties.containsKey("vgap")) node.addProperty("vgap", spacing);
            if (!node.properties.containsKey("spacing")) node.addProperty("spacing", spacing);
        }

        fxmlDimProp(el, "min",      node, "min");
        fxmlDimProp(el, "max",      node, "max");
        fxmlDimProp(el, "value",    node, "value");
        fxmlDimProp(el, "progress", node, "progress");

        String fillAttr = el.getAttribute("fill");
        if (!fillAttr.isEmpty()) node.addProperty("fill", fillAttr);
        String strokeAttr = el.getAttribute("stroke");
        if (!strokeAttr.isEmpty()) node.addProperty("stroke", strokeAttr);
        String strokeWidthAttr = el.getAttribute("strokeWidth");
        if (!strokeWidthAttr.isEmpty()) node.addProperty("strokeWidth", strokeWidthAttr);

        String spAlign = el.getAttribute("StackPane.alignment");
        if (!spAlign.isEmpty()) node.addProperty("stackPaneAlignment", spAlign);

        String gpRow = el.getAttribute("GridPane.rowIndex");
        if (!gpRow.isEmpty()) node.addProperty("gridRow", gpRow);
        String gpCol = el.getAttribute("GridPane.columnIndex");
        if (!gpCol.isEmpty()) node.addProperty("gridCol", gpCol);
        String gpColSpan = el.getAttribute("GridPane.columnSpan");
        if (!gpColSpan.isEmpty()) node.addProperty("colSpan", gpColSpan);
        String gpRowSpan = el.getAttribute("GridPane.rowSpan");
        if (!gpRowSpan.isEmpty()) node.addProperty("rowSpan", gpRowSpan);
        String hgrowAttr = el.getAttribute("HBox.hgrow");
        if (!hgrowAttr.isEmpty()) node.addProperty("hgrow", hgrowAttr);
        String vgrowAttr = el.getAttribute("VBox.vgrow");
        if (!vgrowAttr.isEmpty()) node.addProperty("vgrow", vgrowAttr);

        String align = el.getAttribute("alignment");
        if (!align.isEmpty()) {
            String ca = POS_TO_ALIGN.get(align);
            if (ca != null) node.addProperty("textAlign", ca);
        }

        String style = el.getAttribute("style");
        if (!style.isEmpty()) JavaFxAdapter.parseFxStyle(style, node);

        String styleClassAttr = el.getAttribute("styleClass");
        if (!styleClassAttr.isEmpty()) {
            String existing = node.properties.get("styleClass");
            String newClasses = styleClassAttr.replace(",", " ").replaceAll("\\s+", " ").trim();
            node.addProperty("styleClass", existing == null ? newClasses : existing + " " + newClasses);
        }

        if ("false".equals(el.getAttribute("visible")))  node.addProperty("visible",  "false");
        if ("true".equals(el.getAttribute("disable")))   node.addProperty("enabled",  "false");
        if ("false".equals(el.getAttribute("editable"))) node.addProperty("editable", "false");
        if ("true".equals(el.getAttribute("wrapText")))  node.addProperty("wrapText", "true");
        if ("true".equals(el.getAttribute("selected")))       node.addProperty("selected",          "true");
        if ("true".equals(el.getAttribute("expanded")))       node.addProperty("expanded",          "true");
        if ("false".equals(el.getAttribute("focusTraversable"))) node.addProperty("focusTraversable",  "false");
        if ("true".equals(el.getAttribute("mouseTransparent")))  node.addProperty("mouseTransparent",  "true");
        if ("false".equals(el.getAttribute("closable")))         node.addProperty("closable",          "false");
        if ("true".equals(el.getAttribute("showTickLabels")))    node.addProperty("showTickLabels",    "true");
        if ("true".equals(el.getAttribute("showTickMarks")))     node.addProperty("showTickMarks",     "true");
        fxmlDimProp(el, "prefRowCount", node, "prefRowCount");
        String toggleGroupAttr = el.getAttribute("toggleGroup");
        if (!toggleGroupAttr.isEmpty()) {
            String tgId = toggleGroupAttr.startsWith("$") ? toggleGroupAttr.substring(1) : toggleGroupAttr;
            node.addProperty("toggleGroupId", tgId);
        }
        String rotateAttr = el.getAttribute("rotate");
        if (!rotateAttr.isEmpty()) node.addProperty("rotate", rotateAttr);
        String txAttr = el.getAttribute("translateX");
        if (!txAttr.isEmpty()) node.addProperty("translateX", txAttr);
        String tyAttr = el.getAttribute("translateY");
        if (!tyAttr.isEmpty()) node.addProperty("translateY", tyAttr);
        String opacityAttr = el.getAttribute("opacity");
        if (!opacityAttr.isEmpty()) node.addProperty("nodeOpacity", opacityAttr);

        parseEventAttr(el, "onAction",       node);
        parseEventAttr(el, "onMouseClicked", node);
        parseEventAttr(el, "onMousePressed", node);

        org.w3c.dom.NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node childDom = children.item(i);
            if (childDom.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element childEl = (org.w3c.dom.Element) childDom;

            String childTag = childEl.getLocalName();
            if (childTag == null) childTag = childEl.getNodeName();
            int c = childTag.indexOf(':');
            if (c >= 0) childTag = childTag.substring(c + 1);

            // <fx:define> - skip; ToggleGroups are referenced by toggleGroupId on radio buttons
            if ("define".equals(childTag)) continue;

            // BorderPane region wrappers: <top>, <bottom>, <left>, <right>, <center>
            if (Arrays.asList("top", "bottom", "left", "right", "center").contains(childTag)) {
                org.w3c.dom.NodeList wrapKids = childEl.getChildNodes();
                for (int j = 0; j < wrapKids.getLength(); j++) {
                    org.w3c.dom.Node wk = wrapKids.item(j);
                    if (wk.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                    Node child = parseFxmlElement(
                        (org.w3c.dom.Element) wk, app, idCounters, childTag);
                    if (child != null) ConversionUtils.addChild(app, node, child);
                }
                continue;
            }

            if ("font".equals(childTag)) {
                parseFxmlFontChild(childEl, node);
                continue;
            }

            if ("styleClass".equals(childTag)) {
                parseFxmlStyleClassChild(childEl, node);
                continue;
            }

            if ("effect".equals(childTag)) {
                parseFxmlEffectChild(childEl, node);
                continue;
            }

            if ("padding".equals(childTag)) {
                parseFxmlPaddingChild(childEl, node);
                continue;
            }

            if ("stylesheets".equals(childTag)) {
                parseFxmlStylesheetsChild(childEl, app);
                continue;
            }

            if ("valueFactory".equals(childTag)) {
                org.w3c.dom.NodeList vfKids = childEl.getChildNodes();
                for (int j = 0; j < vfKids.getLength(); j++) {
                    org.w3c.dom.Node vfn = vfKids.item(j);
                    if (vfn.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                    org.w3c.dom.Element vfEl = (org.w3c.dom.Element) vfn;
                    fxmlDimProp(vfEl, "min",          node, "min");
                    fxmlDimProp(vfEl, "max",          node, "max");
                    fxmlDimProp(vfEl, "initialValue", node, "value");
                }
                continue;
            }

            if ("items".equals(childTag)) {
                java.util.List<String> parsedItems = parseFxmlItemsElement(childEl);
                if (!parsedItems.isEmpty()) {
                    node.addProperty("items", String.join("|", parsedItems));
                } else {
                    // Fallback: treat as ordinary container wrapper (KNOWN_TYPES children)
                    org.w3c.dom.NodeList wrapKids = childEl.getChildNodes();
                    for (int j = 0; j < wrapKids.getLength(); j++) {
                        org.w3c.dom.Node wk = wrapKids.item(j);
                        if (wk.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                        Node child = parseFxmlElement(
                            (org.w3c.dom.Element) wk, app, idCounters, null);
                        if (child != null) ConversionUtils.addChild(app, node, child);
                    }
                }
                continue;
            }

            if (childTag.endsWith(".columnConstraints")) {
                // Mark that this GridPane has explicit column constraints in the source FXML
                node.addProperty("hasColumnConstraints", "true");
                continue;
            }
            if (childTag.contains(".") || isContainerWrapper(childTag)) {
                org.w3c.dom.NodeList wrapKids = childEl.getChildNodes();
                for (int j = 0; j < wrapKids.getLength(); j++) {
                    org.w3c.dom.Node wk = wrapKids.item(j);
                    if (wk.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                    Node child = parseFxmlElement(
                        (org.w3c.dom.Element) wk, app, idCounters, null);
                    if (child != null) ConversionUtils.addChild(app, node, child);
                }
                continue;
            }

            Node child = parseFxmlElement(childEl, app, idCounters, null);
            if (child != null) ConversionUtils.addChild(app, node, child);
        }
        if ("GridPane".equals(tagName) && !node.properties.containsKey("hasColumnConstraints"))
            node.addProperty("hasColumnConstraints", "false");
        return node;
    }

    private static boolean isContainerWrapper(String tag) {
        return "children".equals(tag) || "tabs".equals(tag)
            || "columns".equals(tag) || "menus".equals(tag) || "panes".equals(tag)
            || "content".equals(tag) || "root".equals(tag);
    }

    /**
     * Parses an FXML &lt;items&gt; element looking for:
     * &lt;FXCollections fx:factory="observableArrayList"&gt;&lt;String fx:value="..."/&gt;...&lt;/FXCollections&gt;
     * Returns a list of string values, or empty list if the pattern is not found.
     */
    private static java.util.List<String> parseFxmlItemsElement(org.w3c.dom.Element itemsEl) {
        java.util.List<String> result = new java.util.ArrayList<>();
        org.w3c.dom.NodeList children = itemsEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element child = (org.w3c.dom.Element) n;
            String childName = child.getLocalName();
            if (childName == null) childName = child.getNodeName();
            if ("FXCollections".equals(childName)) {
                org.w3c.dom.NodeList strings = child.getChildNodes();
                for (int j = 0; j < strings.getLength(); j++) {
                    org.w3c.dom.Node sn = strings.item(j);
                    if (sn.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                    org.w3c.dom.Element strEl = (org.w3c.dom.Element) sn;
                    String strName = strEl.getLocalName();
                    if (strName == null) strName = strEl.getNodeName();
                    if ("String".equals(strName)) {
                        String fxVal = strEl.getAttributeNS("http://javafx.com/fxml", "value");
                        if (fxVal == null || fxVal.isEmpty()) fxVal = strEl.getAttribute("fx:value");
                        if (fxVal != null && !fxVal.isEmpty()) result.add(fxVal);
                    }
                }
            } else if ("String".equals(childName)) {
                // Direct <String fx:value="..."/> without FXCollections wrapper
                String fxVal = child.getAttributeNS("http://javafx.com/fxml", "value");
                if (fxVal == null || fxVal.isEmpty()) fxVal = child.getAttribute("fx:value");
                if (fxVal != null && !fxVal.isEmpty()) result.add(fxVal);
            }
        }
        return result;
    }

    private static void parseFxmlFontChild(org.w3c.dom.Element fontEl, Node node) {
        org.w3c.dom.NodeList kids = fontEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element fontDef = (org.w3c.dom.Element) n;
            String name = fontDef.getAttribute("name");
            if (!name.isEmpty()) node.addProperty("fontFamily", name);
            String size = fontDef.getAttribute("size");
            if (!size.isEmpty()) {
                node.addProperty("fontSize", size);
                // FXML <Font size> uses CSS pixels, same as Font.font() API
                node.addProperty("fontSizeUnit", "px");
            }
        }
    }

    private static void parseFxmlStyleClassChild(org.w3c.dom.Element scEl, Node node) {
        org.w3c.dom.NodeList kids = scEl.getChildNodes();
        java.util.List<String> classes = new java.util.ArrayList<>();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element child = (org.w3c.dom.Element) n;
            String childName = child.getLocalName();
            if (childName == null) childName = child.getNodeName();
            if ("String".equals(childName)) {
                String fxVal = child.getAttributeNS("http://javafx.com/fxml", "value");
                if (fxVal == null || fxVal.isEmpty()) fxVal = child.getAttribute("fx:value");
                if (fxVal != null && !fxVal.isEmpty()) classes.add(fxVal);
            }
        }
        if (!classes.isEmpty()) {
            String existing = node.properties.get("styleClass");
            String newClasses = String.join(" ", classes);
            node.addProperty("styleClass", existing == null ? newClasses : existing + " " + newClasses);
        }
    }

    private static void parseFxmlPaddingChild(org.w3c.dom.Element padEl, Node node) {
        org.w3c.dom.NodeList kids = padEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element insets = (org.w3c.dom.Element) n;
            String top    = attrOrDefault(insets, "top",    "0");
            String right  = attrOrDefault(insets, "right",  "0");
            String bottom = attrOrDefault(insets, "bottom", "0");
            String left   = attrOrDefault(insets, "left",   "0");
            // Also accept uniform shorthand "topRightBottomLeft"
            String all = insets.getAttribute("topRightBottomLeft");
            if (!all.isEmpty()) {
                node.addProperty("padding", all);
            } else {
                node.addProperty("padding", top + " " + right + " " + bottom + " " + left);
            }
        }
    }

    private static void parseFxmlEffectChild(org.w3c.dom.Element effEl, Node node) {
        org.w3c.dom.NodeList kids = effEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element child = (org.w3c.dom.Element) n;
            String childName = child.getLocalName();
            if (childName == null) childName = child.getNodeName();
            if ("DropShadow".equals(childName)) {
                node.addProperty("effectType", "DropShadow");
                String r = child.getAttribute("radius");
                if (!r.isEmpty()) node.addProperty("effectRadius", r);
                String c = child.getAttribute("color");
                if (!c.isEmpty()) node.addProperty("effectColor", c);
                String ox = child.getAttribute("offsetX");
                if (!ox.isEmpty()) node.addProperty("effectOffsetX", ox);
                String oy = child.getAttribute("offsetY");
                if (!oy.isEmpty()) node.addProperty("effectOffsetY", oy);
                String blur = child.getAttribute("blurType");
                if (!blur.isEmpty()) node.addProperty("effectBlurType", blur);
                String spread = child.getAttribute("spread");
                if (!spread.isEmpty()) node.addProperty("effectSpread", spread);
            } else if ("GaussianBlur".equals(childName)) {
                node.addProperty("effectType", "GaussianBlur");
                String r = child.getAttribute("radius");
                if (!r.isEmpty()) node.addProperty("effectRadius", r);
            }
        }
    }

    private static void parseFxmlStylesheetsChild(org.w3c.dom.Element ssEl, AppMetadata app) {
        org.w3c.dom.NodeList kids = ssEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element url = (org.w3c.dom.Element) n;
            String val = url.getAttributeNS("http://javafx.com/fxml", "value");
            if (val == null || val.isEmpty()) val = url.getAttribute("fx:value");
            if (val == null || val.isEmpty()) val = url.getAttribute("value");
            if (val != null && !val.isEmpty()) {
                // Strip leading '@' (FXML relative path marker)
                if (val.startsWith("@")) val = val.substring(1);
                app.stylesheets.add(val);
            }
        }
    }

    private static void parseEventAttr(org.w3c.dom.Element el, String attr, Node node) {
        String val = el.getAttribute(attr);
        if (val.isEmpty()) return;
        node.addProperty("hasClick", "true");
        // Strip '#' prefix (FXML controller method reference)
        String method = val.startsWith("#") ? val.substring(1) : val;
        node.addProperty("clickMethod", method);
    }

    private static void mapAttr(org.w3c.dom.Element el, String attr, Node node, String prop) {
        String val = el.getAttribute(attr);
        if (!val.isEmpty()) node.addProperty(prop, unescapeFxmlPrefix(val));
    }

    /**
     * Reverses {@link #escapeFxmlPrefix(String)}: strips the leading backslash from values
     * that were escaped to prevent FXML treating them as expression/resource references
     * (e.g. {@code \$124,592} → {@code $124,592}).
     */
    private static String unescapeFxmlPrefix(String v) {
        if (v == null || v.length() < 2) return v;
        if (v.charAt(0) != '\\') return v;
        char c = v.charAt(1);
        if (c == '$' || c == '@' || c == '%' || c == '\\') return v.substring(1);
        return v;
    }

    private static void fxmlDimProp(org.w3c.dom.Element el, String attr,
                                    Node node, String propKey) {
        String val = el.getAttribute(attr);
        if (!val.isEmpty() && !node.properties.containsKey(propKey))
            node.addProperty(propKey, val);
    }

    private static String attrOrDefault(org.w3c.dom.Element el, String attr, String def) {
        String v = el.getAttribute(attr);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private String generateFxml(AppMetadata app, Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        Set<String> usedTypes = new TreeSet<>();
        for (Node n : app.allNodes) {
            if (n.type != null && !n.type.isEmpty()) {
                usedTypes.add(n.type);
                if (n.properties.containsKey("padding"))
                    usedTypes.add("Insets");
            }
        }

        Set<String> importLines = new TreeSet<>();
        for (String type : usedTypes) {
            String pkg = TYPE_PACKAGE.get(type);
            if (pkg != null) importLines.add(pkg + "." + type);
            else if ("Font".equals(type)) importLines.add("javafx.scene.text.Font");
            else if ("Insets".equals(type)) importLines.add("javafx.geometry.Insets");
        }

        boolean needsFXCollections = false, needsStringType = false,
                needsTreeItem = false, needsSpinnerVF = false, needsToggleGroup = false,
                needsPieChartData = false, needsTooltip = false;
        for (Node n : app.allNodes) {
            if (n.type == null) continue;
            String t = n.type;
            String items = n.properties.get("items");
            if (items != null && !items.isEmpty()
                    && ("ComboBox".equals(t) || "ChoiceBox".equals(t) || "ListView".equals(t))) {
                needsFXCollections = true; needsStringType = true;
            }
            if ("TreeView".equals(t) && n.properties.containsKey("treeRootText")) {
                needsTreeItem = true;
            }
            if ("Spinner".equals(t) && (n.properties.containsKey("min")
                    || n.properties.containsKey("max") || n.properties.containsKey("value"))) {
                needsSpinnerVF = true;
            }
            if (n.properties.containsKey("toggleGroupId")) {
                needsToggleGroup = true;
            }
            if ("PieChart".equals(t) && n.properties.containsKey("pieChartData")) {
                needsPieChartData = true;
            }
            if (n.properties.containsKey("tooltipText")) {
                needsTooltip = true;
            }
        }
        if (needsFXCollections) importLines.add("javafx.collections.FXCollections");
        if (needsStringType)    importLines.add("java.lang.String");
        if (needsTreeItem)      importLines.add("javafx.scene.control.TreeItem");
        if (needsSpinnerVF)     importLines.add("javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory");
        if (needsToggleGroup)   importLines.add("javafx.scene.control.ToggleGroup");
        if (needsPieChartData)  importLines.add("javafx.scene.chart.PieChart");
        if (needsTooltip)       importLines.add("javafx.scene.control.Tooltip");

        // XYChart subclasses always need axis imports (FXML emits <CategoryAxis>/<NumberAxis>)
        if (usedTypes.contains("LineChart") || usedTypes.contains("BarChart")
                || usedTypes.contains("AreaChart")) {
            importLines.add("javafx.scene.chart.CategoryAxis");
            importLines.add("javafx.scene.chart.NumberAxis");
        }

        for (Node n : app.allNodes) {
            if (n.properties.containsKey("effectType")) {
                importLines.add("javafx.scene.effect." + n.properties.get("effectType"));
            }
        }

        for (String imp : importLines)
            sb.append("<?import ").append(imp).append("?>\n");

        if (!app.stylesheets.isEmpty() && !importLines.contains("java.lang.String"))
            sb.append("<?import java.lang.String?>\n");

        sb.append("\n");

        Node rootNode = ConversionUtils.findById(app, app.sceneRootId);
        if (rootNode == null && !app.allNodes.isEmpty()) rootNode = app.allNodes.get(0);
        if (rootNode == null) return sb.toString();

        writeXmlNode(sb, rootNode, app, "", true);

        return sb.toString();
    }

    private void writeXmlNode(StringBuilder sb, Node node, AppMetadata app,
                               String indent, boolean isRoot) {
        if (node == null || node.type == null) return;
        String type = node.type;

        sb.append(indent).append("<").append(type);

        if (isRoot) {
            sb.append("\n").append(indent).append("   xmlns=\"http://javafx.com/javafx\"");
            sb.append("\n").append(indent).append("   xmlns:fx=\"http://javafx.com/fxml\"");
            if (app.fxController != null && !app.fxController.isEmpty())
                sb.append("\n").append(indent).append("   fx:controller=\"")
                  .append(xmlEsc(app.fxController)).append("\"");
        }

        if (node.id != null && !node.id.isEmpty())
            appendAttr(sb, "fx:id", node.id, indent);

        if (isRoot) {
            if (app.sceneWidth > 0)
                appendAttr(sb, "prefWidth", String.valueOf(app.sceneWidth), indent);
            if (app.sceneHeight > 0)
                appendAttr(sb, "prefHeight", String.valueOf(app.sceneHeight), indent);
        }

        String text = node.properties.get("text");
        if (text != null) appendAttr(sb, "text", text, indent);

        String promptText = node.properties.get("promptText");
        if (promptText != null) appendAttr(sb, "promptText", promptText, indent);

        if (!isRoot) {
            // Shape subclasses expose different dimension attributes than Region nodes.
            // Rectangle  → width / height
            // Ellipse    → radiusX / radiusY  (IR stores radii as "width"/"height")
            // Circle     → radius (emitted separately below; no width/height attr)
            // Line       → startX/startY/endX/endY (no width/height; emitted separately)
            // Polygon    → points attribute (no width/height; skip here)
            // All others → prefWidth / prefHeight (Region API)
            if ("Rectangle".equals(type)) {
                emitDimAttr(sb, node, "width",  "width",  indent);
                emitDimAttr(sb, node, "height", "height", indent);
            } else if ("Ellipse".equals(type)) {
                emitDimAttr(sb, node, "width",  "radiusX", indent);
                emitDimAttr(sb, node, "height", "radiusY", indent);
            } else if (!"Line".equals(type) && !"Polygon".equals(type) && !"Circle".equals(type)) {
                emitDimAttr(sb, node, "width",  "prefWidth",  indent);
                emitDimAttr(sb, node, "height", "prefHeight", indent);
            }
            if ("Line".equals(type)) {
                String sx = node.properties.get("startX"); if (sx != null) appendAttr(sb, "startX", sx, indent);
                String sy = node.properties.get("startY"); if (sy != null) appendAttr(sb, "startY", sy, indent);
                String ex = node.properties.get("endX");   if (ex != null) appendAttr(sb, "endX",   ex, indent);
                String ey = node.properties.get("endY");   if (ey != null) appendAttr(sb, "endY",   ey, indent);
            }
            emitDimAttr(sb, node, "maxWidth",  "maxWidth",   indent);
            emitDimAttr(sb, node, "maxHeight", "maxHeight",  indent);
            emitDimAttr(sb, node, "minWidth",  "minWidth",   indent);
            emitDimAttr(sb, node, "minHeight", "minHeight",  indent);
            emitDimAttr(sb, node, "layoutX",   "layoutX",    indent);
            emitDimAttr(sb, node, "layoutY",   "layoutY",    indent);
        }

        if ("GridPane".equals(type) || "FlowPane".equals(type) || "TilePane".equals(type)) {
            emitDimAttr(sb, node, "hgap", "hgap", indent);
            emitDimAttr(sb, node, "vgap", "vgap", indent);
        } else if ("VBox".equals(type) || "HBox".equals(type)) {
            // VBox/HBox use spacing, not hgap/vgap
            String sp = node.properties.get("hgap");
            if (sp == null) sp = node.properties.get("vgap");
            if (sp != null) appendAttr(sb, "spacing", sp, indent);
        }

        String textAlign = node.properties.get("textAlign");
        if (textAlign != null) {
            String pos = ALIGN_TO_POS.get(textAlign);
            if (pos != null) appendAttr(sb, "alignment", pos, indent);
        }

        // Spinner: min/max/value go in <valueFactory> child - do NOT emit as attributes
        if (!"Spinner".equals(type)) {
            emitDimAttr(sb, node, "min",      "min",      indent);
            emitDimAttr(sb, node, "max",      "max",      indent);
            emitDimAttr(sb, node, "value",    "value",    indent);
        }
        emitDimAttr(sb, node, "progress", "progress", indent);

        String selVal = node.properties.get("selectedValue");
        if (selVal != null && ("ComboBox".equals(type) || "ChoiceBox".equals(type)))
            appendAttr(sb, "value", selVal, indent);

        if ("false".equals(node.properties.get("visible")))         appendAttr(sb, "visible",           "false", indent);
        if ("false".equals(node.properties.get("enabled")))         appendAttr(sb, "disable",           "true",  indent);
        if ("false".equals(node.properties.get("editable")))        appendAttr(sb, "editable",          "false", indent);
        if ("true".equals(node.properties.get("wrapText")))         appendAttr(sb, "wrapText",          "true",  indent);
        if ("true".equals(node.properties.get("selected")))         appendAttr(sb, "selected",          "true",  indent);
        if ("false".equals(node.properties.get("focusTraversable"))) appendAttr(sb, "focusTraversable", "false", indent);
        if ("true".equals(node.properties.get("mouseTransparent")))  appendAttr(sb, "mouseTransparent", "true",  indent);

        String rotate = node.properties.get("rotate");
        if (rotate != null) appendAttr(sb, "rotate", rotate, indent);
        String scaleX = node.properties.get("scaleX");
        if (scaleX != null) appendAttr(sb, "scaleX", scaleX, indent);
        String scaleY = node.properties.get("scaleY");
        if (scaleY != null) appendAttr(sb, "scaleY", scaleY, indent);
        String translateX = node.properties.get("translateX");
        if (translateX != null) appendAttr(sb, "translateX", translateX, indent);
        String translateY = node.properties.get("translateY");
        if (translateY != null) appendAttr(sb, "translateY", translateY, indent);
        String nodeOpacity = node.properties.get("nodeOpacity");
        if (nodeOpacity != null) appendAttr(sb, "opacity", nodeOpacity, indent);

        String styleClass = node.properties.get("styleClass");
        if (styleClass != null && !styleClass.isEmpty()) {
            // FXML styleClass attribute uses comma-separated lists for multiple classes
            appendAttr(sb, "styleClass", styleClass.trim().replaceAll("\\s+", ", "), indent);
        }

        String orientation = node.properties.get("orientation");
        if (orientation != null) appendAttr(sb, "orientation", orientation, indent);

        String divPos = node.properties.get("dividerPosition");
        if (divPos != null && "SplitPane".equals(type)) appendAttr(sb, "dividerPositions", divPos, indent);

        String prefCols = node.properties.get("prefColumns");
        if (prefCols != null && "TilePane".equals(type)) appendAttr(sb, "prefColumns", prefCols, indent);

        if ("Pagination".equals(type)) {
            String pc = node.properties.get("pageCount");
            if (pc != null) appendAttr(sb, "pageCount", pc, indent);
            String cpi = node.properties.get("currentPageIndex");
            if (cpi != null) appendAttr(sb, "currentPageIndex", cpi, indent);
        }

        if ("true".equals(node.properties.get("hasClick"))) {
            String method = node.properties.get("clickMethod");
            if (method != null && !method.isEmpty())
                appendAttr(sb, "onAction", "#" + method, indent);
            else
                appendAttr(sb, "onAction", "#handleAction", indent);
        }

        String stackPaneAlign = node.properties.get("stackPaneAlignment");
        if (stackPaneAlign != null) appendAttr(sb, "StackPane.alignment", stackPaneAlign, indent);

        String gridRow = node.properties.get("gridRow");
        if (gridRow != null) appendAttr(sb, "GridPane.rowIndex", gridRow, indent);
        String gridCol = node.properties.get("gridCol");
        if (gridCol != null) appendAttr(sb, "GridPane.columnIndex", gridCol, indent);
        String colSpan = node.properties.get("colSpan");
        if (colSpan != null) appendAttr(sb, "GridPane.columnSpan", colSpan, indent);
        String rowSpan = node.properties.get("rowSpan");
        if (rowSpan != null) appendAttr(sb, "GridPane.rowSpan", rowSpan, indent);
        String hgrow = node.properties.get("hgrow");
        if (hgrow != null) appendAttr(sb, "HBox.hgrow", hgrow, indent);
        String vgrow = node.properties.get("vgrow");
        if (vgrow != null) appendAttr(sb, "VBox.vgrow", vgrow, indent);

        if ("false".equals(node.properties.get("closable")))
            appendAttr(sb, "closable", "false", indent);

        if ("true".equals(node.properties.get("expanded")) && "TitledPane".equals(type))
            appendAttr(sb, "expanded", "true", indent);

        // RadioButton toggle group reference (real FXML binding - keep leading $)
        String toggleGroupId = node.properties.get("toggleGroupId");
        if (toggleGroupId != null && "RadioButton".equals(type))
            appendAttrRaw(sb, "toggleGroup", "$" + toggleGroupId, indent);

        String prefRowCount = node.properties.get("prefRowCount");
        if (prefRowCount != null) appendAttr(sb, "prefRowCount", prefRowCount, indent);

        if ("true".equals(node.properties.get("showTickLabels")))
            appendAttr(sb, "showTickLabels", "true", indent);
        if ("true".equals(node.properties.get("showTickMarks")))
            appendAttr(sb, "showTickMarks", "true", indent);

        String radius = node.properties.get("radius");
        if (radius != null) appendAttr(sb, "radius", radius, indent);

        String polyPoints = node.properties.get("points");
        if (polyPoints != null && "Polygon".equals(type)) appendAttr(sb, "points", polyPoints, indent);

        String fill = node.properties.get("fill");
        if (fill != null && !fill.contains("gradient")) appendAttr(sb, "fill", fill, indent);
        String stroke = node.properties.get("stroke");
        if (stroke != null) appendAttr(sb, "stroke", stroke, indent);
        String strokeWidth = node.properties.get("strokeWidth");
        if (strokeWidth != null) appendAttr(sb, "strokeWidth", strokeWidth, indent);

        String chartTitle = node.properties.get("chartTitle");
        if (chartTitle != null && "PieChart".equals(type)) appendAttr(sb, "title", xmlEsc(chartTitle), indent);
        String legendSide = node.properties.get("legendSide");
        if (legendSide != null) appendAttr(sb, "legendSide", legendSide, indent);

        String styleAttr = buildStyleAttr(node);
        if (!styleAttr.isEmpty()) appendAttr(sb, "style", styleAttr, indent);

        String itemsProp         = node.properties.get("items");
        String treeRootText      = node.properties.get("treeRootText");
        boolean hasItems         = itemsProp != null && !itemsProp.isEmpty()
                                   && ("ComboBox".equals(type) || "ChoiceBox".equals(type) || "ListView".equals(type));
        boolean hasTreeRoot      = treeRootText != null && "TreeView".equals(type);
        boolean hasValueFactory  = "Spinner".equals(type)
                                   && (node.properties.containsKey("min")
                                    || node.properties.containsKey("max")
                                    || node.properties.containsKey("value"));
        boolean hasFontChild    = false; // Font emitted via style= attribute; <Font> child conflicts with px units
        boolean hasPaddingChild = node.properties.containsKey("padding")
                                || node.properties.containsKey("padValue");
        boolean hasStylesheets  = isRoot && !app.stylesheets.isEmpty();
        boolean hasChildren     = !node.children.isEmpty();
        boolean hasCellMargin   = node.properties.containsKey("cellMarginParent");
        boolean hasPieData      = "PieChart".equals(type) && node.properties.containsKey("pieChartData");
        boolean hasTooltip      = node.properties.containsKey("tooltipText");
        boolean hasEffect       = node.properties.containsKey("effectType");
        // XYChart subclasses have no no-arg constructor; FXML must supply <xAxis>/<yAxis> for FXMLLoader to instantiate them.
        boolean hasAxes         = "LineChart".equals(type) || "BarChart".equals(type) || "AreaChart".equals(type);

        boolean hasContent = hasFontChild || hasPaddingChild || hasStylesheets || hasChildren
                          || hasItems || hasTreeRoot || hasValueFactory || hasCellMargin
                          || hasPieData || hasTooltip || hasEffect || hasAxes;

        if (!hasContent) {
            sb.append("/>\n");
            return;
        }

        sb.append(">\n");
        String childIndent = indent + "    ";

        if (hasStylesheets) {
            sb.append(childIndent).append("<stylesheets>\n");
            for (String ss : app.stylesheets) {
                String url = ss;
                if (!ss.startsWith("@") && !ss.matches("^(data|http|https|file):.*")) {
                    url = "@" + ss;
                }
                sb.append(childIndent).append("    <String fx:value=\"").append(xmlEsc(url)).append("\"/>\n");
            }
            sb.append(childIndent).append("</stylesheets>\n");
        }

        if (hasFontChild) {
            String fam  = node.properties.get("fontFamily");
            String fsz  = node.properties.get("fontSize");
            String fszU = node.properties.get("fontSizeUnit");
            sb.append(childIndent).append("<font>\n");
            sb.append(childIndent).append("    <Font");
            if (fam != null) sb.append(" name=\"").append(xmlEsc(fam)).append("\"");
            if (fsz != null) {
                sb.append(" size=\"").append(fsz).append("\"");
                if ("px".equals(fszU)) sb.append(" <!-- px -->");
            }
            sb.append("/>\n");
            sb.append(childIndent).append("</font>\n");
        }

        if (hasPaddingChild) {
            String padVal = node.properties.get("padding");
            if (padVal == null) padVal = node.properties.get("padValue");
            sb.append(childIndent).append("<padding>\n");
            sb.append(childIndent).append("    ");
            emitInsetsElement(sb, padVal);
            sb.append("\n");
            sb.append(childIndent).append("</padding>\n");
        }

        if (hasCellMargin) {
            String marginParent = node.properties.get("cellMarginParent");
            String cm = node.properties.get("cellMargin");
            if (marginParent != null && cm != null) {
                sb.append(childIndent).append("<").append(marginParent).append(".margin>\n");
                sb.append(childIndent).append("    ");
                emitInsetsElement(sb, cm);
                sb.append("\n");
                sb.append(childIndent).append("</").append(marginParent).append(".margin>\n");
            }
        }

        if (hasItems) {
            String[] itArr = itemsProp.split("\\|");
            sb.append(childIndent).append("<items>\n");
            sb.append(childIndent).append("    <FXCollections fx:factory=\"observableArrayList\">\n");
            for (String it : itArr)
                sb.append(childIndent).append("        <String fx:value=\"").append(xmlEsc(it.trim())).append("\"/>\n");
            sb.append(childIndent).append("    </FXCollections>\n");
            sb.append(childIndent).append("</items>\n");
        }

        if (hasTreeRoot) {
            String treeChildren = node.properties.get("treeChildren");
            boolean expanded    = !"false".equals(node.properties.get("treeRootExpanded"));
            sb.append(childIndent).append("<root>\n");
            sb.append(childIndent).append("    <TreeItem value=\"").append(xmlEsc(treeRootText)).append("\"");
            if (expanded) sb.append(" expanded=\"true\"");
            if (treeChildren != null && !treeChildren.isEmpty()) {
                sb.append(">\n");
                sb.append(childIndent).append("        <children>\n");
                for (String tc : treeChildren.split("\\|"))
                    sb.append(childIndent).append("            <TreeItem value=\"")
                      .append(xmlEsc(tc.trim())).append("\"/>\n");
                sb.append(childIndent).append("        </children>\n");
                sb.append(childIndent).append("    </TreeItem>\n");
            } else {
                sb.append("/>\n");
            }
            sb.append(childIndent).append("</root>\n");
        }

        if (hasValueFactory) {
            String minVal = node.properties.getOrDefault("min",   "0");
            String maxVal = node.properties.getOrDefault("max",   "100");
            String valVal = node.properties.getOrDefault("value", "0");
            sb.append(childIndent).append("<valueFactory>\n");
            sb.append(childIndent).append("    <SpinnerValueFactory.IntegerSpinnerValueFactory")
              .append(" min=\"").append(minVal).append("\"")
              .append(" max=\"").append(maxVal).append("\"")
              .append(" initialValue=\"").append(valVal).append("\"")
              .append("/>\n");
            sb.append(childIndent).append("</valueFactory>\n");
        }

        // <fx:define> for ToggleGroups - ToggleGroup is not a Node and cannot go in <children>
        if (hasChildren) {
            java.util.Set<String> tgIds = new java.util.LinkedHashSet<>();
            for (Node child : node.children) {
                if ("RadioButton".equals(child.type)) {
                    String gid = child.properties.get("toggleGroupId");
                    if (gid != null && !gid.isEmpty()) tgIds.add(gid);
                }
            }
            if (!tgIds.isEmpty()) {
                sb.append(childIndent).append("<fx:define>\n");
                for (String gid : tgIds)
                    sb.append(childIndent).append("    <ToggleGroup fx:id=\"")
                      .append(xmlEsc(gid)).append("\"/>\n");
                sb.append(childIndent).append("</fx:define>\n");
            }
        }

        if (hasChildren) {
            writeChildrenFxml(sb, node, app, childIndent);
        }

        // XYChart subclasses (LineChart/BarChart/AreaChart) have no no-arg constructor;
        // FXML must supply <xAxis>/<yAxis> for the loader to instantiate them.
        if (hasAxes) {
            sb.append(childIndent).append("<xAxis>\n");
            sb.append(childIndent).append("    <CategoryAxis");
            String xLabel = node.properties.get("xAxisLabel");
            if (xLabel != null && !xLabel.isEmpty())
                sb.append(" label=\"").append(xmlEsc(escapeFxmlPrefix(xLabel))).append("\"");
            sb.append("/>\n");
            sb.append(childIndent).append("</xAxis>\n");
            sb.append(childIndent).append("<yAxis>\n");
            sb.append(childIndent).append("    <NumberAxis");
            String yLabel = node.properties.get("yAxisLabel");
            if (yLabel != null && !yLabel.isEmpty())
                sb.append(" label=\"").append(xmlEsc(escapeFxmlPrefix(yLabel))).append("\"");
            sb.append("/>\n");
            sb.append(childIndent).append("</yAxis>\n");
        }

        if (hasPieData) {
            String[] items = node.properties.get("pieChartData").split("\\|");
            sb.append(childIndent).append("<data>\n");
            for (String item : items) {
                String[] nv = item.split("=", 2);
                if (nv.length == 2)
                    sb.append(childIndent).append("    <PieChart.Data name=\"")
                      .append(xmlEsc(nv[0])).append("\" pieValue=\"").append(nv[1]).append("\"/>\n");
            }
            sb.append(childIndent).append("</data>\n");
        }

        if (hasTooltip) {
            sb.append(childIndent).append("<tooltip>\n");
            sb.append(childIndent).append("    <Tooltip text=\"")
              .append(xmlEsc(node.properties.get("tooltipText"))).append("\"/>\n");
            sb.append(childIndent).append("</tooltip>\n");
        }

        if (hasEffect) {
            String eType = node.properties.get("effectType");
            if ("DropShadow".equals(eType)) {
                sb.append(childIndent).append("<effect>\n");
                sb.append(childIndent).append("    <DropShadow");
                String r = node.properties.get("effectRadius");
                if (r != null) sb.append(" radius=\"").append(xmlEsc(r)).append("\"");
                String c = node.properties.get("effectColor");
                if (c != null) sb.append(" color=\"").append(xmlEsc(c)).append("\"");
                String ox = node.properties.get("effectOffsetX");
                if (ox != null) sb.append(" offsetX=\"").append(xmlEsc(ox)).append("\"");
                String oy = node.properties.get("effectOffsetY");
                if (oy != null) sb.append(" offsetY=\"").append(xmlEsc(oy)).append("\"");
                String blur = node.properties.get("effectBlurType");
                if (blur != null) sb.append(" blurType=\"").append(xmlEsc(blur)).append("\"");
                String spread = node.properties.get("effectSpread");
                if (spread != null) sb.append(" spread=\"").append(xmlEsc(spread)).append("\"");
                sb.append("/>\n");
                sb.append(childIndent).append("</effect>\n");
            } else if ("GaussianBlur".equals(eType)) {
                sb.append(childIndent).append("<effect>\n");
                sb.append(childIndent).append("    <GaussianBlur");
                String r = node.properties.get("effectRadius");
                if (r != null) sb.append(" radius=\"").append(xmlEsc(r)).append("\"");
                sb.append("/>\n");
                sb.append(childIndent).append("</effect>\n");
            }
        }

        sb.append(indent).append("</").append(type).append(">\n");
    }

    private void writeChildrenFxml(StringBuilder sb, Node parent, AppMetadata app,
                                   String indent) {
        String type = parent.type;

        if ("BorderPane".equals(type)) {
            Map<String, List<Node>> regions = new LinkedHashMap<>();
            for (String r : Arrays.asList("top", "bottom", "left", "right", "center"))
                regions.put(r, new ArrayList<>());
            List<Node> unplaced = new ArrayList<>();
            for (Node child : parent.children) {
                String region = child.properties.get("borderPaneRegion");
                if (region != null && regions.containsKey(region))
                    regions.get(region).add(child);
                else
                    unplaced.add(child);
            }
            if (!unplaced.isEmpty()) regions.get("center").addAll(unplaced);
            for (Map.Entry<String, List<Node>> entry : regions.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                sb.append(indent).append("<").append(entry.getKey()).append(">\n");
                for (Node child : entry.getValue())
                    writeXmlNode(sb, child, app, indent + "    ", false);
                sb.append(indent).append("</").append(entry.getKey()).append(">\n");
            }
            return;
        }

        if ("TabPane".equals(type)) {
            sb.append(indent).append("<tabs>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</tabs>\n");
            return;
        }

        if ("TitledPane".equals(type)) {
            sb.append(indent).append("<content>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</content>\n");
            return;
        }

        if ("ScrollPane".equals(type)) {
            sb.append(indent).append("<content>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</content>\n");
            return;
        }

        if ("SplitPane".equals(type)) {
            sb.append(indent).append("<items>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</items>\n");
            return;
        }

        if ("TableView".equals(type) || "TreeTableView".equals(type)) {
            sb.append(indent).append("<columns>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</columns>\n");
            return;
        }

        if ("MenuBar".equals(type)) {
            sb.append(indent).append("<menus>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</menus>\n");
            return;
        }

        if ("Menu".equals(type)) {
            sb.append(indent).append("<items>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</items>\n");
            return;
        }

        if ("Accordion".equals(type)) {
            sb.append(indent).append("<panes>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</panes>\n");
            return;
        }

        if ("ToolBar".equals(type)) {
            sb.append(indent).append("<items>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</items>\n");
            return;
        }

        if ("Tab".equals(type)) {
            sb.append(indent).append("<content>\n");
            for (Node child : parent.children)
                writeXmlNode(sb, child, app, indent + "    ", false);
            sb.append(indent).append("</content>\n");
            return;
        }

        // Default: <children> wrapper (VBox, HBox, GridPane, StackPane, Pane, AnchorPane, FlowPane, TilePane, etc.)
        sb.append(indent).append("<children>\n");
        for (Node child : parent.children)
            writeXmlNode(sb, child, app, indent + "    ", false);
        sb.append(indent).append("</children>\n");
    }

    private String buildStyleAttr(Node node) {
        StringBuilder style = new StringBuilder();

        append(style, "backColor",        "-fx-background-color", node, false);
        append(style, "foreColor",        "-fx-text-fill",        node, false);

        // Gradient fill on shapes (can't be expressed as a FXML fill attribute)
        String fillVal = node.properties.get("fill");
        if (fillVal != null && fillVal.contains("gradient"))
            style.append("-fx-fill: ").append(fillVal).append("; ");

        String bw = node.properties.get("borderWidth");
        String bc = node.properties.get("borderColor");
        if (bw != null) style.append("-fx-border-width: ").append(bw).append("; ");
        if (bc != null) style.append("-fx-border-color: ").append(bc).append("; ");
        if (bw == null && bc == null && "true".equals(node.properties.get("borderFixed")))
            style.append("-fx-border-color: #808080; -fx-border-width: 1; ");
        append(style, "borderStyle",      "-fx-border-style",     node, false);

        append(style, "opacity",          "-fx-opacity",          node, false);
        append(style, "backgroundRadius", "-fx-background-radius", node, false);
        append(style, "borderRadius",     "-fx-border-radius",    node, false);

        String fam = node.properties.get("fontFamily");
        String fsz = node.properties.get("fontSize");
        if (fam != null) style.append("-fx-font-family: '").append(fam).append("'; ");
        if (fsz != null) {
            // JavaFX Font API (Font.font/new Font) uses CSS pixels; only explicit "pt" CSS uses typographic points
            String unit = "pt".equals(node.properties.get("fontSizeUnit")) ? "pt" : "px";
            style.append("-fx-font-size: ").append(fsz).append(unit).append("; ");
        }
        if ("BOLD".equals(node.properties.get("fontWeight")))
            style.append("-fx-font-weight: bold; ");
        if ("ITALIC".equals(node.properties.get("fontPosture")))
            style.append("-fx-font-style: italic; ");

        String extra = node.properties.get("extraCss");
        if (extra != null && !extra.isEmpty())
            style.append(extra.endsWith(";") || extra.endsWith("; ") ? extra : extra + "; ");

        return style.toString().trim();
    }

    private static void append(StringBuilder sb, String propKey, String cssProp,
                                Node node, boolean quote) {
        String val = node.properties.get(propKey);
        if (val == null) return;
        sb.append(cssProp).append(": ");
        if (quote) sb.append("'").append(val).append("'");
        else sb.append(val);
        sb.append("; ");
    }

    private static void appendAttr(StringBuilder sb, String name, String value, String indent) {
        sb.append("\n").append(indent).append("    ").append(name)
          .append("=\"").append(xmlEsc(escapeFxmlPrefix(value))).append("\"");
    }

    /**
     * Variant of appendAttr that does NOT escape FXML prefix chars - for values that
     * are intentionally bindings/resources (e.g. {@code toggleGroup="$radioGroup"}).
     */
    private static void appendAttrRaw(StringBuilder sb, String name, String value, String indent) {
        sb.append("\n").append(indent).append("    ").append(name)
          .append("=\"").append(xmlEsc(value)).append("\"");
    }

    /**
     * Escapes leading FXML expression-prefix characters by prepending a backslash.
     * FXML treats values starting with {@code $}, {@code @}, {@code %}, or {@code \}
     * as expression, resource, resource-bundle, or escape references. A literal value
     * like {@code $124,592} would otherwise be parsed as an invalid key path.
     */
    private static String escapeFxmlPrefix(String value) {
        if (value == null || value.isEmpty()) return value;
        char c = value.charAt(0);
        if (c == '$' || c == '@' || c == '%' || c == '\\') return "\\" + value;
        return value;
    }

    private static void emitDimAttr(StringBuilder sb, Node node,
                                    String propKey, String xmlAttr, String indent) {
        String val = node.properties.get(propKey);
        if (val == null) return;
        if ("Double.MAX_VALUE".equals(val)) val = "Infinity";
        appendAttr(sb, xmlAttr, val, indent);
    }

    private static void emitInsetsElement(StringBuilder sb, String padVal) {
        if (padVal == null || padVal.isEmpty()) {
            sb.append("<Insets/>");
            return;
        }
        String[] parts = padVal.trim().split("\\s+");
        if (parts.length == 1) {
            sb.append("<Insets topRightBottomLeft=\"").append(xmlEsc(parts[0])).append("\"/>");
        } else if (parts.length == 4) {
            sb.append("<Insets top=\"").append(xmlEsc(parts[0])).append("\"")
              .append(" right=\"").append(xmlEsc(parts[1])).append("\"")
              .append(" bottom=\"").append(xmlEsc(parts[2])).append("\"")
              .append(" left=\"").append(xmlEsc(parts[3])).append("\"/>");
        } else if (parts.length == 2) {
            // T+B, L+R
            sb.append("<Insets top=\"").append(xmlEsc(parts[0])).append("\"")
              .append(" right=\"").append(xmlEsc(parts[1])).append("\"")
              .append(" bottom=\"").append(xmlEsc(parts[0])).append("\"")
              .append(" left=\"").append(xmlEsc(parts[1])).append("\"/>");
        } else {
            sb.append("<Insets topRightBottomLeft=\"").append(xmlEsc(padVal.trim())).append("\"/>");
        }
    }

    private static String xmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "&#10;")
                .replace("\r", "");
    }
}

package com.jabcodex.uiporter.adapters;

import com.jabcodex.uiporter.core.ConversionUtils;
import com.jabcodex.uiporter.core.FrameworkAdapter;
import com.jabcodex.uiporter.model.AppMetadata;
import com.jabcodex.uiporter.model.Node;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.util.*;

/**
 * Parses JavaFX source code and generates JavaFX output from any AppMetadata.
 */
public class JavaFxAdapter implements FrameworkAdapter {

    @Override
    public String getDisplayName() { return "JavaFX (Java)"; }

    @Override
    public String[] getFileExtensions() { return new String[]{".java"}; }

    @Override
    public String getSyntaxLanguage() { return "java"; }

    // ── Known canonical JavaFX types ───────────────────────────────────────────────

    private static final java.util.Set<String> KNOWN_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
        "Button", "Label", "TextField", "PasswordField", "TextArea",
        "CheckBox", "RadioButton", "ComboBox", "ChoiceBox", "ListView", "TableView",
        "Slider", "ProgressBar", "ProgressIndicator", "Spinner", "DatePicker", "ColorPicker",
        "Hyperlink", "ToggleButton", "MenuButton", "SplitMenuButton", "ButtonBar", "ImageView", "Separator",
        "MenuBar", "ToolBar", "TabPane", "Tab", "ScrollPane", "ScrollBar", "TreeView",
        "Menu", "MenuItem", "SeparatorMenuItem", "CheckMenuItem", "RadioMenuItem",
        "VBox", "HBox", "GridPane", "BorderPane", "FlowPane", "Pane",
        "StackPane", "AnchorPane", "TilePane", "SplitPane", "TitledPane", "GroupBox",
        "Accordion", "Pagination", "TreeTableView", "Canvas",
        "Region", "Circle", "Rectangle", "Line", "Ellipse", "Polygon",
        "Text", "TextFlow", "Group",
        "PieChart", "BarChart", "LineChart", "AreaChart"
    ));

    // ── Pos ↔ ContentAlignment ────────────────────────────────────────────────

    private static final java.util.Map<String, String> POS_TO_ALIGN = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, String> ALIGN_TO_POS = new java.util.LinkedHashMap<>();
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

        ALIGN_TO_POS.put("MiddleRight",  "CENTER_RIGHT");
        ALIGN_TO_POS.put("MiddleLeft",   "CENTER_LEFT");
        ALIGN_TO_POS.put("MiddleCenter", "CENTER");
        ALIGN_TO_POS.put("TopRight",     "TOP_RIGHT");
        ALIGN_TO_POS.put("TopLeft",      "TOP_LEFT");
        ALIGN_TO_POS.put("TopCenter",    "TOP_CENTER");
        ALIGN_TO_POS.put("BottomRight",  "BOTTOM_RIGHT");
        ALIGN_TO_POS.put("BottomLeft",   "BOTTOM_LEFT");
        ALIGN_TO_POS.put("BottomCenter", "BOTTOM_CENTER");
    }

    // ── Parse ──────────────────────────────────────────────────────────────────

    @Override
    public AppMetadata parse(String source) {
        if (source == null || source.isBlank()) return new AppMetadata();

        // Detect FXML (declarative XML format) and delegate to FxmlAdapter
        String trimmedSrc = source.trim();
        if (trimmedSrc.startsWith("<")) {
            return new FxmlAdapter().parse(source);
        }

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(source);
        } catch (ParseProblemException e) {
            AppMetadata err = new AppMetadata();
            err.parseError = true;
            err.parseErrorMessage = e.getProblems().get(0).getMessage();
            return err;
        }

        AppMetadata app = new AppMetadata();

        // Store the top-level class name as sourceClassName (e.g. "ConvertedApp")
        cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
            .filter(c -> c.getParentNode().isPresent()
                    && c.getParentNode().get() instanceof com.github.javaparser.ast.CompilationUnit)
            .findFirst()
            .ifPresent(c -> app.sourceClassName = c.getNameAsString());

        // ── PRE-PASS: Collect class-level String field constants ──────────────
        final Map<String, String> fieldConstants = new LinkedHashMap<>();
        cu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class).forEach(fd -> {
            for (VariableDeclarator vd : fd.getVariables()) {
                if (!vd.getInitializer().isPresent()) continue;
                String val = stringValue(vd.getInitializer().get());
                if (val != null) fieldConstants.put(vd.getNameAsString(), val);
                // Also capture Color/Paint fields (e.g. static final Color ACCENT = Color.web("#7209b7"))
                String rawFdType = fd.getElementType().asString();
                if (rawFdType.equals("Color") || rawFdType.equals("Paint")) {
                    String hex = resolveJfxColor(vd.getInitializer().get());
                    if (hex != null) fieldConstants.put(vd.getNameAsString(), hex);
                }
            }
        });

        // ── PRE-PASS: Walk all non-start helper methods ───────────────────────
        Map<String, String> methodReturnIds = new LinkedHashMap<>();
        Set<String> walkedHelpers = new LinkedHashSet<>();
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            String mName = md.getNameAsString();
            if ("start".equals(mName) || "main".equals(mName) || !md.getBody().isPresent()) continue;
            walkHelperBody(cu, app, md, mName, methodReturnIds, walkedHelpers, fieldConstants);
        }

        // ── Find start() ──────────────────────────────────────────────────────
        Optional<MethodDeclaration> startMethod = cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals("start") && m.getBody().isPresent())
            .findFirst();

        if (!startMethod.isPresent()) return app;

        BlockStmt body = startMethod.get().getBody().get();

        // ── PRE-PASS (start): build auxiliary maps for data-population ─────────
        // listVarItems    : varName → pipe-delimited item strings
        // treeItemTexts   : varName → TreeItem text
        // treeItemChildren: varName → list of children texts
        // tableColHeaders : varName → TableColumn header text
        // tableRowsMap    : varName → list of row strings (ObservableList<ObservableList<...>>)
        final Map<String, String> listVarItems0 = new java.util.HashMap<>();
        final Map<String, String> treeItemTexts0 = new java.util.HashMap<>();
        final Map<String, java.util.List<String>> treeItemChildren0 = new java.util.HashMap<>();
        final Map<String, String> tableColHeaders0 = new java.util.HashMap<>();
        final Map<String, java.util.List<String>> tableRowsMap = new java.util.HashMap<>();
        // Track ObservableList<ObservableList<...>> data variables
        final java.util.Set<String> listOfListsVars = new java.util.HashSet<>();

        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            String rawType0 = vde.getElementType().asString();
            int lt0 = rawType0.indexOf('<');
            String baseType0 = lt0 >= 0 ? rawType0.substring(0, lt0) : rawType0;
            // Strip package prefix so fully-qualified types (e.g. javafx.collections.ObservableList) match
            String simpleBase0 = baseType0.contains(".")
                ? baseType0.substring(baseType0.lastIndexOf('.') + 1) : baseType0;
            for (VariableDeclarator vd : vde.getVariables()) {
                if (!vd.getInitializer().isPresent()) continue;
                Expression init = vd.getInitializer().get();
                String varName = vd.getNameAsString();
                if ("ObservableList".equals(simpleBase0) || "List".equals(simpleBase0)) {
                    // Check for nested generics: ObservableList appears more than once in the type
                    int firstIdx = rawType0.indexOf("ObservableList");
                    boolean isListOfLists = firstIdx >= 0
                        && rawType0.indexOf("ObservableList", firstIdx + 1) >= 0;
                    if (init instanceof MethodCallExpr) {
                        MethodCallExpr mce = (MethodCallExpr) init;
                        if ("observableArrayList".equals(mce.getNameAsString())) {
                            java.util.List<String> its = new java.util.ArrayList<>();
                            for (Expression a : mce.getArguments()) {
                                String s = stringValue(a);
                                if (s != null) its.add(s);
                            }
                            if (!its.isEmpty()) listVarItems0.put(varName, String.join("|", its));
                            // Always register as list-of-lists when nested (may start empty)
                            if (isListOfLists) listOfListsVars.add(varName);
                        }
                    }
                }
                // String[] array initializer: String[] items = {"A", "B", "C"};
                if (vd.getType().asString().equals("String[]")) {
                    java.util.List<String> its = new java.util.ArrayList<>();
                    if (init instanceof ArrayInitializerExpr) {
                        for (Expression v : ((ArrayInitializerExpr) init).getValues()) {
                            String s = stringValue(v); if (s != null) its.add(s);
                        }
                    } else if (init instanceof ArrayCreationExpr) {
                        ArrayCreationExpr ace = (ArrayCreationExpr) init;
                        if (ace.getInitializer().isPresent())
                            for (Expression v : ace.getInitializer().get().getValues()) {
                                String s = stringValue(v); if (s != null) its.add(s);
                            }
                    }
                    if (!its.isEmpty()) listVarItems0.put(varName, String.join("|", its));
                }
                if ("TreeItem".equals(simpleBase0)) {
                    if (init instanceof ObjectCreationExpr) {
                        ObjectCreationExpr oce = (ObjectCreationExpr) init;
                        if (!oce.getArguments().isEmpty()) {
                            String txt = stringValue(oce.getArgument(0));
                            if (txt == null && oce.getArgument(0) instanceof ObjectCreationExpr) {
                                ObjectCreationExpr inner = (ObjectCreationExpr) oce.getArgument(0);
                                if (!inner.getArguments().isEmpty()) txt = stringValue(inner.getArgument(0));
                            }
                            if (txt != null) treeItemTexts0.put(varName, txt);
                        }
                    }
                }
                if ("TableColumn".equals(simpleBase0) || "TreeTableColumn".equals(simpleBase0)) {
                    if (init instanceof ObjectCreationExpr) {
                        ObjectCreationExpr oce = (ObjectCreationExpr) init;
                        if (!oce.getArguments().isEmpty()) {
                            String txt = stringValue(oce.getArgument(0));
                            if (txt != null) tableColHeaders0.put(varName, txt);
                        }
                    }
                }
            }
        });
        // TreeItem child hierarchy
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.findAncestor(LambdaExpr.class).isPresent()) return;
            String mcName = mc.getNameAsString();
            if (!mc.getScope().isPresent()) return;
            String scopeStr = mc.getScope().get().toString();
            if ((mcName.equals("add") || mcName.equals("addAll"))
                    && scopeStr.endsWith(".getChildren()")) {
                String parentVar = scopeStr.substring(0, scopeStr.length() - ".getChildren()".length()).trim();
                if (treeItemTexts0.containsKey(parentVar)) {
                    java.util.List<String> children =
                        treeItemChildren0.computeIfAbsent(parentVar, k -> new java.util.ArrayList<>());
                    for (Expression arg : mc.getArguments()) {
                        if (arg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr oce = (ObjectCreationExpr) arg;
                            if (!oce.getArguments().isEmpty()) {
                                String txt = stringValue(oce.getArgument(0));
                                if (txt == null && oce.getArgument(0) instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr inner = (ObjectCreationExpr) oce.getArgument(0);
                                    if (!inner.getArguments().isEmpty()) txt = stringValue(inner.getArgument(0));
                                }
                                if (txt != null) children.add(txt);
                            }
                        } else if (arg instanceof NameExpr) {
                            String childVar = ((NameExpr) arg).getNameAsString();
                            String childTxt = treeItemTexts0.get(childVar);
                            if (childTxt != null) children.add(childTxt);
                        }
                    }
                }
            }
            // ObservableList<ObservableList<...>> row collection: dataVar.add(FXCollections.observableArrayList("a","b"))
            if ("add".equals(mcName) && !scopeStr.contains(".") && listOfListsVars.contains(scopeStr)) {
                if (mc.getArguments().size() == 1) {
                    Expression arg = mc.getArgument(0);
                    if (arg instanceof MethodCallExpr) {
                        MethodCallExpr inner = (MethodCallExpr) arg;
                        if ("observableArrayList".equals(inner.getNameAsString())) {
                            java.util.List<String> cells = new java.util.ArrayList<>();
                            for (Expression cell : inner.getArguments()) {
                                String val = stringValue(cell);
                                if (val != null) cells.add(val);
                            }
                            if (!cells.isEmpty()) {
                                tableRowsMap.computeIfAbsent(scopeStr, k -> new java.util.ArrayList<>())
                                    .add(String.join(",", cells));
                            }
                        }
                    }
                }
            }
        });

        // ── PASS 1: Register all UI nodes ─────────────────────────────────────
        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            // Skip declarations inside for-loops; handled by PASS 1.5 unrolling
            if (vde.findAncestor(com.github.javaparser.ast.stmt.ForStmt.class).isPresent()) return;
            String type = vde.getElementType().asString();
            int lt = type.indexOf('<');
            if (lt >= 0) type = type.substring(0, lt);
            if (!KNOWN_TYPES.contains(type)) return;
            for (VariableDeclarator vd : vde.getVariables()) {
                Node node = ConversionUtils.getOrMake(app, vd.getNameAsString());
                if (node.type == null) node.type = type;
                if (!vd.getInitializer().isPresent()) continue;
                Expression init = vd.getInitializer().get();
                if (init instanceof ObjectCreationExpr) {
                    ObjectCreationExpr oce = (ObjectCreationExpr) init;
                    if (!oce.getArguments().isEmpty()) {
                        String txt = stringValue(oce.getArgument(0));
                        if (txt != null && !node.properties.containsKey("text"))
                            node.addProperty("text", txt);
                    }
                    if ("Spinner".equals(type) && oce.getArguments().size() == 3) {
                        helperNumProp(node, "min",   oce.getArgument(0));
                        helperNumProp(node, "max",   oce.getArgument(1));
                        helperNumProp(node, "value", oce.getArgument(2));
                    }
                    if ("Slider".equals(type) && oce.getArguments().size() == 3) {
                        helperNumProp(node, "min",   oce.getArgument(0));
                        helperNumProp(node, "max",   oce.getArgument(1));
                        helperNumProp(node, "value", oce.getArgument(2));
                    }
                    // Circle: Circle(radius) | Circle(radius, fill) |
                    //         Circle(centerX, centerY, radius) | Circle(centerX, centerY, radius, fill)
                    if ("Circle".equals(type) && !oce.getArguments().isEmpty()) {
                        int sz = oce.getArguments().size();
                        if (sz == 1 || sz == 2) helperNumProp(node, "radius", oce.getArgument(0));
                        else if (sz >= 3)       helperNumProp(node, "radius", oce.getArgument(2));
                        // Extract fill color: Circle(radius, fill) or Circle(cx, cy, radius, fill)
                        Expression fillArg = (sz == 2) ? oce.getArgument(1)
                                           : (sz >= 4) ? oce.getArgument(3) : null;
                        if (fillArg != null) {
                            String hex = resolveJfxColor(fillArg, fieldConstants);
                            if (hex != null) node.addProperty("fill", hex);
                        }
                    }
                    if ("ProgressBar".equals(type) && oce.getArguments().size() == 1)
                        helperNumProp(node, "progress", oce.getArgument(0));
                    if ("ProgressIndicator".equals(type) && oce.getArguments().size() == 1)
                        helperNumProp(node, "progress", oce.getArgument(0));
                    if ("Rectangle".equals(type) && oce.getArguments().size() >= 2) {
                        helperNumProp(node, "width",  oce.getArgument(0));
                        helperNumProp(node, "height", oce.getArgument(1));
                    }
                    // Polygon(x1, y1, x2, y2, ...): collect all numeric args as comma-separated points
                    if ("Polygon".equals(type) && !oce.getArguments().isEmpty()) {
                        StringBuilder pts = new StringBuilder();
                        for (Expression arg : oce.getArguments()) {
                            String v = arg.toString().trim();
                            if (v.endsWith("f") || v.endsWith("F")) v = v.substring(0, v.length() - 1);
                            if (v.matches("[\\-\\d.]+")) {
                                if (pts.length() > 0) pts.append(",");
                                pts.append(v);
                            }
                        }
                        if (pts.length() > 0) node.addProperty("points", pts.toString());
                    }
                    // Pagination(pageCount, currentPageIndex)
                    if ("Pagination".equals(type)) {
                        if (oce.getArguments().size() >= 1) {
                            String pc = oce.getArgument(0).toString().trim();
                            if (pc.matches("\\d+")) node.addProperty("pageCount", pc);
                        }
                        if (oce.getArguments().size() >= 2) {
                            String cpi = oce.getArgument(1).toString().trim();
                            if (cpi.matches("\\d+")) node.addProperty("currentPageIndex", cpi);
                        }
                    }
                    // PieChart(FXCollections.observableArrayList(Data...)): extract pie data
                    if ("PieChart".equals(type) && oce.getArguments().size() >= 1) {
                        Expression listArg = oce.getArgument(0);
                        if (listArg instanceof MethodCallExpr) {
                            MethodCallExpr listCall = (MethodCallExpr) listArg;
                            if ("observableArrayList".equals(listCall.getNameAsString())) {
                                StringBuilder pieData = new StringBuilder();
                                for (Expression dataArg : listCall.getArguments()) {
                                    if (dataArg instanceof ObjectCreationExpr) {
                                        ObjectCreationExpr dataOce = (ObjectCreationExpr) dataArg;
                                        if (dataOce.getArguments().size() >= 2) {
                                            String name = stringValue(dataOce.getArgument(0));
                                            String val2 = dataOce.getArgument(1).toString().trim();
                                            if (name != null && val2.matches("[\\d.]+")) {
                                                if (pieData.length() > 0) pieData.append("|");
                                                pieData.append(name).append("=").append(val2);
                                            }
                                        }
                                    }
                                }
                                if (pieData.length() > 0) node.addProperty("pieChartData", pieData.toString());
                            }
                        }
                    }
                } else if (init instanceof MethodCallExpr) {
                    MethodCallExpr mce = (MethodCallExpr) init;
                    String factoryName = mce.getNameAsString();
                    String returnedId = !mce.getScope().isPresent()
                        ? methodReturnIds.get(factoryName) : null;
                    if (returnedId != null) {
                        Node template = ConversionUtils.findById(app, returnedId);
                        if (template != null && !template.id.equals(node.id)) {
                            java.util.Map<String, String> idMap = new java.util.LinkedHashMap<>();
                            idMap.put(template.id, node.id);
                            node.properties.putAll(template.properties);
                            for (int _i = 0; _i < template.children.size(); _i++) {
                                Node childClone = deepCloneSubtreeTracked(app, template.children.get(_i),
                                    node.id + "_c" + _i, idMap);
                                ConversionUtils.addChild(app, node, childClone);
                            }
                            Optional<MethodDeclaration> factoryDecl = cu.findAll(MethodDeclaration.class).stream()
                                .filter(m -> m.getNameAsString().equals(factoryName)
                                          && m.getBody().isPresent()
                                          && m.getParameters().size() == mce.getArguments().size())
                                .findFirst();
                            if (factoryDecl.isPresent()) {
                                reapplyParamsToClone(cu, app, factoryDecl.get(), factoryName,
                                    idMap, mce.getArguments(), fieldConstants);
                            } else {
                                resolveFactoryMethod(cu, node, mce, fieldConstants);
                            }
                        }
                    } else {
                        resolveFactoryMethod(cu, node, mce);
                    }
                }
            }
        });

        // Field assignments without type prefix: display = new TextField("0")
        body.findAll(AssignExpr.class).forEach(ae -> {
            if (!(ae.getValue() instanceof ObjectCreationExpr)) return;
            if (!(ae.getTarget() instanceof NameExpr)) return;
            ObjectCreationExpr oce = (ObjectCreationExpr) ae.getValue();
            String type = ConversionUtils.simpleName(oce.getTypeAsString());
            if (!KNOWN_TYPES.contains(type)) return;
            Node node = ConversionUtils.getOrMake(app, ((NameExpr) ae.getTarget()).getNameAsString());
            if (node.type == null) node.type = type;
            if (!oce.getArguments().isEmpty()) {
                String txt = stringValue(oce.getArgument(0));
                if (txt != null && !node.properties.containsKey("text"))
                    node.addProperty("text", txt);
            }
        });

        // ── PRE-PASS: Collect ToggleGroup variable names ──────────────────────
        final java.util.Set<String> toggleGroupVars = new java.util.HashSet<>();
        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            if (vde.getElementType().asString().contains("ToggleGroup")) {
                for (VariableDeclarator vd : vde.getVariables())
                    toggleGroupVars.add(vd.getNameAsString());
            }
        });

        // ── PRE-PASS: Collect DropShadow variable properties ──────────────────
        final Map<String, Map<String, String>> dropShadowVars0 = new java.util.HashMap<>();
        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            if (vde.getElementType().asString().contains("DropShadow")) {
                for (VariableDeclarator vd : vde.getVariables())
                    dropShadowVars0.put(vd.getNameAsString(), new java.util.LinkedHashMap<>());
            }
        });
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            if (!mc.getScope().isPresent()) return;
            String ds0scope = mc.getScope().get().toString();
            if (!dropShadowVars0.containsKey(ds0scope)) return;
            Map<String, String> dp = dropShadowVars0.get(ds0scope);
            switch (mc.getNameAsString()) {
                case "setColor":  if (!mc.getArguments().isEmpty()) { String c = resolveJfxColor(mc.getArgument(0), fieldConstants); if (c != null) dp.put("color", c); } break;
                case "setRadius": if (!mc.getArguments().isEmpty()) dp.put("radius", mc.getArgument(0).toString().trim()); break;
                case "setSpread": if (!mc.getArguments().isEmpty()) dp.put("spread", mc.getArgument(0).toString().trim()); break;
                case "setOffsetX": if (!mc.getArguments().isEmpty()) dp.put("offsetX", mc.getArgument(0).toString().trim()); break;
                case "setOffsetY": if (!mc.getArguments().isEmpty()) dp.put("offsetY", mc.getArgument(0).toString().trim()); break;
                default: break;
            }
        });

        // ── PASS 1.5: Unroll for-loops ────────────────────────────────────────
        // Handles: for (int i = N; i <= M; i++) { Type var = new Type(args); var.setX(...); parent.getChildren().add(var); }
        body.findAll(com.github.javaparser.ast.stmt.ForStmt.class).forEach(forStmt -> {
            if (forStmt.findAncestor(LambdaExpr.class).isPresent()) return;
            // Extract loop variable, start and end bounds
            if (forStmt.getInitialization().size() != 1) return;
            Expression initExpr = forStmt.getInitialization().get(0);
            if (!(initExpr instanceof VariableDeclarationExpr)) return;
            VariableDeclarationExpr initVde = (VariableDeclarationExpr) initExpr;
            if (initVde.getVariables().size() != 1) return;
            VariableDeclarator initVd = initVde.getVariables().get(0);
            if (!initVd.getInitializer().isPresent()
                    || !(initVd.getInitializer().get() instanceof IntegerLiteralExpr)) return;
            String loopVar = initVd.getNameAsString();
            int startVal;
            int endVal;
            try {
                startVal = Integer.parseInt(initVd.getInitializer().get().toString().trim());
            } catch (NumberFormatException e) { return; }
            if (!forStmt.getCompare().isPresent()) return;
            Expression compare = forStmt.getCompare().get();
            if (!(compare instanceof BinaryExpr)) return;
            BinaryExpr cmp = (BinaryExpr) compare;
            if (!(cmp.getRight() instanceof IntegerLiteralExpr)) return;
            int bound;
            try { bound = Integer.parseInt(cmp.getRight().toString().trim()); }
            catch (NumberFormatException e) { return; }
            boolean inclusive = cmp.getOperator() == BinaryExpr.Operator.LESS_EQUALS;
            endVal = inclusive ? bound : bound - 1;
            if (endVal - startVal > 20 || endVal < startVal) return; // safety limit

            if (!(forStmt.getBody() instanceof com.github.javaparser.ast.stmt.BlockStmt)) return;
            com.github.javaparser.ast.stmt.BlockStmt loopBody =
                (com.github.javaparser.ast.stmt.BlockStmt) forStmt.getBody();

            for (int counter = startVal; counter <= endVal; counter++) {
                final int cv = counter;
                // Find widget VDE inside loop body (one level deep, not nested)
                for (VariableDeclarationExpr lvde : loopBody.findAll(VariableDeclarationExpr.class)) {
                    String lType = lvde.getElementType().asString();
                    int lt2 = lType.indexOf('<'); if (lt2 >= 0) lType = lType.substring(0, lt2);
                    if (!KNOWN_TYPES.contains(lType)) continue;
                    for (VariableDeclarator lvd : lvde.getVariables()) {
                        String synId = "loop_" + lvd.getNameAsString() + "_" + cv;
                        Node loopNode = ConversionUtils.getOrMake(app, synId);
                        if (loopNode.type == null) loopNode.type = lType;
                        // Extract text from constructor arg (handle string concat)
                        if (lvd.getInitializer().isPresent()
                                && lvd.getInitializer().get() instanceof ObjectCreationExpr) {
                            ObjectCreationExpr lvOce = (ObjectCreationExpr) lvd.getInitializer().get();
                            if (!lvOce.getArguments().isEmpty()) {
                                String raw = lvOce.getArgument(0).toString();
                                String txt = evalStringExprWithCounter(raw, loopVar, cv);
                                if (txt != null) loopNode.addProperty("text", txt);
                            }
                        }
                        // Apply method calls on the loop variable
                        final String varName = lvd.getNameAsString();
                        loopBody.findAll(MethodCallExpr.class).forEach(lmc -> {
                            if (!lmc.getScope().isPresent()) return;
                            if (!lmc.getScope().get().toString().equals(varName)) return;
                            String mName = lmc.getNameAsString();
                            if ("setStyle".equals(mName) && lmc.getArguments().size() >= 1) {
                                String rawStyle = lmc.getArgument(0).toString();
                                String resolved = evalStringExprWithCounter(rawStyle, loopVar, cv);
                                if (resolved != null) parseFxStyle(resolved, loopNode);
                            } else if (lmc.getArguments().size() >= 1) {
                                // Delegate to applyPropFromScopeless for simple setters
                                // but first substitute counter in arg
                                applyPropFromScopeless(loopNode, lmc);
                            }
                        });
                        // Find parent.getChildren().add(varName)
                        loopBody.findAll(MethodCallExpr.class).forEach(lmc -> {
                            if (!lmc.getScope().isPresent()) return;
                            String scopeS = lmc.getScope().get().toString();
                            if ((lmc.getNameAsString().equals("add") || lmc.getNameAsString().equals("addAll"))
                                    && (scopeS.endsWith(".getChildren()") || scopeS.endsWith("getChildren()"))) {
                                for (Expression arg : lmc.getArguments()) {
                                    if (arg instanceof NameExpr
                                            && ((NameExpr) arg).getNameAsString().equals(varName)) {
                                        String parentId = scopeS.replace(".getChildren()", "")
                                                                 .replace("getChildren()", "").trim();
                                        Node parent = ConversionUtils.findById(app, parentId);
                                        if (parent != null)
                                            ConversionUtils.addChild(app, parent, loopNode);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        });

        // ── PASS 1.5b: Unroll enhanced for-each loops ────────────────────────
        // Handles: for (String item : stringArrayVar) { Widget w = new Widget(item); parent.add(w); }
        body.findAll(com.github.javaparser.ast.stmt.ForEachStmt.class).forEach(feStmt -> {
            if (feStmt.findAncestor(LambdaExpr.class).isPresent()) return;
            VariableDeclarationExpr varDecl = feStmt.getVariable();
            if (varDecl.getVariables().size() != 1) return;
            String loopVar = varDecl.getVariables().get(0).getNameAsString();
            Expression iterable = feStmt.getIterable();
            java.util.List<String> items = new java.util.ArrayList<>();
            if (iterable instanceof NameExpr) {
                String stored = listVarItems0.get(((NameExpr) iterable).getNameAsString());
                if (stored != null) for (String s : stored.split("\\|")) items.add(s);
            } else if (iterable instanceof ArrayCreationExpr) {
                ArrayCreationExpr ace = (ArrayCreationExpr) iterable;
                if (ace.getInitializer().isPresent())
                    for (Expression v : ace.getInitializer().get().getValues()) {
                        String s = stringValue(v); if (s != null) items.add(s);
                    }
            } else if (iterable instanceof ArrayInitializerExpr) {
                for (Expression v : ((ArrayInitializerExpr) iterable).getValues()) {
                    String s = stringValue(v); if (s != null) items.add(s);
                }
            }
            if (items.isEmpty()) return;
            if (!(feStmt.getBody() instanceof com.github.javaparser.ast.stmt.BlockStmt)) return;
            com.github.javaparser.ast.stmt.BlockStmt feBody =
                (com.github.javaparser.ast.stmt.BlockStmt) feStmt.getBody();
            for (int idx = 0; idx < items.size(); idx++) {
                final String itemText = items.get(idx);
                for (VariableDeclarationExpr lvde : feBody.findAll(VariableDeclarationExpr.class)) {
                    String lType = lvde.getElementType().asString();
                    int lt2 = lType.indexOf('<'); if (lt2 >= 0) lType = lType.substring(0, lt2);
                    if (!KNOWN_TYPES.contains(lType)) continue;
                    for (VariableDeclarator lvd : lvde.getVariables()) {
                        String synId = "foreach_" + lvd.getNameAsString() + "_" + idx;
                        Node loopNode = ConversionUtils.getOrMake(app, synId);
                        if (loopNode.type == null) loopNode.type = lType;
                        if (lvd.getInitializer().isPresent()
                                && lvd.getInitializer().get() instanceof ObjectCreationExpr) {
                            ObjectCreationExpr lvOce = (ObjectCreationExpr) lvd.getInitializer().get();
                            if (!lvOce.getArguments().isEmpty()) {
                                String argStr = lvOce.getArgument(0).toString();
                                if (argStr.equals(loopVar)) loopNode.addProperty("text", itemText);
                                else {
                                    String resolved = evalStringExprWithCounter(argStr, loopVar, 0);
                                    if (resolved != null) loopNode.addProperty("text", resolved.replace("\0", itemText));
                                }
                            }
                        }
                        // Apply style/property calls on the loop variable
                        final String varName = lvd.getNameAsString();
                        feBody.findAll(MethodCallExpr.class).forEach(lmc -> {
                            if (!lmc.getScope().isPresent()) return;
                            if (!lmc.getScope().get().toString().equals(varName)) return;
                            applyPropFromScopeless(loopNode, lmc);
                        });
                        // Conditional style: if (item.equals("X")) btn.getStyleClass().add("cls")
                        feBody.findAll(com.github.javaparser.ast.stmt.IfStmt.class).forEach(ifStmt -> {
                            Expression cond = ifStmt.getCondition();
                            if (!(cond instanceof MethodCallExpr)) return;
                            MethodCallExpr condMc = (MethodCallExpr) cond;
                            if (!"equals".equals(condMc.getNameAsString())) return;
                            if (!condMc.getScope().isPresent()) return;
                            if (!condMc.getScope().get().toString().equals(loopVar)) return;
                            if (condMc.getArguments().isEmpty()) return;
                            String matchVal = stringValue(condMc.getArgument(0));
                            if (matchVal == null || !matchVal.equals(itemText)) return;
                            ifStmt.getThenStmt().findAll(MethodCallExpr.class).forEach(ifMc -> {
                                if (!ifMc.getScope().isPresent()) return;
                                if (!ifMc.getScope().get().toString().equals(varName)) return;
                                applyPropFromScopeless(loopNode, ifMc);
                            });
                        });
                        // Find parent.getChildren().add(varName)
                        feBody.findAll(MethodCallExpr.class).forEach(lmc -> {
                            if (!lmc.getScope().isPresent()) return;
                            String scopeS = lmc.getScope().get().toString();
                            if (!(lmc.getNameAsString().equals("add") || lmc.getNameAsString().equals("addAll"))) return;
                            if (!scopeS.endsWith(".getChildren()") && !scopeS.endsWith("getChildren()")) return;
                            for (Expression arg : lmc.getArguments()) {
                                if (arg instanceof NameExpr
                                        && ((NameExpr) arg).getNameAsString().equals(varName)) {
                                    String parentId = scopeS.replace(".getChildren()", "")
                                                             .replace("getChildren()", "").trim();
                                    Node parent = ConversionUtils.findById(app, parentId);
                                    if (parent != null) ConversionUtils.addChild(app, parent, loopNode);
                                }
                            }
                        });
                    }
                }
            }
        });

        // ── PASS 1.5c: Chart axis labels ──────────────────────────────────────
        // Track CategoryAxis/NumberAxis vars and their setLabel() values, then
        // bind xAxisLabel / yAxisLabel onto the chart node that owns them.
        {
            final Map<String, String> axisLabels = new java.util.HashMap<>();
            final Map<String, String[]> chartAxisVars = new java.util.HashMap<>(); // chartVar → [xVar, yVar]
            body.findAll(MethodCallExpr.class).forEach(mc -> {
                if (!mc.getScope().isPresent()) return;
                if (!"setLabel".equals(mc.getNameAsString())) return;
                if (mc.getArguments().isEmpty()) return;
                String label = stringValue(mc.getArgument(0), fieldConstants);
                if (label != null) axisLabels.put(mc.getScope().get().toString(), label);
            });
            body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
                String t = vde.getElementType().asString(); int lt = t.indexOf('<'); if (lt >= 0) t = t.substring(0, lt);
                if (!java.util.Arrays.asList("LineChart","BarChart","AreaChart","ScatterChart","BubbleChart").contains(t)) return;
                for (VariableDeclarator vd : vde.getVariables()) {
                    if (!vd.getInitializer().isPresent()) continue;
                    Expression init = vd.getInitializer().get();
                    if (!(init instanceof ObjectCreationExpr)) continue;
                    ObjectCreationExpr oce = (ObjectCreationExpr) init;
                    if (oce.getArguments().size() >= 2
                            && oce.getArgument(0) instanceof NameExpr
                            && oce.getArgument(1) instanceof NameExpr) {
                        chartAxisVars.put(vd.getNameAsString(), new String[]{
                            ((NameExpr) oce.getArgument(0)).getNameAsString(),
                            ((NameExpr) oce.getArgument(1)).getNameAsString()
                        });
                    }
                }
            });
            chartAxisVars.forEach((chartVar, axes) -> {
                Node chartNode = ConversionUtils.findById(app, chartVar);
                if (chartNode == null) return;
                String xLabel = axisLabels.get(axes[0]);
                String yLabel = axisLabels.get(axes[1]);
                if (xLabel != null) chartNode.addProperty("xAxisLabel", xLabel);
                if (yLabel != null) chartNode.addProperty("yAxisLabel", yLabel);
            });
        }

        // ── PASS 2: Properties ────────────────────────────────────────────────
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.findAncestor(LambdaExpr.class).isPresent()) return;
            if (!mc.getScope().isPresent()) return;
            String scope  = mc.getScope().get().toString();
            String method = mc.getNameAsString();

            // Handle styleClass / stylesheets
            if (("add".equals(method) || "addAll".equals(method) || "setAll".equals(method))) {
                if (scope.endsWith(".getStyleClass()") || scope.endsWith("getStyleClass()")) {
                    String parentVar = scope.replace(".getStyleClass()", "").replace("getStyleClass()", "").trim();
                    Node targetNode = ConversionUtils.findById(app, parentVar);
                    if (targetNode != null) {
                        for (Expression arg : mc.getArguments()) {
                            String clazz = stringValue(arg, fieldConstants);
                            if (clazz != null) {
                                String existing = targetNode.properties.get("styleClass");
                                targetNode.properties.put("styleClass", existing == null ? clazz : existing + " " + clazz);
                            }
                        }
                    }
                    return;
                } else if (scope.endsWith(".getStylesheets()") || scope.endsWith("getStylesheets()")) {
                    for (Expression arg : mc.getArguments()) {
                        String ss = stringValue(arg, fieldConstants);
                        if (ss != null) app.stylesheets.add(ss);
                    }
                    return;
                }
            }
            
            Node n = ConversionUtils.findById(app, scope);

            // Static layout grow constraints: HBox.setHgrow(child, Priority.X) / VBox.setVgrow(...)
            if (("HBox".equals(scope) && "setHgrow".equals(method))
                    || ("VBox".equals(scope) && "setVgrow".equals(method))) {
                if (mc.getArguments().size() >= 2) {
                    String childVar = mc.getArgument(0).toString().trim();
                    Node child = ConversionUtils.findById(app, childVar);
                    if (child != null) {
                        String prio = mc.getArgument(1).toString().trim();
                        prio = prio.contains(".") ? prio.substring(prio.lastIndexOf('.') + 1) : prio;
                        child.addProperty("HBox".equals(scope) ? "hgrow" : "vgrow", prio);
                    }
                }
                return;
            }

            // Static StackPane alignment: StackPane.setAlignment(child, Pos.X)
            if ("StackPane".equals(scope) && "setAlignment".equals(method)
                    && mc.getArguments().size() >= 2) {
                String childVar = mc.getArgument(0).toString().trim();
                Node child = ConversionUtils.findById(app, childVar);
                if (child != null) {
                    String arg = mc.getArgument(1).toString().trim();
                    String posKey = arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg;
                    child.addProperty("stackPaneAlignment", posKey);
                }
                return;
            }

            switch (method) {
                case "setText": {
                    if (mc.getArguments().size() >= 1) {
                        String val = stringValue(mc.getArgument(0), fieldConstants);
                        if (n != null && val != null) n.addProperty("text", val);
                    }
                    break;
                }
                case "setOnAction": {
                    if (n != null) n.addProperty("hasClick", "true");
                    break;
                }
                case "setStyle": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = stringValue(mc.getArgument(0), fieldConstants);
                        if (val != null) parseFxStyle(val, n);
                    }
                    break;
                }
                case "setPrefSize": {
                    if (n != null && mc.getArguments().size() >= 2) {
                        n.addProperty("width",  mc.getArgument(0).toString());
                        n.addProperty("height", mc.getArgument(1).toString());
                    }
                    break;
                }
                case "setPrefWidth": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("width", mc.getArgument(0).toString());
                    break;
                }
                case "setPrefHeight": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("height", mc.getArgument(0).toString());
                    break;
                }
                case "setFont": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression fontArg = mc.getArgument(0);
                        if (fontArg instanceof MethodCallExpr) {
                            MethodCallExpr fontCall = (MethodCallExpr) fontArg;
                            if ("font".equals(fontCall.getNameAsString())
                                    && !fontCall.getArguments().isEmpty()) {
                                String fam = stringValue(fontCall.getArgument(0), fieldConstants);
                                if (fam != null) n.addProperty("fontFamily", fam);
                                for (int i = 0; i < fontCall.getArguments().size(); i++) {
                                    String a = fontCall.getArgument(i).toString().trim();
                                    if (a.matches("[\\d.]+")) {
                                        n.addProperty("fontSize", a);
                                    } else if (a.toUpperCase().contains("BOLD")) {
                                        n.addProperty("fontWeight", "BOLD");
                                    } else if (a.toUpperCase().contains("ITALIC")) {
                                        n.addProperty("fontPosture", "ITALIC");
                                    }
                                }
                                n.addProperty("fontSizeUnit", "px"); // Font.font() API uses CSS pixels
                            }
                        } else if (fontArg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr fc = (ObjectCreationExpr) fontArg;
                            if (fc.getArguments().size() >= 2) {
                                String fam = stringValue(fc.getArgument(0), fieldConstants);
                                if (fam != null) n.addProperty("fontFamily", fam);
                                String sz = fc.getArgument(1).toString().trim();
                                if (sz.matches("[\\d.]+")) n.addProperty("fontSize", sz);
                                n.addProperty("fontSizeUnit", "px"); // new Font() uses CSS pixels
                            }
                        }
                    }
                    break;
                }
                case "setTextFill": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String colorHex = resolveJfxColor(mc.getArgument(0), fieldConstants);
                        if (colorHex != null) n.addProperty("foreColor", colorHex);
                    }
                    break;
                }
                case "setTextAlignment": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String arg = mc.getArgument(0).toString();
                        String key = arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg;
                        String align;
                        switch (key.toUpperCase()) {
                            case "CENTER": case "JUSTIFY": align = "MiddleCenter"; break;
                            case "RIGHT":  align = "MiddleRight"; break;
                            default:       align = "MiddleLeft"; break;
                        }
                        n.addProperty("textAlign", align);
                    }
                    break;
                }
                case "setColumnIndex": {
                    if (mc.getArguments().size() >= 2 && mc.getArgument(0) instanceof NameExpr) {
                        Node child = ConversionUtils.findById(app,
                            ((NameExpr) mc.getArgument(0)).getNameAsString());
                        if (child != null) child.addProperty("gridCol",
                            mc.getArgument(1).toString().trim());
                    }
                    break;
                }
                case "setRowIndex": {
                    if (mc.getArguments().size() >= 2 && mc.getArgument(0) instanceof NameExpr) {
                        Node child = ConversionUtils.findById(app,
                            ((NameExpr) mc.getArgument(0)).getNameAsString());
                        if (child != null) child.addProperty("gridRow",
                            mc.getArgument(1).toString().trim());
                    }
                    break;
                }
                case "setConstraints": {
                    if (mc.getArguments().size() >= 3 && mc.getArgument(0) instanceof NameExpr) {
                        Node child = ConversionUtils.findById(app,
                            ((NameExpr) mc.getArgument(0)).getNameAsString());
                        if (child != null) {
                            child.addProperty("gridCol", mc.getArgument(1).toString().trim());
                            child.addProperty("gridRow", mc.getArgument(2).toString().trim());
                        }
                    }
                    break;
                }
                case "setMinSize": {
                    if (n != null && mc.getArguments().size() >= 2) {
                        n.addProperty("minWidth",  mc.getArgument(0).toString().trim());
                        n.addProperty("minHeight", mc.getArgument(1).toString().trim());
                    }
                    break;
                }
                case "setMaxSize": {
                    if (n != null && mc.getArguments().size() >= 2) {
                        n.addProperty("maxWidth",  mc.getArgument(0).toString().trim());
                        n.addProperty("maxHeight", mc.getArgument(1).toString().trim());
                    }
                    break;
                }
                case "setMinWidth": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("minWidth", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setMaxWidth": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("maxWidth", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setMinHeight": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("minHeight", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setMaxHeight": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("maxHeight", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setWidth": {
                    // Rectangle.setWidth(n) — stores as "width" (emitted as width="n" in FXML)
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("width", val);
                    }
                    break;
                }
                case "setHeight": {
                    // Rectangle.setHeight(n) — stores as "height" (emitted as height="n" in FXML)
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("height", val);
                    }
                    break;
                }
                case "setRadius": {
                    // Circle.setRadius(n) — stores as "radius" (emitted as radius="n" in FXML)
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("radius", val);
                    }
                    break;
                }
                case "setAlignment": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String arg = mc.getArgument(0).toString();
                        String posKey = arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg;
                        String align = POS_TO_ALIGN.getOrDefault(posKey, null);
                        if (align != null) n.addProperty("textAlign", align);
                    }
                    break;
                }
                case "setTitle": {
                    if (mc.getArguments().size() >= 1) {
                        String val = stringValue(mc.getArgument(0), fieldConstants);
                        if (val != null) {
                            if (n != null) n.addProperty("chartTitle", val); // chart title
                            else app.title = val;  // stage title
                        }
                    }
                    break;
                }
                case "setLegendSide": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String arg = mc.getArgument(0).toString();
                        n.addProperty("legendSide",
                            arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg);
                    }
                    break;
                }
                case "setTooltip": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression tipArg = mc.getArgument(0);
                        if (tipArg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr tipOce = (ObjectCreationExpr) tipArg;
                            if (tipOce.getArguments().size() >= 1) {
                                String tip = stringValue(tipOce.getArgument(0), fieldConstants);
                                if (tip != null) n.addProperty("tooltipText", tip);
                            }
                        } else if (tipArg instanceof NameExpr) {
                            // tooltip variable - look up text from pre-pass (best effort)
                            String tipId = ((NameExpr) tipArg).getNameAsString();
                            Node tipNode = ConversionUtils.findById(app, tipId);
                            if (tipNode != null) {
                                String tip = tipNode.properties.get("text");
                                if (tip != null) n.addProperty("tooltipText", tip);
                            }
                        }
                    }
                    break;
                }
                case "setEditable": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString();
                        if ("false".equals(val)) n.addProperty("editable", "false");
                    }
                    break;
                }
                case "setSpacing": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString();
                        if (val.matches("[\\d.]+")) {
                            n.addProperty("hgap", val);
                            n.addProperty("vgap", val);
                        }
                    }
                    break;
                }
                case "setWrapText": {
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString()))
                        n.addProperty("wrapText", "true");
                    break;
                }
                case "setVisible": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString();
                        if ("false".equals(val)) n.addProperty("visible", "false");
                    }
                    break;
                }
                case "setDisable": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString();
                        if ("true".equals(val)) n.addProperty("enabled", "false");
                    }
                    break;
                }
                case "setResizable": {
                    if (mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString();
                        if ("false".equals(val)) app.resizable = false;
                    }
                    break;
                }
                case "setPadding": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression arg = mc.getArgument(0);
                        if (arg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr insets = (ObjectCreationExpr) arg;
                            int argc = insets.getArguments().size();
                            if (argc == 1) {
                                String v = insets.getArgument(0).toString();
                                if (v.matches("[\\d.]+")) n.addProperty("padding", v);
                            } else if (argc == 4) {
                                String t = insets.getArgument(0).toString().trim();
                                String r = insets.getArgument(1).toString().trim();
                                String b2 = insets.getArgument(2).toString().trim();
                                String l = insets.getArgument(3).toString().trim();
                                n.addProperty("padding", t + " " + r + " " + b2 + " " + l);
                            }
                        }
                    }
                    break;
                }
                case "setHgap": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString();
                        if (val.matches("[\\d.]+")) n.addProperty("hgap", val);
                    }
                    break;
                }
                case "setVgap": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString();
                        if (val.matches("[\\d.]+")) n.addProperty("vgap", val);
                    }
                    break;
                }
                case "setColumnSpan": {
                    // Static call: GridPane.setColumnSpan(child, spanCount)
                    if (mc.getArguments().size() >= 2 && mc.getArgument(0) instanceof NameExpr) {
                        Node target = ConversionUtils.findById(app,
                            ((NameExpr) mc.getArgument(0)).getNameAsString());
                        if (target != null)
                            target.addProperty("colSpan", mc.getArgument(1).toString().trim());
                    }
                    break;
                }
                case "setSelected": {
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString()))
                        n.addProperty("selected", "true");
                    break;
                }
                case "setIndeterminate": {
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString()))
                        n.addProperty("indeterminate", "true");
                    break;
                }
                case "setMin": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("min", val);
                    }
                    break;
                }
                case "setMax": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("max", val);
                    }
                    break;
                }
                case "setValue": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression valExpr = mc.getArgument(0);
                        String numStr = valExpr.toString().trim();
                        if (numStr.matches("[\\d.]+")) {
                            // Numeric value (Spinner, Slider, ScrollBar, etc.)
                            n.addProperty("value", numStr);
                        } else {
                            // String value - capture selected item for ComboBox / ChoiceBox
                            String strVal = stringValue(valExpr);
                            if (strVal != null) n.addProperty("selectedValue", strVal);
                        }
                    }
                    break;
                }
                case "setProgress": {
                    // JavaFX ProgressBar uses 0.0–1.0
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("progress", val);
                    }
                    break;
                }
                case "setRoot": {
                    // treeView.setRoot(rootVar) - resolve TreeItem root from PRE-PASS maps
                    if (n != null && mc.getArguments().size() >= 1
                            && mc.getArgument(0) instanceof NameExpr) {
                        String rootVar = ((NameExpr) mc.getArgument(0)).getNameAsString();
                        String rootText = treeItemTexts0.get(rootVar);
                        if (rootText != null) {
                            n.addProperty("treeRootText", rootText);
                            n.addProperty("treeRootExpanded", "true");
                            java.util.List<String> kids = treeItemChildren0.get(rootVar);
                            if (kids != null && !kids.isEmpty())
                                n.addProperty("treeChildren", String.join("|", kids));
                        }
                    }
                    break;
                }
                case "setItems": {
                    // listView.setItems / tableView.setItems
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression arg0 = mc.getArgument(0);
                        if (arg0 instanceof NameExpr) {
                            String varName = ((NameExpr) arg0).getNameAsString();
                            // Check if it's a list-of-lists TableView data variable
                            java.util.List<String> rows = tableRowsMap.get(varName);
                            if (rows != null && !rows.isEmpty()) {
                                n.addProperty("tableRows", String.join("|", rows));
                            } else {
                                // Regular list variable → items property
                                String items = listVarItems0.get(varName);
                                if (items != null) n.addProperty("items", items);
                            }
                        } else {
                            java.util.List<String> its = extractListItems(arg0, listVarItems0);
                            if (!its.isEmpty()) n.addProperty("items", String.join("|", its));
                        }
                    }
                    break;
                }
                case "setEffect": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression effArg = mc.getArgument(0);
                        if (effArg instanceof NameExpr) {
                            String effVar = ((NameExpr) effArg).getNameAsString();
                            Map<String, String> dp = dropShadowVars0.get(effVar);
                            if (dp != null) {
                                String color  = dp.getOrDefault("color",   "rgba(0,0,0,0.5)");
                                String radius = dp.getOrDefault("radius",  "10");
                                String spread = dp.getOrDefault("spread",  "0");
                                String ox     = dp.getOrDefault("offsetX", "0");
                                String oy     = dp.getOrDefault("offsetY", "0");
                                String effCss = "-fx-effect: dropshadow(gaussian, " + color + ", "
                                               + radius + ", " + spread + ", " + ox + ", " + oy + ");";
                                String existing = n.properties.get("extraCss");
                                n.properties.put("extraCss", existing == null ? effCss : existing + " " + effCss);
                            }
                        } else if (effArg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr oce = (ObjectCreationExpr) effArg;
                            if ("GaussianBlur".equals(oce.getTypeAsString())) {
                                n.addProperty("effectType", "GaussianBlur");
                                if (!oce.getArguments().isEmpty())
                                    n.addProperty("effectRadius", oce.getArgument(0).toString().trim());
                            }
                        }
                    }
                    break;
                }
                case "setRotate": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("rotate", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setTranslateX": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("translateX", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setTranslateY": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("translateY", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setOpacity": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("nodeOpacity", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setFocusTraversable": {
                    if (n != null && mc.getArguments().size() >= 1
                            && "false".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("focusTraversable", "false");
                    break;
                }
                case "setMouseTransparent": {
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("mouseTransparent", "true");
                    break;
                }
                case "setClosable": {
                    if (n != null && mc.getArguments().size() >= 1
                            && "false".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("closable", "false");
                    break;
                }
                case "setPromptText": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String pv = stringValue(mc.getArgument(0));
                        if (pv != null) n.addProperty("promptText", pv);
                    }
                    break;
                }
                case "setPrefRowCount": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String rv = mc.getArgument(0).toString().trim();
                        if (rv.matches("\\d+")) n.addProperty("prefRowCount", rv);
                    }
                    break;
                }
                case "setShowTickLabels": {
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("showTickLabels", "true");
                    break;
                }
                case "setShowTickMarks": {
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("showTickMarks", "true");
                    break;
                }
                case "setToggleGroup": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String groupVar = mc.getArgument(0).toString().trim();
                        if (toggleGroupVars.contains(groupVar))
                            n.addProperty("toggleGroupId", groupVar);
                    }
                    break;
                }
                case "setFill": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String c = resolveJfxColor(mc.getArgument(0), fieldConstants);
                        if (c != null) n.addProperty("fill", c);
                    }
                    break;
                }
                case "setStroke": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String c = resolveJfxColor(mc.getArgument(0), fieldConstants);
                        if (c != null) n.addProperty("stroke", c);
                    }
                    break;
                }
                case "setStrokeWidth": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("strokeWidth", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setRadiusX": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("width", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setRadiusY": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("height", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setStartX": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("startX", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setStartY": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("startY", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setEndX": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("endX", mc.getArgument(0).toString().trim());
                    break;
                }
                case "setEndY": {
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("endY", mc.getArgument(0).toString().trim());
                    break;
                }
                // ── Additional properties missing from main PASS 2 ────────────
                case "setOrientation": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String arg = mc.getArgument(0).toString();
                        n.addProperty("orientation",
                            arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg);
                    }
                    break;
                }
                case "setDividerPositions": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String posStr = mc.getArgument(0).toString().trim();
                        try { Double.parseDouble(posStr); n.addProperty("dividerPosition", posStr); }
                        catch (NumberFormatException ignored) {}
                    }
                    break;
                }
                case "setPrefColumns": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("\\d+")) n.addProperty("prefColumns", val);
                    }
                    break;
                }
                case "setPageCount": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("\\d+")) n.addProperty("pageCount", val);
                    }
                    break;
                }
                case "setCurrentPageIndex": {
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("\\d+")) n.addProperty("currentPageIndex", val);
                    }
                    break;
                }
                case "setExpandedPane": {
                    // accordion.setExpandedPane(pane) → store ref to mark expanded later
                    if (n != null && mc.getArguments().size() >= 1
                            && mc.getArgument(0) instanceof NameExpr) {
                        String paneId = ((NameExpr) mc.getArgument(0)).getNameAsString();
                        n.addProperty("expandedPaneId", paneId);
                    }
                    break;
                }
                default:
                    break;
            }
        });

        // Track per-factory-method call counts to clone helper nodes when called multiple times
        final Map<String, Integer> helperCallCount = new LinkedHashMap<>();

        // ── PASS 3: Hierarchy ─────────────────────────────────────────────────
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            String method = mc.getNameAsString();

            if ((method.equals("add") || method.equals("addAll"))
                    && mc.getScope().isPresent()) {
                String scopeStr = mc.getScope().get().toString();

                // getChildren().add / addAll
                if (scopeStr.endsWith(".getChildren()") || scopeStr.endsWith("getChildren()")) {
                    String parentId = scopeStr
                        .replace(".getChildren()", "")
                        .replace("getChildren()", "").trim();
                    for (Expression arg : mc.getArguments()) {
                        if (arg instanceof NameExpr) {
                            Node parent = ConversionUtils.findById(app, parentId);
                            Node child  = ConversionUtils.findById(app,
                                ((NameExpr) arg).getNameAsString());
                            if (parent != null && child != null)
                                ConversionUtils.addChild(app, parent, child);
                        } else if (arg instanceof MethodCallExpr) {
                            MethodCallExpr factoryCall = (MethodCallExpr) arg;
                            // Check methodReturnIds first
                            String rid = !factoryCall.getScope().isPresent()
                                ? methodReturnIds.get(factoryCall.getNameAsString()) : null;
                            Node parent = ConversionUtils.findById(app, parentId);
                            if (rid != null && parent != null) {
                                Node template = ConversionUtils.findById(app, rid);
                                if (template != null) {
                                    String fName = factoryCall.getNameAsString();
                                    int callIdx = helperCallCount.merge(fName, 1, Integer::sum) - 1;
                                    Node child;
                                    Map<String, String> idMap;
                                    if (callIdx == 0) {
                                        child = template; // first call: use pre-walked template
                                        idMap = buildIdentityIdMap(app, template);
                                    } else {
                                        // subsequent calls: deep-clone the subtree with unique IDs
                                        Map.Entry<Node, Map<String, String>> cloneResult =
                                            deepCloneSubtreeWithMap(app, template, rid + "_" + callIdx);
                                        child = cloneResult.getKey();
                                        idMap = cloneResult.getValue();
                                    }
                                    // apply call-site arguments (text, style, etc.) for this invocation
                                    Optional<MethodDeclaration> factoryDecl = cu.findAll(MethodDeclaration.class).stream()
                                        .filter(m -> m.getNameAsString().equals(fName)
                                                  && m.getBody().isPresent()
                                                  && m.getParameters().size() == factoryCall.getArguments().size())
                                        .findFirst();
                                    if (factoryDecl.isPresent()) {
                                        reapplyParamsToClone(cu, app, factoryDecl.get(), fName,
                                            idMap, factoryCall.getArguments(), fieldConstants);
                                    } else {
                                        resolveFactoryMethod(cu, child, factoryCall, fieldConstants);
                                    }
                                    ConversionUtils.addChild(app, parent, child);
                                }
                            } else {
                                String nodeType = resolveFactoryReturnType(cu,
                                    factoryCall.getNameAsString(), factoryCall.getArguments().size());
                                if (nodeType != null && KNOWN_TYPES.contains(nodeType) && parent != null) {
                                    String synthId = parentId + "_child" + parent.children.size();
                                    Node child = ConversionUtils.getOrMake(app, synthId);
                                    if (child.type == null) child.type = nodeType;
                                    resolveFactoryMethod(cu, child, factoryCall, fieldConstants);
                                    ConversionUtils.addChild(app, parent, child);
                                }
                            }
                        } else if (arg instanceof ObjectCreationExpr) {
                            // Inline new VBox(...) / HBox(...) / Label(...) etc.
                            Node parent = ConversionUtils.findById(app, parentId);
                            if (parent != null) {
                                String synId = parentId + "_ich" + parent.children.size();
                                Node child = resolveExprToNodeNs(app, (ObjectCreationExpr) arg, "", synId);
                                if (child != null) ConversionUtils.addChild(app, parent, child);
                            }
                        }
                    }
                }

                // getTabs() / getItems() / getPanes() / getButtons() / getMenus().add / addAll
                if (scopeStr.matches(".*\\.(getTabs|getItems|getPanes|getButtons|getMenus)\\(\\)$")) {
                    String parentId = scopeStr.replaceAll("\\.(getTabs|getItems|getPanes|getButtons|getMenus)\\(\\)$", "");
                    Node parent = ConversionUtils.findById(app, parentId);
                    if (parent != null) {
                        // Special case: ListView/ComboBox/ChoiceBox getItems().addAll(strings)
                        // → capture as items property rather than as child nodes
                        boolean isDataList = "ListView".equals(parent.type)
                                || "ComboBox".equals(parent.type)
                                || "ChoiceBox".equals(parent.type);
                        if (isDataList && scopeStr.endsWith(".getItems()")) {
                            java.util.List<String> its = new java.util.ArrayList<>();
                            for (Expression arg : mc.getArguments()) {
                                String s = stringValue(arg);
                                if (s != null) its.add(s);
                            }
                            if (!its.isEmpty() && !parent.properties.containsKey("items"))
                                parent.addProperty("items", String.join("|", its));
                            // still fall through so named-node args get added as children
                        }
                        for (Expression arg : mc.getArguments()) {
                            Node child = resolveExprToNode(app, arg, "", methodReturnIds,
                                parent.id + "_tab" + parent.children.size());
                            if (child != null) ConversionUtils.addChild(app, parent, child);
                        }
                    }
                }

                if (method.equals("add") && mc.getArguments().size() >= 3
                        && mc.getScope().isPresent()) {
                    Expression firstArg  = mc.getArgument(0);
                    Expression secondArg = mc.getArgument(1);
                    Expression thirdArg  = mc.getArgument(2);
                    if (firstArg instanceof NameExpr
                            && secondArg instanceof IntegerLiteralExpr
                            && thirdArg instanceof IntegerLiteralExpr) {
                        String parentId = mc.getScope().get().toString();
                        Node parent = ConversionUtils.findById(app, parentId);
                        Node child  = ConversionUtils.findById(app,
                            ((NameExpr) firstArg).getNameAsString());
                        if (parent != null && child != null) {
                            ConversionUtils.addChild(app, parent, child);
                            child.addProperty("gridCol", secondArg.toString());
                            child.addProperty("gridRow", thirdArg.toString());
                        }
                    } else if (firstArg instanceof MethodCallExpr
                            && secondArg instanceof IntegerLiteralExpr
                            && thirdArg instanceof IntegerLiteralExpr) {
                        MethodCallExpr factoryCall = (MethodCallExpr) firstArg;
                        String parentId = mc.getScope().get().toString();
                        Node parent = ConversionUtils.findById(app, parentId);
                        if (parent != null) {
                            String col = secondArg.toString();
                            String row = thirdArg.toString();
                            String synthId = parent.id + "_c" + col + "_r" + row;
                            String nodeType = resolveFactoryReturnType(cu,
                                factoryCall.getNameAsString(), factoryCall.getArguments().size());
                            if (nodeType != null && KNOWN_TYPES.contains(nodeType)) {
                                Node child = ConversionUtils.getOrMake(app, synthId);
                                if (child.type == null) child.type = nodeType;
                                child.addProperty("gridCol", col);
                                child.addProperty("gridRow", row);
                                resolveFactoryMethod(cu, child, factoryCall, fieldConstants);
                                ConversionUtils.addChild(app, parent, child);
                            }
                        }
                    }
                }
            }

            // BorderPane setTop / setCenter / setLeft / setRight / setBottom
            if (mc.getScope().isPresent() && mc.getArguments().size() == 1
                    && java.util.Arrays.asList("setTop","setBottom","setLeft",
                                               "setRight","setCenter").contains(method)) {
                Node parent = ConversionUtils.findById(app, mc.getScope().get().toString());
                if (parent != null) {
                    Node child = resolveExprToNode(app, mc.getArgument(0), "",
                        methodReturnIds, parent.id + "_bp");
                    if (child != null) {
                        child.addProperty("borderPaneRegion", method.substring(3).toLowerCase());
                        ConversionUtils.addChild(app, parent, child);
                    }
                }
            }

            // Tab.setContent(...)
            if ("setContent".equals(method) && mc.getScope().isPresent()
                    && mc.getArguments().size() == 1) {
                Node parent = ConversionUtils.findById(app, mc.getScope().get().toString());
                if (parent != null) {
                    Node child = resolveExprToNode(app, mc.getArgument(0), "",
                        methodReturnIds, parent.id + "_content");
                    if (child != null) ConversionUtils.addChild(app, parent, child);
                }
            }

            // GridPane.setMargin(child, new Insets(v)) / HBox.setMargin / VBox.setMargin / FlowPane.setMargin
            // → restore cellMargin so FXML round-trip can emit <ParentType.margin>
            if ("setMargin".equals(method) && mc.getArguments().size() >= 2
                    && mc.getScope().isPresent()) {
                String marginScope = mc.getScope().get().toString();
                if ("GridPane".equals(marginScope) || "HBox".equals(marginScope)
                        || "FlowPane".equals(marginScope) || "VBox".equals(marginScope)) {
                    Expression childArg = mc.getArgument(0);
                    Expression insetsArg = mc.getArgument(1);
                    String childId = childArg instanceof NameExpr
                        ? ((NameExpr) childArg).getNameAsString() : null;
                    if (childId != null) {
                        Node childNode = ConversionUtils.findById(app, childId);
                        if (childNode != null && !childNode.properties.containsKey("cellMargin")) {
                            if (insetsArg instanceof ObjectCreationExpr) {
                                ObjectCreationExpr insets = (ObjectCreationExpr) insetsArg;
                                childNode.addProperty("cellMarginParent", marginScope);
                                if (insets.getArguments().size() == 1) {
                                    try {
                                        double v = Double.parseDouble(
                                            insets.getArgument(0).toString().trim());
                                        childNode.addProperty("cellMargin",
                                            String.valueOf((int) v));
                                    } catch (NumberFormatException ignored) {}
                                } else if (insets.getArguments().size() == 4) {
                                    String t2 = insets.getArgument(0).toString().trim();
                                    String r  = insets.getArgument(1).toString().trim();
                                    String b  = insets.getArgument(2).toString().trim();
                                    String l  = insets.getArgument(3).toString().trim();
                                    childNode.addProperty("cellMargin",
                                        t2 + " " + r + " " + b + " " + l);
                                }
                            }
                        }
                    }
                }
            }
        });

        // Constructor-arg children: new VBox(5, btn1, btn2)
        body.findAll(ObjectCreationExpr.class).forEach(oce -> {
            String type = oce.getTypeAsString();
            if (!KNOWN_TYPES.contains(type)) return;
            String parentId = null;
            if (oce.getParentNode().isPresent()) {
                com.github.javaparser.ast.Node parent = oce.getParentNode().get();
                if (parent instanceof VariableDeclarator) {
                    parentId = ((VariableDeclarator) parent).getNameAsString();
                } else if (parent instanceof AssignExpr) {
                    Expression target = ((AssignExpr) parent).getTarget();
                    if (target instanceof NameExpr) parentId = ((NameExpr) target).getNameAsString();
                }
            }
            if (parentId == null) return;
            Node parentNode = ConversionUtils.findById(app, parentId);
            List<Expression> oceArgs = oce.getArguments();
            int childStart = 0;
            if (parentNode != null && !oceArgs.isEmpty()
                    && ("VBox".equals(oce.getTypeAsString()) || "HBox".equals(oce.getTypeAsString()))) {
                String firstArg = oceArgs.get(0).toString();
                if (firstArg.matches("[\\d.]+")) {
                    parentNode.addProperty("hgap", firstArg);
                    parentNode.addProperty("vgap", firstArg);
                    childStart = 1;
                }
            }
            for (int _i = childStart; _i < oceArgs.size(); _i++) {
                Expression arg = oceArgs.get(_i);
                Node childNode2 = null;
                if (arg instanceof NameExpr) {
                    childNode2 = ConversionUtils.findById(app,
                        ((NameExpr) arg).getNameAsString());
                } else if (arg instanceof ObjectCreationExpr) {
                    // inline anonymous widget: register and add as child
                    String synChildId = parentId + "_ctor" + _i;
                    childNode2 = resolveExprToNodeNs(app, arg, "", synChildId);
                    if (childNode2 != null && ConversionUtils.findById(app, childNode2.id) == null)
                        app.allNodes.add(childNode2);
                }
                if (parentNode != null && childNode2 != null)
                    ConversionUtils.addChild(app, parentNode, childNode2);
            }
        });

        // Post-pass: mark expandedPane (Accordion.setExpandedPane(pane))
        app.allNodes.forEach(accNode -> {
            String epId = accNode.properties.get("expandedPaneId");
            if (epId != null) {
                Node pane = ConversionUtils.findById(app, epId);
                if (pane != null) pane.addProperty("expanded", "true");
            }
        });

        // new Scene(rootVar, width, height)
        body.findAll(ObjectCreationExpr.class).forEach(oce -> {
            if (!oce.getTypeAsString().equals("Scene")) return;
            if (!oce.getArguments().isEmpty() && oce.getArgument(0) instanceof NameExpr)
                app.sceneRootId = ((NameExpr) oce.getArgument(0)).getNameAsString();
            if (oce.getArguments().size() >= 3) {
                try { app.sceneWidth  = Integer.parseInt(oce.getArgument(1).toString()); }
                catch (NumberFormatException ignored) {}
                try { app.sceneHeight = Integer.parseInt(oce.getArgument(2).toString()); }
                catch (NumberFormatException ignored) {}
            }
        });

        // ── PASS 4: TableView columns (getColumns().add/addAll) ───────────────
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.findAncestor(LambdaExpr.class).isPresent()) return;
            String mCall4 = mc.getNameAsString();
            if (!mc.getScope().isPresent()) return;
            String scopeStr4 = mc.getScope().get().toString();
            if ((mCall4.equals("add") || mCall4.equals("addAll"))
                    && scopeStr4.matches(".*\\.getColumns\\(\\)$")) {
                String parentVar4 = scopeStr4.replaceAll("\\.getColumns\\(\\)$", "").trim();
                Node tableNode = ConversionUtils.findById(app, parentVar4);
                if (tableNode != null) {
                    java.util.List<String> headers = new java.util.ArrayList<>();
                    for (Expression arg : mc.getArguments()) {
                        if (arg instanceof NameExpr) {
                            String colVar = ((NameExpr) arg).getNameAsString();
                            String hdr = tableColHeaders0.get(colVar);
                            if (hdr != null) headers.add(hdr);
                        }
                    }
                    if (!headers.isEmpty()) {
                        String existing = tableNode.properties.get("tableColumns");
                        if (existing == null) {
                            tableNode.addProperty("tableColumns", String.join("|", headers));
                        } else {
                            tableNode.properties.put("tableColumns", existing + "|" + String.join("|", headers));
                        }
                    }
                }
            }
        });

        return app;
    }

    // ── Parse helpers ─────────────────────────────────────────────────────────

    /**
     * Walk a helper method body with namespace prefix (methodName + "_"),
     * registering all nodes and building their internal hierarchy.
     * Records the returned node ID in methodReturnIds.
     */
    private static void walkHelperBody(CompilationUnit cu, AppMetadata app,
            MethodDeclaration method, String methodName,
            Map<String, String> methodReturnIds, Set<String> walkedHelpers,
            Map<String, String> fieldConstants) {
        if (walkedHelpers.contains(methodName)) return;
        walkedHelpers.add(methodName);
        String ns = methodName + "_";
        BlockStmt body = method.getBody().get();

        // Per-factory call count for cloning (reset per walkHelperBody invocation)
        final Map<String, Integer> helperLocalCallCount = new LinkedHashMap<>();

        // Collect local String variable initializers so we can resolve parameter-dependent styles
        final Map<String, String> localStrings = new LinkedHashMap<>(fieldConstants);
        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            if (!"String".equals(vde.getElementType().asString())) return;
            for (VariableDeclarator vd : vde.getVariables()) {
                if (!vd.getInitializer().isPresent()) continue;
                String val = stringValue(vd.getInitializer().get(), localStrings);
                if (val != null) localStrings.put(vd.getNameAsString(), val);
            }
        });

        // PRE-PASS: Build auxiliary maps for non-KNOWN_TYPE helper variables.
        // listVarItems   : varName → pipe-delimited item strings (for ObservableList vars)
        // treeItemTexts  : varName → text of the TreeItem
        // treeItemChildren: varName → list of children's text strings
        // tableColHeaders: varName → column header text
        final Map<String, String> listVarItems = new java.util.HashMap<>();
        final Map<String, String> treeItemTexts = new java.util.HashMap<>();
        final Map<String, java.util.List<String>> treeItemChildren = new java.util.HashMap<>();
        final Map<String, String> tableColHeaders = new java.util.HashMap<>();

        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            String rawType = vde.getElementType().asString();
            int lt2 = rawType.indexOf('<');
            String baseType = lt2 >= 0 ? rawType.substring(0, lt2) : rawType;
            for (VariableDeclarator vd : vde.getVariables()) {
                if (!vd.getInitializer().isPresent()) continue;
                Expression init = vd.getInitializer().get();
                String varName = vd.getNameAsString();
                // ObservableList<...> / List variable = FXCollections.observableArrayList(...)
                if ("ObservableList".equals(baseType) || "List".equals(baseType)) {
                    if (init instanceof MethodCallExpr) {
                        MethodCallExpr mce = (MethodCallExpr) init;
                        if ("observableArrayList".equals(mce.getNameAsString())) {
                            java.util.List<String> its = new java.util.ArrayList<>();
                            for (Expression a : mce.getArguments()) {
                                String s = stringValue(a);
                                if (s != null) its.add(s);
                            }
                            if (!its.isEmpty()) listVarItems.put(varName, String.join("|", its));
                        }
                    }
                }
                // String[] array initializer
                if (vd.getType().asString().equals("String[]")) {
                    java.util.List<String> its = new java.util.ArrayList<>();
                    if (init instanceof ArrayInitializerExpr) {
                        for (Expression v : ((ArrayInitializerExpr) init).getValues()) {
                            String s = stringValue(v); if (s != null) its.add(s);
                        }
                    } else if (init instanceof ArrayCreationExpr) {
                        ArrayCreationExpr ace = (ArrayCreationExpr) init;
                        if (ace.getInitializer().isPresent())
                            for (Expression v : ace.getInitializer().get().getValues()) {
                                String s = stringValue(v); if (s != null) its.add(s);
                            }
                    }
                    if (!its.isEmpty()) listVarItems.put(varName, String.join("|", its));
                }
                // TreeItem<...> variable = new TreeItem<>("text") OR new TreeItem<>(new Pair<>("key","val"))
                if ("TreeItem".equals(baseType)) {
                    if (init instanceof ObjectCreationExpr) {
                        ObjectCreationExpr oce = (ObjectCreationExpr) init;
                        if (!oce.getArguments().isEmpty()) {
                            String txt = stringValue(oce.getArgument(0));
                            // Handle TreeItem<Pair<...>>: first arg is new Pair<>("key","val") - use key
                            if (txt == null && oce.getArgument(0) instanceof ObjectCreationExpr) {
                                ObjectCreationExpr inner = (ObjectCreationExpr) oce.getArgument(0);
                                if (!inner.getArguments().isEmpty())
                                    txt = stringValue(inner.getArgument(0));
                            }
                            if (txt != null) treeItemTexts.put(varName, txt);
                        }
                    }
                }
                // TableColumn<...> / TreeTableColumn<...> variable = new TableColumn<>("Header")
                if ("TableColumn".equals(baseType) || "TreeTableColumn".equals(baseType)) {
                    if (init instanceof ObjectCreationExpr) {
                        ObjectCreationExpr oce = (ObjectCreationExpr) init;
                        if (!oce.getArguments().isEmpty()) {
                            String txt = stringValue(oce.getArgument(0));
                            if (txt != null) tableColHeaders.put(varName, txt);
                        }
                    }
                }
            }
        });
        // TreeItem hierarchy: rootItem.getChildren().addAll(new TreeItem<>("Child"), ...)
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.findAncestor(LambdaExpr.class).isPresent()) return;
            String mcName = mc.getNameAsString();
            if (!mc.getScope().isPresent()) return;
            String scopeStr = mc.getScope().get().toString();
            if ((mcName.equals("add") || mcName.equals("addAll"))
                    && scopeStr.endsWith(".getChildren()")) {
                String parentVar = scopeStr.substring(0, scopeStr.length() - ".getChildren()".length()).trim();
                if (treeItemTexts.containsKey(parentVar)) {
                    java.util.List<String> children =
                        treeItemChildren.computeIfAbsent(parentVar, k -> new java.util.ArrayList<>());
                    for (Expression arg : mc.getArguments()) {
                        if (arg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr oce = (ObjectCreationExpr) arg;
                            if (!oce.getArguments().isEmpty()) {
                                String txt = stringValue(oce.getArgument(0));
                                // Handle new TreeItem<>(new Pair<>("key","val"))
                                if (txt == null && oce.getArgument(0) instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr inner = (ObjectCreationExpr) oce.getArgument(0);
                                    if (!inner.getArguments().isEmpty())
                                        txt = stringValue(inner.getArgument(0));
                                }
                                if (txt != null) children.add(txt);
                            }
                        } else if (arg instanceof NameExpr) {
                            String childVar = ((NameExpr) arg).getNameAsString();
                            String childTxt = treeItemTexts.get(childVar);
                            if (childTxt != null) children.add(childTxt);
                        }
                    }
                }
            }
        });

        // PASS 1: Register nodes
        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            String type = vde.getElementType().asString();
            int lt = type.indexOf('<');
            if (lt >= 0) type = type.substring(0, lt);
            // Handle fully-qualified type names (e.g. javafx.scene.canvas.Canvas)
            if (type.contains(".")) type = type.substring(type.lastIndexOf('.') + 1);
            if (!KNOWN_TYPES.contains(type)) return;
            // Skip declarations inside for-each loops; handled by PASS 1.5b below
            if (vde.findAncestor(com.github.javaparser.ast.stmt.ForEachStmt.class).isPresent()) return;
            if (vde.findAncestor(com.github.javaparser.ast.stmt.ForStmt.class).isPresent()) return;
            for (VariableDeclarator vd : vde.getVariables()) {
                String nodeId = ns + vd.getNameAsString();
                Node node = ConversionUtils.getOrMake(app, nodeId);
                if (node.type == null) node.type = type;
                if (!vd.getInitializer().isPresent()) continue;
                Expression init = vd.getInitializer().get();
                if (init instanceof ObjectCreationExpr) {
                    ObjectCreationExpr oce = (ObjectCreationExpr) init;
                    if (!oce.getArguments().isEmpty()) {
                        String txt = stringValue(oce.getArgument(0), localStrings);
                        if (txt != null && !node.properties.containsKey("text"))
                            node.addProperty("text", txt);
                    }
                    if ("Spinner".equals(type) && oce.getArguments().size() == 3) {
                        helperNumProp(node, "min",   oce.getArgument(0));
                        helperNumProp(node, "max",   oce.getArgument(1));
                        helperNumProp(node, "value", oce.getArgument(2));
                    }
                    if ("ProgressBar".equals(type) && oce.getArguments().size() == 1)
                        helperNumProp(node, "progress", oce.getArgument(0));
                    if ("ProgressIndicator".equals(type) && oce.getArguments().size() == 1)
                        helperNumProp(node, "progress", oce.getArgument(0));
                    if ("Canvas".equals(type) && oce.getArguments().size() >= 2) {
                        helperNumProp(node, "width",  oce.getArgument(0));
                        helperNumProp(node, "height", oce.getArgument(1));
                    }
                    if ("Slider".equals(type) && oce.getArguments().size() == 3) {
                        helperNumProp(node, "min",   oce.getArgument(0));
                        helperNumProp(node, "max",   oce.getArgument(1));
                        helperNumProp(node, "value", oce.getArgument(2));
                    }
                    if ("FlowPane".equals(type) && oce.getArguments().size() >= 2) {
                        helperNumProp(node, "hgap", oce.getArgument(0));
                        helperNumProp(node, "vgap", oce.getArgument(1));
                    }
                    // Circle: any of Circle(radius), Circle(radius, fill),
                    // Circle(centerX, centerY, radius), Circle(centerX, centerY, radius, fill)
                    if ("Circle".equals(type) && !oce.getArguments().isEmpty()) {
                        int sz = oce.getArguments().size();
                        if (sz == 1 || sz == 2) helperNumProp(node, "radius", oce.getArgument(0));
                        else if (sz >= 3)       helperNumProp(node, "radius", oce.getArgument(2));
                        Expression fillArg = (sz == 2) ? oce.getArgument(1)
                                           : (sz >= 4) ? oce.getArgument(3) : null;
                        if (fillArg != null) {
                            String hex = resolveJfxColor(fillArg, fieldConstants);
                            if (hex != null) node.addProperty("fill", hex);
                        }
                    }
                    if ("Rectangle".equals(type) && oce.getArguments().size() >= 2) {
                        helperNumProp(node, "width",  oce.getArgument(0));
                        helperNumProp(node, "height", oce.getArgument(1));
                    }
                    // Ellipse(radiusX, radiusY) - store radii as width/height in IR
                    if ("Ellipse".equals(type) && oce.getArguments().size() >= 2) {
                        helperNumProp(node, "width",  oce.getArgument(0));
                        helperNumProp(node, "height", oce.getArgument(1));
                    }
                    // Line(startX, startY, endX, endY)
                    if ("Line".equals(type) && oce.getArguments().size() >= 4) {
                        helperNumProp(node, "startX", oce.getArgument(0));
                        helperNumProp(node, "startY", oce.getArgument(1));
                        helperNumProp(node, "endX",   oce.getArgument(2));
                        helperNumProp(node, "endY",   oce.getArgument(3));
                    }
                    // ChoiceBox / ComboBox / ListView: extract items from observableArrayList arg or list variable
                    if ("ChoiceBox".equals(type) || "ComboBox".equals(type) || "ListView".equals(type)) {
                        if (!oce.getArguments().isEmpty()) {
                            java.util.List<String> its = extractListItems(oce.getArgument(0), listVarItems);
                            if (!its.isEmpty()) node.addProperty("items", String.join("|", its));
                        }
                    }
                    // TreeView: link to TreeItem root variable
                    if (("TreeView".equals(type) || "TreeTableView".equals(type))
                            && !oce.getArguments().isEmpty()) {
                        Expression firstArg = oce.getArgument(0);
                        if (firstArg instanceof NameExpr) {
                            String rootVar = ((NameExpr) firstArg).getNameAsString();
                            if (treeItemTexts.containsKey(rootVar)) {
                                // Both TreeView and TreeTableView emit tree structure
                                node.addProperty("treeRootText", treeItemTexts.get(rootVar));
                                node.addProperty("treeRootExpanded", "true");
                                java.util.List<String> kids = treeItemChildren.get(rootVar);
                                if (kids != null && !kids.isEmpty())
                                    node.addProperty("treeChildren", String.join("|", kids));
                            }
                        }
                    }
                    // Separator: extract orientation from constructor arg (e.g. Orientation.VERTICAL)
                    if ("Separator".equals(type) && !oce.getArguments().isEmpty()) {
                        String arg = oce.getArgument(0).toString();
                        String orient = arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg;
                        node.addProperty("orientation", orient);
                    }
                } else if (init instanceof MethodCallExpr) {
                    resolveFactoryMethod(cu, node, (MethodCallExpr) init, localStrings);
                }
            }
        });
        body.findAll(AssignExpr.class).forEach(ae -> {
            if (!(ae.getValue() instanceof ObjectCreationExpr)) return;
            if (!(ae.getTarget() instanceof NameExpr)) return;
            ObjectCreationExpr oce = (ObjectCreationExpr) ae.getValue();
            String type = ConversionUtils.simpleName(oce.getTypeAsString());
            if (!KNOWN_TYPES.contains(type)) return;
            String nodeId = ns + ((NameExpr) ae.getTarget()).getNameAsString();
            Node node = ConversionUtils.getOrMake(app, nodeId);
            if (node.type == null) node.type = type;
            if (!oce.getArguments().isEmpty()) {
                String txt = stringValue(oce.getArgument(0), localStrings);
                if (txt != null && !node.properties.containsKey("text"))
                    node.addProperty("text", txt);
            }
        });

        // Maps loop body var name → ordered list of foreach nodes, used by PASS 3 for correct ordering
        final Map<String, java.util.List<Node>> forEachBodyVarNodes = new java.util.HashMap<>();

        // ── PASS 1.5b (helper): Unroll enhanced for-each loops ────────────────
        body.findAll(com.github.javaparser.ast.stmt.ForEachStmt.class).forEach(feStmt -> {
            if (feStmt.findAncestor(LambdaExpr.class).isPresent()) return;
            VariableDeclarationExpr varDecl = feStmt.getVariable();
            if (varDecl.getVariables().size() != 1) return;
            String loopVar = varDecl.getVariables().get(0).getNameAsString();
            Expression iterable = feStmt.getIterable();
            java.util.List<String> items = new java.util.ArrayList<>();
            if (iterable instanceof NameExpr) {
                String stored = listVarItems.get(((NameExpr) iterable).getNameAsString());
                if (stored != null) for (String s : stored.split("\\|")) items.add(s);
            } else if (iterable instanceof ArrayCreationExpr) {
                ArrayCreationExpr ace = (ArrayCreationExpr) iterable;
                if (ace.getInitializer().isPresent())
                    for (Expression v : ace.getInitializer().get().getValues()) {
                        String s = stringValue(v); if (s != null) items.add(s);
                    }
            } else if (iterable instanceof ArrayInitializerExpr) {
                for (Expression v : ((ArrayInitializerExpr) iterable).getValues()) {
                    String s = stringValue(v); if (s != null) items.add(s);
                }
            }
            if (items.isEmpty()) return;
            if (!(feStmt.getBody() instanceof com.github.javaparser.ast.stmt.BlockStmt)) return;
            com.github.javaparser.ast.stmt.BlockStmt feBody =
                (com.github.javaparser.ast.stmt.BlockStmt) feStmt.getBody();
            for (int idx = 0; idx < items.size(); idx++) {
                final String itemText = items.get(idx);
                for (VariableDeclarationExpr lvde : feBody.findAll(VariableDeclarationExpr.class)) {
                    String lType = lvde.getElementType().asString();
                    int lt2 = lType.indexOf('<'); if (lt2 >= 0) lType = lType.substring(0, lt2);
                    if (!KNOWN_TYPES.contains(lType)) continue;
                    for (VariableDeclarator lvd : lvde.getVariables()) {
                        String synId = ns + "foreach_" + lvd.getNameAsString() + "_" + idx;
                        Node loopNode = ConversionUtils.getOrMake(app, synId);
                        if (loopNode.type == null) loopNode.type = lType;
                        if (lvd.getInitializer().isPresent()
                                && lvd.getInitializer().get() instanceof ObjectCreationExpr) {
                            ObjectCreationExpr lvOce = (ObjectCreationExpr) lvd.getInitializer().get();
                            if (!lvOce.getArguments().isEmpty()) {
                                String argStr = lvOce.getArgument(0).toString();
                                if (argStr.equals(loopVar)) loopNode.addProperty("text", itemText);
                                else {
                                    String resolved = evalStringExprWithCounter(argStr, loopVar, 0);
                                    if (resolved != null) loopNode.addProperty("text", resolved.replace("\0", itemText));
                                }
                            }
                        }
                        final String varName = lvd.getNameAsString();
                        final String scScope = varName + ".getStyleClass()";
                        feBody.findAll(MethodCallExpr.class).forEach(lmc -> {
                            // Skip calls inside if-branches - handled by the conditional block below
                            if (lmc.findAncestor(com.github.javaparser.ast.stmt.IfStmt.class).isPresent()) return;
                            if (!lmc.getScope().isPresent()) return;
                            String lmcScope = lmc.getScope().get().toString();
                            if (lmcScope.equals(varName)) {
                                applyPropFromScopeless(loopNode, lmc);
                            } else if (("add".equals(lmc.getNameAsString()) || "addAll".equals(lmc.getNameAsString()))
                                    && lmcScope.equals(scScope)) {
                                for (Expression scArg : lmc.getArguments()) {
                                    String cls = stringValue(scArg, localStrings);
                                    if (cls != null) {
                                        String ex = loopNode.properties.get("styleClass");
                                        loopNode.properties.put("styleClass", ex == null ? cls : ex + " " + cls);
                                    }
                                }
                            }
                        });
                        // Conditional: if (item.equals("X")) widget.getStyleClass().add("cls")
                        feBody.findAll(com.github.javaparser.ast.stmt.IfStmt.class).forEach(ifStmt -> {
                            Expression cond = ifStmt.getCondition();
                            if (!(cond instanceof MethodCallExpr)) return;
                            MethodCallExpr condMc = (MethodCallExpr) cond;
                            if (!"equals".equals(condMc.getNameAsString())) return;
                            if (!condMc.getScope().isPresent()) return;
                            if (!condMc.getScope().get().toString().equals(loopVar)) return;
                            if (condMc.getArguments().isEmpty()) return;
                            String matchVal = stringValue(condMc.getArgument(0));
                            if (matchVal == null || !matchVal.equals(itemText)) return;
                            ifStmt.getThenStmt().findAll(MethodCallExpr.class).forEach(ifMc -> {
                                if (!ifMc.getScope().isPresent()) return;
                                String ifScope = ifMc.getScope().get().toString();
                                if (ifScope.equals(varName)) {
                                    applyPropFromScopeless(loopNode, ifMc);
                                } else if (("add".equals(ifMc.getNameAsString()) || "addAll".equals(ifMc.getNameAsString()))
                                        && ifScope.equals(scScope)) {
                                    for (Expression scArg : ifMc.getArguments()) {
                                        String cls = stringValue(scArg, localStrings);
                                        if (cls != null) {
                                            String ex = loopNode.properties.get("styleClass");
                                            loopNode.properties.put("styleClass", ex == null ? cls : ex + " " + cls);
                                        }
                                    }
                                }
                            });
                        });
                        // Register for PASS 3 ordered insertion (not addChild here - ordering would be wrong)
                        forEachBodyVarNodes.computeIfAbsent(varName, k -> new java.util.ArrayList<>()).add(loopNode);
                    }
                }
            }
        });

        // ── PASS 1.5c (helper): Chart axis labels ─────────────────────────────
        {
            final Map<String, String> axisLabels = new java.util.HashMap<>();
            final Map<String, String[]> chartAxisVars = new java.util.HashMap<>();
            body.findAll(MethodCallExpr.class).forEach(mc -> {
                if (!mc.getScope().isPresent() || !"setLabel".equals(mc.getNameAsString())) return;
                if (mc.getArguments().isEmpty()) return;
                String label = stringValue(mc.getArgument(0), localStrings);
                if (label != null) axisLabels.put(mc.getScope().get().toString(), label);
            });
            body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
                String t = vde.getElementType().asString(); int lt = t.indexOf('<'); if (lt >= 0) t = t.substring(0, lt);
                if (!java.util.Arrays.asList("LineChart","BarChart","AreaChart","ScatterChart","BubbleChart").contains(t)) return;
                for (VariableDeclarator vd : vde.getVariables()) {
                    if (!vd.getInitializer().isPresent()) continue;
                    Expression init = vd.getInitializer().get();
                    if (!(init instanceof ObjectCreationExpr)) continue;
                    ObjectCreationExpr oce = (ObjectCreationExpr) init;
                    if (oce.getArguments().size() >= 2
                            && oce.getArgument(0) instanceof NameExpr
                            && oce.getArgument(1) instanceof NameExpr) {
                        chartAxisVars.put(vd.getNameAsString(), new String[]{
                            ((NameExpr) oce.getArgument(0)).getNameAsString(),
                            ((NameExpr) oce.getArgument(1)).getNameAsString()
                        });
                    }
                }
            });
            chartAxisVars.forEach((chartVar, axes) -> {
                Node chartNode = ConversionUtils.findById(app, ns + chartVar);
                if (chartNode == null) return;
                String xLabel = axisLabels.get(axes[0]);
                String yLabel = axisLabels.get(axes[1]);
                if (xLabel != null) chartNode.addProperty("xAxisLabel", xLabel);
                if (yLabel != null) chartNode.addProperty("yAxisLabel", yLabel);
            });
        }

        // PASS 2 PRE-PASS: Collect ToggleGroup variable names (helper context)
        final java.util.Set<String> toggleGroupVarsH = new java.util.HashSet<>();
        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            if (vde.getElementType().asString().contains("ToggleGroup")) {
                for (VariableDeclarator vd : vde.getVariables())
                    toggleGroupVarsH.add(vd.getNameAsString());
            }
        });

        // PASS 2 PRE-PASS: Collect DropShadow variable properties
        final Map<String, Map<String, String>> dropShadowVarsH = new java.util.HashMap<>();
        body.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            if (vde.getElementType().asString().contains("DropShadow")) {
                for (VariableDeclarator vd : vde.getVariables())
                    dropShadowVarsH.put(vd.getNameAsString(), new java.util.LinkedHashMap<>());
            }
        });
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            if (!mc.getScope().isPresent()) return;
            String dsScope = mc.getScope().get().toString();
            if (!dropShadowVarsH.containsKey(dsScope)) return;
            Map<String, String> dp = dropShadowVarsH.get(dsScope);
            switch (mc.getNameAsString()) {
                case "setColor":   if (!mc.getArguments().isEmpty()) { String c = resolveJfxColor(mc.getArgument(0), localStrings); if (c != null) dp.put("color", c); } break;
                case "setRadius":  if (!mc.getArguments().isEmpty()) dp.put("radius",  mc.getArgument(0).toString().trim()); break;
                case "setSpread":  if (!mc.getArguments().isEmpty()) dp.put("spread",  mc.getArgument(0).toString().trim()); break;
                case "setOffsetX": if (!mc.getArguments().isEmpty()) dp.put("offsetX", mc.getArgument(0).toString().trim()); break;
                case "setOffsetY": if (!mc.getArguments().isEmpty()) dp.put("offsetY", mc.getArgument(0).toString().trim()); break;
                default: break;
            }
        });

        // PASS 2: Key properties
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.findAncestor(LambdaExpr.class).isPresent()) return;
            if (!mc.getScope().isPresent()) return;
            String scope = mc.getScope().get().toString();
            String mCall = mc.getNameAsString();
            
            // Handle styleClass / stylesheets
            if (("add".equals(mCall) || "addAll".equals(mCall) || "setAll".equals(mCall))) {
                if (scope.endsWith(".getStyleClass()") || scope.endsWith("getStyleClass()")) {
                    String parentVar = scope.replace(".getStyleClass()", "").replace("getStyleClass()", "").trim();
                    Node targetNode = ConversionUtils.findById(app, ns + parentVar);
                    if (targetNode == null) targetNode = ConversionUtils.findById(app, parentVar);
                    if (targetNode != null) {
                        for (Expression arg : mc.getArguments()) {
                            String clazz = stringValue(arg, localStrings);
                            if (clazz != null) {
                                String existing = targetNode.properties.get("styleClass");
                                targetNode.properties.put("styleClass", existing == null ? clazz : existing + " " + clazz);
                            }
                        }
                    }
                    return;
                } else if (scope.endsWith(".getStylesheets()") || scope.endsWith("getStylesheets()")) {
                    for (Expression arg : mc.getArguments()) {
                        String ss = stringValue(arg, localStrings);
                        if (ss != null) app.stylesheets.add(ss);
                    }
                    return;
                }
            }
            
            Node n = ConversionUtils.findById(app, ns + scope);

            // Static layout grow constraints: HBox.setHgrow(child, Priority.X) / VBox.setVgrow(...)
            if (("HBox".equals(scope) && "setHgrow".equals(mCall))
                    || ("VBox".equals(scope) && "setVgrow".equals(mCall))) {
                if (mc.getArguments().size() >= 2) {
                    String childVar = mc.getArgument(0).toString().trim();
                    Node child = ConversionUtils.findById(app, ns + childVar);
                    if (child == null) child = ConversionUtils.findById(app, childVar);
                    if (child != null) {
                        String prio = mc.getArgument(1).toString().trim();
                        prio = prio.contains(".") ? prio.substring(prio.lastIndexOf('.') + 1) : prio;
                        child.addProperty("HBox".equals(scope) ? "hgrow" : "vgrow", prio);
                    }
                }
                return;
            }

            // Static StackPane alignment: StackPane.setAlignment(child, Pos.X)
            if ("StackPane".equals(scope) && "setAlignment".equals(mCall)
                    && mc.getArguments().size() >= 2) {
                String childVar = mc.getArgument(0).toString().trim();
                Node child = ConversionUtils.findById(app, ns + childVar);
                if (child == null) child = ConversionUtils.findById(app, childVar);
                if (child != null) {
                    String arg = mc.getArgument(1).toString().trim();
                    String posKey = arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg;
                    child.addProperty("stackPaneAlignment", posKey);
                }
                return;
            }

            switch (mCall) {
                case "setText":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String v = stringValue(mc.getArgument(0), localStrings);
                        if (v != null) n.addProperty("text", v);
                    } break;
                case "setOnAction":
                    if (n != null) n.addProperty("hasClick", "true"); break;
                case "setStyle":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String v = stringValue(mc.getArgument(0), localStrings);
                        if (v == null) v = resolveStringWithParams(mc.getArgument(0),
                            java.util.Collections.emptyMap(), localStrings);
                        if (v != null) parseFxStyle(v, n);
                    } break;
                case "setPrefSize":
                    if (n != null && mc.getArguments().size() >= 2) {
                        n.addProperty("width",  mc.getArgument(0).toString().trim());
                        n.addProperty("height", mc.getArgument(1).toString().trim());
                    } break;
                case "setPrefWidth":
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("width", mc.getArgument(0).toString().trim()); break;
                case "setPrefHeight":
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("height", mc.getArgument(0).toString().trim()); break;
                case "setPadding":
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression arg = mc.getArgument(0);
                        if (arg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr ins = (ObjectCreationExpr) arg;
                            int argc = ins.getArguments().size();
                            if (argc == 1) {
                                String v = ins.getArgument(0).toString();
                                if (v.matches("[\\d.]+")) n.addProperty("padding", v);
                            } else if (argc == 4) {
                                String t = ins.getArgument(0).toString().trim();
                                String r = ins.getArgument(1).toString().trim();
                                String b2 = ins.getArgument(2).toString().trim();
                                String l = ins.getArgument(3).toString().trim();
                                n.addProperty("padding", t + " " + r + " " + b2 + " " + l);
                            }
                        }
                    } break;
                case "setSelected":
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString()))
                        n.addProperty("selected", "true"); break;
                case "setEditable":
                    if (n != null && mc.getArguments().size() >= 1
                            && "false".equals(mc.getArgument(0).toString()))
                        n.addProperty("editable", "false"); break;
                case "setVisible":
                    if (n != null && mc.getArguments().size() >= 1
                            && "false".equals(mc.getArgument(0).toString()))
                        n.addProperty("visible", "false"); break;
                case "setDisable":
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString()))
                        n.addProperty("enabled", "false"); break;
                case "setHgap":
                    if (n != null && mc.getArguments().size() >= 1)
                        helperNumProp(n, "hgap", mc.getArgument(0)); break;
                case "setVgap":
                    if (n != null && mc.getArguments().size() >= 1)
                        helperNumProp(n, "vgap", mc.getArgument(0)); break;
                case "setMin":
                    if (n != null && mc.getArguments().size() >= 1)
                        helperNumProp(n, "min", mc.getArgument(0)); break;
                case "setMax":
                    if (n != null && mc.getArguments().size() >= 1)
                        helperNumProp(n, "max", mc.getArgument(0)); break;
                case "setValue":
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression valExpr2 = mc.getArgument(0);
                        String numStr2 = valExpr2.toString().trim();
                        if (numStr2.matches("[\\d.]+")) helperNumProp(n, "value", valExpr2);
                        else { String sv2 = stringValue(valExpr2, localStrings); if (sv2 != null) n.addProperty("selectedValue", sv2); }
                    } break;
                case "setProgress":
                    if (n != null && mc.getArguments().size() >= 1)
                        helperNumProp(n, "progress", mc.getArgument(0)); break;
                case "setContent":
                    if (n != null && mc.getArguments().size() >= 1) {
                        Node child = resolveExprToNodeNs(app, mc.getArgument(0), ns,
                            n.id + "_content");
                        if (child != null) ConversionUtils.addChild(app, n, child);
                    } break;
                case "setOrientation":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String arg = mc.getArgument(0).toString();
                        n.addProperty("orientation",
                            arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg);
                    } break;
                case "setDividerPositions":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String posStr = mc.getArgument(0).toString().trim();
                        try { Double.parseDouble(posStr); n.addProperty("dividerPosition", posStr); }
                        catch (NumberFormatException ignored) {}
                    } break;
                case "setIndeterminate":
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString()))
                        n.addProperty("indeterminate", "true"); break;
                case "setTextFill":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String colorHex = resolveJfxColor(mc.getArgument(0), localStrings);
                        if (colorHex != null) n.addProperty("foreColor", colorHex);
                    } break;
                case "setItems":
                    // setItems(FXCollections.observableArrayList(...)) or setItems(listVar)
                    if (n != null && mc.getArguments().size() >= 1) {
                        java.util.List<String> its = extractListItems(mc.getArgument(0), listVarItems);
                        if (!its.isEmpty()) n.addProperty("items", String.join("|", its));
                    } break;
                case "setFont":
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression fontArg = mc.getArgument(0);
                        if (fontArg instanceof MethodCallExpr) {
                            MethodCallExpr fontCall = (MethodCallExpr) fontArg;
                            if ("font".equals(fontCall.getNameAsString()) && !fontCall.getArguments().isEmpty()) {
                                // Font.font(size) single-arg - no family
                                String first = fontCall.getArgument(0).toString().trim();
                                if (first.matches("[\\d.]+")) {
                                    n.addProperty("fontSize", first);
                                } else {
                                    String fam = stringValue(fontCall.getArgument(0), localStrings);
                                    if (fam != null) n.addProperty("fontFamily", fam);
                                }
                                for (int i = 0; i < fontCall.getArguments().size(); i++) {
                                    String a = fontCall.getArgument(i).toString().trim();
                                    if (a.matches("[\\d.]+")) n.addProperty("fontSize", a);
                                    else if (a.toUpperCase().contains("BOLD")) n.addProperty("fontWeight", "BOLD");
                                    else if (a.toUpperCase().contains("ITALIC")) n.addProperty("fontPosture", "ITALIC");
                                }
                                n.addProperty("fontSizeUnit", "px"); // Font.font() API uses CSS pixels
                            }
                        } else if (fontArg instanceof ObjectCreationExpr) {
                            // new Font(family, size)
                            ObjectCreationExpr fc = (ObjectCreationExpr) fontArg;
                            if (fc.getArguments().size() >= 2) {
                                String fam = stringValue(fc.getArgument(0), localStrings);
                                if (fam != null) n.addProperty("fontFamily", fam);
                                String sz = fc.getArgument(1).toString().trim();
                                if (sz.matches("[\\d.]+")) n.addProperty("fontSize", sz);
                                n.addProperty("fontSizeUnit", "px"); // new Font() uses CSS pixels
                            } else if (fc.getArguments().size() == 1) {
                                String sz = fc.getArgument(0).toString().trim();
                                if (sz.matches("[\\d.]+")) n.addProperty("fontSize", sz);
                                n.addProperty("fontSizeUnit", "px");
                            }
                        }
                    } break;
                case "setAlignment":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String argStr = mc.getArgument(0).toString();
                        String posKey = argStr.contains(".") ? argStr.substring(argStr.lastIndexOf('.') + 1) : argStr;
                        String align = POS_TO_ALIGN.getOrDefault(posKey, null);
                        if (align != null) n.addProperty("textAlign", align);
                    } break;
                case "setMaxWidth":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String w = mc.getArgument(0).toString().trim();
                        n.addProperty("maxWidth", w);
                    } break;
                case "setMaxHeight":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String h = mc.getArgument(0).toString().trim();
                        n.addProperty("maxHeight", h);
                    } break;
                case "setWidth":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("width", val);
                    } break;
                case "setHeight":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("height", val);
                    } break;
                case "setRadius":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("[\\d.]+")) n.addProperty("radius", val);
                    } break;
                case "setEffect":
                    if (n != null && mc.getArguments().size() >= 1) {
                        Expression effArg = mc.getArgument(0);
                        if (effArg instanceof NameExpr) {
                            String effVar = ((NameExpr) effArg).getNameAsString();
                            Map<String, String> dp = dropShadowVarsH.get(effVar);
                            if (dp != null) {
                                String color  = dp.getOrDefault("color",   "rgba(0,0,0,0.5)");
                                String radius = dp.getOrDefault("radius",  "10");
                                String spread = dp.getOrDefault("spread",  "0");
                                String ox     = dp.getOrDefault("offsetX", "0");
                                String oy     = dp.getOrDefault("offsetY", "0");
                                String effCss = "-fx-effect: dropshadow(gaussian, " + color + ", "
                                               + radius + ", " + spread + ", " + ox + ", " + oy + ");";
                                String existing = n.properties.get("extraCss");
                                n.properties.put("extraCss", existing == null ? effCss : existing + " " + effCss);
                            }
                        } else if (effArg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr oce = (ObjectCreationExpr) effArg;
                            if ("GaussianBlur".equals(oce.getTypeAsString())) {
                                n.addProperty("effectType", "GaussianBlur");
                                if (!oce.getArguments().isEmpty())
                                    n.addProperty("effectRadius", oce.getArgument(0).toString().trim());
                            }
                        }
                    } break;
                case "setRotate":
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("rotate", mc.getArgument(0).toString().trim()); break;
                case "setTranslateX":
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("translateX", mc.getArgument(0).toString().trim()); break;
                case "setTranslateY":
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("translateY", mc.getArgument(0).toString().trim()); break;
                case "setOpacity":
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("nodeOpacity", mc.getArgument(0).toString().trim()); break;
                case "setFocusTraversable":
                    if (n != null && mc.getArguments().size() >= 1
                            && "false".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("focusTraversable", "false"); break;
                case "setMouseTransparent":
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("mouseTransparent", "true"); break;
                case "setWrapText":
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("wrapText", "true"); break;
                case "setClosable":
                    if (n != null && mc.getArguments().size() >= 1
                            && "false".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("closable", "false"); break;
                case "setPromptText":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String pv = stringValue(mc.getArgument(0), localStrings);
                        if (pv != null) n.addProperty("promptText", pv);
                    } break;
                case "setPrefRowCount":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String rv = mc.getArgument(0).toString().trim();
                        if (rv.matches("\\d+")) n.addProperty("prefRowCount", rv);
                    } break;
                case "setShowTickLabels":
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("showTickLabels", "true"); break;
                case "setShowTickMarks":
                    if (n != null && mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString().trim()))
                        n.addProperty("showTickMarks", "true"); break;
                case "setToggleGroup":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String groupVar = mc.getArgument(0).toString().trim();
                        if (toggleGroupVarsH.contains(groupVar))
                            n.addProperty("toggleGroupId", groupVar);
                    } break;
                case "setFill":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String c = resolveJfxColor(mc.getArgument(0), fieldConstants);
                        if (c != null) n.addProperty("fill", c);
                    } break;
                case "setStroke":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String c = resolveJfxColor(mc.getArgument(0), fieldConstants);
                        if (c != null) n.addProperty("stroke", c);
                    } break;
                case "setStrokeWidth":
                    if (n != null && mc.getArguments().size() >= 1)
                        n.addProperty("strokeWidth", mc.getArgument(0).toString().trim()); break;
                case "setPrefColumns":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("\\d+")) n.addProperty("prefColumns", val);
                    } break;
                case "setPageCount":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("\\d+")) n.addProperty("pageCount", val);
                    } break;
                case "setCurrentPageIndex":
                    if (n != null && mc.getArguments().size() >= 1) {
                        String val = mc.getArgument(0).toString().trim();
                        if (val.matches("\\d+")) n.addProperty("currentPageIndex", val);
                    } break;
                case "setExpandedPane":
                    if (n != null && mc.getArguments().size() >= 1
                            && mc.getArgument(0) instanceof NameExpr) {
                        String paneId = ((NameExpr) mc.getArgument(0)).getNameAsString();
                        n.addProperty("expandedPaneId", paneId);
                    } break;
                default: break;
            }
            // getSelectionModel().selectFirst() → selectedIndex = 0
            if ("selectFirst".equals(mCall) && mc.getScope().isPresent()) {
                String sStr = mc.getScope().get().toString();
                if (sStr.endsWith(".getSelectionModel()")) {
                    String ctrlVar = sStr.substring(0, sStr.length() - ".getSelectionModel()".length()).trim();
                    Node ctrl = ConversionUtils.findById(app, ns + ctrlVar);
                    if (ctrl != null) ctrl.addProperty("selectedIndex", "0");
                }
            }
        });

        // PASS 3: Hierarchy
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            String mCall = mc.getNameAsString();
            if (!mc.getScope().isPresent()) return;
            String scopeStr = mc.getScope().get().toString();

            // Helper: demand-walk a factory method then resolve it into a node (with cloning)
            // Returns the resolved/cloned child node, or null if not a known factory.
            // Side-effects: registers clone in app.allNodes, calls reapplyParamsToClone.
            java.util.function.Function<MethodCallExpr, Node> resolveFactoryArg = factoryCall -> {
                if (factoryCall.getScope().isPresent()) return null;
                String fName = factoryCall.getNameAsString();
                // Demand-walk the method if not done yet
                Optional<MethodDeclaration> factoryDecl = cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> m.getNameAsString().equals(fName)
                              && m.getBody().isPresent()
                              && m.getParameters().size() == factoryCall.getArguments().size())
                    .findFirst();
                if (factoryDecl.isPresent()) {
                    walkHelperBody(cu, app, factoryDecl.get(), fName,
                        methodReturnIds, walkedHelpers, fieldConstants);
                }
                String rid = methodReturnIds.get(fName);
                if (rid == null) return null;
                Node template = ConversionUtils.findById(app, rid);
                if (template == null) return null;

                int callIdx = helperLocalCallCount.merge(fName, 1, Integer::sum) - 1;
                Node child;
                Map<String, String> idMap;
                if (callIdx == 0) {
                    // First call: use the template as-is, build trivial idMap
                    child = template;
                    idMap = buildIdentityIdMap(app, template);
                } else {
                    // Subsequent calls: deep-clone and get the original→clone ID map
                    Map.Entry<Node, Map<String, String>> cloneResult =
                        deepCloneSubtreeWithMap(app, template, rid + "_" + callIdx);
                    child = cloneResult.getKey();
                    idMap = cloneResult.getValue();
                }
                // Re-apply actual call-site args to all cloned nodes
                if (factoryDecl.isPresent()) {
                    reapplyParamsToClone(cu, app, factoryDecl.get(), fName,
                        idMap, factoryCall.getArguments(), fieldConstants);
                }
                return child;
            };

            // getChildren().add / addAll
            if ((mCall.equals("add") || mCall.equals("addAll"))
                    && (scopeStr.endsWith(".getChildren()") || scopeStr.endsWith("getChildren()"))) {
                String parentId = scopeStr
                    .replace(".getChildren()", "").replace("getChildren()", "").trim();
                Node parent = ConversionUtils.findById(app, ns + parentId);
                if (parent == null) return;
                int idx = parent.children.size();
                for (Expression arg : mc.getArguments()) {
                    Node child = null;
                    if (arg instanceof MethodCallExpr) {
                        child = resolveFactoryArg.apply((MethodCallExpr) arg);
                    }
                    if (child == null) {
                        child = resolveExprToNodeNs(app, arg, ns, parent.id + "_ch" + idx);
                    }
                    // ForEach loop variable: inject all iterated nodes at this position
                    if (child == null && arg instanceof NameExpr) {
                        java.util.List<Node> feNodes = forEachBodyVarNodes.get(((NameExpr) arg).getNameAsString());
                        if (feNodes != null) {
                            for (Node feNode : feNodes) { ConversionUtils.addChild(app, parent, feNode); idx++; }
                            continue;
                        }
                    }
                    if (child != null) { ConversionUtils.addChild(app, parent, child); idx++; }
                }
            }
            // getTabs() / getItems() / getPanes() / getButtons() / getMenus()
            if ((mCall.equals("add") || mCall.equals("addAll"))
                    && scopeStr.matches(".*\\.(getTabs|getItems|getPanes|getButtons|getMenus)\\(\\)$")) {
                String parentId = scopeStr.replaceAll("\\.(getTabs|getItems|getPanes|getButtons|getMenus)\\(\\)$", "");
                Node parent = ConversionUtils.findById(app, ns + parentId);
                if (parent == null) return;
                int idx = parent.children.size();
                for (Expression arg : mc.getArguments()) {
                    Node child = null;
                    if (arg instanceof MethodCallExpr) {
                        child = resolveFactoryArg.apply((MethodCallExpr) arg);
                    }
                    if (child == null) {
                        child = resolveExprToNodeNs(app, arg, ns, parent.id + "_tab" + idx);
                    }
                    if (child != null) { ConversionUtils.addChild(app, parent, child); idx++; }
                }
            }
            // GridPane.add(child, col, row)
            if (mCall.equals("add") && mc.getArguments().size() >= 3) {
                Expression c0 = mc.getArgument(0), c1 = mc.getArgument(1), c2 = mc.getArgument(2);
                if (c1 instanceof IntegerLiteralExpr && c2 instanceof IntegerLiteralExpr) {
                    Node parent = ConversionUtils.findById(app, ns + scopeStr);
                    if (parent != null) {
                        String col = c1.toString(), row = c2.toString();
                        Node child = resolveExprToNodeNs(app, c0, ns,
                            parent.id + "_c" + col + "_r" + row);
                        if (child != null) {
                            ConversionUtils.addChild(app, parent, child);
                            child.addProperty("gridCol", col);
                            child.addProperty("gridRow", row);
                        }
                    }
                }
            }
            // BorderPane setTop / setCenter / setLeft / setRight / setBottom
            if (mc.getArguments().size() == 1
                    && java.util.Arrays.asList("setTop","setBottom","setLeft",
                                               "setRight","setCenter").contains(mCall)) {
                Node parent = ConversionUtils.findById(app, ns + scopeStr);
                if (parent != null) {
                    Node child = resolveExprToNodeNs(app, mc.getArgument(0), ns,
                        parent.id + "_bp");
                    if (child != null) {
                        child.addProperty("borderPaneRegion", mCall.substring(3).toLowerCase());
                        ConversionUtils.addChild(app, parent, child);
                    }
                }
            }
        });

        // Constructor-arg children: new VBox(spacing, child1, child2, ...)
        body.findAll(ObjectCreationExpr.class).forEach(oce -> {
            String type = oce.getTypeAsString();
            if (!KNOWN_TYPES.contains(type)) return;
            String parentId = null;
            if (oce.getParentNode().isPresent()) {
                com.github.javaparser.ast.Node par = oce.getParentNode().get();
                if (par instanceof VariableDeclarator)
                    parentId = ns + ((VariableDeclarator) par).getNameAsString();
                else if (par instanceof AssignExpr) {
                    Expression tgt = ((AssignExpr) par).getTarget();
                    if (tgt instanceof NameExpr) parentId = ns + ((NameExpr) tgt).getNameAsString();
                }
            }
            if (parentId == null) return;
            Node parentNode = ConversionUtils.findById(app, parentId);
            if (parentNode == null) return;
            List<Expression> args = oce.getArguments();
            int childStart = 0;
            if (("VBox".equals(type) || "HBox".equals(type)) && !args.isEmpty()) {
                String first = args.get(0).toString();
                if (first.matches("[\\d.]+")) {
                    parentNode.addProperty("hgap", first);
                    parentNode.addProperty("vgap", first);
                    childStart = 1;
                }
            }
            for (int i = childStart; i < args.size(); i++) {
                Node child = resolveExprToNodeNs(app, args.get(i), ns, parentId + "_c" + i);
                if (child != null) ConversionUtils.addChild(app, parentNode, child);
            }
        });

        // PASS 4: TableView columns (getColumns().addAll) and rows (getItems().addAll with Pair)
        body.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.findAncestor(LambdaExpr.class).isPresent()) return;
            String mCall4 = mc.getNameAsString();
            if (!mc.getScope().isPresent()) return;
            String scopeStr4 = mc.getScope().get().toString();
            // tableView.getColumns().addAll(col1, col2, ...) → tableColumns property
            if ((mCall4.equals("add") || mCall4.equals("addAll"))
                    && scopeStr4.matches(".*\\.getColumns\\(\\)$")) {
                String parentVar4 = scopeStr4.replaceAll("\\.getColumns\\(\\)$", "").trim();
                Node tableNode = ConversionUtils.findById(app, ns + parentVar4);
                if (tableNode != null) {
                    java.util.List<String> headers = new java.util.ArrayList<>();
                    for (Expression arg : mc.getArguments()) {
                        if (arg instanceof NameExpr) {
                            String colVar = ((NameExpr) arg).getNameAsString();
                            String hdr = tableColHeaders.get(colVar);
                            if (hdr != null) headers.add(hdr);
                        }
                    }
                    if (!headers.isEmpty()) tableNode.addProperty("tableColumns", String.join("|", headers));
                }
            }
            // tableView.getItems().addAll(new Pair<>("Alice","25"), ...) → tableRows property
            // (Only handle if parent is a TableView/TreeTableView, not generic getItems collections)
            if ((mCall4.equals("add") || mCall4.equals("addAll"))
                    && scopeStr4.matches(".*\\.getItems\\(\\)$")) {
                String parentVar4 = scopeStr4.replaceAll("\\.getItems\\(\\)$", "").trim();
                Node tableNode = ConversionUtils.findById(app, ns + parentVar4);
                if (tableNode != null && ("TableView".equals(tableNode.type) || "TreeTableView".equals(tableNode.type))) {
                    java.util.List<String> rows = new java.util.ArrayList<>();
                    for (Expression arg : mc.getArguments()) {
                        if (arg instanceof ObjectCreationExpr) {
                            ObjectCreationExpr oce = (ObjectCreationExpr) arg;
                            java.util.List<String> cells = new java.util.ArrayList<>();
                            for (Expression cell : oce.getArguments()) {
                                String val = stringValue(cell);
                                if (val != null) cells.add(val);
                            }
                            if (!cells.isEmpty()) rows.add(String.join(",", cells));
                        }
                    }
                    if (!rows.isEmpty()) tableNode.addProperty("tableRows", String.join("|", rows));
                }
            }
        });

        // Post-pass: mark expandedPane (Accordion.setExpandedPane(pane))
        app.allNodes.forEach(accNode -> {
            String epId = accNode.properties.get("expandedPaneId");
            if (epId != null) {
                Node pane = ConversionUtils.findById(app, ns + epId);
                if (pane == null) pane = ConversionUtils.findById(app, epId);
                if (pane != null) pane.addProperty("expanded", "true");
            }
        });

        // Track return node
        body.findAll(com.github.javaparser.ast.stmt.ReturnStmt.class).forEach(ret -> {
            if (!ret.getExpression().isPresent()) return;
            Expression retExpr = ret.getExpression().get();
            if (retExpr instanceof NameExpr) {
                String retVar = ((NameExpr) retExpr).getNameAsString();
                String nodeId = ns + retVar;
                if (ConversionUtils.findById(app, nodeId) != null)
                    methodReturnIds.putIfAbsent(methodName, nodeId);
            }
        });
    }

    /**
     * Resolve an expression to a Node within a given namespace.
     * NameExpr: looks up ns+name, falls back to bare name.
     * ObjectCreationExpr of known type: creates synthetic node with synId,
     *   and recursively resolves constructor args for VBox/HBox/ScrollPane/StackPane.
     */
    private static Node resolveExprToNodeNs(AppMetadata app, Expression expr,
            String ns, String synId) {
        if (expr instanceof NameExpr) {
            String varName = ((NameExpr) expr).getNameAsString();
            Node n = ConversionUtils.findById(app, ns + varName);
            if (n == null) n = ConversionUtils.findById(app, varName);
            return n;
        }
        if (expr instanceof ObjectCreationExpr) {
            ObjectCreationExpr oce = (ObjectCreationExpr) expr;
            String type = oce.getTypeAsString();
            int lt = type.indexOf('<');
            if (lt >= 0) type = type.substring(0, lt);
            if (!KNOWN_TYPES.contains(type)) return null;
            Node n = ConversionUtils.getOrMake(app, synId);
            if (n.type == null) n.type = type;
            if (!oce.getArguments().isEmpty()) {
                String txt = stringValue(oce.getArgument(0));
                if (txt != null && !n.properties.containsKey("text"))
                    n.addProperty("text", txt);
            }
            // Slider(min, max, value) inline constructor
            if ("Slider".equals(type) && oce.getArguments().size() == 3) {
                helperNumProp(n, "min",   oce.getArgument(0));
                helperNumProp(n, "max",   oce.getArgument(1));
                helperNumProp(n, "value", oce.getArgument(2));
            }
            // ProgressIndicator(value) / ProgressBar(value) inline constructor
            if (("ProgressIndicator".equals(type) || "ProgressBar".equals(type))
                    && oce.getArguments().size() == 1)
                helperNumProp(n, "progress", oce.getArgument(0));
            // ScrollPane(child): set AutoScroll and link the wrapped child
            if ("ScrollPane".equals(type) && !oce.getArguments().isEmpty()
                    && oce.getArgument(0) instanceof NameExpr) {
                n.addProperty("autoScroll", "true");
                String childVar = ((NameExpr) oce.getArgument(0)).getNameAsString();
                Node child = ConversionUtils.findById(app, ns + childVar);
                if (child == null) child = ConversionUtils.findById(app, childVar);
                if (child != null) ConversionUtils.addChild(app, n, child);
            }
            // StackPane(children...)
            if ("StackPane".equals(type)) {
                for (int i = 0; i < oce.getArguments().size(); i++) {
                    Node child = resolveExprToNodeNs(app, oce.getArgument(i), ns, synId + "_c" + i);
                    if (child != null) ConversionUtils.addChild(app, n, child);
                }
            }
            // VBox / HBox inline: first numeric arg = spacing, rest are children
            if ("VBox".equals(type) || "HBox".equals(type)) {
                int start = 0;
                if (!oce.getArguments().isEmpty()) {
                    String first = oce.getArgument(0).toString();
                    if (first.matches("[\\d.]+")) {
                        n.addProperty("hgap", first);
                        n.addProperty("vgap", first);
                        start = 1;
                    }
                }
                for (int i = start; i < oce.getArguments().size(); i++) {
                    Node child = resolveExprToNodeNs(app, oce.getArgument(i), ns, synId + "_c" + i);
                    if (child != null) ConversionUtils.addChild(app, n, child);
                }
            }
            // TitledPane(text, contentNode): second arg is the content child
            if ("TitledPane".equals(type) && oce.getArguments().size() >= 2) {
                Node content = resolveExprToNodeNs(app, oce.getArgument(1), ns, synId + "_content");
                if (content != null) ConversionUtils.addChild(app, n, content);
            }
            // ToolBar(Node...): all args are toolbar items
            if ("ToolBar".equals(type)) {
                for (int i = 0; i < oce.getArguments().size(); i++) {
                    Node child = resolveExprToNodeNs(app, oce.getArgument(i), ns, synId + "_item" + i);
                    if (child != null) ConversionUtils.addChild(app, n, child);
                }
            }
            // AnchorPane / FlowPane / Pane: all args are direct children
            if ("AnchorPane".equals(type) || "FlowPane".equals(type) || "Pane".equals(type)) {
                for (int i = 0; i < oce.getArguments().size(); i++) {
                    Node child = resolveExprToNodeNs(app, oce.getArgument(i), ns, synId + "_c" + i);
                    if (child != null) ConversionUtils.addChild(app, n, child);
                }
            }
            // Anonymous initializer block: new Widget() {{ setX(...); }}
            if (oce.getAnonymousClassBody().isPresent()) {
                for (com.github.javaparser.ast.body.BodyDeclaration<?> member
                        : oce.getAnonymousClassBody().get()) {
                    if (member instanceof com.github.javaparser.ast.body.InitializerDeclaration) {
                        ((com.github.javaparser.ast.body.InitializerDeclaration) member)
                            .getBody().findAll(MethodCallExpr.class).forEach(mce -> {
                                if (mce.getScope().isPresent()) return; // scoped calls target other objects
                                applyPropFromScopeless(n, mce);
                            });
                    }
                }
            }
            return n;
        }
        return null;
    }

    /**
     * Like resolveExprToNodeNs but also handles MethodCallExpr factory calls
     * via methodReturnIds (used in start() context where ns = "").
     */
    private static Node resolveExprToNode(AppMetadata app, Expression expr,
            String ns, Map<String, String> methodReturnIds, String synId) {
        if (expr instanceof MethodCallExpr) {
            MethodCallExpr mce = (MethodCallExpr) expr;
            if (!mce.getScope().isPresent()) {
                String rid = methodReturnIds.get(mce.getNameAsString());
                if (rid != null) return ConversionUtils.findById(app, rid);
            }
        }
        return resolveExprToNodeNs(app, expr, ns, synId);
    }

    /**
     * Merges "from" node's children and properties into "into",
     * then removes "from" from allNodes and fixes any dangling child references.
     */
    private static void mergeHelperNode(AppMetadata app, Node from, Node into) {
        for (Node child : new ArrayList<>(from.children)) {
            if (!into.children.contains(child))
                ConversionUtils.addChild(app, into, child);
        }
        from.properties.forEach((k, v) -> into.properties.putIfAbsent(k, v));
        app.allNodes.remove(from);
        for (Node n : app.allNodes)
            n.children.replaceAll(c -> c == from ? into : c);
    }

    /** Sets a numeric property if the expression resolves to a plain number literal. */
    private static void helperNumProp(Node node, String key, Expression expr) {
        String v = expr.toString().trim();
        if (v.matches("[\\d.]+")) node.addProperty(key, v);
    }

    /**
     * Returns true if a JavaFX type derives from Region (and therefore exposes
     * {@code setPadding(Insets)}). Shapes, Canvas, ImageView, Text, Group, MediaView
     * do not have setPadding and would cause a compile error if called.
     */
    private static boolean supportsSetPadding(String type) {
        if (type == null) return false;
        switch (type) {
            case "Circle": case "Rectangle": case "Line": case "Ellipse":
            case "Polygon": case "Polyline":
            case "Text": case "TextFlow":
            case "Canvas": case "ImageView": case "MediaView": case "Group":
                return false;
            default:
                return true;
        }
    }

    /**
     * Applies a scopeless method call (from an anonymous init block) as a property on node.
     * Only handles common setter methods relevant to UI properties.
     */
    private static void applyPropFromScopeless(Node n, MethodCallExpr mc) {
        switch (mc.getNameAsString()) {
            case "setStyle":
                if (mc.getArguments().size() >= 1) {
                    String val = stringValue(mc.getArgument(0));
                    if (val != null) parseFxStyle(val, n);
                }
                break;
            case "setPromptText":
                if (mc.getArguments().size() >= 1) {
                    String val = stringValue(mc.getArgument(0));
                    if (val != null) n.addProperty("promptText", val);
                }
                break;
            case "setText":
                if (mc.getArguments().size() >= 1) {
                    String val = stringValue(mc.getArgument(0));
                    if (val != null && !n.properties.containsKey("text"))
                        n.addProperty("text", val);
                }
                break;
            case "setSelected":
                if (mc.getArguments().size() >= 1
                        && "true".equals(mc.getArgument(0).toString()))
                    n.addProperty("selected", "true");
                break;
            case "setValue":
                if (mc.getArguments().size() >= 1) {
                    String v = mc.getArgument(0).toString().trim();
                    if (v.matches("[\\-\\d.]+")) n.addProperty("value", v);
                    else {
                        String sv = stringValue(mc.getArgument(0));
                        if (sv != null) n.addProperty("value", sv);
                    }
                }
                break;
            case "setOrientation":
                if (mc.getArguments().size() >= 1) {
                    String arg = mc.getArgument(0).toString();
                    n.addProperty("orientation",
                        arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg);
                }
                break;
            case "setPrefSize":
                if (mc.getArguments().size() >= 2) {
                    String w = mc.getArgument(0).toString().trim();
                    String h = mc.getArgument(1).toString().trim();
                    if (w.matches("[\\d.]+")) n.addProperty("width", w);
                    if (h.matches("[\\d.]+")) n.addProperty("height", h);
                }
                break;
            case "setPrefWidth":
                if (mc.getArguments().size() >= 1) {
                    String w = mc.getArgument(0).toString().trim();
                    if (w.matches("[\\d.]+")) n.addProperty("width", w);
                }
                break;
            case "setPrefHeight":
                if (mc.getArguments().size() >= 1) {
                    String h = mc.getArgument(0).toString().trim();
                    if (h.matches("[\\d.]+")) n.addProperty("height", h);
                }
                break;
            case "setMin":
                if (mc.getArguments().size() >= 1) {
                    String v = mc.getArgument(0).toString().trim();
                    if (v.matches("[\\-\\d.]+")) n.addProperty("min", v);
                }
                break;
            case "setMax":
                if (mc.getArguments().size() >= 1) {
                    String v = mc.getArgument(0).toString().trim();
                    if (v.matches("[\\-\\d.]+")) n.addProperty("max", v);
                }
                break;
            case "setDisable":
                if (mc.getArguments().size() >= 1
                        && "true".equals(mc.getArgument(0).toString()))
                    n.addProperty("disable", "true");
                break;
            default:
                break;
        }
    }

    /**
     * Recursively deep-clones a node subtree, assigning a new root ID.
     * Child IDs are derived by appending "_c0", "_c1", etc. to the new root ID.
     * All cloned nodes are registered in app.allNodes.
     */
    private static Node deepCloneSubtree(AppMetadata app, Node template, String newRootId) {
        Node clone = new Node();
        clone.id = newRootId;
        clone.type = template.type;
        clone.properties.putAll(template.properties);
        app.allNodes.add(clone);
        for (int i = 0; i < template.children.size(); i++) {
            Node childClone = deepCloneSubtree(app, template.children.get(i), newRootId + "_c" + i);
            ConversionUtils.addChild(app, clone, childClone);
        }
        return clone;
    }

    /**
     * Deep-clones a subtree and returns a mapping from original node IDs to clone node IDs.
     */
    private static java.util.Map.Entry<Node, Map<String, String>> deepCloneSubtreeWithMap(
            AppMetadata app, Node template, String newRootId) {
        Map<String, String> idMap = new LinkedHashMap<>();
        Node clone = deepCloneSubtreeTracked(app, template, newRootId, idMap);
        return new java.util.AbstractMap.SimpleEntry<>(clone, idMap);
    }

    private static Node deepCloneSubtreeTracked(AppMetadata app, Node template,
            String newId, Map<String, String> idMap) {
        Node clone = new Node();
        clone.id = newId;
        clone.type = template.type;
        clone.properties.putAll(template.properties);
        app.allNodes.add(clone);
        idMap.put(template.id, clone.id);
        for (int i = 0; i < template.children.size(); i++) {
            Node childClone = deepCloneSubtreeTracked(app, template.children.get(i),
                newId + "_c" + i, idMap);
            ConversionUtils.addChild(app, clone, childClone);
        }
        return clone;
    }

    /**
     * Builds an identity idMap (originalId → originalId) for the subtree rooted at root.
     * Used when reusing the template itself (first call), so reapplyParamsToClone can
     * find cloneNode by the same ID.
     */
    private static Map<String, String> buildIdentityIdMap(AppMetadata app, Node root) {
        Map<String, String> map = new LinkedHashMap<>();
        buildIdentityIdMapRec(root, map);
        return map;
    }

    private static void buildIdentityIdMapRec(Node n, Map<String, String> map) {
        map.put(n.id, n.id);
        for (Node c : n.children) buildIdentityIdMapRec(c, map);
    }

    /**
     * Re-applies call-site arguments to a cloned subtree of a factory method.
     * Uses the original-to-clone idMap to locate each cloned node by its original id.
     *
     * @param method     the factory MethodDeclaration
     * @param methodName the factory method name (used as namespace prefix)
     * @param idMap      originalId → clonedId mapping
     * @param callArgs   actual call-site argument expressions
     */
    private static void reapplyParamsToClone(CompilationUnit cu, AppMetadata app,
            MethodDeclaration method, String methodName,
            Map<String, String> idMap, List<Expression> callArgs,
            Map<String, String> fieldConstants) {
        List<Parameter> params = method.getParameters();
        Map<String, Expression> paramMap = new java.util.HashMap<>();
        for (int i = 0; i < params.size() && i < callArgs.size(); i++)
            paramMap.put(params.get(i).getNameAsString(), callArgs.get(i));

        // Build localStrings: fieldConstants + resolved param string values
        Map<String, String> localStrings = new LinkedHashMap<>(fieldConstants);
        for (Map.Entry<String, Expression> e : paramMap.entrySet()) {
            String sv = stringValue(e.getValue(), fieldConstants);
            if (sv != null) localStrings.put(e.getKey(), sv);
        }

        String ns = methodName + "_";
        BlockStmt mb = method.getBody().get();

        // Collect local String variable initializers so ternary expressions like
        // (isActive ? activeStyle : baseStyle) can be resolved correctly.
        mb.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            if (!"String".equals(vde.getElementType().asString())) return;
            for (VariableDeclarator vd : vde.getVariables()) {
                if (!vd.getInitializer().isPresent()) continue;
                String val = resolveStringWithParams(vd.getInitializer().get(), paramMap, localStrings);
                if (val != null) localStrings.put(vd.getNameAsString(), val);
            }
        });
        mb.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            String type = vde.getElementType().asString();
            int lt = type.indexOf('<');
            if (lt >= 0) type = type.substring(0, lt);
            if (!KNOWN_TYPES.contains(type)) return;
            for (VariableDeclarator vd : vde.getVariables()) {
                String origId = ns + vd.getNameAsString();
                String cloneId = idMap.get(origId);
                if (cloneId == null) continue;
                Node cloneNode = ConversionUtils.findById(app, cloneId);
                if (cloneNode == null || !vd.getInitializer().isPresent()) continue;
                Expression init = vd.getInitializer().get();
                if (init instanceof ObjectCreationExpr) {
                    ObjectCreationExpr oce = (ObjectCreationExpr) init;
                    if (!oce.getArguments().isEmpty()) {
                        String txt = resolveStringWithParams(oce.getArgument(0), paramMap, localStrings);
                        if (txt != null) cloneNode.properties.put("text", txt);
                    }
                }
            }
        });

        // Re-apply setter calls
        mb.findAll(MethodCallExpr.class).forEach(mc -> {
            if (!mc.getScope().isPresent()) return;
            String origId = ns + mc.getScope().get().toString();
            String cloneId = idMap.get(origId);
            if (cloneId == null) return;
            Node cloneNode = ConversionUtils.findById(app, cloneId);
            if (cloneNode == null) return;
            switch (mc.getNameAsString()) {
                case "setText":
                    if (!mc.getArguments().isEmpty()) {
                        String txt = resolveStringWithParams(mc.getArgument(0), paramMap, localStrings);
                        if (txt != null) cloneNode.properties.put("text", txt);
                    }
                    break;
                case "setStyle":
                    if (!mc.getArguments().isEmpty()) {
                        String s = resolveStringWithParams(mc.getArgument(0), paramMap, localStrings);
                        if (s != null) {
                            // Clear old style properties before reapply so each variant is clean
                            // NOTE: 'padding' is intentionally NOT cleared - it's set by setPadding(), not setStyle()
                            for (String sk : new String[]{"backColor", "foreColor", "borderWidth",
                                    "borderColor", "borderFixed", "borderStyle", "backgroundRadius",
                                    "borderRadius", "opacity", "fontFamily", "fontSize", "fontSizeUnit",
                                    "fontWeight", "fontPosture", "extraCss", "effect"})
                                cloneNode.properties.remove(sk);
                            parseFxStyle(s, cloneNode);
                        }
                    }
                    break;
                case "setTextFill":
                    if (!mc.getArguments().isEmpty()) {
                        String colorHex = resolveJfxColor(mc.getArgument(0), localStrings);
                        if (colorHex != null) cloneNode.properties.put("foreColor", colorHex);
                    }
                    break;
                case "setFont":
                    if (!mc.getArguments().isEmpty()) {
                        Expression fontArg = mc.getArgument(0);
                        if (fontArg instanceof MethodCallExpr) {
                            MethodCallExpr fc = (MethodCallExpr) fontArg;
                            if ("font".equals(fc.getNameAsString()) && !fc.getArguments().isEmpty()) {
                                String fam = resolveStringWithParams(fc.getArgument(0), paramMap, localStrings);
                                if (fam != null) cloneNode.properties.put("fontFamily", fam);
                                for (int i = 0; i < fc.getArguments().size(); i++) {
                                    String a = fc.getArgument(i).toString().trim();
                                    if (a.matches("[\\d.]+")) cloneNode.properties.put("fontSize", a);
                                    else if (a.toUpperCase().contains("BOLD")) cloneNode.properties.put("fontWeight", "BOLD");
                                    else if (a.toUpperCase().contains("ITALIC")) cloneNode.properties.put("fontPosture", "ITALIC");
                                }
                                cloneNode.properties.put("fontSizeUnit", "px"); // Font API = CSS pixels
                            }
                        }
                    }
                    break;
                case "setAlignment":
                    if (!mc.getArguments().isEmpty()) {
                        String argStr = mc.getArgument(0).toString();
                        String posKey = argStr.contains(".")
                            ? argStr.substring(argStr.lastIndexOf('.') + 1) : argStr;
                        String align = POS_TO_ALIGN.getOrDefault(posKey, null);
                        if (align != null) cloneNode.properties.put("textAlign", align);
                    }
                    break;
                case "setMaxWidth":
                    if (!mc.getArguments().isEmpty()) {
                        String w = resolveNumericWithParams(mc.getArgument(0), paramMap);
                        if (w != null) cloneNode.properties.put("maxWidth", w);
                    }
                    break;
                case "setFill":
                    if (!mc.getArguments().isEmpty()) {
                        Expression fillArg = mc.getArgument(0);
                        if (fillArg instanceof NameExpr) {
                            Expression sub = paramMap.get(((NameExpr) fillArg).getNameAsString());
                            if (sub != null) fillArg = sub;
                        }
                        String hex = resolveJfxColor(fillArg, localStrings);
                        if (hex != null) cloneNode.properties.put("fill", hex);
                    }
                    break;
                case "setStroke":
                    if (!mc.getArguments().isEmpty()) {
                        Expression strokeArg = mc.getArgument(0);
                        if (strokeArg instanceof NameExpr) {
                            Expression sub = paramMap.get(((NameExpr) strokeArg).getNameAsString());
                            if (sub != null) strokeArg = sub;
                        }
                        String hex = resolveJfxColor(strokeArg, localStrings);
                        if (hex != null) cloneNode.properties.put("stroke", hex);
                    }
                    break;
                default:
                    break;
            }
        });

        // Re-apply setEffect (DropShadow) on clones
        final Map<String, Map<String, String>> dropShadowVarsC = new java.util.HashMap<>();
        mb.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            if (vde.getElementType().asString().contains("DropShadow"))
                for (VariableDeclarator vd : vde.getVariables())
                    dropShadowVarsC.put(vd.getNameAsString(), new java.util.LinkedHashMap<>());
        });
        mb.findAll(MethodCallExpr.class).forEach(mc -> {
            if (!mc.getScope().isPresent()) return;
            String dsS = mc.getScope().get().toString();
            if (!dropShadowVarsC.containsKey(dsS)) return;
            Map<String, String> dp = dropShadowVarsC.get(dsS);
            switch (mc.getNameAsString()) {
                case "setColor":   if (!mc.getArguments().isEmpty()) { String c = resolveJfxColor(mc.getArgument(0), localStrings); if (c != null) dp.put("color", c); } break;
                case "setRadius":  if (!mc.getArguments().isEmpty()) dp.put("radius",  mc.getArgument(0).toString().trim()); break;
                case "setSpread":  if (!mc.getArguments().isEmpty()) dp.put("spread",  mc.getArgument(0).toString().trim()); break;
                case "setOffsetX": if (!mc.getArguments().isEmpty()) dp.put("offsetX", mc.getArgument(0).toString().trim()); break;
                case "setOffsetY": if (!mc.getArguments().isEmpty()) dp.put("offsetY", mc.getArgument(0).toString().trim()); break;
                default: break;
            }
        });
        mb.findAll(MethodCallExpr.class).forEach(mc -> {
            if (!"setEffect".equals(mc.getNameAsString())) return;
            if (!mc.getScope().isPresent()) return;
            String origId = ns + mc.getScope().get().toString();
            String cloneId = idMap.get(origId);
            if (cloneId == null) return;
            Node cloneNode = ConversionUtils.findById(app, cloneId);
            if (cloneNode == null || mc.getArguments().isEmpty()) return;
            if (!(mc.getArgument(0) instanceof NameExpr)) return;
            String effVar = ((NameExpr) mc.getArgument(0)).getNameAsString();
            Map<String, String> dp = dropShadowVarsC.get(effVar);
            if (dp != null) {
                String color  = dp.getOrDefault("color",   "rgba(0,0,0,0.5)");
                String radius = dp.getOrDefault("radius",  "10");
                String spread = dp.getOrDefault("spread",  "0");
                String ox     = dp.getOrDefault("offsetX", "0");
                String oy     = dp.getOrDefault("offsetY", "0");
                String effCss = "-fx-effect: dropshadow(gaussian, " + color + ", "
                               + radius + ", " + spread + ", " + ox + ", " + oy + ");";
                String existing = cloneNode.properties.get("extraCss");
                cloneNode.properties.put("extraCss", existing == null ? effCss : existing + " " + effCss);
            }
        });
    }

    // ── Generate ───────────────────────────────────────────────────────────────

    @Override
    public String generate(AppMetadata app, Map<String, Object> options) {
        boolean matchDefaultFont  = Boolean.TRUE.equals(options.get("matchSourceDefaultFont"));
        String sceneRootVar = uniqueVarName(app, "sceneRoot");

        List<Node> roots = ConversionUtils.computeRoots(app);
        StringBuilder sb = new StringBuilder();

        List<Node> imageNodes = new ArrayList<>();
        for (Node n : app.allNodes)
            if ("ImageView".equals(n.type) || n.properties.get("imagePath") != null) imageNodes.add(n);

        boolean hasAbsPos = app.allNodes.stream()
            .anyMatch(n -> n.properties.get("layoutX") != null);

        // Build parent-type lookup so we can suppress layoutX/Y inside managed containers
        java.util.Set<String> managedLayoutTypes = new java.util.HashSet<>(java.util.Arrays.asList(
            "HBox", "VBox", "FlowPane", "GridPane", "TilePane", "TitledPane", "StackPane"
        ));
        Map<String, String> nodeParentType = new HashMap<>();
        for (Node n : app.allNodes)
            for (Node child : n.children)
                nodeParentType.put(child.id, n.type);

        // Pre-compute effective JavaFX declaration types for Menu/Separator nodes.
        // ToolStripMenuItem maps to "Menu" but may really be MenuItem, CheckMenuItem, etc.
        // ToolStripSeparator inside a menu must be SeparatorMenuItem (not Separator).
        Map<String, String> effectiveTypeMap = new HashMap<>();
        for (Node en : app.allNodes) {
            String et = en.type != null ? en.type : "";
            if ("Menu".equals(et)) {
                if (!en.children.isEmpty()) {
                    effectiveTypeMap.put(en.id, "Menu");
                } else if ("MenuBar".equals(nodeParentType.get(en.id))) {
                    // Empty top-level menus (e.g. "Edit", "Help") stay as Menu
                    effectiveTypeMap.put(en.id, "Menu");
                } else if ("true".equals(en.properties.get("selected"))) {
                    effectiveTypeMap.put(en.id, "CheckMenuItem");
                } else {
                    effectiveTypeMap.put(en.id, "MenuItem");
                }
            } else if ("Separator".equals(et) && "Menu".equals(nodeParentType.get(en.id))) {
                effectiveTypeMap.put(en.id, "SeparatorMenuItem");
            }
        }

        boolean needsCheckBoxCell = app.allNodes.stream()
            .anyMatch(n -> "true".equals(n.properties.get("checkable")));

        sb.append("import javafx.application.Application;\n");
        sb.append("import javafx.geometry.Insets;\n");
        sb.append("import javafx.geometry.Pos;\n");
        sb.append("import javafx.scene.Scene;\n");
        sb.append("import javafx.scene.control.*;\n");
        if (needsCheckBoxCell) {
            sb.append("import javafx.scene.control.cell.CheckBoxListCell;\n");
            sb.append("import javafx.beans.property.SimpleBooleanProperty;\n");
        }
        sb.append("import javafx.collections.ObservableList;\n");
        sb.append("import javafx.scene.image.*;\n");
        sb.append("import javafx.scene.layout.*;\n");
        sb.append("import javafx.scene.text.Font;\n");
        sb.append("import javafx.stage.Stage;\n");
        boolean hasShapes = app.allNodes.stream().anyMatch(n ->
            n.type != null && java.util.Arrays.asList("Circle","Rectangle","Line","Ellipse","Polygon").contains(n.type));
        if (hasShapes)
            sb.append("import javafx.scene.shape.*;\n");
        boolean hasTextNodes = app.allNodes.stream().anyMatch(n ->
            "Text".equals(n.type) || "TextFlow".equals(n.type));
        if (hasTextNodes)
            sb.append("import javafx.scene.text.Text;\n");
        boolean hasFillOrStroke = app.allNodes.stream().anyMatch(n ->
            n.properties.containsKey("fill") || n.properties.containsKey("stroke"));
        if (hasFillOrStroke || hasShapes || hasTextNodes)
            sb.append("import javafx.scene.paint.Color;\n");
        boolean hasCharts = app.allNodes.stream().anyMatch(n ->
            n.type != null && java.util.Arrays.asList(
                "LineChart","BarChart","AreaChart","PieChart","ScatterChart","BubbleChart").contains(n.type));
        if (hasCharts) sb.append("import javafx.scene.chart.*;\n");
        boolean hasCanvas = app.allNodes.stream().anyMatch(n -> "Canvas".equals(n.type));
        if (hasCanvas) sb.append("import javafx.scene.canvas.Canvas;\n");
        sb.append("\n");

        if (!imageNodes.isEmpty() || app.formBackgroundImage != null) {
            if (app.formBackgroundImage != null) {
                String bgFile = app.formBackgroundImage.replaceAll("[^\\w.]", "_") + ".png";
                sb.append("//   assets/").append(bgFile).append("  (form background)\n");
            }
            for (Node n : imageNodes) {
                String res = n.properties.get("imagePath");
                String fileName = (res != null)
                    ? res.replaceAll("[^\\w.]", "_") + ".png"
                    : n.id + ".png";
                String layout = n.properties.get("bgImageLayout");
                String mode   = n.properties.get("sizeMode");
                String hint   = (layout != null) ? "  [BackgroundImageLayout=" + layout + "]"
                              : (mode   != null) ? "  [SizeMode=" + mode + "]"
                              : "";
                sb.append("//   assets/").append(fileName).append("  (").append(n.id).append(")").append(hint).append("\n");
            }
            sb.append("\n");
        }

        sb.append("public class ConvertedApp extends Application {\n");
        sb.append("    @Override\n");
        sb.append("    public void start(Stage stage) {\n\n");

        // Build set of IDs that writeJfxTree will auto-generate as local variables.
        // These must NOT be declared in the initial declarations loop (duplicate var error).
        java.util.Set<String> skipDeclaration = new java.util.HashSet<>();
        for (Node n : app.allNodes) {
            if ("Tab".equals(n.type)) {
                skipDeclaration.add(n.id + "Scroll");
            } else if ("SplitPane".equals(n.type)) {
                skipDeclaration.add(n.id + "Panel1");
                skipDeclaration.add(n.id + "Panel2");
            }
        }

        for (Node n : app.allNodes) {
            if (skipDeclaration.contains(n.id)) continue;
            String type = n.type;
            if (type == null) {
                continue;
            }
            String imagePath = n.properties.get("imagePath");
            if ("Tab".equals(type)) {
                String tabText = n.properties.get("text");
                sb.append("        Tab ").append(n.id).append(" = new Tab(")
                  .append(ConversionUtils.quoted(tabText != null ? tabText : n.id)).append(");\n");
                sb.append("        ").append(n.id).append(".setClosable(false);\n");
            } else if ("Spinner".equals(type)) {
                String initVal = n.properties.get("value");
                String textVal = n.properties.get("text");
                String itemsVal = n.properties.get("items");
                if (itemsVal != null) {
                    // DomainUpDown with an items list → Spinner<String> with ListSpinnerValueFactory
                    sb.append("        Spinner<String> ").append(n.id)
                      .append(" = new Spinner<>(new javafx.scene.control.SpinnerValueFactory.ListSpinnerValueFactory<>(")
                      .append("javafx.collections.FXCollections.observableArrayList(");
                    String[] its = itemsVal.split("\\|");
                    for (int i = 0; i < its.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(ConversionUtils.quoted(its[i]));
                    }
                    sb.append(")));\n");
                } else if (textVal != null && !textVal.matches("[\\d.]+")) {
                    // DomainUpDown with a single text value
                    sb.append("        Spinner<String> ").append(n.id)
                      .append(" = new Spinner<>(new javafx.scene.control.SpinnerValueFactory.ListSpinnerValueFactory<>(")
                      .append("javafx.collections.FXCollections.observableArrayList(")
                      .append(ConversionUtils.quoted(textVal)).append(")));\n");
                } else {
                    int startVal = 0;
                    if (initVal != null) { try { startVal = (int) Double.parseDouble(initVal); } catch (NumberFormatException ignored) {} }
                    String spinMin = n.properties.get("min");
                    String spinMax = n.properties.get("max");
                    int minInt = 0, maxInt = 100;
                    if (spinMin != null) { try { minInt = (int) Double.parseDouble(spinMin); } catch (NumberFormatException ignored) {} }
                    if (spinMax != null) { try { maxInt = (int) Double.parseDouble(spinMax); } catch (NumberFormatException ignored) {} }
                    sb.append("        Spinner<Integer> ").append(n.id)
                      .append(" = new Spinner<>(").append(minInt).append(", ").append(maxInt).append(", ").append(startVal).append(");\n");
                }
            } else if ("ImageView".equals(type)) {
                String fileName = (imagePath != null)
                    ? imagePath.replaceAll("[^\\w.]", "_") + ".png"
                    : n.id + ".png";
                String bgLayout = n.properties.get("bgImageLayout");
                String sizeMode = n.properties.get("sizeMode");
                boolean isBackground = "background".equals(n.properties.get("imageSource"));
                String effectiveMode = (sizeMode != null) ? sizeMode
                    : (bgLayout != null) ? bgLayout
                    : (isBackground ? "Tile" : "StretchImage");
                if ("Tile".equals(effectiveMode)) {
                    sb.append("        Region ").append(n.id).append(" = new Region();\n");
                } else {
                    sb.append("        ImageView ").append(n.id)
                      .append(" = new ImageView(\"assets/").append(fileName).append("\");\n");
                }
            } else if ("TreeItem".equals(type)) {
                String treeVal = n.properties.get("value");
                String treeText = (treeVal != null) ? treeVal : "";
                sb.append("        TreeItem<String> ").append(n.id)
                  .append(" = new TreeItem<>(").append(ConversionUtils.quoted(treeText)).append(");\n");
                if ("true".equals(n.properties.get("expanded")))
                    sb.append("        ").append(n.id).append(".setExpanded(true);\n");
            } else if ("TreeView".equals(type)) {
                sb.append("        TreeView<String> ").append(n.id).append(" = new TreeView<>();\n");
            } else if (java.util.Arrays.asList("LineChart","BarChart","AreaChart").contains(type)) {
                // XYChart subclasses require axes - declare them inline and pass to the constructor.
                String xVar = n.id + "X";
                String yVar = n.id + "Y";
                sb.append("        CategoryAxis ").append(xVar).append(" = new CategoryAxis();\n");
                sb.append("        NumberAxis ").append(yVar).append(" = new NumberAxis();\n");
                String xLabel = n.properties.get("xAxisLabel");
                String yLabel = n.properties.get("yAxisLabel");
                if (xLabel != null && !xLabel.isEmpty())
                    sb.append("        ").append(xVar).append(".setLabel(")
                      .append(ConversionUtils.quoted(xLabel)).append(");\n");
                if (yLabel != null && !yLabel.isEmpty())
                    sb.append("        ").append(yVar).append(".setLabel(")
                      .append(ConversionUtils.quoted(yLabel)).append(");\n");
                sb.append("        ").append(type).append("<String, Number> ").append(n.id)
                  .append(" = new ").append(type).append("<>(").append(xVar).append(", ").append(yVar).append(");\n");
            } else if (effectiveTypeMap.containsKey(n.id)) {
                // Menu-family and Separator-in-menu nodes: use the pre-computed effective type
                String effType = effectiveTypeMap.get(n.id);
                sb.append("        ").append(effType).append(" ").append(n.id)
                  .append(" = new ").append(effType).append("();\n");
            } else {
                // Detect Pane that contains a MenuBar child → declare as BorderPane
                boolean isBorderPane = "Pane".equals(type)
                    && n.children.stream().anyMatch(c -> "MenuBar".equals(c.type))
                    && n.children.stream().anyMatch(c -> !"MenuBar".equals(c.type));
                // Detect Pane with WinForms DockStyle children → declare as BorderPane
                boolean isDockLayoutPane = !isBorderPane && "Pane".equals(type)
                    && !n.children.isEmpty()
                    && n.children.stream().anyMatch(c -> c.properties.get("wfDock") != null);
                // Detect Pane whose children are all Menu → declare as MenuBar
                boolean isMenuBarPane = "Pane".equals(type)
                    && !n.children.isEmpty()
                    && n.children.stream().allMatch(c -> "Menu".equals(c.type));
                // Detect Pane whose children are all TitledPane (WinForms accordion panel) → VBox
                boolean isAccordionVBox = "Pane".equals(type)
                    && !n.children.isEmpty()
                    && n.children.stream().allMatch(c -> "TitledPane".equals(c.type));
                // Detect thin separator Pane: width≤3 or height≤3 and no children
                boolean isSepPane = "Pane".equals(type) && n.children.isEmpty()
                    && (isThin(n.properties.get("height")) || isThin(n.properties.get("width")));
                // WrapContents=false FlowPane → HBox (never wraps, uses HBox.setMargin per item)
                boolean isNoWrapHBox = "FlowPane".equals(type)
                    && "true".equals(n.properties.get("nowrap"));
                String declType = (isBorderPane || isDockLayoutPane) ? "BorderPane"
                    : isMenuBarPane ? "MenuBar"
                    : isAccordionVBox ? "VBox"
                    : isSepPane ? "Separator"
                    : isNoWrapHBox ? "HBox"
                    : type;
                sb.append("        ").append(declType).append(" ").append(n.id)
                  .append(" = new ").append(declType).append("();\n");
            }
        }
        sb.append("\n");

        for (Node n : app.allNodes) {
            if (skipDeclaration.contains(n.id)) continue;
            String type = n.type;
            if (type == null) continue;
            // Tab text is set in its constructor; layout/style properties don't apply
            if ("Tab".equals(type)) continue;
            // SeparatorMenuItem has no settable properties
            if ("SeparatorMenuItem".equals(effectiveTypeMap.getOrDefault(n.id, ""))) continue;

            // Use effective type (e.g. CheckMenuItem) instead of original type for property checks
            String effType = effectiveTypeMap.getOrDefault(n.id, type);

            String text = n.properties.get("text");
            // For Spinner, text is already encoded in the constructor; don't call setText()
            if (text != null && !"ImageView".equals(type) && !"Spinner".equals(type)) {
                // quoted() handles all escaping (\ " \n \t \r) correctly
                sb.append("        ").append(n.id).append(".setText(")
                  .append(ConversionUtils.quoted(text)).append(");\n");
            }

            if ("true".equals(n.properties.get("hasClick")))
                sb.append("        ").append(n.id)
                  .append(".setOnAction(event -> {\n        });\n");

            if (!"ImageView".equals(type)) {
                StringBuilder style = new StringBuilder();
                String back = n.properties.get("backColor");
                if (back != null) style.append("-fx-background-color: ").append(back).append("; ");
                String fore = n.properties.get("foreColor");
                if (fore != null) style.append("-fx-text-fill: ").append(fore).append("; ");
                // Border: explicit width/color first, then FixedSingle fallback
                String bw = n.properties.get("borderWidth");
                String bc = n.properties.get("borderColor");
                if (bw != null) style.append("-fx-border-width: ").append(bw).append("; ");
                if (bc != null) style.append("-fx-border-color: ").append(bc).append("; ");
                if (bw == null && bc == null && "true".equals(n.properties.get("borderFixed")))
                    style.append("-fx-border-color: #808080; -fx-border-width: 1; ");
                String bStyle = n.properties.get("borderStyle");
                if (bStyle != null) style.append("-fx-border-style: ").append(bStyle).append("; ");
                // padding is emitted via setPadding(new Insets(...)) below for Region-derived
                // types - don't duplicate it as -fx-padding in setStyle().
                String padProp = n.properties.get("padding");
                if (padProp != null && !supportsSetPadding(type))
                    style.append("-fx-padding: ").append(padProp).append("; ");
                String opacity = n.properties.get("opacity");
                if (opacity != null) style.append("-fx-opacity: ").append(opacity).append("; ");
                String bgRadius = n.properties.get("backgroundRadius");
                if (bgRadius != null) style.append("-fx-background-radius: ").append(bgRadius).append("; ");
                String bdRadius = n.properties.get("borderRadius");
                if (bdRadius != null) style.append("-fx-border-radius: ").append(bdRadius).append("; ");

                String fam = n.properties.get("fontFamily");
                String fsz = n.properties.get("fontSize");
                // For WinForms conversion, apply default Segoe UI 9pt when no font is specified at all
                if (fam == null && fsz == null && matchDefaultFont) {
                    fam = "Segoe UI";
                    fsz = "9";
                }
                String fszUnit = "pt".equals(n.properties.get("fontSizeUnit")) ? "pt" : "px";
                String wConst = n.properties.get("fontWeight");
                String pConst = n.properties.get("fontPosture");
                // Emit each font property independently - any subset is valid CSS
                if (fam != null)
                    style.append("-fx-font-family: '").append(fam).append("'; ");
                if (fsz != null)
                    style.append("-fx-font-size: ").append(fsz).append(fszUnit).append("; ");
                if (wConst != null)
                    style.append("-fx-font-weight: ").append(wConst.toLowerCase()).append("; ");
                if (pConst != null)
                    style.append("-fx-font-style: ").append(pConst.toLowerCase()).append("; ");
                // Passthrough: any extra CSS not mapped to canonical properties
                String extra = n.properties.get("extraCss");
                if (extra != null) style.append(extra).append(" ");
                if (style.length() > 0)
                    sb.append("        ").append(n.id).append(".setStyle(")
                      .append(ConversionUtils.quoted(style.toString().trim())).append(");\n");
            }
            
            String styleClass = n.properties.get("styleClass");
            if (styleClass != null && !styleClass.isEmpty()) {
                String[] classes = styleClass.split("\\s+");
                sb.append("        ").append(n.id).append(".getStyleClass().addAll(");
                for (int i = 0; i < classes.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(ConversionUtils.quoted(classes[i]));
                }
                sb.append(");\n");
            }

            // promptText (placeholder) for text input controls
            String promptText = n.properties.get("promptText");
            if (promptText != null && ("TextField".equals(type) || "TextArea".equals(type)
                    || "PasswordField".equals(type)))
                sb.append("        ").append(n.id).append(".setPromptText(")
                  .append(ConversionUtils.quoted(promptText)).append(");\n");

            String w = n.properties.get("width"), h = n.properties.get("height");
            if (w != null || h != null) {
                if ("ImageView".equals(type)) {
                    // ImageView only sized when both dimensions present
                    if (w != null && h != null) {
                        String sizeMode = n.properties.get("sizeMode");
                        String bgLayout = n.properties.get("bgImageLayout");
                        boolean isBackground = "background".equals(n.properties.get("imageSource"));
                        String effectiveMode = (sizeMode != null) ? sizeMode
                            : (bgLayout != null) ? bgLayout
                            : (isBackground ? "Tile" : "StretchImage");
                        String fileName2 = (n.properties.get("imagePath") != null)
                            ? n.properties.get("imagePath").replaceAll("[^\\w.]", "_") + ".png"
                            : n.id + ".png";
                        switch (effectiveMode) {
                            case "Normal": case "AutoSize": case "None":
                                break;
                            case "Tile":
                                sb.append("        ").append(n.id).append(".setStyle(\"-fx-background-image: url('assets/")
                                  .append(fileName2).append("'); -fx-background-repeat: repeat; -fx-background-size: auto;\");\n");
                                sb.append("        ").append(n.id).append(".setPrefSize(").append(w).append(", ").append(h).append(");\n");
                                break;
                            case "Zoom": case "CenterImage": case "Center":
                                sb.append("        ").append(n.id).append(".setFitWidth(").append(w).append(");\n");
                                sb.append("        ").append(n.id).append(".setFitHeight(").append(h).append(");\n");
                                sb.append("        ").append(n.id).append(".setPreserveRatio(true);\n");
                                break;
                            default: // StretchImage, Stretch
                                sb.append("        ").append(n.id).append(".setFitWidth(").append(w).append(");\n");
                                sb.append("        ").append(n.id).append(".setFitHeight(").append(h).append(");\n");
                                break;
                        }
                    }
                } else if ("Rectangle".equals(type) || "Ellipse".equals(type)
                           || "Circle".equals(type) || "Line".equals(type)) {
                    // Shape sizing is handled via dedicated setters below (setWidth/Height, setRadius, etc.)
                    // Do NOT call setPrefSize - shapes are not Regions
                } else {
                    // Non-ImageView sizing
                    boolean isFixedSizePane = "Pane".equals(type) && w != null && h != null
                        && !isThin(h) && !isThin(w);
                    // Data view components (ListView, TreeView, TableView) should not stretch
                    boolean isDataView = "ListView".equals(type) || "TreeView".equals(type)
                        || "TableView".equals(type) || "TreeTableView".equals(type);
                    if (w != null && h != null) {
                        sb.append("        ").append(n.id).append(".setPrefSize(")
                          .append(w).append(", ").append(h).append(");\n");
                        if (isFixedSizePane || isDataView) {
                            sb.append("        ").append(n.id).append(".setMaxSize(")
                              .append(w).append(", ").append(h).append(");\n");
                        }
                    } else if (w != null) {
                        sb.append("        ").append(n.id).append(".setPrefWidth(").append(w).append(");\n");
                        if (isDataView) sb.append("        ").append(n.id).append(".setMaxWidth(").append(w).append(");\n");
                    } else {
                        sb.append("        ").append(n.id).append(".setPrefHeight(").append(h).append(");\n");
                        if (isDataView) sb.append("        ").append(n.id).append(".setMaxHeight(").append(h).append(");\n");
                    }
                }
            }

            // Circle / Ellipse / Rectangle dimension properties (shapes don't extend Region)
            String radius = n.properties.get("radius");
            if ("Circle".equals(type) && radius != null)
                sb.append("        ").append(n.id).append(".setRadius(").append(radius).append(");\n");
            String radiusX = n.properties.get("width"), radiusY = n.properties.get("height");
            if ("Ellipse".equals(type)) {
                if (radiusX != null) sb.append("        ").append(n.id).append(".setRadiusX(").append(radiusX).append(");\n");
                if (radiusY != null) sb.append("        ").append(n.id).append(".setRadiusY(").append(radiusY).append(");\n");
            }
            if ("Rectangle".equals(type)) {
                if (w != null) sb.append("        ").append(n.id).append(".setWidth(").append(w).append(");\n");
                if (h != null) sb.append("        ").append(n.id).append(".setHeight(").append(h).append(");\n");
            }

            String align = n.properties.get("textAlign");
            if (align != null) {
                String pos = ALIGN_TO_POS.getOrDefault(align, null);
                if (pos != null)
                    sb.append("        ").append(n.id)
                      .append(".setAlignment(Pos.").append(pos).append(");\n");
            }

            if ("false".equals(n.properties.get("editable")))
                sb.append("        ").append(n.id).append(".setEditable(false);\n");
            if ("false".equals(n.properties.get("visible")))
                sb.append("        ").append(n.id).append(".setVisible(false);\n");
            if ("false".equals(n.properties.get("enabled")))
                sb.append("        ").append(n.id).append(".setDisable(true);\n");
            if ("true".equals(n.properties.get("selected")) &&
                    ("CheckBox".equals(effType) || "RadioButton".equals(effType) || "ToggleButton".equals(effType)
                     || "CheckMenuItem".equals(effType) || "RadioMenuItem".equals(effType)))
                sb.append("        ").append(n.id).append(".setSelected(true);\n");
            if ("true".equals(n.properties.get("indeterminate")) && "CheckBox".equals(type))
                sb.append("        ").append(n.id).append(".setIndeterminate(true);\n");
            if ("true".equals(n.properties.get("wrapText")) &&
                    ("Label".equals(type) || "TextArea".equals(type)))
                sb.append("        ").append(n.id).append(".setWrapText(true);\n");

            // Layout container gaps and padding
            // nowrap FlowPanes are declared as HBox; their spacing is handled by HBox.setMargin
            // per item in writeJfxTree, so no setHgap/setVgap is needed here.
            // Wrapping FlowPanes only emit hgap/vgap when children have NO per-item cellMargin;
            // if cellMargin is present, FlowPane.setMargin per item handles spacing instead
            // (emitting both setHgap and setMargin would double-count the gap).
            if ("FlowPane".equals(type) && !"true".equals(n.properties.get("nowrap"))) {
                boolean hasChildCellMargins = n.children.stream()
                    .anyMatch(c -> c.properties.containsKey("cellMargin"));
                if (!hasChildCellMargins) {
                    String fpHgap = n.properties.get("hgap");
                    String fpVgap = n.properties.get("vgap");
                    if (fpHgap != null) sb.append("        ").append(n.id).append(".setHgap(").append(fpHgap).append(");\n");
                    if (fpVgap != null) sb.append("        ").append(n.id).append(".setVgap(").append(fpVgap).append(");\n");
                }
            } else if ("VBox".equals(type)) {
                // Use explicit spacing from FXML/source if available, otherwise default to 5
                String vboxSpacing = n.properties.getOrDefault("hgap", n.properties.getOrDefault("spacing", "5"));
                if (n.children.isEmpty() || !n.children.stream().allMatch(c -> "TitledPane".equals(c.type)))
                    sb.append("        ").append(n.id).append(".setSpacing(").append(vboxSpacing).append(");\n");
            } else if ("HBox".equals(type)) {
                String hboxSpacing = n.properties.getOrDefault("hgap", n.properties.get("spacing"));
                if (hboxSpacing != null)
                    sb.append("        ").append(n.id).append(".setSpacing(").append(hboxSpacing).append(");\n");
            }
            String padVal = n.properties.get("padding");
            if (padVal != null && supportsSetPadding(type))
                sb.append("        ").append(n.id).append(".setPadding(new Insets(").append(padVal.trim().replace(" ", ", ")).append("));\n");

            String minVal = n.properties.get("min"), maxVal = n.properties.get("max"),
                   sliderVal = n.properties.get("value"), progressVal = n.properties.get("progress");
            if ("Slider".equals(type)) {
                if (minVal != null) sb.append("        ").append(n.id).append(".setMin(").append(minVal).append(");\n");
                if (maxVal != null) sb.append("        ").append(n.id).append(".setMax(").append(maxVal).append(");\n");
                if (sliderVal != null) sb.append("        ").append(n.id).append(".setValue(").append(sliderVal).append(");\n");
                if ("true".equals(n.properties.get("showTickLabels")))
                    sb.append("        ").append(n.id).append(".setShowTickLabels(true);\n");
                if ("true".equals(n.properties.get("showTickMarks")))
                    sb.append("        ").append(n.id).append(".setShowTickMarks(true);\n");
            } else if ("ScrollBar".equals(type)) {
                if (minVal != null) sb.append("        ").append(n.id).append(".setMin(").append(minVal).append(");\n");
                if (maxVal != null) sb.append("        ").append(n.id).append(".setMax(").append(maxVal).append(");\n");
                if (sliderVal != null) sb.append("        ").append(n.id).append(".setValue(").append(sliderVal).append(");\n");
            } else if ("ProgressBar".equals(type) || "ProgressIndicator".equals(type)) {
                if ("true".equals(n.properties.get("indeterminate"))) {
                    sb.append("        ").append(n.id).append(".setProgress(ProgressBar.INDETERMINATE_PROGRESS);\n");
                } else if (progressVal != null) {
                    sb.append("        ").append(n.id).append(".setProgress(").append(progressVal).append(");\n");
                }
            } else if ("DatePicker".equals(type) && n.properties.get("value") != null) {
                // Only set a specific date when the source explicitly specified one
                sb.append("        ").append(n.id).append(".setValue(java.time.LocalDate.parse(\"")
                  .append(n.properties.get("value")).append("\"));\n");
            }

            // Items for ComboBox and ListView
            String items = n.properties.get("items");
            if (items != null && ("ComboBox".equals(type) || "ListView".equals(type))) {
                sb.append("        ").append(n.id).append(".getItems().addAll(");
                String[] itemArr = items.split("\\|");
                for (int i = 0; i < itemArr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(ConversionUtils.quoted(itemArr[i]));
                }
                sb.append(");\n");
            }

            // TableView columns
            String columns = n.properties.get("columns");
            if (columns != null && "TableView".equals(type)) {
                // Retype the declaration as TableView<ObservableList<String>>
                // (we already declared it as TableView above; we'll add column factories here)
                sb.append("        ").append(n.id).append(".setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);\n");
                String[] colArr = columns.split("\\|");
                for (int ci = 0; ci < colArr.length; ci++) {
                    String col = colArr[ci];
                    String colId = n.id + "_col_" + col.replaceAll("[^A-Za-z0-9]", "_");
                    final int colIdx = ci;
                    sb.append("        TableColumn<ObservableList<String>,String> ").append(colId)
                      .append(" = new TableColumn<>(").append(ConversionUtils.quoted(col)).append(");\n");
                    sb.append("        ").append(colId).append(".setCellValueFactory(cd -> ");
                    sb.append("new javafx.beans.property.SimpleStringProperty(cd.getValue().size() > ")
                      .append(colIdx).append(" ? cd.getValue().get(").append(colIdx).append(") : \"\"));\n");
                    sb.append("        ").append(n.id).append(".getColumns().add(").append(colId).append(");\n");
                }
            }

            // Emit border for controls with FixedSingle border style (now handled in style block above)

            // ScrollBar orientation
            String orientation = n.properties.get("orientation");
            if (orientation != null && "ScrollBar".equals(type)) {
                sb.append("        ").append(n.id)
                  .append(".setOrientation(javafx.geometry.Orientation.").append(orientation).append(");\n");
            }

            // SplitPane: orientation + divider position
            if ("SplitPane".equals(type)) {
                String splitOrient = n.properties.get("splitOrientation");
                if (splitOrient != null) {
                    sb.append("        ").append(n.id)
                      .append(".setOrientation(javafx.geometry.Orientation.").append(splitOrient).append(");\n");
                }
                String splitterDist = n.properties.get("splitterDistance");
                String totalW = n.properties.get("width");
                String totalH = n.properties.get("height");
                if (splitterDist != null) {
                    boolean isVert = "VERTICAL".equals(splitOrient); // top/bottom split uses height
                    String totalSize = isVert ? totalH : totalW;
                    if (totalSize != null) {
                        double ratio = Double.parseDouble(splitterDist) / Double.parseDouble(totalSize);
                        sb.append("        ").append(n.id)
                          .append(String.format(".setDividerPositions(%.4f);\n", ratio));
                    }
                }
            }

            String imgPath = n.properties.get("imagePath");
            if (imgPath != null && !"ImageView".equals(type)) {
                String fileName = imgPath.replaceAll("[^\\w.]", "_") + ".png";
                sb.append("        ImageView ").append(n.id).append("Icon")
                  .append(" = new ImageView(\"assets/").append(fileName).append("\");\n");
                sb.append("        ").append(n.id).append("Icon.setFitWidth(24);\n");
                sb.append("        ").append(n.id).append("Icon.setFitHeight(24);\n");
                sb.append("        ").append(n.id).append(".setGraphic(").append(n.id).append("Icon);\n");
            }

            // CheckedListBox: checkbox cell factory
            if ("ListView".equals(type) && "true".equals(n.properties.get("checkable"))) {
                sb.append("        ").append(n.id)
                  .append(".setCellFactory(javafx.scene.control.cell.CheckBoxListCell")
                  .append(".forListView(item -> new SimpleBooleanProperty(false)));\n");
            }

            // TreeView item population
            String treeItemData = n.properties.get("treeItems");
            if (treeItemData != null && "TreeView".equals(type)) {
                String[] topAndChildren = treeItemData.split(":", 2);
                sb.append("        TreeItem<String> ").append(n.id).append("Root = new TreeItem<>(").
                  append(ConversionUtils.quoted(topAndChildren[0])).append(");\n");
                sb.append("        ").append(n.id).append("Root.setExpanded(true);\n");
                if (topAndChildren.length > 1) {
                    for (String ch : topAndChildren[1].split(",")) {
                        if (!ch.trim().isEmpty())
                            sb.append("        ").append(n.id).append("Root.getChildren().add(new TreeItem<>(").
                              append(ConversionUtils.quoted(ch.trim())).append("));\n");
                    }
                }
                sb.append("        ").append(n.id).append(".setRoot(").append(n.id).append("Root);\n");
            }

            // TableView row data
            String tableRowData = n.properties.get("tableRows");
            if (tableRowData != null && "TableView".equals(type)) {
                sb.append("        javafx.collections.ObservableList<javafx.collections.ObservableList<String>> ")
                  .append(n.id).append("Data = javafx.collections.FXCollections.observableArrayList();\n");
                for (String row : tableRowData.split(";")) {
                    String[] cells = row.split(",");
                    sb.append("        ").append(n.id).append("Data.add(javafx.collections.FXCollections.observableArrayList(");
                    for (int ri = 0; ri < cells.length; ri++) {
                        if (ri > 0) sb.append(", ");
                        sb.append(ConversionUtils.quoted(cells[ri].trim()));
                    }
                    sb.append("));\n");
                }
                sb.append("        ").append(n.id).append(".setItems(").append(n.id).append("Data);\n");
            }

            // ComboBox / ChoiceBox first selection
            String selIdx = n.properties.get("selectedIndex");
            if (selIdx != null && ("ComboBox".equals(type) || "ChoiceBox".equals(type))) {
                String itemsVal = n.properties.get("items");
                if (itemsVal != null) {
                    try {
                        int idx = Integer.parseInt(selIdx);
                        String firstItem = itemsVal.split("\\|")[idx];
                        sb.append("        ").append(n.id).append(".setValue(")
                          .append(ConversionUtils.quoted(firstItem)).append(");\n");
                    } catch (Exception ignored) {}
                }
            }
            // ComboBox/ChoiceBox default value from FXML 'value' attribute (string, not index)
            String comboValStr = n.properties.get("value");
            if (comboValStr != null && !comboValStr.matches("[\\d.]+") && selIdx == null
                    && ("ComboBox".equals(type) || "ChoiceBox".equals(type))) {
                sb.append("        ").append(n.id).append(".setValue(")
                  .append(ConversionUtils.quoted(comboValStr)).append(");\n");
            }
            // TextArea preferred row count
            String prefRowCount = n.properties.get("prefRowCount");
            if (prefRowCount != null && "TextArea".equals(type))
                sb.append("        ").append(n.id).append(".setPrefRowCount(").append(prefRowCount).append(");\n");
            // HBox.setHgrow for nodes with explicit hgrow (e.g. Region spacers)
            String hgrowVal = n.properties.get("hgrow");
            if (hgrowVal != null && "HBox".equals(nodeParentType.get(n.id)))
                sb.append("        HBox.setHgrow(").append(n.id)
                  .append(", javafx.scene.layout.Priority.").append(hgrowVal).append(");\n");

            // Shape / Text fill and stroke (Paint properties - not CSS)
            String fillColor = n.properties.get("fill");
            if (fillColor != null) {
                String colorExpr = colorHexToJavaExpr(fillColor);
                sb.append("        ").append(n.id).append(".setFill(").append(colorExpr).append(");\n");
            }
            String strokeColor = n.properties.get("stroke");
            if (strokeColor != null) {
                String colorExpr = colorHexToJavaExpr(strokeColor);
                sb.append("        ").append(n.id).append(".setStroke(").append(colorExpr).append(");\n");
            }
            String strokeWidth = n.properties.get("strokeWidth");
            if (strokeWidth != null)
                sb.append("        ").append(n.id).append(".setStrokeWidth(").append(strokeWidth).append(");\n");
                
            String effectType = n.properties.get("effectType");
            if ("DropShadow".equals(effectType)) {
                sb.append("        javafx.scene.effect.DropShadow ").append(n.id).append("Effect = new javafx.scene.effect.DropShadow();\n");
                String r = n.properties.get("effectRadius");
                if (r != null) sb.append("        ").append(n.id).append("Effect.setRadius(").append(r).append(");\n");
                String c = n.properties.get("effectColor");
                if (c != null) sb.append("        ").append(n.id).append("Effect.setColor(").append(colorHexToJavaExpr(c)).append(");\n");
                String ox = n.properties.get("effectOffsetX");
                if (ox != null) sb.append("        ").append(n.id).append("Effect.setOffsetX(").append(ox).append(");\n");
                String oy = n.properties.get("effectOffsetY");
                if (oy != null) sb.append("        ").append(n.id).append("Effect.setOffsetY(").append(oy).append(");\n");
                String spread = n.properties.get("effectSpread");
                if (spread != null) sb.append("        ").append(n.id).append("Effect.setSpread(").append(spread).append(");\n");
                String blur = n.properties.get("effectBlurType");
                if (blur != null) sb.append("        ").append(n.id).append("Effect.setBlurType(javafx.scene.effect.BlurType.").append(blur).append(");\n");
                sb.append("        ").append(n.id).append(".setEffect(").append(n.id).append("Effect);\n");
            }
            if ("GaussianBlur".equals(effectType)) {
                String r = n.properties.getOrDefault("effectRadius", "10");
                sb.append("        javafx.scene.effect.GaussianBlur ").append(n.id).append("Effect = new javafx.scene.effect.GaussianBlur(").append(r).append(");\n");
                sb.append("        ").append(n.id).append(".setEffect(").append(n.id).append("Effect);\n");
            }

            // StackPane.setAlignment for children with explicit alignment constraint
            String spAlignVal = n.properties.get("stackPaneAlignment");
            if (spAlignVal != null && "StackPane".equals(nodeParentType.get(n.id)))
                sb.append("        StackPane.setAlignment(").append(n.id)
                  .append(", javafx.geometry.Pos.").append(spAlignVal).append(");\n");

            // Only emit absolute position for nodes NOT inside a managed layout container
            String parentType = nodeParentType.get(n.id);
            boolean inManagedLayout = parentType != null && managedLayoutTypes.contains(parentType);
            if (!inManagedLayout) {
                String lx = n.properties.get("layoutX"), ly = n.properties.get("layoutY");
                if (lx != null)
                    sb.append("        ").append(n.id).append(".setLayoutX(").append(lx).append(");\n");
                if (ly != null)
                    sb.append("        ").append(n.id).append(".setLayoutY(").append(ly).append(");\n");
            }
            String tx = n.properties.get("translateX");
            if (tx != null)
                sb.append("        ").append(n.id).append(".setTranslateX(").append(tx).append(");\n");
            String ty = n.properties.get("translateY");
            if (ty != null)
                sb.append("        ").append(n.id).append(".setTranslateY(").append(ty).append(");\n");
        }
        sb.append("\n");

        // ToggleGroups: declare and wire RadioButton/ToggleButton groups
        java.util.LinkedHashSet<String> toggleGroupIds = new java.util.LinkedHashSet<>();
        for (Node n : app.allNodes) {
            String tgId = n.properties.get("toggleGroupId");
            if (tgId != null) toggleGroupIds.add(tgId);
        }
        for (String tgId : toggleGroupIds)
            sb.append("        ToggleGroup ").append(tgId).append(" = new ToggleGroup();\n");
        for (Node n : app.allNodes) {
            String tgId = n.properties.get("toggleGroupId");
            if (tgId != null)
                sb.append("        ").append(n.id).append(".setToggleGroup(").append(tgId).append(");\n");
        }
        if (!toggleGroupIds.isEmpty()) sb.append("\n");

        // Filter roots: Menu-family nodes are not scene-graph Nodes and must not be
        // added to getChildren(). They get wired up through writeJfxTree → getItems().
        List<Node> visualRoots = new java.util.ArrayList<>();
        for (Node r : roots) {
            String rt = effectiveTypeMap.getOrDefault(r.id, r.type != null ? r.type : "");
            if (!"Menu".equals(rt) && !"MenuItem".equals(rt) && !"CheckMenuItem".equals(rt)
                    && !"RadioMenuItem".equals(rt) && !"SeparatorMenuItem".equals(rt)) {
                visualRoots.add(r);
            }
        }

        String rootVar;
        if (hasAbsPos) {
            rootVar = sceneRootVar;
            sb.append("        Pane ").append(sceneRootVar).append(" = new Pane();\n");
            emitRootStyle(sb, app);
            if (!visualRoots.isEmpty()) {
                for (Node root : visualRoots) {
                    if (root.type == null || "Tab".equals(root.type)) continue;
                    sb.append("        ").append(sceneRootVar).append(".getChildren().add(").append(root.id).append(");\n");
                    if (!root.children.isEmpty()) {
                        // Check for WinForms-style root: Pane containing MenuBar + main content
                        boolean isBorderPaneRoot = "Pane".equals(root.type)
                            && root.children.stream().anyMatch(c -> "MenuBar".equals(c.type))
                            && root.children.stream().anyMatch(c -> !"MenuBar".equals(c.type));
                        if (isBorderPaneRoot) {
                            for (Node child : root.children) {
                                if ("MenuBar".equals(child.type)) {
                                    sb.append("        ").append(root.id).append(".setTop(").append(child.id).append(");\n");
                                } else {
                                    sb.append("        ").append(root.id).append(".setCenter(").append(child.id).append(");\n");
                                }
                                writeJfxTree(sb, child);
                            }
                        } else {
                            writeJfxTree(sb, root);
                        }
                    }
                }
            } else {
                for (Node n : app.allNodes) {
                    if (n.type == null || "Tab".equals(n.type)) continue;
                    sb.append("        ").append(sceneRootVar).append(".getChildren().add(").append(n.id).append(");\n");
                }
            }
        } else if (visualRoots.size() == 1 && !visualRoots.get(0).children.isEmpty()) {
            Node rootNode = visualRoots.get(0);
            rootVar = rootNode.id;
            // Check for WinForms-style root: Pane containing MenuBar + main content → BorderPane
            boolean isBorderPaneRoot = "Pane".equals(rootNode.type)
                && rootNode.children.stream().anyMatch(c -> "MenuBar".equals(c.type))
                && rootNode.children.stream().anyMatch(c -> !"MenuBar".equals(c.type));
            if (isBorderPaneRoot) {
                for (Node child : rootNode.children) {
                    if ("MenuBar".equals(child.type)) {
                        sb.append("        ").append(rootNode.id).append(".setTop(").append(child.id).append(");\n");
                    } else {
                        sb.append("        ").append(rootNode.id).append(".setCenter(").append(child.id).append(");\n");
                    }
                    writeJfxTree(sb, child);
                }
            } else {
                writeJfxTree(sb, rootNode);
            }
        } else {
            rootVar = sceneRootVar;
            sb.append("        VBox ").append(sceneRootVar).append(" = new VBox();\n");
            emitRootStyle(sb, app);
            for (Node r : visualRoots) {
                sb.append("        ").append(sceneRootVar).append(".getChildren().add(").append(r.id).append(");\n");
                writeJfxTree(sb, r);
            }
        }

        // Build the Scene as a named local so we can attach stylesheets/etc. to it.
        if (!app.stylesheets.isEmpty()) {
            sb.append("\n        Scene scene = new Scene(").append(rootVar).append(", ")
              .append(app.sceneWidth).append(", ").append(app.sceneHeight).append(");\n");
            for (String ss : app.stylesheets) {
                if (ss.matches("^(data|http|https|file):.*")) {
                    sb.append("        scene.getStylesheets().add(")
                      .append(ConversionUtils.quoted(ss)).append(");\n");
                } else {
                    sb.append("        scene.getStylesheets().add(getClass().getResource(")
                      .append(ConversionUtils.quoted(ss)).append(").toExternalForm());\n");
                }
            }
            sb.append("        stage.setScene(scene);\n");
        } else {
            sb.append("\n        stage.setScene(new Scene(").append(rootVar).append(", ")
              .append(app.sceneWidth).append(", ").append(app.sceneHeight).append("));\n");
        }
        sb.append("        stage.setTitle(").append(ConversionUtils.quoted(app.title)).append(");\n");
        if (!app.resizable) sb.append("        stage.setResizable(false);\n");
        sb.append("        stage.show();\n");
        sb.append("    }\n\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        launch(args);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static String uniqueVarName(AppMetadata app, String base) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Node n : app.allNodes) {
            if (n.id != null) used.add(n.id);
        }
        String candidate = base;
        int suffix = 2;
        while (used.contains(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private static void writeJfxTree(StringBuilder sb, Node parent) {
        // BorderPane - route each child to its region setter (top/bottom/left/right/center)
        if ("BorderPane".equals(parent.type)) {
            for (Node child : parent.children) {
                String region = child.properties.getOrDefault("borderPaneRegion", "center");
                String setter = "set" + Character.toUpperCase(region.charAt(0)) + region.substring(1);
                sb.append("        ").append(parent.id).append(".").append(setter)
                  .append("(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
            }
            return;
        }
        // TreeView - first child TreeItem becomes the root
        if ("TreeView".equals(parent.type)) {
            if (!parent.children.isEmpty()) {
                Node rootItem = parent.children.get(0);
                sb.append("        ").append(parent.id).append(".setRoot(").append(rootItem.id).append(");\n");
                writeJfxTree(sb, rootItem);
            }
            return;
        }
        if ("TitledPane".equals(parent.type) && !parent.children.isEmpty()) {
            if (parent.children.size() == 1) {
                Node child = parent.children.get(0);
                sb.append("        ").append(parent.id)
                  .append(".setContent(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
            } else {
                String paneId = parent.id + "Content";
                sb.append("        Pane ").append(paneId).append(" = new Pane();\n");
                for (Node child : parent.children) {
                    if (child.id.equals(paneId)) {
                        for (Node gc : child.children) {
                            sb.append("        ").append(paneId)
                              .append(".getChildren().add(").append(gc.id).append(");\n");
                            writeJfxTree(sb, gc);
                        }
                        continue;
                    }
                    sb.append("        ").append(paneId)
                      .append(".getChildren().add(").append(child.id).append(");\n");
                    writeJfxTree(sb, child);
                }
                sb.append("        ").append(parent.id)
                  .append(".setContent(").append(paneId).append(");\n");
            }
            return;
        }
        if ("TabPane".equals(parent.type)) {
            for (Node child : parent.children) {
                sb.append("        ").append(parent.id)
                  .append(".getTabs().add(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
            }
            return;
        }
        if ("Tab".equals(parent.type) && !parent.children.isEmpty()) {
            if (parent.children.size() == 1) {
                Node child = parent.children.get(0);
                if ("Pane".equals(child.type) && child.children.size() == 1) {
                    // WinForms-style: bare Pane wrapper around content - keep ScrollPane for potential overflow
                    String spId = parent.id + "Scroll";
                    sb.append("        ScrollPane ").append(spId).append(" = new ScrollPane();\n");
                    sb.append("        ").append(spId).append(".setFitToWidth(true);\n");
                    Node layout = child.children.get(0);
                    if ("GridPane".equals(layout.type)) {
                        String lp = layout.properties.get("padding");
                        if (lp == null) sb.append("        ").append(layout.id).append(".setPadding(new Insets(10));\n");
                    }
                    sb.append("        ").append(spId).append(".setContent(").append(layout.id).append(");\n");
                    writeJfxTree(sb, layout);
                    sb.append("        ").append(parent.id).append(".setContent(").append(spId).append(");\n");
                } else {
                    // FXML-style: Tab content is a direct layout container - set it without ScrollPane
                    sb.append("        ").append(parent.id).append(".setContent(").append(child.id).append(");\n");
                    writeJfxTree(sb, child);
                }
            } else {
                // Multiple direct children (WinForms edge case) - wrap in VBox inside ScrollPane
                String spId = parent.id + "Scroll";
                String vboxId = parent.id + "Content";
                sb.append("        ScrollPane ").append(spId).append(" = new ScrollPane();\n");
                sb.append("        ").append(spId).append(".setFitToWidth(true);\n");
                sb.append("        VBox ").append(vboxId).append(" = new VBox(5);\n");
                sb.append("        ").append(vboxId).append(".setPadding(new Insets(10));\n");
                sb.append("        ").append(spId).append(".setContent(").append(vboxId).append(");\n");
                for (Node child : parent.children) {
                    if (child.id.equals(vboxId) || child.id.equals(spId)) {
                        for (Node gc : child.children) {
                            sb.append("        ").append(vboxId)
                              .append(".getChildren().add(").append(gc.id).append(");\n");
                            writeJfxTree(sb, gc);
                        }
                        continue;
                    }
                    sb.append("        ").append(vboxId)
                      .append(".getChildren().add(").append(child.id).append(");\n");
                    writeJfxTree(sb, child);
                }
                sb.append("        ").append(parent.id).append(".setContent(").append(spId).append(");\n");
            }
            return;
        }
        if ("SplitPane".equals(parent.type)) {
            // Group children by splitPanel property (Panel1 / Panel2)
            java.util.List<Node> panel1Children = new java.util.ArrayList<>();
            java.util.List<Node> panel2Children = new java.util.ArrayList<>();
            for (Node child : parent.children) {
                String panel = child.properties.get("splitPanel");
                if ("Panel2".equals(panel)) panel2Children.add(child);
                else panel1Children.add(child); // Panel1 or untagged
            }
            java.util.List<java.util.List<Node>> panels = new java.util.ArrayList<>();
            if (!panel1Children.isEmpty()) panels.add(panel1Children);
            if (!panel2Children.isEmpty()) panels.add(panel2Children);
            for (int pi = 0; pi < panels.size(); pi++) {
                java.util.List<Node> group = panels.get(pi);
                String paneId = parent.id + "Panel" + (pi + 1);
                sb.append("        Pane ").append(paneId).append(" = new Pane();\n");
                for (Node child : group) {
                    if (child.id.equals(paneId)) {
                        for (Node gc : child.children) {
                            sb.append("        ").append(paneId)
                              .append(".getChildren().add(").append(gc.id).append(");\n");
                            writeJfxTree(sb, gc);
                        }
                        continue;
                    }
                    sb.append("        ").append(paneId)
                      .append(".getChildren().add(").append(child.id).append(");\n");
                    writeJfxTree(sb, child);
                }
                sb.append("        ").append(parent.id)
                  .append(".getItems().add(").append(paneId).append(");\n");
            }
            return;
        }
        if ("GridPane".equals(parent.type)) {
            // Determine max column index to create ColumnConstraints
            int maxCol = 0;
            for (Node child : parent.children) {
                try { maxCol = Math.max(maxCol, Integer.parseInt(child.properties.getOrDefault("gridCol","0"))); }
                catch (NumberFormatException ignored) {}
            }
            // Emit vgap/hgap only when explicitly stored (from parsed source); cell-level
            // margins (GridPane.setMargin) handle WinForms-derived spacing instead.
            String vgapVal = parent.properties.get("vgap");
            if (vgapVal != null) {
                sb.append("        ").append(parent.id).append(".setVgap(").append(vgapVal).append(");\n");
            }
            // Detect if any direct or wrapper child contains a fixed-size data view.
            // If so, columns must NOT stretch - otherwise ListView/TreeView/etc. are
            // pushed to opposite ends of the window.
            boolean hasDataViews = parent.children.stream().anyMatch(child ->
                isDataViewType(child.type)
                || child.children.stream().anyMatch(gc -> isDataViewType(gc.type)));

            // Add column constraints so controls stretch properly
            String hgapVal = parent.properties.get("hgap");
            if (hgapVal != null) {
                sb.append("        ").append(parent.id).append(".setHgap(").append(hgapVal).append(");\n");
            }
            // Only generate auto column constraints for WinForms-derived GridPanes.
            // FxmlAdapter always sets hasColumnConstraints (true/false); its absence means WinForms.
            // When false, the source FXML had no <columnConstraints> - don't add any.
            if ("false".equals(parent.properties.get("hasColumnConstraints"))) {
                // No column constraints in original FXML - leave GridPane using natural sizing
            } else if (!parent.properties.containsKey("hasColumnConstraints")) {
            // WinForms source: apply heuristic column constraints
            for (int ci = 0; ci <= maxCol; ci++) {
                String ccId = parent.id + "Cc" + ci;
                sb.append("        ColumnConstraints ").append(ccId).append(" = new ColumnConstraints();\n");
                if (maxCol == 0) {
                    // Single column: grow to fill
                    sb.append("        ").append(ccId).append(".setHgrow(javafx.scene.layout.Priority.ALWAYS);\n");
                } else if (hasDataViews) {
                    // Fixed-size data-view columns: don't stretch
                    sb.append("        ").append(ccId).append(".setHgrow(javafx.scene.layout.Priority.NEVER);\n");
                } else if (maxCol == 1 && ci == 0) {
                    // 2-column label+input layout: first column (labels) auto-sizes, never grows
                    sb.append("        ").append(ccId).append(".setHgrow(javafx.scene.layout.Priority.NEVER);\n");
                } else if (maxCol == 1 && ci == 1) {
                    // 2-column label+input layout: second column (inputs) fills remaining space
                    sb.append("        ").append(ccId).append(".setHgrow(javafx.scene.layout.Priority.ALWAYS);\n");
                } else {
                    sb.append("        ").append(ccId).append(".setHgrow(javafx.scene.layout.Priority.SOMETIMES);\n");
                }
                sb.append("        ").append(parent.id).append(".getColumnConstraints().add(").append(ccId).append(");\n");
            }
            } // end WinForms column constraints block
            for (Node child : parent.children) {
                String col = child.properties.getOrDefault("gridCol", "0");
                String row = child.properties.getOrDefault("gridRow", "0");
                sb.append("        ").append(parent.id)
                  .append(".add(").append(child.id).append(", ")
                  .append(col).append(", ").append(row).append(");\n");
                // Translate WinForms Margin to GridPane.setMargin for faithful spacing
                String cm = child.properties.get("cellMargin");
                if (cm != null) {
                    sb.append("        GridPane.setMargin(").append(child.id)
                      .append(", new Insets(").append(cm).append("));\n");
                }
                // ScrollBar should not stretch to fill the cell - keep its natural pref size
                if ("ScrollBar".equals(child.type)) {
                    sb.append("        GridPane.setFillHeight(").append(child.id).append(", false);\n");
                    sb.append("        GridPane.setFillWidth(").append(child.id).append(", false);\n");
                }
                // Controls with explicit width (like TextField/TextArea) get capped maxWidth
                String cw = child.properties.get("width");
                if (cw != null && !isThin(cw) && ("TextField".equals(child.type) || "PasswordField".equals(child.type)
                        || "TextArea".equals(child.type) || "Spinner".equals(child.type))) {
                    sb.append("        ").append(child.id).append(".setMaxWidth(").append(cw).append(");\n");
                }
                writeJfxTree(sb, child);
            }
            return;
        }
        // VBox (from accordion Pane whose children are all TitledPanes)
        if ("VBox".equals(parent.type) && !parent.children.isEmpty()
                && parent.children.stream().allMatch(c -> "TitledPane".equals(c.type))) {
            sb.append("        ").append(parent.id).append(".setSpacing(2);\n");
            for (Node child : parent.children) {
                sb.append("        ").append(child.id).append(".setExpanded(true);\n");
                sb.append("        ").append(parent.id)
                  .append(".getChildren().add(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
            }
            return;
        }
        // Separator - has no children; set orientation & max-size depending on thin dimension
        // Also handle Pane nodes used as separators (thin width or height, no children)
        boolean isSepPane = "Pane".equals(parent.type) && parent.children.isEmpty()
            && (isThin(parent.properties.get("height")) || isThin(parent.properties.get("width")));
        if ("Separator".equals(parent.type) || isSepPane) {
            String h = parent.properties.get("height");
            String w = parent.properties.get("width");
            if (isThin(h)) {
                // Horizontal separator - stretch to fill width
                sb.append("        ").append(parent.id).append(".setMaxWidth(Double.MAX_VALUE);\n");
            } else if (isThin(w)) {
                // Vertical separator
                sb.append("        ").append(parent.id)
                  .append(".setOrientation(javafx.geometry.Orientation.VERTICAL);\n");
                if (h != null) sb.append("        ").append(parent.id).append(".setPrefHeight(").append(h).append(");\n");
            }
            return;
        }
        // Pane with WinForms DockStyle children → reconstruct as BorderPane layout
        boolean isDockLayoutPane = "Pane".equals(parent.type)
            && !parent.children.isEmpty()
            && parent.children.stream().anyMatch(c -> c.properties.get("wfDock") != null);
        if (isDockLayoutPane) {
            // Process in BorderPane-preferred order: Top, Bottom, Left, Right, Fill/Center
            String[] dockVals   = {"Top", "Bottom", "Left", "Right", "Fill"};
            String[] setterNames = {"setTop", "setBottom", "setLeft", "setRight", "setCenter"};
            for (int d = 0; d < dockVals.length; d++) {
                final String dockVal = dockVals[d];
                final String setter  = setterNames[d];
                for (Node child : parent.children) {
                    if (dockVal.equals(child.properties.get("wfDock"))) {
                        sb.append("        ").append(parent.id).append(".").append(setter)
                          .append("(").append(child.id).append(");\n");
                        writeJfxTree(sb, child);
                    }
                }
            }
            // Children without wfDock → center
            for (Node child : parent.children) {
                if (child.properties.get("wfDock") == null) {
                    sb.append("        ").append(parent.id)
                      .append(".setCenter(").append(child.id).append(");\n");
                    writeJfxTree(sb, child);
                }
            }
            return;
        }
        // ToolBar - children are added via getItems(), not getChildren()
        if ("ToolBar".equals(parent.type)) {
            for (Node child : parent.children) {
                if (child.type == null) continue; // skip items with no JavaFX equivalent
                sb.append("        ").append(parent.id)
                  .append(".getItems().add(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
            }
            return;
        }
        // MenuBar - top-level menus added via getMenus()
        if ("MenuBar".equals(parent.type)) {
            for (Node child : parent.children) {
                if (child.type == null) continue;
                sb.append("        ").append(parent.id)
                  .append(".getMenus().add(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
            }
            return;
        }
        // Pane whose children are all Menu behaves like a MenuBar
        if ("Pane".equals(parent.type) && !parent.children.isEmpty()
                && parent.children.stream().allMatch(c -> "Menu".equals(c.type))) {
            for (Node child : parent.children) {
                sb.append("        ").append(parent.id)
                  .append(".getMenus().add(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
            }
            return;
        }
        // Menu - sub-items added via getItems()
        if ("Menu".equals(parent.type)) {
            for (Node child : parent.children) {
                if (child.type == null) continue;
                sb.append("        ").append(parent.id)
                  .append(".getItems().add(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
            }
            return;
        }
        // ScrollPane - single content child set via setContent (no getChildren())
        if ("ScrollPane".equals(parent.type)) {
            for (Node child : parent.children) {
                if (child.type == null) continue;
                sb.append("        ").append(parent.id)
                  .append(".setContent(").append(child.id).append(");\n");
                writeJfxTree(sb, child);
                break; // ScrollPane only holds one content node
            }
            return;
        }
        for (Node child : parent.children) {
            sb.append("        ").append(parent.id)
              .append(".getChildren().add(").append(child.id).append(");\n");
            // Translate WinForms Margin to FlowPane.setMargin / HBox.setMargin for faithful spacing
            if ("FlowPane".equals(parent.type)) {
                String cm = child.properties.get("cellMargin");
                if (cm != null) {
                    // nowrap FlowPane is declared as HBox; use HBox.setMargin for static import compat
                    String marginClass = "true".equals(parent.properties.get("nowrap")) ? "HBox" : "FlowPane";
                    sb.append("        ").append(marginClass).append(".setMargin(").append(child.id)
                      .append(", new Insets(").append(cm).append("));\n");
                }
            }
            writeJfxTree(sb, child);
        }
    }

    /** Returns true if the type is a data-view control that carries a fixed preferred size. */
    private static boolean isDataViewType(String type) {
        return "ListView".equals(type) || "TreeView".equals(type)
            || "TableView".equals(type) || "TreeTableView".equals(type);
    }

    /** Returns true if the size string represents a thin separator dimension (≤ 3px). */
    private static boolean isThin(String val) {
        if (val == null) return false;
        try { return Integer.parseInt(val.trim()) <= 3; }
        catch (NumberFormatException e) { return false; }
    }

    private static void emitRootStyle(StringBuilder sb, AppMetadata app) {
        if (app.formBackColor == null && app.formBackgroundImage == null) return;
        StringBuilder css = new StringBuilder();
        if (app.formBackColor != null)
            css.append("-fx-background-color: ").append(app.formBackColor).append("; ");
        if (app.formBackgroundImage != null) {
            String imgFile = app.formBackgroundImage.replaceAll("[^\\w.]", "_") + ".png";
            css.append("-fx-background-image: url('assets/").append(imgFile).append("'); ");
            css.append("-fx-background-size: cover; ");
            css.append("-fx-background-repeat: no-repeat;");
        }
        sb.append("        root.setStyle(\"").append(css.toString().trim()).append("\");\n");
    }

    static void parseFxStyle(String css, Node node) {
        StringBuilder extra = new StringBuilder();
        for (String part : css.split(";")) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) continue;
            String[] kv = trimmedPart.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().toLowerCase();
            String val = kv[1].trim();
            // Skip declarations with empty values - they often come from
            // unresolved ternary/string-concat expressions in the source.
            if (val.isEmpty()) continue;
            switch (key) {
                case "-fx-background-color": node.addProperty("backColor", val); break;
                case "-fx-text-fill":        node.addProperty("foreColor", val); break;
                case "-fx-font-weight":
                    if (val.equalsIgnoreCase("bold") || val.matches("[6-9]\\d{2}"))
                        node.addProperty("fontWeight", "BOLD");
                    break;
                case "-fx-font-style":
                    if (val.equalsIgnoreCase("italic") || val.equalsIgnoreCase("oblique"))
                        node.addProperty("fontPosture", "ITALIC");
                    break;
                case "-fx-font-size":
                    node.addProperty("fontSize", val.replaceAll("[^\\d.]", ""));
                    if (val.trim().toLowerCase().endsWith("px"))
                        node.addProperty("fontSizeUnit", "px");
                    else
                        node.addProperty("fontSizeUnit", "pt");
                    break;
                case "-fx-font-family":
                    String rawFamily = val.replace("'", "").replace("\"", "");
                    // JavaFX CSS does not support font fallback lists; take only the first family
                    int commaIdx = rawFamily.indexOf(',');
                    node.addProperty("fontFamily", commaIdx >= 0
                        ? rawFamily.substring(0, commaIdx).trim()
                        : rawFamily.trim());
                    break;
                case "-fx-border-width":
                    node.addProperty("borderWidth", val.trim()); break;
                case "-fx-border-color":
                    node.addProperty("borderColor", val.trim()); break;
                case "-fx-padding":
                    node.addProperty("padding", val.trim()); break;
                case "-fx-opacity":
                    node.addProperty("opacity", val.trim()); break;
                case "-fx-background-radius":
                    node.addProperty("backgroundRadius", val.trim()); break;
                case "-fx-border-radius":
                    node.addProperty("borderRadius", val.trim()); break;
                case "-fx-border-style":
                    node.addProperty("borderStyle", val.trim()); break;
                case "-fx-pref-width":
                    if (!node.properties.containsKey("width"))
                        node.addProperty("width", val.replaceAll("[^\\d.]", "")); break;
                case "-fx-pref-height":
                    if (!node.properties.containsKey("height"))
                        node.addProperty("height", val.replaceAll("[^\\d.]", "")); break;
                case "-fx-min-width":
                    node.addProperty("minWidth", val.replaceAll("[^\\d.]", "")); break;
                case "-fx-min-height":
                    node.addProperty("minHeight", val.replaceAll("[^\\d.]", "")); break;
                case "-fx-max-width":
                    node.addProperty("maxWidth", val.replaceAll("[^\\d.]", "")); break;
                case "-fx-max-height":
                    node.addProperty("maxHeight", val.replaceAll("[^\\d.]", "")); break;
                case "-fx-rotate":
                    if (!val.isEmpty()) node.addProperty("rotate", val.replaceAll("[^\\-\\d.]", "")); break;
                case "-fx-scale-x":
                    if (!val.isEmpty()) node.addProperty("scaleX", val.replaceAll("[^\\-\\d.]", "")); break;
                case "-fx-scale-y":
                    if (!val.isEmpty()) node.addProperty("scaleY", val.replaceAll("[^\\-\\d.]", "")); break;
                case "-fx-background-image": {
                    // url('path/to/image') → store path
                    java.util.regex.Matcher imgM = java.util.regex.Pattern
                        .compile("url\\(['\"]?([^'\"\\)]+)['\"]?\\)").matcher(val);
                    if (imgM.find()) node.addProperty("imagePath", imgM.group(1));
                    break;
                }
                default:
                    if (!val.isEmpty()) // skip properties with empty/unresolved values
                        extra.append(key).append(": ").append(val).append("; ");
                    break;
            }
        }
        if (extra.length() > 0) {
            String existing = node.properties.get("extraCss");
            node.addProperty("extraCss", (existing != null ? existing : "") + extra.toString().trim());
        }
    }

    private static String resolveJfxColor(Expression expr) {
        return resolveJfxColor(expr, java.util.Collections.emptyMap());
    }

    private static String resolveJfxColor(Expression expr, Map<String, String> constants) {
        if (expr instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) expr;
            switch (mc.getNameAsString()) {
                case "valueOf": {
                    if (!mc.getArguments().isEmpty()) {
                        String val = stringValue(mc.getArgument(0), constants);
                        if (val != null) return val;
                    }
                    break;
                }
                case "web": {
                    if (!mc.getArguments().isEmpty()) {
                        String val = stringValue(mc.getArgument(0), constants);
                        if (val != null) {
                            String hex;
                            if (val.startsWith("#")) hex = val;
                            else {
                                String titled = val.isEmpty() ? val
                                    : val.substring(0, 1).toUpperCase() + val.substring(1).toLowerCase();
                                hex = ConversionUtils.NAMED_COLORS.get(titled);
                                if (hex == null) hex = val;
                            }
                            // Handle 2-arg Color.web(str, opacity)
                            if (mc.getArguments().size() >= 2) {
                                try {
                                    double alpha = Double.parseDouble(mc.getArgument(1).toString().trim());
                                    return hexToRgba(hex, alpha);
                                } catch (NumberFormatException ignored) {}
                            }
                            return hex;
                        }
                    }
                    break;
                }
                case "rgb": {
                    if (mc.getArguments().size() >= 3) {
                        try {
                            int r = Integer.parseInt(mc.getArgument(0).toString().trim());
                            int g = Integer.parseInt(mc.getArgument(1).toString().trim());
                            int b = Integer.parseInt(mc.getArgument(2).toString().trim());
                            // Handle 4-arg Color.rgb(r, g, b, opacity)
                            if (mc.getArguments().size() >= 4) {
                                double alpha = Double.parseDouble(mc.getArgument(3).toString().trim());
                                return "rgba(" + r + ", " + g + ", " + b + ", " + alpha + ")";
                            }
                            return ConversionUtils.rgbToHex(r, g, b);
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                }
                case "color": {
                    if (mc.getArguments().size() >= 3) {
                        try {
                            double r = Double.parseDouble(mc.getArgument(0).toString().trim());
                            double g = Double.parseDouble(mc.getArgument(1).toString().trim());
                            double b = Double.parseDouble(mc.getArgument(2).toString().trim());
                            return ConversionUtils.rgbToHex((int)(r * 255), (int)(g * 255), (int)(b * 255));
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                }
                default: break;
            }
        } else if (expr instanceof FieldAccessExpr) {
            String name = ((FieldAccessExpr) expr).getNameAsString();
            String titled = name.isEmpty() ? name
                : name.charAt(0) + name.substring(1).toLowerCase();
            String hex = ConversionUtils.NAMED_COLORS.get(titled);
            if (hex != null) return hex;
            return ConversionUtils.NAMED_COLORS.get(name);
        } else if (expr instanceof NameExpr) {
            String name = ((NameExpr) expr).getNameAsString();
            // Check field/local constants first (e.g. ACCENT_COLOR_1 = "#7c3aed")
            String constVal = constants.get(name);
            if (constVal != null) return constVal;
            String titled = name.isEmpty() ? name
                : name.charAt(0) + name.substring(1).toLowerCase();
            return ConversionUtils.NAMED_COLORS.getOrDefault(titled,
                ConversionUtils.NAMED_COLORS.get(name));
        } else {
            String val = stringValue(expr, constants);
            if (val != null && val.startsWith("#")) return val;
        }
        return null;
    }

    private static String stringValue(Expression expr) {
        return stringValue(expr, java.util.Collections.emptyMap());
    }

    /**
     * Converts a stored hex color (e.g. "#FFFFFF", "#4cc9f0") to a Java Color expression.
     * Uses Color.web("...") for arbitrary hex, or named Color constants for common colors.
     */
    private static String colorHexToJavaExpr(String hex) {
        // Try to find a named Color constant matching this hex value
        for (java.util.Map.Entry<String, String> e : ConversionUtils.NAMED_COLORS.entrySet()) {
            if (e.getValue().equalsIgnoreCase(hex)) {
                return "javafx.scene.paint.Color." + e.getKey().toUpperCase();
            }
        }
        return "javafx.scene.paint.Color.web(\"" + hex + "\")";
    }

    private static String stringValue(Expression expr, Map<String, String> constants) {
        if (expr instanceof StringLiteralExpr) return ((StringLiteralExpr) expr).asString();
        if (expr instanceof NameExpr) {
            String val = constants.get(((NameExpr) expr).getNameAsString());
            if (val != null) return val;
        }
        if (expr instanceof BinaryExpr) {
            BinaryExpr be = (BinaryExpr) expr;
            if (be.getOperator() == BinaryExpr.Operator.PLUS) {
                String l = stringValue(be.getLeft(),  constants);
                String r = stringValue(be.getRight(), constants);
                if (l != null && r != null) return l + r;
                if (l != null) return l; // partial: keep left if right is unresolvable
            }
        }
        return null;
    }

    /**
     * Evaluates a Java string expression (string concatenation with a loop counter variable).
     * Handles: "literal" + i, "literal" + (i * N), (i * N), bare i.
     */
    private static String evalStringExprWithCounter(String javaExpr, String loopVar, int counterVal) {
        // Remove outer quotes if it's a bare string literal
        if (javaExpr.startsWith("\"") && javaExpr.endsWith("\"") && javaExpr.length() >= 2
                && javaExpr.indexOf('"', 1) == javaExpr.length() - 1)
            return javaExpr.substring(1, javaExpr.length() - 1);
        // Step 1: replace (loopVar * N) with the computed int value
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(
            "\\(\\s*" + java.util.regex.Pattern.quote(loopVar) + "\\s*\\*\\s*(\\d+)\\s*\\)"
        ).matcher(javaExpr);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) m1.appendReplacement(sb1, String.valueOf(counterVal * Integer.parseInt(m1.group(1))));
        m1.appendTail(sb1);
        javaExpr = sb1.toString();
        // Step 2: replace standalone loopVar with counter value
        javaExpr = javaExpr.replaceAll("(?<![a-zA-Z_$\\d])" + java.util.regex.Pattern.quote(loopVar)
                + "(?![a-zA-Z_$\\d])", String.valueOf(counterVal));
        // Step 3: extract and concatenate all string literal and numeric tokens
        java.util.regex.Matcher tm = java.util.regex.Pattern.compile("\"([^\"]*)\"|([\\-\\d.]+)").matcher(javaExpr);
        StringBuilder result = new StringBuilder();
        while (tm.find()) {
            if (tm.group(1) != null) result.append(tm.group(1));
            else result.append(tm.group(2));
        }
        return result.length() > 0 ? result.toString() : null;
    }

    /**
     * Extract string items from an expression that is either:
     *   - a FXCollections.observableArrayList("a","b",...) call
     *   - a variable name whose items string is in listVarItems
     */
    private static java.util.List<String> extractListItems(Expression arg,
            Map<String, String> listVarItems) {
        java.util.List<String> items = new java.util.ArrayList<>();
        if (arg instanceof MethodCallExpr) {
            MethodCallExpr mce = (MethodCallExpr) arg;
            if ("observableArrayList".equals(mce.getNameAsString())) {
                for (Expression a : mce.getArguments()) {
                    String s = stringValue(a);
                    if (s != null) items.add(s);
                }
            }
        } else if (arg instanceof NameExpr) {
            String varName = ((NameExpr) arg).getNameAsString();
            String stored = listVarItems.get(varName);
            if (stored != null) items.addAll(java.util.Arrays.asList(stored.split("\\|")));
        }
        return items;
    }

    private static String resolveFactoryReturnType(CompilationUnit cu,
            String methodName, int paramCount) {
        return cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals(methodName)
                      && m.getParameters().size() == paramCount)
            .findFirst()
            .map(m -> {
                String ret = m.getType().asString();
                int lt = ret.indexOf('<');
                if (lt >= 0) ret = ret.substring(0, lt);
                return ret;
            })
            .orElse(null);
    }

    private static void resolveFactoryMethod(CompilationUnit cu, Node node,
            MethodCallExpr factoryCall) {
        resolveFactoryMethod(cu, node, factoryCall, java.util.Collections.emptyMap());
    }

    private static void resolveFactoryMethod(CompilationUnit cu, Node node,
            MethodCallExpr factoryCall, Map<String, String> fieldConstants) {
        String methodName = factoryCall.getNameAsString();
        List<Expression> callArgs = factoryCall.getArguments();
        Optional<MethodDeclaration> methodOpt = cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals(methodName)
                      && m.getBody().isPresent()
                      && m.getParameters().size() == callArgs.size())
            .findFirst();
        if (!methodOpt.isPresent()) return;

        MethodDeclaration method = methodOpt.get();
        Map<String, Expression> paramMap = new HashMap<>();
        List<Parameter> params = method.getParameters();
        for (int i = 0; i < params.size(); i++)
            paramMap.put(params.get(i).getNameAsString(), callArgs.get(i));

        BlockStmt mb = method.getBody().get();

        mb.findAll(ObjectCreationExpr.class).forEach(oce -> {
            if (!KNOWN_TYPES.contains(oce.getTypeAsString())) return;
            if (!oce.getArguments().isEmpty()) {
                String txt = resolveStringWithParams(oce.getArgument(0), paramMap, fieldConstants);
                if (txt != null && !node.properties.containsKey("text"))
                    node.addProperty("text", txt);
            }
        });

        mb.findAll(MethodCallExpr.class).forEach(mc -> {
            switch (mc.getNameAsString()) {
                case "setText":
                    if (mc.getArguments().size() >= 1 && !node.properties.containsKey("text")) {
                        String txt = resolveStringWithParams(mc.getArgument(0), paramMap, fieldConstants);
                        if (txt != null) node.addProperty("text", txt);
                    }
                    break;
                case "setPrefSize":
                    if (mc.getArguments().size() >= 2 && !node.properties.containsKey("width")) {
                        String w = resolveNumericWithParams(mc.getArgument(0), paramMap);
                        String h = resolveNumericWithParams(mc.getArgument(1), paramMap);
                        if (w != null) node.addProperty("width",  w);
                        if (h != null) node.addProperty("height", h);
                    }
                    break;
                case "setPrefWidth":
                    if (mc.getArguments().size() >= 1 && !node.properties.containsKey("width")) {
                        String w = resolveNumericWithParams(mc.getArgument(0), paramMap);
                        if (w != null) node.addProperty("width", w);
                    }
                    break;
                case "setPrefHeight":
                    if (mc.getArguments().size() >= 1 && !node.properties.containsKey("height")) {
                        String h = resolveNumericWithParams(mc.getArgument(0), paramMap);
                        if (h != null) node.addProperty("height", h);
                    }
                    break;
                case "setMaxWidth":
                    if (mc.getArguments().size() >= 1 && !node.properties.containsKey("maxWidth")) {
                        String w = resolveNumericWithParams(mc.getArgument(0), paramMap);
                        if (w != null) node.addProperty("maxWidth", w);
                    }
                    break;
                case "setSpacing":
                    if (mc.getArguments().size() >= 1) {
                        String s = resolveNumericWithParams(mc.getArgument(0), paramMap);
                        if (s != null) { node.addProperty("hgap", s); node.addProperty("vgap", s); }
                    }
                    break;
                case "setFont":
                    if (mc.getArguments().size() >= 1 && !node.properties.containsKey("fontFamily")) {
                        Expression fontArg = mc.getArgument(0);
                        if (fontArg instanceof MethodCallExpr) {
                            MethodCallExpr fc = (MethodCallExpr) fontArg;
                            if (fc.getNameAsString().equals("font")
                                    && !fc.getArguments().isEmpty()) {
                                String fam = resolveStringWithParams(fc.getArgument(0), paramMap, fieldConstants);
                                if (fam != null) node.addProperty("fontFamily", fam);
                                for (int i = fc.getArguments().size() - 1; i >= 0; i--) {
                                    String a = resolveNumericWithParams(fc.getArgument(i), paramMap);
                                    if (a != null) { node.addProperty("fontSize", a); break; }
                                }
                                node.addProperty("fontSizeUnit", "px"); // Font API = CSS pixels
                            }
                        }
                    }
                    break;
                case "setStyle":
                    if (mc.getArguments().size() >= 1) {
                        String style = resolveStringWithParams(mc.getArgument(0), paramMap, fieldConstants);
                        if (style != null) parseFxStyle(style, node);
                    }
                    break;
                case "setTextFill":
                    if (mc.getArguments().size() >= 1) {
                        // Build a merged constants map: fieldConstants + paramMap string values
                        Map<String, String> merged = new LinkedHashMap<>(fieldConstants);
                        for (Map.Entry<String, Expression> e : paramMap.entrySet()) {
                            String sv = stringValue(e.getValue(), fieldConstants);
                            if (sv != null) merged.put(e.getKey(), sv);
                        }
                        String colorHex = resolveJfxColor(mc.getArgument(0), merged);
                        if (colorHex != null) node.addProperty("foreColor", colorHex);
                    }
                    break;
                case "setAlignment":
                    if (mc.getArguments().size() >= 1) {
                        String arg = mc.getArgument(0).toString();
                        String posKey = arg.contains(".") ? arg.substring(arg.lastIndexOf('.') + 1) : arg;
                        String align = POS_TO_ALIGN.getOrDefault(posKey, null);
                        if (align != null) node.addProperty("textAlign", align);
                    }
                    break;
                case "setVisible":
                    if (mc.getArguments().size() >= 1
                            && "false".equals(mc.getArgument(0).toString()))
                        node.addProperty("visible", "false");
                    break;
                case "setDisable":
                    if (mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString()))
                        node.addProperty("enabled", "false");
                    break;
                case "setEditable":
                    if (mc.getArguments().size() >= 1
                            && "false".equals(mc.getArgument(0).toString()))
                        node.addProperty("editable", "false");
                    break;
                case "setWrapText":
                    if (mc.getArguments().size() >= 1
                            && "true".equals(mc.getArgument(0).toString()))
                        node.addProperty("wrapText", "true");
                    break;
                case "setOnAction":
                    node.addProperty("hasClick", "true");
                    break;
                default:
                    break;
            }
        });
    }

    /**
     * Tries to statically resolve a boolean ternary condition given a paramMap.
     * Returns true/false if deterministic, or null if the condition cannot be resolved.
     */
    private static Boolean resolveBooleanCondition(Expression condition,
            Map<String, Expression> paramMap) {
        return resolveBooleanCondition(condition, paramMap, java.util.Collections.emptyMap());
    }

    private static Boolean resolveBooleanCondition(Expression condition,
            Map<String, Expression> paramMap, Map<String, String> constants) {
        if (condition instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr)
            return ((com.github.javaparser.ast.expr.BooleanLiteralExpr) condition).getValue();
        if (condition instanceof NameExpr) {
            Expression sub = paramMap.get(((NameExpr) condition).getNameAsString());
            if (sub instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr)
                return ((com.github.javaparser.ast.expr.BooleanLiteralExpr) sub).getValue();
            if (sub != null) {
                String s = sub.toString().trim();
                if ("true".equals(s)) return true;
                if ("false".equals(s)) return false;
            }
        }
        if (condition instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) condition;
            String mName = mc.getNameAsString();
            if (mc.getScope().isPresent() && !mc.getArguments().isEmpty()) {
                String subject = resolveStringWithParams(mc.getScope().get(), paramMap, constants);
                if (subject != null) {
                    String arg0 = resolveStringWithParams(mc.getArgument(0), paramMap, constants);
                    if (arg0 != null) {
                        if ("startsWith".equals(mName)) return subject.startsWith(arg0);
                        if ("endsWith".equals(mName))   return subject.endsWith(arg0);
                        if ("equals".equals(mName))     return subject.equals(arg0);
                        if ("contains".equals(mName))   return subject.contains(arg0);
                    }
                }
            }
        }
        return null;
    }

    private static String resolveStringWithParams(Expression expr,
            Map<String, Expression> paramMap) {
        return resolveStringWithParams(expr, paramMap, java.util.Collections.emptyMap());
    }

    private static String resolveStringWithParams(Expression expr,
            Map<String, Expression> paramMap, Map<String, String> constants) {
        if (expr instanceof StringLiteralExpr) return ((StringLiteralExpr) expr).asString();
        if (expr instanceof NameExpr) {
            String name = ((NameExpr) expr).getNameAsString();
            // Check string constants first (local vars, field constants)
            String cv = constants.get(name);
            if (cv != null) return cv;
            Expression sub = paramMap.get(name);
            if (sub != null) return resolveStringWithParams(sub, paramMap, constants);
        }
        if (expr instanceof ConditionalExpr) {
            ConditionalExpr ce = (ConditionalExpr) expr;
            Boolean condResult = resolveBooleanCondition(ce.getCondition(), paramMap, constants);
            if (condResult != null)
                return resolveStringWithParams(
                    condResult ? ce.getThenExpr() : ce.getElseExpr(), paramMap, constants);
            // Unknown condition: try then, else
            String thenVal = resolveStringWithParams(ce.getThenExpr(), paramMap, constants);
            if (thenVal != null) return thenVal;
            return resolveStringWithParams(ce.getElseExpr(), paramMap, constants);
        }
        if (expr instanceof BinaryExpr) {
            BinaryExpr be = (BinaryExpr) expr;
            if (be.getOperator() == BinaryExpr.Operator.PLUS) {
                String l = resolveStringWithParams(be.getLeft(),  paramMap, constants);
                String r = resolveStringWithParams(be.getRight(), paramMap, constants);
                if (l != null && r != null) return l + r;
                if (l != null) return l;
            }
        }
        return null;
    }

    private static String resolveNumericWithParams(Expression expr,
            Map<String, Expression> paramMap) {
        String s = expr.toString();
        if (s.matches("[\\d.]+")) return s;
        if (expr instanceof NameExpr) {
            Expression sub = paramMap.get(((NameExpr) expr).getNameAsString());
            if (sub != null) return resolveNumericWithParams(sub, paramMap);
        }
        return null;
    }

    /** Converts a hex color string + alpha (0.0–1.0) to CSS rgba(...). */
    private static String hexToRgba(String hex, double alpha) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            // Round alpha to max 2 decimal places
            String a = String.format(java.util.Locale.US, "%.2f", alpha).replaceAll("\\.?0+$", "");
            if (a.isEmpty() || a.equals("0")) a = "0";
            return "rgba(" + r + ", " + g + ", " + b + ", " + a + ")";
        } catch (Exception ignored) {
            return hex;
        }
    }

    // ── FXML Parsing ──────────────────────────────────────────────────────────

    /**
     * Parses JavaFX FXML (XML format) into an AppMetadata IR.
     * Uses the JDK built-in XML parser - no extra dependencies.
     */
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

        Map<String, int[]> idCounters = new java.util.HashMap<>();
        org.w3c.dom.Element root = doc.getDocumentElement();

        // Read scene size from root element prefWidth/prefHeight
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

        Node rootNode = parseFxmlElement(root, app, idCounters, null);
        if (rootNode != null) app.sceneRootId = rootNode.id;
        return app;
    }

    private Node parseFxmlElement(org.w3c.dom.Element el, AppMetadata app,
                                  Map<String, int[]> idCounters, String parentBpRegion) {
        // Resolve tag name (strip any XML namespace prefix)
        String tagName = el.getLocalName();
        if (tagName == null) tagName = el.getNodeName();
        int colon = tagName.indexOf(':');
        if (colon >= 0) tagName = tagName.substring(colon + 1);

        if (!KNOWN_TYPES.contains(tagName)) return null;

        // Determine node ID: prefer fx:id, else auto-generate a stable one
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

        // ── Map FXML attributes to IR properties ─────────────────────────────
        String text = el.getAttribute("text");
        if (!text.isEmpty()) node.addProperty("text", text);

        fxmlDimProp(el, "prefWidth",  node, "width");
        fxmlDimProp(el, "prefHeight", node, "height");
        fxmlDimProp(el, "layoutX",   node, "layoutX");
        fxmlDimProp(el, "layoutY",   node, "layoutY");

        // Gap: explicit hgap/vgap first, then spacing as fallback for missing ones
        fxmlDimProp(el, "hgap", node, "hgap");
        fxmlDimProp(el, "vgap", node, "vgap");
        String spacing = el.getAttribute("spacing");
        if (!spacing.isEmpty()) {
            if (!node.properties.containsKey("hgap")) node.addProperty("hgap", spacing);
            if (!node.properties.containsKey("vgap")) node.addProperty("vgap", spacing);
        }

        // Alignment (JavaFX Pos name → canonical ContentAlignment)
        String align = el.getAttribute("alignment");
        if (!align.isEmpty()) {
            String ca = POS_TO_ALIGN.get(align);
            if (ca != null) node.addProperty("textAlign", ca);
        }

        // Inline CSS style
        String style = el.getAttribute("style");
        if (!style.isEmpty()) parseFxStyle(style, node);

        // Boolean state attributes
        if ("false".equals(el.getAttribute("visible")))  node.addProperty("visible",  "false");
        if ("true".equals(el.getAttribute("disable")))   node.addProperty("enabled",  "false");
        if ("false".equals(el.getAttribute("editable"))) node.addProperty("editable", "false");
        if ("true".equals(el.getAttribute("wrapText")))  node.addProperty("wrapText", "true");

        // Range / progress controls
        fxmlDimProp(el, "min",      node, "min");
        fxmlDimProp(el, "max",      node, "max");
        fxmlDimProp(el, "value",    node, "value");
        fxmlDimProp(el, "progress", node, "progress");

        // ── Recurse into child elements ──────────────────────────────────────
        org.w3c.dom.NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node childDom = children.item(i);
            if (childDom.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element childEl = (org.w3c.dom.Element) childDom;

            String childTag = childEl.getLocalName();
            if (childTag == null) childTag = childEl.getNodeName();
            int c = childTag.indexOf(':');
            if (c >= 0) childTag = childTag.substring(c + 1);

            // BorderPane region wrapper elements: <top>, <bottom>, <left>, <right>, <center>
            if (java.util.Arrays.asList("top","bottom","left","right","center")
                    .contains(childTag)) {
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

            // Property wrapper elements like <VBox.children>, <GridPane.columnConstraints>
            if (childTag.contains(".")) {
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
        return node;
    }

    /** Sets a Node property from an FXML attribute, skipping if the property is already set. */
    private static void fxmlDimProp(org.w3c.dom.Element el, String attr,
                                    Node node, String propKey) {
        String val = el.getAttribute(attr);
        if (!val.isEmpty() && !node.properties.containsKey(propKey))
            node.addProperty(propKey, val);
    }
}

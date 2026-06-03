package com.jabcodex.uiporter.model;

import java.util.*;

public class AppMetadata {
    public ArrayList<Node> allNodes = new ArrayList<>();
    public HashSet<String> childIds = new HashSet<>();
    public String title = "App";
    public String sceneRootId = null;
    public int sceneWidth = 800;
    public int sceneHeight = 600;
    public boolean resizable = true;
    public String formBackColor = null;
    public String formBackgroundImage = null;

    /** The class name of the JavaFX source (e.g. "ConvertedApp"), used as WinForms form class name. */
    public String sourceClassName = null;

    /** fx:controller attribute value from root FXML element (e.g. "com.example.MyController"). */
    public String fxController = null;

    /** Stylesheet URLs collected from &lt;stylesheets&gt; in FXML or getStylesheets().add() in Java. */
    public List<String> stylesheets = new ArrayList<>();

    /** Set by an adapter's parse() when the source cannot be parsed. */
    public boolean parseError = false;
    public String parseErrorMessage = null;
}

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

    public String sourceClassName = null;

    public String fxController = null;

    public List<String> stylesheets = new ArrayList<>();

    public boolean parseError = false;
    public String parseErrorMessage = null;
}

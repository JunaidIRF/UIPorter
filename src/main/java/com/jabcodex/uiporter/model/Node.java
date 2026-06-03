package com.jabcodex.uiporter.model;

import java.util.*;

public class Node {
    public String id;
    public String type;
    public Map<String, String> properties = new HashMap<>();
    public ArrayList<Node> children = new ArrayList<>();

    public void addProperty(String key, String value) {
        properties.put(key, value);
    }
}

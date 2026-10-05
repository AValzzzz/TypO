package com.example.model.help;

import java.util.List;

public class HelpNode {
    public String id;
    public String type;
    public String yes;
    public String no;
    public String skip;
    public List<HelpStep> demo;

    public boolean isLeaf() {
        return "leaf".equals(type);
    }
}
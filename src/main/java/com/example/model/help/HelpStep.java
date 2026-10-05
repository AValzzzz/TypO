package com.example.model.help;

public class HelpStep {
    public String type;
    public String kind;

    public String text;
    public String keys;
    public String fn;
    public String align;
    public String front;

    public int cap = -1;
    public int[] caps;
    public int[] shade;
    public int[] merge;
    public double[] w;

    public int hl = -1;
    public int ms = 0;
    public int rows = 0;
    public int cols = 0;
    public int count = 1;
    public int size = 0;

    public Double x, y, dx, dy, scale, angle;

    public boolean par;
    public boolean handles;
    public boolean curve;
    public boolean selected;
    public boolean deselect;
    public boolean landscape;

    public String[] args;
    public String[] lines;
    public String[] output;
    public String[] add;
    public String[] styles;
}
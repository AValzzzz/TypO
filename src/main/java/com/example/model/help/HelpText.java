package com.example.model.help;

import java.util.List;
import java.util.Map;

public class HelpText {
    public Map<String, String> ui;
    public List<String> stopwords;
    public Map<String, NodeText> nodes;

    public static class NodeText {
        public String question;
        public String title;
        public List<String> body;
        public List<String> keywords;
        public List<String> captions;
    }
}
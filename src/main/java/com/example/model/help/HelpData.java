package com.example.model.help;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public final class HelpData {
    private static final String BASE = "/com/example/help/";
    private static HelpTree cachedTree;

    private final HelpTree tree;
    private final HelpText text;
    private final Map<String, HelpNode> index = new HashMap<>();
    private final HelpSearch search;

    private HelpData(HelpTree tree, HelpText text) {
        this.tree = tree;
        this.text = text;
        List<String> leafIds = new ArrayList<>();
        for (HelpNode n : tree.nodes) {
            index.put(n.id, n);
            if (n.isLeaf())
                leafIds.add(n.id);
        }
        this.search = new HelpSearch(text, leafIds);
    }

    public static HelpData load(String lang) throws IOException {
        Gson gson = new Gson();
        synchronized (HelpData.class) {
            if (cachedTree == null)
                cachedTree = read(gson, "help_tree.json", HelpTree.class);
        }
        HelpText t = read(gson, "help_" + lang + ".json", HelpText.class);
        return new HelpData(cachedTree, t);
    }

    private static <T> T read(Gson gson, String name, Class<T> type) throws IOException {
        try (InputStream in = HelpData.class.getResourceAsStream(BASE + name)) {
            if (in == null)
                throw new IOException("Missing resource: " + name);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                T value = gson.fromJson(reader, type);
                if (value == null)
                    throw new IOException("Empty resource: " + name);
                return value;
            }
        } catch (JsonParseException e) {
            throw new IOException("Invalid " + name + ": " + e.getMessage(), e);
        }
    }

    public String start() {
        return tree.start;
    }

    public HelpNode node(String id) {
        return id == null ? null : index.get(id);
    }

    public String ui(String key) {
        String v = text.ui == null ? null : text.ui.get(key);
        return v != null ? v : key;
    }

    private HelpText.NodeText nodeText(String id) {
        return text.nodes == null ? null : text.nodes.get(id);
    }

    public String question(String id) {
        HelpText.NodeText n = nodeText(id);
        return n != null && n.question != null ? n.question : id;
    }

    public String title(String id) {
        HelpText.NodeText n = nodeText(id);
        return n != null && n.title != null ? n.title : id;
    }

    public List<String> body(String id) {
        HelpText.NodeText n = nodeText(id);
        return n != null && n.body != null ? n.body : List.of();
    }

    public String caption(String id, int i) {
        HelpText.NodeText n = nodeText(id);
        if (n == null || n.captions == null || i < 0 || i >= n.captions.size())
            return "";
        return n.captions.get(i);
    }

    public List<HelpSearch.Hit> search(String query) {
        return search.search(query);
    }
}
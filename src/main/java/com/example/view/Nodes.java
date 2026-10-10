package com.example.view;

import javafx.scene.Node;

public final class Nodes {
    private Nodes() {
    }

    public static <T> T ancestor(Object target, Class<T> type) {
        for (Node n = target instanceof Node node ? node : null; n != null; n = n.getParent())
            if (type.isInstance(n))
                return type.cast(n);
        return null;
    }

    public static boolean isWithin(Object target, Node owner) {
        for (Node n = target instanceof Node node ? node : null; n != null; n = n.getParent())
            if (n == owner)
                return true;
        return false;
    }
}

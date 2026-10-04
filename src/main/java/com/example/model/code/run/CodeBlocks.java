package com.example.model.code.run;

import java.util.ArrayList;
import java.util.List;

import com.example.view.RichTextArea;

public final class CodeBlocks {
    public record Block(int open, int close) {
    }

    private CodeBlocks() {
    }

    public static List<Block> find(RichTextArea editor) {
        List<Block> blocks = new ArrayList<>();
        Integer open = null;
        for (int i = 0; i < editor.getParagraphs().size(); i++) {
            if (!editor.getParagraph(i).getText().trim().startsWith("```"))
                continue;
            if (open == null)
                open = i;
            else {
                blocks.add(new Block(open, i));
                open = null;
            }
        }
        return blocks;
    }

    public static Block at(RichTextArea editor, int paragraph) {
        for (Block b : find(editor))
            if (paragraph >= b.open() && paragraph <= b.close())
                return b;
        return null;
    }

    public static String tag(RichTextArea editor, Block block) {
        String line = editor.getParagraph(block.open()).getText().trim();
        String tag = line.length() > 3 ? line.substring(3).trim() : "";
        int end = 0;
        while (end < tag.length() && !Character.isWhitespace(tag.charAt(end)))
            end++;
        return tag.substring(0, end).toLowerCase();
    }

    public static String code(RichTextArea editor, Block block) {
        StringBuilder sb = new StringBuilder();
        for (int p = block.open() + 1; p < block.close(); p++) {
            if (p > block.open() + 1)
                sb.append('\n');
            sb.append(editor.getParagraph(p).getText());
        }
        return sb.toString();
    }
}

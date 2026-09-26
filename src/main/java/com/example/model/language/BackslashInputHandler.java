package com.example.model.language;

import java.util.Optional;

import com.example.view.RichTextArea;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class BackslashInputHandler {
    private final RichTextArea editor;
    private final CommandRegistry registry;

    private boolean buffering = false;
    private int commandStart = -1;

    private boolean justConverted = false;
    private int convertedStart = -1;
    private int convertedEnd = -1;
    private String convertedRawText = null;

    public BackslashInputHandler(RichTextArea editor, CommandRegistry registry) {
        this.editor = editor;
        this.registry = registry;
        editor.addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);
        editor.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    private void onKeyTyped(KeyEvent event) {
        String character = event.getCharacter();
        if (character == null || character.isEmpty())
            return;

        if (character.equals(KeyEvent.CHAR_UNDEFINED))
            character = "^";

        char c = character.charAt(0);

        if (Character.isISOControl(c)) {
            if (c!='\b')
                resetBuffering();
            return;
        }

        justConverted = false;

        int caret = editor.getCaretPosition();

        if (c == '\\') {
            if (buffering && caret == commandStart + 1) {
                event.consume();
                editor.replaceText(commandStart, caret, "\\");
                resetBuffering();
                return;
            }

            buffering = true;
            commandStart = caret;
            return;
        }

        if (c == ' ' && buffering) {
            String raw = editor.getText(commandStart, caret).substring(1);
            System.out.println("RAW COMMAND: [" + raw + "]");
            Optional<Command> match = registry.find(raw);
            System.out.println("MATCHED: " + match.map(m -> m.getClass().getSimpleName()).orElse("NONE")); 

            if (match.isPresent()) {
                CommandResult result = match.get().apply(raw);
                if (result.isMathObject()) {
                    editor.replaceText(commandStart, caret, "");
                    editor.insertMathObject(commandStart, result.getMathObject());
                    convertedStart = commandStart;
                    convertedRawText = "\\" + raw;
                    justConverted = true;
                    convertedEnd = commandStart + 1;
                } else {

                    String text = result.getText();
                    editor.replaceText(commandStart, caret, text);

                    if (result.hasStyle()) {
                        editor.applyStyle(commandStart + result.getStyleStart(), commandStart + result.getStyleEnd(),
                                result.getStyleChange());
                    }

                    convertedStart = commandStart;
                    convertedEnd = commandStart + text.length();
                    convertedRawText = "\\" + raw;
                    justConverted = true;
                }
            }

            resetBuffering();
            return;
        }
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.UNDEFINED
                && event.getText().isEmpty()
                && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
            event.consume();
            System.out.println("caret=" + editor.getCaretPosition() + " convertedEnd=" + convertedEnd + " justConverted=" + justConverted);
            editor.replaceSelection(event.isShiftDown() ? "¨" : "^");
            return;
        }

        if (event.getCode() != KeyCode.BACK_SPACE || !justConverted)
            return;

        if (editor.getCaretPosition() == convertedEnd) {
            event.consume();
            editor.replaceText(convertedStart, convertedEnd, convertedRawText);
            buffering = true;
            commandStart = convertedStart;
            justConverted = false;
        }
    }

    private void resetBuffering() {
        buffering = false;
        commandStart = -1;
    }
}

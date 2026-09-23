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

    public BackslashInputHandler (RichTextArea editor, CommandRegistry registry) {
        this.editor = editor;
        this.registry = registry;
        editor.addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);
        editor.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    private void onKeyTyped(KeyEvent event) {
        String character = event.getCharacter();
        if (character == null || character.isEmpty()) return;

        char c = character.charAt(0);

        if (Character.isISOControl(c)) {
            resetBuffering();
            return;
        }

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
            Optional<Command> match = registry.find(raw);

            if (match.isPresent()) {
                String placeholder = match.get().renderPlaceholder(raw);
                editor.replaceText(commandStart, caret, placeholder);

                convertedStart = commandStart;
                convertedEnd = commandStart + placeholder.length();
                convertedRawText = "\\" + raw;
                justConverted = true;
            }

            resetBuffering();
            return;
        }
    }

    private void onKeyPressed(KeyEvent event) {
        if(event.getCode() != KeyCode.BACK_SPACE || !justConverted) return;

        if(editor.getCaretPosition() == convertedEnd) {
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

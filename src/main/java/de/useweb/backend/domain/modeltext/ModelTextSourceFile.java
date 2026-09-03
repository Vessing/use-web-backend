package de.useweb.backend.domain.modeltext;

/** A source file bundled with the root USE model text. */
public record ModelTextSourceFile(String sourcePath, String text) {

    public ModelTextSourceFile {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
        text = text == null ? "" : text;
    }
}

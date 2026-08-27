package de.useweb.backend.domain.modeltext;

import java.time.Instant;

public record ModelText(
        String text,
        String language,
        String languageVersion,
        Instant updatedAt,
        String sourceName,
        String sourceOrigin) {

    public ModelText {
        text = text == null ? "" : text;
        language = language == null || language.isBlank() ? "USE_MODEL_TEXT" : language;
        languageVersion = languageVersion == null || languageVersion.isBlank() ? "mvp-subset" : languageVersion;
    }
}

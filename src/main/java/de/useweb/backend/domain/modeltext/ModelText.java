package de.useweb.backend.domain.modeltext;

import java.time.Instant;
import java.util.List;

public record ModelText(
        String text,
        String language,
        String languageVersion,
        Instant updatedAt,
        String sourceName,
        String sourceOrigin,
        List<ModelTextSourceProvenance> sources) {

    public ModelText(String text, String language, String languageVersion, Instant updatedAt,
            String sourceName, String sourceOrigin) {
        this(text, language, languageVersion, updatedAt, sourceName, sourceOrigin, List.of());
    }

    public ModelText {
        text = text == null ? "" : text;
        language = language == null || language.isBlank() ? "USE_MODEL_TEXT" : language;
        languageVersion = languageVersion == null || languageVersion.isBlank() ? "mvp-subset" : languageVersion;
        sources = List.copyOf(sources == null ? List.of() : sources);
    }
}

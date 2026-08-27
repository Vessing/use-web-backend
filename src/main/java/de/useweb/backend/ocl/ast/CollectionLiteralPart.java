package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public sealed interface CollectionLiteralPart permits CollectionItem, CollectionRangeItem {
    SourceRange sourceRange();
}

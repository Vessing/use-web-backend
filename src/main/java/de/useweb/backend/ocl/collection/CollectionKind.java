package de.useweb.backend.ocl.collection;

import java.util.Arrays;
import java.util.Optional;

public enum CollectionKind {
    COLLECTION("Collection", false, true),
    SET("Set", false, false),
    BAG("Bag", false, true),
    SEQUENCE("Sequence", true, true),
    ORDERED_SET("OrderedSet", true, false);

    private final String oclName;
    private final boolean ordered;
    private final boolean duplicatesAllowed;

    CollectionKind(String oclName, boolean ordered, boolean duplicatesAllowed) {
        this.oclName = oclName;
        this.ordered = ordered;
        this.duplicatesAllowed = duplicatesAllowed;
    }

    public String oclName() {
        return oclName;
    }

    public boolean ordered() {
        return ordered;
    }

    public boolean duplicatesAllowed() {
        return duplicatesAllowed;
    }

    public static Optional<CollectionKind> fromOclName(String name) {
        return Arrays.stream(values()).filter(kind -> kind != COLLECTION && kind.oclName.equals(name)).findFirst();
    }
}

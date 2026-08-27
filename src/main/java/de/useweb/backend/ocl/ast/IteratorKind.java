package de.useweb.backend.ocl.ast;

import java.util.Arrays;
import java.util.Optional;

public enum IteratorKind {
    FOR_ALL("forAll"),
    EXISTS("exists"),
    SELECT("select"),
    REJECT("reject"),
    COLLECT("collect"),
    COLLECT_NESTED("collectNested"),
    ANY("any"),
    ONE("one"),
    IS_UNIQUE("isUnique"),
    SORTED_BY("sortedBy"),
    CLOSURE("closure");

    private final String oclName;

    IteratorKind(String oclName) {
        this.oclName = oclName;
    }

    public String oclName() {
        return oclName;
    }

    public static Optional<IteratorKind> fromOclName(String name) {
        return Arrays.stream(values()).filter(kind -> kind.oclName.equals(name)).findFirst();
    }
}

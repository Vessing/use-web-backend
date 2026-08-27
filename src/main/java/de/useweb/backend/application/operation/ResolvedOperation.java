package de.useweb.backend.application.operation;

import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlOperation;

public record ResolvedOperation(UmlClass requestedOwner, UmlOperation requested, UmlClass owner, UmlOperation operation) {}

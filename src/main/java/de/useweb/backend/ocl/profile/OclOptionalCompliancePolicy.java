package de.useweb.backend.ocl.profile;

import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlVisibility;

/** Single source of truth for optional OCL 2.4 evaluation compliance points. */
public final class OclOptionalCompliancePolicy {
    public static final boolean NON_NAVIGABLE_ASSOCIATION_ACCESS = false;
    public static final boolean BYPASS_NON_PUBLIC_FEATURE_VISIBILITY = false;

    private OclOptionalCompliancePolicy() {
    }

    public static boolean mayNavigate(UmlAssociationEnd end) {
        return end.navigable() || NON_NAVIGABLE_ASSOCIATION_ACCESS;
    }

    public static boolean mayAccess(UmlModel model, UmlVisibility visibility, UmlClass owner,
            UmlClass accessingClass) {
        return BYPASS_NON_PUBLIC_FEATURE_VISIBILITY || model.isVisible(visibility, owner, accessingClass);
    }
}

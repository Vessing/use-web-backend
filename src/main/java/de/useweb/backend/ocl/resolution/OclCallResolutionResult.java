package de.useweb.backend.ocl.resolution;

import java.util.Optional;

public record OclCallResolutionResult(Status status, OclCallResolution resolution, String message) {
    public enum Status {
        RESOLVED,
        UNKNOWN,
        INACCESSIBLE,
        ARGUMENT_MISMATCH,
        AMBIGUOUS
    }

    public OclCallResolutionResult {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if ((status == Status.RESOLVED) != (resolution != null)) {
            throw new IllegalArgumentException("Only RESOLVED results contain a resolution");
        }
        message = message == null ? "" : message;
    }

    public static OclCallResolutionResult resolved(OclCallResolution resolution) {
        return new OclCallResolutionResult(Status.RESOLVED, resolution, "");
    }

    public static OclCallResolutionResult unresolved(Status status, String message) {
        return new OclCallResolutionResult(status, null, message);
    }

    public Optional<OclCallResolution> resolvedCall() {
        return Optional.ofNullable(resolution);
    }
}

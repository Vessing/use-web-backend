package de.useweb.backend.api.dto.ocl;

import java.util.List;
import java.util.Map;

public record OclComplianceProfileDto(
        String profileId,
        String oclVersion,
        String complianceClaim,
        String apiVersion,
        List<String> enabledOptionalCompliancePoints,
        List<OclFeatureSupportDto> features,
        Map<String, Long> runtimeLimits) {

    public record OclFeatureSupportDto(
            String id,
            String group,
            String status,
            String standardBasis,
            String notes) {
    }
}

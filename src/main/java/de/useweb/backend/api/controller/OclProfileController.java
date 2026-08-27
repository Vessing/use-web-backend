package de.useweb.backend.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.useweb.backend.api.dto.ocl.OclComplianceProfileDto;
import de.useweb.backend.api.dto.ocl.OclComplianceProfileDto.OclFeatureSupportDto;
import de.useweb.backend.ocl.profile.OclComplianceProfile;

@RestController
@RequestMapping("/api/v1/ocl")
public class OclProfileController {

    @GetMapping("/profile")
    public OclComplianceProfileDto profile() {
        OclComplianceProfile profile = OclComplianceProfile.current();
        return new OclComplianceProfileDto(
                profile.profileId(),
                profile.oclVersion(),
                profile.complianceClaim(),
                profile.apiVersion(),
                profile.enabledOptionalCompliancePoints(),
                profile.features().stream()
                        .map(feature -> new OclFeatureSupportDto(
                                feature.id(), feature.group(), feature.status().name(),
                                feature.standardBasis(), feature.notes()))
                        .toList(),
                profile.runtimeLimits());
    }
}

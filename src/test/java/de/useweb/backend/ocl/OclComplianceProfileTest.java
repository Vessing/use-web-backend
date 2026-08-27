package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.useweb.backend.ocl.profile.OclComplianceProfile;
import de.useweb.backend.ocl.profile.OclFeatureStatus;
import de.useweb.backend.ocl.profile.OclOptionalCompliancePolicy;

class OclComplianceProfileTest {

    @Test
    void publishesAUniqueAndExplicitOcl24SubsetMatrix() {
        OclComplianceProfile profile = OclComplianceProfile.current();

        assertThat(profile.profileId()).isEqualTo("use-web-ocl-2.4-subset-v3");
        assertThat(profile.oclVersion()).isEqualTo("2.4");
        assertThat(profile.complianceClaim()).contains("subset").contains("no full");
        assertThat(profile.features()).extracting(feature -> feature.id()).doesNotHaveDuplicates();
        assertThat(profile.features()).allSatisfy(feature -> {
            assertThat(feature.group()).isNotBlank();
            assertThat(feature.standardBasis()).isNotBlank();
            assertThat(feature.notes()).isNotBlank();
        });
        assertThat(profile.features()).extracting(feature -> feature.status())
                .contains(OclFeatureStatus.SUPPORTED, OclFeatureStatus.PARTIAL,
                        OclFeatureStatus.NOT_SUPPORTED, OclFeatureStatus.OUT_OF_SCOPE);
    }

    @Test
    void publishesOnlyFullyAcceptedFeatureGroupsAsSupported() {
        OclComplianceProfile profile = OclComplianceProfile.current();

        assertThat(profile.features()).filteredOn(feature -> feature.id().equals("OCL-PROFILE-001")
                        || feature.id().equals("OCL-PROFILE-007") || feature.id().equals("OCL-PROFILE-009")
                        || feature.id().equals("OCL-PROFILE-011"))
                .allSatisfy(feature -> assertThat(feature.status()).isEqualTo(OclFeatureStatus.PARTIAL));
    }

    @Test
    void publishesTheEvaluatorBindingLimitFromTheRuntimeProfile() {
        assertThat(OclComplianceProfile.current().runtimeLimits())
                .containsEntry("maxIteratorBindings", OclComplianceProfile.MAX_ITERATOR_BINDINGS)
                .containsEntry("maxTokens", OclComplianceProfile.MAX_TOKENS)
                .containsEntry("maxSourceCharacters", OclComplianceProfile.MAX_SOURCE_CHARACTERS)
                .containsEntry("maxDiagnostics", OclComplianceProfile.MAX_DIAGNOSTICS)
                .containsEntry("maxAstDepth", OclComplianceProfile.MAX_AST_DEPTH)
                .containsEntry("maxEvaluationMillis", OclComplianceProfile.MAX_EVALUATION_MILLIS)
                .containsEntry("maxDefinitionRecursion", OclComplianceProfile.MAX_DEFINITION_RECURSION)
                .containsEntry("maxResultElements", OclComplianceProfile.MAX_RESULT_ELEMENTS);
    }

    @Test
    void publishesTheRejectedOptionalNavigationAndVisibilityCompliancePoints() {
        OclComplianceProfile profile = OclComplianceProfile.current();

        assertThat(profile.enabledOptionalCompliancePoints())
                .doesNotContain("nonNavigableAssociationAccess", "nonPublicFeatureVisibilityBypass",
                        "OclMessage", "oclInState", "stateMachines");
        assertThat(profile.features()).filteredOn(feature ->
                feature.id().equals("OCL-PROFILE-013") || feature.id().equals("OCL-PROFILE-015"))
                .allSatisfy(feature -> assertThat(feature.status()).isEqualTo(OclFeatureStatus.NOT_SUPPORTED));
        assertThat(OclOptionalCompliancePolicy.NON_NAVIGABLE_ASSOCIATION_ACCESS).isFalse();
        assertThat(OclOptionalCompliancePolicy.BYPASS_NON_PUBLIC_FEATURE_VISIBILITY).isFalse();
    }

    @Test
    void publishesStateAndMessageFeaturesAsExcludedFromTheTargetProfile() {
        OclComplianceProfile profile = OclComplianceProfile.current();

        assertThat(profile.features()).filteredOn(feature -> feature.id().equals("OCL-PROFILE-012"))
                .allSatisfy(feature -> assertThat(feature.status()).isEqualTo(OclFeatureStatus.NOT_SUPPORTED));
        assertThat(profile.features()).filteredOn(feature -> feature.id().equals("OCL-PROFILE-016"))
                .allSatisfy(feature -> assertThat(feature.status()).isEqualTo(OclFeatureStatus.OUT_OF_SCOPE));
    }
}

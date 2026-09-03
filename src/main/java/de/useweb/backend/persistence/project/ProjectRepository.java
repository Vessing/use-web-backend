package de.useweb.backend.persistence.project;

import java.util.Optional;
import java.util.List;

import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.layout.LayoutInformation;

public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> updateLayout(ProjectId projectId, LayoutInformation layout);

    Optional<Project> findById(ProjectId projectId);

    boolean existsById(ProjectId projectId);

    List<Project> findAll();
}

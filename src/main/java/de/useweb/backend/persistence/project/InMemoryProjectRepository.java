package de.useweb.backend.persistence.project;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;

@Repository
public class InMemoryProjectRepository implements ProjectRepository {

    private final Map<ProjectId, Project> projects = new ConcurrentHashMap<>();

    @Override
    public Project save(Project project) {
        projects.put(project.id(), project);
        return project;
    }

    @Override
    public Optional<Project> findById(ProjectId projectId) {
        return Optional.ofNullable(projects.get(projectId));
    }

    @Override
    public boolean existsById(ProjectId projectId) {
        return projects.containsKey(projectId);
    }

    @Override
    public List<Project> findAll() {
        return List.copyOf(projects.values());
    }
}

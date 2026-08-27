package de.useweb.backend.application.project;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.project.ProjectSummaryDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.persistence.project.ProjectRepository;

@Service
public class RecentProjectService {

    private static final int DEFAULT_LIMIT = 5;

    private final ProjectRepository projectRepository;

    public RecentProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<ProjectSummaryDto> recentProjects() {
        return recentProjects(DEFAULT_LIMIT);
    }

    public List<ProjectSummaryDto> recentProjects(int limit) {
        return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(project -> project.metadata().updatedAt(), Comparator.reverseOrder()))
                .limit(Math.max(0, limit))
                .map(ProjectDtoMapper::toSummaryDto)
                .toList();
    }
}

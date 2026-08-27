package de.useweb.backend.persistence.json;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import de.useweb.backend.api.dto.project.ProjectDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.error.InvalidProjectFormatException;

@Component
public class ProjectJsonSerializer {

    private final ObjectMapper objectMapper;

    public ProjectJsonSerializer() {
        this(defaultObjectMapper());
    }

    public ProjectJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(Project project) {
        return serialize(ProjectDtoMapper.toDto(project));
    }

    public String serialize(ProjectDto project) {
        requireSupportedFormatVersion(project.formatVersion());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(project);
        } catch (JsonProcessingException exception) {
            throw new InvalidProjectFormatException("Projekt konnte nicht als JSON geschrieben werden.", exception);
        }
    }

    public ProjectDto deserializeDto(String json) {
        try {
            ProjectDto dto = objectMapper.readValue(json, ProjectDto.class);
            validateRequiredTopLevelFields(dto);
            requireSupportedFormatVersion(dto.formatVersion());
            return dto;
        } catch (JsonProcessingException exception) {
            throw new InvalidProjectFormatException("Projekt-JSON konnte nicht gelesen werden.", exception);
        }
    }

    public Project deserialize(String json) {
        return ProjectDtoMapper.toDomain(deserializeDto(json));
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        return mapper;
    }

    private static void validateRequiredTopLevelFields(ProjectDto dto) {
        if (dto == null) {
            throw new InvalidProjectFormatException("Projekt-JSON ist leer.");
        }
        if (dto.formatVersion() == null || dto.formatVersion().isBlank()) {
            throw new InvalidProjectFormatException("Pflichtfeld formatVersion fehlt.");
        }
        if (dto.project() == null) {
            throw new InvalidProjectFormatException("Pflichtfeld project fehlt.");
        }
        if (dto.umlModel() == null) {
            throw new InvalidProjectFormatException("Pflichtfeld umlModel fehlt.");
        }
        if (dto.objectModel() == null) {
            throw new InvalidProjectFormatException("Pflichtfeld objectModel fehlt.");
        }
    }

    private static void requireSupportedFormatVersion(String formatVersion) {
        if (!ProjectJsonFormat.CURRENT_FORMAT_VERSION.equals(formatVersion)) {
            throw new InvalidProjectFormatException(
                    "Projektformat-Version wird nicht unterstuetzt.",
                    Map.of(
                            "formatVersion", formatVersion,
                            "expectedFormatVersion", ProjectJsonFormat.CURRENT_FORMAT_VERSION));
        }
    }
}

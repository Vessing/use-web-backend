package de.useweb.backend.domain.snapshot;

import java.util.List;
import java.util.Optional;

public record ObjectModel(
        ObjectModelId id,
        String name,
        List<ObjectInstance> objects,
        List<ObjectLink> links) {

    public ObjectModel {
        if (id == null) {
            throw new IllegalArgumentException("ObjectModel id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ObjectModel name must not be blank");
        }
        objects = List.copyOf(objects == null ? List.of() : objects);
        links = List.copyOf(links == null ? List.of() : links);
    }

    public Optional<ObjectInstance> findObject(ObjectInstanceId objectId) {
        return objects.stream()
                .filter(object -> object.id().equals(objectId))
                .findFirst();
    }

    public Optional<ObjectLink> findLink(ObjectLinkId linkId) {
        return links.stream()
                .filter(link -> link.id().equals(linkId))
                .findFirst();
    }
}

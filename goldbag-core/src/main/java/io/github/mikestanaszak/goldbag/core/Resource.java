package io.github.mikestanaszak.goldbag.core;

import java.util.Objects;

public record Resource(String id, String material, long depositPrice, long withdrawPrice,
                       boolean depositEnabled, boolean withdrawEnabled) {
    public Resource {
        id = Catalog.normalize(id);
        material = Catalog.normalizeMaterial(material);
        if (id.isEmpty() || material.isEmpty()) {
            throw new IllegalArgumentException("Resource id and material are required");
        }
        if (depositPrice <= 0 || withdrawPrice <= 0) {
            throw new IllegalArgumentException("Resource prices must be positive");
        }
    }
}

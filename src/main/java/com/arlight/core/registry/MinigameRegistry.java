package com.arlight.core.registry;

import com.arlight.core.api.MinigameProvider;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class MinigameRegistry {

    private final Map<String, MinigameProvider> providers = new LinkedHashMap<>();

    public void register(MinigameProvider provider) {
        if (provider == null || provider.getId() == null || provider.getId().isBlank()) {
            throw new IllegalArgumentException("El minijuego debe tener un id válido.");
        }
        String id = provider.getId().toLowerCase();
        if (providers.containsKey(id)) {
            throw new IllegalStateException("Ya existe un minijuego registrado con el id '" + id + "'.");
        }
        String category = provider.getCategory() == null ? id : provider.getCategory().trim().toLowerCase();
        boolean categoryUsed = providers.values().stream().anyMatch(existing -> {
            String existingCategory = existing.getCategory() == null ? existing.getId() : existing.getCategory();
            return existingCategory.equalsIgnoreCase(category);
        });
        if (categoryUsed) {
            throw new IllegalStateException("Ya existe un minijuego registrado en la categoría '" + category + "'.");
        }
        providers.put(id, provider);
    }

    public void unregister(String id) {
        if (id != null) providers.remove(id.toLowerCase());
    }

    public Collection<MinigameProvider> getAll() {
        return providers.values();
    }

    public int size() {
        return providers.size();
    }

    public MinigameProvider get(String id) {
        return id == null ? null : providers.get(id.toLowerCase());
    }
}

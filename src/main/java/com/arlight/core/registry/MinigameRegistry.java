package com.arlight.core.registry;

import com.arlight.core.api.MinigameProvider;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class MinigameRegistry {

    private final Map<String, MinigameProvider> providers = new LinkedHashMap<>();

    public void register(MinigameProvider provider) {
        providers.put(provider.getId(), provider);
    }

    public void unregister(String id) {
        providers.remove(id);
    }

    public Collection<MinigameProvider> getAll() {
        return providers.values();
    }

    public MinigameProvider get(String id) {
        return providers.get(id);
    }
}

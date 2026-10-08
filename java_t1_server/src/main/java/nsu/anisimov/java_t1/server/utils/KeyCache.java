package nsu.anisimov.java_t1.server.utils;

import nsu.anisimov.java_t1.server.utils.data.CacheEntry;
import nsu.anisimov.java_t1.server.utils.data.CreationStatus;

import java.util.HashMap;
import java.util.Map;

public class KeyCache {

    private final Map<String, CacheEntry> clientDataCache;

    public KeyCache() {
        this.clientDataCache = new HashMap<>();
    }

    public synchronized CreationStatus createLockedOrGet(String key) {
        CacheEntry getResult = clientDataCache.get(key);

        if (getResult == null) {
            CacheEntry newEntry = new CacheEntry();
            clientDataCache.put(key, newEntry);

            return new CreationStatus(newEntry, true);
        } else {
            return new CreationStatus(getResult, false);
        }
    }

    public synchronized void delete(String key) {
        this.clientDataCache.remove(key);
    }

}

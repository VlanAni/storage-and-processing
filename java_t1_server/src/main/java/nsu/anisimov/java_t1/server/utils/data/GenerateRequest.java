package nsu.anisimov.java_t1.server.utils.data;

import java.nio.channels.SelectionKey;

public record GenerateRequest(SelectionKey connKey, String subjectName, CacheEntry entry) {
}

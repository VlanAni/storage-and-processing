package nsu.anisimov.java_t1.server.utils;

import nsu.anisimov.java_t1.server.utils.data.*;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.BlockingQueue;

public class SubjectHandler implements Runnable {

    private final SelectionKey key;
    private final String subjectName;
    private final KeyCache cache;
    private final Selector selector;
    private final BlockingQueue<GenerateRequest> generateRequestQueue;
    private final BlockingQueue<DataToSend> dataToSendQueue;

    public SubjectHandler(
            SelectionKey connKey,
            String subjectName,
            KeyCache cache,
            Selector selector,
            BlockingQueue<GenerateRequest> generateRequestQueue,
            BlockingQueue<DataToSend> dataToSendQueue
    ) {
        this.key = connKey;
        this.subjectName = subjectName;
        this.cache = cache;
        this.selector = selector;
        this.dataToSendQueue = dataToSendQueue;
        this.generateRequestQueue = generateRequestQueue;
    }

    @Override
    public void run() {

        CreationStatus creationStatus = cache.createLockedOrGet(subjectName);

        if (creationStatus.wasCreated()) {

            generateRequestQueue.add(new GenerateRequest(key, subjectName, creationStatus.entry()));

        } else {

            CacheEntry entry = creationStatus.entry();

            ClientData data = entry.fetch();

            dataToSendQueue.add(new DataToSend(key, data));

            selector.wakeup();

        }

    }

}

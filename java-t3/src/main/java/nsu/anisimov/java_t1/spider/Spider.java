package nsu.anisimov.java_t1.spider;

import nsu.anisimov.java_t1.spider.path_handler.PathHandler;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Spider {

    public static SpiderReport parseServer(int port) {

        Set<String> handledPaths = ConcurrentHashMap.newKeySet();
        Queue<String> messages = new ConcurrentLinkedQueue<>();
        Queue<String> unhandledPaths = new ConcurrentLinkedQueue<>();
        Queue<String> errorMessages = new ConcurrentLinkedQueue<>();

        unhandledPaths.add("/");

        AtomicLong tasksCounter = new AtomicLong();
        AtomicBoolean errorOccured = new AtomicBoolean(false);

        try (HttpClient client = HttpClient.newHttpClient()) {

            while (true) {

                if (errorOccured.get()) {
                    client.shutdownNow();
                    return new SpiderReport(null, new ArrayList<>(errorMessages));
                }

                String path = unhandledPaths.poll();

                if (path == null) {
                    if (tasksCounter.get() == 0 && unhandledPaths.isEmpty()) {
                        break;
                    }
                    continue;
                }

                if (!handledPaths.add(path)) {
                    continue;
                }

                PathHandler handler;
                try {
                    handler = new PathHandler(
                            path,
                            Integer.toString(port),
                            client,
                            unhandledPaths,
                            messages,
                            errorOccured,
                            tasksCounter,
                            errorMessages
                    );
                } catch (IllegalArgumentException e) {
                    client.shutdownNow();
                    errorMessages.add("Invalid path '" + path + "': " + e.getMessage());
                    return new SpiderReport(null, new ArrayList<>(errorMessages));
                }

                tasksCounter.incrementAndGet();
                Thread.startVirtualThread(handler);
            }

            if (errorOccured.get()) {
                return new SpiderReport(null, new ArrayList<>(errorMessages));
            }

        }

        List<String> messagesList = new ArrayList<>(messages);
        Collections.sort(messagesList);
        return new SpiderReport(messagesList, null);

    }

}
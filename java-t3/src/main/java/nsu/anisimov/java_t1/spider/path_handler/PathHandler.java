package nsu.anisimov.java_t1.spider.path_handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nsu.anisimov.java_t1.spider.serverobj.ServerData;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PathHandler implements Runnable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final HttpRequest request;
    private final AtomicBoolean errorOccured;
    private final Queue<String> unhandledPaths;
    private final Queue<String> messages;
    private final AtomicLong taskCounter;
    private final Queue<String> errorMessages;


    public PathHandler(
            String pathToHandle,
            String serverPort,
            HttpClient client,
            Queue<String> unhandledPaths,
            Queue<String> messages,
            AtomicBoolean errorOccured,
            AtomicLong taskCounter,
            Queue<String> errorMessages
    ) {
        if (pathToHandle == null ||
                client == null ||
                unhandledPaths == null ||
                messages == null ||
                serverPort == null ||
                errorOccured == null ||
                taskCounter == null ||
                errorMessages == null
        ) {
            throw new IllegalArgumentException("arguments must be non-null");
        }

        this.client = client;
        this.unhandledPaths = unhandledPaths;
        this.messages = messages;
        this.request = createGetRequest(serverPort, pathToHandle);
        this.errorOccured = errorOccured;
        this.taskCounter = taskCounter;
        this.errorMessages = errorMessages;
    }

    @Override
    public void run() {
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                fail("Status code " + response.statusCode() + " for " + request.uri());
                return;
            }

            ServerData sd;
            try {
                sd = MAPPER.readValue(response.body(), ServerData.class);
            } catch (IOException e) {
                fail("Failed to deserialize body for " + request.uri() + ": " + e.getMessage());
                return;
            }

            if (sd.getMessage() == null) {
                fail("Field 'message' is missing for " + request.uri());
                return;
            }

            messages.add(sd.getMessage());

            List<String> successors = sd.getSuccessors();
            if (successors != null) {
                unhandledPaths.addAll(successors);
            }

        } catch (IOException e) {
            fail("HTTP request to " + request.uri() + " failed: " + e);
        } catch (InterruptedException e) {
            fail("Thread was interrupted during HTTP request to " + request.uri());
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            fail("Unexpected error for " + request.uri() + ": " + e);
        } finally {
            taskCounter.decrementAndGet();
        }
    }

    private void fail(String message) {
        errorMessages.add(message);
        errorOccured.set(true);
    }

    private static HttpRequest createGetRequest(String serverPort, String pathToHandle) {
        String path = pathToHandle.startsWith("/") ? pathToHandle : "/" + pathToHandle;
        URI uri = URI.create("http://localhost:" + serverPort + path);
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
    }
}

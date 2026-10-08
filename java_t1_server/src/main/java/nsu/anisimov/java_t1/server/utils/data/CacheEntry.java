package nsu.anisimov.java_t1.server.utils.data;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CacheEntry {

    private final CountDownLatch ready;
    private ClientData data;
    private boolean notValid;

    public CacheEntry() {
        this.ready = new CountDownLatch(1);
        this.notValid = false;
    }

    public void putResultOrSetNotValid(ClientData data, boolean notValid) {
        this.data = data;
        this.notValid = notValid;

        ready.countDown();
    }

    public ClientData fetch() {
        try {
            ready.await();

            if (notValid) {
                return null;
            }

            return data;
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            return null;

        }
    }

}

package nsu.anisimov.storage_processing.task1.sort_threads;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class SyncListSortThread extends Thread {

    private final List<String> list;
    private final int stepTimeout;
    private final int passTimeout;
    private final AtomicLong stepCounter;
    private volatile boolean running;

    public SyncListSortThread(
            List<String> list,
            int stepTimeout,
            int passTimeout,
            int id,
            AtomicLong stepCounter
    ) {
        super("SyncSorter-" + id);
        this.list = list;
        this.stepTimeout = stepTimeout;
        this.passTimeout = passTimeout;
        this.stepCounter = stepCounter;
        this.running = true;
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try {
            while (running) {
                sortIteration();
                Thread.sleep(passTimeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sortIteration() throws InterruptedException {
        int index = 0;
        while (running && step(index)) {
            Thread.sleep(stepTimeout);
            index++;
        }
    }

    private boolean step(int index) {
        synchronized (list) {
            if (index + 1 >= list.size()) {
                return false;
            }

            String a = list.get(index);
            String b = list.get(index + 1);

            stepCounter.incrementAndGet();

            if (a.compareTo(b) > 0) {
                list.set(index, b);
                list.set(index + 1, a);
            }

            return true;
        }
    }
}
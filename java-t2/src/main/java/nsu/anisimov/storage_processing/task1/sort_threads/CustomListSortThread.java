package nsu.anisimov.storage_processing.task1.sort_threads;

import nsu.anisimov.storage_processing.task1.list.CustomThreadSafeList;

public class CustomListSortThread extends Thread {

    private final CustomThreadSafeList list;
    private final int stepTimeout;
    private final int passTimeout;
    private volatile boolean running;

    public CustomListSortThread(CustomThreadSafeList list, int stepTimeout, int passTimeout, int id) {
        super("CustomSorter-" + id);
        this.list = list;
        this.stepTimeout = stepTimeout;
        this.passTimeout = passTimeout;
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
                list.sortIteration(stepTimeout);
                Thread.sleep(passTimeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
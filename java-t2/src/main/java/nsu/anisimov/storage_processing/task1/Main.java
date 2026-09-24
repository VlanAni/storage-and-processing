package nsu.anisimov.storage_processing.task1;

import nsu.anisimov.storage_processing.task1.list.CustomThreadSafeList;
import nsu.anisimov.storage_processing.task1.sort_threads.CustomListSortThread;
import nsu.anisimov.storage_processing.task1.sort_threads.SyncListSortThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    private static final int CHUNK_SIZE = 80;

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: Main <mode: custom|sync> <sorters> <stepDelayMs> <passDelayMs>");
            return;
        }

        String mode      = args[0];
        int sortersCount = Integer.parseInt(args[1]);
        int stepTimeout  = Integer.parseInt(args[2]);
        int pauseTimeout  = Integer.parseInt(args[3]);

        switch (mode) {
            case "custom" -> runCustom(sortersCount, stepTimeout, pauseTimeout);
            case "sync"   -> runSync  (sortersCount, stepTimeout, pauseTimeout);
            default -> System.err.println("Unknown mode: " + mode);
        }
    }

    private static void runCustom(int sortersCount, int stepDelayMs, int passDelayMs)
            throws IOException {

        CustomThreadSafeList list = new CustomThreadSafeList();

        List<CustomListSortThread> sorters = new ArrayList<>();
        for (int i = 0; i < sortersCount; i++) {
            CustomListSortThread t =
                    new CustomListSortThread(list, stepDelayMs, passDelayMs, i);
            sorters.add(t);
        }

        sorters.forEach(Thread::start);

        readInput(line -> {
            if (line.isEmpty()) {
                printCustom(list);
            } else {
                putChunksToHeadCustom(list, line);
            }
        });

        shutdownAndJoin(sorters);

        System.out.printf(
                "mode=custom sorters=%d stepDelay=%dms passDelay=%dms totalSteps=%d%n",
                sortersCount, stepDelayMs, passDelayMs, list.stepCount());
    }

    private static void putChunksToHeadCustom(CustomThreadSafeList list, String line) {
        for (int i = 0; i < line.length(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, line.length());
            list.putToHead(line.substring(i, end));
        }
    }

    private static void printCustom(CustomThreadSafeList list) {
        StringBuilder sb = new StringBuilder("---- custom list ----\n");
        int idx = 0;
        for (String s : list) {
            sb.append(String.format("%3d: %s%n", idx++, s));
        }
        sb.append("----\n");
        System.out.print(sb);
    }

    private static void runSync(int sortersCount, int stepDelayMs, int passDelayMs)
            throws IOException {

        List<String> list = Collections.synchronizedList(new ArrayList<>());
        AtomicLong stepCounter = new AtomicLong();

        List<SyncListSortThread> sorters = new ArrayList<>();
        for (int i = 0; i < sortersCount; i++) {
            SyncListSortThread t =
                    new SyncListSortThread(list, stepDelayMs, passDelayMs, i, stepCounter);
            sorters.add(t);
        }

        sorters.forEach(Thread::start);

        readInput(line -> {
            if (line.isEmpty()) {
                printSync(list);
            } else {
                putChunksToHeadSync(list, line);
            }
        });

        shutdownAndJoin(sorters);

        System.out.printf(
                "mode=sync sorters=%d stepDelay=%dms passDelay=%dms totalSteps=%d%n",
                sortersCount, stepDelayMs, passDelayMs, stepCounter.get());
    }

    private static void putChunksToHeadSync(List<String> list, String line) {
        for (int i = 0; i < line.length(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, line.length());
            String chunk = line.substring(i, end);
            synchronized (list) {
                list.add(0, chunk);
            }
        }
    }

    private static void printSync(List<String> list) {
        StringBuilder sb = new StringBuilder("---- sync list ----\n");
        synchronized (list) {
            int idx = 0;
            for (String s : list) {
                sb.append(String.format("%3d: %s%n", idx++, s));
            }
        }
        sb.append("----\n");
        System.out.print(sb);
    }

    private interface LineHandler {
        void handle(String line) throws IOException;
    }

    private static void readInput(LineHandler handler) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            handler.handle(line);
        }
    }

    private static void shutdownAndJoin(List<? extends Thread> threads) {
        for (Thread t : threads) {
            if (t instanceof CustomListSortThread c) c.shutdown();
            if (t instanceof SyncListSortThread s)   s.shutdown();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
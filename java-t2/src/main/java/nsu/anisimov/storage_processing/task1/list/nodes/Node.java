package nsu.anisimov.storage_processing.task1.list.nodes;

import java.util.concurrent.locks.ReentrantLock;

public class Node {

    private String data;
    private Node next;
    private ReentrantLock mutex;

    public Node(String data) {
        if (data == null) {
            throw new IllegalArgumentException();
        }

        this.mutex = new ReentrantLock();
        this.data = data;
        this.next = null;
    }

    public Node(String data, Node next) {
        if (data == null) {
            throw new IllegalArgumentException();
        }

        this.mutex = new ReentrantLock();
        this.data = data;
        this.next = next;
    }

    public void updateNext(Node newNext) {
        this.next = newNext;
    }

    public void lock() {
        this.mutex.lock();
    }

    public void unlock() {
        this.mutex.unlock();
    }

    public Node next() {
        return this.next;
    }

    public String data() {
        return this.data;
    }

    @Override
    public String toString() {
        return this.data;
    }
}

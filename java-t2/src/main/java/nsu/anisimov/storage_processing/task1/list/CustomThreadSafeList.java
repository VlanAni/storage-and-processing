package nsu.anisimov.storage_processing.task1.list;

import nsu.anisimov.storage_processing.task1.list.nodes.Node;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class CustomThreadSafeList implements Iterable<String> {

    private Node head;
    private final ReentrantLock headMutex;
    private AtomicLong stepCount;

    public CustomThreadSafeList() {
        this.headMutex = new ReentrantLock();
        this.head = null;
        this.stepCount = new AtomicLong();
    }

    public void putToHead(String data) {
        Node newHead = new Node(data);

        headMutex.lock();

        try {
            Node fstNode = head;
            newHead.updateNext(fstNode);
            head = newHead;
        } finally {
            headMutex.unlock();
        }
    }

    @Override
    public Iterator<String> iterator() {
        return new CustomListIterator();
    }

    public Long stepCount() {
        return this.stepCount.get();
    }

    public void sortIteration(int timeout) throws InterruptedException {
        headMutex.lock();
        try {
            if (head == null) {
                return;
            }
        } finally {
            headMutex.unlock();
        }

        Node prev = null;

        while (true) {
            Node nextPrev = step(prev, timeout);
            if (nextPrev == null) {
                return;
            }
            prev = nextPrev;
        }
    }

    private Node step(Node prev, int timeout) throws InterruptedException {
        Node result;

        if (prev == null) {
            headMutex.lock();
            try {
                Node a = head;
                if (a == null) {
                    return null;
                }
                a.lock();
                try {
                    Node b = a.next();
                    if (b == null) {
                        return null;
                    }
                    b.lock();
                    try {
                        Node q = b.next();

                        if (a.data().compareTo(b.data()) > 0) {
                            b.updateNext(a);
                            a.updateNext(q);
                            head = b;
                            result = b;
                        } else {
                            result = a;
                        }
                    } finally {
                        b.unlock();
                    }
                } finally {
                    a.unlock();
                }
            } finally {
                headMutex.unlock();
            }
        } else {
            prev.lock();
            try {
                Node a = prev.next();
                if (a == null) {
                    return null;
                }
                a.lock();
                try {
                    Node b = a.next();
                    if (b == null) {
                        return null;
                    }
                    b.lock();
                    try {
                        Node q = b.next();
                        stepCount.incrementAndGet();
                        if (a.data().compareTo(b.data()) > 0) {
                            prev.updateNext(b);
                            b.updateNext(a);
                            a.updateNext(q);
                            result = b;
                        } else {
                            result = a;
                        }
                    } finally {
                        b.unlock();
                    }
                } finally {
                    a.unlock();
                }
            } finally {
                prev.unlock();
            }
        }

        Thread.sleep(timeout);
        return result;
    }

    private class CustomListIterator implements Iterator<String> {

        private Node current;
        private Node next;

        public CustomListIterator() {
            headMutex.lock();
            try {
                this.current = head;
                if (current != null) {
                    current.lock();
                }
            } finally {
                headMutex.unlock();
            }

            updNext();
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public String next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            String value = current.data();

            current.unlock();
            current = next;
            updNext();

            return value;
        }

        private void updNext() {
            if (this.current == null) {
                next = null;
                return;
            }
            Node currNext = current.next();
            if (currNext != null) {
                currNext.lock();
            }
            next = currNext;
        }

    }

}

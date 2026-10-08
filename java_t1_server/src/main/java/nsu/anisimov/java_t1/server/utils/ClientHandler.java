package nsu.anisimov.java_t1.server.utils;

import nsu.anisimov.java_t1.server.utils.data.ClientData;
import nsu.anisimov.java_t1.server.utils.data.DataToSend;
import nsu.anisimov.java_t1.server.utils.data.GenerateRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler implements Runnable {

    private static final int READ_BUFFER_SIZE = 4096;
    private static final int MAX_NAME_SIZE = 16 * 1024;

    private final Selector selector;
    private final ServerSocketChannel serverChannel;

    private final KeyCache cache;

    private final BlockingQueue<GenerateRequest> generateRequestQueue;
    private final BlockingQueue<DataToSend> dataToSendQueue;

    private final ExecutorService subjectExecutor;

    public ClientHandler(
            String host,
            int port,
            KeyCache cache,
            BlockingQueue<GenerateRequest> generateRequestQueue,
            BlockingQueue<DataToSend> dataToSendQueue
    ) throws IOException {

        this.cache = cache;
        this.generateRequestQueue = generateRequestQueue;
        this.dataToSendQueue = dataToSendQueue;

        this.selector = Selector.open();

        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(new InetSocketAddress(host, port));
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.subjectExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("subject-handler-", 0).factory()
        );
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {

                processResponses();

                selector.select();

                processResponses();
                processSelectedKeys();
            }

        } catch (ClosedSelectorException | ConcurrentModificationException ignored) {
        } catch (IOException e) {
            throw new RuntimeException("client handler failed", e);
        } finally {
            shutdown();
        }
    }

    public Selector selector() {
        return selector;
    }

    private void processSelectedKeys() throws IOException {
        Iterator<SelectionKey> iterator =
                selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();

            if (!key.isValid()) {
                continue;
            }

            try {
                if (key.isAcceptable()) {
                    acceptClients();
                    continue;
                }

                if (key.isReadable()) {
                    readClientName(key);
                }

                if (key.isValid() && key.isWritable()) {
                    writeClientResponse(key);
                }

            } catch (IOException | CancelledKeyException e) {
                if (key.channel() != serverChannel) {
                    closeConnection(key);
                }
            }
        }
    }

    private void acceptClients() throws IOException {
        while (true) {
            SocketChannel channel = serverChannel.accept();

            if (channel == null) {
                return;
            }

            channel.configureBlocking(false);

            ClientConnection connection = new ClientConnection(channel);

            channel.register(
                    selector,
                    SelectionKey.OP_READ,
                    connection
            );
        }
    }

    private void readClientName(SelectionKey key) throws IOException {
        ClientConnection connection =
                (ClientConnection) key.attachment();

        if (connection.nameReceived) {
            return;
        }

        int bytesRead = connection.channel.read(connection.readBuffer);

        if (bytesRead == -1) {
            closeConnection(key);
            return;
        }

        if (bytesRead == 0) {
            return;
        }

        connection.readBuffer.flip();

        while (connection.readBuffer.hasRemaining()) {
            byte value = connection.readBuffer.get();

            if (value == 0) {
                String subjectName = parseAsciiName(connection.nameBytes);

                connection.nameReceived = true;

                key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);

                subjectExecutor.submit(
                        new SubjectHandler(
                                key,
                                subjectName,
                                cache,
                                selector,
                                generateRequestQueue,
                                dataToSendQueue
                        )
                );

                break;
            }

            if ((value & 0x80) != 0) {
                throw new IOException("client name must contain ASCII only");
            }

            connection.nameBytes.write(value);

            if (connection.nameBytes.size() > MAX_NAME_SIZE) {
                throw new IOException("client name is too long");
            }
        }

        connection.readBuffer.compact();
    }

    private String parseAsciiName(ByteArrayOutputStream bytes)
            throws IOException {

        if (bytes.size() == 0) {
            throw new IOException("empty client name");
        }

        return bytes.toString(StandardCharsets.US_ASCII);
    }

    private void processResponses() {
        DataToSend dataToSend;

        while ((dataToSend = dataToSendQueue.poll()) != null) {
            SelectionKey key = dataToSend.connKey();

            if (key == null || !key.isValid()) {
                continue;
            }

            ClientConnection connection =
                    (ClientConnection) key.attachment();

            if (connection == null) {
                continue;
            }

            ByteBuffer response =
                    serializeResponse(dataToSend.clientData());

            connection.outputQueue.add(response);

            key.interestOps(
                    key.interestOps() | SelectionKey.OP_WRITE
            );
        }
    }

    private void writeClientResponse(SelectionKey key)
            throws IOException {

        ClientConnection connection =
                (ClientConnection) key.attachment();

        if (connection.outputQueue.isEmpty()) {
            key.interestOps(
                    key.interestOps() & ~SelectionKey.OP_WRITE
            );
            return;
        }

        ByteBuffer buffer = connection.outputQueue.peek();

        int written = connection.channel.write(buffer);

        if (written == -1) {
            closeConnection(key);
            return;
        }

        if (!buffer.hasRemaining()) {
            connection.outputQueue.poll();
        }

        if (connection.outputQueue.isEmpty()) {
            closeConnection(key);
        }
    }

    private ByteBuffer serializeResponse(ClientData data) {
        if (data == null) {
            ByteBuffer buffer = ByteBuffer.allocate(12);

            buffer.putInt(0);
            buffer.putInt(0);
            buffer.putInt(0);

            buffer.flip();

            return buffer;
        }

        byte[] publicKey = data.publicKey();
        byte[] privateKey = data.privateKey();
        byte[] certificate = data.cert();

        long totalSize =
                12L
                        + publicKey.length
                        + privateKey.length
                        + certificate.length;

        if (totalSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "response is too large"
            );
        }

        ByteBuffer buffer =
                ByteBuffer.allocate((int) totalSize);

        buffer.putInt(publicKey.length);
        buffer.put(publicKey);

        buffer.putInt(privateKey.length);
        buffer.put(privateKey);

        buffer.putInt(certificate.length);
        buffer.put(certificate);

        buffer.flip();

        return buffer;
    }

    private void closeConnection(SelectionKey key) {
        try {
            key.cancel();

            if (key.channel() instanceof SocketChannel channel) {
                channel.close();
            }
        } catch (IOException ignored) {
        }
    }

    public void close() {
        shutdown();
    }

    private void shutdown() {
        subjectExecutor.shutdownNow();

        try {
            serverChannel.close();
        } catch (IOException ignored) {
        }

        try {
            selector.close();
        } catch (IOException ignored) {
        }
    }

    private static final class ClientConnection {

        private final SocketChannel channel;
        private final ByteBuffer readBuffer;
        private final ByteArrayOutputStream nameBytes;
        private final Queue<ByteBuffer> outputQueue;

        private boolean nameReceived;

        private ClientConnection(SocketChannel channel) {
            this.channel = channel;
            this.readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
            this.nameBytes = new ByteArrayOutputStream();
            this.outputQueue = new ArrayDeque<>();
        }
    }
}
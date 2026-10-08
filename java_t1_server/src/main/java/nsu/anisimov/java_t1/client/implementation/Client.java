package nsu.anisimov.java_t1.client.implementation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class Client {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private static final int MAX_PART_SIZE = 16 * 1024 * 1024;

    private final String host;
    private final int port;
    private final String subjectName;

    private final long delaySeconds;
    private final boolean abort;

    private final Path outputDirectory;
    private final String keyFileName;
    private final String certificateFileName;

    public Client(
            String host,
            int port,
            String subjectName,
            long delaySeconds,
            boolean abort,
            Path outputDirectory,
            String keyFileName,
            String certificateFileName
    ) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                    "host must not be empty"
            );
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "invalid port: " + port
            );
        }

        validateSubjectName(subjectName);

        if (delaySeconds < 0) {
            throw new IllegalArgumentException(
                    "delay must be >= 0"
            );
        }

        if (outputDirectory == null) {
            throw new IllegalArgumentException(
                    "output directory must not be null"
            );
        }

        if (keyFileName == null || keyFileName.isBlank()) {
            throw new IllegalArgumentException(
                    "key file name must not be empty"
            );
        }

        if (certificateFileName == null
                || certificateFileName.isBlank()) {
            throw new IllegalArgumentException(
                    "certificate file name must not be empty"
            );
        }

        this.host = host;
        this.port = port;
        this.subjectName = subjectName;
        this.delaySeconds = delaySeconds;
        this.abort = abort;
        this.outputDirectory = outputDirectory;
        this.keyFileName = keyFileName;
        this.certificateFileName = certificateFileName;
    }

    public void run() throws IOException {
        Files.createDirectories(outputDirectory);

        Path privateKeyPath = outputDirectory.resolve(keyFileName + "-private.key");
        Path publicKeyPath = outputDirectory.resolve(keyFileName + "-public.key");
        Path certificatePath =
                outputDirectory.resolve(certificateFileName);

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(host, port),
                    CONNECT_TIMEOUT_MILLIS
            );

            DataOutputStream output =
                    new DataOutputStream(socket.getOutputStream());

            DataInputStream input =
                    new DataInputStream(socket.getInputStream());

            sendSubjectName(output);

            if (abort) {
                return;
            }

            if (delaySeconds > 0) {
                sleep(delaySeconds);
            }

            ClientResponse response = readResponse(input);

            System.out.println("response read");

            if (response.isError()) {
                throw new IOException(
                        "server failed to generate key pair"
                );
            }

            Files.write(
                    privateKeyPath,
                    response.privateKey()
            );

            Files.write(
                    publicKeyPath,
                    response.publicKey()
            );

            Files.write(
                    certificatePath,
                    response.certificate()
            );
        }
    }

    private void sendSubjectName(
            DataOutputStream output
    ) throws IOException {

        byte[] nameBytes =
                subjectName.getBytes(StandardCharsets.US_ASCII);

        output.write(nameBytes);
        output.writeByte(0);
        output.flush();
    }

    private ClientResponse readResponse(
            DataInputStream input
    ) throws IOException {

        int publicKeyLength = input.readInt();
        byte[] publicKey = readPart(input, publicKeyLength);

        int privateKeyLength = input.readInt();
        byte[] privateKey = readPart(input, privateKeyLength);

        int certificateLength = input.readInt();
        byte[] certificate =
                readPart(input, certificateLength);

        if (publicKeyLength == 0
                && privateKeyLength == 0
                && certificateLength == 0) {

            return new ClientResponse(
                    null,
                    null,
                    null,
                    true
            );
        }

        return new ClientResponse(
                publicKey,
                privateKey,
                certificate,
                false
        );
    }

    private static byte[] readPart(
            DataInputStream input,
            int length
    ) throws IOException {

        if (length < 0) {
            throw new IOException(
                    "negative response part length: " + length
            );
        }

        if (length > MAX_PART_SIZE) {
            throw new IOException(
                    "response part is too large: " + length
            );
        }

        byte[] data = new byte[length];

        try {
            input.readFully(data);
        } catch (EOFException e) {
            throw new IOException(
                    "unexpected end of server response",
                    e
            );
        }

        return data;
    }

    private static void sleep(long seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "client was interrupted",
                    e
            );
        }
    }

    private static void validateSubjectName(
            String subjectName
    ) {
        if (subjectName == null || subjectName.isEmpty()) {
            throw new IllegalArgumentException(
                    "subject name must not be empty"
            );
        }

        for (int i = 0; i < subjectName.length(); i++) {
            char c = subjectName.charAt(i);

            if (c > 0x7F) {
                throw new IllegalArgumentException(
                        "subject name must contain ASCII only"
                );
            }

            if (c == '\0') {
                throw new IllegalArgumentException(
                        "subject name must not contain NUL"
                );
            }
        }
    }

    private record ClientResponse(
            byte[] publicKey,
            byte[] privateKey,
            byte[] certificate,
            boolean error
    ) {
        private boolean isError() {
            return error;
        }
    }
}
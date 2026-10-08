package nsu.anisimov.java_t1.server.implementation;

import nsu.anisimov.java_t1.server.utils.ClientDataGenerateWorker;
import nsu.anisimov.java_t1.server.utils.ClientHandler;
import nsu.anisimov.java_t1.server.utils.KeyCache;
import nsu.anisimov.java_t1.server.utils.data.DataToSend;
import nsu.anisimov.java_t1.server.utils.data.GenerateRequest;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class Server implements AutoCloseable {

    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 8192;

    private final String bindAddress;
    private final int port;
    private final String issuerName;
    private final int generatorThreads;
    private final Path signingKeyPath;

    private final KeyCache cache;
    private final BlockingQueue<GenerateRequest> generateRequestQueue;
    private final BlockingQueue<DataToSend> dataToSendQueue;

    private final PrivateKey signerKey;

    private final ClientHandler clientHandler;
    private final ExecutorService generateExecutor;

    public Server(
            String bindAddress,
            int port,
            String issuerName,
            int generatorThreads,
            Path signingKeyPath
    ) throws IOException {
        this(bindAddress, port, issuerName, generatorThreads, signingKeyPath, KEY_SIZE);
    }

    public Server(
            String bindAddress,
            int port,
            String issuerName,
            int generatorThreads,
            Path signingKeyPath,
            int keySize
    ) throws IOException {

        if (bindAddress == null || bindAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "bind address must not be empty"
            );
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "invalid port: " + port
            );
        }

        if (issuerName == null || issuerName.isBlank()) {
            throw new IllegalArgumentException(
                    "issuer name must not be empty"
            );
        }

        if (generatorThreads <= 0) {
            throw new IllegalArgumentException(
                    "generator threads must be > 0"
            );
        }

        this.bindAddress = bindAddress;
        this.port = port;
        this.issuerName = issuerName;
        this.generatorThreads = generatorThreads;
        this.signingKeyPath = signingKeyPath;

        this.cache = new KeyCache();

        this.generateRequestQueue =
                new LinkedBlockingQueue<>();

        this.dataToSendQueue =
                new LinkedBlockingQueue<>();


        this.signerKey = loadPrivateKey(signingKeyPath);

        this.clientHandler = new ClientHandler(
                bindAddress,
                port,
                cache,
                generateRequestQueue,
                dataToSendQueue
        );


        this.generateExecutor =
                Executors.newFixedThreadPool(
                        generatorThreads,
                        Thread.ofPlatform()
                                .name("key-generator-", 0)
                                .factory()
                );

        Selector selector = clientHandler.selector();

        for (int i = 0; i < generatorThreads; i++) {
            generateExecutor.submit(
                    new ClientDataGenerateWorker(
                            KEY_ALGORITHM,
                            keySize,
                            issuerName,
                            cache,
                            dataToSendQueue,
                            generateRequestQueue,
                            signerKey,
                            selector
                    )
            );
        }
    }

    public void run() {
        clientHandler.run();
    }

    @Override
    public void close() {

        generateExecutor.shutdownNow();

        clientHandler.close();

    }

    private static PrivateKey loadPrivateKey(Path path) {
        if (path == null) {
            throw new IllegalArgumentException(
                    "signing key path must not be null"
            );
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "signing key file does not exist: " + path
            );
        }

        try (var reader = Files.newBufferedReader(path);
             PEMParser parser = new PEMParser(reader)) {

            Object object = parser.readObject();

            if (object instanceof PEMKeyPair keyPair) {
                return new JcaPEMKeyConverter()
                        .getKeyPair(keyPair)
                        .getPrivate();
            }

            if (object instanceof PrivateKeyInfo privateKeyInfo) {
                return new JcaPEMKeyConverter()
                        .getPrivateKey(privateKeyInfo);
            }

        } catch (Exception ignored) {
        }

        try {
            byte[] encoded = Files.readAllBytes(path);

            PrivateKeyInfo privateKeyInfo =
                    PrivateKeyInfo.getInstance(encoded);

            return new JcaPEMKeyConverter()
                    .getPrivateKey(privateKeyInfo);

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "failed to load signing key: " + path,
                    e
            );
        }
    }

    public String bindAddress() {
        return bindAddress;
    }

    public int port() {
        return port;
    }

    public String issuerName() {
        return issuerName;
    }

    public int generatorThreads() {
        return generatorThreads;
    }

    public Path signingKeyPath() {
        return signingKeyPath;
    }
}
package nsu.anisimov.java_t1;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.security.auth.x500.X500Principal;
import nsu.anisimov.java_t1.client.implementation.Client;
import nsu.anisimov.java_t1.server.implementation.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;


abstract class TestBase {

    static final String HOST = "127.0.0.1";
    static final String ISSUER = "CN=Test Issuer";

    static final int KEY_BITS = 1024;

    static final KeyPair ISSUER_KEYS = newRsaKeyPair(2048);

    @TempDir
    Path tmp;

    private final List<Server> servers = new ArrayList<>();
    Thread serverThread;

    @AfterEach
    void stopServers() {
        servers.forEach(Server::close);
    }

    int startServer(int generatorThreads, int keySize) throws Exception {
        return startServer(generatorThreads, keySize, writeSigningKey(ISSUER_KEYS.getPrivate()));
    }

    int startServer(int generatorThreads, int keySize, Path signingKeyFile) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        Server server = new Server(HOST, port, ISSUER, generatorThreads, signingKeyFile, keySize);
        servers.add(server);

        serverThread = new Thread(server::run, "test-server");
        serverThread.setDaemon(true);
        serverThread.start();
        return port;
    }

    void assertServerAlive() {
        assertTrue(serverThread.isAlive(), "server thread has died");
    }

    Path writeSigningKey(PrivateKey key) throws IOException {
        Path file = Files.createTempFile(tmp, "signing", ".key");
        Files.write(file, key.getEncoded());
        return file;
    }

    record KeyFiles(
            byte[] privateKeyBytes,
            byte[] publicKeyBytes,
            byte[] certBytes,
            X509Certificate cert,
            PrivateKey privateKey) {}

    KeyFiles fetch(int port, String name) throws Exception {
        return fetch(port, name, 0);
    }

    KeyFiles fetch(int port, String name, long delaySeconds) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "client");
        new Client(HOST, port, name, delaySeconds, false, dir, "client", "client.crt").run();
        return readKeyFiles(dir);
    }

    void runAbortingClient(int port, String name) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "aborted");
        new Client(HOST, port, name, 0, true, dir, "client", "client.crt").run();
    }

    ExecutorService clientThreads() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    Future<KeyFiles> fetchAsync(ExecutorService threads, int port, String name, long delaySeconds) {
        return threads.submit(() -> fetch(port, name, delaySeconds));
    }

    static KeyFiles await(Future<KeyFiles> future) throws Exception {
        return future.get(60, TimeUnit.SECONDS);
    }

    private static KeyFiles readKeyFiles(Path dir) throws Exception {
        byte[] privateKeyBytes = Files.readAllBytes(dir.resolve("client-private.key"));
        byte[] publicKeyBytes = Files.readAllBytes(dir.resolve("client-private.key"));
        byte[] certBytes = Files.readAllBytes(dir.resolve("client.crt"));
        return parse(privateKeyBytes, publicKeyBytes, certBytes);
    }

    static KeyFiles parse(byte[] privateKeyBytes, byte[] publicKeyBytes, byte[] certBytes) throws Exception {
        X509Certificate cert = parseCertificate(certBytes);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        return new KeyFiles(privateKeyBytes, publicKeyBytes, certBytes, cert, key);
    }

    static X509Certificate parseCertificate(byte[] der) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(der));
    }

    void assertKeysAreValid(KeyFiles files, String expectedName) throws Exception {
        assertKeysAreValid(files, expectedName, KEY_BITS);
    }

    void assertKeysAreValid(KeyFiles files, String expectedName, int expectedKeyBits) throws Exception {
        X509Certificate cert = files.cert();

        cert.verify(ISSUER_KEYS.getPublic());
        cert.checkValidity();
        assertEquals(new X500Principal(ISSUER), cert.getIssuerX500Principal());
        assertEquals(expectedName, commonName(cert));

        RSAPublicKey publicKey = (RSAPublicKey) cert.getPublicKey();
        assertEquals(expectedKeyBits, publicKey.getModulus().bitLength());
        assertEquals(publicKey.getModulus(), ((RSAPrivateKey) files.privateKey()).getModulus(),
                "private key does not match the certificate");
    }

    static String commonName(X509Certificate cert) throws InvalidNameException {
        LdapName subject = new LdapName(cert.getSubjectX500Principal().getName());
        assertEquals(1, subject.size(), "Subject must consist of exactly one attribute");
        return subject.getRdn(0).getValue().toString();
    }

    static KeyPair newRsaKeyPair(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

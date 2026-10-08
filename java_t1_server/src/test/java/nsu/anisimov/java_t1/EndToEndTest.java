package nsu.anisimov.java_t1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 120, unit = TimeUnit.SECONDS)
class EndToEndTest extends TestBase {

    @Test
    void clientReceivesValidKeyAndCertificate() throws Exception {
        int port = startServer(1, KEY_BITS);

        KeyFiles files = fetch(port, "alice");

        assertKeysAreValid(files, "alice");
    }

    @Test
    void sameNameTwiceGivesIdenticalKeys() throws Exception {
        int port = startServer(2, KEY_BITS);

        KeyFiles first = fetch(port, "alice");
        KeyFiles second = fetch(port, "alice");

        assertArrayEquals(first.privateKeyBytes(), second.privateKeyBytes());
        assertArrayEquals(first.publicKeyBytes(), second.publicKeyBytes());
        assertArrayEquals(first.certBytes(), second.certBytes());
    }

    @Test
    void differentNamesGiveDifferentKeys() throws Exception {
        int port = startServer(2, KEY_BITS);

        KeyFiles alice = fetch(port, "alice");
        KeyFiles bob = fetch(port, "bob");

        assertKeysAreValid(alice, "alice");
        assertKeysAreValid(bob, "bob");
        assertFalse(java.util.Arrays.equals(alice.privateKeyBytes(), bob.privateKeyBytes()));
        assertFalse(java.util.Arrays.equals(alice.publicKeyBytes(), bob.publicKeyBytes()));
    }

    @Test
    void duplicateRequestDuringGenerationGetsSameKeys() throws Exception {
        int bits = 3072;
        int port = startServer(1, bits);

        try (ExecutorService clients = clientThreads()) {
            fetchAsync(clients, port, "blocker", 0);
            Thread.sleep(100);
            Future<KeyFiles> first = fetchAsync(clients, port, "dup", 0);
            Future<KeyFiles> second = fetchAsync(clients, port, "dup", 0);

            KeyFiles a = await(first);
            KeyFiles b = await(second);

            assertKeysAreValid(a, "dup", bits);
            assertArrayEquals(a.privateKeyBytes(), b.privateKeyBytes());
            assertArrayEquals(a.publicKeyBytes(), b.publicKeyBytes());
            assertArrayEquals(a.certBytes(), b.certBytes());
        }
    }

    @Test
    void manyClientsWithDifferentNames() throws Exception {
        int port = startServer(4, KEY_BITS);

        try (ExecutorService clients = clientThreads()) {
            List<Future<KeyFiles>> results = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                long delay = (i % 3 == 0) ? 2 : 0;
                results.add(fetchAsync(clients, port, "client-" + i, delay));
            }

            Set<java.math.BigInteger> distinctKeys = new HashSet<>();
            for (int i = 0; i < results.size(); i++) {
                KeyFiles files = await(results.get(i));
                assertKeysAreValid(files, "client-" + i);
                distinctKeys.add(((java.security.interfaces.RSAPublicKey) files.cert().getPublicKey()).getModulus());
            }
            assertEquals(120, distinctKeys.size());
        }
        assertServerAlive();
    }

    @Test
    void manyClientsWithTheSameName() throws Exception {
        int port = startServer(2, KEY_BITS);

        try (ExecutorService clients = clientThreads()) {
            List<Future<KeyFiles>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                results.add(fetchAsync(clients, port, "shared", 0));
            }

            KeyFiles first = await(results.get(0));
            assertKeysAreValid(first, "shared");
            for (Future<KeyFiles> result : results) {
                KeyFiles other = await(result);
                assertArrayEquals(first.privateKeyBytes(), other.privateKeyBytes());
                assertArrayEquals(first.publicKeyBytes(), other.publicKeyBytes());
                assertArrayEquals(first.certBytes(), other.certBytes());
            }
        }
    }

    @Test
    void slowClientDoesNotDelayOthers() throws Exception {
        int port = startServer(2, KEY_BITS);

        try (ExecutorService clients = clientThreads()) {
            Future<KeyFiles> slow = fetchAsync(clients, port, "slowpoke", 4);
            Thread.sleep(200);

            for (int i = 0; i < 5; i++) {
                assertKeysAreValid(fetch(port, "fast-" + i), "fast-" + i);
            }
            assertFalse(slow.isDone(), "the slow client should still be waiting");

            assertKeysAreValid(await(slow), "slowpoke");
        }
    }

    @Test
    void abortedClientsDoNotBreakServer() throws Exception {
        int port = startServer(1, KEY_BITS);

        runAbortingClient(port, "ghost");
        for (int i = 0; i < 30; i++) {
            runAbortingClient(port, "ghost-" + i);
        }

        KeyFiles first = fetch(port, "ghost");
        KeyFiles second = fetch(port, "ghost");

        assertKeysAreValid(first, "ghost");
        assertArrayEquals(first.privateKeyBytes(), second.privateKeyBytes());
        assertArrayEquals(first.publicKeyBytes(), second.publicKeyBytes());
        assertServerAlive();
    }
}
package nsu.anisimov.java_t1.server.utils;

import nsu.anisimov.java_t1.server.utils.data.CacheEntry;
import nsu.anisimov.java_t1.server.utils.data.ClientData;
import nsu.anisimov.java_t1.server.utils.data.DataToSend;
import nsu.anisimov.java_t1.server.utils.data.GenerateRequest;
import nsu.anisimov.java_t1.server.utils.errors.ClientDataGenerationError;

import java.nio.channels.Selector;
import java.security.PrivateKey;
import java.util.concurrent.BlockingQueue;

public class ClientDataGenerateWorker implements Runnable {

    private final String keyAlgo;
    private final int keySize;
    private final String issuerName;
    private final KeyCache cache;
    private final BlockingQueue<DataToSend> dataToSendQueue;
    private final BlockingQueue<GenerateRequest> generateRequestQueue;
    private final PrivateKey signerKey;
    private final Selector selector;

    public ClientDataGenerateWorker(
            String keyAlgo,
            int keySize,
            String issuerName,
            KeyCache cache,
            BlockingQueue<DataToSend> dataToSendQueue,
            BlockingQueue<GenerateRequest> generateRequestQueue,
            PrivateKey signerKey,
            Selector selector
    ) {
        this.keyAlgo = keyAlgo;
        this.keySize = keySize;
        this.issuerName = issuerName;
        this.cache = cache;
        this.dataToSendQueue = dataToSendQueue;
        this.signerKey = signerKey;
        this.generateRequestQueue = generateRequestQueue;
        this.selector = selector;
    }

    @Override
    public void run() {

        while (!Thread.currentThread().isInterrupted()) {

            GenerateRequest request;

            try {
                request = this.generateRequestQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            process(request);
        }
    }

    private void process(GenerateRequest request) {
        CacheEntry entry = request.entry();

        try {

            ClientData data =
                    KeyCertGenerator.generateKeyPairAndCertificate(
                            keyAlgo,
                            keySize,
                            issuerName,
                            request.subjectName(),
                            signerKey
                    );

            dataToSendQueue.add(
                    new DataToSend(request.connKey(), data)
            );

            entry.putResultOrSetNotValid(data, false);

        } catch (Exception e) {
            cache.delete(request.subjectName());
            entry.putResultOrSetNotValid(null, true);

            dataToSendQueue.add(
                    new DataToSend(request.connKey(), null)
            );
        }

        selector.wakeup();
    }
}


package nsu.anisimov.java_t1.server.utils.data;

import java.nio.channels.SelectionKey;

public record DataToSend(SelectionKey connKey, ClientData clientData) {
}

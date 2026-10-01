package nsu.anisimov.java_t1.spider;

import java.util.List;

public class SpiderReport {
    private final List<String> sortedMessages;
    private final List<String> errorMessages;

    public SpiderReport(
            List<String> sortedMessages,
            List<String> errorMessages
    ) {
        this.errorMessages = errorMessages;
        this.sortedMessages = sortedMessages;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public List<String> getSortedMessages() {
        return sortedMessages;
    }

}

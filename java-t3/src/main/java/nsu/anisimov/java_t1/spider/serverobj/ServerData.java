package nsu.anisimov.java_t1.spider.serverobj;

import java.util.List;

public class ServerData {
    private String message;
    private List<String> successors;

    public ServerData() {}

    public ServerData(String text, List<String> items) {
        this.message = text;
        this.successors = items;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getSuccessors() { return successors; }
    public void setSuccessors(List<String> successors) { this.successors = successors; }
}

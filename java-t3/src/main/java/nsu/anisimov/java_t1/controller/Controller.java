package nsu.anisimov.java_t1.controller;

import nsu.anisimov.java_t1.spider.Spider;
import nsu.anisimov.java_t1.spider.SpiderReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class Controller {

    public static final int EXIT_OK = 0;
    public static final int EXIT_ERROR = 1;
    public static final int EXIT_USAGE = 2;

    public static int run(String[] args) {

        if (args == null || args.length != 2) {
            System.err.println("Must be used as [port] [pathToFileForMessages]");
            return EXIT_USAGE;
        }

        int port;
        try {
            port = Integer.parseInt(args[0].trim());
        } catch (NumberFormatException e) {
            System.err.println("Port must be an integer, got: " + args[0]);
            return EXIT_USAGE;
        }
        if (port < 1 || port > 65535) {
            System.err.println("Port must be in range 1..65535, got: " + port);
            return EXIT_USAGE;
        }

        Path outputPath;
        try {
            outputPath = Path.of(args[1]).toAbsolutePath();
        } catch (InvalidPathException e) {
            System.err.println("Invalid output path: " + e.getMessage());
            return EXIT_USAGE;
        }

        SpiderReport report = Spider.parseServer(port);

        if (report.getErrorMessages() != null) {
            System.err.println("Crawling failed, result file was not written:");
            report.getErrorMessages().forEach(m -> System.err.println("  " + m));
            return EXIT_ERROR;
        }

        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(outputPath, report.getSortedMessages(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to write result to " + outputPath + ": " + e.getMessage());
            return EXIT_ERROR;
        }

        System.out.println("Saved " + report.getSortedMessages().size() + " messages to " + outputPath);
        return EXIT_OK;
    }

}

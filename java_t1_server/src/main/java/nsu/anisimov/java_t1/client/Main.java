package nsu.anisimov.java_t1.client;

import nsu.anisimov.java_t1.client.implementation.Client;

import java.nio.file.Path;

public class Main {

    static void main(String[] args) {
        try {
            Config config = parseArgs(args);

            if (config.help()) {
                printUsage();
                return;
            }

            Client client = new Client(
                    config.host(),
                    config.port(),
                    config.name(),
                    config.delaySeconds(),
                    config.abort(),
                    config.outputDirectory(),
                    config.keyFileName(),
                    config.certificateFileName()
            );

            client.run();

            if (config.abort()) {
                System.out.println(
                        "Client terminated without reading response."
                );
            } else {
                System.out.println(
                        "Keys successfully saved to "
                                + config.outputDirectory()
                );
            }

        } catch (IllegalArgumentException e) {
            System.err.println(
                    "Configuration error: " + e.getMessage()
            );
            System.err.println();
            printUsage();

        } catch (Exception e) {
            System.err.println(
                    "Client failed: " + e.getMessage()
            );
        }
    }

    private static Config parseArgs(String[] args) {
        String host = null;
        Integer port = null;
        String name = null;

        Path outputDirectory = Path.of(".");
        String keyFileName = null;
        String certificateFileName = null;

        long delaySeconds = 0;
        boolean abort = false;

        for (int i = 0; i < args.length; i++) {

            switch (args[i]) {

                case "--host" -> {
                    host = requireValue(
                            args,
                            ++i,
                            "--host"
                    );
                }

                case "--port" -> {
                    port = parseInt(
                            requireValue(
                                    args,
                                    ++i,
                                    "--port"
                            ),
                            "--port"
                    );
                }

                case "--name" -> {
                    name = requireValue(
                            args,
                            ++i,
                            "--name"
                    );
                }

                case "--output-dir" -> {
                    outputDirectory = Path.of(
                            requireValue(
                                    args,
                                    ++i,
                                    "--output-dir"
                            )
                    );
                }

                case "--key-file" -> {
                    keyFileName = requireValue(
                            args,
                            ++i,
                            "--key-file"
                    );
                }

                case "--cert-file" -> {
                    certificateFileName = requireValue(
                            args,
                            ++i,
                            "--cert-file"
                    );
                }

                case "--delay" -> {
                    delaySeconds = parseLong(
                            requireValue(
                                    args,
                                    ++i,
                                    "--delay"
                            ),
                            "--delay"
                    );
                }

                case "--abort" -> abort = true;

                case "--help", "-h" -> {
                    return new Config(
                            null,
                            0,
                            null,
                            Path.of("."),
                            null,
                            null,
                            0,
                            false,
                            true
                    );
                }

                default -> throw new IllegalArgumentException(
                        "unknown argument: " + args[i]
                );
            }
        }

        if (host == null) {
            throw new IllegalArgumentException(
                    "missing --host"
            );
        }

        if (port == null) {
            throw new IllegalArgumentException(
                    "missing --port"
            );
        }

        if (name == null) {
            throw new IllegalArgumentException(
                    "missing --name"
            );
        }

        if (keyFileName == null) {
            throw new IllegalArgumentException(
                    "missing --key-file"
            );
        }

        if (certificateFileName == null) {
            throw new IllegalArgumentException(
                    "missing --cert-file"
            );
        }

        if (delaySeconds < 0) {
            throw new IllegalArgumentException(
                    "--delay must be >= 0"
            );
        }

        return new Config(
                host,
                port,
                name,
                outputDirectory,
                keyFileName,
                certificateFileName,
                delaySeconds,
                abort,
                false
        );
    }

    private static String requireValue(
            String[] args,
            int index,
            String option
    ) {
        if (index >= args.length) {
            throw new IllegalArgumentException(
                    "missing value for " + option
            );
        }

        return args[index];
    }

    private static int parseInt(
            String value,
            String option
    ) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "invalid integer for "
                            + option
                            + ": "
                            + value
            );
        }
    }

    private static long parseLong(
            String value,
            String option
    ) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "invalid number for "
                            + option
                            + ": "
                            + value
            );
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java ... Main [options]

                Required:
                  --host <address>       server IP or DNS name
                  --port <port>          server TCP port
                  --name <name>          requested client name
                  --key-file <filename>  private key filename
                  --cert-file <filename> certificate filename

                Optional:
                  --output-dir <path>    output directory (default: .)
                  --delay <seconds>      delay before reading response
                  --abort                terminate without reading response
                  --help                 show this message

                Example:
                  java ... Main \\
                      --host 127.0.0.1 \\
                      --port 9000 \\
                      --name alice \\
                      --output-dir ./keys \\
                      --key-file alice.key \\
                      --cert-file alice.crt

                Slow client:
                  java ... Main \\
                      --host 127.0.0.1 \\
                      --port 9000 \\
                      --name alice \\
                      --output-dir ./keys \\
                      --key-file alice.key \\
                      --cert-file alice.crt \\
                      --delay 10

                Abnormal termination:
                  java ... Main \\
                      --host 127.0.0.1 \\
                      --port 9000 \\
                      --name alice \\
                      --output-dir ./keys \\
                      --key-file alice.key \\
                      --cert-file alice.crt \\
                      --abort
                """);
    }

    private record Config(
            String host,
            int port,
            String name,
            Path outputDirectory,
            String keyFileName,
            String certificateFileName,
            long delaySeconds,
            boolean abort,
            boolean help
    ) {
    }
}
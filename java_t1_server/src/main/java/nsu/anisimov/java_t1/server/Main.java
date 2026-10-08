package nsu.anisimov.java_t1.server;

import nsu.anisimov.java_t1.server.implementation.Server;

import java.nio.file.Path;

public class Main {

    static void main(String[] args) {
        try {
            Config config = parseArgs(args);

            if (config.help()) {
                printUsage();
                return;
            }

            Server server = new Server(
                    config.host(),
                    config.port(),
                    config.issuer(),
                    config.workers(),
                    config.key()
            );

            Runtime.getRuntime().addShutdownHook(
                    new Thread(
                            server::close,
                            "server-shutdown"
                    )
            );

            System.out.printf(
                    "Server started on %s:%d with %d generator threads%n",
                    config.host(),
                    config.port(),
                    config.workers()
            );

            server.run();

        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.err.println();
            printUsage();
        } catch (Exception e) {
            System.err.println("Server failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Config parseArgs(String[] args) {
        String host = null;
        Integer port = null;
        String issuer = null;
        Integer workers = null;
        Path key = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> {
                    host = requireValue(args, ++i, "--host");
                }

                case "--port" -> {
                    port = parseInt(
                            requireValue(args, ++i, "--port"),
                            "--port"
                    );
                }

                case "--issuer" -> {
                    issuer = requireValue(args, ++i, "--issuer");
                }

                case "--workers" -> {
                    workers = parseInt(
                            requireValue(args, ++i, "--workers"),
                            "--workers"
                    );
                }

                case "--key" -> {
                    key = Path.of(
                            requireValue(args, ++i, "--key")
                    );
                }

                case "--help", "-h" -> {
                    return new Config(
                            null,
                            0,
                            null,
                            0,
                            null,
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

        if (issuer == null) {
            throw new IllegalArgumentException(
                    "missing --issuer"
            );
        }

        if (workers == null) {
            throw new IllegalArgumentException(
                    "missing --workers"
            );
        }

        if (key == null) {
            throw new IllegalArgumentException(
                    "missing --key"
            );
        }

        return new Config(
                host,
                port,
                issuer,
                workers,
                key,
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
                    "invalid integer for " + option + ": " + value
            );
        }
    }

    private static void printUsage() {
        System.out.println("""
                Options:
                  --host <address>   address to bind server
                  --port <port>      TCP port
                  --issuer <name>    X.509 issuer name
                  --workers <count>  RSA generator thread count
                  --key <path>       signing private key
                  --help             show this message

                Example:
                  java ... Main \\
                      --host 0.0.0.0 \\
                      --port 9000 \\
                      --issuer "CN=NSU Key Server" \\
                      --workers 8 \\
                      --key server.key
                """);
    }

    private record Config(
            String host,
            int port,
            String issuer,
            int workers,
            Path key,
            boolean help
    ) {
    }
}
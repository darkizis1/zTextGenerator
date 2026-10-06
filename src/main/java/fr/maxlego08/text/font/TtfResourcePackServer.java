package fr.maxlego08.text.font;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Very small http server used to share the generated resource pack with the players.
 *
 * <p>It is only started when {@code ttf.resource-pack.auto-host} is enabled, otherwise the
 * administrator has to upload the generated pack somewhere and to fill the
 * {@code ttf.resource-pack.url} option.</p>
 */
public class TtfResourcePackServer {

    private final HttpServer server;
    private final File file;
    private final String path;
    private final int port;

    public TtfResourcePackServer(String host, int port, File file, Logger logger) throws IOException {

        this.file = file;
        this.path = "/" + file.getName();
        this.port = port;

        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext(this.path, exchange -> {

            try {

                byte[] content = Files.readAllBytes(this.file.toPath());
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, content.length);

                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(content);
                }

            } catch (Exception exception) {
                logger.warning("Unable to send the resource pack : " + exception.getMessage());
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });

        this.server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "zTextGenerator-resource-pack");
            thread.setDaemon(true);
            return thread;
        }));

        this.server.start();
    }

    /**
     * Gets the public url of the pack.
     *
     * @param address the address the players have to use to reach the server
     * @return the url of the generated pack
     */
    public String getUrl(String address) {
        return "http://" + address + ":" + this.port + this.path;
    }

    public void stop() {
        this.server.stop(0);
    }
}

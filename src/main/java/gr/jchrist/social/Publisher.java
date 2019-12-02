package gr.jchrist.social;

import gr.jchrist.social.conf.ConfigProvider;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Publisher {
    private static final Logger logger = Logger.getLogger(Publisher.class.getName());
    private final BlockingQueue<String> notifQueue;
    private final HttpClient client;
    private volatile boolean keepRunning;
    private final URI chatUri;

    public Publisher(ConfigProvider cp, BlockingQueue<String> notifQueue) {
        this.notifQueue = notifQueue;
        this.keepRunning = true;
        this.chatUri = URI.create(cp.getChatUrl());
        this.client = HttpClient.newHttpClient();
    }

    public void init(ExecutorService exec) {
        exec.submit(this::consumeQueueAndPublish);
    }

    public void stop() {
        this.keepRunning = false;
    }

    protected void consumeQueueAndPublish() {
        while (keepRunning) {
            try {
                String msg = notifQueue.poll(10, TimeUnit.SECONDS);
                if (msg != null && !msg.isEmpty() && !msg.isBlank()) {
                    publishMessage(msg);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "error getting message from queue", e);
            }
        }
    }

    protected void publishMessage(String msg) {
        logger.fine(() -> "publishing message: " + msg);
        try {
            String gchatJsonMsg = convertToGchat(msg);
            logger.info(() -> "sending to gchat: " + gchatJsonMsg);
            var req = HttpRequest.newBuilder(chatUri)
                    .POST(HttpRequest.BodyPublishers.ofString(gchatJsonMsg))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            logger.fine(() -> "received response: " + resp.statusCode() + " " + resp.body());
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "error creating request for msg: " + msg);
        }
    }

    protected String convertToGchat(String msg) {
        JSONObject pub = new JSONObject();
        pub.put("text", msg);

        return pub.toString();
    }
}

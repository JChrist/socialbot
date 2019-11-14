package gr.jchrist.social;

import com.google.common.base.Strings;
import gr.jchrist.social.conf.ConfigProvider;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class Publisher {
    private static final Logger logger = LoggerFactory.getLogger(Publisher.class);
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final ConfigProvider cp;
    private final BlockingQueue<String> notifQueue;
    private final OkHttpClient client;
    private volatile boolean keepRunning;

    public Publisher(ConfigProvider cp, BlockingQueue<String> notifQueue) {
        this.cp = cp;
        this.notifQueue = notifQueue;
        this.keepRunning = true;
        this.client = new OkHttpClient();
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
                if (!Strings.isNullOrEmpty(msg)) {
                    publishMessage(msg);
                }
            } catch (Exception e) {
                logger.warn("error getting message from queue", e);
            }
        }
    }

    protected void publishMessage(String msg) {
        logger.debug("publishing message: {}", msg);
        try {
            if (Strings.isNullOrEmpty(msg)) {
                return;
            }

            String gchatJsonMsg = convertToGchat(msg);
            logger.info("sending to gchat: {}", gchatJsonMsg);
            var rb = RequestBody.create(gchatJsonMsg, JSON);
            var r = new Request.Builder().url(cp.getChatUrl()).post(rb).build();
            try(Response resp = client.newCall(r).execute()) {
                logger.debug("received response: {} {}", resp.code(),
                        resp.body() != null ? resp.body().string() : "<null>");
            }
        } catch (Exception e) {
            logger.warn("error creating request for msg:{}", msg, e);
        }
    }

    protected String convertToGchat(String msg) {
        JSONObject pub = new JSONObject();
        pub.put("text", msg);

        return pub.toString();
    }
}

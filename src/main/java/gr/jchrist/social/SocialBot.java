package gr.jchrist.social;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import gr.jchrist.social.conf.ConfigProvider;

public class SocialBot {
    private static final Logger logger = Logger.getLogger(SocialBot.class.getName());
    private final ConfigProvider cp;
    private final ScheduledExecutorService schedExec;
    private final ExecutorService pubExec;
    private final ListPoller poller;
    private final Publisher publisher;
    private final BlockingQueue<String> notifQueue;

    public SocialBot(Map<String, String> env) {
        this.cp = new ConfigProvider(env);
        schedExec = Executors.newSingleThreadScheduledExecutor();
        pubExec = Executors.newSingleThreadExecutor();
        notifQueue = new ArrayBlockingQueue<>(100000);
        poller = new ListPoller(cp, schedExec, notifQueue);
        this.publisher = new Publisher(cp, notifQueue);
    }

    public void start() {
        this.poller.init();
        this.publisher.init(pubExec);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        this.poller.stop();
        this.publisher.stop();
        this.schedExec.shutdownNow();
        this.pubExec.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        if (logger.getParent().getHandlers().length == 0) {
            logger.getParent().addHandler(new ConsoleHandler());
        }
        for (var h : logger.getParent().getHandlers()) {
            h.setLevel(Level.INFO);
        }
        logger.info("starting up application");
        Map<String, String> env = new HashMap<>(System.getenv());
        env.putAll(System.getProperties().stringPropertyNames().stream()
            .collect(Collectors.toMap(k -> k, System::getProperty)));
        SocialBot sb = new SocialBot(env);
        sb.start();
    }
}

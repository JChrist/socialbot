package gr.jchrist.social;

import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Strings;
import gr.jchrist.social.conf.ConfigProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

public class ListPoller {
    private static final Logger logger = LoggerFactory.getLogger(ListPoller.class);
    private final ConfigProvider cp;
    private final BlockingQueue<String> notifQueue;
    private final Twitter twitter;
    private final ScheduledExecutorService schedExec;
    protected long lastId;
    protected Future future;

    public ListPoller(ConfigProvider cp, ScheduledExecutorService schedExec, BlockingQueue<String> notifQueue) {
        this.cp = cp;
        this.schedExec = schedExec;
        /* Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
        this.notifQueue = notifQueue;
        ConfigurationBuilder cb = new ConfigurationBuilder()
                .setDebugEnabled(false)
                .setOAuthConsumerKey(cp.getConsumerApiKey())
                .setOAuthConsumerSecret(cp.getConsumerApiSecretKey())
                .setOAuthAccessToken(cp.getAccessToken())
                .setOAuthAccessTokenSecret(cp.getAccessTokenSecret());
        TwitterFactory tf = new TwitterFactory(cb.build());
        this.twitter = tf.getInstance();
        this.lastId = -1;
    }

    public void init() {
        try {
            logger.info("initializing polling list with id: {}", cp.getListId());
            var rls = twitter.list().getUserListStatuses(cp.getListId(), new Paging(1, 1_000));
            Date lastCreatedAt = new Date(0);
            for (var s : rls) {
                if (s.getCreatedAt().after(lastCreatedAt)) {
                    lastId = s.getId();
                    lastCreatedAt = s.getCreatedAt();
                }
            }
            logger.info("initialized list polling with last id: {}", lastId);
            future = schedExec.scheduleWithFixedDelay(this::getListStatuses, 1, 1, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("error during initialization trying to find last tweet id", e);
            throw new IllegalArgumentException("error trying to find last id for list: " + cp.getListId(), e);
        }
    }

    public void stop() {
        if (future != null) {
            future.cancel(true);
        }
    }

    public void getListStatuses() {
        try {
            var rls = twitter.list().getUserListStatuses(cp.getListId(), new Paging(1, 1, lastId));
            if (rls == null) {
                return;
            }
            rls.sort(Comparator.comparingLong(Status::getId));
            for (var s : rls) {
                if (s.getId() <= lastId) {
                    continue;
                }
                pushStatus(s);
            }
            lastId = rls.get(rls.size() - 1).getId();
        } catch (Exception e) {
            logger.warn("error getting list statuses", e);
        }
    }

    protected void pushStatus(Status s) {
        String msg = parseStatus(s);
        logger.debug("converted status: {} to msg: {}", s, msg);
        notifQueue.add(msg);
    }

    protected String parseStatus(Status s) {
        String usr = s.getUser().getScreenName();
        String name = s.getUser().getName();
        String statusUrl;
        String text = s.getText();
        String retweetDetails = "";
        String tweetVerb = "tweeted";
        boolean retweet = s.isRetweet();
        if (retweet) {
            tweetVerb = "re" + tweetVerb;
            Status rt = s.getRetweetedStatus();
            text = rt.getText();
            String rtUsrHandle = rt.getUser().getScreenName();
            retweetDetails = "<https://twitter.com/@" + rtUsrHandle + "|" + rt.getUser().getName() + " @" + rtUsrHandle + ">\n";
            statusUrl = "https://twitter.com/" + rtUsrHandle + "/status/" + rt.getId();
        } else {
            statusUrl = "https://twitter.com/" + usr + "/status/" + s.getId();
        }
        text = replaceUrls(s, text);
        String postedMessage = statusUrl + "\n<https://twitter.com/@" + usr + "|@" + name + " (" + usr + ")> " + tweetVerb + ":\n" + retweetDetails + text;
        logger.debug("message after parsing: {}", postedMessage);
        return postedMessage;
    }

    protected String replaceUrls(Status s, String msg) {
        if (s.getURLEntities() != null && s.getURLEntities().length > 0) {
            for (URLEntity url : s.getURLEntities()) {
                String turl = url.getURL();
                String expanded = url.getExpandedURL();
                String display = url.getDisplayURL();
                msg = msg.replace(turl, "<" + expanded + "|" +display + ">");
            }
        }
        if (s.getMediaEntities() != null && s.getMediaEntities().length > 0) {
            for (MediaEntity med : s.getMediaEntities()) {
                String turl = med.getURL();
                String medUrl = med.getMediaURLHttps();
                if (Strings.isNullOrEmpty(medUrl)) {
                    medUrl = med.getMediaURL();
                }
                msg = msg.replace(turl, medUrl);
            }
        }

        return msg;
    }
}

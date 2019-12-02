package gr.jchrist.social;

import gr.jchrist.social.conf.ConfigProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ListPoller {
    private static final Logger logger = Logger.getLogger(ListPoller.class.getName());
    public static final URI TOKEN_URI = URI.create("https://api.twitter.com/oauth2/token");
    public static final String LIST_URL = "https://api.twitter.com/1.1/lists/statuses.json";
    private final ConfigProvider cp;
    private final BlockingQueue<String> notifQueue;
    private final ScheduledExecutorService schedExec;
    private final HttpClient httpClient;
    protected String token;
    protected long lastId;
    protected Future future;

    public ListPoller(ConfigProvider cp, ScheduledExecutorService schedExec, BlockingQueue<String> notifQueue) {
        this.cp = cp;
        this.schedExec = schedExec;
        /* Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
        this.notifQueue = notifQueue;
        httpClient = HttpClient.newHttpClient();
        this.lastId = -1;
    }

    public void init() {
        try {
            obtainToken();
            logger.info(() -> "initializing polling list with id: " + cp.getListId());
            getListStatuses();
            logger.info(() -> "initialized polling list: " + cp.getListId() + " with last id: " + lastId);
            future = schedExec.scheduleWithFixedDelay(this::getAndPushListStatuses, 1, 1, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e, ()->"error initializing to poll list: " + cp.getListId());
            throw new IllegalArgumentException("error initializing to poll list: " + cp.getListId(), e);
        }
    }

    public void stop() {
        if (future != null) {
            future.cancel(true);
        }
    }

    public Collection<JSONObject> getListStatuses() {
        try {
            String listUrl = LIST_URL + "?list_id=" + cp.getListId() + "&count=1000";
            if (lastId > 0) {
                if (cp.getIncludeRts()) {
                    listUrl += "&include_rts=true";
                }
                listUrl += "&since_id=" + lastId;
            }
            var req = HttpRequest.newBuilder(URI.create(listUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                logger.warning(() -> "error getting list statuses, received status code: " + resp.statusCode() +
                        " with body:{}" + resp.body());
                return null;
            }
            var sts = new JSONArray(resp.body());
            TreeSet<JSONObject> tr = new TreeSet<>(Comparator.comparingLong(js -> js.getLong("id")));
            for (var st : sts) {
                JSONObject js = (JSONObject) st;
                tr.add(js);
            }

            if (!tr.isEmpty()) {
                lastId = tr.last().getLong("id");
            }

            return tr;
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () ->"error getting list statuses");
            return Collections.emptyList();
        }
    }

    public void getAndPushListStatuses() {
        getListStatuses().forEach(this::pushStatus);
    }

    protected void obtainToken() throws Exception {
        var authHeader = Base64.getEncoder().encodeToString((cp.getConsumerApiKey() + ":" + cp.getConsumerApiSecretKey()).getBytes());
        var req = HttpRequest.newBuilder()
                .uri(TOKEN_URI)
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + authHeader)
                .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            logger.severe(() -> "received error response while obtaining token with code: " + resp.statusCode() +
                    " and body: {}" + resp.body());
            throw new IllegalArgumentException("received error response while obtaining token with code: " +
                    resp.statusCode() + " and body: " + resp.body());
        }
        token = new JSONObject(resp.body()).getString("access_token");
        logger.info(() -> "successfully obtained token");
    }

    protected void pushStatus(JSONObject s) {
        String msg = parseStatus(s);
        logger.fine(() -> "converted status: " + s + " to msg: " + msg);
        notifQueue.add(msg);
    }

    protected String parseStatus(JSONObject s) {
        String usr = s.getJSONObject("user").getString("screen_name");
        String name = s.getJSONObject("user").getString("name");
        String statusUrl;
        String text = s.getString("text");
        String retweetDetails = "";
        String tweetVerb = "tweeted";
        boolean retweet = text.startsWith("RT");
        if (retweet) {
            tweetVerb = "re" + tweetVerb;
            JSONObject rt = s.getJSONObject("retweeted_status");
            text = rt.getString("text");
            String rtUsrHandle = rt.getJSONObject("user").getString("screen_name");
            retweetDetails = "<https://twitter.com/@" + rtUsrHandle + "|" + rt.getJSONObject("user").getString("name") + " @" + rtUsrHandle + ">\n";
            statusUrl = "https://twitter.com/" + rtUsrHandle + "/status/" + rt.getLong("id");
        } else {
            statusUrl = "https://twitter.com/" + usr + "/status/" + s.getLong("id");
        }
        text = replaceUrls(s, text);
        String postedMessage = statusUrl + "\n<https://twitter.com/@" + usr + "|@" + name + " (" + usr + ")> " + tweetVerb + ":\n" + retweetDetails + text;
        logger.fine(() -> "message after parsing: " + postedMessage);
        return postedMessage;
    }

    protected String replaceUrls(JSONObject s, String msg) {
        if (!s.has("entities")) {
            return msg;
        }
        JSONObject entities = s.getJSONObject("entities");
        if (entities.has("urls")) {
            for (var urlObj : entities.getJSONArray("urls")) {
                JSONObject url = (JSONObject) urlObj;
                String turl = url.getString("url");
                String expanded = url.getString("expanded_url");
                String display = url.getString("display_url");
                msg = msg.replace(turl, "<" + expanded + "|" +display + ">");
            }
        }
        if (entities.has("media")) {
            for (var medObj : entities.getJSONArray("media")) {
                var med = (JSONObject) medObj;
                String turl = med.getString("url");
                String medUrl = med.getString("media_url_https");
                if (medUrl == null || medUrl.isEmpty() || medUrl.isBlank()) {
                    medUrl = med.getString("media_url");
                }
                msg = msg.replace(turl, medUrl);
            }
        }

        return msg;
    }
}

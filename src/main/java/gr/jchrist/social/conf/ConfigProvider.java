package gr.jchrist.social.conf;

import java.util.HashMap;
import java.util.Map;

public class ConfigProvider {
    public static final String CONSUMER_API_KEY = "CONSUMER_API_KEY";
    public static final String CONSUMER_API_SECRET_KEY = "CONSUMER_API_SECRET_KEY";
    public static final String WEBHOOK_URL = "WEBHOOK_URL";
    public static final String TW_LIST_ID = "TW_LIST_ID";
    public static final String TW_INCLUDE_RTS = "TW_INCLUDE_RTS";

    private final Map<String, String> props;

    public ConfigProvider(Map<String, String> env) {
        this.props = new HashMap<>(env);
    }

    public String getConsumerApiKey() {
        return getProperty(CONSUMER_API_KEY);
    }

    public String getConsumerApiSecretKey() {
        return getProperty(CONSUMER_API_SECRET_KEY);
    }

    public String getChatUrl() {
        return getProperty(WEBHOOK_URL);
    }

    public Long getListId() {
        return Long.valueOf(getProperty(TW_LIST_ID));
    }

    public boolean getIncludeRts() {
        return !"false".equalsIgnoreCase(getProperty(TW_INCLUDE_RTS));
    }

    private String getProperty(String propKey) {
        return props.computeIfAbsent(propKey, k -> props.get(k.toLowerCase()));
    }
}

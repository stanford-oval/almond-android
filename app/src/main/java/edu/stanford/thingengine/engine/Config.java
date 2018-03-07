package edu.stanford.thingengine.engine;

import java.util.Locale;

/**
 * Created by gcampagn on 7/6/16.
 */
public class Config {
    private Config() {}

    public static String getLanguage() {
        String locale = Locale.getDefault().toString();
        switch (locale) {
            case "en_US":
                return "en-us";
            case "zh_CN_#Hans":
                return "zh-cn";
            case "zh_TW_#Hant":
                return "zh-tw";
            case "it_IT":
                return "it-it";
            default:
                return "en-us";
        }
    }

    public static final String THINGPEDIA_URL = "https://crowdie.stanford.edu/thingpedia";
}

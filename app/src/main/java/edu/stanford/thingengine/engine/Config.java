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
            case "it_IT":
                return "it-it";
            default:
                return "en-us";
        }
    }

    // this is only used by the speech recognition
    // FIXME: make it a build property, or make it runtime detected
    public static final String LOCALE = "en-us";

    public static final String S3_CLOUDFRONT_HOST = "https://d1ge76rambtuys.cloudfront.net";

    public static final String MS_SPEECH_RECOGNITION_PRIMARY_KEY = "14661070ff9c496398a09c939b41bd72";
    public static final String MS_SPEECH_RECOGNITION_SECONDARY_KEY = "155d0afd2d084be48dbe18dc8844cfa8";
}

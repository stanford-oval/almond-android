package edu.stanford.thingengine.engine;

/**
 * Created by gcampagn on 10/7/15.
 */
public class CloudAuthInfo {
    private final String cloudId;
    private final String authToken;

    public CloudAuthInfo(String cloudId, String authToken) {
        this.cloudId = cloudId;
        this.authToken = authToken;
    }

    public boolean isValid() {
        return cloudId != null && authToken != null;
    }

    public String getCloudId() {
        return cloudId;
    }

    public String getAuthToken() {
        return authToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CloudAuthInfo that = (CloudAuthInfo) o;

        if (cloudId != null ? !cloudId.equals(that.cloudId) : that.cloudId != null) return false;
        return authToken != null ? authToken.equals(that.authToken) : that.authToken == null;

    }

    @Override
    public int hashCode() {
        int result = cloudId != null ? cloudId.hashCode() : 0;
        result = 31 * result + (authToken != null ? authToken.hashCode() : 0);
        return result;
    }
}

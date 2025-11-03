package models;

public class SourceProfile {
    private final String sourceName;
    private final String url;
    private final String description;

    public SourceProfile(String sourceName, String url, String description) {
        this.sourceName = sourceName;
        this.url = url;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public String getSourceName() {
        return sourceName;
    }
}

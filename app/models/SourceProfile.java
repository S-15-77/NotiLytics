package models;

/**
 * Represents information in a profile to share about the source website for an article.
 * @author Haytham
 */
public class SourceProfile {
    /** Name of the Source */
    private final String sourceName;
    /** Website URL of the Source */
    private final String url;
    /** A short description for the source of the Source */
    private final String description;

    /**
     * Constructs a Source Profile object
     * @param sourceName
     * @param url
     * @param description
     * @author Haytham
     */
    public SourceProfile(String sourceName, String url, String description) {
        this.sourceName = sourceName;
        this.url = url;
        this.description = description;
    }

    /**
     * Retrieves the Description of a Source
     * @return Source Description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Retrieves the Url of a Source
     * @return Source Url
     */
    public String getUrl() {
        return url;
    }

    /**
     * Retrieves the Name of a Source
     * @return Source Name
     */
    public String getSourceName() {
        return sourceName;
    }
}

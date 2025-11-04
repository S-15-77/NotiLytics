package models;

/**
 * Represents a single news article.
 * Group Part – NotiLytics
 * @author Santhosh
 */
public class Article {
    /** Title of the article. */
    private final String title;
    /** URL of the article. */
    private final String url;
    /** Name of the source. */
    private final String sourceName;
    /** URL of the source. */
    private final String sourceUrl;
    /** Published date of the article. */
    private final String publishedAt;
    /** Flesch-Kincaid Grade Level. */
    private final int kincaidGrade;
    /** Flesch Reading Score. */
    private final int readingScore;
    /** Article description**/
    private final String description; //added for stats

    /**
     * Constructs an Article object.
     * @param title Title of the article.
     * @param url URL of the article.
     * @param sourceName Name of the source.
     * @param sourceUrl URL of the source.
     * @param publishedAt Published date.
     * @param kincaidGrade Flesch-Kincaid Grade Level.
     * @param readingScore Flesch Reading Score.
     * @author Santhosh
     */
    public Article(String title, String url, String sourceName, String sourceUrl, String publishedAt,int kincaidGrade, int readingScore, String description) {
        this.title = title;
        this.url = url;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.publishedAt = publishedAt;
        this.kincaidGrade = kincaidGrade;
        this.readingScore = readingScore;
        this.description = description;
    }

    /**
     * Gets the title of the article.
     * @return Article title.
     * @author Santhosh
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gets the URL of the article.
     * @return Article URL.
     * @author Santhosh
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the source name.
     * @return Source name.
     * @author Santhosh
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Gets the source URL.
     * @return Source URL.
     * @author Santhosh
     */
    public String getSourceUrl() {
        return sourceUrl;
    }

    /**
     * Gets the published date.
     * @return Published date.
     * @author Santhosh
     */
    public String getPublishedAt() {
        return publishedAt;
    }

    /**
     * Gets the Flesch-Kincaid Grade Level.
     * @return Grade level.
     * @author Santhosh
     */
    public int getKincaidGrade() {
        return kincaidGrade;
    }

    /**
     * Gets the Flesch Reading Score.
     * @return Reading score.
     * @author Santhosh
     */
    public int getReadingScore() {
        return readingScore;
    }

    /**
     * Gets the description of the Article, used in the Statistics.
     * @return A String description.
     * @author Karim BG
     */
    public String getDescription() {
        return description;
    }
}

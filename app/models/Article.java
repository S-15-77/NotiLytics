package models;

/**
 * Represents a single news article.
 * Group Part – NotiLytics
 * @author Team
 */
public class Article {
    private final String title;
    private final String url;
    private final String sourceName;
    private final String sourceUrl;
    private final String publishedAt;
    private final int kincaidGrade;
    private final int readingScore;

    public Article(String title, String url, String sourceName, String sourceUrl, String publishedAt,int kincaidGrade, int readingScore) {
        this.title = title;
        this.url = url;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.publishedAt = publishedAt;
        this.kincaidGrade = kincaidGrade;
        this.readingScore = readingScore;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getPublishedAt() {
        return publishedAt;
    }
    public int getKincaidGrade() {
        return kincaidGrade;
    }
    public int getReadingScore() {
        return readingScore;
    }
}

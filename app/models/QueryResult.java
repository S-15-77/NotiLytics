package models;

import java.util.List;

/**
 * Represents the result of a news search query, including articles and readability averages.
 * @author Santhosh
 */
public class QueryResult {
    private final String query;
    private final List<Article> articles;
    private final double avgGrade;
    private final double avgScore;

    /**
     * Constructs a QueryResult for a search query.
     * @param query The search query string.
     * @param articles List of articles returned for the query.
     * @param avgGrade Average Flesch-Kincaid Grade Level for the articles.
     * @param avgScore Average Flesch Reading Score for the articles.
     * @author Santhosh
     */
    public QueryResult(String query, List<Article> articles, double avgGrade, double avgScore) {
        this.query = query;
        this.articles = articles;
        this.avgGrade = avgGrade;
        this.avgScore = avgScore;
    }

    /**
     * Gets the search query string.
     * @return The query string.
     * @author Santhosh
     */
    public String getQuery() {
        return query;
    }

    /**
     * Gets the list of articles for the query.
     * @return List of articles.
     * @author Santhosh
     */
    public List<Article> getArticles() {
        return articles;
    }

    /**
     * Gets the average Flesch-Kincaid Grade Level for the articles.
     * @return Average grade level.
     * @author Santhosh
     */
    public double getAvgGrade() {
        return avgGrade;
    }

    /**
     * Gets the average Flesch Reading Score for the articles.
     * @return Average reading score.
     * @author Santhosh
     */
    public double getAvgScore() {
        return avgScore;
    }
}

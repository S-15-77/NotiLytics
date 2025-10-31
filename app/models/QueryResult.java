package models;

import java.util.List;

public class QueryResult {
    private final String query;
    private final List<Article> articles;
    private final double avgGrade;
    private final double avgScore;

    public QueryResult(String query, List<Article> articles, double avgGrade, double avgScore) {
        this.query = query;
        this.articles = articles;
        this.avgGrade = avgGrade;
        this.avgScore = avgScore;
    }

    public String getQuery() {
        return query;
    }

    public List<Article> getArticles() {
        return articles;
    }

    public double getAvgGrade() {
        return avgGrade;
    }

    public double getAvgScore() {
        return avgScore;
    }
}


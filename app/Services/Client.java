package Services;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.ws.*;
import javax.inject.Inject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import models.Article;
import models.Source;

/**
 * Service class that handles asynchronous API calls and parsing.
 */
public class Client implements WSBodyReadables, WSBodyWritables {

    private final WSClient ws;

    @Inject
    public Client(WSClient ws) {
        this.ws = ws;
    }

    /**
     * Fetches and parses NewsAPI articles asynchronously.
     *
     * @param url NewsAPI request URL
     * @return CompletionStage<List<Article>>
     */
    public CompletionStage<List<Article>> clientRequest(String url) {

        WSRequest request = ws.url(url).setRequestTimeout(Duration.ofSeconds(5));

        return request.get().thenApply(response -> {
            if (response.getStatus() != 200) {
                System.out.println("Error: " + response.getStatusText());
                return Collections.emptyList();
            }

            JsonNode json = response.asJson();
            JsonNode articlesNode = json.get("articles");

            if (articlesNode == null || !articlesNode.isArray()) {
                return Collections.emptyList();
            }

            // Parse top 10 articles with Java Streams
            return StreamSupport.stream(articlesNode.spliterator(), false)
                    .limit(10)
                    .map(articleNode -> {
                        String title = articleNode.get("title").asText("No title");
                        String urlToArticle = articleNode.get("url").asText("#");
                        String sourceName = articleNode.get("source").get("name").asText("Unknown Source");
                        String sourceUrl = buildSourceUrl(sourceName);
                        String publishedAt = convertToEDT(articleNode.get("publishedAt").asText("Unknown Date"));
                        int kincaidGrade = 5;
                        int readingScore = 5;

                        return new Article(title, urlToArticle, sourceName, sourceUrl, publishedAt,kincaidGrade, readingScore);
                    })
                    .collect(Collectors.toList());

        });
    }

    /**
     * Returns a list of all sources available on NewsAPI
     * @param requestUrl requestUrl for news api
     * @return Promise of a list of all sources
     */
    public CompletionStage<List<Source>> fetchSources(String requestUrl) {
        return ws.url(requestUrl)
                .get()
                .thenApply(response -> {
                    JsonNode json = response.asJson();
                    JsonNode sourcesNode = json.get("sources");

                    List<Source> sources = new ArrayList<>();
                    if (sourcesNode != null && sourcesNode.isArray()) {
                        for (JsonNode node : sourcesNode) {
                            Source source = new Source(
                                    node.get("id").asText(),
                                    node.get("name").asText(),
                                    node.has("description") ? node.get("description").asText() : "",
                                    node.get("url").asText(),
                                    node.get("category").asText(),
                                    node.get("language").asText(),
                                    node.get("country").asText()
                            );
                            sources.add(source);
                        }
                    }
                    return sources;
                });
    }

    /** Converts UTC date to EDT */
    private String convertToEDT(String utcDate) {
        try {
            Instant instant = Instant.parse(utcDate);
            ZonedDateTime edtTime = instant.atZone(ZoneId.of("America/Toronto"));
            return edtTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm:ss"));
        } catch (Exception e) {
            return "Unknown Date";
        }
    }

    /** Builds a valid hyperlink for the source website */
    private String buildSourceUrl(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "#";
        String normalized = sourceName.toLowerCase().replaceAll("\\s+", "");
        return "https://www." + normalized + ".com";
    }
}
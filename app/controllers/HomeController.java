package controllers;

import models.Article;
import models.QueryResult;
import models.ReadabilityCalculator;
import play.mvc.*;
import play.libs.ws.*;
import com.typesafe.config.Config;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import Services.Client;

public class HomeController extends Controller {
    private final WSClient ws;
    private final Executor executor;
    private final String Key;
    private final String url;
    private static final String SESSION_KEY = "queries";

    /** Read queries stored in user session */
    private List<String> getPreviousQueries(Http.Session session) {
        String data = session.get(SESSION_KEY).orElse("");
        if (data == null || data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }

    /** Store new query at top, remove duplicates, keep at most 10 */
    private Http.Session updateSession(Http.Session session, String newQuery) {
        List<String> queries = getPreviousQueries(session);
        queries.remove(newQuery);       // avoid duplicates
        queries.add(0, newQuery);       // add newest at top (ArrayList)
        if (queries.size() > 10)        // limit 10
            queries = queries.subList(0, 10);
        return session.adding(SESSION_KEY, String.join(",", queries));
    }

    @Inject
    public HomeController(WSClient ws, Executor executor, Config config) {
        this.ws = ws;
        this.executor = executor;
        this.Key = config.getString("newsapi.key");
        this.url = config.getString("newsapi.url");
    }

    public CompletionStage<Result> index(Http.Request request) {
        // show welcome page with no results
        Map<String, QueryResult> empty = new LinkedHashMap<>();
        return CompletableFuture.completedFuture(ok(views.html.index.render("Welcome to NotiLytics! Enter your search terms below.", empty)));
    }

    public CompletionStage<Result> search(Http.Request request) {
        // Read query params from the request
        String searchInput = request.getQueryString("SearchInput");
        String sortBy = Optional.ofNullable(request.getQueryString("sortBy")).orElse("publishedAt");

        if (searchInput == null || searchInput.trim().isEmpty()) {
            // No search provided - render the index page (don't return badRequest text)
            Map<String, QueryResult> empty = new LinkedHashMap<>();
            return CompletableFuture.completedFuture(ok(views.html.index.render("Please enter a search term.", empty)));
        }

        // Update session with new query
        Http.Session updatedSession = updateSession(request.session(), searchInput);
        List<String> queries = getPreviousQueries(updatedSession);

        // Create async requests for all stored queries to display each search separately
        List<CompletionStage<Map.Entry<String, QueryResult>>> futures = queries.stream()
                .map(query -> {
                    String encodedQuery = query.trim().replaceAll("\\s+", "+");
                    String requestUrl = this.url + "q=" + encodedQuery + "&sortBy=" + sortBy + "&apiKey=" + this.Key;
                    Client client = new Client(this.ws);

                    CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

                    return response.thenApply(articles -> {
                        // Get descriptions
                        List<String> descriptions = articles.stream()
                                .map(a -> a.getTitle() != null ? a.getTitle() : "") // Replace with a.getDescription() if available
                                .collect(Collectors.toList());
                        double avgGrade = ReadabilityCalculator.averageGrade(descriptions);
                        double avgScore = ReadabilityCalculator.averageScore(descriptions);
                        QueryResult qr = new QueryResult(query, articles, avgGrade, avgScore);
                        return (Map.Entry<String, QueryResult>) new AbstractMap.SimpleEntry<>(query, qr);
                    });
                })
                .collect(Collectors.toList());

        CompletableFuture<?>[] futuresArray = futures.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);

        // Combine all results - each search query will be displayed separately
        return CompletableFuture.allOf(futuresArray)
                .thenApplyAsync(v -> {
                    Map<String, QueryResult> resultsByQuery = new LinkedHashMap<>();

                    for (CompletionStage<Map.Entry<String, QueryResult>> future : futures) {
                        try {
                            Map.Entry<String, QueryResult> entry = future.toCompletableFuture().join();
                            resultsByQuery.put(entry.getKey(), entry.getValue());
                        } catch (Exception e) {
                            System.err.println("Error fetching results for a query: " + e.getMessage());
                        }
                    }

                    return ok(views.html.index.render("Search Results for: " + searchInput, resultsByQuery))
                            .withSession(updatedSession);
                }, executor)
                .exceptionally(ex -> {
                    System.err.println("Error fetching results: " + ex.getMessage());
                    return internalServerError("Error fetching results: " + ex.getMessage());
                });
    }
}

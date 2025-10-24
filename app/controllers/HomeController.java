package controllers;

import models.Article;
import play.mvc.*;
import play.libs.ws.*;
import com.typesafe.config.Config;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
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
        if (data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }

    /** Store new query at top, remove duplicates, keep at most 10 */
    private Http.Session updateSession(Http.Session session, String newQuery) {
        List<String> queries = getPreviousQueries(session);
        queries.remove(newQuery);       // avoid duplicates
        queries.addFirst(newQuery);     // add newest at top
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

    public CompletionStage<Result> index() {
        return CompletableFuture.supplyAsync(() ->
            ok(views.html.index.render("Welcome to NotiLytics! Enter your search terms below.", new LinkedHashMap<>()))
        );
    }

    public CompletionStage<Result> search(Http.Request request) {
        Optional<String> searchInputOpt = request.queryString("SearchInput");
        Optional<String> sortByOpt = request.queryString("sortBy");

        if (searchInputOpt.isEmpty() || searchInputOpt.get().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                badRequest("Please enter a search term.")
            );
        }

        String searchInput = searchInputOpt.get();
        String sortBy = sortByOpt.orElse("publishedAt");

        // Update session with new query
        Http.Session updatedSession = updateSession(request.session(), searchInput);
        List<String> queries = getPreviousQueries(updatedSession);

        // Create async requests for all stored queries to display each search separately
        List<CompletionStage<Map.Entry<String, List<Article>>>> futures = queries.stream()
                .map(query -> {
                    String encodedQuery = query.trim().replaceAll("\\s+", "+");
                    String requestUrl = this.url + "q=" + encodedQuery + "&sortBy=" + sortBy + "&apiKey=" + this.Key;
                    Client client = new Client(this.ws);

                    CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

                    return response.thenApply(articles ->
                        (Map.Entry<String, List<Article>>) new AbstractMap.SimpleEntry<>(query, articles)
                    );
                })
                .toList();

        // Combine all results - each search query will be displayed separately
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApplyAsync(v -> {
                    Map<String, List<Article>> resultsByQuery = new LinkedHashMap<>();

                    for (CompletionStage<Map.Entry<String, List<Article>>> future : futures) {
                        try {
                            Map.Entry<String, List<Article>> entry = future.toCompletableFuture().join();
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

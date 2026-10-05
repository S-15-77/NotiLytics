package controllers;

import models.Article;
import models.QueryResult;
import controllers.ReadabilityCalculator;
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

/**
 * Main controller for NotiLytics web application.
 * Handles search, session management, and result rendering.
 * @author Team
 */
public class HomeController extends Controller {
    private final WSClient ws;
    private final Executor executor;
    private final String Key;
    private final String url;
    private final String topHeadlinesUrl;
    //We have to move to an in memory cache because if not we recall every single past query with the new filters applied.
    //Or else this maxes out calls toq the API for country or category, as it uses a different link "top headlines" (see application.conf)
    private final Map<String, QueryResult> cache = new LinkedHashMap<>();

    private static final String SESSION_KEY = "queries";

    /**
     * Reads queries stored in user session.
     * @param session The HTTP session.
     * @return List of previous queries.
     * @author Team
     */
    private List<String> getPreviousQueries(Http.Session session) {
        String data = session.get(SESSION_KEY).orElse("");
        if (data == null || data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }

    /**
     * Stores new query at top, removes duplicates, keeps at most 10.
     * @param session The HTTP session.
     * @param newQuery The new search query.
     * @return Updated session.
     * @author Team
     */
    private Http.Session updateSession(Http.Session session, String newQuery) {
        List<String> queries = getPreviousQueries(session);
        queries.remove(newQuery);       // avoid duplicates
        queries.add(0, newQuery);       // add newest at top (ArrayList)
        if (queries.size() > 10)        // limit 10
            queries = queries.subList(0, 10);
        return session.adding(SESSION_KEY, String.join(",", queries));
    }

    /**
     * Constructs the HomeController with dependencies.
     * @param ws Play WSClient for HTTP requests.
     * @param executor Executor for async tasks.
     * @param config App configuration.
     * @author Team
     */
    @Inject
    public HomeController(WSClient ws, Executor executor, Config config) {
        this.ws = ws;
        this.executor = executor;
        this.Key = config.getString("newsapi.key");
        this.url = config.getString("newsapi.url");
        this.topHeadlinesUrl = config.getString("newsapi.topheadlines.url");
    }

    /**
     * Render the index page showing a welcome message and no search results.
     *
     * @param request the incoming HTTP request
     * @return an HTTP 200 OK result rendering the index view with a welcome message and an empty results map
     */
    public CompletionStage<Result> index(Http.Request request) {
        // show welcome page with no results
        Map<String, QueryResult> empty = new LinkedHashMap<>();
        return CompletableFuture.completedFuture(ok(views.html.index.render("Welcome to NotiLytics! Enter your search terms below.", empty, true)));
    }

    /**
     * Process a search query, fetch matching articles, compute readability metrics, update the session history, and render the index view with results.
     *
     * @return `200 OK` rendering the index page populated with cached query results and readability metrics (session updated with the search term);
     *         `200 OK` rendering the index page with a prompt and no results when the search term is missing or empty;
     *         `500 Internal Server Error` with an error message if fetching articles fails.
     */
    public CompletionStage<Result> search(Http.Request request) {
        String searchInput = request.getQueryString("SearchInput");
        String sortBy = Optional.ofNullable(request.getQueryString("sortBy")).orElse("publishedAt");

        String showSourcesParam = request.getQueryString("showSources");
        boolean showSources = showSourcesParam != null && showSourcesParam.equals("true");

        if (searchInput == null || searchInput.trim().isEmpty()) {
            Map<String, QueryResult> empty = new LinkedHashMap<>();
            return CompletableFuture.completedFuture(ok(views.html.index.render("Please enter a search term.", empty, true)));
        }

        Http.Session updatedSession = updateSession(request.session(), searchInput);
        List<String> queries = getPreviousQueries(updatedSession);

        String encodedQuery = searchInput.trim().replaceAll("\\s+", "+");

        // Simple URL construction without filters
        String requestUrl = this.url + "q=" + encodedQuery + "&sortBy=" + sortBy + "&pageSize=10&apiKey=" + this.Key;

        Client client = new Client(this.ws);
        CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

        return response.thenApplyAsync(articles -> {
            List<String> descriptions = articles.stream()
                    .map(a -> a.getTitle() != null ? a.getTitle() : "")
                    .collect(Collectors.toList());
            double avgGrade = ReadabilityCalculator.averageGrade(descriptions);
            double avgScore = ReadabilityCalculator.averageScore(descriptions);
            QueryResult qr = new QueryResult(searchInput, articles, avgGrade, avgScore);

            cache.put(searchInput, qr);

            Map<String, QueryResult> resultsByQuery = new LinkedHashMap<>();
            for (String q : queries) {
                QueryResult r = cache.get(q);
                if (r != null) resultsByQuery.put(q, r);
            }

            return ok(views.html.index.render("Search Results for: " + searchInput, resultsByQuery, showSources))
                    .withSession(updatedSession);

        }, executor).exceptionally(ex -> {
            System.err.println("Error fetching results: " + ex.getMessage());
            return internalServerError("Error fetching results: " + ex.getMessage());
        });
    }

    /**
     * Render the sources page using optional country, category, and language filters.
     *
     * Reads the optional "country", "category", and "language" query parameters, requests matching sources from NewsAPI,
     * and renders the sources view populated with the returned sources and the selected filter values.
     *
     * @param request the HTTP request containing optional "country", "category", and "language" query parameters
     * @return an HTTP Result rendering the sources view with the list of sources and the selected filter values;
     *         if fetching sources fails, an internal server error Result with an error message is returned
     */
    public CompletionStage<Result> sources(Http.Request request) {
        String country = request.getQueryString("country");
        String category = request.getQueryString("category");
        String language = request.getQueryString("language");

        String requestUrl = "https://newsapi.org/v2/top-headlines/sources?apiKey=" + this.Key;

        // Add filters to URL if present
        if (country != null && !country.isEmpty()) {
            requestUrl += "&country=" + country;
        }
        if (category != null && !category.isEmpty()) {
            requestUrl += "&category=" + category;
        }
        if (language != null && !language.isEmpty()) {
            requestUrl += "&language=" + language;
        }

        Client client = new Client(this.ws);

        return client.fetchSources(requestUrl)
                .thenApply(sources -> ok(views.html.sources.render(
                        sources,
                        country != null ? country : "",
                        category != null ? category : "",
                        language != null ? language : ""
                )))
                .exceptionally(ex -> {
                    System.err.println("Error fetching sources: " + ex.getMessage());
                    return internalServerError("Error fetching sources");
                });
    }
}
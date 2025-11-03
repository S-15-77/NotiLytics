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
     * Renders the index page with no results.
     * @param request The HTTP request.
     * @return The rendered result.
     * @author Team
     */
    public CompletionStage<Result> index(Http.Request request) {
        // show welcome page with no results
        Map<String, QueryResult> empty = new LinkedHashMap<>();
        return CompletableFuture.completedFuture(ok(views.html.index.render("Welcome to NotiLytics! Enter your search terms below.", empty, true, "")));
    }

    /**
     * Handles search requests, fetches articles, computes readability, and renders results.
     * @param request The HTTP request.
     * @return The rendered result.
     * @author Team
     */
    public CompletionStage<Result> search(Http.Request request) {
        // Read query params from the request
        String searchInput = request.getQueryString("SearchInput");
        String sortBy = Optional.ofNullable(request.getQueryString("sortBy")).orElse("publishedAt");

        //New checks to show whether we display sources or not
        String showSourcesParam = request.getQueryString("showSources");
        boolean showSources = showSourcesParam != null && showSourcesParam.equals("true");

        //Read filter parameter and parse it through the drop down menus
        String filterValue = request.getQueryString("filterValue");
        final String filterType;
        final String filterCode;

        if (filterValue != null && !filterValue.isEmpty()) {
            String[] parts = filterValue.split(":");
            if (parts.length == 2) {
                filterType = parts[0];  // "country", "category", or "language"
                filterCode = parts[1];   // "us", "sports", "en", etc.
            } else {
                filterType = null;
                filterCode = null;
            }
        } else {
            filterType = null;
            filterCode = null;
        }

        if (searchInput == null || searchInput.trim().isEmpty()) {
            // No search provided - render the index page (don't return badRequest text)
            Map<String, QueryResult> empty = new LinkedHashMap<>();
            return CompletableFuture.completedFuture(ok(views.html.index.render("Please enter a search term.", empty, true, "")));
        }

        // Update session with new query
        Http.Session updatedSession = updateSession(request.session(), searchInput);
        List<String> queries = getPreviousQueries(updatedSession);

        // Create async requests for all stored queries to display each search separately
        String encodedQuery = searchInput.trim().replaceAll("\\s+", "+"); //This normalizes query spacing for API URL, or else we get bad API calls

        String baseUrl = this.url;
        // switch to the new top headlines endpoint when API requires it for country/category filters BUT not for language filters
        if (filterType != null && (filterType.equals("country") || filterType.equals("category"))) {
            baseUrl = this.topHeadlinesUrl;
        }
        String requestUrl = baseUrl + "q=" + encodedQuery + "&sortBy=" + sortBy;
        if (filterType != null && filterCode != null) {
            requestUrl += "&" + filterType + "=" + filterCode;
        }
        requestUrl += "&apiKey=" + this.Key;

        //We have to create a new Client initialization to prepare tne API requests for the newest query only, and not all past ones too
        Client client = new Client(this.ws);
        CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

        return response.thenApplyAsync(articles -> {
            List<String> descriptions = articles.stream()
                    .map(a -> a.getTitle() != null ? a.getTitle() : "")
                    .collect(Collectors.toList());
            double avgGrade = ReadabilityCalculator.averageGrade(descriptions);
            double avgScore = ReadabilityCalculator.averageScore(descriptions);
            QueryResult qr = new QueryResult(searchInput, articles, avgGrade, avgScore);

            // store in cache
            cache.put(searchInput, qr);

            //This is to rebuild visible history strictly from cached entries (no re-requests), so that we keep the functionality given prior
            Map<String, QueryResult> resultsByQuery = new LinkedHashMap<>();
            for (String q : queries) {
                QueryResult r = cache.get(q);
                if (r != null) resultsByQuery.put(q, r); //Ensures no NullPointerException if we get a bad call when testing for example
            }

            return ok(views.html.index.render("Search Results for: " + searchInput, resultsByQuery, showSources, filterValue != null ? filterValue : ""))
                    .withSession(updatedSession);

        }, executor).exceptionally(ex -> {
            System.err.println("Error fetching results: " + ex.getMessage());
            return internalServerError("Error fetching results: " + ex.getMessage());
        });
    }
}
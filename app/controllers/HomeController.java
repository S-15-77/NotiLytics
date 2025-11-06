package controllers;

import models.Article;
import models.QueryResult;
import controllers.ReadabilityCalculator;
import models.SourceProfile;
import models.Statistics;
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
    //We have to move to an in memory cache because if not we recall every single past query with the new filters applied.
    //Or else this maxes out calls toq the API for country or category, as it uses a different link "top headlines" (see application.conf)
    Map<String, QueryResult> cache = new LinkedHashMap<>();

    private static final String SESSION_KEY = "queries";
    private static final int maxArticlesVisible = 50;

    /**
     * Fetches the cache field
     * @return the cache
     * @author Team
     */
    public Map<String, QueryResult> getCache() {
        return this.cache;
    }

    /**
     * modifies the cache
     * @return the cache
     * @author Team
     */
    public void setCache(final Map<String, QueryResult> newCache) {
        this.cache = newCache;
    }

    /**
     * Fetches the maxArticlesVisible field
     * @return the max number of articles to print on the view
     * @author Team
     */
    public static int getMaxArticlesVisible() {
        return maxArticlesVisible;
    }

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
     * @param querySize the max number of query
     * @return Updated session.
     * @author Team
     */
    private Http.Session updateSession(Http.Session session, String newQuery, int querySize) {
        List<String> queries = getPreviousQueries(session);
        queries.remove(newQuery);       // avoid duplicates
        queries.add(0, newQuery);       // add newest at top (ArrayList)
        if (queries.size() > querySize)        // limit querySize
            queries = queries.subList(0, querySize);
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
        return CompletableFuture.completedFuture(ok(views.html.index.render("Welcome to NotiLytics! Enter your search terms below.", empty, true)));
    }

    /**
     * Handles search requests, fetches articles, computes readability, and renders results.
     * @param request The HTTP request.
     * @return The rendered result.
     * @author Karim , Santhosh
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

        Http.Session updatedSession = updateSession(request.session(), searchInput, getMaxArticlesVisible());
        List<String> queries = getPreviousQueries(updatedSession);

        String encodedQuery = searchInput.trim().replaceAll("\\s+", "+");

        // Simple URL construction without filters
        String requestUrl = this.url + "q=" + encodedQuery + "&sortBy=" + sortBy + "&pageSize="+getMaxArticlesVisible()+"&apiKey=" + this.Key;

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
            int count = 0; //to use with maxArticlesVisible
            for (String q : queries) {
                if (count >= maxArticlesVisible) break;
                QueryResult r = cache.get(q);
                if (r != null) resultsByQuery.put(q, r); //Ensures no NullPointerException if we get a bad call when testing for example
                count++;
            }

            return ok(views.html.index.render("Search Results for: " + searchInput, resultsByQuery, showSources))
                    .withSession(updatedSession);

        }, executor).exceptionally(ex -> {
            System.err.println("Error fetching results: " + ex.getMessage());
            return internalServerError("Error fetching results: " + ex.getMessage());
        });
    }

    /**
     * Returns all sources found in NewsAPI
     * @param request request on where to pick up the sources
     * @return all courses in NewsAPI
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

    /**
     * Handles the calculation of the word statistics for the articles.
     * @param request The HTTP request.
     * @param key the statistics button clicked
     * @return The rendered result.
     * @author Karim BG
     */
    public Result stats(Http.Request request, String key) {
        Statistics s = new Statistics(cache.get(key));
        int numberOfArticles = cache.get(key).getArticles().size();
        List<String> TitlesAndDescription = new ArrayList<>(s.getTitles());
        TitlesAndDescription.addAll(s.getDescriptions());
        String counter = Statistics.getString(
                Statistics.getCounter(
                        Statistics.filtering(
                                Statistics.getWords(
                                        TitlesAndDescription))));
        return ok("More Statistics:\n" + numberOfArticles +" articles have been taken into account.\n"+counter);
    }

    /**
     * Handles retrieving the last 10 articles of a source for its Profile Page.
     * @param sourceName the name of the selected source.
     * @return the rendered result.
     * @author Haytham
     */
    public CompletionStage<Result> profile(String sourceName, String id) {

        String requestUrl = this.url + "sources=" + (id != null ? id : sourceName) + "&apiKey=" + this.Key;

        Client client = new Client(this.ws);

        CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

        return response.thenApply(articles -> {

            if (articles == null || articles.isEmpty()) {
                return ok(views.html.sourceProfile.render(
                        new SourceProfile(sourceName, "", "No Articles Found for this source at this time. Please try again later!"),
                        new ArrayList<>()
                ));
            }

            List<Article> last10 = articles.stream().limit(10).toList();

            SourceProfile profile = new SourceProfile(
                    sourceName,
                    last10.get(0).getSourceUrl(),
                    "Listing Articles from " + sourceName + "."
            );

            return ok(views.html.sourceProfile.render(profile,last10));
        });

    }

}
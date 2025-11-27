package controllers;

import actors.ReadabilityActor;
import actors.UserParentActor;
import models.Article;
import models.QueryResult;
import models.ReadabilityCalculator;
import models.SourceProfile;
import models.Statistics;
import org.apache.pekko.actor.typed.RecipientRef;
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

import org.slf4j.Logger;
import org.apache.pekko.stream.javadsl.Flow;
import com.fasterxml.jackson.databind.JsonNode;
import play.libs.F.Either;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.Adapter;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.ActorSystem;
import views.html.index;

import java.time.Duration;

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
    private final Logger logger = org.slf4j.LoggerFactory.getLogger("controllers.HomeController"); //As seen in Lab 10
    private final List<String> validOrigins = Arrays.asList("localhost:9000"); //We only need a single origin for now, can expand later if need be
    private final ActorSystem system;
    private final ActorRef<UserParentActor.Create> userParentActor;
    private final ActorRef<ReadabilityActor.Command> readabilityActor;
    Map<String, QueryResult> cache = new LinkedHashMap<>();

    private static final String SESSION_KEY = "queries";
    private static final int maxArticlesVisible = 50;

    /**
     * Fetches the cache field
     *
     * @return the cache
     * @author Team
     */
    public Map<String, QueryResult> getCache() {
        return this.cache;
    }

    /**
     * modifies the cache
     *
     * @return the cache
     * @author Team
     */
    public void setCache(final Map<String, QueryResult> newCache) {
        this.cache = newCache;
    }

    /**
     * Fetches the maxArticlesVisible field
     *
     * @return the max number of articles to print on the view
     * @author Team
     */
    public static int getMaxArticlesVisible() {
        return maxArticlesVisible;
    }

    /**
     * Reads queries stored in user session.
     *
     * @param session The HTTP session.
     * @return List of previous queries.
     * @author Team
     */
    private List<String> getPreviousQueries(Http.Session session) {
        String data = session.get(SESSION_KEY).orElse("");
        if (data == null || data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }

//    /**
//     * Stores new query at top, removes duplicates, keeps at most 10.
//     *
//     * @param session  The HTTP session.
//     * @param newQuery The new search query.
//     * @return Updated session.
//     * @author Team
//     */
//    private Http.Session updateSession(Http.Session session, String newQuery) {
//        List<String> queries = getPreviousQueries(session);
//        queries.remove(newQuery);       // avoid duplicates
//        queries.add(0, newQuery);       // add newest at top (ArrayList)
//        if (queries.size() > 10)        // limit 10
//            queries = queries.subList(0, 10);
//        return session.adding(SESSION_KEY, String.join(",", queries));
//    }

    /**
     * Stores new query at top, removes duplicates, keeps at most 10.
     *
     * @param session   The HTTP session.
     * @param newQuery  The new search query.
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
     *
     * @param ws       Play WSClient for HTTP requests.
     * @param executor Executor for async tasks.
     * @param config   App configuration.
     * @author Team
     */
    @Inject
    public HomeController(WSClient ws, Executor executor, Config config, ActorSystem system, ActorRef<UserParentActor.Create> userParentActor,ActorRef<ReadabilityActor.Command> readabilityActor) {
        this.ws = ws;
        this.executor = executor;
        this.Key = config.getString("newsapi.key");
        this.url = config.getString("newsapi.url");
        this.system = system;
        this.userParentActor = userParentActor;
        this.readabilityActor = readabilityActor;
    }

    /**
     * Renders the index page with no results.
     *
     * @param request The HTTP request.
     * @return The rendered result.
     * @author Team
     */
    public CompletionStage<Result> index(Http.Request request) {
        // show welcome page with no results
        Map<String, QueryResult> empty = new LinkedHashMap<>();
        return CompletableFuture.completedFuture(ok(views.html.index.render("Welcome to NotiLytics! Enter your search terms below.", empty, true, request)));
    }

    /**
     * Handles search requests, fetches articles, computes readability, and renders results.
     *
     * @param request The HTTP request.
     * @return The rendered result.
     * @author Karim , Santhosh, Ilyes
     */
//    public CompletionStage<Result> search(Http.Request request) {
//        String searchInput = request.getQueryString("SearchInput");
//        String sortBy = Optional.ofNullable(request.getQueryString("sortBy")).orElse("publishedAt");
//
//        String showSourcesParam = request.getQueryString("showSources");
//        boolean showSources = showSourcesParam != null && showSourcesParam.equals("true");
//
//        if (searchInput == null || searchInput.trim().isEmpty()) {
//            Map<String, QueryResult> empty = new LinkedHashMap<>();
//            return CompletableFuture.completedFuture(ok(views.html.index.render("Please enter a search term.", empty, true, request)));
//        }
//
//        Http.Session updatedSession = updateSession(request.session(), searchInput, getMaxArticlesVisible());
//        List<String> queries = getPreviousQueries(updatedSession);
//
//        String encodedQuery = searchInput.trim().replaceAll("\\s+", "+");
//
//        // Simple URL construction without filters
//        String requestUrl = this.url + "q=" + encodedQuery + "&sortBy=" + sortBy + "&pageSize=" + getMaxArticlesVisible() + "&apiKey=" + this.Key;
//
//        Client client = new Client(this.ws);
//        CompletionStage<List<Article>> response = client.clientRequest(requestUrl);
//
//        return response.thenApplyAsync(articles -> {
//            List<String> descriptions = articles.stream()
//                    .map(a -> a.getTitle() != null ? a.getTitle() : "")
//                    .collect(Collectors.toList());
//
//            // Ask the readability actor (timeout 5s)
//            Scheduler scheduler = Adapter.toTyped(system.scheduler());
//            Duration askTimeout = Duration.ofSeconds(5);
//
//
//            return AskPattern.<ReadabilityActor.Command, ReadabilityActor.Result>ask(
//                    readabilityActor,
//                    replyTo -> new ReadabilityActor.Calculate(descriptions, replyTo),
//                    askTimeout,
//                    scheduler
//            ).thenApply(readResult -> {
//                double avgGrade = readResult.averageGrade;
//                double avgScore = readResult.averageScore;
//
//                QueryResult qr = new QueryResult(searchInput, articles, avgGrade, avgScore);
//
//                cache.put(searchInput, qr);
//
//                Map<String, QueryResult> resultsByQuery = new LinkedHashMap<>();
//                int count = 0; //to use with maxArticlesVisible
//                for (String q : queries) {
//                    if (count >= maxArticlesVisible) break;
//                    QueryResult r = cache.get(q);
//                    if (r != null)
//                        resultsByQuery.put(q, r); //Ensures no NullPointerException if we get a bad call when testing for example
//                    count++;
//                }
//
//                return ok(index.render("Search Results for: " + searchInput, resultsByQuery, showSources, request))
//                        .withSession(updatedSession);
//            });
//
//        }, executor).exceptionally(ex -> {
//            System.err.println("Error fetching results: " + ex.getMessage());
//            return internalServerError("Error fetching results: " + ex.getMessage());
//        });
//    }

    public CompletionStage<Result> search(Http.Request request) {
        String searchInput = request.getQueryString("SearchInput");
        String sortBy = Optional.ofNullable(request.getQueryString("sortBy")).orElse("publishedAt");

        String showSourcesParam = request.getQueryString("showSources");
        boolean showSources = showSourcesParam != null && showSourcesParam.equals("true");

        if (searchInput == null || searchInput.trim().isEmpty()) {
            Map<String, QueryResult> empty = new LinkedHashMap<>();
            return CompletableFuture.completedFuture(
                    ok(views.html.index.render("Please enter a search term.", empty, true, request))
            );
        }

        Http.Session updatedSession = updateSession(request.session(), searchInput, getMaxArticlesVisible());
        List<String> queries = getPreviousQueries(updatedSession);

        String encodedQuery = searchInput.trim().replaceAll("\\s+", "+");

        String requestUrl = this.url
                + "q=" + encodedQuery
                + "&sortBy=" + sortBy
                + "&pageSize=" + getMaxArticlesVisible()
                + "&apiKey=" + this.Key;

        Client client = new Client(this.ws);
        CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

        return response
                .thenComposeAsync(articles -> {   // 🔁 use thenComposeAsync, not thenApplyAsync
                    List<String> descriptions = articles.stream()
                            .map(a -> a.getTitle() != null ? a.getTitle() : "")
                            .collect(Collectors.toList());

                    Scheduler scheduler = Adapter.toTyped(system.scheduler());
                    Duration askTimeout = Duration.ofSeconds(5);

                    return AskPattern.<ReadabilityActor.Command, ReadabilityActor.Result>ask(
                            readabilityActor,                                   // make sure this is injected
                            replyTo -> new ReadabilityActor.Calculate(descriptions, replyTo),
                            askTimeout,
                            scheduler
                    ).thenApply(readResult -> {
                        double avgGrade = readResult.averageGrade;
                        double avgScore = readResult.averageScore;

                        QueryResult qr = new QueryResult(searchInput, articles, avgGrade, avgScore);
                        cache.put(searchInput, qr);

                        Map<String, QueryResult> resultsByQuery = new LinkedHashMap<>();
                        int count = 0;
                        for (String q : queries) {
                            if (count >= maxArticlesVisible) break;
                            QueryResult r = cache.get(q);
                            if (r != null) {
                                resultsByQuery.put(q, r);
                            }
                            count++;
                        }

                        return ok(views.html.index.render(
                                "Search Results for: " + searchInput,
                                resultsByQuery,
                                showSources,
                                request
                        )).withSession(updatedSession);
                    });
                }, executor)
                .exceptionally(ex -> {          // 🔁 now T is Result, so return Result
                    System.err.println("Error fetching results: " + ex.getMessage());
                    return internalServerError("Error fetching results: " + ex.getMessage());
                });
    }

    /**
     * Returns all sources found in NewsAPI
     *
     * @param request request on where to pick up the sources
     * @return all courses in NewsAPI
     * @author Ilyes
     */
    public CompletionStage<Result> sources(Http.Request request) {
        return CompletableFuture.completedFuture(
                ok(views.html.sources.render(
                        Collections.emptyList(),
                        "",
                        "",
                        "",
                        request
                ))
        );
    }

    /**
     * Handles the calculation of the word statistics for the articles.
     *
     * @param request The HTTP request.
     * @param key     the statistics button clicked
     * @return The rendered result.
     * @author Karim BG
     */
    public CompletionStage<Result> stats(Http.Request request, String key) {
        return CompletableFuture.supplyAsync(() -> {
            Statistics s = new Statistics(cache.get(key));
            int numberOfArticles = cache.get(key).getArticles().size();
            List<String> titlesAndDescription = new ArrayList<>(s.getTitles());
            titlesAndDescription.addAll(s.getDescriptions());
            String counter = Statistics.getString(Statistics.getCounter(
                    Statistics.filtering(
                            Statistics.getWords(titlesAndDescription))));
            return ok(views.html.statProfile.render("Word Statistics for " + key + " (" + numberOfArticles + " articles)", counter));
        });
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

    private boolean sameOriginCheck(Http.RequestHeader rh) {
        final Optional<String> origin = rh.header("Origin");

        if (! origin.isPresent()) {
            logger.error("originCheck: rejecting request because no Origin header found");
            return false;
        } else if (originMatches(origin.get())) {
            logger.debug("originCheck: originValue = " + origin);
            return true;
        } else {
            logger.error("originCheck: rejecting request because Origin header value " + origin + " is not in the same origin: "
                    + String.join(", ", validOrigins));
            return false;
        }
    }

    private boolean originMatches(String actualOrigin) {
        return validOrigins.stream().parallel().anyMatch(actualOrigin::contains); //Changed from lab to parallel since we are using anyMatch here
    }

    public WebSocket ws() {
        return WebSocket.Json.acceptOrResult(request -> {
            if (sameOriginCheck(request)) {
                final CompletionStage<Flow<JsonNode, JsonNode, NotUsed>> future = wsFutureFlow(request);
                final CompletionStage<Either<Result, Flow<JsonNode, JsonNode, ?>>> stage = future.thenApply(Either::Right);
                return stage.exceptionally(this::logException);
            } else {
                return forbiddenResult();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Flow<JsonNode, JsonNode, NotUsed>> wsFutureFlow(Http.RequestHeader request) {
        String id = Long.toString(request.asScala().id());
        Scheduler scheduler = Adapter.toTyped(system.scheduler());
        Duration timeout = Duration.ofSeconds(5);

        return AskPattern.<UserParentActor.Create, Flow<JsonNode, JsonNode, NotUsed>>ask(
                userParentActor,
                replyTo -> new UserParentActor.Create(id, replyTo),
                timeout,
                scheduler
        ).thenApply(f -> f.named("websocket"));
    }


    private CompletionStage<Either<Result, Flow<JsonNode, JsonNode, ?>>> forbiddenResult() {
        final Result forbidden = Results.forbidden("forbidden");
        final Either<Result, Flow<JsonNode, JsonNode, ?>> left = Either.Left(forbidden);

        return CompletableFuture.completedFuture(left);
    }

    private Either<Result, Flow<JsonNode, JsonNode, ?>> logException(Throwable throwable) {
        logger.error("Cannot create websocket", throwable);
        Result result = Results.internalServerError("error");
        return Either.Left(result);
    }
}
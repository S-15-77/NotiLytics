package controllers;

import actors.ReadabilityActor;
import actors.StatisticsActor;
import actors.UserParentActor;
import actors.StatisticsActor;
import actors.CacheActor;

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
    private final ActorRef<StatisticsActor.Command> statisticsActor;
    private final ActorRef<CacheActor.Command> cacheActor;

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
     * @param newCache the new cache
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
    public HomeController(WSClient ws,
                          Executor executor,
                          Config config,
                          ActorSystem system,
                          ActorRef<UserParentActor.Create> userParentActor,
                          ActorRef<ReadabilityActor.Command> readabilityActor,
                          ActorRef<StatisticsActor.Command> statisticsActor,
                          ActorRef<CacheActor.Command> cacheActor) {
        this.ws = ws;
        this.executor = executor;
        this.Key = config.getString("newsapi.key");
        this.url = config.getString("newsapi.url");
        this.system = system;
        this.userParentActor = userParentActor;
        this.readabilityActor = readabilityActor;
        this.statisticsActor = statisticsActor;
        this.cacheActor = cacheActor;
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
        //All searches is now via WebSocket
        //All searches is now via WebSocket
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
        return CompletableFuture.completedFuture(redirect(routes.HomeController.index())); //We have to keep this,, or else I couldn't make it work otherwise (sorry)
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
     * Handles the calculation of the word statistics for the articles now using Actors.
     *
     * @param request The HTTP request.
     * @param key     the statistics button clicked
     * @return The rendered result.
     * @author Karim BG
     */
    public CompletionStage<Result> stats(Http.Request request, String key) {
        /*return CompletableFuture.supplyAsync(() -> {
            Statistics s = new Statistics(cache.get(key));
            int numberOfArticles = cache.get(key).getArticles().size();
            List<String> titlesAndDescription = new ArrayList<>(s.getTitles());
            titlesAndDescription.addAll(s.getDescriptions());
            String counter = Statistics.getString(Statistics.getCounter(
                    Statistics.filtering(
                            Statistics.getWords(titlesAndDescription))));
            return ok(views.html.statProfile.render("Word Statistics for " + key + " (" + numberOfArticles + " articles)", counter));
        });*/
        Duration timeout = Duration.ofSeconds(5);
        Scheduler scheduler = Adapter.toTyped(system.scheduler());

        //Cache Information loaded
        return AskPattern.<CacheActor.Command, CacheActor.Response>ask(
                        cacheActor,
                        replyTo -> new CacheActor.Get(key, replyTo),
                        timeout,
                        scheduler
                )
                .thenCompose(cacheResponse -> {


            QueryResult query = cacheResponse.result();

            if (query == null) {
                return CompletableFuture.completedFuture(
                        badRequest("No Data for : " + key) //returns a empty
                );
            }

            //get stat information froom query
            return AskPattern.<StatisticsActor.Command, StatisticsActor.Response>ask(
                            statisticsActor,
                            replyTo -> new StatisticsActor.Compute(query, replyTo),
                            timeout,
                            scheduler
                    ).thenApply(statResponse -> {

                int number = query.getArticles().size();
                String title = "Word Statistics for " + key + " (" + number + " articles)";

                return ok(views.html.statProfile.render(title, statResponse.resultString()));
            });
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

    /**
     * Validates that the WebSocket request originates from an allowed origin.
     * Needed to protect against Cross-Site WebSocket Hijacking attacks.
     * Taken from the Play Framework Websocket example
     * @param rh The HTTP request header
     * @return true if origin is valid, false otherwise
     * @author Ilyes
     */
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

    /**
     * Checks if the actual origin contains any valid origin string.
     * Taken from the Play Framework Websocket example
     * @param actualOrigin The origin header value
     * @return true if origin is valid, false otherwise
     * @author Ilyes
     */
    private boolean originMatches(String actualOrigin) {
        return validOrigins.stream().parallel().anyMatch(actualOrigin::contains); //Changed from lab to parallel since we are using anyMatch here
    }

    /**
     * Creates a WebSocket connection for real-time communication after validating origin and creates a user-specific WebSocket flow.
     * Taken from the Play Framework Websocket example
     * @return WebSocket connection that accepts JSON messages
     * @author Ilyes
     */
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

    /**
     * Creates a WebSocket flow for a specific user by asking UserParentActor.
     * Taken from the Play Framework Websocket example
     * @param request The HTTP request header
     * @return CompletionStage containing the WebSocket flow
     * @author Ilyes
     */
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


    /**
     * Creates a forbidden result for rejected WebSocket connections.
     * Taken from the Play Framework Websocket example
     * @return CompletionStage with Left(forbidden result)
     * @author Ilyes
     */
    private CompletionStage<Either<Result, Flow<JsonNode, JsonNode, ?>>> forbiddenResult() {
        final Result forbidden = Results.forbidden("forbidden");
        final Either<Result, Flow<JsonNode, JsonNode, ?>> left = Either.Left(forbidden);

        return CompletableFuture.completedFuture(left);
    }

    /**
     * Logs exceptions during WebSocket creation and returns error result.
     * Taken from the Play Framework Websocket example
     * @param throwable The exception that occurred
     * @return Either.Left with internal server error
     * @author Ilyes
     */
    private Either<Result, Flow<JsonNode, JsonNode, ?>> logException(Throwable throwable) {
        logger.error("Cannot create websocket", throwable);
        Result result = Results.internalServerError("error");
        return Either.Left(result);
    }
}
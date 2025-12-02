package actors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import actors.SourcesActor.GetSources;
import actors.CacheActor;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.stream.javadsl.*;
import com.fasterxml.jackson.databind.JsonNode;

import javax.inject.Inject;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.Materializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import play.libs.ws.WSClient;
import com.typesafe.config.Config;
import models.Article;
import models.QueryResult;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

public class UserActor {
    public interface Message {}

    /**
     * Internal message to stop the actor.
     */
    private static final class InternalStop implements Message {
        private static final InternalStop INSTANCE = new InternalStop();
        public static InternalStop get() {
            return INSTANCE;
        }
        private InternalStop() {}
    }

    /**
     * Internal message to trigger periodic polling of queries in case a new article is found
     */
    private static final class PollTick implements Message {
        private static final PollTick INSTANCE = new PollTick();
        public static PollTick get() {
            return INSTANCE;
        }
        private PollTick() {}
    }

    private final Duration timeout = Duration.of(3, ChronoUnit.SECONDS);

    private final String id;
    private final ActorRef<SourcesActor.GetSources> sourcesActor;
    private final ActorRef<ReadabilityActor.Command> readabilityActor;
    private final ActorRef<CacheActor.Command> cacheActor;
    private final Scheduler scheduler;
    private static final Logger logger = LoggerFactory.getLogger(UserActor.class);
    private final ActorContext<Message> context;
    private final WSClient ws;
    private final String apiKey;
    private final String url;
    private final Materializer mat;

    private final Sink<JsonNode, NotUsed> hubSink;
    private final Flow<JsonNode, JsonNode, NotUsed> websocketFlow;

    private final Map<String, QueryResult> cache = new LinkedHashMap<>();
    private final Map<String, Set<String>> seenArticles = new HashMap<>();

    /**
     * Creates a new UserActor behavior.
     * @param id The unique identifier for the user
     * @param sourcesActor Reference to the sources actor
     * @param readabilityActor Reference to the readability actor
     * @param ws WebSocket client for API requests
     * @param config Application configuration
     * @return The actor behavior
     */
    public static Behavior<Message> create(String id, ActorRef<GetSources> sourcesActor,
                                           ActorRef<ReadabilityActor.Command> readabilityActor,
                                           ActorRef<CacheActor.Command> cacheActor,
                                           WSClient ws, Config config) {
        return Behaviors.setup(context -> {
            return Behaviors.withTimers(timers -> {
                return new UserActor(id, sourcesActor, readabilityActor, cacheActor, ws, config, context, timers).behavior();
            });
        });
    }

    @Inject
    public UserActor(String id,
                     ActorRef<GetSources> sourcesActor,
                     ActorRef<ReadabilityActor.Command> readabilityActor,
                     ActorRef<CacheActor.Command> cacheActor,
                     WSClient ws,
                     Config config,
                     ActorContext<Message> context,
                     TimerScheduler<Message> timers) {
        this.id = id;
        this.sourcesActor = sourcesActor;
        this.readabilityActor = readabilityActor;
        this.ws = ws;
        this.apiKey = config.getString("newsapi.key");
        this.url = config.getString("newsapi.url");
        this.scheduler = context.getSystem().scheduler();
        this.context = context;
        this.mat = Materializer.matFromSystem(context.getSystem());
        this.cacheActor = cacheActor;

        timers.startTimerAtFixedRate(PollTick.get(), Duration.ofSeconds(60));

        Pair<Sink<JsonNode, NotUsed>, Source<JsonNode, NotUsed>> sinkSourcePair =
                MergeHub.of(JsonNode.class, 16)
                        .toMat(BroadcastHub.of(JsonNode.class, 256), Keep.both())
                        .run(mat);

        this.hubSink = sinkSourcePair.first();
        Source<JsonNode, NotUsed> hubSource = sinkSourcePair.second();

        Sink<JsonNode, CompletionStage<Done>> jsonSink = Sink.foreach((JsonNode json) -> {
            handleIncomingMessage(json);
        });

        this.websocketFlow = Flow.fromSinkAndSourceCoupled(jsonSink, hubSource);
    }

    /**
     * Handles incoming WebSocket messages from the client.
     * @param json The incoming JSON message
     */
    private void handleIncomingMessage(JsonNode json) {
        String type = json.has("type") ? json.get("type").asText() : "";

        switch (type) {
            case "search":
                handleSearch(json);
                break;
            case "filter":
                handleSourceFilter(json);
                break;
        }
    }

    /**
     * Handles a search request and fetches articles.
     * @param json The search request JSON
     */
    private void handleSearch(JsonNode json) {
        String query = json.has("query") ? json.get("query").asText() : "";
        String sortBy = json.has("sortBy") ? json.get("sortBy").asText() : "publishedAt";

//        System.out.println("handleSearch called for query: " + query);
        if (query.isEmpty()) {
            return;
        }

        if (cache.containsKey(query)) {
            cache.remove(query);
            seenArticles.remove(query);
            seenArticles.put(query, new HashSet<>());
        } else {
            seenArticles.put(query, new HashSet<>());
        }


        //I had a race condition here because it was asynchronous, hence why it was not updating properly lol

        while (cache.size() >= 10) {
                String oldestKey = cache.keySet().iterator().next();
                cache.remove(oldestKey);
                seenArticles.remove(oldestKey);
        }

        fetchAndSendResults(query, sortBy, true);
    }

    /**
     * Fetches articles from the NewsAPI API and sends results to the client.
     * @param query The search query
     * @param sortBy The sort order for results
     * @param isInitial Whether this is an initial search or an update
     */
    private void fetchAndSendResults(String query, String sortBy, boolean isInitial) {
        String encodedQuery = query.trim().replaceAll("\\s+", "+");
        String requestUrl = this.url + "q=" + encodedQuery + "&sortBy=" + sortBy +
                "&pageSize=50&apiKey=" + this.apiKey;

//        System.out.println("fetchAndSendResults called");

        ws.url(requestUrl)
                .setRequestTimeout(Duration.ofSeconds(10))
                .get()
                .thenAccept(response -> {
//                    System.out.println("Response received for query: " + query);
                    JsonNode articlesJson = response.asJson();
                    if (articlesJson == null || !articlesJson.has("articles")) {
                        return;
                    }

                    List<Article> allArticles = parseArticles(articlesJson.get("articles"));

                    Set<String> seen = seenArticles.get(query);
                    if (seen == null) {
                        seen = new HashSet<>();
                        seenArticles.put(query, seen);
                    }

                    final Set<String> finalSeen = seen;

                    List<Article> newArticles = allArticles.stream()
                            .filter(article -> {
                                String key = getArticleKey(article);
                                if (finalSeen.contains(key)) {
                                    return false;
                                } else {
                                    finalSeen.add(key);
                                    return true;
                                }
                            })
                            .collect(Collectors.toList());

                    if (isInitial) {
                        List<Article> initial = newArticles.stream().limit(50).collect(Collectors.toList());

                        // /!\ Changed limit here (I need 50 for the cache) tell me if any problem detected on your end

                        if (!initial.isEmpty()) {
                            calculateReadabilityAndSend(query, initial, "initial");
                        }
                    } else {
                        if (!newArticles.isEmpty()) {
                            calculateReadabilityAndSend(query, newArticles, "update");
                        }
                    }
                })
                .exceptionally(ex -> {
                    return null;
                });
    }

    /**
     * Calculates readability scores for articles and sends them to the client.
     * @param query The search query
     * @param articles The list of articles
     * @param messageType The type of message to send
     */
    private void calculateReadabilityAndSend(String query, List<Article> articles, String messageType) {
        List<String> descriptions = articles.stream()
                .map(a -> a.getTitle() != null ? a.getTitle() : "")
                .collect(Collectors.toList());

        Duration askTimeout = Duration.ofSeconds(5);

        AskPattern.<ReadabilityActor.Command, ReadabilityActor.Result>ask(
                readabilityActor,
                replyTo -> new ReadabilityActor.Calculate(descriptions, replyTo),
                askTimeout,
                scheduler
        ).thenAccept(readResult -> {
            // Extract both individual scores and averages from ReadabilityActor result
            List<Double> individualGrades = readResult.grades;
            List<Double> individualScores = readResult.scores;
            double avgGrade = readResult.averageGrade;
            double avgScore = readResult.averageScore;

            for (int i = 0; i < articles.size() && i < individualGrades.size(); i++) {
                Article article = articles.get(i);
                article = new Article(
                        article.getTitle(),
                        article.getUrl(),
                        article.getSourceName(),
                        article.getSourceUrl(),
                        article.getPublishedAt(),
                        individualGrades.get(i).intValue(),
                        individualScores.get(i).intValue(),
                        article.getDescription()
                );
                articles.set(i, article);
            }

            QueryResult qr = new QueryResult(query, articles, avgGrade, avgScore);
            cacheActor.tell(new CacheActor.Put(query, qr));

            QueryResult existing = cache.get(query);
            if (existing != null) {
                List<Article> merged = new ArrayList<>(existing.getArticles());
                merged.addAll(articles);
                qr = new QueryResult(query, merged,
                        (existing.getAvgGrade() + avgGrade) / 2,
                        (existing.getAvgScore() + avgScore) / 2);
            }
            cache.put(query, qr);

            sendArticlesToClient(query, articles, individualGrades, individualScores, avgGrade, avgScore, messageType);
        }).exceptionally(ex -> {
            return null;
        });
    }

    /**
     * Sends article data to the WebSocket client.
     * @param query The search query
     * @param articles The list of articles
     * @param individualGrades Individual readability grades
     * @param individualScores Individual readability scores
     * @param avgGrade Average readability grade
     * @param avgScore Average readability score
     * @param messageType The type of message to send
     */
    private void sendArticlesToClient(String query, List<Article> articles,
                                      List<Double> individualGrades, List<Double> individualScores,
                                      double avgGrade, double avgScore, String messageType) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode message = mapper.createObjectNode();
        message.put("type", messageType);
        message.put("query", query);

        ObjectNode readability = mapper.createObjectNode();
        readability.put("avgGrade", avgGrade);
        readability.put("avgScore", avgScore);
        message.set("readability", readability);

        ArrayNode articlesArray = mapper.createArrayNode();
        List<Article> visible = articles.stream().limit(10).toList();

        for (int i = 0; i < visible.size(); i++) {
            Article article = articles.get(i);
            ObjectNode articleNode = mapper.createObjectNode();
            articleNode.put("title", article.getTitle());
            articleNode.put("description", article.getDescription());
            articleNode.put("url", article.getUrl());
            articleNode.put("publishedAt", article.getPublishedAt());
            articleNode.put("sourceName", article.getSourceName());
            articleNode.put("sourceUrl", article.getSourceUrl());

            if (individualGrades != null && i < individualGrades.size()) {
                articleNode.put("kincaidGrade", individualGrades.get(i));
            } else {
                articleNode.put("kincaidGrade", 0.0);
            }

            if (individualScores != null && i < individualScores.size()) {
                articleNode.put("readingScore", individualScores.get(i));
            } else {
                articleNode.put("readingScore", 0.0);
            }

            articlesArray.add(articleNode);
        }
        message.set("articles", articlesArray);

        Source.<JsonNode>single(message)
                .runWith(hubSink, mat);
    }

    /**
     * Polls all cached queries for new articles.
     */
    private void pollAllQueriesAndSendHistory() {
        logger.info("Polling all queries for new articles");

        if (cache.isEmpty()) {
            return;
        }

        List<String> queriesToPoll = new ArrayList<>(cache.keySet());

        for (String query : queriesToPoll) {
//            System.out.println("Fetching results for query: " + query);
            fetchAndSendResults(query, "publishedAt", false);
        }

        context.getSystem().scheduler().scheduleOnce(
                Duration.ofSeconds(2),
                () -> { sendFullHistoryToClient();},
                context.getSystem().executionContext()
        );
    }

    /**
     * Sends the full query history to the client.
     */
    private void sendFullHistoryToClient() {
        if (cache.isEmpty()) {
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "history");

        ArrayNode queriesArray = mapper.createArrayNode();

        List<String> queryKeys = new ArrayList<>(cache.keySet());
        Collections.reverse(queryKeys);

        for (String query : queryKeys) {
            QueryResult qr = cache.get(query);
            ObjectNode queryNode = mapper.createObjectNode();
            queryNode.put("query", query);

            ObjectNode readability = mapper.createObjectNode();
            readability.put("avgGrade", qr.getAvgGrade());
            readability.put("avgScore", qr.getAvgScore());
            queryNode.set("readability", readability);

            List<Article> articles = qr.getArticles();

            ArrayNode articlesArray = mapper.createArrayNode();
            for (Article article : articles) {
                ObjectNode articleNode = mapper.createObjectNode();
                articleNode.put("title", article.getTitle());
                articleNode.put("description", article.getDescription());
                articleNode.put("url", article.getUrl());
                articleNode.put("publishedAt", article.getPublishedAt());
                articleNode.put("sourceName", article.getSourceName());
                articleNode.put("sourceUrl", article.getSourceUrl());
                articleNode.put("kincaidGrade", article.getKincaidGrade());
                articleNode.put("readingScore", article.getReadingScore());
                articlesArray.add(articleNode);
            }
            queryNode.set("articles", articlesArray);
            queriesArray.add(queryNode);
        }

        message.set("queries", queriesArray);

        Source.<JsonNode>single(message)
                .runWith(hubSink, mat);
    }

    /**
     * Parses article JSON data into Article objects.
     * @param articlesJson The JSON array of articles
     * @return List of parsed articles
     */
    private List<Article> parseArticles(JsonNode articlesJson) {
        List<Article> articles = new ArrayList<>();
        if (articlesJson.isArray()) {
            for (JsonNode node : articlesJson) {
                Article article = new Article(
                        node.has("title") ? node.get("title").asText() : "",
                        node.has("url") ? node.get("url").asText() : "",
                        node.has("source") && node.get("source").has("name") ?
                                node.get("source").get("name").asText() : "",
                        node.has("source") && node.get("source").has("url") ?
                                node.get("source").get("url").asText() : "",
                        node.has("publishedAt") ? node.get("publishedAt").asText() : "",
                        0,
                        0,
                        node.has("description") ? node.get("description").asText() : ""
                );
                articles.add(article);
            }
        }
        return articles;
    }

    /**
     * Generates a unique key for an article.
     * @param article The article
     * @return A unique key string
     */
    private String getArticleKey(Article article) {
        return article.getUrl() + "|" + article.getTitle();
    }

    /**
     * Handles source filtering requests.
     * @param json The filter request JSON
     */
    private void handleSourceFilter(JsonNode json) {
        String country = json.has("country") ? json.get("country").asText() : "";
        String category = json.has("category") ? json.get("category").asText() : "";
        String language = json.has("language") ? json.get("language").asText() : "";

        String requestUrl = "https://newsapi.org/v2/top-headlines/sources?apiKey=" + apiKey;

        if (!country.isEmpty()) {
            requestUrl += "&country=" + country;
        }
        if (!category.isEmpty()) {
            requestUrl += "&category=" + category;
        }
        if (!language.isEmpty()) {
            requestUrl += "&language=" + language;
        }

        ws.url(requestUrl)
                .setRequestTimeout(Duration.ofSeconds(10))
                .get()
                .thenAccept(response -> {
                    JsonNode sourcesJson = response.asJson();
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode result = mapper.createObjectNode();
                    result.put("type", "sources");
                    result.set("data", sourcesJson.get("sources"));

                    Source.<JsonNode>single(result)
                            .runWith(hubSink, mat);
                })
                .exceptionally(ex -> {
                    return null;
                });
    }

    /**
     * Returns the actor behavior.
     * @return The actor behavior
     */
    public Behavior<Message> behavior() {
        return Behaviors
                .receive(Message.class)
                .onMessage(UserParentActor.GetFlow.class, getFlow -> {
                    getFlow.replyTo.tell(websocketFlow);
                    return Behaviors.same();
                })
                .onMessage(PollTick.class, tick -> {
//                    System.out.println("POLLTICK RECEIVED);
                    logger.info("Polling all queries for new articles");
                    pollAllQueriesAndSendHistory();
                    return Behaviors.same();
                })
                .onMessageEquals(InternalStop.get(), Behaviors::stopped)
                .onSignal(PostStop.class, _postStop -> {
                    context.getLog().info("Stopping actor {}", context.getSelf());
                    return Behaviors.same();
                })
                .build();
    }

    /**
     * Returns the WebSocket flow for this user.
     * @return The WebSocket flow
     */
    public Flow<JsonNode, JsonNode, NotUsed> getWebsocketFlow() {
        return websocketFlow;
    }

    public interface Factory {
        Behavior<Message> create(String id);
    }
}
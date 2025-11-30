package actors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import actors.SourcesActor.GetSources;
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

    private static final class InternalStop implements Message {
        private static final InternalStop INSTANCE = new InternalStop();
        public static InternalStop get() {
            return INSTANCE;
        }
        private InternalStop() {}
    }

    // Timer message for polling
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
    private final Scheduler scheduler;
    private static final Logger logger = LoggerFactory.getLogger(UserActor.class);
    private final ActorContext<Message> context;
    private final WSClient ws;
    private final String apiKey;
    private final String url;
    private final Materializer mat;

    private final Sink<JsonNode, NotUsed> hubSink;
    private final Flow<JsonNode, JsonNode, NotUsed> websocketFlow;

    // Cache to track queries and their results
    private final Map<String, QueryResult> cache = new LinkedHashMap<>();
    // Track seen articles per query (using URL + title as key)
    private final Map<String, Set<String>> seenArticles = new HashMap<>();
    // Active search queries
    private String activeQuery = null;
    private String activeSortBy = "publishedAt";

    public static Behavior<Message> create(String id, ActorRef<GetSources> sourcesActor,
                                           ActorRef<ReadabilityActor.Command> readabilityActor,
                                           WSClient ws, Config config) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers ->
                        new UserActor(id, sourcesActor, readabilityActor, ws, config, context, timers).behavior()
                )
        );
    }

    @Inject
    public UserActor(String id,
                     ActorRef<GetSources> sourcesActor,
                     ActorRef<ReadabilityActor.Command> readabilityActor,
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

        // Start polling timer (every 60 seconds)
        timers.startTimerAtFixedRate(PollTick.get(), Duration.ofSeconds(5)); //CHANGE HERE TO TEST UPDATES

        Pair<Sink<JsonNode, NotUsed>, Source<JsonNode, NotUsed>> sinkSourcePair =
                MergeHub.of(JsonNode.class, 16)
                        .toMat(BroadcastHub.of(JsonNode.class, 256), Keep.both())
                        .run(mat);

        this.hubSink = sinkSourcePair.first();
        Source<JsonNode, NotUsed> hubSource = sinkSourcePair.second();

        Sink<JsonNode, CompletionStage<Done>> jsonSink = Sink.foreach((JsonNode json) -> {
            logger.info("Received WebSocket message: {}", json);
            handleIncomingMessage(json);
        });

        this.websocketFlow = Flow.fromSinkAndSourceCoupled(jsonSink, hubSource);
    }

    private void handleIncomingMessage(JsonNode json) {
        String type = json.has("type") ? json.get("type").asText() : "";

        switch (type) {
            case "search":
                handleSearch(json);
                break;
            case "filter":
                handleSourceFilter(json);
                break;
            default:
                logger.warn("Unknown message type: {}", type);
        }
    }

    private void handleSearch(JsonNode json) {
        String query = json.has("query") ? json.get("query").asText() : "";
        String sortBy = json.has("sortBy") ? json.get("sortBy").asText() : "publishedAt";

        if (query.isEmpty()) {
            logger.warn("Empty search query received");
            return;
        }

        logger.info("Starting search for: {} with sortBy: {}", query, sortBy);

        this.activeQuery = query;
        this.activeSortBy = sortBy;

        if (cache.containsKey(query)) {
            cache.remove(query);
            seenArticles.remove(query);
            logger.info("Removed existing query to re-add as most recent: {}", query);
        }

        while (cache.size() >= 10) { //Trying to force remove until we keep 10 latest
            String oldestKey = cache.keySet().iterator().next();
            cache.remove(oldestKey);
            seenArticles.remove(oldestKey);
            logger.info("Removed oldest query from cache: {} (size was {})", oldestKey, cache.size() + 1);
        }

        seenArticles.put(query, new HashSet<>());

        fetchAndSendResults(query, sortBy, true);
    }

    private void fetchAndSendResults(String query, String sortBy, boolean isInitial) {
        String encodedQuery = query.trim().replaceAll("\\s+", "+");
        String requestUrl = this.url + "q=" + encodedQuery + "&sortBy=" + sortBy +
                "&pageSize=50&apiKey=" + this.apiKey;

        logger.info("Fetching from: {}", requestUrl);

        ws.url(requestUrl)
                .setRequestTimeout(Duration.ofSeconds(10))
                .get()
                .thenAccept(response -> {
                    JsonNode articlesJson = response.asJson();
                    if (articlesJson == null || !articlesJson.has("articles")) {
                        logger.warn("No articles found in response");
                        return;
                    }

                    List<Article> allArticles = parseArticles(articlesJson.get("articles"));

                    // Filter out duplicates
                    Set<String> seen = seenArticles.get(query);
                    List<Article> newArticles = allArticles.stream()
                            .filter(article -> {
                                String key = getArticleKey(article);
                                if (seen.contains(key)) {
                                    return false;
                                } else {
                                    seen.add(key);
                                    return true;
                                }
                            })
                            .collect(Collectors.toList());

                    if (isInitial) {
                        // Send up to 10 initial results
                        List<Article> initial = newArticles.stream().limit(10).collect(Collectors.toList());
                        if (!initial.isEmpty()) {
                            calculateReadabilityAndSend(query, initial, "initial");
                        }
                    } else {
                        // Send new results as updates
                        if (!newArticles.isEmpty()) {
                            logger.info("Found {} new articles for query: {}", newArticles.size(), query);
                            calculateReadabilityAndSend(query, newArticles, "update");
                        } else {
                            logger.info("No new articles for query: {}", query);
                        }
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error fetching articles for query: " + query, ex);
                    return null;
                });
    }

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
            double avgGrade = readResult.averageGrade;
            double avgScore = readResult.averageScore;

            QueryResult qr = new QueryResult(query, articles, avgGrade, avgScore);

            // Update cache
            QueryResult existing = cache.get(query);
            if (existing != null) {
                // Merge with existing articles
                List<Article> merged = new ArrayList<>(existing.getArticles());
                merged.addAll(articles);
                qr = new QueryResult(query, merged,
                        (existing.getAvgGrade() + avgGrade) / 2,
                        (existing.getAvgScore() + avgScore) / 2);
            }
            cache.put(query, qr);

            // Send to WebSocket
            sendArticlesToClient(query, articles, avgGrade, avgScore, messageType);
        }).exceptionally(ex -> {
            logger.error("Error calculating readability", ex);
            return null;
        });
    }

    private void sendArticlesToClient(String query, List<Article> articles,
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
        for (Article article : articles) {
            ObjectNode articleNode = mapper.createObjectNode();
            articleNode.put("title", article.getTitle());
            articleNode.put("description", article.getDescription());
            articleNode.put("url", article.getUrl());
            articleNode.put("publishedAt", article.getPublishedAt());
            articleNode.put("sourceName", article.getSourceName());
            articleNode.put("sourceUrl", article.getSourceUrl());
            articlesArray.add(articleNode);
        }
        message.set("articles", articlesArray);

        Source.<JsonNode>single(message)
                .runWith(hubSink, mat);

        logger.info("Sent {} articles to client for query: {}", articles.size(), query);
    }

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
                        0, // kincaidGrade - will be calculated later
                        0, // readingScore - will be calculated later
                        node.has("description") ? node.get("description").asText() : ""
                );
                articles.add(article);
            }
        }
        return articles;
    }

    private String getArticleKey(Article article) {
        return article.getUrl() + "|" + article.getTitle();
    }

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

        logger.info("Fetching sources from: {}", requestUrl);

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
                    logger.info("Sent sources to client");
                })
                .exceptionally(ex -> {
                    logger.error("Error fetching sources", ex);
                    return null;
                });
    }

    public Behavior<Message> behavior() {
        return Behaviors
                .receive(Message.class)
                .onMessage(UserParentActor.GetFlow.class, getFlow -> {
                    getFlow.replyTo.tell(websocketFlow);
                    return Behaviors.same();
                })
                .onMessage(PollTick.class, tick -> {
                    // Poll for new articles if there's an active search
                    if (activeQuery != null) {
                        logger.info("Polling for new articles for query: {}", activeQuery);
                        fetchAndSendResults(activeQuery, activeSortBy, false);
                    }
                    return Behaviors.same();
                })
                .onMessageEquals(InternalStop.get(), Behaviors::stopped)
                .onSignal(PostStop.class, _postStop -> {
                    context.getLog().info("Stopping actor {}", context.getSelf());
                    return Behaviors.same();
                })
                .build();
    }

    public Flow<JsonNode, JsonNode, NotUsed> getWebsocketFlow() {
        return websocketFlow;
    }

    public interface Factory {
        Behavior<Message> create(String id);
    }
}
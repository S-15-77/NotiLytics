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
import org.apache.pekko.stream.javadsl.*;
import com.fasterxml.jackson.databind.JsonNode;

import javax.inject.Inject;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.Materializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.ws.WSClient;
import com.typesafe.config.Config;

public class UserActor {
    public interface Message {}

    private static final class InternalStop implements Message {
        private static final InternalStop INSTANCE = new InternalStop();
        public static InternalStop get() {
            return INSTANCE;
        }
        private InternalStop() {}
    }

    private final Duration timeout = Duration.of(3, ChronoUnit.SECONDS);

    private final String id;
    private final ActorRef<SourcesActor.GetSources> sourcesActor;
    private final Scheduler scheduler;
    private static final Logger logger = LoggerFactory.getLogger(UserActor.class);
    private final ActorContext<Message> context;
    private final WSClient ws;
    private final String apiKey;
    private final Materializer mat;

    private final Sink<JsonNode, NotUsed> hubSink;

    private final Flow<JsonNode, JsonNode, NotUsed> websocketFlow;

    public static Behavior<Message> create(String id, ActorRef<GetSources> sourcesActor, WSClient ws, Config config) {
        return Behaviors.setup(context -> new UserActor(id, sourcesActor, ws, config, context).behavior());    }

    @Inject
    public UserActor(String id,
                     ActorRef<GetSources> sourcesActor,
                     WSClient ws,
                     Config config,
                     ActorContext<Message> context) {
        this.id = id;
        this.sourcesActor = sourcesActor;
        this.ws = ws;
        this.apiKey = config.getString("newsapi.key");
        this.scheduler = context.getSystem().scheduler();
        this.context = context;
        this.mat = Materializer.matFromSystem(context.getSystem());

        Pair<Sink<JsonNode, NotUsed>, Source<JsonNode, NotUsed>> sinkSourcePair =
                MergeHub.of(JsonNode.class, 16)
                        .toMat(BroadcastHub.of(JsonNode.class, 256), Keep.both())
                        .run(mat);

        this.hubSink = sinkSourcePair.first();
        Source<JsonNode, NotUsed> hubSource = sinkSourcePair.second();

        Sink<JsonNode, CompletionStage<Done>> jsonSink = Sink.foreach((JsonNode json) -> {
            logger.info("Received filter request: {}", json);
            handleSourceFilter(json);
        });

        this.websocketFlow = Flow.fromSinkAndSourceCoupled(jsonSink, hubSource);
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
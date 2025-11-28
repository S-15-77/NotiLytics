package actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.*;

import play.mvc.Result;
import play.mvc.Results;
import play.libs.ws.WSClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
//
//import play.libs.json.*;

import models.Article;
import models.SourceProfile;
import Services.Client;

public class SourceProfileActor extends AbstractBehavior<SourceProfileActor.Command> {

    public interface Command {}

    public static final class ProfileRequest implements Command {
        public final String sourceName;
        public final String id;
        public final ActorRef<Result> replyTo;

        public ProfileRequest(String sourceName, String id, ActorRef<Result> replyTo) {
            this.sourceName = sourceName;
            this.id = id;
            this.replyTo = replyTo;
        }
    }

    public static Behavior<Command> create(String baseUrl, String apiKey, WSClient ws) {
        return Behaviors.setup(ctx -> new SourceProfileActor(ctx, baseUrl, apiKey, ws));
    }

    private final String baseUrl;
    private final String apiKey;
    private final WSClient ws;

    private SourceProfileActor(ActorContext<Command> ctx, String baseUrl, String apiKey, WSClient ws) {
        super(ctx);
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.ws = ws;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProfileRequest.class, this::onProfileRequest)
                .build();
    }

    private Behavior<Command> onProfileRequest(ProfileRequest msg) {
        String resolved = msg.id != null ? msg.id : msg.sourceName;

        String requestUrl = baseUrl + "sources=" + resolved + "&apiKey=" + apiKey;

        Client client = new Client(this.ws);

        CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

        response.whenComplete((articles, ex) -> {

            if (ex != null) {
                msg.replyTo.tell(
                        Results.internalServerError("Error retrieving articles: " + ex.getMessage())
                );
                return;
            }

            if (articles == null || articles.isEmpty()) {
                SourceProfile profile = new SourceProfile(
                        msg.sourceName,
                        "",
                        "No Articles Found for this source at this time. Please try again later!"
                );

                msg.replyTo.tell(
                        Results.ok(
                                views.html.sourceProfile.render(profile, new ArrayList<>())
                        )
                );
                return;
            }

            List<Article> last10 = articles.stream().limit(10).collect(Collectors.toList());

            SourceProfile profile = new SourceProfile(
                    msg.sourceName,
                    last10.get(0).getSourceUrl(),
                    "Listing Articles from " + msg.sourceName + "."
            );

            msg.replyTo.tell(
                    Results.ok(
                            views.html.sourceProfile.render(profile, last10)
                    )
            );
        });

        return this;
    }

//    public static List<Article> fromJson(String json) {
//        JsonNode root = Json.parse(json);
//
//        JsonNode articlesNode = root.get("articles");
//        if (articlesNode == null || !articlesNode.isArray()) {
//            return Collections.emptyList();
//        }
//
//        List<Article> list = new ArrayList<>();
//
//        for (JsonNode n : articlesNode) {
//            String title = safe(n, "title");
//            String url = safe(n, "url");
//
//            // source URL may nest in "source" object
//            String sourceUrl = "";
//            if (n.has("source") && n.get("source").has("url")) {
//                sourceUrl = n.get("source").get("url").asText("");
//            }
//
//            list.add(new Article(title, url, sourceUrl));
//        }
//
//        return list;
//    }
//
//    private static String safe(JsonNode n, String field) {
//        return n.has(field) ? n.get(field).asText("") : "";
//    }



}
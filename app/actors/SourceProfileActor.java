package actors;

import models.ReadabilityCalculator;
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

/**
 * Actor that fetches the last 10 articles of a Source and returns them.
 * @author Haytham
 */
public class SourceProfileActor extends AbstractBehavior<SourceProfileActor.Command> {

    /** Marker interface for actor commands. */
    public interface Command {}

    /**
     * Message to request a profile and its source name and ID.
     */
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

    /**
     * Factory to create the actor's behavior.
     */
    public static Behavior<Command> create(String baseUrl, String apiKey, WSClient ws) {
        return Behaviors.setup(ctx -> new SourceProfileActor(ctx, baseUrl, apiKey, ws));
    }


    private final String baseUrl;
    private final String apiKey;
    private final WSClient ws;

    /**
     * Constructor to build an actor with the specified parameters.
     * @param ctx Actor Context
     * @param baseUrl Source Base URL
     * @param apiKey App Api Key
     * @param ws WSClient
     */
    private SourceProfileActor(ActorContext<Command> ctx, String baseUrl, String apiKey, WSClient ws) {
        super(ctx);
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.ws = ws;

        clientFactory = Client::new;
    }

    // Custom Client Creation
    public SourceProfileActor(ActorContext<Command> ctx, String baseUrl, String apiKey, WSClient ws,
                              ClientFactory clientFactory) {
        super(ctx);
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.ws = ws;
        this.clientFactory = clientFactory;
    }

    private final ClientFactory clientFactory;

    public interface ClientFactory {
        Client create(WSClient ws);
    }

    public static Behavior<Command> create(String baseUrl, String apiKey, WSClient ws, ClientFactory factory) {
        return Behaviors.setup(ctx -> new SourceProfileActor(ctx, baseUrl, apiKey, ws, factory));
    }

    /**
     * Defines message handling for this actor.
     * @return RecieveBuilder
     */
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProfileRequest.class, this::onProfileRequest)
                .build();
    }

    /**
     * Handles fetching and returning articles for the Profile Request.
     *
     * @param msg the ProfileRequest message
     */
    private Behavior<Command> onProfileRequest(ProfileRequest msg) {
        String resolved = msg.id != null ? msg.id : msg.sourceName;

        String requestUrl = baseUrl + "sources=" + resolved + "&apiKey=" + apiKey;

        Client client = clientFactory.create(ws);

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
}
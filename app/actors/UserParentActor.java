package actors;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.stream.javadsl.Flow;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;

public final class UserParentActor {
    private UserParentActor() {}

    /**
     * Creates a new UserParentActor behavior.
     * @param childFactory Factory for creating child UserActors
     * @param config Application configuration
     * @return The actor behavior
     */
    public static Behavior<Create> create(UserActor.Factory childFactory, Config config) {
        return Behaviors.setup(context -> {
            return Behaviors.receive(Create.class)
                    .onMessage(Create.class, create -> {
                        Behavior<UserActor.Message> childBehavior = Behaviors.supervise(
                                        childFactory.create(create.id)
                                )
                                .onFailure(SupervisorStrategy.restart());

                        ActorRef<UserActor.Message> child = context.spawn(
                                childBehavior,
                                "userActor-" + create.id
                        );
                        child.tell(new GetFlow(create.replyTo));
                        return Behaviors.same();
                    })
                    .build();
        });
    }

    /**
     * Message to request the WebSocket flow from a UserActor.
     */
    public static class GetFlow implements UserActor.Message {
        final ActorRef<Flow<JsonNode, JsonNode, NotUsed>> replyTo;

        public GetFlow(ActorRef<Flow<JsonNode, JsonNode, NotUsed>> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /**
     * Message to create a new UserActor child.
     */
    public static final class Create {
        final String id;
        final ActorRef<Flow<JsonNode, JsonNode, NotUsed>> replyTo;

        public Create(String id, ActorRef<Flow<JsonNode, JsonNode, NotUsed>> replyTo) {
            this.id = id;
            this.replyTo = replyTo;
        }
    }
}
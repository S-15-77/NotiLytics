package actors;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.stream.javadsl.Flow;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;

public final class UserParentActor {
    private UserParentActor() {}

    public static Behavior<Create> create(UserActor.Factory childFactory, Config config) {
        return Behaviors.setup(context -> {
            return Behaviors.receive(Create.class)
                    .onMessage(Create.class, create -> {
                        ActorRef<UserActor.Message> child = context.spawn(
                                childFactory.create(create.id),
                                "userActor-" + create.id
                        );
                        child.tell(new GetFlow(create.replyTo));
                        return Behaviors.same();
                    })
                    .build();
        });
    }

    public static class GetFlow implements UserActor.Message {
        final ActorRef<Flow<JsonNode, JsonNode, NotUsed>> replyTo;

        public GetFlow(ActorRef<Flow<JsonNode, JsonNode, NotUsed>> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class Create {
        final String id;
        final ActorRef<Flow<JsonNode, JsonNode, NotUsed>> replyTo;

        public Create(String id, ActorRef<Flow<JsonNode, JsonNode, NotUsed>> replyTo) {
            this.id = id;
            this.replyTo = replyTo;
        }
    }
}
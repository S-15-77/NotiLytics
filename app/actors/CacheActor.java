package actors;

import models.QueryResult;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates the actor managing the 50 articles in the cache.
 */
public class CacheActor {

    /**
     * Interface for Caching Actor
     * @author Karim
     */
    public interface Command {}

    /**
     * Command to put in the cache
     * @param key
     * @param value
     * @author Karim
     */
    public record Put(String key, QueryResult value) implements Command {}

    /**
     * Command to get back from the cache
     * @param key
     * @param replyTo
     * @author Karim
     */
    public record Get(String key, ActorRef<Response> replyTo) implements Command {}

    /**
     * Respond to the message
     * @param result
     * @author Karim
     */
    public record Response(QueryResult result) {}

    /**
     * Creates the behaviour for the cache actor
     * @return the behaviour
     */
    public static Behavior<Command> create() {
        return Behaviors.setup(ctx -> {

            Map<String, QueryResult> cache = new HashMap<>();

            return Behaviors.receive(Command.class)
                    .onMessage(Put.class, msg -> {
                        cache.put(msg.key, msg.value);
                        return Behaviors.same();
                    })
                    .onMessage(Get.class, msg -> {
                        QueryResult r = cache.get(msg.key);
                        msg.replyTo.tell(new Response(r));
                        return Behaviors.same();
                    })
                    .build();
        });
    }


}

package actors;

import models.Statistics;
import models.QueryResult;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.util.List;
import java.util.Map;

/**
 * Implements the Actor calculating the statistics
 * @author Karim
 */
public class StatisticsActor {

    /**
     * Interface for commands
     * @author Karim
     */
    public interface Command {}

    /**
     * Compute tge statistics based on the parameters
     * @param query
     * @param replyTo
     * @author Karim
     */
    public record Compute(QueryResult query, ActorRef<Response> replyTo) implements Command {}

    /**
     * Returns after computation
     * @param resultString
     * @param counter
     * @author Karim
     */
    public record Response(String resultString, Map<String, Long> counter) {}

    /**
     * Creates behaviour for Statistic Actor
     * @return behaviour of computing stats on Articles
     */
    public static Behavior<Command> create() {
        return Behaviors.receive((ctx, msg) -> {

            if (msg instanceof Compute c) {
                ctx.getLog().info("StatisticsActor: stats on the way");

                //Use the statistic function from project part 1
                Statistics stats = new Statistics(c.query());

                List<String> words = Statistics.getWords(stats.getTitles());
                words.addAll(Statistics.getWords(stats.getDescriptions()));

                List<String> filtered = Statistics.filtering(words);
                Map<String, Long> counter = Statistics.getCounter(filtered);
                String output = Statistics.getString(counter);

                //Reply
                c.replyTo().tell(new Response(output, counter));
            }

            return Behaviors.same();
        });
    }
}

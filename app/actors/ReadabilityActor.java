package actors;

import models.ReadabilityCalculator;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Actor that calculates readability metrics for a list of descriptions.
 * It delegates the actual calculations to {@link ReadabilityCalculator}.
 * <p>
 * Messages:
 * - {@link Calculate}: request carrying descriptions and a replyTo
 * - {@link Result}: reply containing per-description grade/score and averages
 * </p>
 * @author Santhosh
 */
public class ReadabilityActor {

    /** Marker interface for actor commands. */
    public interface Command {}

    /**
     * Message to request calculation of readability metrics.
     */
    public static final class Calculate implements Command {
        public final List<String> descriptions;
        public final ActorRef<Result> replyTo;

        public Calculate(List<String> descriptions, ActorRef<Result> replyTo) {
            this.descriptions = descriptions;
            this.replyTo = replyTo;
        }
    }

    /**
     * Reply message with per-description results and averages.
     */
    public static final class Result {
        public final List<Double> grades;
        public final List<Double> scores;
        public final double averageGrade;
        public final double averageScore;

        public Result(List<Double> grades, List<Double> scores, double averageGrade, double averageScore) {
            this.grades = grades;
            this.scores = scores;
            this.averageGrade = averageGrade;
            this.averageScore = averageScore;
        }
    }

    /** Factory to create the actor behavior. */
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> Behaviors.receive(Command.class)
                .onMessage(Calculate.class, msg -> {
                    // Delegate to compute() so logic can be tested synchronously
                    Result result = compute(msg.descriptions);
                    msg.replyTo.tell(result);
                    return Behaviors.same();
                })
                .build());
    }

    /**
     * Synchronous, testable computation that mirrors the actor's behavior.
     * This method intentionally does not modify ReadabilityCalculator.
     *
     * @param descriptions list of descriptions to process
     * @return Result containing per-item grades/scores and averages
     */
    public static Result compute(List<String> descriptions) {
        Objects.requireNonNull(descriptions, "descriptions must not be null");

        // compute per-item readability metrics (limit to 50 as per requirements)
        List<String> limited = descriptions.stream()
                .filter(d -> d != null && !d.trim().isEmpty())
                .limit(50)
                .collect(Collectors.toList());

        List<Double> grades = limited.stream()
                .map(ReadabilityCalculator::calculateFleschKincaidGrade)
                .collect(Collectors.toList());

        List<Double> scores = limited.stream()
                .map(ReadabilityCalculator::calculateFleschReadingScore)
                .collect(Collectors.toList());

        double avgGrade = ReadabilityCalculator.averageGrade(limited);
        double avgScore = ReadabilityCalculator.averageScore(limited);

        return new Result(grades, scores, avgGrade, avgScore);
    }

}

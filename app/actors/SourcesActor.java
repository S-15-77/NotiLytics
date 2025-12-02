package actors;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import models.Source;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Implements the actor returning the list of sources
 * @author ihasnaou
 */
public final class SourcesActor {
    private SourcesActor() {}

    /**
     * Message containing list of sources
     */
    public static class Sources {
        final Set<Source> sources;

        public Sources(Set<Source> sources) {
            this.sources = requireNonNull(sources);
        }
    }

    /**
     * Message asking for list of sources
     */
    public static final class GetSources {
        final Set<String> sourceIds;
        final ActorRef<Sources> replyTo;

        public GetSources(Set<String> sourceIds, ActorRef<Sources> replyTo) {
            this.sourceIds = requireNonNull(sourceIds);
            this.replyTo = requireNonNull(replyTo);
        }

        @Override
        public String toString() {
            return "GetSources(" + sourceIds + ")";
        }
    }

    /**
     * Creates a new SourcesActor behavior.
     * @return The actor behavior
     */
    public static Behavior<GetSources> create() {
        Map<String, Source> sourcesMap = new HashMap<>();
        return Behaviors.logMessages(
                Behaviors
                        .receive(GetSources.class)
                        .onMessage(GetSources.class, getSources -> {
                            Set<Source> sources = getSources.sourceIds.stream()
                                    .map(sourcesMap::get)
                                    .filter(s -> s != null)
                                    .collect(Collectors.toSet()); //Exactly like the lab
                            getSources.replyTo.tell(new Sources(sources));
                            return Behaviors.same();
                        })
                        .build()
        );
    }
}
package actors;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import models.QueryResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public final class QueryResultActor {
    private QueryResultActor() {}

    public static class QueryResults {
        final Map<String, QueryResult> queryResults;

        public QueryResults(Map<String, QueryResult> queryResults) {
            this.queryResults = requireNonNull(queryResults);
        }
    }

    public static final class GetQueryResults {
        final Set<String> queries;
        final ActorRef<QueryResults> replyTo;

        public GetQueryResults(Set<String> queries, ActorRef<QueryResults> replyTo) {
            this.queries = requireNonNull(queries);
            this.replyTo = requireNonNull(replyTo);
        }

        @Override
        public String toString() {
            return "GetQueryResults(" + queries + ")";
        }
    }

    public static Behavior<GetQueryResults> create() {
        Map<String, QueryResult> queryResultsMap = new LinkedHashMap<>();
        return Behaviors.logMessages(
                Behaviors
                        .receive(GetQueryResults.class)
                        .onMessage(GetQueryResults.class, getQueryResults -> {
                            Map<String, QueryResult> results = new LinkedHashMap<>();
                            for (String query : getQueryResults.queries) {
                                QueryResult result = queryResultsMap.get(query);
                                if (result != null) {
                                    results.put(query, result);
                                }
                            }
                            getQueryResults.replyTo.tell(new QueryResults(results));
                            return Behaviors.same();
                        })
                        .build()
        );
    }
}
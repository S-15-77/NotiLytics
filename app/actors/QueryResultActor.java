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

    public interface Message {}

    /**
     * Message to request a search for articles.
     */
    public static final class SearchRequest implements Message {
        public final String query;
        public final String sortBy;

        public SearchRequest(String query, String sortBy) {
            this.query = requireNonNull(query);
            this.sortBy = sortBy != null ? sortBy : "publishedAt";
        }

        @Override
        public String toString() {
            return "SearchRequest(" + query + ", " + sortBy + ")";
        }
    }

    /**
     * Message to stop tracking a specific search query.
     */
    public static final class StopSearch implements Message {
        public final String query;

        public StopSearch(String query) {
            this.query = requireNonNull(query);
        }

        @Override
        public String toString() {
            return "StopSearch(" + query + ")";
        }
    }

    /**
     * Message containing new articles for a query.
     */
    public static final class NewArticles implements Message {
        public final String query;
        public final QueryResult result;

        public NewArticles(String query, QueryResult result) {
            this.query = requireNonNull(query);
            this.result = requireNonNull(result);
        }

        @Override
        public String toString() {
            return "NewArticles(" + query + ", " + result.getArticles().size() + " articles)";
        }
    }

    /**
     * Response message containing query results.
     */
    public static class QueryResults {
        final Map<String, QueryResult> queryResults;

        public QueryResults(Map<String, QueryResult> queryResults) {
            this.queryResults = requireNonNull(queryResults);
        }
    }

    /**
     * Message to retrieve cached query results for specified queries.
     */
    public static final class GetQueryResults implements Message {
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

    /**
     * Creates a new QueryResultActor behavior.
     *
     * @return The actor behavior
     */
    public static Behavior<Message> create() {
        Map<String, QueryResult> queryResultsMap = new LinkedHashMap<>();
        return Behaviors.logMessages(
                Behaviors
                        .receive(Message.class)
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
                        .onMessage(NewArticles.class, newArticles -> {
                            // Store or update the query result
                            queryResultsMap.put(newArticles.query, newArticles.result);
                            return Behaviors.same();
                        })
                        .build()
        );
    }
}
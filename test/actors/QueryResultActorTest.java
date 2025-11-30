package actors;

import models.Article;
import models.QueryResult;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class QueryResultActorTest {
    private static ActorTestKit testKit;

    @BeforeClass
    public static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterClass
    public static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    public void testGetQueryResults_emptyQueries() {
        Behavior<QueryResultActor.Message> behavior = QueryResultActor.create();
        ActorRef<QueryResultActor.Message> actor = testKit.spawn(behavior);
        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();

        actor.tell(new QueryResultActor.GetQueryResults(Collections.emptySet(), probe.getRef()));

        QueryResultActor.QueryResults result = probe.receiveMessage();
        assertNotNull(result);
        assertTrue(result.queryResults.isEmpty());
    }

    @Test
    public void testGetQueryResults_nonExistentQuery() {
        Behavior<QueryResultActor.Message> behavior = QueryResultActor.create();
        ActorRef<QueryResultActor.Message> actor = testKit.spawn(behavior);
        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();

        Set<String> queries = new HashSet<>();
        queries.add("nonexistent");

        actor.tell(new QueryResultActor.GetQueryResults(queries, probe.getRef()));

        QueryResultActor.QueryResults result = probe.receiveMessage();
        assertNotNull(result);
        assertTrue(result.queryResults.isEmpty());
    }

    @Test
    public void testNewArticles_storesQueryResult() {
        Behavior<QueryResultActor.Message> behavior = QueryResultActor.create();
        ActorRef<QueryResultActor.Message> actor = testKit.spawn(behavior);
        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();

        List<Article> articles = Arrays.asList(
                new Article("Title1", "url1", "source1", "sourceUrl1", "2024-01-01", 5, 60, "desc1")
        );
        QueryResult queryResult = new QueryResult("test query", articles, 5.0, 60.0);

        actor.tell(new QueryResultActor.NewArticles("test query", queryResult));

        Set<String> queries = new HashSet<>();
        queries.add("test query");
        actor.tell(new QueryResultActor.GetQueryResults(queries, probe.getRef()));

        QueryResultActor.QueryResults result = probe.receiveMessage();
        assertNotNull(result);
        assertEquals(1, result.queryResults.size());
        assertTrue(result.queryResults.containsKey("test query"));
        assertEquals(1, result.queryResults.get("test query").getArticles().size());
    }

    @Test
    public void testNewArticles_updatesExistingQuery() {
        Behavior<QueryResultActor.Message> behavior = QueryResultActor.create();
        ActorRef<QueryResultActor.Message> actor = testKit.spawn(behavior);
        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();

        List<Article> articles1 = Arrays.asList(
                new Article("Title1", "url1", "source1", "sourceUrl1", "2024-01-01", 5, 60, "desc1")
        );
        QueryResult queryResult1 = new QueryResult("query", articles1, 5.0, 60.0);
        actor.tell(new QueryResultActor.NewArticles("query", queryResult1));

        List<Article> articles2 = Arrays.asList(
                new Article("Title2", "url2", "source2", "sourceUrl2", "2024-01-02", 6, 70, "desc2")
        );
        QueryResult queryResult2 = new QueryResult("query", articles2, 6.0, 70.0);
        actor.tell(new QueryResultActor.NewArticles("query", queryResult2));

        Set<String> queries = new HashSet<>();
        queries.add("query");
        actor.tell(new QueryResultActor.GetQueryResults(queries, probe.getRef()));

        QueryResultActor.QueryResults result = probe.receiveMessage();
        assertNotNull(result);
        assertEquals(1, result.queryResults.size());
        assertEquals(1, result.queryResults.get("query").getArticles().size());
        assertEquals("Title2", result.queryResults.get("query").getArticles().get(0).getTitle());
    }

    @Test
    public void testGetQueryResults_multipleQueries() {
        Behavior<QueryResultActor.Message> behavior = QueryResultActor.create();
        ActorRef<QueryResultActor.Message> actor = testKit.spawn(behavior);
        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();

        List<Article> articles1 = Arrays.asList(
                new Article("Title1", "url1", "source1", "sourceUrl1", "2024-01-01", 5, 60, "desc1")
        );
        QueryResult queryResult1 = new QueryResult("query1", articles1, 5.0, 60.0);
        actor.tell(new QueryResultActor.NewArticles("query1", queryResult1));

        List<Article> articles2 = Arrays.asList(
                new Article("Title2", "url2", "source2", "sourceUrl2", "2024-01-02", 6, 70, "desc2")
        );
        QueryResult queryResult2 = new QueryResult("query2", articles2, 6.0, 70.0);
        actor.tell(new QueryResultActor.NewArticles("query2", queryResult2));

        Set<String> queries = new HashSet<>();
        queries.add("query1");
        queries.add("query2");
        actor.tell(new QueryResultActor.GetQueryResults(queries, probe.getRef()));

        QueryResultActor.QueryResults result = probe.receiveMessage();
        assertNotNull(result);
        assertEquals(2, result.queryResults.size());
        assertTrue(result.queryResults.containsKey("query1"));
        assertTrue(result.queryResults.containsKey("query2"));
    }

    @Test
    public void testGetQueryResults_partialMatch() {
        Behavior<QueryResultActor.Message> behavior = QueryResultActor.create();
        ActorRef<QueryResultActor.Message> actor = testKit.spawn(behavior);
        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();

        List<Article> articles = Arrays.asList(
                new Article("Title1", "url1", "source1", "sourceUrl1", "2024-01-01", 5, 60, "desc1")
        );
        QueryResult queryResult = new QueryResult("existing", articles, 5.0, 60.0);
        actor.tell(new QueryResultActor.NewArticles("existing", queryResult));

        Set<String> queries = new HashSet<>();
        queries.add("existing");
        queries.add("nonexistent");
        actor.tell(new QueryResultActor.GetQueryResults(queries, probe.getRef()));

        QueryResultActor.QueryResults result = probe.receiveMessage();
        assertNotNull(result);
        assertEquals(1, result.queryResults.size());
        assertTrue(result.queryResults.containsKey("existing"));
        assertFalse(result.queryResults.containsKey("nonexistent"));
    }

    @Test(expected = NullPointerException.class)
    public void testGetQueryResults_nullQueriesThrows() {
        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();
        new QueryResultActor.GetQueryResults(null, probe.getRef());
    }

    @Test(expected = NullPointerException.class)
    public void testGetQueryResults_nullReplyToThrows() {
        Set<String> queries = new HashSet<>();
        queries.add("query");
        new QueryResultActor.GetQueryResults(queries, null);
    }

    @Test(expected = NullPointerException.class)
    public void testNewArticles_nullQueryThrows() {
        List<Article> articles = Arrays.asList(
                new Article("Title1", "url1", "source1", "sourceUrl1", "2024-01-01", 5, 60, "desc1")
        );
        QueryResult queryResult = new QueryResult("query", articles, 5.0, 60.0);
        new QueryResultActor.NewArticles(null, queryResult);
    }

    @Test(expected = NullPointerException.class)
    public void testNewArticles_nullResultThrows() {
        new QueryResultActor.NewArticles("query", null);
    }

    @Test(expected = NullPointerException.class)
    public void testSearchRequest_nullQueryThrows() {
        new QueryResultActor.SearchRequest(null, "publishedAt");
    }

    @Test
    public void testSearchRequest_nullSortByDefaultsToPublishedAt() {
        QueryResultActor.SearchRequest request = new QueryResultActor.SearchRequest("query", null);
        assertNotNull(request);
        assertEquals("query", request.query);
        assertEquals("publishedAt", request.sortBy);
    }

    @Test
    public void testSearchRequest_customSortBy() {
        QueryResultActor.SearchRequest request = new QueryResultActor.SearchRequest("query", "relevancy");
        assertNotNull(request);
        assertEquals("query", request.query);
        assertEquals("relevancy", request.sortBy);
    }

    @Test(expected = NullPointerException.class)
    public void testStopSearch_nullQueryThrows() {
        new QueryResultActor.StopSearch(null);
    }

    @Test
    public void testStopSearch_validQuery() {
        QueryResultActor.StopSearch stopSearch = new QueryResultActor.StopSearch("query");
        assertNotNull(stopSearch);
        assertEquals("query", stopSearch.query);
    }

    @Test
    public void testToStringMethods() {
        QueryResultActor.SearchRequest searchRequest = new QueryResultActor.SearchRequest("test", "publishedAt");
        assertTrue(searchRequest.toString().contains("SearchRequest"));
        assertTrue(searchRequest.toString().contains("test"));

        QueryResultActor.StopSearch stopSearch = new QueryResultActor.StopSearch("test");
        assertTrue(stopSearch.toString().contains("StopSearch"));
        assertTrue(stopSearch.toString().contains("test"));

        List<Article> articles = Arrays.asList(new Article("Title1", "url1", "source1", "sourceUrl1", "2024-01-01", 5, 60, "desc1"));
        QueryResult queryResult = new QueryResult("test", articles, 5.0, 60.0);
        QueryResultActor.NewArticles newArticles = new QueryResultActor.NewArticles("test", queryResult);
        assertTrue(newArticles.toString().contains("NewArticles"));
        assertTrue(newArticles.toString().contains("test"));

        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();
        QueryResultActor.GetQueryResults getQueryResults = new QueryResultActor.GetQueryResults(Collections.singleton("test"), probe.getRef());
        assertTrue(getQueryResults.toString().contains("GetQueryResults"));
    }

    @Test
    public void testNewArticles_multipleArticles() {
        Behavior<QueryResultActor.Message> behavior = QueryResultActor.create();
        ActorRef<QueryResultActor.Message> actor = testKit.spawn(behavior);
        TestProbe<QueryResultActor.QueryResults> probe = testKit.createTestProbe();

        List<Article> articles = Arrays.asList(
                new Article("Title1", "url1", "source1", "sourceUrl1", "2024-01-01", 5, 60, "desc1"),
                new Article("Title2", "url2", "source2", "sourceUrl2", "2024-01-02", 6, 70, "desc2"),
                new Article("Title3", "url3", "source3", "sourceUrl3", "2024-01-03", 7, 80, "desc3")
        );
        QueryResult queryResult = new QueryResult("query", articles, 6.0, 70.0);
        actor.tell(new QueryResultActor.NewArticles("query", queryResult));

        Set<String> queries = new HashSet<>();
        queries.add("query");
        actor.tell(new QueryResultActor.GetQueryResults(queries, probe.getRef()));

        QueryResultActor.QueryResults result = probe.receiveMessage();
        assertNotNull(result);
        assertEquals(3, result.queryResults.get("query").getArticles().size());
    }

    @Test(expected = NullPointerException.class)
    public void testQueryResults_nullMapThrows() {
        new QueryResultActor.QueryResults(null);
    }

    @Test
    public void testQueryResults_emptyMap() {
        QueryResultActor.QueryResults results =
                new QueryResultActor.QueryResults(new LinkedHashMap<>());
        assertNotNull(results);
        assertTrue(results.queryResults.isEmpty());
    }
}
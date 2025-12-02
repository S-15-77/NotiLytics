package actors;

import models.Article;
import models.QueryResult;
import models.Statistics;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class StatisticsActorTest {

    private static ActorTestKit testKit;

    @BeforeClass
    public static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterClass
    public static void teardown() {
        testKit.shutdownTestKit();
    }

    /** Helper: creates a minimal valid Article instance */
    private Article article(String title, String description) {
        return new Article(
                title,
                "https://example.com",
                "SourceName",
                "https://source.com",
                "2025-01-01",
                5,   // kincaid grade
                70,  // reading score
                description
        );
    }

    /** Helper: creates a QueryResult with the given articles */
    private QueryResult qr(List<Article> articles) {
        return new QueryResult(
                "qr1",
                articles,
                0.0,
                0.0
        );
    }

    @Test
    public void testStatisticsActorComputesStatistics() {

        ActorRef<StatisticsActor.Command> actor =
                testKit.spawn(StatisticsActor.create());

        TestProbe<StatisticsActor.Response> probe =
                testKit.createTestProbe(StatisticsActor.Response.class);

        Article a1 = article("Apple Banana", "Fruit salad is good");
        Article a2 = article("Banana News", "Banana markets rising");

        QueryResult query = qr(List.of(a1, a2));

        actor.tell(new StatisticsActor.Compute(query, probe.ref()));

        StatisticsActor.Response response = probe.receiveMessage();

        assertNotNull(response);

        String result = response.resultString();
        Map<String, Long> counter = response.counter();

        assertNotNull(result);
        assertNotNull(counter);
        assertFalse(counter.isEmpty());

        // Basic semantic checks
        assertTrue(counter.containsKey("banana"));
        assertEquals(2L, (long) counter.get("banana"));
    }

    @Test
    public void testEmptyArticlesProducesEmptyStats() {

        ActorRef<StatisticsActor.Command> actor =
                testKit.spawn(StatisticsActor.create());

        TestProbe<StatisticsActor.Response> probe =
                testKit.createTestProbe(StatisticsActor.Response.class);

        QueryResult query = qr(List.of());

        actor.tell(new StatisticsActor.Compute(query, probe.ref()));

        StatisticsActor.Response response = probe.receiveMessage();

        assertNotNull(response);
        assertTrue(response.counter().isEmpty());
        assertTrue(response.resultString().isEmpty());
    }

    @Test
    public void testActorHandlesSingleWord() {

        ActorRef<StatisticsActor.Command> actor =
                testKit.spawn(StatisticsActor.create());

        TestProbe<StatisticsActor.Response> probe =
                testKit.createTestProbe(StatisticsActor.Response.class);

        Article article = article("Hello", "Hello");

        QueryResult query = qr(List.of(article));

        actor.tell(new StatisticsActor.Compute(query, probe.ref()));

        StatisticsActor.Response response = probe.receiveMessage();

        assertEquals(1L, (long) response.counter().get("hello"));
    }

    @Test
    public void testActorDoesNotCrashOnSpecialCharacters() {

        ActorRef<StatisticsActor.Command> actor =
                testKit.spawn(StatisticsActor.create());

        TestProbe<StatisticsActor.Response> probe =
                testKit.createTestProbe(StatisticsActor.Response.class);

        Article article = article("$$$ ### !!!", "@@@ ??? !!!");

        QueryResult query = qr(List.of(article));

        actor.tell(new StatisticsActor.Compute(query, probe.ref()));

        StatisticsActor.Response response = probe.receiveMessage();

        // Expect no meaningful words
        assertTrue(response.counter().isEmpty());
    }
}

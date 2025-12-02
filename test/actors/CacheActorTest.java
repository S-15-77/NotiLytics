package actors;

import models.Article;
import models.QueryResult;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CacheActorTest {

    private static ActorTestKit testKit;

    @BeforeClass
    public static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterClass
    public static void teardown() {
        testKit.shutdownTestKit();
    }

    /** Creates a minimal valid Article instance. */
    private Article sampleArticle(String id) {
        return new Article(
                "Title " + id,
                "https://example.com/" + id,
                "Source " + id,
                "https://source.com/" + id,
                "2024-01-01",
                7,          // kincaidGrade
                60,         // readingScore
                "Description " + id
        );
    }

    /** Creates a valid QueryResult instance */
    private QueryResult sampleQuery(String id) {
        return new QueryResult(
                id,
                List.of(sampleArticle(id)),
                12.5,       // readability
                87.3        // statistic score
        );
    }

    @Test
    public void testPutAndGet() {
        ActorRef<CacheActor.Command> cache =
                testKit.spawn(CacheActor.create());

        QueryResult qr = sampleQuery("test");

        TestProbe<CacheActor.Response> probe =
                testKit.createTestProbe(CacheActor.Response.class);

        cache.tell(new CacheActor.Put("key1", qr));
        cache.tell(new CacheActor.Get("key1", probe.ref()));

        assertEquals(qr, probe.receiveMessage().result());
    }

    @Test
    public void testMissingKeyReturnsNull() {
        ActorRef<CacheActor.Command> cache =
                testKit.spawn(CacheActor.create());

        TestProbe<CacheActor.Response> probe =
                testKit.createTestProbe(CacheActor.Response.class);

        cache.tell(new CacheActor.Get("missing", probe.ref()));

        assertNull(probe.receiveMessage().result());
    }

    @Test
    public void testMultipleEntries() {
        ActorRef<CacheActor.Command> cache =
                testKit.spawn(CacheActor.create());

        QueryResult q1 = sampleQuery("1");
        QueryResult q2 = sampleQuery("2");

        cache.tell(new CacheActor.Put("k1", q1));
        cache.tell(new CacheActor.Put("k2", q2));

        TestProbe<CacheActor.Response> p1 =
                testKit.createTestProbe(CacheActor.Response.class);
        TestProbe<CacheActor.Response> p2 =
                testKit.createTestProbe(CacheActor.Response.class);

        cache.tell(new CacheActor.Get("k1", p1.ref()));
        cache.tell(new CacheActor.Get("k2", p2.ref()));

        assertEquals(q1, p1.receiveMessage().result());
        assertEquals(q2, p2.receiveMessage().result());
    }

    @Test
    public void testOverwrite() {
        ActorRef<CacheActor.Command> cache =
                testKit.spawn(CacheActor.create());

        QueryResult first = sampleQuery("first");
        QueryResult second = sampleQuery("second");

        TestProbe<CacheActor.Response> probe =
                testKit.createTestProbe(CacheActor.Response.class);

        cache.tell(new CacheActor.Put("x", first));
        cache.tell(new CacheActor.Put("x", second));

        cache.tell(new CacheActor.Get("x", probe.ref()));

        assertEquals(second, probe.receiveMessage().result());
    }
}

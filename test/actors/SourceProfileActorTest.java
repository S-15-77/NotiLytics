package actors;

import Services.Client;
import models.Article;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.stream.Materializer;
import org.junit.*;
import play.mvc.Result;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * A class that tests the core functionalities of the {@link SourceProfileActor} class.
 * @author Haytham
 */
public class SourceProfileActorTest {
    private static ActorTestKit testKit;

    @Before
    public void setup() {
        testKit = ActorTestKit.create();
    }

    @After
    public void teardown() {
        testKit.shutdownTestKit();
    }

    /**
     * Mocked Client class that permits custom behaviors.
     * @author Haytham
     */
    static class FakeClient extends Client {
        private final List<Article> articles;
        public FakeClient(List<Article> articles) {
            super(null);
            this.articles = articles;
        }

        @Override
        public CompletableFuture<List<Article>> clientRequest(String url) {
            return CompletableFuture.completedFuture(articles);
        }
    }

    /**
     * Verifies that {@link SourceProfileActor}:
     * returns articles when called with a source possessing articles to return.
     * @author Haytham
     */
    @Test
    public void testProfileRequest_returnsArticles() throws Exception {
        Article article = new Article("T1", "u1", "BBC", "https://bbc.com",
                "2025-01-01, 12:00:00", 5, 5, "d1");
        List<Article> articles = List.of(article);

        SourceProfileActor.ClientFactory factory = ws -> new FakeClient(articles);

        Behavior<SourceProfileActor.Command> behavior =
                SourceProfileActor.create("https://test.url/", "testKey", null, factory);

        ActorRef<SourceProfileActor.Command> actorRef = testKit.spawn(behavior, "withArticles");
        TestProbe<Result> probe = testKit.createTestProbe();

        actorRef.tell(new SourceProfileActor.ProfileRequest("BBC", null, probe.getRef()));

        Result result = probe.receiveMessage();

        assertEquals(200, result.status());
        String body = result.body().consumeData(Materializer.apply(testKit.system())).toCompletableFuture().get().utf8String();
        assertTrue(body.contains("Listing Articles from BBC"));
        assertTrue(body.contains("https://bbc.com"));
    }

    /**
     * Verifies that {@link SourceProfileActor}:
     * returns the proper warning when no articles are found.
     * @author Haytham
     */
    @Test
    public void testProfileRequest_noArticles() throws Exception {
        SourceProfileActor.ClientFactory factory = ws -> new FakeClient(List.of());

        Behavior<SourceProfileActor.Command> behavior =
                SourceProfileActor.create("https://test.url/", "testKey", null, factory);

        ActorRef<SourceProfileActor.Command> actorRef = testKit.spawn(behavior, "noArticles");
        TestProbe<Result> probe = testKit.createTestProbe();

        actorRef.tell(new SourceProfileActor.ProfileRequest("BBC", null, probe.getRef()));

        Result result = probe.receiveMessage();

        assertEquals(200, result.status());
        String body = result.body().consumeData(Materializer.apply(testKit.system())).toCompletableFuture().get().utf8String();
        assertTrue(body.contains("No Articles Found for this source"));
    }

    /**
     * Verifies that {@link SourceProfileActor}:
     * uses the ID to fetch when that value is not null.
     * @author Haytham
     */
    @Test
    public void testProfileRequest_withID() {
        final AtomicReference<String> capturedUrl = new AtomicReference<>();

        SourceProfileActor.ClientFactory factory = ws -> new Client(null) {
            @Override
            public CompletableFuture<List<Article>> clientRequest(String url) {
                capturedUrl.set(url);
                return CompletableFuture.completedFuture(List.of());
            }
        };

        Behavior<SourceProfileActor.Command> behavior =
                SourceProfileActor.create("https://test.url/", "testKey", null, factory);

        ActorRef<SourceProfileActor.Command> actorRef = testKit.spawn(behavior, "noArticlesWithID");
        TestProbe<Result> probe = testKit.createTestProbe();

        actorRef.tell(new SourceProfileActor.ProfileRequest("BBC", "bbc-news", probe.getRef()));

        Result result = probe.receiveMessage();

        assertEquals(200, result.status());

        String url = capturedUrl.get();
        assertNotNull(url);
        assertTrue(url.contains("sources=bbc-news"));

    }
}
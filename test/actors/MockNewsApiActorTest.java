package actors;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

// Mock NewsApiClient interface
interface NewsApiClient {
    List<String> fetchHeadlines(String query);
}

// Actor that uses DI for NewsApiClient
class NewsApiActor {
    public interface Command {}
    public static class Fetch implements Command {
        public final String query;
        public final ActorRef<Response> replyTo;
        public Fetch(String query, ActorRef<Response> replyTo) {
            this.query = query;
            this.replyTo = replyTo;
        }
    }
    public static class Response {
        public final List<String> headlines;
        public Response(List<String> headlines) { this.headlines = headlines; }
    }
    public static Behavior<Command> create(NewsApiClient client) {
        return org.apache.pekko.actor.typed.javadsl.Behaviors.receive(Command.class)
            .onMessage(Fetch.class, msg -> {
                List<String> headlines = client.fetchHeadlines(msg.query);
                msg.replyTo.tell(new Response(headlines));
                return org.apache.pekko.actor.typed.javadsl.Behaviors.same();
            })
            .build();
    }
}

public class MockNewsApiActorTest {
    private static ActorTestKit testKit;
    @BeforeClass
    public static void setup() { testKit = ActorTestKit.create(); }
    @AfterClass
    public static void teardown() { testKit.shutdownTestKit(); }

    @Test
    public void testNewsApiActor_withGuiceDI_andMockito() {
        // Use Guice to inject a mock NewsApiClient
        NewsApiClient mockClient = Mockito.mock(NewsApiClient.class);
        Mockito.when(mockClient.fetchHeadlines("climate")).thenReturn(Arrays.asList("Headline1", "Headline2"));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(NewsApiClient.class).toInstance(mockClient);
            }
        });
        NewsApiClient injectedClient = injector.getInstance(NewsApiClient.class);
        Behavior<NewsApiActor.Command> behavior = NewsApiActor.create(injectedClient);
        ActorRef<NewsApiActor.Command> actor = testKit.spawn(behavior);
        TestProbe<NewsApiActor.Response> probe = testKit.createTestProbe();
        actor.tell(new NewsApiActor.Fetch("climate", probe.getRef()));
        NewsApiActor.Response response = probe.receiveMessage();
        assertNotNull(response);
        assertEquals(2, response.headlines.size());
        assertEquals("Headline1", response.headlines.get(0));
    }
}


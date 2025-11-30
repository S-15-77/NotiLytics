package actors;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class SourcesActorTest {
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
    public void testNonePresent() {
        Behavior<SourcesActor.GetSources> behavior = SourcesActor.create();
        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(behavior);
        TestProbe<SourcesActor.Sources> probe = testKit.createTestProbe();

        Set<String> ids = new HashSet<>();
        ids.add("missing");

        actor.tell(new SourcesActor.GetSources(ids, probe.getRef()));

        SourcesActor.Sources reply = probe.receiveMessage();

        assertNotNull(reply);
        assertTrue(reply.sources.isEmpty());
    }

    @Test
    public void testEmptyRequest() {
        Behavior<SourcesActor.GetSources> behavior = SourcesActor.create();
        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(behavior);
        TestProbe<SourcesActor.Sources> probe = testKit.createTestProbe();

        actor.tell(new SourcesActor.GetSources(Collections.emptySet(), probe.getRef()));

        SourcesActor.Sources reply = probe.receiveMessage();

        assertNotNull(reply);
        assertTrue(reply.sources.isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void testNullSetThrows() {
        TestProbe<SourcesActor.Sources> probe = testKit.createTestProbe();
        new SourcesActor.GetSources(null, probe.getRef());
    }

    @Test(expected = NullPointerException.class)
    public void testNullReplyToThrows() {
        Set<String> ids = new HashSet<>();
        ids.add("a");
        new SourcesActor.GetSources(ids, null);
    }

    @Test
    public void testIgnoresInvalidID() {
        Behavior<SourcesActor.GetSources> behavior = SourcesActor.create();
        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(behavior);
        TestProbe<SourcesActor.Sources> probe = testKit.createTestProbe();

        Set<String> ids = new HashSet<>();
        ids.add("x");
        ids.add("y");

        actor.tell(new SourcesActor.GetSources(ids, probe.getRef()));

        SourcesActor.Sources reply = probe.receiveMessage();

        assertNotNull(reply);
        assertEquals(0, reply.sources.size());
    }

    @Test
    public void testIgnoresDuplicates() {
        Behavior<SourcesActor.GetSources> behavior = SourcesActor.create();
        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(behavior);
        TestProbe<SourcesActor.Sources> probe = testKit.createTestProbe();

        Set<String> ids = new HashSet<>();
        ids.add("a");
        ids.add("a");

        actor.tell(new SourcesActor.GetSources(ids, probe.getRef()));

        SourcesActor.Sources reply = probe.receiveMessage();

        assertNotNull(reply);
        assertTrue(reply.sources.isEmpty());
    }
}
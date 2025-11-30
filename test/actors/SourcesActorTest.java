//package actors;
//
//import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
//import org.apache.pekko.actor.typed.ActorRef;
//import org.junit.AfterClass;
//import org.junit.BeforeClass;
//import org.junit.Test;
//
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.Set;
//
//import static org.junit.Assert.*;
//
//public class SourcesActorTest {
//
//    private static final ActorTestKit testKit = ActorTestKit.create();
//
//    @BeforeClass
//    public static void setup() {}
//
//    @AfterClass
//    public static void teardown() {
//        testKit.shutdownTestKit();
//    }
//
//    @Test
//    public void testNonePresent() {
//        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(SourcesActor.create());
//
//        Set<String> ids = new HashSet<>();
//        ids.add("missing");
//
//        ActorRef<SourcesActor.Sources> probe =
//                testKit.<SourcesActor.Sources>createTestProbe().getRef();
//
//        actor.tell(new SourcesActor.GetSources(ids, probe));
//
//        SourcesActor.Sources reply =
//                testKit.<SourcesActor.Sources>createTestProbe().receiveMessage();
//
//        assertNotNull(reply);
//        assertTrue(reply.sources.isEmpty());
//    }
//
//    @Test
//    public void testEmptyRequest() {
//        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(SourcesActor.create());
//
//        ActorRef<SourcesActor.Sources> probe =
//                testKit.<SourcesActor.Sources>createTestProbe().getRef();
//
//        actor.tell(new SourcesActor.GetSources(Collections.emptySet(), probe));
//
//        SourcesActor.Sources reply =
//                testKit.<SourcesActor.Sources>createTestProbe().receiveMessage();
//
//        assertNotNull(reply);
//        assertTrue(reply.sources.isEmpty());
//    }
//
//    @Test
//    public void testNullSetThrows() {
//        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(SourcesActor.create());
//
//        ActorRef<SourcesActor.Sources> probe =
//                testKit.<SourcesActor.Sources>createTestProbe().getRef();
//
//        try {
//            actor.tell(new SourcesActor.GetSources(null, probe));
//            fail("Expected NullPointerException");
//        } catch (NullPointerException ignored) {}
//    }
//
//    @Test
//    public void testNullReplyToThrows() {
//        Set<String> ids = new HashSet<>();
//        ids.add("a");
//
//        try {
//            new SourcesActor.GetSources(ids, null);
//            fail("Expected NullPointerException");
//        } catch (NullPointerException ignored) {}
//    }
//
//    @Test
//    public void testIgnoresInvalidID() {
//        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(SourcesActor.create());
//
//        Set<String> ids = new HashSet<>();
//        ids.add("x");
//        ids.add("y");
//
//        ActorRef<SourcesActor.Sources> probe =
//                testKit.<SourcesActor.Sources>createTestProbe().getRef();
//
//        actor.tell(new SourcesActor.GetSources(ids, probe));
//
//        SourcesActor.Sources reply =
//                testKit.<SourcesActor.Sources>createTestProbe().receiveMessage();
//
//        assertNotNull(reply);
//        assertEquals(0, reply.sources.size());
//    }
//
//    @Test
//    public void testIgnoresDuplicates() {
//        ActorRef<SourcesActor.GetSources> actor = testKit.spawn(SourcesActor.create());
//
//        Set<String> ids = new HashSet<>();
//        ids.add("a");
//        ids.add("a");
//
//        ActorRef<SourcesActor.Sources> probe =
//                testKit.<SourcesActor.Sources>createTestProbe().getRef();
//
//        actor.tell(new SourcesActor.GetSources(ids, probe));
//
//        SourcesActor.Sources reply =
//                testKit.<SourcesActor.Sources>createTestProbe().receiveMessage();
//
//        assertNotNull(reply);
//        assertTrue(reply.sources.isEmpty());
//    }
//}

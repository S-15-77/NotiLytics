package actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.stream.javadsl.Flow;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.libs.ws.WSClient;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UserParentActorTest {
    private static ActorTestKit testKit;
    private static Config testConfig;

    @BeforeClass
    public static void setup() {
        testKit = ActorTestKit.create();

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("newsapi.key", "test-api-key");
        configMap.put("newsapi.url", "https://newsapi.org/v2/everything?");
        testConfig = ConfigFactory.parseMap(configMap);
    }

    @AfterClass
    public static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    public void testCreate_spawnsChildActor() {
        WSClient mockWsClient = mock(WSClient.class);
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());

        UserActor.Factory factory = id -> UserActor.create(
                id,
                sourcesActor,
                readabilityActor,
                mockWsClient,
                testConfig
        );

        Behavior<UserParentActor.Create> behavior = UserParentActor.create(factory, testConfig);
        ActorRef<UserParentActor.Create> actor = testKit.spawn(behavior);
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe = testKit.createTestProbe();

        actor.tell(new UserParentActor.Create("test-user-1", probe.getRef()));

        Flow<JsonNode, JsonNode, NotUsed> flow = probe.receiveMessage();
        assertNotNull(flow);
    }

    @Test
    public void testCreate_multipleUsers() {
        WSClient mockWsClient = mock(WSClient.class);
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());

        UserActor.Factory factory = id -> UserActor.create(
                id,
                sourcesActor,
                readabilityActor,
                mockWsClient,
                testConfig
        );

        Behavior<UserParentActor.Create> behavior = UserParentActor.create(factory, testConfig);
        ActorRef<UserParentActor.Create> actor = testKit.spawn(behavior);

        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe1 = testKit.createTestProbe();
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe2 = testKit.createTestProbe();

        actor.tell(new UserParentActor.Create("user-1", probe1.getRef()));
        actor.tell(new UserParentActor.Create("user-2", probe2.getRef()));

        Flow<JsonNode, JsonNode, NotUsed> flow1 = probe1.receiveMessage();
        Flow<JsonNode, JsonNode, NotUsed> flow2 = probe2.receiveMessage();

        assertNotNull(flow1);
        assertNotNull(flow2);
    }

    @Test
    public void testCreate_sameUserIdTwice() {
        WSClient mockWsClient = mock(WSClient.class);
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());

        UserActor.Factory factory = id -> UserActor.create(
                id,
                sourcesActor,
                readabilityActor,
                mockWsClient,
                testConfig
        );

        Behavior<UserParentActor.Create> behavior = UserParentActor.create(factory, testConfig);
        ActorRef<UserParentActor.Create> actor = testKit.spawn(behavior);

        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe1 = testKit.createTestProbe();
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe2 = testKit.createTestProbe();

        actor.tell(new UserParentActor.Create("same-user", probe1.getRef()));
        Flow<JsonNode, JsonNode, NotUsed> flow1 = probe1.receiveMessage();
        assertNotNull(flow1);

        //The second create should still work
        actor.tell(new UserParentActor.Create("same-user", probe2.getRef()));
        //But there's a change we get an exception here if it is considered a duplicate child
    }

    @Test
    public void testCreate_emptyUserId() {
        WSClient mockWsClient = mock(WSClient.class);
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());

        UserActor.Factory factory = id -> UserActor.create(
                id,
                sourcesActor,
                readabilityActor,
                mockWsClient,
                testConfig
        );

        Behavior<UserParentActor.Create> behavior = UserParentActor.create(factory, testConfig);
        ActorRef<UserParentActor.Create> actor = testKit.spawn(behavior);
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe = testKit.createTestProbe();

        actor.tell(new UserParentActor.Create("", probe.getRef()));

        Flow<JsonNode, JsonNode, NotUsed> flow = probe.receiveMessage();
        assertNotNull(flow);
    }

    @Test
    public void testCreate_specialCharactersInId() {
        WSClient mockWsClient = mock(WSClient.class);
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());

        UserActor.Factory factory = id -> UserActor.create(
                id,
                sourcesActor,
                readabilityActor,
                mockWsClient,
                testConfig
        );

        Behavior<UserParentActor.Create> behavior = UserParentActor.create(factory, testConfig);
        ActorRef<UserParentActor.Create> actor = testKit.spawn(behavior);
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe = testKit.createTestProbe();

        actor.tell(new UserParentActor.Create("user-with-dashes", probe.getRef()));

        Flow<JsonNode, JsonNode, NotUsed> flow = probe.receiveMessage();
        assertNotNull(flow);
    }

    @Test
    public void testGetFlow_messageCreation() {
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe = testKit.createTestProbe();
        UserParentActor.GetFlow getFlow = new UserParentActor.GetFlow(probe.getRef());

        assertNotNull(getFlow);
        assertNotNull(getFlow.replyTo);
        assertEquals(probe.getRef(), getFlow.replyTo);
    }

    @Test
    public void testCreate_messageCreation() {
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe = testKit.createTestProbe();
        UserParentActor.Create create = new UserParentActor.Create("test-id", probe.getRef());

        assertNotNull(create);
        assertEquals("test-id", create.id);
        assertNotNull(create.replyTo);
        assertEquals(probe.getRef(), create.replyTo);
    }

    @Test
    public void testCreate_longUserId() {
        WSClient mockWsClient = mock(WSClient.class);
        ActorRef<SourcesActor.GetSources> sourcesActor = testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor = testKit.spawn(ReadabilityActor.create());

        UserActor.Factory factory = id -> UserActor.create(
                id,
                sourcesActor,
                readabilityActor,
                mockWsClient,
                testConfig
        );

        Behavior<UserParentActor.Create> behavior = UserParentActor.create(factory, testConfig);
        ActorRef<UserParentActor.Create> actor = testKit.spawn(behavior);
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe = testKit.createTestProbe();

        String longId = "a".repeat(100);
        actor.tell(new UserParentActor.Create(longId, probe.getRef()));

        Flow<JsonNode, JsonNode, NotUsed> flow = probe.receiveMessage();
        assertNotNull(flow);
    }

    @Test
    public void testCreate_parentStaysActive() {
        WSClient mockWsClient = mock(WSClient.class);
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());

        UserActor.Factory factory = id -> UserActor.create(
                id,
                sourcesActor,
                readabilityActor,
                mockWsClient,
                testConfig
        );

        Behavior<UserParentActor.Create> behavior = UserParentActor.create(factory, testConfig);
        ActorRef<UserParentActor.Create> actor = testKit.spawn(behavior);

        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe1 = testKit.createTestProbe();
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe2 = testKit.createTestProbe();
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe3 = testKit.createTestProbe();

        actor.tell(new UserParentActor.Create("user-1", probe1.getRef()));
        actor.tell(new UserParentActor.Create("user-2", probe2.getRef()));
        actor.tell(new UserParentActor.Create("user-3", probe3.getRef()));

        assertNotNull(probe1.receiveMessage());
        assertNotNull(probe2.receiveMessage());
        assertNotNull(probe3.receiveMessage());
    }
}
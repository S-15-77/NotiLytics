package actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class UserActorTest {
    private static ActorTestKit testKit;
    private static WSClient mockWsClient;
    private static Config testConfig;

    @BeforeClass
    public static void setup() {
        testKit = ActorTestKit.create();
        mockWsClient = mock(WSClient.class);

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
    public void testActorCreation() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Behavior<UserActor.Message> behavior = UserActor.create(
                "test-user-1",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        assertNotNull(actor);
    }

    @Test
    public void testGetFlowMessage() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Behavior<UserActor.Message> behavior = UserActor.create(
                "test-user-2",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe = testKit.createTestProbe();

        actor.tell(new UserParentActor.GetFlow(probe.getRef()));

        Flow<JsonNode, JsonNode, NotUsed> flow = probe.receiveMessage();
        assertNotNull(flow);
    }

    @Test
    public void testMultipleUserActors() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Behavior<UserActor.Message> behavior1 = UserActor.create(
                "user-1",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        Behavior<UserActor.Message> behavior2 = UserActor.create(
                "user-2",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor1 = testKit.spawn(behavior1);
        ActorRef<UserActor.Message> actor2 = testKit.spawn(behavior2);

        assertNotNull(actor1);
        assertNotNull(actor2);
        assertNotEquals(actor1, actor2);
    }

    @Test
    public void testActorWithDifferentConfigs() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("newsapi.key", "different-key");
        configMap.put("newsapi.url", "https://different-url.com/");
        Config differentConfig = ConfigFactory.parseMap(configMap);

        Behavior<UserActor.Message> behavior = UserActor.create(
                "test-user-3",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                differentConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        assertNotNull(actor);
    }

    @Test
    public void testGetFlowReturnsValidFlow() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Behavior<UserActor.Message> behavior = UserActor.create(
                "test-user-4",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe = testKit.createTestProbe();

        actor.tell(new UserParentActor.GetFlow(probe.getRef()));

        Flow<JsonNode, JsonNode, NotUsed> flow = probe.receiveMessage();
        assertNotNull(flow);

        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe2 = testKit.createTestProbe();
        actor.tell(new UserParentActor.GetFlow(probe2.getRef()));
        Flow<JsonNode, JsonNode, NotUsed> flow2 = probe2.receiveMessage();
        assertNotNull(flow2);
    }

    @Test
    public void testActorWithEmptyUserId() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Behavior<UserActor.Message> behavior = UserActor.create(
                "",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        assertNotNull(actor);
    }

    @Test
    public void testActorWithNullUserId() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Behavior<UserActor.Message> behavior = UserActor.create(
                null,
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        assertNotNull(actor);
    }

    @Test
    public void testActorBehaviorStaysActive() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Behavior<UserActor.Message> behavior = UserActor.create(
                "test-user-5",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe1 = testKit.createTestProbe();
        TestProbe<Flow<JsonNode, JsonNode, NotUsed>> probe2 = testKit.createTestProbe();

        actor.tell(new UserParentActor.GetFlow(probe1.getRef()));
        Flow<JsonNode, JsonNode, NotUsed> flow1 = probe1.receiveMessage();
        assertNotNull(flow1);

        actor.tell(new UserParentActor.GetFlow(probe2.getRef()));
        Flow<JsonNode, JsonNode, NotUsed> flow2 = probe2.receiveMessage();
        assertNotNull(flow2);
    }

    @Test
    public void testMinimalConfig() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Map<String, Object> minimalConfig = new HashMap<>();
        minimalConfig.put("newsapi.key", "key");
        minimalConfig.put("newsapi.url", "url");
        Config config = ConfigFactory.parseMap(minimalConfig);

        Behavior<UserActor.Message> behavior = UserActor.create(
                "test-user-6",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                config
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        assertNotNull(actor);
    }

    @Test
    public void testLongUserId() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        String longId = "a".repeat(1000);

        Behavior<UserActor.Message> behavior = UserActor.create(
                longId,
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        assertNotNull(actor);
    }

    @Test
    public void testSpecialCharactersInUserId() {
        ActorRef<SourcesActor.GetSources> sourcesActor =
                testKit.spawn(SourcesActor.create());
        ActorRef<ReadabilityActor.Command> readabilityActor =
                testKit.spawn(ReadabilityActor.create());
        ActorRef<CacheActor.Command> mockCacheActor =
                testKit.spawn(CacheActor.create());

        Behavior<UserActor.Message> behavior = UserActor.create(
                "user@#$%^&*()_+-=[]{}|;':\",./<>?",
                sourcesActor,
                readabilityActor,
                mockCacheActor,
                mockWsClient,
                testConfig
        );

        ActorRef<UserActor.Message> actor = testKit.spawn(behavior);
        assertNotNull(actor);
    }
}
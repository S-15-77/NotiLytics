package actors;

import models.ReadabilityCalculator;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ReadabilityActorTest {
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
    public void testCompute_typicalList() {
        List<String> descriptions = Arrays.asList(
                "This is a simple sentence.",
                "Another one!",
                "",
                null,
                "Short."
        );
        ReadabilityActor.Result result = ReadabilityActor.compute(descriptions);
        assertNotNull(result);
        assertEquals(3, result.grades.size()); // Only non-empty, non-null
        assertEquals(3, result.scores.size());
        assertTrue(result.averageGrade >= 0);
        assertTrue(result.averageScore >= 0);
    }

    @Test
    public void testCompute_emptyList() {
        List<String> descriptions = Collections.emptyList();
        ReadabilityActor.Result result = ReadabilityActor.compute(descriptions);
        assertNotNull(result);
        assertEquals(0, result.grades.size());
        assertEquals(0, result.scores.size());
        assertEquals(0.0, result.averageGrade, 0.0001);
        assertEquals(0.0, result.averageScore, 0.0001);
    }

    @Test
    public void testCompute_allNullOrEmpty() {
        List<String> descriptions = Arrays.asList("", null, "   ");
        ReadabilityActor.Result result = ReadabilityActor.compute(descriptions);
        assertNotNull(result);
        assertEquals(0, result.grades.size());
        assertEquals(0, result.scores.size());
        assertEquals(0.0, result.averageGrade, 0.0001);
        assertEquals(0.0, result.averageScore, 0.0001);
    }

    @Test(expected = NullPointerException.class)
    public void testCompute_nullInputThrows() {
        ReadabilityActor.compute(null);
    }

    @Test
    public void testCompute_limitsTo50() {
        List<String> descriptions = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            descriptions.add("Sentence " + i);
        }
        ReadabilityActor.Result result = ReadabilityActor.compute(descriptions);
        assertEquals(50, result.grades.size());
        assertEquals(50, result.scores.size());
    }

    @Test
    public void testActorBehavior_calculateMessage() {
        Behavior<ReadabilityActor.Command> behavior = ReadabilityActor.create();
        ActorRef<ReadabilityActor.Command> actor = testKit.spawn(behavior);
        TestProbe<ReadabilityActor.Result> probe = testKit.createTestProbe();
        List<String> descriptions = Arrays.asList("Test sentence.", "Another.");
        actor.tell(new ReadabilityActor.Calculate(descriptions, probe.getRef()));
        ReadabilityActor.Result result = probe.receiveMessage();
        assertNotNull(result);
        assertEquals(2, result.grades.size());
        assertEquals(2, result.scores.size());
    }

    @Test
    public void testDefaultConstructorCoverage() {
        new ReadabilityActor();
    }
}

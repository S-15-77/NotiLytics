package actors;

import org.junit.Test;
import models.ReadabilityCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for ReadabilityActor.compute()
 * @author Santhosh
 */
public class ReadabilityActorTest {

    @Test
    public void testCompute_Simple() {
        String text = "This is a simple sentence.";
        List<String> input = Collections.singletonList(text);

        ReadabilityActor.Result result = ReadabilityActor.compute(input);

        assertNotNull(result);
        assertEquals(1, result.grades.size());
        assertEquals(1, result.scores.size());

        double expectedGrade = ReadabilityCalculator.calculateFleschKincaidGrade(text);
        double expectedScore = ReadabilityCalculator.calculateFleschReadingScore(text);

        assertEquals(expectedGrade, result.grades.get(0), 0.0001);
        assertEquals(expectedScore, result.scores.get(0), 0.0001);
        assertEquals(expectedGrade, result.averageGrade, 0.0001);
        assertEquals(expectedScore, result.averageScore, 0.0001);
    }

    @Test
    public void testCompute_MultipleAndLimit() {
        List<String> input = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            input.add("Sentence number " + i + ".");
        }
        ReadabilityActor.Result result = ReadabilityActor.compute(input);
        // limited to 50
        assertEquals(50, result.grades.size());
        assertEquals(50, result.scores.size());
        // averages computed by ReadabilityCalculator should match
        List<String> limited = input.subList(0, 50);
        double expectedAvgGrade = ReadabilityCalculator.averageGrade(limited);
        double expectedAvgScore = ReadabilityCalculator.averageScore(limited);
        assertEquals(expectedAvgGrade, result.averageGrade, 0.0001);
        assertEquals(expectedAvgScore, result.averageScore, 0.0001);
    }

    @Test
    public void testCompute_EmptyAndNullEntries() {
        List<String> input = new ArrayList<>();
        input.add("");
        input.add(null);
        input.add("   ");
        ReadabilityActor.Result result = ReadabilityActor.compute(input);
        // all entries filtered out -> sizes 0 and averages 0
        assertEquals(0, result.grades.size());
        assertEquals(0, result.scores.size());
        assertEquals(0.0, result.averageGrade, 0.0001);
        assertEquals(0.0, result.averageScore, 0.0001);
    }

    @Test(expected = NullPointerException.class)
    public void testCompute_NullInputThrows() {
        ReadabilityActor.compute(null);
    }
}


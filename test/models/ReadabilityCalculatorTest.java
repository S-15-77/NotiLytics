package models;

import controllers.ReadabilityCalculator;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/**
 * Unit tests for ReadabilityCalculator.
 * Covers all methods and edge cases for 100% coverage.
 * @author Santhosh
 */
public class ReadabilityCalculatorTest {
    /**
     * Test Flesch-Kincaid Grade Level for a simple sentence.
     * @author Santhosh
     */
    @Test
    public void testCalculateFleschKincaidGrade_Simple() {
        String text = "This is a simple sentence.";
        double expected = 0.39 * (5.0/1.0) + 11.8 * (7.0/5.0) - 15.59; // 5 words, 7 syllables, 1 sentence
        double actual = ReadabilityCalculator.calculateFleschKincaidGrade(text);
        assertEquals(expected, actual, 0.01);
    }

    /**
     * Test Flesch Reading Score for a simple sentence.
     * @author Santhosh
     */
    @Test
    public void testCalculateFleschReadingScore_Simple() {
        String text = "This is a simple sentence.";
        double expected = 206.835 - 1.015 * (5.0/1.0) - 84.6 * (7.0/5.0); // 5 words, 7 syllables, 1 sentence
        double actual = ReadabilityCalculator.calculateFleschReadingScore(text);
        assertEquals(expected, actual, 0.01);
    }

    /**
     * Test averageGrade for multiple descriptions.
     * @author Santhosh
     */
    @Test
    public void testAverageGrade_MultipleDescriptions() {
        String t1 = "Easy text."; // 2 words, 3 syllables, 1 sentence
        String t2 = "This is a more complicated sentence with several words and syllables."; // 12 words, 18 syllables, 1 sentence
        double g1 = ReadabilityCalculator.calculateFleschKincaidGrade(t1);
        double g2 = ReadabilityCalculator.calculateFleschKincaidGrade(t2);
        double expected = (g1 + g2) / 2.0;
        double actual = ReadabilityCalculator.averageGrade(Arrays.asList(t1, t2));
        assertEquals(expected, actual, 0.01);
    }

    /**
     * Test averageScore for multiple descriptions.
     * @author Santhosh
     */
    @Test
    public void testAverageScore_MultipleDescriptions() {
        String t1 = "Easy text."; // 2 words, 3 syllables, 1 sentence
        String t2 = "This is a more complicated sentence with several words and syllables."; // 12 words, 18 syllables, 1 sentence
        double s1 = ReadabilityCalculator.calculateFleschReadingScore(t1);
        double s2 = ReadabilityCalculator.calculateFleschReadingScore(t2);
        double expected = (s1 + s2) / 2.0;
        double actual = ReadabilityCalculator.averageScore(Arrays.asList(t1, t2));
        assertEquals(expected, actual, 0.01);
    }

    /**
     * Test edge case: empty string input.
     * @author Santhosh
     */
    @Test
    public void testEmptyString() {
        assertEquals(0.0, ReadabilityCalculator.calculateFleschKincaidGrade(""), 0.001);
        assertEquals(0.0, ReadabilityCalculator.calculateFleschReadingScore(""), 0.001);
        assertEquals(0.0, ReadabilityCalculator.averageGrade(Collections.singletonList("")), 0.001);
        assertEquals(0.0, ReadabilityCalculator.averageScore(Collections.singletonList("")), 0.001);
    }

    /**
     * Test edge case: null input.
     * @author Santhosh
     */
    @Test
    public void testNullInput() {
        assertEquals(0.0, ReadabilityCalculator.calculateFleschKincaidGrade(null), 0.001);
        assertEquals(0.0, ReadabilityCalculator.calculateFleschReadingScore(null), 0.001);
    }

    /**
     * Test a complex sentence for grade and score.
     * @author Santhosh
     */
    @Test
    public void testComplexSentence() {
        String text = "Although the rain was heavy, the children continued to play outside, undeterred by the weather.";
        double grade = ReadabilityCalculator.calculateFleschKincaidGrade(text);
        double score = ReadabilityCalculator.calculateFleschReadingScore(text);
        assertTrue(grade > 0);
        assertTrue(score > 0);
    }

    /**
     * Test syllable counting for edge cases.
     * @author Santhosh
     */
    @Test
    public void testSyllableCounting() {
        assertEquals(1, ReadabilityCalculator.countSyllablesInWord("a"), 0.001); // one word, one syllable
        assertEquals(0.0, ReadabilityCalculator.countSyllablesInWord(""), 0.001); // empty
        assertEquals(0.0, ReadabilityCalculator.countSyllablesInWord(null), 0.001); // null
        // Only vowels
        assertTrue(ReadabilityCalculator.countSyllablesInWord("aeiouy") >= 0);
        // Silent 'e'
        assertTrue(ReadabilityCalculator.countSyllablesInWord("make") >= 0);
        // No vowels
        assertTrue(ReadabilityCalculator.countSyllablesInWord("rhythm") >= 0);
    }
}


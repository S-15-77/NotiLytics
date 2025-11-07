package models;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link QueryResult}.
 * Covers constructor and all getters with multiple input shapes.
 * @author Santhosh
 */
public class QueryResultTest {

    /**
     * Verifies that a typical, non-empty construction preserves the query string,
     * stores the exact list reference passed to the constructor, and returns the
     * expected average grade and score values.
     * @author Santhosh
     */
    @Test
    public void testConstructorAndGetters_WithTypicalValues() {
        List<Article> articles = new ArrayList<>();
        articles.add(new Article(
                "Title A", "https://example.com/a",
                "Source A", "https://sourcea.com",
                "2025-11-04, 12:00:00",
                8, 65, "Desc A"
        ));
        articles.add(new Article(
                "Title B", "https://example.com/b",
                "Source B", "https://sourceb.com",
                "2025-11-04, 13:00:00",
                9, 60, "Desc B"
        ));

        QueryResult qr = new QueryResult("climate change", articles, 8.5, 62.5);

        assertEquals("climate change", qr.getQuery());
        assertSame("Articles list reference should be the same instance passed in",
                articles, qr.getArticles());
        assertEquals(8.5, qr.getAvgGrade(), 0.0001);
        assertEquals(62.5, qr.getAvgScore(), 0.0001);
    }

    /**
     * Verifies behavior when the articles list is empty: the query is preserved,
     * the empty list reference is retained (not copied), and averages are exactly 0.0.
     * @author Santhosh
     */
    @Test
    public void testConstructorAndGetters_WithEmptyArticles() {
        List<Article> empty = Collections.emptyList();
        QueryResult qr = new QueryResult("empty", empty, 0.0, 0.0);

        assertEquals("empty", qr.getQuery());
        assertSame(empty, qr.getArticles());
        assertTrue(qr.getArticles().isEmpty());
        assertEquals(0.0, qr.getAvgGrade(), 0.0);
        assertEquals(0.0, qr.getAvgScore(), 0.0);
    }

    /**
     * Documents and checks that the constructor does not defensively copy the
     * articles list. Mutating the original list after construction must be
     * reflected by {@code getArticles()}, confirming shared reference semantics.
     * <p>Note: This test encodes the current contract. If defensive copying is
     * introduced later, update both code and test accordingly.</p>
     * @author Santhosh
     */
    @Test
    public void testArticlesReferenceIsNotCopied() {
        // This test documents current behavior: the list is stored as-is (no defensive copy).
        List<Article> articles = new ArrayList<>();
        QueryResult qr = new QueryResult("mutable", articles, 7.0, 55.0);

        assertSame(articles, qr.getArticles());

        // Mutating the original list reflects in the stored reference.
        articles.add(new Article(
                "Now Added", "https://example.com/x",
                "Src", "https://src.com",
                "2025-11-04, 14:00:00",
                7, 55, "Desc"
        ));
        assertEquals(1, qr.getArticles().size());
    }

    /**
     * Ensures calculateFleschKincaidGrade() returns 0.0 when no sentences are detected
     *  A string of only punctuation is split away by the
     * sentence pattern, yielding zero sentences.
     * @author Santhosh
     */
    @Test
    public void testGrade_NoSentences_ReturnsZero() {
        String text = "!!!...???";
        assertEquals(0.0, ReadabilityCalculator.calculateFleschKincaidGrade(text), 0.0);
    }

    /**
     * Ensures calculateFleschReadingScore() returns 0.0 when no sentences are detected
     * (line 56 guard: sentences == 0). Again use only punctuation so the split produces zero sentences.
     * @author Santhosh
     */
    @Test
    public void testScore_NoSentences_ReturnsZero() {
        String text = "?!..!!";
        assertEquals(0.0, ReadabilityCalculator.calculateFleschReadingScore(text), 0.0);
    }

    /**
     * Covers the branch at the start of countSyllablesInWord  after stripping
     * non-letters, an empty word should return 0 syllables (e.g., "1234" or symbols only).
     * @author Santhosh
     */
    @Test
    public void testCountSyllablesInWord_OnlyNonLetters_ReturnsZero() {
        assertEquals(0, ReadabilityCalculator.countSyllablesInWord("1234"));
        assertEquals(0, ReadabilityCalculator.countSyllablesInWord("$$$"));
    }

    /**
     * Covers the silent 'e' decrement branch  where the word ends with 'e'
     * but NOT "consonant + le". For "make", the naive count is 2, then the trailing 'e' is
     * removed → result should be 1.
     * @author Santhosh
     */
    @Test
    public void testCountSyllablesInWord_SilentE_Decrements() {
        assertEquals(1, ReadabilityCalculator.countSyllablesInWord("make"));
        assertEquals(1, ReadabilityCalculator.countSyllablesInWord("cake"));
    }

    /**
     * Covers the "consonant + le" exception branch words ending in "le"
     * preceded by a consonant should NOT decrement for the trailing 'e'. For "table", the
     * expected count is 2, not 1.
     * @author Santhosh
     */
    @Test
    public void testCountSyllablesInWord_ConsonantPlusLe_NotDecremented() {
        assertEquals(2, ReadabilityCalculator.countSyllablesInWord("table"));
        assertEquals(2, ReadabilityCalculator.countSyllablesInWord("bottle"));
    }
    /**
     * Extra guard coverage: a sentence delimiter plus digits yields one "sentence" but zero words,
     * so score should be 0.0 due to words == 0.
     * @author Santhosh
     */
    @Test
    public void testScore_NoWords_ReturnsZero() {
        String text = "12345!!!";
        assertEquals(0.0, ReadabilityCalculator.calculateFleschReadingScore(text), 0.0);
    }

    /**
     * Verifies the early-return guard cases and a normal computation case for grade.
     * Cases:
     * - No sentences
     * - No valid words
     * - Regular sentence (non-zero result)
     * @author Santhosh
     */
    @Test
    public void testGrade_GuardsAndNormalCase() {
        assertEquals(0.0, ReadabilityCalculator.calculateFleschKincaidGrade("!!!"), 0.0);   // no sentences
        assertEquals(0.0, ReadabilityCalculator.calculateFleschKincaidGrade("123."), 0.0);  // no words
        assertNotEquals(0.0, ReadabilityCalculator.calculateFleschKincaidGrade("This is a sentence."), 0.0); // normal
    }

    /**
     * Verifies syllable counting around silent 'e' and consonant+'le' handling,
     * plus the case where the count would not be decremented.
     * Cases:
     * - consonant+le (no decrement)
     * - vowel+le (decrement)
     * - ends with 'e' but not 'le' (decrement)
     * - single-letter 'e' (no decrement due to minimum)
     * @author Santhosh
     */
    @Test
    public void testCountSyllables_SilentEAndLeVariants() {
        assertEquals(2, ReadabilityCalculator.countSyllablesInWord("table")); // consonant+le → no decrement
        assertEquals(1, ReadabilityCalculator.countSyllablesInWord("ale"));   // vowel+le → decrement
        assertEquals(1, ReadabilityCalculator.countSyllablesInWord("make"));  // trailing 'e' (not 'le') → decrement
        assertEquals(1, ReadabilityCalculator.countSyllablesInWord("e"));     // minimum syllable enforcement
    }


}
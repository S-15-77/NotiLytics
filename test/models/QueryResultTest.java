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
}
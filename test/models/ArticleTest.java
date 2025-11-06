package models;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Article}.
 * Covers constructor and all getters across typical and edge inputs.
 * @author Santhosh
 */
public class ArticleTest {

    @Test
    public void testConstructorAndGetters_WithTypicalValues() {
        Article a = new Article(
                "Title A",
                "https://example.com/a",
                "Source A",
                "https://sourcea.com",
                "2025-11-04, 12:00:00",
                9,
                62,
                "Short description"
        );

        assertEquals("Title A", a.getTitle());
        assertEquals("https://example.com/a", a.getUrl());
        assertEquals("Source A", a.getSourceName());
        assertEquals("https://sourcea.com", a.getSourceUrl());
        assertEquals("2025-11-04, 12:00:00", a.getPublishedAt());
        assertEquals(9, a.getKincaidGrade());
        assertEquals(62, a.getReadingScore());
        assertEquals("Short description", a.getDescription());
    }

    @Test
    public void testConstructorAndGetters_WithEmptyStrings() {
        Article a = new Article(
                "",
                "",
                "",
                "",
                "",
                0,
                0,
                ""
        );

        assertEquals("", a.getTitle());
        assertEquals("", a.getUrl());
        assertEquals("", a.getSourceName());
        assertEquals("", a.getSourceUrl());
        assertEquals("", a.getPublishedAt());
        assertEquals(0, a.getKincaidGrade());
        assertEquals(0, a.getReadingScore());
        assertEquals("", a.getDescription());
    }

    @Test
    public void testConstructorAndGetters_WithNullables() {
        // If your code never passes nulls, you can skip this. But including it documents behavior.
        Article a = new Article(
                null,
                null,
                null,
                null,
                null,
                -1,
                -1,
                null
        );

        assertNull(a.getTitle());
        assertNull(a.getUrl());
        assertNull(a.getSourceName());
        assertNull(a.getSourceUrl());
        assertNull(a.getPublishedAt());
        assertEquals(-1, a.getKincaidGrade());
        assertEquals(-1, a.getReadingScore());
        assertNull(a.getDescription());
    }
}

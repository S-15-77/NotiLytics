package models;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the Source model.
 * @author Ilyes
 */
public class SourceTest {


    /**
     * Test Source constructor and all getters
     */
    @Test
    public void testSourceConstructorAndGetters() {
        Source source = new Source(
                "bbc-news",
                "BBC News",
                "British Broadcasting Corporation",
                "https://www.bbc.com",
                "general",
                "en",
                "gb"
        );

        assertEquals("bbc-news", source.getId());
        assertEquals("BBC News", source.getName());
        assertEquals("British Broadcasting Corporation", source.getDescription());
        assertEquals("https://www.bbc.com", source.getUrl());
        assertEquals("general", source.getCategory());
        assertEquals("en", source.getLanguage());
        assertEquals("gb", source.getCountry());
    }

    /**
     * Test Source with null values.
     */
    @Test
    public void testSourceWithNullValues() {
        Source source = new Source(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertNull(source.getId());
        assertNull(source.getName());
        assertNull(source.getDescription());
        assertNull(source.getUrl());
        assertNull(source.getCategory());
        assertNull(source.getLanguage());
        assertNull(source.getCountry());
    }

    /**
     * Test Source with different categories
     */
    @Test
    public void testSourceWithDifferentCategories() {
        String[] categories = {"business", "entertainment", "general", "health", "science", "sports", "technology"};

        for (String category : categories) {
            Source source = new Source(
                    "test-id",
                    "Test Source",
                    "Description",
                    "https://test.com",
                    category,
                    "en",
                    "us"
            );
            assertEquals(category, source.getCategory());
        }
    }

    /**
     * Test Source with different languages
     */
    @Test
    public void testSourceWithDifferentLanguages() {
        String[] languages = {"ar", "de", "en", "es", "fr", "he", "it", "nl", "no", "pt", "ru", "sv", "zh"};

        for (String language : languages) {
            Source source = new Source(
                    "test-id",
                    "Test Source",
                    "Description",
                    "https://test.com",
                    "general",
                    language,
                    "us"
            );
            assertEquals(language, source.getLanguage());
        }
    }

    /**
     * Test Source with different countries
     */
    @Test
    public void testSourceWithDifferentCountries() {
        String[] countries = {"us", "ca", "gb", "au", "de", "fr", "jp", "cn"};

        for (String country : countries) {
            Source source = new Source(
                    "test-id",
                    "Test Source",
                    "Description",
                    "https://test.com",
                    "general",
                    "en",
                    country
            );
            assertEquals(country, source.getCountry());
        }
    }
}
package models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Statistics class.
 */
public class StatisticsTest {

    private QueryResult sampleQuery;
    private Statistics stats;

    @BeforeEach
    public void setup() {
        //Create some articles
        Article a1 = new Article("Title One", "url1", "Source1", "sourceUrl1", "2025-01-01, 12:00:00", 5, 5, "Description One");
        Article a2 = new Article("Title Two", "url2", "Source2", "sourceUrl2", "2025-01-02, 12:00:00", 5, 5, "Description Two");
        Article a3 = new Article("Title Three", "url3", "Source3", "sourceUrl3", "2025-01-03, 12:00:00", 5, 5, "Description Three");

        sampleQuery = new QueryResult("TestQuery", Arrays.asList(a1, a2, a3), 5.0, 5.0);
        stats = new Statistics(sampleQuery);
    }

    @Test
    public void testGetTitles() {
        List<String> titles = stats.getTitles();
        assertEquals(3, titles.size());
        assertTrue(titles.contains("Title One"));
        assertTrue(titles.contains("Title Two"));
        assertTrue(titles.contains("Title Three"));
    }

    @Test
    public void testGetDescriptions() {
        List<String> descriptions = stats.getDescriptions();
        assertEquals(3, descriptions.size());
        assertTrue(descriptions.contains("Description One"));
        assertTrue(descriptions.contains("Description Two"));
        assertTrue(descriptions.contains("Description Three"));
    }

    @Test
    public void testGetWords() {
        List<String> sentences = Arrays.asList("Hello World", "Java Programming");
        List<String> words = Statistics.getWords(sentences);
        assertEquals(4, words.size());
        assertTrue(words.contains("hello"));
        assertTrue(words.contains("world"));
        assertTrue(words.contains("java"));
        assertTrue(words.contains("programming"));
    }

    @Test
    public void testFiltering() {
        List<String> words = Arrays.asList("a", "is", "hello", "world");
        List<String> filtered = Statistics.filtering(words);
        assertEquals(2, filtered.size());
        assertTrue(filtered.contains("hello"));
        assertTrue(filtered.contains("world"));
    }

    @Test
    public void testGetCounterAndGetString() {
        List<String> words = Arrays.asList("apple", "banana", "apple", "orange", "banana", "apple");
        Map<String, Long> counter = Statistics.getCounter(words);
        assertEquals(3, counter.get("apple"));
        assertEquals(2, counter.get("banana"));
        assertEquals(1, counter.get("orange"));

        String resultString = Statistics.getString(counter);
        assertTrue(resultString.startsWith("apple:3"));
    }
}

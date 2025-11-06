package Service;

import Services.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Article;
import models.Source;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

// Add this import so we don't hardcode the limit:
import controllers.HomeController;

public class ClientTest {

    private WSClient mockWs;
    private WSRequest mockRequest;
    private WSResponse mockResponse;
    private Client client;

    private final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        mockWs = Mockito.mock(WSClient.class);
        mockRequest = Mockito.mock(WSRequest.class);
        mockResponse = Mockito.mock(WSResponse.class);

        Mockito.when(mockWs.url(anyString())).thenReturn(mockRequest);
        Mockito.when(mockRequest.setRequestTimeout(any(Duration.class))).thenReturn(mockRequest);

        client = new Client(mockWs);
    }

    // ---------- helper builders ----------

    private ObjectNode makeArticleNode(String title, String url, String sourceName, String publishedAtIso, String description) {
        ObjectNode src = mapper.createObjectNode();
        src.put("name", sourceName);

        ObjectNode n = mapper.createObjectNode();
        n.put("title", title);
        n.put("url", url);
        n.set("source", src);
        n.put("publishedAt", publishedAtIso);
        n.put("description", description);
        return n;
    }

    private ObjectNode makeArticlesPayload(int count) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode arr = mapper.createArrayNode();
        for (int i = 0; i < count; i++) {
            String t = "Title " + i;
            String u = "https://example.com/a" + i;
            String s = "Source " + i;
            String published = Instant.parse("2025-01-01T00:00:00Z").toString(); // fixed UTC
            String desc = "Description " + i;
            arr.add(makeArticleNode(t, u, s, published, desc));
        }
        root.set("articles", arr);
        return root;
    }

    private ObjectNode makeSourcesPayload(int count) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode arr = mapper.createArrayNode();
        for (int i = 0; i < count; i++) {
            ObjectNode s = mapper.createObjectNode();
            s.put("id", "id" + i);
            s.put("name", "Name " + i);
            s.put("description", "Desc " + i);
            s.put("url", "https://src" + i + ".com");
            s.put("category", "category" + i);
            s.put("language", "en");
            s.put("country", "us");
            arr.add(s);
        }
        root.set("sources", arr);
        return root;
    }

    // ---------- tests for clientRequest ----------

    /**
     * Verifies: 200 OK parses articles and respects the HomeController.getMaxArticlesVisible() limit.
     */
    @Test
    public void testClientRequest_success_parsesAndLimits() {
        int requested = 200; // larger than controller limit
        ObjectNode payload = makeArticlesPayload(requested);

        Mockito.when(mockResponse.getStatus()).thenReturn(200);
        Mockito.when(mockResponse.asJson()).thenReturn(payload);
        Mockito.when(mockRequest.get()).thenReturn(CompletableFuture.completedFuture(mockResponse));

        List<Article> articles = client.clientRequest("https://fake/news").toCompletableFuture().join();

        assertNotNull(articles);
        assertEquals(HomeController.getMaxArticlesVisible(), articles.size()); // no hardcode
        Article a0 = articles.get(0);
        assertTrue(a0.getTitle().startsWith("Title "));
        assertTrue(a0.getUrl().startsWith("https://example.com/a"));
        assertTrue(a0.getSourceName().startsWith("Source "));
        assertTrue(a0.getPublishedAt().matches("\\d{4}-\\d{2}-\\d{2}, \\d{2}:\\d{2}:\\d{2}"));
        assertTrue(a0.getDescription().startsWith("Description "));
    }

    /**
     * Verifies the happy-path time conversion: valid ISO-8601 UTC -> formatted America/Toronto string.
     */
    @Test
    public void testClientRequest_validIsoUtc_formatsToEdt() {
        // Fixed instant to avoid flakiness (DST safe enough for unit test)
        String utc = "2025-03-09T06:30:00Z";
        ZonedDateTime expectedEdt = Instant.parse(utc).atZone(ZoneId.of("America/Toronto"));
        String expected = expectedEdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm:ss"));

        ObjectNode root = mapper.createObjectNode();
        ArrayNode arr = root.putArray("articles");
        arr.add(makeArticleNode(
                "T1",
                "https://example.com/a1",
                "MySource",
                utc,
                "desc"
        ));

        Mockito.when(mockResponse.getStatus()).thenReturn(200);
        Mockito.when(mockResponse.asJson()).thenReturn(root);
        Mockito.when(mockRequest.get()).thenReturn(CompletableFuture.completedFuture(mockResponse));

        List<Article> result = client.clientRequest("https://fake/news").toCompletableFuture().join();

        assertEquals(1, result.size());
        assertEquals(expected, result.get(0).getPublishedAt());
    }

    /**
     * Verifies: non-200 returns an empty list.
     */
    @Test
    public void testClientRequest_non200_returnsEmpty() {
        Mockito.when(mockResponse.getStatus()).thenReturn(429);
        Mockito.when(mockResponse.getStatusText()).thenReturn("Too Many Requests");
        Mockito.when(mockRequest.get()).thenReturn(CompletableFuture.completedFuture(mockResponse));

        List<Article> articles = client.clientRequest("https://fake/news").toCompletableFuture().join();
        assertNotNull(articles);
        assertTrue(articles.isEmpty());
    }

    /**
     * Verifies: missing or non-array "articles" returns empty list.
     */
    @Test
    public void testClientRequest_missingArticles_returnsEmpty() {
        ObjectNode payload = mapper.createObjectNode(); // no "articles" key
        Mockito.when(mockResponse.getStatus()).thenReturn(200);
        Mockito.when(mockResponse.asJson()).thenReturn(payload);
        Mockito.when(mockRequest.get()).thenReturn(CompletableFuture.completedFuture(mockResponse));

        List<Article> articles = client.clientRequest("https://fake/news").toCompletableFuture().join();
        assertNotNull(articles);
        assertTrue(articles.isEmpty());
    }

    /**
     * Verifies: invalid publishedAt triggers catch → "Unknown Date".
     */
    @Test
    public void testClientRequest_invalidPublishedAt_usesUnknownDate() {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode arr = root.putArray("articles");

        ObjectNode src = mapper.createObjectNode().put("name", "BadSource");
        ObjectNode art = mapper.createObjectNode();
        art.put("title", "Bad Date");
        art.put("url", "https://example.com/bad");
        art.set("source", src);
        art.put("publishedAt", "NOT-A-DATE"); // catch path
        art.put("description", "desc");
        arr.add(art);

        Mockito.when(mockResponse.getStatus()).thenReturn(200);
        Mockito.when(mockResponse.asJson()).thenReturn(root);
        Mockito.when(mockRequest.get()).thenReturn(CompletableFuture.completedFuture(mockResponse));

        List<Article> articles = client.clientRequest("https://fake/news").toCompletableFuture().join();
        assertEquals(1, articles.size());
        assertEquals("Unknown Date", articles.get(0).getPublishedAt());
    }

    // ---------- tests for fetchSources ----------

    /**
     * Verifies: fetchSources parses multiple sources.
     */
    @Test
    public void testFetchSources_success_parsesList() {
        ObjectNode payload = makeSourcesPayload(3);
        Mockito.when(mockResponse.asJson()).thenReturn(payload);
        Mockito.when(mockRequest.get()).thenReturn(CompletableFuture.completedFuture(mockResponse));

        List<Source> sources = client.fetchSources("https://fake/sources").toCompletableFuture().join();

        assertNotNull(sources);
        assertEquals(3, sources.size());
        assertEquals("id0", sources.get(0).getId());
        assertEquals("Name 1", sources.get(1).getName());
        assertEquals("https://src2.com", sources.get(2).getUrl());
    }

    /**
     * Verifies: fetchSources with missing/empty "sources" returns empty.
     */
    @Test
    public void testFetchSources_missingOrEmpty_returnsEmpty() {
        // Case 1: missing "sources"
        ObjectNode missing = mapper.createObjectNode();
        Mockito.when(mockResponse.asJson()).thenReturn(missing);
        Mockito.when(mockRequest.get()).thenReturn(CompletableFuture.completedFuture(mockResponse));

        List<Source> empty1 = client.fetchSources("https://fake/sources").toCompletableFuture().join();
        assertNotNull(empty1);
        assertTrue(empty1.isEmpty());

        // Case 2: empty array
        ObjectNode emptyArr = mapper.createObjectNode();
        emptyArr.set("sources", mapper.createArrayNode());
        Mockito.when(mockResponse.asJson()).thenReturn(emptyArr);

        List<Source> empty2 = client.fetchSources("https://fake/sources").toCompletableFuture().join();
        assertNotNull(empty2);
        assertTrue(empty2.isEmpty());
    }
}
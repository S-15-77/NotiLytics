package controllers;

import Services.Client;
import models.Article;
import models.QueryResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import play.libs.ws.*;
import play.mvc.Http;
import play.mvc.Result;
import com.typesafe.config.Config;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.*;


import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;


import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;




public class HomeControllerTest {

    private WSClient mockWs;
    private WSRequest mockRequest;
    private WSResponse mockResponse;
    private Config mockConfig;
    private Executor executor;
    private HomeController controller;

    @Before
    public void setup() {
        // --- Mock dependencies ---
        mockWs = Mockito.mock(WSClient.class);
        mockRequest = Mockito.mock(WSRequest.class);
        mockResponse = Mockito.mock(WSResponse.class);
        mockConfig = Mockito.mock(Config.class);
        executor = Executors.newSingleThreadExecutor();

        // --- Stub config values ---
        when(mockConfig.getString("newsapi.key")).thenReturn("dummyKey");
        when(mockConfig.getString("newsapi.url")).thenReturn("https://newsapi.org/v2/everything?");

        // --- Stub WSClient chain ---
        when(mockWs.url(anyString())).thenReturn(mockRequest);
        when(mockRequest.setRequestTimeout(Mockito.any(Duration.class))).thenReturn(mockRequest);

        // --- Mock WSResponse behavior ---
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.asJson()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .putArray("articles")  // empty array, safe dummy response
        );

        // --- Return a completed future when get() is called ---
        CompletableFuture<WSResponse> fakeFuture = CompletableFuture.completedFuture(mockResponse);
        when(mockRequest.get()).thenReturn(fakeFuture);

        // --- Instantiate controller ---
        controller = new HomeController(mockWs, executor, mockConfig);
    }

    /** Test that index() renders the welcome message correctly. */
    @Test
    public void testIndexRendersWelcomeMessage() {
        Http.Request fakeRequest = fakeRequest().build();
        Result result = controller.index(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        assertTrue(contentAsString(result).contains("Welcome to NotiLytics"));
    }

    /** Test that empty SearchInput renders the prompt message. */
    @Test
    public void testSearchWithEmptyInputReturnsPrompt() {
        Http.Request fakeRequest = fakeRequest().method(GET).uri("/search").build();
        Result result = controller.search(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        assertTrue(contentAsString(result).contains("Please enter a search term"));
    }

    /** Test that valid SearchInput produces a rendered response. */
    @Test
    public void testSearchWithValidInputUpdatesSession() {
        Http.Request fakeRequest = fakeRequest()
                .method(GET)
                .uri("/search?SearchInput=climate&sortBy=publishedAt")
                .build();

        Result result = controller.search(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("Search Results for"));
    }

    /**
     * Tests the source() method in HomeController
     * @author Ilyes
     */
    @Test
    public void testSource() {
        //No filters tested here
        Http.Request fakeRequest = fakeRequest()
                .method(GET)
                .uri("/sources")
                .build();

        Result result = controller.sources(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("News Sources"));

        //Country filter tested here
        fakeRequest = fakeRequest()
                .method(GET)
                .uri("/sources?country=us")
                .build();

        result = controller.sources(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        body = contentAsString(result);
        assertTrue(body.contains("News Sources"));

        //Category filter now
        fakeRequest = fakeRequest()
                .method(GET)
                .uri("/sources?category=technology")
                .build();

        result = controller.sources(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        body = contentAsString(result);
        assertTrue(body.contains("News Sources"));

        //Language filter
        fakeRequest = fakeRequest()
                .method(GET)
                .uri("/sources?language=fr")
                .build();

        result = controller.sources(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        body = contentAsString(result);
        assertTrue(body.contains("News Sources"));

        //We will simulate an error to see what happens here
        CompletableFuture<WSResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("API Error")); //CompletableFuture does not fulfill promise here
        when(mockRequest.get()).thenReturn(failedFuture); //Mockito mocks a failed return

        fakeRequest = fakeRequest() //fake http request
                .method(GET)
                .uri("/sources")
                .build();

        result = controller.sources(fakeRequest).toCompletableFuture().join();

        assertEquals(INTERNAL_SERVER_ERROR, result.status());
        body = contentAsString(result);
        assertTrue(body.contains("Error fetching sources"));
    }
    @Test
    public void testStat() {
        String key = "testKey";

        // Create dummy articles
        List<Article> dummyArticles = Arrays.asList(
                new Article("Title 1", "url1", "Source 1", "https://source1.com", "2025-11-04, 12:00:00", 5, 5, "Title 1"),
                new Article("tiTLE 2", "url2", "Source 2", "https://source2.com", "2025-11-04, 13:00:00", 5, 5, "Title 2")
        );

        // Create QueryResult
        QueryResult qr = new QueryResult(key, dummyArticles, 5.0, 5.0);

        // Populate the controller cache
        Map<String, QueryResult> testCache = new LinkedHashMap<>();
        testCache.put(key, qr);
        controller.setCache(testCache); // now modifies the cache

        // Build a fake request
        Http.Request fakeRequest = fakeRequest().build();

        // Call stats
        CompletionStage<Result> notArrivedResult = controller.stats(fakeRequest, key);
        Result result = notArrivedResult.toCompletableFuture().join();

        // Verify results
        assertEquals(OK, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("Word Statistics for " + key));
    }

    @Test
    public void profile_returnsEmptyViewMessage_whenNoArticles() throws Exception {
        try (MockedConstruction<Client> mocked =
                     mockConstruction(Client.class, (m, ctx) ->
                             when(m.clientRequest(anyString()))
                                     .thenReturn(CompletableFuture.completedFuture(Collections.emptyList())))) {

            CompletionStage<Result> stage = controller.profile("BBC", null);
            Result result = stage.toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(OK, result.status());
            String body = contentAsString(result);
            assertTrue(body.contains("No Articles Found for this source"));
        }
    }

    @Test
    public void profile_listsArticles_whenPresent() throws Exception {
        Article a = new Article("T1", "u1", "BBC", "https://bbc.com",
                "2025-01-01, 12:00:00", 5, 5, "d1");
        List<Article> articles = List.of(a);

        try (MockedConstruction<Client> mocked =
                     mockConstruction(Client.class, (m, ctx) ->
                             when(m.clientRequest(anyString()))
                                     .thenReturn(CompletableFuture.completedFuture(articles)))) {

            Result result = controller.profile("BBC", null).toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(OK, result.status());
            String body = contentAsString(result);
            assertTrue(body.contains("Listing Articles from BBC"));
            assertTrue(body.contains("https://bbc.com")); // sourceUrl appears in rendered view
        }
    }


    @Test
    public void updateSession_removesDuplicate_putsNewFirst_andTrimsToLimit() throws Exception {
        // Build a session that already has more than the limit and contains the new query in the middle
        String[] initial = {"old1","keep1","dup","keep2","keep3","keep4","keep5","keep6","keep7","keep8","extra1","extra2"};
        Http.Session in = new Http.Session(Map.of("queries", String.join(",", initial)));

        // Access private method via reflection
        Method m = HomeController.class.getDeclaredMethod(
                "updateSession", Http.Session.class, String.class, int.class);
        m.setAccessible(true);

        int limit = 10;
        Http.Session out = (Http.Session) m.invoke(controller, in, "dup", limit);

        String stored = out.get("queries").orElse("");
        String[] qs = stored.split(",");

        // New query added at the top
        assertEquals("dup", qs[0]);

        // Size is trimmed to the specified limit
        assertEquals(limit, qs.length);

        // Next entries should be the older ones in original order (without the removed duplicate)
        // Expected tail: old1, keep1, keep2..keep8 (10-1 = 9 items)
        String[] expectedTail = {"old1","keep1","keep2","keep3","keep4","keep5","keep6","keep7","keep8"};
        assertArrayEquals(expectedTail, Arrays.copyOfRange(qs, 1, qs.length));
    }

    // ---------------- getCache() (and setter) ----------------

    @Test
    public void getCache_returnsSameMap_setViaSetter() {
        Map<String, QueryResult> expected = new LinkedHashMap<>();
        expected.put("q", new QueryResult("q", Collections.emptyList(), 0.0, 0.0));

        controller.setCache(expected);
        Map<String, QueryResult> actual = controller.getCache();

        assertSame(expected, actual);
        assertTrue(actual.containsKey("q"));
    }


}
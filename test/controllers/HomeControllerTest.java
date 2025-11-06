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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.*;

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
        Mockito.when(mockConfig.getString("newsapi.key")).thenReturn("dummyKey");
        Mockito.when(mockConfig.getString("newsapi.url")).thenReturn("https://newsapi.org/v2/everything?");

        // --- Stub WSClient chain ---
        Mockito.when(mockWs.url(Mockito.anyString())).thenReturn(mockRequest);
        Mockito.when(mockRequest.setRequestTimeout(Mockito.any(Duration.class))).thenReturn(mockRequest);

        // --- Mock WSResponse behavior ---
        Mockito.when(mockResponse.getStatus()).thenReturn(200);
        Mockito.when(mockResponse.asJson()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .putArray("articles")  // empty array, safe dummy response
        );

        // --- Return a completed future when get() is called ---
        CompletableFuture<WSResponse> fakeFuture = CompletableFuture.completedFuture(mockResponse);
        Mockito.when(mockRequest.get()).thenReturn(fakeFuture);

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
        Result result = controller.stats(fakeRequest, key);
        // Verify results
        assertEquals(OK, result.status());
        String body = contentAsString(result);
        //System.out.println(body);
        assertTrue(body.contains("2 articles have been taken into account"));
        assertTrue(body.contains("title:4"));
    }

    /**
     * Tests the source() method in HomeController
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
        Mockito.when(mockRequest.get()).thenReturn(failedFuture); //Mockito mocks a failed return

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
    public void testSearchWithShowSourcesTrue() {
        Http.Request req = fakeRequest()
                .method(GET)
                .uri("/search?SearchInput=energy&sortBy=relevancy&showSources=true")
                .build();

        Result result = controller.search(req).toCompletableFuture().join();

        assertEquals(OK, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("Search Results for: energy"));
    }

    /** Covers multi-query accumulation using session + cache (two sequential searches). */
    @Test
    public void testSearchAccumulatesQueriesInSessionAndCache() {
        // 1st search — adds "alpha" to session and cache
        Http.Request r1 = fakeRequest()
                .method(GET)
                .uri("/search?SearchInput=alpha&sortBy=publishedAt")
                .build();
        Result res1 = controller.search(r1).toCompletableFuture().join();
        assertEquals(OK, res1.status());

        // 2nd search — adds "beta" and should keep (alpha, beta) in session; results map built from cache
        Http.Request r2 = fakeRequest()
                .method(GET)
                .uri("/search?SearchInput=beta&sortBy=publishedAt")
                .build();
        Result res2 = controller.search(r2).toCompletableFuture().join();
        assertEquals(OK, res2.status());
        String body2 = contentAsString(res2);
        assertTrue(body2.contains("Search Results for: beta"));
        // We can also sanity-check the controller cache now holds at least beta
        assertTrue(controller.getCache().containsKey("beta"));
    }

    /** Covers the exceptionally(...) branch in search() by forcing the WS call to fail. */
    @Test
    public void testSearchHandlesApiFailure() {
        // Make WSRequest.get() fail for this test only
        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom"));
        Mockito.when(mockRequest.get()).thenReturn(failed);

        Http.Request req = fakeRequest()
                .method(GET)
                .uri("/search?SearchInput=failcase&sortBy=publishedAt")
                .build();

        Result result = controller.search(req).toCompletableFuture().join();

        assertEquals(INTERNAL_SERVER_ERROR, result.status());
        String body = contentAsString(result);
        assertTrue(body.contains("Error fetching results"));
    }

    /** Trivial getters/setters + static accessor to close coverage gaps. */
    @Test
    public void testCacheGetterSetterAndMaxVisible() {
        Map<String, QueryResult> m = new LinkedHashMap<>();
        m.put("q", new QueryResult("q", Collections.emptyList(), 0.0, 0.0));
        controller.setCache(m);
        assertSame(m, controller.getCache());
        assertTrue(HomeController.getMaxArticlesVisible() > 0);
    }


    @Test
    public void testProfile_NoArticles() throws ExecutionException, InterruptedException, TimeoutException {
        try(MockedConstruction<Client> mockedClient = mockConstruction(Client.class, (mock, context) -> {
            when(mock.clientRequest(anyString())).thenReturn(CompletableFuture.completedFuture(new ArrayList<> ()));
        })) {
            CompletionStage<Result> stage = controller.profile("TestSourceName", "TestID");
            Result result = stage.toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(OK, result.status());
            String body = contentAsString(result);
            assertTrue(body.contains("No Articles Found for this source"));
        }
    }

    @Test
    public void testProfile() throws ExecutionException, InterruptedException, TimeoutException {
        Article testArticle = new Article("Title One", "url1", "Source1", "sourceUrl1", "2025-01-01, 12:00:00", 5, 5, "Description One");

        List<Article> articles = List.of(testArticle);

        try (MockedConstruction<Client> mockedClient = mockConstruction(Client.class, (mock, context) -> {
            when(mock.clientRequest(anyString())).thenReturn(CompletableFuture.completedFuture(articles));
        })) {
            CompletionStage<Result> stage = controller.profile("BBC", null);
            Result result = stage.toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(OK, result.status());
            String body = contentAsString(result);

            assertTrue(body.contains("Listing Articles from BBC"));
            assertTrue(body.contains("sourceUrl1"));
        }
    }
    @Test
    public void updateSession_truncatesWhenOverLimit_andPlacesNewQueryFirst() throws Exception {
        // Arrange: controller with dummy deps
        HomeController ctrl = new HomeController(
                org.mockito.Mockito.mock(play.libs.ws.WSClient.class),
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                org.mockito.Mockito.mock(com.typesafe.config.Config.class)
        );

        // Seed session with 12 prior queries (over the limit we'll pass: 10)
        String seeded = String.join(",", "q1","q2","q3","q4","q5","q6","q7","q8","q9","q10","q11","q12");
        Http.Session in = new Http.Session(Collections.singletonMap("queries", seeded));

        // Access private method: updateSession(Http.Session, String, int)
        Method m = HomeController.class.getDeclaredMethod(
                "updateSession", Http.Session.class, String.class, int.class);
        m.setAccessible(true);

        // Act: add a new query with limit = 10
        Http.Session out = (Http.Session) m.invoke(ctrl, in, "q13", 10);

        // Assert: at most 10 queries, new query first, and order preserved for the rest
        String stored = out.get("queries").orElse("");
        String[] qs = stored.split(",");
        assertEquals("Should keep exactly 10 entries", 10, qs.length);
        assertEquals("New query must be first", "q13", qs[0]);

        // Next 9 should be q1..q9 (q10+ trimmed)
        String[] expectedTail = {"q1","q2","q3","q4","q5","q6","q7","q8","q9"};
        for (int i = 0; i < expectedTail.length; i++) {
            assertEquals("Order of older queries should be preserved",
                    expectedTail[i], qs[i + 1]);
        }
    }


    @Test
    public void testSearch_exceptionally_returns500() {
        // Arrange: make WSClient.get() fail to hit controller's exceptionally(...)
        WSRequest failingRequest = Mockito.mock(WSRequest.class);
        Mockito.when(mockWs.url(Mockito.anyString())).thenReturn(failingRequest);
        Mockito.when(failingRequest.setRequestTimeout(Mockito.any(Duration.class))).thenReturn(failingRequest);

        CompletableFuture<WSResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Boom"));
        Mockito.when(failingRequest.get()).thenReturn(failed);

        Http.Request fakeRequest = fakeRequest()
                .method(GET)
                .uri("/search?SearchInput=climate&sortBy=publishedAt")
                .build();

        Result result = controller.search(fakeRequest).toCompletableFuture().join();

        assertEquals(INTERNAL_SERVER_ERROR, result.status());
        assertTrue(contentAsString(result).contains("Error fetching results"));
    }
}
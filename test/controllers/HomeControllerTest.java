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
/**
 * Integration-style unit tests for {@link HomeController}.
 * <p>
 * Uses Mockito to stub Play's {@link WSClient} and related HTTP classes so that
 * no real network calls are made. Exercises controller actions (index, search,
 * sources, stats, profile) plus session/cache behavior and error branches to
 * maximize JaCoCo coverage.
 * @author Santhosh
 */
public class HomeControllerTest {

    private WSClient mockWs;
    private WSRequest mockRequest;
    private WSResponse mockResponse;
    private Config mockConfig;
    private Executor executor;
    private HomeController controller;

    /**
     * Sets up test fixtures and default stubs:
     * <ul>
     *   <li>Mocks {@link WSClient}, {@link WSRequest}, and {@link WSResponse}.</li>
     *   <li>Configures a dummy NewsAPI URL and key.</li>
     *   <li>Stubs GET requests to return 200 OK with an empty <code>articles</code> array.</li>
     *   <li>Builds the {@link HomeController} under test.</li>
     * </ul>
     * No external I/O occurs.
     * @author Santhosh
     */
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

    /**
     * Verifies that {@link HomeController#index(play.mvc.Http.Request)} renders the
     * landing page and contains the expected welcome text.
     * Asserts HTTP 200 and the presence of "Welcome to NotiLytics".
     * @author Santhosh
     */
    @Test
    public void testIndexRendersWelcomeMessage() {
        Http.Request fakeRequest = fakeRequest().build();
        Result result = controller.index(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        assertTrue(contentAsString(result).contains("Welcome to NotiLytics"));
    }

    /**
     * Verifies that {@link HomeController#search(play.mvc.Http.Request)} returns a
     * prompt when the <code>SearchInput</code> parameter is missing/empty.
     * Asserts HTTP 200 and the presence of "Please enter a search term".
     * @author Santhosh
     */
    @Test
    public void testSearchWithEmptyInputReturnsPrompt() {
        Http.Request fakeRequest = fakeRequest().method(GET).uri("/search").build();
        Result result = controller.search(fakeRequest).toCompletableFuture().join();

        assertEquals(OK, result.status());
        assertTrue(contentAsString(result).contains("Please enter a search term"));
    }

    /**
     * Verifies a happy-path search: a valid <code>SearchInput</code> produces a
     * rendered result view. Uses the default stubbed WS response.
     * Asserts HTTP 200 and that the body contains "Search Results for".
     * @author Santhosh
     */
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
     * Verifies the <code>stats</code> action using a pre-populated controller cache.
     * Builds a {@link QueryResult} with two articles, invokes
     * {@link HomeController#stats(play.mvc.Http.Request, String)}, and asserts:
     * <ul>
     *   <li>HTTP 200</li>
     *   <li>Correct article count message</li>
     *   <li>Presence of a known token frequency (e.g., "title:4")</li>
     * </ul>
     * @author Santhosh,Karim
     */
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
     * Verifies the <code>sources</code> action for multiple filter scenarios and
     * for an API failure branch:
     * <ul>
     *   <li>No filters, country=us, category=technology, language=fr</li>
     *   <li>Simulated failing future to hit the error handler</li>
     * </ul>
     * Asserts HTTP 200 for success cases and 500 with "Error fetching sources" for failure.
     * @author Hasnaou
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

    /**
     * Verifies that the <code>showSources=true</code> query parameter toggles the
     * corresponding branch in the search action and the page renders as expected.
     * Asserts HTTP 200 and the "Search Results for: energy" header.
     * @author Santhosh
     */
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

    /**
     * Covers session accumulation and cache usage across sequential searches.
     * Performs two searches ("alpha" then "beta") and asserts:
     * <ul>
     *   <li>Second response includes "Search Results for: beta"</li>
     *   <li>Controller cache contains the latest query ("beta")</li>
     * </ul>
     * @author Santhosh
     */
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

    /**
     * Forces the WS request to fail and verifies the search action’s
     * <code>exceptionally(...)</code> path.
     * Asserts HTTP 500 and the presence of "Error fetching results" in the body.
     * @author Santhosh
     */
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

    /**
     * Covers trivial getters/setters and the static accessor:
     * <ul>
     *   <li>{@link HomeController#setCache(Map)} and {@link HomeController#getCache()}</li>
     *   <li>{@link HomeController#getMaxArticlesVisible()}</li>
     * </ul>
     * Ensures these code paths are executed for full coverage.
     * @author Santhosh
     */
    @Test
    public void testCacheGetterSetterAndMaxVisible() {
        Map<String, QueryResult> m = new LinkedHashMap<>();
        m.put("q", new QueryResult("q", Collections.emptyList(), 0.0, 0.0));
        controller.setCache(m);
        assertSame(m, controller.getCache());
        assertTrue(HomeController.getMaxArticlesVisible() > 0);
    }


    /**
     * Verifies the <code>profile</code> action when the API returns no articles.
     * Uses Mockito's {@link org.mockito.MockedConstruction} to return an empty list
     * from {@link Services.Client#clientRequest(String)}.
     * Asserts HTTP 200 and "No Articles Found for this source".
     * @author Haytham
     */
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

    /**
     * Verifies the <code>profile</code> action when the API returns at least one article.
     * Mocks {@link Services.Client} construction to return a singleton list and
     * asserts that the response:
     * <ul>
     *   <li>Is HTTP 200</li>
     *   <li>Contains "Listing Articles from BBC"</li>
     *   <li>Includes the expected source URL</li>
     * </ul>
     * @author Haytham
     */
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
    /**
     * Uses reflection to invoke the private three-argument
     * <code>updateSession(Http.Session, String, int)</code> overload and verifies:
     * <ul>
     *   <li>The session is truncated to the specified limit (10)</li>
     *   <li>The new query is placed at the front</li>
     *   <li>The order of remaining items is preserved</li>
     * </ul>
     * This directly covers the branch that trims the stored query list.
     * @author Santhosh
     */
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

    /**
     * Alternative failure-path coverage for the search action:
     * re-stubs the WS chain so <code>get()</code> completes exceptionally, then
     * asserts HTTP 500 and the error message. Ensures robustness against transient
     * upstream failures.
     * @author Santhosh
     */
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
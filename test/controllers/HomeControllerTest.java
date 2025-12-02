package controllers;

import Services.Client;
import actors.SourceProfileActor;
import actors.CacheActor;
import actors.StatisticsActor;
import actors.UserParentActor;
import actors.ReadabilityActor;
import models.Article;
import models.QueryResult;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
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

import org.apache.pekko.actor.ActorSystem;


public class HomeControllerTest {

    private WSClient mockWs;
    private WSRequest mockRequest;
    private WSResponse mockResponse;
    private Config mockConfig;
    private Executor executor;
    private HomeController controller;
    private ActorSystem system;
    private ActorRef<UserParentActor.Create> userParentActor;
    private ActorRef<ReadabilityActor.Command> readabilityActor;
    private ActorRef<StatisticsActor.Command> StatisticsActor;
    private ActorRef<CacheActor.Command> CacheActor;
    private ActorRef<SourceProfileActor.Command> sourceProfileActor;

    /**
     * Sets up a minimal controller with mocked dependencies (WS client, config, executor).
     * Stubs NewsAPI config values and ensures WSClient.get() returns a completed future.
     * @author Santhosh
     */
    @Before
    public void setup() {
        // --- Mock dependencies ---
        mockWs = Mockito.mock(WSClient.class);
        mockRequest = Mockito.mock(WSRequest.class);
        mockResponse = Mockito.mock(WSResponse.class);
        mockConfig = Mockito.mock(Config.class);
        system = Mockito.mock(ActorSystem.class);
        userParentActor = Mockito.mock(ActorRef.class);
        readabilityActor = Mockito.mock(ActorRef.class);
        StatisticsActor = Mockito.mock(ActorRef.class);
        CacheActor = Mockito.mock(ActorRef.class);
        sourceProfileActor = Mockito.mock(ActorRef.class);
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
        controller = new HomeController(mockWs, executor, mockConfig, system, userParentActor, readabilityActor, StatisticsActor, CacheActor, sourceProfileActor);
    }

    /**
     * Verifies that {@link HomeController#index(play.mvc.Http.Request)} renders the
     * welcome page and returns HTTP 200 with the expected greeting.
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
     * Ensures that when no SearchInput is provided, {@link HomeController#search(Http.Request)}
     * returns HTTP 200 and prompts the user to enter a search term.
     * @author Santhosh
     */
    // @Test
    // public void testSearchWithEmptyInputReturnsPrompt() {
    //     Http.Request fakeRequest = fakeRequest().method(GET).uri("/search").build();
    //     Result result = controller.search(fakeRequest).toCompletableFuture().join();
    //     assertEquals(OK, result.status());
    //     assertTrue(contentAsString(result).contains("Please enter a search term"));
    // }

    /**
     * Confirms that a valid SearchInput path renders a results page (HTTP 200)
     * and includes the “Search Results for …” marker in the response body.
     * @author Santhosh
     */
    // @Test
    // public void testSearchWithValidInputUpdatesSession() {
    //     Http.Request fakeRequest = fakeRequest()
    //             .method(GET)
    //             .uri("/search?SearchInput=climate&sortBy=publishedAt")
    //             .build();
    //
    //     Result result = controller.search(fakeRequest).toCompletableFuture().join();
    //
    //     assertEquals(OK, result.status());
    //     String body = contentAsString(result);
    //     assertTrue(body.contains("Search Results for"));
    // }

    /**
     * Tests the source() method in HomeController
     * @author Ilyes
     */
//    @Test
//    public void testSource() {
//        //No filters tested here
//        Http.Request fakeRequest = fakeRequest()
//                .method(GET)
//                .uri("/sources")
//                .build();
//
//        Result result = controller.sources(fakeRequest).toCompletableFuture().join();
//
//        assertEquals(OK, result.status());
//        String body = contentAsString(result);
//        assertTrue(body.contains("News Sources"));
//
//        //Country filter tested here
//        fakeRequest = fakeRequest()
//                .method(GET)
//                .uri("/sources?country=us")
//                .build();
//
//        result = controller.sources(fakeRequest).toCompletableFuture().join();
//
//        assertEquals(OK, result.status());
//        body = contentAsString(result);
//        assertTrue(body.contains("News Sources"));
//
//        //Category filter now
//        fakeRequest = fakeRequest()
//                .method(GET)
//                .uri("/sources?category=technology")
//                .build();
//
//        result = controller.sources(fakeRequest).toCompletableFuture().join();
//
//        assertEquals(OK, result.status());
//        body = contentAsString(result);
//        assertTrue(body.contains("News Sources"));
//
//        //Language filter
//        fakeRequest = fakeRequest()
//                .method(GET)
//                .uri("/sources?language=fr")
//                .build();
//
//        result = controller.sources(fakeRequest).toCompletableFuture().join();
//
//        assertEquals(OK, result.status());
//        body = contentAsString(result);
//        assertTrue(body.contains("News Sources"));
//
//        //We will simulate an error to see what happens here
//        CompletableFuture<WSResponse> failedFuture = new CompletableFuture<>();
//        failedFuture.completeExceptionally(new RuntimeException("API Error")); //CompletableFuture does not fulfill promise here
//        when(mockRequest.get()).thenReturn(failedFuture); //Mockito mocks a failed return
//
//        fakeRequest = fakeRequest() //fake http request
//                .method(GET)
//                .uri("/sources")
//                .build();
//
//        result = controller.sources(fakeRequest).toCompletableFuture().join();
//
//        assertEquals(INTERNAL_SERVER_ERROR, result.status());
//        body = contentAsString(result);
//        assertTrue(body.contains("Error fetching sources"));
//    }
    /**
     * Covers {@link HomeController#stats(Http.Request, String)}:
     * - seeds the in-memory cache with a {@link QueryResult}
     * - verifies that the response is HTTP 200 and the title includes the query key.
     * @author karim
     */
    // @Test
    // public void testStat() {
    //     String key = "testKey";
    //
    //     // Create dummy articles
    //     List<Article> dummyArticles = Arrays.asList(
    //             new Article("Title 1", "url1", "Source 1", "https://source1.com", "2025-11-04, 12:00:00", 5, 5, "Title 1"),
    //             new Article("tiTLE 2", "url2", "Source 2", "https://source2.com", "2025-11-04, 13:00:00", 5, 5, "Title 2")
    //     );
    //
    //     // Create QueryResult
    //     QueryResult qr = new QueryResult(key, dummyArticles, 5.0, 5.0);
    //
    //     // Populate the controller cache
    //     Map<String, QueryResult> testCache = new LinkedHashMap<>();
    //     testCache.put(key, qr);
    //     controller.setCache(testCache); // now modifies the cache
    //
    //     // Build a fake request
    //     Http.Request fakeRequest = fakeRequest().build();
    //
    //     // Call stats
    //     CompletionStage<Result> notArrivedResult = controller.stats(fakeRequest, key);
    //     Result result = notArrivedResult.toCompletableFuture().join();
    //
    //     // Verify results
    //     assertEquals(OK, result.status());
    //     String body = contentAsString(result);
    //     assertTrue(body.contains("Word Statistics for " + key));
    // }

    /**
     * Verifies the empty-articles branch in {@link HomeController#profile(String, String)}:
     * when the client returns an empty list, the controller renders the “No Articles Found” message.
     * @author Santhosh & Haytham
     */
//    @Test
//    public void testProfile_returnsEmptyViewMessage_whenNoArticles() throws Exception {
//            CompletionStage<Result> stage = controller.profile("BBC", null);
//            SourceProfileActor.ProfileRequest emptyRequest = sourceProfile_Probe.expectMessageClass(
//                    SourceProfileActor.ProfileRequest.class
//            );
//
//            Result emptyResult = Results.ok(views.html.sourceProfile.render(new SourceProfile(
//                    "BBC News",
//                    "https://www.bbc.com",
//                    "No Articles Found for this source"
//            ), List.of()));
//
//            emptyRequest.replyTo.tell(emptyResult);
//
//            Result result = stage.toCompletableFuture().get(2, TimeUnit.SECONDS);
//
//            assertEquals(OK, result.status());
//            String body = contentAsString(result);
//            assertTrue(body.contains("No Articles Found for this source"));
//
//    }

    /**
     * Verifies the non-empty branch in {@link HomeController#profile(String, String)}:
     * when the client returns at least one article, the controller renders a listing
     * and surfaces the source URL in the view.
     * @author Santhosh & Haytham
     */
//    @Test
//    public void testProfile_listsArticles_whenPresent() throws Exception {
//        Article a = new Article("T1", "u1", "BBC", "https://bbc.com",
//                "2025-01-01, 12:00:00", 5, 5, "d1");
//        List<Article> articles = List.of(a);
//
//        CompletionStage<Result> stage = controller.profile("BBC", null);
//
//        SourceProfileActor.ProfileRequest request = sourceProfile_Probe.expectMessageClass(
//                SourceProfileActor.ProfileRequest.class
//        );
//
//        Result expected = Results.ok(views.html.sourceProfile.render(new SourceProfile(
//                "BBC News",
//                "https://www.bbc.com",
//                "Listing Articles from BBC"
//        ), articles));
//
//
//        request.replyTo.tell(expected);
//
//        Result result = stage.toCompletableFuture().get(2, TimeUnit.SECONDS);
//
//        assertEquals(OK, result.status());
//        String body = contentAsString(result);
//        assertTrue(body.contains("Listing Articles from BBC"));
//        assertTrue(body.contains("https://www.bbc.com")); // sourceUrl appears in rendered view
//
//    }


    /**
     * Verifies that all optional filter parameters (country, category, language) are appended
     * to the URL passed into the client for the sources endpoint.
     * @author Santhosh & Haytham
     */
//    @Test
//    public void testProfile_withId_usesIdInUrl_andRendersArticles() throws Exception {
//        // Build a minimal article so the "non-empty" path is taken
//
//        Article a = new Article("T1", "u1", "BBC", "https://bbc.com",
//                "2025-01-01, 12:00:00", 5, 5, "d1");
//        List<Article> articles = List.of(a);
//
//        CompletionStage<Result> stage = controller.profile("BBC", "bbc-news");
//
//        SourceProfileActor.ProfileRequest request = sourceProfile_Probe.expectMessageClass(
//                SourceProfileActor.ProfileRequest.class
//        );
//
//        Result expected = Results.ok(views.html.sourceProfile.render(new SourceProfile(
//                "BBC News",
//                "https://www.bbc.com",
//                "Listing Articles from BBC"
//        ), articles));
//
//
//        request.replyTo.tell(expected);
//
//        Result result = stage.toCompletableFuture().get(2, TimeUnit.SECONDS);
//
//        assertEquals(OK, result.status());
//        String body = contentAsString(result);
//        assertTrue(request.id.contains("bbc-news")); // id used, not the sourceName
//        assertTrue(body.contains("Listing Articles from BBC")); // rendered non-empty branch
//    }


    /**
     * Unit-tests the private overload {@code updateSession(session, newQuery, limit)} via reflection:
     * - removes an existing duplicate of the new query,
     * - places the new query at the front,
     * - trims the list to the provided limit while preserving relative order of older items.
     * @author Santhosh
     */
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


    /**
     * Sanity-checks cache accessor behavior:
     * ensures {@link HomeController#setCache(Map)} and {@link HomeController#getCache()}
     * operate on the same instance and that entries are preserved.
     * @author Santhosh
     */
    @Test
    public void getCache_returnsSameMap_setViaSetter() {
        Map<String, QueryResult> expected = new LinkedHashMap<>();
        expected.put("q", new QueryResult("q", Collections.emptyList(), 0.0, 0.0));

        controller.setCache(expected);
        Map<String, QueryResult> actual = controller.getCache();

        assertSame(expected, actual);
        assertTrue(actual.containsKey("q"));
    }

    /**
     * Ensures that when the {@code showSources} parameter is absent, the search page still
     * renders successfully with the default flag (false) and shows the results banner.
     * @author Santhosh
     */
    // @Test
    // public void testSearch_showSourcesAbsent_defaultsFalse() {
    //     try (org.mockito.MockedConstruction<Client> mocked = org.mockito.Mockito.mockConstruction(
    //             Client.class,
    //             (mock, ctx) -> when(mock.clientRequest(anyString()))
    //                     .thenReturn(CompletableFuture.completedFuture(java.util.Collections.emptyList()))
    //     )) {
    //         Http.Request req = fakeRequest()
    //                 .method(GET)
    //                 .uri("/search?SearchInput=energy&sortBy=relevancy") // no showSources param
    //                 .build();
    //
    //         Result result = controller.search(req).toCompletableFuture().join();
    //         assertEquals(OK, result.status());
    //         String body = contentAsString(result);
    //         assertTrue(body.contains("Search Results for: energy")); // page rendered with showSources=false
    //     }
    // }

    /**
     * Duplicates the “no articles” profile path to ensure coverage when invoking
     * the method without an id and with a specific source name.
     * @author Santhosh
     */
//    @Test
//    public void testProfile_emptyList_rendersNoArticlesMessage() {
//        try (org.mockito.MockedConstruction<Client> mocked = org.mockito.Mockito.mockConstruction(
//                Client.class,
//                (mock, ctx) -> when(mock.clientRequest(anyString()))
//                        .thenReturn(CompletableFuture.completedFuture(java.util.Collections.emptyList()))
//        )) {
//            Result result = controller.profile("AnySource", null).toCompletableFuture().join();
//            assertEquals(OK, result.status());
//            String body = contentAsString(result);
//            assertTrue(body.contains("No Articles Found for this source"));
//        }
//    }


    /**
     * Reflection-based test for the private {@code getPreviousQueries(session)}:
     * - returns an empty list when the session lacks the key,
     * - splits a CSV string into an ordered list of prior queries when present.
     * @author Santhosh
     */
    @Test
    @SuppressWarnings("unchecked")
    public void getPreviousQueries_handlesEmptyAndCsv() throws Exception {
        // Arrange controller with minimal deps
        WSClient ws = Mockito.mock(WSClient.class);
        Executor ex = java.util.concurrent.Executors.newSingleThreadExecutor();
        Config cfg = Mockito.mock(Config.class);
        ActorSystem ast = Mockito.mock(ActorSystem.class);
        ActorRef<UserParentActor.Create> upa = Mockito.mock(ActorRef.class);
        ActorRef<ReadabilityActor.Command> ra = Mockito.mock(ActorRef.class);
        ActorRef<CacheActor.Command> ca = Mockito.mock(ActorRef.class);
        ActorRef<StatisticsActor.Command> sa = Mockito.mock(ActorRef.class);
        ActorRef<SourceProfileActor.Command> spa = Mockito.mock(ActorRef.class);
        Mockito.when(cfg.getString("newsapi.key")).thenReturn("dummyKey");
        Mockito.when(cfg.getString("newsapi.url")).thenReturn("https://newsapi.org/v2/everything?");
        HomeController ctrl = new HomeController(ws, ex, cfg, ast, upa, ra, sa, ca, spa);

        // Access private method
        java.lang.reflect.Method m = HomeController.class
                .getDeclaredMethod("getPreviousQueries", Http.Session.class);
        m.setAccessible(true);

        // Case 1: no 'queries' in session (empty result expected)
        Http.Session sEmpty = new Http.Session(java.util.Collections.emptyMap());
        java.util.List<String> none =
                (java.util.List<String>) m.invoke(ctrl, sEmpty);
        assertNotNull(none);
        assertTrue(none.isEmpty());

        // Case 2: CSV present → split into list
        Http.Session sCsv = new Http.Session(
                java.util.Collections.singletonMap("queries", "q1,q2,q3"));
        java.util.List<String> got =
                (java.util.List<String>) m.invoke(ctrl, sCsv);
        assertEquals(3, got.size());
        assertEquals(java.util.Arrays.asList("q1","q2","q3"), got);
    }
}


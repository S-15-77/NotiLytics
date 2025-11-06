package controllers;

import junit.framework.TestCase;
import models.Article;
import models.QueryResult;
import Services.Client;
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

public class SourceControllerTest {

    private WSClient mockWs;
    private WSRequest mockRequest;
    private WSResponse mockResponse;
    private Config mockConfig;
    private Executor executor;
    private SourceController controller;

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
        controller = new SourceController(mockWs, executor, mockConfig);
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
}
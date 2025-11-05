package controllers;

import models.Article;
import models.QueryResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import play.libs.ws.*;
import play.mvc.Http;
import play.mvc.Result;
import com.typesafe.config.Config;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;
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
        controller = new SourceController(mockWs, executor, mockConfig);
    }
}
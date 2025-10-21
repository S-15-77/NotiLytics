package controllers;


import models.Article;
import play.mvc.*;
import play.mvc.Http.Request;

import play.libs.ws.*;
//webservice documentation: https://www.playframework.com/documentation/3.0.x/JavaWS

import play.mvc.Controller; //to get the configuration
import com.typesafe.config.Config;
// https://www.playframework.com/documentation/3.0.x/ConfigFile

import play.libs.concurrent.HttpExecutionContext;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


import Services.Client;


public class HomeController extends Controller {
   private final WSClient ws;
   private final HttpExecutionContext ec;
   private final Config config;
   private final String Key;
   private final String url;
    private static final String SESSION_KEY = "queries";

    /** Read queries stored in user session */
    private List<String> getPreviousQueries(Http.Session session) {
        String data = session.getOptional(SESSION_KEY).orElse("");
        if (data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }

    /** Store new query at top, remove duplicates, keep at most 10 */
    private Http.Session updateSession(Http.Session session, String newQuery) {
        List<String> queries = getPreviousQueries(session);
        queries.remove(newQuery);       // avoid duplicates
        queries.add(0, newQuery);       // add newest at top
        if (queries.size() > 10)        // limit 10
            queries = queries.subList(0, 10);
        return session.adding(SESSION_KEY, String.join(",", queries));
    }

   //request look:
   //https://newsapi.org/v2/everything?q=tesla&from=2025-09-17&sortBy=publishedAt&apiKey=cab8fb494a5e4326baa696536c3f270c
   //https://newsapi.org/v2/everything?q=            CONSTANT PART
   // tesla                                          WORD QUERY (implemented)
   // &from=2025-09-17                               DATE (Unimplemented)
   // &sortBy=publishedAt                            SORTING
   // &apiKey=cab8fb494a5e4326baa696536c3f270c       IMPLEMENTING


   @Inject
   public HomeController(WSClient ws, HttpExecutionContext ec, Config config) {
       this.ec = ec;
       this.ws = ws; //to pass to the client class

       //environment variables
       this.config = config;
       this.Key = config.getString("newsapi.key");
       this.url = config.getString("newsapi.url");
   }



   public CompletionStage<Result> index() {
        //Async response with a simple message
        return CompletableFuture.supplyAsync(() -> ok(views.html.index.render("Empty", Collections.emptyList())));
    }
/**
   public CompletionStage<Result> search(Request request)  {
       String searchInput = request.getQueryString("SearchInput");

       RequestToNewsAPI(this.Key, searchInput);
       return CompletableFuture.supplyAsync(() -> ok(views.html.index.render("Searching... :" + searchInput)));
   }

   public void RequestToNewsAPI(String key, String SearchInput){
       String url = this.url +"q=" + SearchInput + "&apiKey=" + this.Key;
       Client client = new Client(this.ws);
       CompletionStage<String> response = client.clientRequest(url);
       CompletableFuture.supplyAsync(() -> ok(views.html.index.render("Result... :" + response)));
   }
 */

public CompletionStage<Result> search(Http.Request request) {
    String searchInput = request.getQueryString("SearchInput");
    String sortBy = request.getQueryString("sortBy");

    String url = this.url + "q=" + searchInput + "&sortBy=" + sortBy + "&apiKey=" + this.Key;
    Client client = new Client(this.ws);

    // explicitly specify generic type to help compiler
    CompletionStage<List<Article>> response = client.clientRequest(url);

    return response.thenApplyAsync(
            (List<Article> articles) -> {
                String displayMessage =
                        "Result for: <b>" + searchInput + "</b> (sorted by <i>" + sortBy + "</i>)";
                return ok(views.html.index.render(displayMessage, articles));
            },
            ec.current()
    ).exceptionally(ex -> {
        ex.printStackTrace();
        return internalServerError("Error fetching results: " + ex.getMessage());
    });
}

}

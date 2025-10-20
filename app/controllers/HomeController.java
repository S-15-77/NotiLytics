package controllers;


import play.mvc.*;
import play.mvc.Http.Request;

import play.libs.ws.*;
//webservice documentation: https://www.playframework.com/documentation/3.0.x/JavaWS

import play.mvc.Controller; //to get the configuration
import com.typesafe.config.Config;
// https://www.playframework.com/documentation/3.0.x/ConfigFile

import play.libs.concurrent.HttpExecutionContext;
import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


import Services.Client;


public class HomeController extends Controller {
   private final WSClient ws;
   private final HttpExecutionContext ec;
   private final Config config;
   private final String Key;
   private final String url;

   //request look:
   //https://newsapi.org/v2/everything?q=tesla&from=2025-09-17&sortBy=publishedAt&apiKey=cab8fb494a5e4326baa696536c3f270c
   //https://newsapi.org/v2/everything?q=            CONSTANT PART
   // tesla                                          WORD QUERY (implemented)
   // &from=2025-09-17                               DATE (Unimplemented)
   // &sortBy=publishedAt                            SORTING (Unimplemented)
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
        return CompletableFuture.supplyAsync(() -> ok(views.html.index.render("Empty")));
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
    //if (searchInput == null || searchInput.isEmpty()) searchInput = "latest";
    //problem with search input finality

    String url = this.url + "q=" + searchInput + "&apiKey=" + this.Key;
    Client client = new Client(this.ws);

    // client.clientRequest returns CompletionStage<String>
    return client.clientRequest(url)
            .thenApplyAsync(responseBody -> {
                // responseBody is either API result or timeout message
                return ok(views.html.index.render("Result for: " + searchInput + "<br><br>" + responseBody));
            }, ec.current());
}

}

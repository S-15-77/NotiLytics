package controllers;

import models.Article;
import models.QueryResult;
import models.ReadabilityCalculator;
import models.SourceProfile;
import play.mvc.*;
import play.libs.ws.*;
import com.typesafe.config.Config;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import Services.Client;
import views.html.*;

import static play.shaded.ahc.com.sun.activation.registries.LogSupport.log;

public class SourceProfileController extends Controller {
    private final WSClient ws;
    private final Executor executor;
    private final String Key;
    private final String url;

    @Inject
    public SourceProfileController(WSClient ws, Executor executor, Config config) {
        this.ws = ws;
        this.executor = executor;
        this.Key = config.getString("newsapi.key");
        this.url = config.getString("newsapi.url");
    }


    public CompletionStage<Result> profile(Http.Request request, String sourceName, String sourceLink, String searchInput) {

        String encodedSource = sourceName.trim().replaceAll("\\s+", "-").toLowerCase();
        String sortParam = "publishedAt";


        String requestUrl = this.url + "top-headlines?domains=" + encodedSource + "&sources=" + encodedSource + "&apiKey=" + this.Key;

//        return CompletableFuture.failedFuture(new InternalError(requestUrl));

        Client client = new Client(this.ws);

        CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

        //TODO() Fix so you're always getting articles

        return response.thenApply(articles -> {

            if (articles == null || articles.isEmpty()) {
                return ok(views.html.sourceProfile.render(
                        new SourceProfile(sourceName, "No Articles Found for this source", ""),
                        new ArrayList<>(),
                        searchInput,
                        ""
                ));
            }

            List<Article> last10 = articles.stream().limit(10).toList();

            SourceProfile profile = new SourceProfile(
                    sourceName,
                    last10.get(0).getSourceUrl(),
                    "Listing Articles from " + sourceName + "."
            );


            return ok(views.html.sourceProfile.render(profile,last10, searchInput, ""));
        });
    }
}

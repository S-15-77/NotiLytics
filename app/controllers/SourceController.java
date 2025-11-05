package controllers;

import Services.Client;
import com.typesafe.config.Config;
import models.Article;
import models.SourceProfile;
import play.libs.ws.WSClient;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;


import static play.mvc.Results.ok;

public class SourceController {
    private final WSClient ws;
    private final Executor executor;
    private final String Key;
    private final String url;

    @Inject
    public SourceController(WSClient ws, Executor executor, Config config) {
        this.ws = ws;
        this.executor = executor;
        this.Key = config.getString("newsapi.key");
        this.url = config.getString("newsapi.url");
    }

    /**
     * Handles retrieving the last 10 articles of a source for its Profile Page.
     * @param sourceName the name of the selected source.
     * @return the rendered result.
     * @author Team
     */
    public CompletionStage<Result> profile(String sourceName, String id) {
//
//        String encodedSourceName = sourceName.trim().toLowerCase();

        String requestUrl = this.url + "sources=" + (id != null ? id : sourceName) + "&apiKey=" + this.Key;

        Client client = new Client(this.ws);

//        return CompletableFuture.failedFuture(new InternalError(requestUrl));

        CompletionStage<List<Article>> response = client.clientRequest(requestUrl);

        return response.thenApply(articles -> {

            if (articles == null || articles.isEmpty()) {
                return ok(views.html.sourceProfile.render(
                        new SourceProfile(sourceName, "", "No Articles Found for this source at this time. Please try again later!"),
                        new ArrayList<>()
                ));
            }

            List<Article> last10 = articles.stream().limit(10).toList();

            SourceProfile profile = new SourceProfile(
                    sourceName,
                    last10.get(0).getSourceUrl(),
                    "Listing Articles from " + sourceName + "."
            );

            return ok(views.html.sourceProfile.render(profile,last10));
        });

    }


}

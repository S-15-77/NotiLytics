package controllers;

import play.mvc.*;
import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HomeController extends Controller {

    @Inject
    public HomeController() {}

    public CompletionStage<Result> index() {
        // Async response with a simple message
        return CompletableFuture.supplyAsync(() -> ok(views.html.index.render("Welcome to NotiLytics!")));
    }
}

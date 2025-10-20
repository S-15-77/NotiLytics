package Services;
import play.mvc.Http.Request;
import javax.inject.Inject;
import play.libs.ws.*;
import play.mvc.*;
import java.util.concurrent.CompletionStage;
import play.mvc.Controller;

public class Client implements WSBodyReadables, WSBodyWritables {
    private final WSClient ws;

    @Inject
    public Client(WSClient ws) {
        this.ws = ws;
    }

    public CompletionStage<String> clientRequest(String url){
        //No longer Used
        WSRequest request = this.ws.url(url).setRequestTimeout(5000);;
        CompletionStage<? extends WSResponse> responsePromise = request.get();
        return request.get()
                .thenApply(response -> response.getBody())
                .exceptionally(ex -> {return "Request failed: " + ex.getMessage();}); //timout case
    }
    // ...
}
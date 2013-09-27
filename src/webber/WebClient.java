/*
 */
package webber;

import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * WebClient implements general purpose "browser" with limited features
 * based upon the JavaFX {@link javafx.scene.web.WebView WebView}.
 * 
 * @author maxwelld
 */
public class WebClient extends Stage {
    
    private static final String WEB_CLIENT_WIDTH_PARAM = "width";
    
    private static final String WEB_CLIENT_HEIGHT_PARAM = "height";
    
    private static final double WEB_CLIENT_DEFAULT_WIDTH = 800;
            
    private static final double WEB_CLIENT_DEFAULT_HEIGHT = 800;
    
    private WebView webView;


    public WebClient(Webber.Parameters parameters) {
        webView = new WebView();
        double width = parameters.getNamed(WEB_CLIENT_WIDTH_PARAM, WEB_CLIENT_DEFAULT_WIDTH);    
        double height = parameters.getNamed(WEB_CLIENT_HEIGHT_PARAM, WEB_CLIENT_DEFAULT_HEIGHT);
        setScene(new Scene(webView, width, height));
    }
    
    public void load(String url) {
        webView.getEngine().load(url);
    }
}

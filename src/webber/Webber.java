/*
 */
package webber;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;


/**
 * Webber is the primary class of this application.  It is responsible for
 * initialization, startup and shutdown of the application.
 * 
 * @author maxwelld
 */
public class Webber extends Application {
    
    
    private static final String WEBBER_DEFAULT_URL = "http://localhost:0";
  
    private static final String WEBBER_DEFAULT_ICON_NAME = "icon.png";
    
    private static final String WEBBER_DEFAULT_ICON_URL = "webber/icon.png";
    
    private static final String WEBBER_TITLE_PARAM = "title";
    
    private static final String WEBBER_DEFAULT_TITLE = "Webber";
    
    private static final int WEBBER_DEFAULT_ICON_WIDTH = 128;
    
    private static final int WEBBER_DEFAULT_ICON_HEIGHT = 128;
    
    private static final double WEBBER_STATUS_DEFAULT_WIDTH = 400;
    
    private static final double WEBBER_STATUS_DEFAULT_HEIGHT = 150;
    
    private static final String WEBBER_STATUS_STARTING_MESSAGE = "Starting...";
    
    private static final String WEBBER_STATUS_STARTED_MESSAGE = "Started";
    
    private static final String WEBBER_CLASS = Webber.class.getName().replace(".","/")+".class";
    
    private static final Logger logger = Logger.getLogger(Webber.class.getName());
    
    
    public static String getBaseDirectory() {
        URL resource = Webber.class.getClassLoader().getResource(WEBBER_CLASS);
        if( resource == null ) {
            logger.log(Level.WARNING, "Could not find resource: {0}", WEBBER_CLASS);
            return null;
        }

        String protocol = resource.getProtocol();
        
        String base = null;
        if(protocol.equals("jar")) {
            base = resource.getPath().split("!")[0];
            if( base.startsWith("file:") ) {
                base = base.substring(5);
            }
            base = new File(base).getAbsoluteFile().getParent();
        } else {
            logger.log(Level.WARNING, "Resource URI not supported: {0}", resource);
        }

        return base;
    }
    
    public static String getConfigDirectory() {
        String home = System.getProperty("user.home");
        if( home == null ) {
            logger.log(Level.WARNING, "System property, 'user.home', returns null!");
            return null;
        }
        
        File config = new File(home, ".webber");
        if( !config.isDirectory() && !config.mkdir() ) {
            logger.log(Level.WARNING, "Could not create configuration directory {0}", config);
            return null;
        }
        
        return config.getPath();
    }
    
    private static Image getCustomIcon() {
        String base = getBaseDirectory();
        if(base == null) {
            return null;
        }
        File iconFile = new File(base, WEBBER_DEFAULT_ICON_NAME);
        if(!iconFile.isFile()) {
            logger.log(Level.FINEST, "Custom application icon not found: {0}", iconFile);
            return null;
        }
        String iconURL = iconFile.getAbsoluteFile().toURI().toString();
        logger.log(Level.INFO, "Custom application icon found: {0}", iconURL);
        return new Image(iconURL, WEBBER_DEFAULT_ICON_WIDTH, WEBBER_DEFAULT_ICON_HEIGHT, true, true);
    }
    
    private static Image getDefaultIcon() {
        return new Image(WEBBER_DEFAULT_ICON_URL, WEBBER_DEFAULT_ICON_WIDTH, WEBBER_DEFAULT_ICON_HEIGHT, true, true);
    }
    
    
    private Image icon;
    
    private String title;
    
    private Parameters parameters;
    
    private WebServer webServer;
    
    private WebServerStatus webServerStatus;
    

    @Override
    public void start(Stage primaryStage) {
        
        parameters = new Parameters();
        
        icon = getCustomIcon();
        if(icon == null) {
            icon = getDefaultIcon();
        }
        
        title = parameters.getNamed(WEBBER_TITLE_PARAM, WEBBER_DEFAULT_TITLE);
        
        webServer = new WebServer(parameters);
 
        webServerStatus = new WebServerStatus();
        
        
        final EventHandler<KeyEvent> onConsoleShortcutTyped = new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if(event.isControlDown() && "W".equals(event.getCharacter())) {
                    webServer.show();
                }
            }
        };
        
        webServerStatus.addEventFilter(KeyEvent.KEY_TYPED, onConsoleShortcutTyped);
        
        webServer.setOnStarting(new EventHandler<WebServerEvent>() {
            @Override
            public void handle(WebServerEvent t) {
                webServerStatus.setStatus(WEBBER_STATUS_STARTING_MESSAGE);
                webServerStatus.show();
            }
        });
        
        webServer.setOnStarted(new EventHandler<WebServerEvent<Integer>>() {
            @Override
            public void handle(WebServerEvent<Integer> t) {
                
                webServerStatus.setStatus(WEBBER_STATUS_STARTED_MESSAGE);
                
                String webServerPort = ":"+t.getData();
                
                List<String> urls = parameters.getUnnamed();
                
                if( urls.isEmpty() ) {
                    urls = Collections.singletonList(WEBBER_DEFAULT_URL);
                }
                
                for(String url : urls) {
                    WebClient wc = new WebClient(parameters);
                    wc.setTitle(title);
                    wc.getIcons().add(icon);
                    wc.addEventFilter(KeyEvent.KEY_TYPED, onConsoleShortcutTyped);
                    wc.load(url.replace(":0", webServerPort));
                    wc.show();
                }
                
                webServerStatus.hide();
            }
        });
        
        webServer.setOnError(new EventHandler<WebServerEvent<String>>() {
            @Override
            public void handle(WebServerEvent<String> t) {
                webServerStatus.setError(t.getData());
                webServerStatus.show();
            }
        });
        
        webServer.start();
    }

    @Override
    public void stop() {
        try {
            webServer.stopAndWait();
        } catch( InterruptedException e ) {
            // continue without waiting
        }
    }
    
    public class Parameters extends Application.Parameters {
        
        @Override
        public List<String> getRaw() {
            return getParameters().getRaw();
        }

        @Override
        public List<String> getUnnamed() {
            return getParameters().getUnnamed();
        }

        @Override
        public Map<String, String> getNamed() {
            return getParameters().getNamed();
        }
        
        public String getNamed(String name, String defValue) {
            if(getNamed().containsKey(name)) {
                return getNamed().get(name);
            }
            return defValue;
        }
        
        public double getNamed(String name, double defValue) {
            if(getNamed().containsKey(name)) {
                try {
                    return Double.valueOf(getNamed().get(name));
                } catch(NumberFormatException e) {
                    return defValue;
                }
            }
            return defValue;
        }
    }
    
    
    public class WebServerStatus extends Stage {
        
        private Text statusText;
        
        public WebServerStatus() {
            statusText = new Text();
            ImageView iconView = new ImageView(icon);
            
            HBox parent = new HBox();
            parent.getChildren().addAll(iconView, statusText);
            HBox.setMargin(iconView, new Insets(10, 10, 10, 10));
            HBox.setMargin(statusText, new Insets(10, 10, 10, 10));
            parent.setAlignment(Pos.CENTER_LEFT);
                    
            setScene(new Scene(parent, WEBBER_STATUS_DEFAULT_WIDTH, WEBBER_STATUS_DEFAULT_HEIGHT));
            setTitle(title);
            setResizable(false);
            getIcons().add(icon);
        }
        
        public void setStatus(String msg) {
            statusText.setText(msg);
            statusText.setFill(Color.BLACK);
        }
        
        public void setError(String msg) {
            statusText.setText(msg);
            statusText.setFill(Color.RED);
        }
    }
    
    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

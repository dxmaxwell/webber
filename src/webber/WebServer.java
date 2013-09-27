/*
 */
package webber;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
      
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.application.Platform;

import java.util.Queue;
import java.util.LinkedList;


/**
 * WebServer manages an Apache Tomcat web server.  This class is responsible
 * for starting and stopping the web server process, as well as, reading
 * output from the web server process.  This class implements a console
 * window where the output from the web server can be easily viewed
 * for debugging purposes.
 * 
 * @author maxwelld
 */
public class WebServer extends Stage {

    private static final double WEB_SERVER_CONSOLE_WIDTH = 800;
    
    private static final double WEB_SERVER_CONSOLE_HEIGHT = 800;
    
    private static final int WEB_SERVER_CONSOLE_MAX_LINES = 10000;
    
    private static final String WEB_SERVER_PORT_REGEX = "\\[\"http\\-bio\\-.*auto\\-\\d\\-(\\d+)\"\\]";
    
    private static final Logger logger = Logger.getLogger(WebServer.class.getName());
    
    private static File createTempDirectory(File parent, String name) throws IOException {
        Path parentPath = FileSystems.getDefault().getPath(parent.getAbsolutePath());
        Path tempPath = Files.createTempDirectory(parentPath, name);
        return tempPath.toFile();
    }
    
    private static boolean deleteTempDirectory(File directory) {
        if(directory.isDirectory()) {
            for(File f : directory.listFiles()) {
                if(!deleteTempDirectory(f)) {
                    return false;
                }
            }
        }
        return directory.delete();
    }
    
    
    private WebServerConsole wsConsole;
    
    private WebServerExecuterThread wsExecuterThread;
    
    private EventHandler<WebServerEvent> onStarting;
    
    private EventHandler<WebServerEvent<Integer>> onStarted;
    
    private EventHandler<WebServerEvent<String>> onMessage;
    
    private EventHandler<WebServerEvent<String>> onError;
    
    private EventHandler<WebServerEvent> onStopping;
    
    private EventHandler<WebServerEvent> onStopped;
    
    
    public WebServer(Webber.Parameters parameters) {
        wsExecuterThread = new WebServerExecuterThread();
        
        wsConsole = new WebServerConsole(WEB_SERVER_CONSOLE_MAX_LINES);
        
        addEventHandler(WebServerEvent.MESSAGE, new EventHandler<WebServerEvent<String>>() {
            @Override
            public void handle(WebServerEvent<String> event) {
                wsConsole.appendText(event.getData());
            }
        });
        
        setScene(new Scene(wsConsole, WEB_SERVER_CONSOLE_HEIGHT, WEB_SERVER_CONSOLE_WIDTH));
    }
    
    
    public void start() {
        fireOnStarting();
        wsExecuterThread.start();
    }
    
    public void stop() {
        fireOnStopping();
        wsExecuterThread.interrupt();
    }
    
    public void stopAndWait() throws InterruptedException {
        stop();
        wsExecuterThread.join();
    }
    
    private synchronized void fireOnStarting() {
        fireWebServerEvent(onStarting, WebServerEvent.STARTING);
    }
    
    public synchronized void setOnStarting(EventHandler<WebServerEvent> onStarting) {
        this.onStarting = onStarting;
    }
    
    private synchronized void fireOnStarted(int port) {
        fireWebServerEvent(onStarted, WebServerEvent.STARTED, port);
    }
    
    public synchronized void setOnStarted(EventHandler<WebServerEvent<Integer>> onStarted) {
        this.onStarted = onStarted;
    }
    
    
    /**
     * Call the event handler in Application thread with message.
     * 
     * @param message 
     */
    private synchronized void fireOnMessage(String message) {
        fireWebServerEvent(onMessage, WebServerEvent.MESSAGE, message);
    }
    
    public synchronized void setOnMessage(EventHandler<WebServerEvent<String>> onMessage) {
        this.onMessage = onMessage;
    }
    
    private synchronized void fireOnError(String errmsg) {
        fireWebServerEvent(onError, WebServerEvent.ERROR, errmsg);
    }
    
    public synchronized void setOnError(EventHandler<WebServerEvent<String>> onError) {
        this.onError = onError;
    }
    
    private synchronized void fireOnStopping() {
        fireWebServerEvent(onStopping, WebServerEvent.STOPPING);
    }
    
    public synchronized void setOnStopping(EventHandler<WebServerEvent> onStopping) {
        this.onStopping = onStopping;
    }
    
    private synchronized void fireOnStopped() {
        fireWebServerEvent(onStopped, WebServerEvent.STOPPED);
    }
    
    public synchronized void setOnStopped(EventHandler<WebServerEvent> onStopped) {
        this.onStopped = onStopped;
    }
    
    private void fireWebServerEvent( final EventHandler<WebServerEvent> handler, EventType<WebServerEvent> eventType) {
        logger.log(Level.FINEST, "Fire WebServerEvent of type: {0}", eventType);
        final WebServerEvent event = new WebServerEvent(eventType, null);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if(handler != null) {
                    handler.handle(event);
                }
                fireEvent(event);
            }
        });
    }
    
    private <T> void fireWebServerEvent(final EventHandler<WebServerEvent<T>> handler, EventType<WebServerEvent<T>> eventType, T data) {
        logger.log(Level.FINEST, "Fire WebServerEvent of type: {0}: with data: \"{1}\"", new Object[] { eventType, data });
        final WebServerEvent<T> event = new WebServerEvent<>(eventType, data);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if(handler != null) {
                    handler.handle(event);
                }
                fireEvent(event);
            }
        });
    }
    
    private class WebServerConsole extends TextArea {
    
        private int maxLines;
        
        private Queue<Integer> lines = new LinkedList<>();
        
        WebServerConsole(int maxLines) {
            this.maxLines = maxLines;
            setEditable(false);
        }
        
        @Override
        public void appendText(String text) {
            while(lines.size() >= maxLines) {
                deleteText(0, lines.poll());
            }
            lines.offer(text.length()+1);
            super.appendText(text);
            super.appendText("\n");
        }
    }
    
    /**
     * WebServerExecuterThread starts the web server process and waits
     * for it to complete. 'Error' events are published in the case
     * the process cannot be started, or it if stops unexpectedly.
     * 
     */
    private class WebServerExecuterThread extends Thread {
        
        public WebServerExecuterThread() {
            setDaemon(true);
        }
        
        @Override
        public void run() {
        
            String webberBase = Webber.getBaseDirectory();
            if(webberBase == null) {
                fireOnError("Webber base directory not found.");
                return;
            }
            
            File catalinaBase = new File(webberBase, "apache-tomcat-7");
            if(!catalinaBase.isDirectory()) {
                logger.log(Level.WARNING, "Catalina base directory not found: {0}", catalinaBase);
                fireOnError("Catalina base directory not found.");
                return;
            }
          
            File catalinaBin = new File(catalinaBase, "bin");
            if(!catalinaBin.isDirectory()) {
                logger.log(Level.WARNING, "Catalina bin directory not found: {0}", catalinaBin);
                fireOnError("Catalina bin directory not found.");
                return;
            }
            
            File catalinaExe = new File(catalinaBin, "catalina.sh");
            if(!catalinaExe.isFile()) {
                logger.log(Level.WARNING, "Catalina executable not found: {0}", catalinaExe);
                fireOnError("Catalina executable not found.");
                return;
            }
          
            String webberConfig = Webber.getConfigDirectory();
            if(webberConfig == null) {
                fireOnError("Webber configuration directory not found.");
                return;
            }
            
            File webberConfigPath = new File(webberConfig);
            if(!webberConfigPath.isDirectory()) {
                logger.log(Level.WARNING, "Webber configuration directory not found: {0}", webberConfigPath);
                fireOnError("Webber configuration directory not found.");
                return;
            }
            
            File catalinaTmp;
            try {
                catalinaTmp = createTempDirectory(webberConfigPath, "tomcat-");
            } catch(IOException e) {
                logger.log(Level.WARNING, "Catalina temp directory could not be created", e);
                fireOnError("Catalina temp directory not found.");
                return;
            }
            
            List<String> commands = new ArrayList<>();
            commands.add(catalinaExe.getPath());
            commands.add("run");
                        
            Map<String,String> environment = new HashMap<>();
            environment.put("CATALINA_BASE", catalinaBase.getAbsolutePath());
            environment.put("CATALINA_HOME", catalinaBase.getAbsolutePath());
            environment.put("CATALINA_TMPDIR", catalinaTmp.getAbsolutePath());
            
            ProcessBuilder builder = new ProcessBuilder(commands);
            builder.environment().putAll(environment);
            builder.redirectErrorStream(true);

            Process process;
            try {
                process = builder.start();

            } catch(IOException e) {
                logger.log(Level.WARNING, "Catalina could not be started: {0}", builder);
                fireOnError("Catalina could not be started.");
                return;
            }

            WebServerReaderThread wsReaderThread = new WebServerReaderThread(process.getInputStream());
            wsReaderThread.setDaemon(true);
            wsReaderThread.start();

            try {
                int rtn = process.waitFor();
                logger.log(Level.WARNING, "Catalina stopped unexceptedly with status: {0}", rtn);
                fireOnError("Catalina stopped unexpectedly.");
                fireOnStopping();
            } catch( InterruptedException e ) {
                process.destroy();
            }

            try {
                wsReaderThread.join();
            } catch(InterruptedException e) {
                // continue without joining
            }
            
            if(!deleteTempDirectory(catalinaTmp)) {
                logger.log(Level.WARNING, "Catalina temp directory could not deleted: {0}", catalinaTmp);
                fireOnError("Catalina temp directory not deleted.");
            }
            
            fireOnStopped();
        }
    }
    
    /**
     * WebServerReaderThread reads the combined-output from the web server
     * process and publishes a 'message' event for each line received from the 
     * web server.  The most imported function of this class is to discover
     * the port on which the web server is listening and then publishing
     * the 'started' event.  It does this by parsing the output from the
     * web server until it receives a specially formated string.
     * {@see #WEB_SERVER_PORT_REGEX}
     */
    
    private class WebServerReaderThread extends Thread {
         
        private BufferedReader reader = null;
        
        public WebServerReaderThread(InputStream inputStream) {
            reader = new BufferedReader(new InputStreamReader(inputStream));
        }
        
        @Override
        public void run() {
            String line;
            Matcher matcher;
            boolean starting = true;
            
            try {
                while( true ) {
                    line = reader.readLine();
                    if( line == null ) {
                        return; // EOF
                    }
                    if( starting ) {
                        matcher = Pattern.compile(WEB_SERVER_PORT_REGEX).matcher(line);
                        if( matcher.find() ) {       
                            starting = false;
                            fireOnStarted(Integer.valueOf(matcher.group(1)));
                        }
                    }
                    fireOnMessage(line);
                }   
            } catch( IOException e ) {
                // Normal to be thrown when process exits.
            }
        }
    }
}

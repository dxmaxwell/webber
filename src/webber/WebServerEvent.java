/*
 */
package webber;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * WebServerEvent is used to by a {@link webber.WebServer WebServer}
 * publish events within the JavaFX application.
 * 
 * @author maxwelld
 */
public class WebServerEvent<T> extends Event {
    
    public static final EventType<WebServerEvent> ANY = new EventType<>(Event.ANY, "ANY");
    
    public static final EventType<WebServerEvent> STARTING = new EventType<>(ANY, "STARTING");
    
    public static final EventType<WebServerEvent<Integer>> STARTED = new EventType<>(ANY, "STARTED");
    
    public static final EventType<WebServerEvent<String>> MESSAGE = new EventType<>(ANY, "MESSAGE");
    
    public static final EventType<WebServerEvent<String>> ERROR = new EventType<>(ANY, "ERROR");
    
    public static final EventType<WebServerEvent> STOPPING = new EventType<>(ANY, "STOPPING");
    
    public static final EventType<WebServerEvent> STOPPED = new EventType<>(ANY, "STOPPED");
    
    final private T data;

    
    WebServerEvent(EventType<WebServerEvent<T>> eventType, T data) {
        super(eventType);
        this.data = data;
    }

    public T getData() {
        return data;
    }
}

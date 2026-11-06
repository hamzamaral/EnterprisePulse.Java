package dev.enterprisepulse.web.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** STOMP connection to the API's /ws endpoint; forwards /user/queue/notifications to the session's open UIs. */
public final class Realtime {
    private static final Logger log = LoggerFactory.getLogger(Realtime.class);

    private final Session session;
    private final Set<Consumer<JsonNode>> listeners = new CopyOnWriteArraySet<>();
    private volatile StompSession stomp;
    private volatile boolean connecting;

    Realtime(Session session) {
        this.session = session;
    }

    public void subscribe(Consumer<JsonNode> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<JsonNode> listener) {
        listeners.remove(listener);
    }

    public synchronized void start() {
        if ((stomp != null && stomp.isConnected()) || connecting || session.accessToken == null) return;
        connecting = true;
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        String url = ApiClient.baseUrl().replaceFirst("^http", "ws") + "/ws?access_token=" + ApiClient.enc(session.accessToken);
        client.connectAsync(url, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession s, StompHeaders headers) {
                stomp = s;
                connecting = false;
                s.subscribe("/user/queue/notifications", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders h) {
                        return JsonNode.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders h, Object payload) {
                        for (Consumer<JsonNode> l : listeners) l.accept((JsonNode) payload);
                    }
                });
            }

            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                connecting = false;
                log.debug("Realtime transport error: {}", ex.getMessage());
            }
        }).whenComplete((s, ex) -> {
            if (ex != null) {
                connecting = false;
                log.debug("Realtime connect failed: {}", ex.getMessage());
            }
        });
    }

    public synchronized void stop() {
        StompSession s = stomp;
        stomp = null;
        if (s != null && s.isConnected()) {
            try {
                s.disconnect();
            } catch (RuntimeException ignored) {
            }
        }
    }
}

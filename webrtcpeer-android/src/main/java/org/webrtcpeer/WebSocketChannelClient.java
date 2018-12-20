package org.webrtcpeer;

import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.URI;

/**
 *
 */
public class WebSocketChannelClient extends WebSocketClient {

    private static final String TAG = "WebSocketChannelClient";
    private static final int CLOSE_TIMEOUT = 1000;

    @Nullable
    private URI serverUri;
    @Nullable
    private WebSocketChannelEvents events;
    @Nullable
    private WebSocketConnectionState state;
    @Nullable
    private SSLSocketFactory sslSocketFactory;
    @Nullable
    private final Handler handler;
    /**
     * Possible WebSocket connection states.
     */
    public enum WebSocketConnectionState {NEW, CONNECTED, CLOSED, ERROR}

    private final Object closeEventLock = new Object();
    private boolean closeEvent;

    public WebSocketConnectionState getState() {
        return state;
    }

    public WebSocketChannelClient(URI serverUri,
                                  Handler handler,
                                  WebSocketChannelEvents events) {
        super(serverUri);
        this.serverUri = serverUri;
        this.handler = handler;
        this.events = events;
        state = WebSocketConnectionState.NEW;
        setReuseAddr(true);
    }

    @Nullable
    public URI getServerUri() {
        return serverUri;
    }

    public final void setSSLSocketFactory(
            SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    public final SSLSocketFactory getSSLSocketFactory() {
        return this.sslSocketFactory;
    }

    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() !=
                handler.getLooper().getThread()) {
            throw new IllegalStateException("" +
                    "WebSocket method is not called on valid thread");
        }
    }

    public void startConnectWebsocket() {
        checkIfCalledOnValidThread();
        if (state != WebSocketConnectionState.NEW) {
            Log.e(TAG, "WebSocket is already connected.");
            return;
        }
        closeEvent = false;
        if (sslSocketFactory != null) {
            try {
                this.setSocket(sslSocketFactory.createSocket(
                        this.getServerUri().getHost(),
                        this.getServerUri().getPort()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            this.connectBlocking();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void disconnect(boolean waitForComplete) {
        checkIfCalledOnValidThread();
        if (this.getConnection().isOpen()) {
            this.close();
            state = WebSocketConnectionState.CLOSED;
            if (waitForComplete) {
                synchronized (closeEventLock) {
                    while (!closeEvent) {
                        try {
                            closeEventLock.wait(CLOSE_TIMEOUT);
                            break;
                        } catch (InterruptedException e) {
                            Log.e(TAG, "WebSocket wait error: " + e.toString());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onOpen(final ServerHandshake handshakedata) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                state = WebSocketConnectionState.CONNECTED;
                events.onWebSocketOpen(handshakedata);
            }
        });
    }

    @Override
    public void onMessage(final String message) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state ==
                        WebSocketConnectionState.CONNECTED) {
                    events.onWebSocketMessage(message);
                }
            }
        });
    }

    @Override
    public void onClose(final int code, final String reason, final boolean remote) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != WebSocketConnectionState.CLOSED) {
                    state = WebSocketConnectionState.CLOSED;
                    events.onWebSocketClose(code, reason, remote);
                }
            }
        });
    }

    @Override
    public void onError(final Exception ex) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != WebSocketConnectionState.ERROR) {
                    state = WebSocketConnectionState.ERROR;
                    events.onWebSocketError(ex);
                }
            }
        });
    }

    /**
     * Callback interface for messages delivered on WebSocket.
     * All events are dispatched from a looper executor thread.
     */
    public interface WebSocketChannelEvents {

        void onWebSocketOpen(ServerHandshake handshakedata);

        void onWebSocketMessage(String message);

        void onWebSocketClose(int code, String reason, boolean remote);

        void onWebSocketError(Exception ex);
    }
}

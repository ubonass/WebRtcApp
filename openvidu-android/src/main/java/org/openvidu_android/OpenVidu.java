package org.openvidu_android;

import android.support.annotation.Nullable;
import android.util.Log;
import net.minidev.json.JSONObject;
import org.java_websocket.handshake.ServerHandshake;
import org.jsonrpc_ws_android.JsonRpcNotification;
import org.jsonrpc_ws_android.JsonRpcRequest;
import org.jsonrpc_ws_android.JsonRpcResponse;
import org.jsonrpc_ws_android.JsonRpcWebSocketClient;
import org.utilities_android.LooperExecutor;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Vector;

public abstract class OpenVidu implements
        JsonRpcWebSocketClient.WebSocketConnectionEvents {

    private static final String TAG = "OpenVidu";
    @Nullable
    protected JsonRpcWebSocketClient client;
    @Nullable
    protected LooperExecutor executor;
    @Nullable
    protected String wsUri;
    @Nullable
    protected SSLSocketFactory sslSocketFactory;

    public enum Method {JOIN_ROOM, PUBLISH_VIDEO, UNPUBLISH_VIDEO, RECEIVE_VIDEO, STOP_RECEIVE_VIDEO}

    public OpenVidu(LooperExecutor executor,
                    String uri,
                    SSLSocketFactory sslSocketFactory) {
        this.executor = executor;
        this.wsUri = uri;
        this.sslSocketFactory = sslSocketFactory;
    }

    /**
     * Opens a web socket connection to the predefined URI as provided in the constructor.
     * The method responds immediately, whether or not the connection is opened.
     * The method isWebSocketConnected() should be called to ensure that the connection is open.
     */
    public void connectWebSocket() {
        try {
            if (isWebSocketConnected()) {
                return;
            }
            URI uri = new URI(wsUri);
            client = new JsonRpcWebSocketClient(uri,
                    this, executor);
            if (sslSocketFactory != null) {
                client.setSSLSocketFactory(sslSocketFactory);
            }
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        client.connect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception exc) {
            Log.e(TAG, "connectWebSocket", exc);
        }
    }

    /**
     * Method to check if the web socket connection is connected.
     *
     * @return true if the connection state is connected, and false otherwise.
     */
    public boolean isWebSocketConnected() {
        if (client != null) {
            return (client.getConnectionState()
                    .equals(JsonRpcWebSocketClient.WebSocketConnectionState.CONNECTED));
        } else {
            return false;
        }
    }

    /**
     * Attempts to close the web socket connection asynchronously.
     */
    public void disconnectWebSocket(boolean wait) {
        try {
            if (client != null) {
                executor.execute(new Runnable() {
                    public void run() {
                        client.disconnect(wait);
                    }
                });
            }
        } catch (Exception exc) {
            Log.e(TAG, "disconnectWebSocket", exc);
        } finally {
            ;
        }
    }


    /**
     * @param method
     * @param namedParameters
     * @param id
     */
    protected void send(String method,
                        Map<String, Object> namedParameters, int id) {

        try {
            final JsonRpcRequest request = new JsonRpcRequest();
            request.setMethod(method);
            if (namedParameters != null) {
                request.setNamedParams(namedParameters);
            }
            if (id >= 0) {
                request.setId(id);
            }
            executor.execute(new Runnable() {
                public void run() {
                    if (isWebSocketConnected()) {
                        client.sendRequest(request);
                    }
                }
            });
        } catch (Exception exc) {
            Log.e(TAG, "send: " + method, exc);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {

    }

    @Override
    public void onRequest(JsonRpcRequest request) {

    }

    @Override
    public void onResponse(JsonRpcResponse response) {

    }

    @Override
    public void onNotification(JsonRpcNotification notification) {

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

    }

    @Override
    public void onError(Exception e) {

    }
}

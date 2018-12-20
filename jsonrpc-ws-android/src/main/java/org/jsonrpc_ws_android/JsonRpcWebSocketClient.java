/*
 * (C) Copyright 2016 VTT (http://www.vtt.fi)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.jsonrpc_ws_android;


import android.support.annotation.Nullable;
import android.util.Log;
import com.thetransactioncompany.jsonrpc2.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.utilities_android.LooperExecutor;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.URI;

public class JsonRpcWebSocketClient {

    private static final String TAG = "JsonRpcWebSocketClient";
    private static final int CLOSE_TIMEOUT = 1000;
    @Nullable
    private SSLSocketFactory sslSocketFactory;
    @Nullable
    private WebSocketConnectionState connectionState;
    @Nullable
    private WebSocketConnectionEvents events;
    @Nullable
    private ExtendedWebSocketClient client;
    @Nullable
    private LooperExecutor executor;

    private final Object closeEventLock = new Object();
    private boolean closeEvent;

    public enum WebSocketConnectionState {
        CONNECTED, CLOSED, ERROR
    }

    public interface WebSocketConnectionEvents {
        public void onOpen(ServerHandshake handshakedata);

        public void onRequest(JsonRpcRequest request);

        public void onResponse(JsonRpcResponse response);

        public void onNotification(JsonRpcNotification notification);

        public void onClose(int code, String reason, boolean remote);

        public void onError(Exception e);
    }

    public JsonRpcWebSocketClient(URI serverUri,
                                  WebSocketConnectionEvents events,
                                  LooperExecutor executor) {
        this.connectionState = WebSocketConnectionState.CLOSED;
        this.events = events;
        this.executor = executor;
        this.client = new ExtendedWebSocketClient(serverUri, events);
    }

    public void connect() throws IOException {
        checkIfCalledOnValidThread();
        closeEvent = false;
        if (sslSocketFactory != null) {
            client.setSocket(sslSocketFactory.createSocket(
                    client.getServerUri().getHost(),
                    client.getServerUri().getPort()));
        }
        client.connect();
    }

    public void disconnect(boolean waitForComplete) {
        checkIfCalledOnValidThread();

        if (client.getConnection().isOpen()) {
            client.close();
            connectionState = WebSocketConnectionState.CLOSED;

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

    public void sendRequest(JsonRpcRequest request) {
        checkIfCalledOnValidThread();
        client.send(request.toString());
    }

    public void sendNotification(JsonRpcNotification notification) {
        checkIfCalledOnValidThread();
        client.send(notification.toString());
    }

    public WebSocketConnectionState getConnectionState() {
        return connectionState;
    }

    private void checkIfCalledOnValidThread() {
        if (!executor.checkOnLooperThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    public final void setSSLSocketFactory(
            SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    public final SSLSocketFactory getSSLSocketFactory() {
        return this.sslSocketFactory;
    }

    private class ExtendedWebSocketClient extends WebSocketClient {

        @Nullable
        public URI getServerUri() {
            return serverUri;
        }

        @Nullable
        private URI serverUri;

        public ExtendedWebSocketClient(URI serverUri,
                                       WebSocketConnectionEvents events) {
            super(serverUri/*, new Draft_17()*/);
            this.serverUri = serverUri;
        }

        @Override
        public void onOpen(final ServerHandshake handshakedata) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    connectionState = WebSocketConnectionState.CONNECTED;
                    events.onOpen(handshakedata);
                }
            });
        }

        @Override
        public void onClose(final int code, final String reason, final boolean remote) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (connectionState != WebSocketConnectionState.CLOSED) {
                        connectionState = WebSocketConnectionState.CLOSED;
                        events.onClose(code, reason, remote);
                    }
                }
            });
        }

        @Override
        public void onError(final Exception e) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (connectionState != WebSocketConnectionState.ERROR) {
                        connectionState = WebSocketConnectionState.ERROR;
                        events.onError(e);
                    }
                }
            });
        }

        @Override
        public void onMessage(final String message) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (connectionState == WebSocketConnectionState.CONNECTED) {
                        try {
                            JSONRPC2Message msg = JSONRPC2Message.parse(message);
                            if (msg instanceof JSONRPC2Request) {
                                JsonRpcRequest request = new JsonRpcRequest();
                                request.setId(((JSONRPC2Request) msg).getID());
                                request.setMethod(((JSONRPC2Request) msg).getMethod());
                                request.setNamedParams(((JSONRPC2Request) msg).getNamedParams());
                                request.setPositionalParams(((JSONRPC2Request) msg).getPositionalParams());
                                events.onRequest(request);
                            } else if (msg instanceof JSONRPC2Notification) {
                                JsonRpcNotification notification = new JsonRpcNotification();
                                notification.setMethod(((JSONRPC2Notification) msg).getMethod());
                                notification.setNamedParams(((JSONRPC2Notification) msg).getNamedParams());
                                notification.setPositionalParams(((JSONRPC2Notification) msg).getPositionalParams());
                                events.onNotification(notification);
                            } else if (msg instanceof JSONRPC2Response) {
                                JsonRpcResponse notification = new JsonRpcResponse(message);
                                events.onResponse(notification);
                            }
                        } catch (JSONRPC2ParseException e) {
                            // TODO: Handle exception
                        }
                    }
                }
            });
        }
    }
}

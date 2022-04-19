package com.fangte.sdk.ws;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.fangte.sdk.util.KLLog;

public class KLWebSocket {
    // 上层对象
    public KLClient mKLClient = null;
    // 连接对象
    private WebSocketClient mWebSocketClient = null;

    // 打开连接
    boolean openWebSocket(String strUrl) {
        KLLog.e("WebSocketClient openWebSocket = " + strUrl);
        try {
            URI url = new URI(strUrl);
            mWebSocketClient = new WebSocketClient(url) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    KLLog.e("WebSocketClient onOpen = " + handshakedata.getHttpStatus());
                    KLLog.e("WebSocketClient onOpen = " + handshakedata.getHttpStatusMessage());
                    if (mKLClient != null) {
                        mKLClient.onOpen();
                    }
                }

                @Override
                public void onMessage(String message) {
                    KLLog.e("WebSocketClient onMessage = " + message);
                    if (mKLClient != null) {
                        mKLClient.OnDataRecv(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    KLLog.e("WebSocketClient onClose = " + code);
                    KLLog.e("WebSocketClient reason = " + reason);
                    KLLog.e("WebSocketClient remote = " + remote);
                    if (mKLClient != null) {
                        mKLClient.onClose();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    KLLog.e("WebSocketClient onError = " + ex.toString());
                    if (mKLClient != null) {
                        mKLClient.onClose();
                    }
                }
            };
            return mWebSocketClient.connectBlocking();
        } catch (Exception e) {
            e.printStackTrace();
            KLLog.e("WebSocketClient openWebSocket error = " + e);
        }
        return false;
    }

    // 关闭连接
    void closeWebSocket() {
        KLLog.e("WebSocketClient closeWebSocket");
        if (mWebSocketClient != null) {
            mWebSocketClient.close();
            mWebSocketClient = null;
        }
    }

    // 发送数据
    boolean sendData(String strData) {
        if (mWebSocketClient != null && getConnectStatus()) {
            mWebSocketClient.send(strData);
            return true;
        }
        return false;
    }

    // 获取连接状态
    boolean getConnectStatus() {
        if (mWebSocketClient != null) {
            return mWebSocketClient.isOpen();
        }
        return false;
    }
}

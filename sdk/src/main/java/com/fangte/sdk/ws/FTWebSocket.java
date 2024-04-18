package com.fangte.sdk.ws;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.fangte.sdk.util.FTLog;

public class FTWebSocket {
    // 上层对象
    public FTClient mFTClient = null;
    // 连接对象
    private WebSocketClient mWebSocketClient = null;

    // 打开连接
    boolean openWebSocket(String strUrl) {
        FTLog.e("WebSocketClient openWebSocket = " + strUrl);
        try {
            URI url = new URI(strUrl);
            mWebSocketClient = new WebSocketClient(url) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    FTLog.e("WebSocketClient onOpen = " + handshakedata.getHttpStatus());
                    FTLog.e("WebSocketClient onOpen = " + handshakedata.getHttpStatusMessage());
                    if (mFTClient != null) {
                        mFTClient.onOpen();
                    }
                }

                @Override
                public void onMessage(String message) {
                    FTLog.e("WebSocketClient onMessage = " + message);
                    if (mFTClient != null) {
                        mFTClient.OnDataRecv(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    FTLog.e("WebSocketClient onClose = " + code);
                    FTLog.e("WebSocketClient reason = " + reason);
                    FTLog.e("WebSocketClient remote = " + remote);
                    if (mFTClient != null) {
                        mFTClient.onClose();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    FTLog.e("WebSocketClient onError = " + ex.toString());
                    if (mFTClient != null) {
                        mFTClient.onClose();
                    }
                }
            };
            return mWebSocketClient.connectBlocking();
        } catch (Exception e) {
            e.printStackTrace();
            FTLog.e("WebSocketClient openWebSocket error = " + e);
        }
        return false;
    }

    // 关闭连接
    void closeWebSocket() {
        FTLog.e("WebSocketClient closeWebSocket");
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
    private boolean getConnectStatus() {
        if (mWebSocketClient != null) {
            return mWebSocketClient.isOpen();
        }
        return false;
    }
}

package com.fangte.sdk.ws;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fangte.sdk.KLEngine;
import com.fangte.sdk.util.KLLog;

import static com.fangte.sdk.KLBase.*;

public class KLClient {
    // id计数
    private int nCount = 0;
    // 上层对象
    public KLEngine mKLEngine = null;
    // WebSocket 对象
    private final KLWebSocket mKLWebSocket = new KLWebSocket();
    private final ReentrantLock mSocketLock = new ReentrantLock();

    // 当前发送信令类型
    private int nIndex = 0;
    private int nType = -1;
    private int nRespOK = -1;
    private boolean bRespResult = false;

    // 临时变量
    public String sfuId = "";
    public String strMid = "";
    public String strSdp = "";
    public String strSid = "";

    // 标记
    private boolean bClose = false;
    private boolean bConnect = false;

    public void onOpen() {
        bConnect = true;
    }

    public void onClose() {
        bConnect = false;
        if (!bClose && mKLEngine != null) {
            mKLEngine.respSocketEvent();
        }
    }

    // 建立ws连接
    public boolean start(String strUrl) {
        stop();

        bClose = false;
        bConnect = false;
        mKLWebSocket.mKLClient = this;

        mSocketLock.lock();
        boolean bResult = mKLWebSocket.openWebSocket(strUrl);
        mSocketLock.unlock();
        return bResult;
    }

    // 关闭ws连接
    public void stop() {
        bClose = true;
        bConnect = false;
        mSocketLock.lock();
        mKLWebSocket.closeWebSocket();
        mSocketLock.unlock();
    }

    // 返回连接状态
    public boolean getConnect() {
        return bConnect;
    }

    // 分析接收的数据
    void OnDataRecv(String strData) {
        if (bClose) {
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(strData);
            if (jsonObject.has("response")) {
                boolean bResp = jsonObject.getBoolean("response");
                if (bResp && jsonObject.has("id")) {
                    int nId = jsonObject.getInt("id");
                    if (nId == nIndex && jsonObject.has("ok")) {
                        bResp = jsonObject.getBoolean("ok");
                        if (bResp) {
                            nRespOK = 1;
                            if (jsonObject.has("data")) {
                                if (nType == SEND_BIZ_JOIN) {
                                    bRespResult = true;
                                    // 加入房间,解析返回的数据
                                    JSONObject data = jsonObject.getJSONObject("data");
                                    JSONArray users = data.getJSONArray("users");
                                    JSONArray pubs = data.getJSONArray("pubs");
                                    for (int i = 0; i < users.length(); i++) {
                                        JSONObject user = users.getJSONObject(i);
                                        if (user != null) {
                                            mKLEngine.respPeerJoin(user);
                                        }
                                    }
                                    for (int i = 0; i < pubs.length(); i++) {
                                        JSONObject pub = pubs.getJSONObject(i);
                                        if (pub != null) {
                                            mKLEngine.respStreamAdd(pub);
                                        }
                                    }
                                } else if (nType == SEND_BIZ_KEEP) {
                                    bRespResult = true;
                                } else if (nType == SEND_BIZ_PUB) {
                                    // 推流
                                    JSONObject data = jsonObject.getJSONObject("data");
                                    JSONObject jsep = data.getJSONObject("jsep");
                                    if (jsep.has("sdp")) {
                                        strSdp = jsep.getString("sdp");
                                    }
                                    if (data.has("mid")) {
                                        strMid = data.getString("mid");
                                    }
                                    if (data.has("sfuid")) {
                                        sfuId = data.getString("sfuid");
                                    }
                                    bRespResult = true;
                                } else if (nType == SEND_BIZ_SUB) {
                                    // 拉流
                                    JSONObject data = jsonObject.getJSONObject("data");
                                    JSONObject jsep = data.getJSONObject("jsep");
                                    if (jsep.has("sdp")) {
                                        strSdp = jsep.getString("sdp");
                                    }
                                    if (data.has("sid")) {
                                        strSid = data.getString("sid");
                                    }
                                    bRespResult = true;
                                }
                            }
                        } else {
                            nRespOK = 0;
                        }
                    }
                }
            } else if (jsonObject.has("notification")) {
                boolean bResp = jsonObject.getBoolean("notification");
                if (bResp && jsonObject.has("method")) {
                    String strMethod = jsonObject.getString("method");
                    if (strMethod.contains("peer-join")) {
                        if (jsonObject.has("data")) {
                            JSONObject jsonData = jsonObject.getJSONObject("data");
                            mKLEngine.respPeerJoin(jsonData);
                        }
                    }
                    if (strMethod.contains("peer-leave")) {
                        if (jsonObject.has("data")) {
                            JSONObject jsonData = jsonObject.getJSONObject("data");
                            mKLEngine.respPeerLeave(jsonData);
                        }
                    }
                    if (strMethod.contains("stream-add")) {
                        if (jsonObject.has("data")) {
                            JSONObject jsonData = jsonObject.getJSONObject("data");
                            mKLEngine.respStreamAdd(jsonData);
                        }
                    }
                    if (strMethod.contains("stream-remove")) {
                        if (jsonObject.has("data")) {
                            JSONObject jsonData = jsonObject.getJSONObject("data");
                            mKLEngine.respStreamRemove(jsonData);
                        }
                    }
                    if (strMethod.contains("peer-kick")) {
                        if (jsonObject.has("data")) {
                            JSONObject jsonData = jsonObject.getJSONObject("data");
                            mKLEngine.respPeerKick(jsonData);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            KLLog.e("OnDataRecv error = " + e);
        }
    }

    /*
      "request":true
      "id":3764139
      "method":"join"
      "data":{
        "rid":"room"
      }
    */
    // 加入房间
    public boolean SendJoin() {
        if (bClose || !bConnect) {
            return false;
        }
        if (mKLEngine != null && mKLEngine.strRid.equals("")) {
            return false;
        }

        mSocketLock.lock();
        try {
            nCount = new Random().nextInt(9000000) + 1000000;
            nIndex = nCount;

            JSONObject jsonData = new JSONObject();
            jsonData.put("rid", mKLEngine.strRid);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("request", true);
            jsonObject.put("id", nCount);
            jsonObject.put("method", "join");
            jsonObject.put("data", jsonData);
            KLLog.e("SendJoin = " + jsonObject);

            // 发送数据
            nRespOK = -1;
            bRespResult = false;
            nType = SEND_BIZ_JOIN;
            if (mKLWebSocket.sendData(jsonObject.toString())) {
                KLLog.e("SendJoin ok");
                long nStartTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - nStartTime < 5000) {
                    if (nRespOK == 0) {
                        mSocketLock.unlock();
                        return false;
                    }
                    if (nRespOK == 1) {
                        if (bRespResult) {
                            KLLog.e("SendJoin ok2");
                            mSocketLock.unlock();
                            return true;
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } else {
                KLLog.e("SendJoin fail");
            }
            mSocketLock.unlock();
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mSocketLock.unlock();
        return false;
    }

    /*
      "request":true
      "id":3764139
      "method":"leave"
      "data":{
        "rid":"room"
      }
    */
    // 退出房间
    public void SendLeave() {
        if (bClose || !bConnect) {
            return;
        }
        if (mKLEngine != null && mKLEngine.strRid.equals("")) {
            return;
        }

        mSocketLock.lock();
        try {
            nCount = new Random().nextInt(9000000) + 1000000;

            JSONObject jsonData = new JSONObject();
            jsonData.put("rid", mKLEngine.strRid);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("request", true);
            jsonObject.put("id", nCount);
            jsonObject.put("method", "leave");
            jsonObject.put("data", jsonData);
            KLLog.e("SendLeave = " + jsonObject);

            // 发送数据
            mKLWebSocket.sendData(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mSocketLock.unlock();
    }

    /*
      "request":true
      "id":3764139
      "method":"keepalive"
      "data":{
        "rid":"room"
      }
    */
    // 发送心跳
    public boolean SendAlive() {
        if (bClose || !bConnect) {
            return false;
        }
        if (mKLEngine != null && mKLEngine.strRid.equals("")) {
            return false;
        }

        mSocketLock.lock();
        try {
            nCount = new Random().nextInt(9000000) + 1000000;
            nIndex = nCount;

            JSONObject jsonData = new JSONObject();
            jsonData.put("rid", mKLEngine.strRid);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("request", true);
            jsonObject.put("id", nCount);
            jsonObject.put("method", "keepalive");
            jsonObject.put("data", jsonData);
            KLLog.e("SendAlive = " + jsonObject);

            // 发送数据
            nRespOK = -1;
            bRespResult = false;
            nType = SEND_BIZ_KEEP;
            if (mKLWebSocket.sendData(jsonObject.toString())) {
                KLLog.e("SendAlive ok");
                long nStartTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - nStartTime < 5000) {
                    if (nRespOK == 0) {
                        mSocketLock.unlock();
                        return false;
                    }
                    if (nRespOK == 1) {
                        if (bRespResult) {
                            KLLog.e("SendAlive ok2");
                            mSocketLock.unlock();
                            return true;
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } else {
                KLLog.e("SendAlive fail");
            }
            mSocketLock.unlock();
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mSocketLock.unlock();
        return false;
    }

    /*
      "request":true
      "id":3764139
      "method":"publish"
      "data":{
        "rid":"room",
        "jsep":{"type":"offer","sdp":"..."},
        "minfo":{
            "audio":true,
            "video":true,
            "videotype":0
        }
      }
    */
    // 发布流
    public boolean SendPublish(String sdp, boolean bAudio, boolean bVideo, int videoType) {
        if (bClose || !bConnect) {
            return false;
        }
        if (mKLEngine != null && mKLEngine.strRid.equals("")) {
            return false;
        }

        mSocketLock.lock();
        try {
            nCount = new Random().nextInt(9000000) + 1000000;
            nIndex = nCount;

            JSONObject jsonJsep = new JSONObject();
            jsonJsep.put("sdp", sdp);
            jsonJsep.put("type", "offer");

            JSONObject jsonMinfo = new JSONObject();
            jsonMinfo.put("audio", bAudio);
            jsonMinfo.put("video", bVideo);
            jsonMinfo.put("videotype", videoType);

            JSONObject jsonData = new JSONObject();
            jsonData.put("rid", mKLEngine.strRid);
            jsonData.put("jsep", jsonJsep);
            jsonData.put("minfo", jsonMinfo);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("request", true);
            jsonObject.put("id", nCount);
            jsonObject.put("method", "publish");
            jsonObject.put("data", jsonData);
            KLLog.e("SendPublish = " + jsonObject);

            // 发送数据
            nRespOK = -1;
            bRespResult = false;
            nType = SEND_BIZ_PUB;
            if (mKLWebSocket.sendData(jsonObject.toString())) {
                KLLog.e("SendPublish ok");
                long nStartTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - nStartTime < 5000) {
                    if (nRespOK == 0) {
                        mSocketLock.unlock();
                        return false;
                    }
                    if (nRespOK == 1) {
                        if (bRespResult) {
                            KLLog.e("SendPublish ok2");
                            mSocketLock.unlock();
                            return true;
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } else {
                KLLog.e("SendPublish fail");
            }
            mSocketLock.unlock();
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mSocketLock.unlock();
        return false;
    }

    /*
      "request":true
      "id":3764139
      "method":"unpublish"
      "data":{
        "rid":"room",
        "mid":"64236c21-21e8-4a3d-9f80-c767d1e1d67f#ABCDEF",
        "sfuid":"shenzhen-sfu-1", (可选)
      }
    */
    // 取消发布流
    public void SendUnpublish(String mid, String sfuid) {
        if (bClose || !bConnect) {
            return;
        }
        if (mKLEngine != null && mKLEngine.strRid.equals("")) {
            return;
        }

        mSocketLock.lock();
        try {
            nCount = new Random().nextInt(9000000) + 1000000;

            JSONObject jsonData = new JSONObject();
            jsonData.put("rid", mKLEngine.strRid);
            jsonData.put("mid", mid);
            if (!sfuid.equals("")) {
                jsonData.put("sfuid", sfuid);
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("request", true);
            jsonObject.put("id", nCount);
            jsonObject.put("method", "unpublish");
            jsonObject.put("data", jsonData);
            KLLog.e("SendUnpublish = " + jsonObject);

            // 发送数据
            mKLWebSocket.sendData(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mSocketLock.unlock();
    }

    /*
      "request":true
      "id":3764139
      "method":"subscribe"
      "data":{
        "rid":"room",
        "mid":"64236c21-21e8-4a3d-9f80-c767d1e1d67f#ABCDEF",
        "jsep":{"type":"offer","sdp":"..."},
        "sfuid":"shenzhen-sfu-1", (可选)
      }
    */
    // 订阅流
    public boolean SendSubscribe(String sdp, String mid, String sfuid) {
        if (bClose || !bConnect) {
            return false;
        }
        if (mKLEngine != null && mKLEngine.strRid.equals("")) {
            return false;
        }

        mSocketLock.lock();
        try {
            nCount = new Random().nextInt(9000000) + 1000000;
            nIndex = nCount;

            JSONObject jsonJsep = new JSONObject();
            jsonJsep.put("sdp", sdp);
            jsonJsep.put("type", "offer");

            JSONObject jsonData = new JSONObject();
            jsonData.put("rid", mKLEngine.strRid);
            jsonData.put("mid", mid);
            jsonData.put("jsep", jsonJsep);
            if (!sfuid.equals("")) {
                jsonData.put("sfuid", sfuid);
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("request", true);
            jsonObject.put("id", nCount);
            jsonObject.put("method", "subscribe");
            jsonObject.put("data", jsonData);
            KLLog.e("SendSubscribe = " + jsonObject);

            // 发送数据
            nRespOK = -1;
            bRespResult = false;
            nType = SEND_BIZ_SUB;
            if (mKLWebSocket.sendData(jsonObject.toString())) {
                KLLog.e("SendSubscribe ok");
                long nStartTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - nStartTime < 5000) {
                    if (nRespOK == 0) {
                        mSocketLock.unlock();
                        return false;
                    }
                    if (nRespOK == 1) {
                        if (bRespResult) {
                            KLLog.e("SendSubscribe ok2");
                            mSocketLock.unlock();
                            return true;
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } else {
                KLLog.e("SendSubscribe fail");
            }
            mSocketLock.unlock();
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mSocketLock.unlock();
        return false;
    }

    /*
      "request":true
      "id":3764139
      "method":"unsubscribe"
      "data":{
        "rid": "room",
        "mid": "64236c21-21e8-4a3d-9f80-c767d1e1d67f#ABCDEF"
        "sid": "64236c21-21e8-4a3d-9f80-c767d1e1d67f#ABCDEF"
        "sfuid":"shenzhen-sfu-1", (可选)
      }
    */
    // 取消订阅流
    public void SendUnsubscribe(String mid, String sid, String sfuid) {
        if (bClose || !bConnect) {
            return;
        }
        if (mKLEngine != null && mKLEngine.strRid.equals("")) {
            return;
        }

        mSocketLock.lock();
        try {
            nCount = new Random().nextInt(9000000) + 1000000;

            JSONObject jsonData = new JSONObject();
            jsonData.put("rid", mKLEngine.strRid);
            jsonData.put("mid", mid);
            jsonData.put("sid", sid);
            jsonData.put("sfuid", sfuid);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("request", true);
            jsonObject.put("id", nCount);
            jsonObject.put("method", "unsubscribe");
            jsonObject.put("data", jsonData);
            KLLog.e("SendUnsubscribe = " + jsonObject);

            // 发送数据
            mKLWebSocket.sendData(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mSocketLock.unlock();
    }
}

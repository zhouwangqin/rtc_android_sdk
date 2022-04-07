package com.fangte;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.fangte.sdk.KLSdk;
import com.fangte.sdk.listen.KLEvent;
import com.fangte.sdk.util.KLLog;

import org.json.JSONObject;

import java.util.HashMap;

import static com.fangte.DataBase.EVENT_JOIN_ROOM;
import static com.fangte.DataBase.EVENT_LEAVE_ROOM;
import static com.fangte.DataBase.EVENT_SOCKET;
import static com.fangte.DataBase.MEET_JOIN_RESP;
import static com.fangte.DataBase.MEET_LEAVE_RESP;
import static com.fangte.sdk.KLBase.EVENT_JOIN_ROOM_FAIL;
import static com.fangte.sdk.KLBase.EVENT_JOIN_ROOM_OK;
import static com.fangte.sdk.KLBase.EVENT_LEAVE_ROOM_FAIL;
import static com.fangte.sdk.KLBase.EVENT_LEAVE_ROOM_OK;
import static com.fangte.sdk.KLBase.SOCKET_EVENT_CONNECT;
import static com.fangte.sdk.KLBase.SOCKET_EVENT_ERROR;
import static com.fangte.sdk.KLBase.SOCKET_EVENT_UNCONNECT;

public class KLApplication extends Application implements KLEvent {
    // 唯一实例对象
    private static KLApplication mKLApplication = null;
    public static KLApplication getInstance() {
        return mKLApplication;
    }

    // sdk参数
    public String strUid = "";
    public String strRid = "";
    public boolean bSdk = false;

    // 人参数
    static class PeerParam {
        String rid;
        String uid;
        String bizid;
    }

    // 流参数
    static class StreamParam {
        String rid;
        String uid;
        String mid;
        String sfuid;
        JSONObject mInfo;
    }

    // 人对象集合
    public HashMap<String, PeerParam> peerParamHashMap = new HashMap<>();
    // 流对象集合
    public HashMap<String, StreamParam> streamParamHashMap = new HashMap<>();

    @Override
    public void OnSocketEvent(int nCode, String msg) {
        Message message = new Message();
        message.what = EVENT_SOCKET;
        message.arg1 = nCode;
        message.obj = msg;
        mHandler.sendMessage(message);
    }

    @Override
    public void OnJoinRoom(int nCode, String msg) {
        Message message = new Message();
        message.what = EVENT_JOIN_ROOM;
        message.arg1 = nCode;
        message.obj = msg;
        mHandler.sendMessage(message);
    }

    @Override
    public void OnLeaveRoom(int nCode, String msg) {
        Message message = new Message();
        message.what = EVENT_LEAVE_ROOM;
        message.arg1 = nCode;
        message.obj = msg;
        mHandler.sendMessage(message);
    }

    @Override
    public void OnEngineError(String err) {

    }

    @Override
    public void OnPeerJoin(String rid, String uid, String biz) {
        if (!peerParamHashMap.containsKey(uid)) {
            PeerParam peerParam = new PeerParam();
            peerParam.rid = rid;
            peerParam.uid = uid;
            peerParam.bizid = biz;
            peerParamHashMap.put(uid, peerParam);
        }
    }

    @Override
    public void OnPeerLeave(String rid, String uid) {
        peerParamHashMap.remove(uid);
    }

    @Override
    public void OnStreamAdd(String rid, String uid, String mid, String sfu, JSONObject mInfo) {
        if (!streamParamHashMap.containsKey(mid)) {
            StreamParam streamParam = new StreamParam();
            streamParam.rid = rid;
            streamParam.uid = uid;
            streamParam.mid = mid;
            streamParam.sfuid = sfu;
            streamParam.mInfo = mInfo;
            streamParamHashMap.put(mid, streamParam);
        }
    }

    @Override
    public void OnStreamRemove(String rid, String uid, String mid) {
        streamParamHashMap.remove(mid);
    }

    @Override
    public void OnNotify(JSONObject data) {

    }

    @Override
    public void OnPeerKick() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        mKLApplication = this;
        // 增加回调
        peerParamHashMap.clear();
        streamParamHashMap.clear();
        KLSdk.getInstance().setListen(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    @SuppressLint("HandlerLeak")
    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == EVENT_SOCKET) {
                String uid = (String) msg.obj;
                if (msg.arg1 == SOCKET_EVENT_CONNECT) {
                    KLLog.e("用户 " + uid + " 连接成功");
                }
                if (msg.arg1 == SOCKET_EVENT_UNCONNECT) {
                    KLLog.e("用户 " + uid + " 连接断开");
                }
                if (msg.arg1 == SOCKET_EVENT_ERROR) {
                    KLLog.e("用户 " + uid + " 连接出错");
                }
            }
            if (msg.what == EVENT_JOIN_ROOM) {
                String uid = (String) msg.obj;
                if (msg.arg1 == EVENT_JOIN_ROOM_OK) {
                    KLLog.e("用户 " + uid + " 加入房间成功");
                    Toast.makeText(getApplicationContext(), "用户 " + uid + " 加入房间成功", Toast.LENGTH_SHORT).show();
                }
                if (msg.arg1 == EVENT_JOIN_ROOM_FAIL) {
                    KLLog.e("用户 " + uid + " 加入房间失败");
                    Toast.makeText(getApplicationContext(), "用户 " + uid + " 加入房间失败", Toast.LENGTH_SHORT).show();
                }
                // 加入会议结果
                Intent intent = new Intent();
                intent.setAction(MEET_JOIN_RESP);
                intent.putExtra("result", msg.arg1);
                intent.putExtra("obj1", uid);
                sendBroadcast(intent);
            }
            if (msg.what == EVENT_LEAVE_ROOM) {
                String uid = (String) msg.obj;
                if (msg.arg1 == EVENT_LEAVE_ROOM_OK) {
                    KLLog.e("用户 " + uid + " 退出房间成功");
                    Toast.makeText(getApplicationContext(), "用户 " + uid + " 退出房间成功", Toast.LENGTH_SHORT).show();
                }
                if (msg.arg1 == EVENT_LEAVE_ROOM_FAIL) {
                    KLLog.e("用户 " + uid + " 退出房间失败");
                    Toast.makeText(getApplicationContext(), "用户 " + uid + " 退出房间失败", Toast.LENGTH_SHORT).show();
                }
                // 加入会议结果
                Intent intent = new Intent();
                intent.setAction(MEET_LEAVE_RESP);
                intent.putExtra("result", msg.arg1);
                intent.putExtra("obj1", uid);
                sendBroadcast(intent);
            }
        }
    };
}

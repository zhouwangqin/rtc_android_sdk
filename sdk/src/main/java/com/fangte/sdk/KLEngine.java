package com.fangte.sdk;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.fangte.sdk.audio.AppRTCAudioManager;
import com.fangte.sdk.peer.KLPeerLocal;
import com.fangte.sdk.peer.KLPeerRemote;
import com.fangte.sdk.util.KLLog;
import com.fangte.sdk.ws.KLClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.CallSessionFileRotatingLogSink;
import org.webrtc.EglBase;
import org.webrtc.HardwareVideoDecoderFactory;
import org.webrtc.HardwareVideoEncoderFactory;
import org.webrtc.Logging;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static com.fangte.sdk.KLBase.SERVER_IP;
import static com.fangte.sdk.KLBase.SERVER_PORT;
import static com.fangte.sdk.KLBase.RELAY_SERVER_IP;
import static com.fangte.sdk.KLBase.VIDEO_FLEXFEC_FIELDTRIAL;
import static com.fangte.sdk.KLBase.VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;

public class KLEngine {
    // 基本参数
    public String strRid = "";
    public String strUid = "";
    // 上下文对象
    private Activity mContext = null;
    private Handler mHandler = null;

    // 音频设备管理
    private AppRTCAudioManager audioManager = null;
    // 底层日志对象
    private CallSessionFileRotatingLogSink mCallSessionFileRotatingLogSink = null;

    // RTC对象
    public EglBase mEglBase = null;
    public PeerConnectionFactory mPeerConnectionFactory = null;
    public LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    // 线程池
    public static final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    // 信令对象
    public KLClient mKLClient = new KLClient();
    // 推流对象
    public KLPeerLocal klPeerLocal = new KLPeerLocal();
    // 拉流对象
    public HashMap<String, KLPeerRemote> klPeerRemoteHashMap = new HashMap<>();
    public ReentrantLock mMapLock = new ReentrantLock();

    // 心跳线程
    private int nHeartError = 0;
    private boolean bHeart = false;
    private final Runnable HeartThread = new Runnable() {
        @Override
        public void run() {
            mExecutor.execute(() -> {
                if (mKLClient.SendAlive()) {
                    nHeartError = 0;
                } else {
                    nHeartError++;
                }
                if (nHeartError == 2) {
                    // 回调socket断开
                    respSocketEvent();
                }
            });
            mHandler.postDelayed(this, 20000);
        }
    };

    // 设置信令服务器IP
    void setServerIp(String strIp, int nPort) {
        SERVER_IP = strIp;
        SERVER_PORT = nPort;
    }

    // 初始化
    boolean initSdk(String uid) {
        // 获取上下文对象
        initActivity();
        if (mContext == null) {
            return false;
        }
        // 设置参数
        strUid = uid;
        mKLClient.mKLEngine = this;
        klPeerLocal.strUid = strUid;
        klPeerLocal.mKLEngine = this;
        // 初始化RTC
        mEglBase = EglBase.create();
        initPeerConnectionFactory();
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.post(this::initAudioManager);
        return true;
    }

    // 释放资源
    void freeSdk() {
        if (mHandler != null) {
            mHandler.post(this::freeAudioManager);
        }
        freePeerConnectFactory();
        if (mEglBase != null) {
            mEglBase.release();
            mEglBase = null;
        }
        mContext = null;
    }

    // 登陆服务器
    public boolean start() {
        String strUrl = "ws://" + SERVER_IP + ":" + SERVER_PORT + "/ws?peer=" + strUid;
        AtomicBoolean bReturn = new AtomicBoolean(false);
        CountDownLatch mLatch = new CountDownLatch(1);
        mExecutor.execute(() -> {
            bReturn.set(mKLClient.start(strUrl));
            mLatch.countDown();
        });
        try {
            mLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return bReturn.get();
    }

    // 退出服务器
    void stop() {
        if (mHandler != null && bHeart) {
            mHandler.removeCallbacks(HeartThread);
            bHeart = false;
        }
        CountDownLatch mLatch = new CountDownLatch(1);
        mExecutor.execute(() -> {
            mKLClient.stop();
            mLatch.countDown();
        });
        try {
            mLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 加入房间
    boolean JoinRoom(String rid) {
        strRid = rid;
        AtomicBoolean bReturn = new AtomicBoolean(false);
        CountDownLatch mLatch = new CountDownLatch(1);
        mExecutor.execute(() -> {
            bReturn.set(mKLClient.SendJoin());
            if (bReturn.get()) {
                if (mHandler != null) {
                    mHandler.postDelayed(HeartThread, 20000);
                    bHeart = true;
                }
            }
            mLatch.countDown();
        });
        try {
            mLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return bReturn.get();
    }

    // 离开房间
    boolean LeaveRoom() {
        if (mHandler != null && bHeart) {
            mHandler.removeCallbacks(HeartThread);
            bHeart = false;
        }
        if (strRid.equals("")) {
            return true;
        }
        CountDownLatch mLatch = new CountDownLatch(1);
        mExecutor.execute(() -> {
            mKLClient.SendLeave();
            mLatch.countDown();
        });
        try {
            mLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }

    // 推流
    void Publish() {
        if (klPeerLocal != null) {
            klPeerLocal.startPublish();
        }
    }

    // 取消推流
    void UnPublish() {
        if (klPeerLocal != null) {
            klPeerLocal.stopPublish();
        }
    }

    // 订阅
    void Subscribe(String uid, String mid, String sfuId) {
        mMapLock.lock();
        if (klPeerRemoteHashMap.containsKey(mid)) {
            KLPeerRemote klPeerRemote = klPeerRemoteHashMap.get(mid);
            if (klPeerRemote != null) {
                klPeerRemote.startSubscribe();
            }
        } else {
            KLPeerRemote klPeerRemote = new KLPeerRemote();
            klPeerRemote.strUid = uid;
            klPeerRemote.strMid = mid;
            klPeerRemote.sfuId = sfuId;
            klPeerRemote.mKLEngine = this;
            klPeerRemote.startSubscribe();
            klPeerRemoteHashMap.put(mid, klPeerRemote);
        }
        mMapLock.unlock();
    }

    // 取消拉流
    void UnSubscribe(String mid) {
        mMapLock.lock();
        if (klPeerRemoteHashMap.containsKey(mid)) {
            KLPeerRemote klPeerRemote = klPeerRemoteHashMap.get(mid);
            if (klPeerRemote != null) {
                klPeerRemote.stopSubscribe();
            }
            klPeerRemoteHashMap.remove(mid);
        }
        mMapLock.unlock();
    }

    // 设置麦克风Mute
    void setMicrophoneMute(boolean bMute) {
        if (audioManager != null) {
            audioManager.setMicrophoneMute(bMute);
        }
    }

    // 获取麦克风Mute状态
    boolean getMicrophoneMute() {
        if (audioManager != null) {
            return audioManager.getMicrophoneMute();
        }
        return false;
    }

    // 开关扬声器
    void setSpeakerphoneOn(boolean bOpen) {
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(bOpen);
        }
    }

    // 获取扬声器状态
    boolean getSpeakerphoneOn() {
        if (audioManager != null) {
            return audioManager.getSpeakerphoneOn();
        }
        return false;
    }

    // 创建音频设备
    private AudioDeviceModule createAudioDevice() {
        // 设置音频录音错误回调
        JavaAudioDeviceModule.AudioRecordErrorCallback audioRecordErrorCallback = new JavaAudioDeviceModule.AudioRecordErrorCallback() {
            @Override
            public void onWebRtcAudioRecordInitError(String errorMessage) {
                KLLog.e("onWebRtcAudioRecordInitError = " + errorMessage);
                onEngineError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
                KLLog.e("onWebRtcAudioRecordStartError errorCode = " + errorCode);
                KLLog.e("onWebRtcAudioRecordStartError errorMessage = " + errorMessage);
                onEngineError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordError(String errorMessage) {
                KLLog.e("onWebRtcAudioRecordError = " + errorMessage);
                onEngineError(errorMessage);
            }
        };
        // 设置音频播放错误回调
        JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback = new JavaAudioDeviceModule.AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                KLLog.e("onWebRtcAudioTrackInitError = " + errorMessage);
                onEngineError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                KLLog.e("onWebRtcAudioTrackStartError errorCode = " + errorCode);
                KLLog.e("onWebRtcAudioTrackStartError errorMessage = " + errorMessage);
                onEngineError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                KLLog.e("onWebRtcAudioTrackError = " + errorMessage);
                onEngineError(errorMessage);
            }
        };
        JavaAudioDeviceModule.AudioRecordStateCallback audioRecordStateCallback = new JavaAudioDeviceModule.AudioRecordStateCallback() {
            @Override
            public void onWebRtcAudioRecordStart() {
                KLLog.e("Audio recording starts");
            }

            @Override
            public void onWebRtcAudioRecordStop() {
                KLLog.e("Audio recording stops");
            }
        };
        JavaAudioDeviceModule.AudioTrackStateCallback audioTrackStateCallback = new JavaAudioDeviceModule.AudioTrackStateCallback() {
            @Override
            public void onWebRtcAudioTrackStart() {
                KLLog.e("Audio playout starts");
            }

            @Override
            public void onWebRtcAudioTrackStop() {
                KLLog.e("Audio playout stops");
            }
        };
        // 创建音频设备对象
        JavaAudioDeviceModule.Builder mBuilder = JavaAudioDeviceModule.builder(mContext);
        mBuilder.setSamplesReadyCallback(null);
        mBuilder.setUseHardwareNoiseSuppressor(true);
        mBuilder.setUseHardwareAcousticEchoCanceler(true);
        mBuilder.setAudioTrackStateCallback(audioTrackStateCallback);
        mBuilder.setAudioTrackErrorCallback(audioTrackErrorCallback);
        mBuilder.setAudioRecordStateCallback(audioRecordStateCallback);
        mBuilder.setAudioRecordErrorCallback(audioRecordErrorCallback);
        return mBuilder.createAudioDeviceModule();
    }

    // 初始化连接工厂对象
    private void initPeerConnectionFactory() {
        freePeerConnectFactory();
        String fieldTrials = "";
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
        //fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
        PeerConnectionFactory.InitializationOptions.Builder mOptionsBuilder = PeerConnectionFactory.InitializationOptions.builder(mContext);
        mOptionsBuilder.setFieldTrials(fieldTrials);
        mOptionsBuilder.setEnableInternalTracer(false);
        PeerConnectionFactory.initialize(mOptionsBuilder.createInitializationOptions());
        // 创建音频设备
        AudioDeviceModule mAudioDeviceModule = createAudioDevice();
        // 创建工厂对象参数
        PeerConnectionFactory.Options mOptions = new PeerConnectionFactory.Options();
        // 创建工厂对象
        PeerConnectionFactory.Builder mBuilder = PeerConnectionFactory.builder();
        mBuilder.setOptions(mOptions);
        mBuilder.setAudioDeviceModule(mAudioDeviceModule);
        VideoEncoderFactory mVideoEncoderFactory = new HardwareVideoEncoderFactory(mEglBase.getEglBaseContext(), true, false);
        VideoDecoderFactory mVideoDecoderFactory = new HardwareVideoDecoderFactory(mEglBase.getEglBaseContext());
        mBuilder.setVideoEncoderFactory(mVideoEncoderFactory);
        mBuilder.setVideoDecoderFactory(mVideoDecoderFactory);
        mPeerConnectionFactory = mBuilder.createPeerConnectionFactory();
        // 释放音频对象
        mAudioDeviceModule.release();
        // 生成日志文件
        /*
        File mFile = mContext.getExternalFilesDir(null);
        if (mFile != null) {
            mCallSessionFileRotatingLogSink = new CallSessionFileRotatingLogSink(mFile.getAbsolutePath(), 1024 * 1024 * 100, Logging.Severity.LS_VERBOSE);
            Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);
        }*/
        // ICE服务器
        String strStun = "stun:" + RELAY_SERVER_IP + ":3478";
        String strturntcp = "turn:" + RELAY_SERVER_IP + ":3478?transport=tcp";
        String strturnudp = "turn:" + RELAY_SERVER_IP + ":3478?transport=udp";
        // 赋值
        PeerConnection.IceServer turnServer0 = PeerConnection.IceServer.builder(strStun).setUsername("").setPassword("").createIceServer();
        PeerConnection.IceServer turnServer1 = PeerConnection.IceServer.builder(strturntcp).setUsername("demo").setPassword("123456").createIceServer();
        PeerConnection.IceServer turnServer2 = PeerConnection.IceServer.builder(strturnudp).setUsername("demo").setPassword("123456").createIceServer();
        iceServers.clear();
        iceServers.add(turnServer0);
        iceServers.add(turnServer1);
        iceServers.add(turnServer2);
    }

    // 释放连接工厂对象
    private void freePeerConnectFactory() {
        if (mCallSessionFileRotatingLogSink != null) {
            mCallSessionFileRotatingLogSink.dispose();
            mCallSessionFileRotatingLogSink = null;
        }
        if (mPeerConnectionFactory != null) {
            mPeerConnectionFactory.dispose();
            mPeerConnectionFactory = null;
        }
    }

    // 初始化音频设备
    private void initAudioManager() {
        freeAudioManager();
        audioManager = AppRTCAudioManager.create(mContext);
        audioManager.start(this::onAudioManagerDevicesChanged);
    }

    // 释放音频设备
    private void freeAudioManager() {
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(final AppRTCAudioManager.AudioDevice device, final Set<AppRTCAudioManager.AudioDevice> availableDevices) {
        KLLog.e("onAudioManagerDevicesChanged: " + availableDevices + ", " + "selected: " + device);
    }

    // 处理引擎出错
    private void onEngineError(String strDescription) {
        KLLog.e("KCEngine onEngineError = " + strDescription);
    }

    // 获取上层对象
    private void initActivity() {
        try {
            Class<?> class_type = Class.forName("com.unity3d.player.UnityPlayer");
            mContext = (Activity) class_type.getDeclaredField("currentActivity").get(class_type);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 反调用上层对象
    private void callUnity(String argsName) {
        try {
            Class<?> classtype = Class.forName("com.unity3d.player.UnityPlayer");
            Method method = classtype.getMethod("UnitySendMessage", String.class, String.class, String.class);
            method.invoke(classtype, "ReceiveAndroidMsg", "FromAndroid", argsName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 处理socket断开消息
    public void respSocketEvent() {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("to", "audio");
                    obj.put("type", "200");
                    obj.put("data", "");
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 处理有人加入的通知
    // json (rid, uid, biz)
    public void respPeerJoin(JSONObject jsonObject) {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("to", "audio");
                    obj.put("type", "201");
                    obj.put("data", jsonObject);
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 处理有人离开的通知
    // json (rid, uid)
    public void respPeerLeave(JSONObject jsonObject) {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("to", "audio");
                    obj.put("type", "202");
                    obj.put("data", jsonObject);
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 处理有流加入的通知
    // json (rid, uid, mid, sfuid, minfo)
    public void respStreamAdd(JSONObject jsonObject) {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("to", "audio");
                    obj.put("type", "203");
                    obj.put("data", jsonObject);
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 处理有流移除的通知
    // json (rid, uid, mid)
    public void respStreamRemove(JSONObject jsonObject) {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("to", "audio");
                    obj.put("type", "204");
                    obj.put("data", jsonObject);
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 处理被踢下线
    // json (rid, uid)
    public void respPeerKick(JSONObject jsonObject) {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("to", "audio");
                    obj.put("type", "210");
                    obj.put("data", jsonObject);
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 推流回调
    public void OnPeerPublish(boolean bSuc, String error) {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    JSONObject data = new JSONObject();
                    data.put("result", bSuc);
                    data.put("msg", error);

                    obj.put("to", "audio");
                    obj.put("type", "220");
                    obj.put("data", data);
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 推流连接断开回调
    public void OnPeerPublishError() {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("to", "audio");
                    obj.put("type", "221");
                    obj.put("data", "");
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 拉流回调
    public void OnPeerSubscribe(String mid, boolean bSuc, String error) {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    JSONObject data = new JSONObject();
                    data.put("mid", mid);
                    data.put("result", bSuc);
                    data.put("msg", error);

                    obj.put("to", "audio");
                    obj.put("type", "222");
                    obj.put("data", data);
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // 拉流连接断开回调
    public void OnPeerSubscribeError(String mid) {
        if (mHandler != null) {
            mHandler.post(() -> {
                JSONObject obj = new JSONObject();
                try {
                    JSONObject data = new JSONObject();
                    data.put("mid", mid);

                    obj.put("to", "audio");
                    obj.put("type", "223");
                    obj.put("data", data);
                    callUnity(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}

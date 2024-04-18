package com.fangte.sdk;

import static com.fangte.sdk.FTBase.SERVER_IP;
import static com.fangte.sdk.FTBase.SERVER_PORT;
import static com.fangte.sdk.FTBase.RELAY_SERVER_IP;
import static com.fangte.sdk.FTBase.VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import com.fangte.sdk.audio.AppRTCAudioManager;
import com.fangte.sdk.listen.FTListen;
import com.fangte.sdk.listen.FTMedia;
import com.fangte.sdk.listen.FTPeer;
import com.fangte.sdk.peer.FTPeerScreen;
import com.fangte.sdk.peer.FTPeerVideo;
import com.fangte.sdk.peer.FTPeerAudio;
import com.fangte.sdk.peer.FTPeerRemote;
import com.fangte.sdk.util.FTLog;
import com.fangte.sdk.ws.FTClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.HardwareVideoDecoderFactory;
import org.webrtc.HardwareVideoEncoderFactory;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class FTEngine {
    // 基本参数
    public String strRid = "";
    private String strUid = "";
    private String strUrl = "";

    // 上下文对象
    public Context mContext = null;
    public Handler mHandler = null;

    // 回调对象
    public FTListen mFTListen = null;

    // 状态标记
    private int mStatus = 0;
    private boolean bRoomClose = false;

    // 音频推流开关
    private boolean bAudioPub = false;
    // 视频推流开关
    private boolean bVideoPub = false;
    // 屏幕推流开关
    private boolean bScreenPub = false;

    // 麦克风可用标记
    public boolean bAudioEnable = true;
    // 摄像头可用标记
    public boolean bVideoEnable = true;
    // 屏幕共享可用标记
    public boolean bScreenEnable = true;

    // 音频自动拉流开关
    private boolean bAudioSub = false;
    // 音频拉流列表
    private String[] strAudioSubs = null;
    // 视频拉流列表
    private String[] strVideoSubs = null;
    // 屏幕拉流列表
    private String[] strScreenSubs = null;

    // 音频设备管理
    private AppRTCAudioManager audioManager = null;

    // RTC对象
    public EglBase mEglBase = null;
    public PeerConnectionFactory mPeerConnectionFactory = null;
    public LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();

    // 信令对象
    public final FTClient mFTClient = new FTClient();
    // 音频推流对象
    private final FTPeerAudio mFTPeerAudio = new FTPeerAudio();
    // 视频推流对象
    private final FTPeerVideo mFTPeerVideo = new FTPeerVideo();
    // 屏幕推流对象
    private final FTPeerScreen mFTPeerScreen = new FTPeerScreen();
    // 拉流对象
    private final HashMap<String, FTPeerRemote> mFTPeerRemotes = new HashMap<>();
    // 拉流视频渲染对象
    private final HashMap<String, ViewGroup> mVideoViewMap = new HashMap<>();
    // 拉流屏幕渲染对象
    private final HashMap<String, ViewGroup> mScreenViewMap = new HashMap<>();
    // 操作锁
    private final ReentrantLock mMapLock = new ReentrantLock();

    // 心跳线程
    private int nCount = 1;
    private boolean bHeartExit = false;
    private final Runnable HeartThread = () -> {
        FTLog.e("启动心跳线程");
        nCount = 1;
        while (!bHeartExit) {
            if (bRoomClose) {
                FTLog.e("退出心跳线程1");
                return;
            }

            if (mStatus == 0) {
                nCount = 10;
                FTLog.e("重连socket = " + strUrl);
                if (mFTClient.start(strUrl)) {
                    FTLog.e("重连socket成功");
                    if (bHeartExit || bRoomClose) {
                        FTLog.e("退出心跳线程2");
                        return;
                    }

                    FTLog.e("重新加入房间");
                    if (mFTClient.SendJoin()) {
                        FTLog.e("重新加入房间成功");
                        nCount = 200;
                        mStatus = 1;
                    } else {
                        FTLog.e("重新加入房间失败");
                        mFTClient.stop();
                    }

                    if (bHeartExit || bRoomClose) {
                        FTLog.e("退出心跳线程3");
                        return;
                    }
                } else {
                    FTLog.e("重连socket失败");
                    mFTClient.stop();

                    if (bHeartExit || bRoomClose) {
                        FTLog.e("退出心跳线程4");
                        return;
                    }
                }
            } else if (mStatus == 1) {
                FTLog.e("发送心跳");
                nCount = 200;
                mFTClient.SendAlive();
            }

            for (int i = 0; i < nCount; i++) {
                if (bHeartExit || bRoomClose) {
                    FTLog.e("退出心跳线程5");
                    return;
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        FTLog.e("退出心跳线程");
    };

    // 工作线程
    private boolean bWorkExit = false;
    private final Runnable WorkThread = () -> {
        FTLog.e("启动工作线程");
        while (!bWorkExit) {
            if (bRoomClose) {
                FTLog.e("退出工作线程1");
                return;
            }

            if (mStatus == 1) {
                // 判断音频推流
                if (bAudioPub) {
                    if (mFTPeerAudio.nLive == 0) {
                        FTLog.e("启动音频推流");
                        mFTPeerAudio.startPublish();
                    }
                } else {
                    mFTPeerAudio.stopPublish();
                }

                if (bWorkExit || bRoomClose) {
                    FTLog.e("退出工作线程2");
                    return;
                }

                // 判断视频推流
                if (bVideoPub) {
                    if (mFTPeerVideo.nLive == 0) {
                        FTLog.e("启动视频推流");
                        mFTPeerVideo.startPublish();
                    }
                } else {
                    mFTPeerVideo.stopPublish();
                }

                if (bWorkExit || bRoomClose) {
                    FTLog.e("退出工作线程3");
                    return;
                }

                // 判断屏幕推流
                if (bScreenPub) {
                    if (mFTPeerScreen.nLive == 0) {
                        FTLog.e("启动屏幕推流");
                        mFTPeerScreen.startPublish();
                    }
                } else {
                    mFTPeerScreen.stopPublish();
                }

                if (bWorkExit || bRoomClose) {
                    FTLog.e("退出工作线程4");
                    return;
                }

                // 判断拉流
                mMapLock.lock();
                for (Map.Entry<String, FTPeerRemote> remote : mFTPeerRemotes.entrySet()) {
                    if (bWorkExit || bRoomClose) {
                        FTLog.e("退出工作线程5");
                        mMapLock.unlock();
                        return;
                    }

                    FTPeerRemote mFTPeerRemote = remote.getValue();
                    if (mFTPeerRemote != null) {
                        if (mFTPeerRemote.nLive == 0) {
                            if (mFTPeerRemote.bAudio && mFTPeerRemote.audio_type == 0) {
                                if (bAudioSub) {
                                    FTLog.e("启动音频拉流 = " + mFTPeerRemote.strUid);
                                    mFTPeerRemote.startSubscribe();
                                } else {
                                    // 判断该人是否在拉人列表中
                                    if (strAudioSubs != null) {
                                        boolean bHas = false;
                                        for (String strAudioSub : strAudioSubs) {
                                            if (Objects.equals(mFTPeerRemote.strUid, strAudioSub)) {
                                                bHas = true;
                                                break;
                                            }
                                        }
                                        if (bHas) {
                                            FTLog.e("启动音频拉流 = " + mFTPeerRemote.strUid);
                                            mFTPeerRemote.startSubscribe();
                                        } else {
                                            mFTPeerRemote.stopSubscribe();
                                        }
                                    }
                                }
                                continue;
                            }
                            if (mFTPeerRemote.bVideo && mFTPeerRemote.video_type == 0) {
                                // 判断该人是否在拉人列表中
                                if (strVideoSubs != null) {
                                    boolean bHas = false;
                                    for (String strVideoSub : strVideoSubs) {
                                        if (Objects.equals(mFTPeerRemote.strUid, strVideoSub)) {
                                            bHas = true;
                                            break;
                                        }
                                    }
                                    if (bHas) {
                                        if (mFTPeerRemote.mVideoView == null) {
                                            ViewGroup viewGroup = mVideoViewMap.get(mFTPeerRemote.strUid);
                                            if (viewGroup != null) {
                                                FTLog.e("找到掉线前保存的视频渲染窗口，重新设置");
                                                mFTPeerRemote.setVideoRenderer(viewGroup);
                                                mVideoViewMap.remove(mFTPeerRemote.strUid);
                                            }
                                        }

                                        FTLog.e("启动视频拉流 = " + mFTPeerRemote.strUid);
                                        mFTPeerRemote.startSubscribe();
                                    } else {
                                        mFTPeerRemote.stopSubscribe();
                                    }
                                }
                                continue;
                            }
                            if (mFTPeerRemote.bVideo && mFTPeerRemote.video_type == 1) {
                                if (strScreenSubs != null) {
                                    boolean bHas = false;
                                    for (String strScreenSub : strScreenSubs) {
                                        if (Objects.equals(mFTPeerRemote.strUid, strScreenSub)) {
                                            bHas = true;
                                            break;
                                        }
                                    }
                                    if (bHas) {
                                        if (mFTPeerRemote.mVideoView == null) {
                                            ViewGroup viewGroup = mScreenViewMap.get(mFTPeerRemote.strUid);
                                            if (viewGroup != null) {
                                                FTLog.e("找到掉线前保存的屏幕渲染窗口，重新设置");
                                                mFTPeerRemote.setVideoRenderer(viewGroup);
                                                mScreenViewMap.remove(mFTPeerRemote.strUid);
                                            }
                                        }

                                        FTLog.e("启动屏幕拉流 = " + mFTPeerRemote.strUid);
                                        mFTPeerRemote.startSubscribe();
                                    } else {
                                        mFTPeerRemote.stopSubscribe();
                                    }
                                }
                            }
                        }
                    }
                }
                mMapLock.unlock();
            } else {
                FTLog.e("网络故障，停止音频推流");
                mFTPeerAudio.stopPublish();

                if (bWorkExit || bRoomClose) {
                    FTLog.e("退出工作线程6");
                    return;
                }

                FTLog.e("网络故障，停止视频推流");
                mFTPeerVideo.stopPublish();

                if (bWorkExit || bRoomClose) {
                    FTLog.e("退出工作线程7");
                    return;
                }

                FTLog.e("网络故障，停止屏幕推流");
                mFTPeerScreen.stopPublish();

                if (bWorkExit || bRoomClose) {
                    FTLog.e("退出工作线程7");
                    return;
                }

                FTLog.e("网络故障，停止所有拉流");
                mMapLock.lock();
                for (Map.Entry<String, FTPeerRemote> remote : mFTPeerRemotes.entrySet()) {
                    FTPeerRemote mFTPeerRemote = remote.getValue();
                    if (mFTPeerRemote != null) {
                        mFTPeerRemote.stopSubscribe();
                    }

                    if (bWorkExit || bRoomClose) {
                        FTLog.e("退出工作线程8");
                        mMapLock.unlock();
                        return;
                    }
                }
                mFTPeerRemotes.clear();
                mMapLock.unlock();
            }

            for (int i = 0; i < 10; i++) {
                if (bWorkExit || bRoomClose) {
                    FTLog.e("退出工作线程9");
                    return;
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        FTLog.e("退出工作线程");
    };

    /**
     * SDK基础接口
     */

    // 设置信令服务器地址
    // strIp -- 服务器ip,nPort -- 服务器端口
    void setServerIp(String strIp, int nPort) {
        SERVER_IP = strIp;
        SERVER_PORT = nPort;
    }

    // 设置消息回调
    void setSdkListen(FTListen listen) {
        mFTListen = listen;
    }

    // 初始化SDK
    // uid:人的唯一id，Context:上下文对象
    boolean initSdk(String uid, Context context) {
        if (context == null || uid.equals("")) {
            return false;
        }

        // 设置参数
        strUid = uid;
        mContext = context;

        mFTClient.mFTEngine = this;
        mFTPeerAudio.strUid = strUid;
        mFTPeerAudio.mFTEngine = this;
        mFTPeerVideo.strUid = strUid;
        mFTPeerVideo.mFTEngine = this;
        mFTPeerScreen.strUid = strUid;
        mFTPeerScreen.mFTEngine = this;
        // 初始化RTC
        mEglBase = EglBase.create();
        initPeerConnectionFactory();
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.post(this::initAudioManager);
        return true;
    }

    // 释放SDK
    void freeSdk() {
        if (strUid.equals("")) {
            return;
        }

        if (mHandler != null) {
            mHandler.post(this::freeAudioManager);
            mHandler = null;
        }
        freePeerConnectFactory();
        if (mEglBase != null) {
            mEglBase.release();
            mEglBase = null;
        }
        mContext = null;
        strUid = "";
    }

    // 加入房间
    // rid:房间号
    boolean JoinRoom(String rid) {
        if (mContext == null || strUid.equals("") || mPeerConnectionFactory == null) {
            return false;
        }
        if (rid.equals("")) {
            return false;
        }

        strRid = rid;
        strUrl = "ws://" + SERVER_IP + ":" + SERVER_PORT + "/ws?peer=" + strUid;

        bRoomClose = false;
        AtomicBoolean bReturn = new AtomicBoolean(false);
        CountDownLatch mLatch = new CountDownLatch(1);
        new Thread(() -> {
            bReturn.set(false);
            FTLog.e("启动socket连接");
            if (mFTClient.start(strUrl)) {
                FTLog.e("启动socket连接成功");
                FTLog.e("开始加入房间");
                if (mFTClient.SendJoin()) {
                    FTLog.e("加入房间成功");
                    mStatus = 1;
                    // 启动工作线程
                    bWorkExit = false;
                    new Thread(WorkThread).start();
                    // 启动心跳线程
                    bHeartExit = false;
                    new Thread(HeartThread).start();
                    bReturn.set(true);
                } else {
                    FTLog.e("加入房间失败");
                    mStatus = 0;
                    mFTClient.stop();
                }
            } else {
                FTLog.e("启动socket连接失败");
                mStatus = 0;
                mFTClient.stop();
            }
            mLatch.countDown();
        }).start();
        try {
            mLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return bReturn.get();
    }

    // 离开房间
    void LeaveRoom() {
        if (strRid.equals("")) {
            return;
        }
        if (bRoomClose) {
            return;
        }

        mStatus = 0;
        bRoomClose = true;
        FTLog.e("停止线程");
        bHeartExit = true;
        bWorkExit = true;
        FTLog.e("停止所有拉流");
        FreeAllSubscribe();
        FTLog.e("停止屏幕推流");
        mFTPeerScreen.stopPublish();
        FTLog.e("停止视频推流");
        mFTPeerVideo.stopPublish();
        FTLog.e("停止音频推流");
        mFTPeerAudio.stopPublish();

        FTLog.e("停止socket连接");
        CountDownLatch mLatch = new CountDownLatch(1);
        new Thread(() -> {
            mFTClient.SendLeave();
            mFTClient.stop();
            mLatch.countDown();
        }).start();
        try {
            mLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        strRid = "";
    }

    /**
     * 本地音频流接口
     */

    // 设置启动音频推流
    // bPub=YES 启动音频推流
    // bPub=NO  停止音频推流(默认)
    void setAudioPub(boolean bPub) {
        bAudioPub = bPub;
    }

    // 设置麦克风静音
    // bMute=YES 禁麦
    // bMute=NO  正常(默认)
    void setMicrophoneMute(boolean bMute) {
        bAudioEnable = !bMute;
        mFTPeerAudio.setAudioEnable(bAudioEnable);
    }

    // 获取麦克风状态
    boolean getMicrophoneMute() {
        return !bAudioEnable;
    }

    // 设置扬声器
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

    // 设置麦克风增益
    void setMicrophoneVolume(int nVolume) {
        mFTPeerAudio.setAudioVolume(nVolume);
    }

    /**
     * 本地视频流接口
     */

    // 设置启动视频推流
    // bPub=YES 启动视频推流
    // bPub=NO  停止视频推流(默认)
    void setVideoPub(boolean bPub) {
        bVideoPub = bPub;
    }

    // 设置本地视频渲染窗口
    void setVideoLocalView(ViewGroup videoLocalView) {
        mFTPeerVideo.setVideoRenderer(videoLocalView);
    }

    // 设置本地视频质量
    /*
     0 -- 120p  160*120*15   100kbps
     1 -- 240p  320*240*15   200kbps
     2 -- 360p  480*360*15   350kbps
     3 -- 480p  640*480*15   500kbps
     4 -- 540p  960*540*15   1Mbps
     5 -- 720p  1280*720*15  1.5Mbps
     6 -- 1080p 1920*1080*15 2Mbps
     */
    void setVideoLocalLevel(int nLevel) {
        mFTPeerVideo.setVideoLevel(nLevel);
    }

    // 切换前后摄像头
    // nIndex=0 前置(默认)
    // nIndex=1 后置
    void setVideoSwitch(int nIndex) {
        mFTPeerVideo.switchCapture(nIndex);
    }

    // 获取前后摄像头
    // 返回值=0 前置
    // 返回值=1 后置
    public int getVideoSwitch() {
        return mFTPeerVideo.getCaptureIndex();
    }

    // 设置摄像头设备可用或者禁用
    // bEnable=YES 可用(默认)
    // bEnable=NO  禁用
    void setVideoEnable(boolean bEnable) {
        bVideoEnable = bEnable;
        mFTPeerVideo.setVideoEnable(bVideoEnable);
    }

    // 获取摄像头可用状态
    boolean getVideoEnable() {
        return bVideoEnable;
    }

    /**
     * 本地屏幕流接口
     */

    // 设置录屏权限
    // intent 录屏权限
    void setScreenIntent(Intent intent) {
        mFTPeerScreen.setScreenIntent(intent);
    }

    // 设置录屏帧率 ( 5-15 帧)
    void setScreenFrame(int frame) {
        mFTPeerScreen.setScreenFrame(frame);
    }

    // 设置启动屏幕推流
    // bPub=YES 启动屏幕推流
    // bPub=NO  停止屏幕推流(默认)
    void setScreenPub(boolean bPub) {
        bScreenPub = bPub;
    }

    // 设置本地屏幕渲染窗口
    void setScreenLocalView(ViewGroup videoLocalView) {
        mFTPeerScreen.setVideoRenderer(videoLocalView);
    }

    // 设置屏幕设备可用或者禁用
    // bEnable=YES 可用(默认)
    // bEnable=NO  禁用
    public void setScreenEnable(boolean bEnable) {
        bScreenEnable = bEnable;
        mFTPeerScreen.setVideoEnable(bEnable);
    }

    // 获取屏幕设备可用状态
    public boolean getScreenEnable() {
        return bScreenEnable;
    }

    /**
     * 远端音频流接口
     */

    // 设置自动拉所有音频流
    // bSub=YES 自动拉所有的音频流(默认)
    // bSub=NO  不自动拉所有音频流
    void setAudioSub(boolean bSub) {
        bAudioSub = bSub;
    }

    // 设置拉取指定音频流
    // 当上面接口 bSub=NO 时，拉指定人的音频流
    void setAudioSubPeers(String[] uids) {
        strAudioSubs = uids;
    }

    /**
     * 远端视频流接口
     */

    // 设置拉取指定视频流，不会自动拉取视频流
    void setVideoRemoteSubs(String[] subs) {
        strVideoSubs = subs;
    }

    // 设置远端视频流渲染窗口
    void setVideoRemoteView(String sub, ViewGroup videoRemoteView) {
        mMapLock.lock();
        for (Map.Entry<String, FTPeerRemote> remote : mFTPeerRemotes.entrySet()) {
            FTPeerRemote mFTPeerRemote = remote.getValue();
            if (mFTPeerRemote != null) {
                if (mFTPeerRemote.bVideo && mFTPeerRemote.video_type == 0) {
                    if (mFTPeerRemote.strUid.equals(sub)) {
                        mFTPeerRemote.setVideoRenderer(videoRemoteView);
                        break;
                    }
                }
            }
        }
        mMapLock.unlock();
    }

    /**
     * 远端屏幕流接口
     */

    // 设置拉取指定屏幕流，不会自动拉取屏幕流
    void setScreenRemoteSubs(String[] uids) {
        strScreenSubs = uids;
    }

    // 设置远端屏幕流渲染窗口
    void setScreenRemoteView(String sub, ViewGroup videoRemoteView) {
        mMapLock.lock();
        for (Map.Entry<String, FTPeerRemote> remote : mFTPeerRemotes.entrySet()) {
            FTPeerRemote mFTPeerRemote = remote.getValue();
            if (mFTPeerRemote != null) {
                if (mFTPeerRemote.bVideo && mFTPeerRemote.video_type == 1) {
                    if (mFTPeerRemote.strUid.equals(sub)) {
                        mFTPeerRemote.setVideoRenderer(videoRemoteView);
                        break;
                    }
                }
            }
        }
        mMapLock.unlock();
    }

    /**
     * 私有接口
     */

    // 增加拉流
    private void Subscribe(String uid, String mid, String sfuId, boolean bAudio, boolean bVideo, int audio_type, int video_type) {
        mMapLock.lock();
        if (!mFTPeerRemotes.containsKey(mid)) {
            FTPeerRemote mFTPeerRemote = new FTPeerRemote();
            mFTPeerRemote.mFTEngine = this;
            mFTPeerRemote.strUid = uid;
            mFTPeerRemote.strMid = mid;
            mFTPeerRemote.sfuId = sfuId;
            mFTPeerRemote.bAudio = bAudio;
            mFTPeerRemote.bVideo = bVideo;
            mFTPeerRemote.audio_type = audio_type;
            mFTPeerRemote.video_type = video_type;
            mFTPeerRemotes.put(mid, mFTPeerRemote);
        }
        mMapLock.unlock();
    }

    // 取消拉流
    private void UnSubscribe(String mid) {
        mMapLock.lock();
        if (mFTPeerRemotes.containsKey(mid)) {
            FTPeerRemote mFTPeerRemote = mFTPeerRemotes.get(mid);
            if (mFTPeerRemote != null) {
                mFTPeerRemote.stopSubscribe();
            }
            mFTPeerRemotes.remove(mid);
        }
        mMapLock.unlock();
    }

    // 取消所有拉流
    private void FreeAllSubscribe() {
        mMapLock.lock();
        for (Map.Entry<String, FTPeerRemote> remote : mFTPeerRemotes.entrySet()) {
            FTPeerRemote FTPeerRemote = remote.getValue();
            if (FTPeerRemote != null) {
                FTPeerRemote.stopSubscribe();
            }
        }
        mFTPeerRemotes.clear();
        mVideoViewMap.clear();
        mScreenViewMap.clear();
        mMapLock.unlock();
    }

    // 记录掉线前的远端视频渲染窗口
    private void remVideoRemoteViews() {
        mMapLock.lock();
        mVideoViewMap.clear();
        for (Map.Entry<String, FTPeerRemote> remote : mFTPeerRemotes.entrySet()) {
            FTPeerRemote mFTPeerRemote = remote.getValue();
            if (mFTPeerRemote != null) {
                // 判断视频是否有设置渲染窗口
                if (mFTPeerRemote.bVideo && mFTPeerRemote.video_type == 0) {
                    if (mFTPeerRemote.mVideoView != null) {
                        mVideoViewMap.put(mFTPeerRemote.strUid, mFTPeerRemote.mVideoView);
                    }
                }
            }
        }
        mMapLock.unlock();
    }

    // 记录掉线前的远端屏幕渲染窗口
    private void remScreenRemoteViews() {
        // 记录删除前的渲染窗口对象
        mMapLock.lock();
        mScreenViewMap.clear();
        for (Map.Entry<String, FTPeerRemote> remote : mFTPeerRemotes.entrySet()) {
            FTPeerRemote mFTPeerRemote = remote.getValue();
            if (mFTPeerRemote != null) {
                // 判断视频是否有设置渲染窗口
                if (mFTPeerRemote.bVideo && mFTPeerRemote.video_type == 1) {
                    if (mFTPeerRemote.mVideoView != null) {
                        mScreenViewMap.put(mFTPeerRemote.strUid, mFTPeerRemote.mVideoView);
                    }
                }
            }
        }
        mMapLock.unlock();
    }

    // 创建音频设备
    private AudioDeviceModule createAudioDevice() {
        // 设置音频录音错误回调
        JavaAudioDeviceModule.AudioRecordErrorCallback audioRecordErrorCallback = new JavaAudioDeviceModule.AudioRecordErrorCallback() {
            @Override
            public void onWebRtcAudioRecordInitError(String errorMessage) {
                FTLog.e("onWebRtcAudioRecordInitError = " + errorMessage);
                onEngineError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
                FTLog.e("onWebRtcAudioRecordStartError errorCode = " + errorCode);
                FTLog.e("onWebRtcAudioRecordStartError errorMessage = " + errorMessage);
                onEngineError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordError(String errorMessage) {
                FTLog.e("onWebRtcAudioRecordError = " + errorMessage);
                onEngineError(errorMessage);
            }
        };
        // 设置音频播放错误回调
        JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback = new JavaAudioDeviceModule.AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                FTLog.e("onWebRtcAudioTrackInitError = " + errorMessage);
                onEngineError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                FTLog.e("onWebRtcAudioTrackStartError errorCode = " + errorCode);
                FTLog.e("onWebRtcAudioTrackStartError errorMessage = " + errorMessage);
                onEngineError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                FTLog.e("onWebRtcAudioTrackError = " + errorMessage);
                onEngineError(errorMessage);
            }
        };
        JavaAudioDeviceModule.AudioRecordStateCallback audioRecordStateCallback = new JavaAudioDeviceModule.AudioRecordStateCallback() {
            @Override
            public void onWebRtcAudioRecordStart() {
                FTLog.e("Audio recording starts");
            }

            @Override
            public void onWebRtcAudioRecordStop() {
                FTLog.e("Audio recording stops");
            }
        };
        JavaAudioDeviceModule.AudioTrackStateCallback audioTrackStateCallback = new JavaAudioDeviceModule.AudioTrackStateCallback() {
            @Override
            public void onWebRtcAudioTrackStart() {
                FTLog.e("Audio playout starts");
            }

            @Override
            public void onWebRtcAudioTrackStop() {
                FTLog.e("Audio playout stops");
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
        // Disable WebRTC AGC
        //fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
        // Enable H264 FlexFEC
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
        VideoDecoderFactory mVideoDecoderFactory = new DefaultVideoDecoderFactory(mEglBase.getEglBaseContext());
        mBuilder.setVideoEncoderFactory(mVideoEncoderFactory);
        mBuilder.setVideoDecoderFactory(mVideoDecoderFactory);
        mPeerConnectionFactory = mBuilder.createPeerConnectionFactory();
        // 释放音频对象
        mAudioDeviceModule.release();
        // ICE服务器
        String strStun = "stun:" + RELAY_SERVER_IP + ":3478";
        //String strturntcp = "turn:" + RELAY_SERVER_IP + ":3478?transport=tcp";
        //String strturnudp = "turn:" + RELAY_SERVER_IP + ":3478?transport=udp";
        // 赋值
        PeerConnection.IceServer turnServer0 = PeerConnection.IceServer.builder(strStun).setUsername("").setPassword("").createIceServer();
        //PeerConnection.IceServer turnServer1 = PeerConnection.IceServer.builder(strturntcp).setUsername("demo").setPassword("123456").createIceServer();
        //PeerConnection.IceServer turnServer2 = PeerConnection.IceServer.builder(strturnudp).setUsername("demo").setPassword("123456").createIceServer();
        iceServers.clear();
        iceServers.add(turnServer0);
        //iceServers.add(turnServer1);
        //iceServers.add(turnServer2);
        // 增加底层日志输出
        //Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);
    }

    // 释放连接工厂对象
    private void freePeerConnectFactory() {
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
        FTLog.e("onAudioManagerDevicesChanged: " + availableDevices + ", " + "selected: " + device);
    }

    // 处理引擎出错
    private void onEngineError(String strDescription) {
        FTLog.e("KCEngine onEngineError = " + strDescription);
    }

    // 处理socket断开消息
    public void respSocketEvent() {
        new Thread(() -> {
            if (mStatus == 1) {
                FTLog.e("掉线前保存视频渲染窗口");
                remVideoRemoteViews();
                FTLog.e("掉线前保存屏幕渲染窗口");
                remScreenRemoteViews();
            }
            nCount = 10;
            mStatus = 0;
            mFTClient.stop();
        }).start();
    }

    // 处理有人加入的通知
    // json (rid, uid, biz)
    public void respPeerJoin(JSONObject jsonObject) throws JSONException {
        String strRid = "";
        String strUid = "";
        String strBiz = "";

        if (jsonObject.has("rid")) {
            strRid = jsonObject.getString("rid");
        }
        if (jsonObject.has("uid")) {
            strUid = jsonObject.getString("uid");
        }
        if (jsonObject.has("bizid")) {
            strBiz = jsonObject.getString("bizid");
        }

        if (mFTListen != null) {
            FTPeer peer = new FTPeer();
            peer.rid = strRid;
            peer.uid = strUid;
            peer.biz = strBiz;
            mFTListen.OnPeerJoin(peer);
        }
    }

    // 处理有人离开的通知
    // json (rid, uid)
    public void respPeerLeave(JSONObject jsonObject) throws JSONException {
        String strRid = "";
        String strUid = "";

        if (jsonObject.has("rid")) {
            strRid = jsonObject.getString("rid");
        }
        if (jsonObject.has("uid")) {
            strUid = jsonObject.getString("uid");
        }

        if (mFTListen != null) {
            FTPeer peer = new FTPeer();
            peer.rid = strRid;
            peer.uid = strUid;
            peer.biz = "";
            mFTListen.OnPeerLeave(peer);
        }
    }

    // 处理有流加入的通知
    // json (rid, uid, mid, sfuid, minfo)
    public void respStreamAdd(JSONObject jsonObject) {
        if (bRoomClose) {
            return;
        }

        try {
            String strRid = "";
            String strUid = "";
            String strMid = "";
            String strSfu = "";

            boolean bAudio = false;
            boolean bVideo = false;
            int audio_type = 0;
            int video_type = 0;
            if (jsonObject.has("rid")) {
                strRid = jsonObject.getString("rid");
            }
            if (jsonObject.has("uid")) {
                strUid = jsonObject.getString("uid");
            }
            if (jsonObject.has("mid")) {
                strMid = jsonObject.getString("mid");
            }
            if (jsonObject.has("sfuid")) {
                strSfu = jsonObject.getString("sfuid");
            }
            if (jsonObject.has("minfo")) {
                JSONObject jsonMInfo = jsonObject.getJSONObject("minfo");
                if (jsonMInfo.has("audiotype")) {
                    audio_type = jsonMInfo.getInt("audiotype");
                }
                if (jsonMInfo.has("videotype")) {
                    video_type = jsonMInfo.getInt("videotype");
                }
                if (jsonMInfo.has("audio")) {
                    bAudio = jsonMInfo.getBoolean("audio");
                }
                if (jsonMInfo.has("video")) {
                    bVideo = jsonMInfo.getBoolean("video");
                }
            }
            Subscribe(strUid, strMid, strSfu, bAudio, bVideo, audio_type, video_type);

            if (mFTListen != null) {
                FTMedia media = new FTMedia();
                media.rid = strRid;
                media.uid = strUid;
                media.mid = strMid;
                media.bHasAudio = bAudio;
                media.bHasVideo = bVideo;
                media.audio_type = audio_type;
                media.video_type = video_type;
                media.sfu = strSfu;
                mFTListen.OnPeerAddMeida(media);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 处理有流移除的通知
    // json (rid, uid, mid)
    public void respStreamRemove(JSONObject jsonObject) {
        try {
            String strRid = "";
            String strUid = "";
            String strMid = "";
            String strSfu = "";

            boolean bAudio = false;
            boolean bVideo = false;
            int audio_type = 0;
            int video_type = 0;

            if (jsonObject.has("rid")) {
                strRid = jsonObject.getString("rid");
            }
            if (jsonObject.has("uid")) {
                strUid = jsonObject.getString("uid");
            }
            if (jsonObject.has("mid")) {
                strMid = jsonObject.getString("mid");

                if (mFTPeerRemotes.containsKey(strMid)) {
                    FTPeerRemote mFTPeerRemote = mFTPeerRemotes.get(strMid);
                    if (mFTPeerRemote != null) {
                        bAudio = mFTPeerRemote.bAudio;
                        bVideo = mFTPeerRemote.bVideo;
                        audio_type = mFTPeerRemote.audio_type;
                        video_type = mFTPeerRemote.video_type;
                        strSfu = mFTPeerRemote.sfuId;
                    }
                }
            }
            UnSubscribe(strMid);

            if (mFTListen != null) {
                FTMedia media = new FTMedia();
                media.rid = strRid;
                media.uid = strUid;
                media.mid = strMid;
                media.bHasAudio = bAudio;
                media.bHasVideo = bVideo;
                media.audio_type = audio_type;
                media.video_type = video_type;
                media.sfu = strSfu;
                mFTListen.OnPeerRemoveMedia(media);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 处理被踢下线
    // json (rid, uid)
    public void respPeerKick(JSONObject jsonObject) {

    }
}

package com.fangte.sdk;

/*
    KLSdk sdk对外接口类
 */
public class KLSdk {
    // sdk对象
    private final KLEngine mKLEngine = new KLEngine();

    // 设置信令服务器地址
    public void setServerIp(String strIp, int nPort) {
        mKLEngine.setServerIp(strIp, nPort);
    }

    // 设置回调
    public void setListen(KLListen listen) {
        mKLEngine.setListen(listen);
    }

    // 初始化SDK
    public boolean initSdk(String uid) {
        return mKLEngine.initSdk(uid);
    }

    // 释放SDK
    public void freeSdk() {
        mKLEngine.freeSdk();
    }

    // 加入房间
    public boolean joinRoom(String rid) {
        return mKLEngine.JoinRoom(rid);
    }

    // 离开房间
    public void leaveRoom() {
        mKLEngine.LeaveRoom();
    }

    // 设置是否启动音频推流
    public void setPublish(boolean bPub) {
        mKLEngine.setPublish(bPub);
    }

    // 设置是否启动视频
    public void setCamera(boolean bPub) {
        mKLEngine.setCamera(bPub);
    }

    // 设置是否启动视频推流
    public void setCameraPub(boolean bPub) {
        mKLEngine.setCameraPub(bPub);
    }

    // 设置空间音效开关
    public void setAudioLive(boolean bAudio) {
        mKLEngine.setAudioLive(bAudio);
    }

    // 切换前后摄像头
    public void switchCapture(boolean bSwitch) {
        mKLEngine.switchCapture(bSwitch);
    }

    // 设置麦克风
    public void setMicrophoneMute(boolean bMute) {
        mKLEngine.setMicrophoneMute(bMute);
    }

    // 获取麦克风状态
    public boolean getMicrophoneMute() {
        return mKLEngine.getMicrophoneMute();
    }

    // 设置扬声器
    public void setSpeakerphoneOn(boolean bOpen) {
        mKLEngine.setSpeakerphoneOn(bOpen);
    }

    // 获取扬声器状态
    public boolean getSpeakerphoneOn() {
        return mKLEngine.getSpeakerphoneOn();
    }
}

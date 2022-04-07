package com.fangte.sdk;

public class KLSdk {
    // sdk对象
    private final KLEngine mKLEngine = new KLEngine();

    // 设置信令服务器地址
    public void setServerIp(String strIp, int nPort) {
        mKLEngine.setServerIp(strIp, nPort);
    }

    // 初始化SDK
    public boolean initSdk(String uid) {
        return mKLEngine.initSdk(uid);
    }

    // 释放SDK
    public void freeSdk() {
        mKLEngine.freeSdk();
    }

    // 启动
    public boolean start() {
        return mKLEngine.start();
    }

    // 停止
    public void stop() {
        mKLEngine.stop();
    }

    // 返回连接状态
    public boolean getConnect() {
        return mKLEngine.mKLClient.getConnect();
    }

    // 加入房间
    public boolean JoinRoom(String rid) {
        return mKLEngine.JoinRoom(rid);
    }

    // 离开房间
    public boolean leaveRoom() {
        return mKLEngine.LeaveRoom();
    }

    // 开始推流
    void Publish() {
        mKLEngine.Publish();
    }

    // 取消推流
    void UnPublish() {
        mKLEngine.UnPublish();
    }

    // 订阅
    void Subscribe(String uid, String mid, String sfuId) {
        mKLEngine.Subscribe(uid, mid, sfuId);
    }

    // 取消拉流
    void UnSubscribe(String mid) {
        mKLEngine.UnSubscribe(mid);
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

package com.fangte.sdk;

import android.content.Context;
import android.content.Intent;
import android.view.ViewGroup;

import com.fangte.sdk.listen.FTListen;

/**
 *   FTSdk sdk对外接口类
 */
public class FTSdk {
    // sdk对象
    private final FTEngine mFTEngine = new FTEngine();

    /**
     * SDK基础接口
     */

    // 设置信令服务器地址
    // strIp -- 服务器ip,nPort -- 服务器端口
    public void setServerIp(String strIp, int nPort) {
        mFTEngine.setServerIp(strIp, nPort);
    }

    // 设置消息回调
    public void setSdkListen(FTListen listen) {
        mFTEngine.setSdkListen(listen);
    }

    // 初始化SDK
    // uid:人的唯一id，Context:上下文对象
    public boolean initSdk(String uid, Context context) {
        return mFTEngine.initSdk(uid, context);
    }

    // 释放SDK
    public void freeSdk() {
        mFTEngine.freeSdk();
    }

    // 加入房间
    // rid:房间号
    public boolean joinRoom(String rid) {
        return mFTEngine.JoinRoom(rid);
    }

    // 离开房间
    public void leaveRoom() {
        mFTEngine.LeaveRoom();
    }

    /**
     * 本地音频流接口
     */

    // 设置启动音频推流
    // bPub=YES 启动音频推流
    // bPub=NO  停止音频推流(默认)
    public void setAudioPub(boolean bPub) {
        mFTEngine.setAudioPub(bPub);
    }

    // 设置麦克风静音
    // bMute=YES 禁麦
    // bMute=NO  正常(默认)
    public void setMicrophoneMute(boolean bMute) {
        mFTEngine.setMicrophoneMute(bMute);
    }

    // 获取麦克风静音状态
    public boolean getMicrophoneMute() {
        return mFTEngine.getMicrophoneMute();
    }

    // 设置扬声器
    // bOpen=YES 打开扬声器
    // bOpen=NO  关闭扬声器(默认)
    public void setSpeakerphoneOn(boolean bOpen) {
        mFTEngine.setSpeakerphoneOn(bOpen);
    }

    // 获取扬声器状态
    public boolean getSpeakerphoneOn() {
        return mFTEngine.getSpeakerphoneOn();
    }

    // 设置麦克风增益
    // 范围从 0-10
    public void setMicrophoneVolume(int nVolume) {
        mFTEngine.setMicrophoneVolume(nVolume);
    }

    /**
     * 本地视频流接口
     */

    // 设置启动视频推流
    // bPub=YES 启动视频推流
    // bPub=NO  停止视频推流(默认)
    public void setVideoPub(boolean bPub) {
        mFTEngine.setVideoPub(bPub);
    }

    // 设置本地视频渲染窗口
    public void setVideoLocalView(ViewGroup videoLocalView) {
        mFTEngine.setVideoLocalView(videoLocalView);
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
    public void setVideoLocalLevel(int nLevel) {
        mFTEngine.setVideoLocalLevel(nLevel);
    }

    // 切换前后摄像头
    // nIndex=0 前置(默认)
    // nIndex=1 后置
    public void setVideoSwitch(int nIndex) {
        mFTEngine.setVideoSwitch(nIndex);
    }

    // 获取前后摄像头
    // 返回值=0 前置
    // 返回值=1 后置
    public int getVideoSwitch() {
        return mFTEngine.getVideoSwitch();
    }

    // 设置摄像头设备可用或者禁用
    // bEnable=YES 可用(默认)
    // bEnable=NO  禁用
    public void setVideoEnable(boolean bEnable) {
        mFTEngine.setVideoEnable(bEnable);
    }

    // 获取摄像头可用状态
    public boolean getVideoEnable() {
        return mFTEngine.getVideoEnable();
    }

    /**
     * 本地屏幕流接口
     */

    // 设置录屏权限
    // intent 录屏权限
    public void setScreenIntent(Intent intent) {
        mFTEngine.setScreenIntent(intent);
    }

    // 设置录屏帧率 ( 5-15 帧)
    public void setScreenFrame(int frame) {
        mFTEngine.setScreenFrame(frame);
    }

    // 设置启动屏幕推流
    // bPub=YES 启动屏幕推流
    // bPub=NO  停止屏幕推流(默认)
    public void setScreenPub(boolean bPub) {
        mFTEngine.setScreenPub(bPub);
    }

    // 设置本地屏幕渲染窗口
    public void setScreenLocalView(ViewGroup videoLocalView) {
        mFTEngine.setScreenLocalView(videoLocalView);
    }

    // 设置屏幕设备可用或者禁用
    // bEnable=YES 可用(默认)
    // bEnable=NO  禁用
    public void setScreenEnable(boolean bEnable) {
        mFTEngine.setScreenEnable(bEnable);
    }

    // 获取屏幕设备可用状态
    public boolean getScreenEnable() {
        return mFTEngine.getScreenEnable();
    }

    /**
     * 远端音频流接口
     */

    // 设置自动拉所有音频流
    // bSub=YES 自动拉所有的音频流(默认)
    // bSub=NO  不自动拉所有音频流
    public void setAudioSub(boolean bSub) {
        mFTEngine.setAudioSub(bSub);
    }

    // 设置拉取指定音频流
    // 当上面接口 bSub=NO 时，拉指定人的音频流
    public void setAudioSubPeers(String[] uids) {
        mFTEngine.setAudioSubPeers(uids);
    }

    /**
     * 远端视频流接口
     */

    // 设置拉取指定视频流，不会自动拉取视频流
    public void setVideoRemoteSubs(String[] uids) {
        mFTEngine.setVideoRemoteSubs(uids);
    }

    // 设置远端视频流渲染窗口
    public void setVideoRemoteView(String uid, ViewGroup videoRemoteView) {
        mFTEngine.setVideoRemoteView(uid, videoRemoteView);
    }

    /**
     * 远端屏幕流接口
     */

    // 设置拉取指定屏幕流，不会自动拉取屏幕流
    public void setScreenRemoteSubs(String[] uids) {
        mFTEngine.setScreenRemoteSubs(uids);
    }

    // 设置远端屏幕流渲染窗口
    public void setScreenRemoteView(String uid, ViewGroup videoRemoteView) {
        mFTEngine.setScreenRemoteView(uid, videoRemoteView);
    }
}

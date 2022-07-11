package com.fangte.sdk;

/*
    KLListen 回调函数接口
 */
public interface KLListen {
    // sdk日志回调
    void OnDebugLog(String log);
    // 远端音频数据回调
    void OnRemoteAudio(KLAudio frame);
    // 本地视频数据回调
    void OnLocalVideo(KLFrame frame);
    // 远端视频数据回调
    void OnRemoteVideo(KLFrame frame);
}

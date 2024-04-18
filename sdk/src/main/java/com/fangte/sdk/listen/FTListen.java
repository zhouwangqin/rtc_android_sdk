package com.fangte.sdk.listen;

/*
    FTListen 回调函数接口
 */
public interface FTListen {
    // 有人加入房间
    void OnPeerJoin(FTPeer peer);
    // 有人离开房间
    void OnPeerLeave(FTPeer peer);
    // 有人开始推流
    void OnPeerAddMeida(FTMedia media);
    // 有人取消推流
    void OnPeerRemoveMedia(FTMedia media);
}

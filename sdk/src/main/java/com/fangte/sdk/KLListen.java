package com.fangte.sdk;

public interface KLListen {
    void OnLocalVideo(String uid, int video_type, byte[] yb, byte[] ub, byte[] vb, int sy, int su, int sv, int width, int height);
    void OnRemoteVideo(String uid, int video_type, byte[] yb, byte[] ub, byte[] vb, int sy, int su, int sv, int width, int height);
}

package com.fangte.sdk;

public class KLBase {
    /*
        信令发送码
     */

    // 房间
    public static final int SEND_BIZ_JOIN = 10000;
    public static final int SEND_BIZ_LEAVE = 10001;
    public static final int SEND_BIZ_KEEP = 10002;
    // 推流
    public static final int SEND_BIZ_PUB = 10010;
    public static final int SEND_BIZ_UNPUB = 10011;
    // 拉流
    public static final int SEND_BIZ_SUB = 10015;
    public static final int SEND_BIZ_UNSUB = 10016;

    // 信令服务器地址
    static String SERVER_IP = "120.238.78.213";
    static int SERVER_PORT = 10443;
    // 转发服务器地址
    static String RELAY_SERVER_IP = "120.238.78.214";

    // Track参数
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final String VIDEO_TRACK_TYPE = "video";
    // 视频VP8编码参数
    static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
    static final String VIDEO_FLEXFEC_FIELDTRIAL = "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
    // 音频前处理算法参数
    public static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    public static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    public static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    public static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
}

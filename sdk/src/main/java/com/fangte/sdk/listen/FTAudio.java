package com.fangte.sdk.listen;

/*
    FTAudio 音频帧数据pcm
 */
public class FTAudio {
    public String uid;
    public int audio_type;
    public byte[] data;
    public int bits;
    public int samples;
    public int channels;
    public int frames;
    public double audioLevel;
}

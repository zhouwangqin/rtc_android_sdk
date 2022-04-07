package com.fangte.sdk.util;

import android.util.Log;

public class KLLog {
    // TAG
    private final static String TAG = "MY_RTC";
    // e格式打印日志
    public static void e(String msg) {
        if (msg != null) {
            Log.e(TAG, msg);
        }
    }
}

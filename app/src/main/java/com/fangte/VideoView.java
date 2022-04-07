package com.fangte;

import android.widget.RelativeLayout;

import com.fangte.sdk.peer.KLPeerLocal;
import com.fangte.sdk.peer.KLPeerRemote;
import com.fangte.sdk.util.KLLog;

public class VideoView implements KLPeerListen {
    // 参数
    int nIndex = 0;
    boolean bUse = false;
    boolean bLocal = false;
    KLPeerLocal klPeerLocal = null;
    KLPeerRemote klPeerRemote = null;
    RelativeLayout mRelativeLayout = null;

    @Override
    public void onConnectionChange(int nConnect) {
        if (bLocal) {
            KLLog.e("onConnectionChange local = " + klPeerLocal.strMid);
            KLLog.e("onConnectionChange local = " + nConnect);
        } else {
            KLLog.e("onConnectionChange remote = " + klPeerRemote.strMid);
            KLLog.e("onConnectionChange remote = " + nConnect);
        }
    }

    @Override
    public void onPeerConnectionError(String strError) {
        KLLog.e("onPeerConnectionError = " + strError);
    }

    @Override
    public void onPeerPublish(boolean bSuc) {
        KLLog.e("onPeerPublish = " + bSuc);
        if (bSuc && klPeerLocal != null) {
            klPeerLocal.setCapture(true);
        }
    }

    @Override
    public void onPeerSubscribe(boolean bSuc) {
        KLLog.e("onPeerSubscribe = " + bSuc);
    }
}

package com.fangte.sdk.peer;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.fangte.sdk.FTEngine;
import com.fangte.sdk.listen.FTVideo;
import com.fangte.sdk.util.FTLog;

import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RendererCommon;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.YuvHelper;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static com.fangte.sdk.FTBase.VIDEO_TRACK_ID;
import static com.fangte.sdk.FTBase.VIDEO_TRACK_TYPE;
import static org.webrtc.SessionDescription.Type.ANSWER;

public class FTPeerScreen {
    // 参数
    public String strUid = "";
    private String strMid = "";
    private String strSfu = "";
    // 上层对象
    public FTEngine mFTEngine = null;

    // 连接状态
    public int nLive = 0;
    // 退出标记
    private boolean bClose = false;

    // 临时变量
    private SessionDescription localSdp = null;

    // 回调对象
    private final PCObserver pcObserver = new PCObserver();
    private final SDPOfferObserver sdpOfferObserver = new SDPOfferObserver();
    private final SDPAnswerObserver sdpAnswerObserver = new SDPAnswerObserver();

    // peer对象
    private PeerConnection mPeerConnection = null;
    // sdp media 对象
    private MediaConstraints sdpMediaConstraints = null;

    // 录屏参数
    private int nFrame = 8;
    private int nMinBps = 100;
    private int nMaxBps = 1000;

    // 录屏对象
    private Intent mIntent = null;
    private boolean bCapture = false;
    private RtpSender localVideoSender = null;
    private VideoSource mVideoSource = null;
    private VideoTrack mVideoTrack = null;
    private VideoCapturer mVideoCapturer = null;
    private SurfaceTextureHelper mSurfaceTextureHelper = null;

    // 渲染对象
    private ViewGroup mVideoView = null;
    private SurfaceViewRenderer videoRenderer = null;
    private final ProxyVideoSink videoProxy = new ProxyVideoSink();

    // 视频流回调
    private static class ProxyVideoSink implements VideoSink {
        private VideoSink mVideoSink = null;
        synchronized private void setTarget(VideoSink target) {
            mVideoSink = target;
        }

        @Override
        public void onFrame(VideoFrame frame) {
            if (mVideoSink != null) {
                mVideoSink.onFrame(frame);
            }
        }
    }

    // 设置录屏帧率
    public void setScreenFrame(int frame) {
        nFrame = frame;
        if (nFrame > 15) {
            nFrame = 15;
        }
        if (nFrame < 5) {
            nFrame = 5;
        }

        // 判断对应的码率
        if (nFrame < 8) {
            nMinBps = 200;
            nMaxBps = 1000;
        } else {
            nMinBps = 500;
            nMaxBps = 1500;
        }
    }

    // 设置录屏参数
    public void setScreenIntent(Intent intent) {
        mIntent = intent;
    }

    // 设置视频渲染窗口
    public void setVideoRenderer(ViewGroup view) {
        mVideoView = view;
        if (mFTEngine.mHandler != null) {
            mFTEngine.mHandler.post(this::setRenderer);
        }
    }

    // 启动推流
    public void startPublish() {
        if (mFTEngine.mHandler != null) {
            mFTEngine.mHandler.post(this::initRenderer);
        }
        initCapture();
        initPeerConnection();
        createOffer();
    }

    // 取消推流
    public void stopPublish() {
        sendUnPublish();
        freePeerConnection();
        freeCapture();
        if (mFTEngine.mHandler != null) {
            mFTEngine.mHandler.post(this::freeRenderer);
        }
    }

    // 设置屏幕设备可用或者禁用
    public void setVideoEnable(boolean bEnable) {
        if (mVideoTrack != null) {
            mVideoTrack.setEnabled(bEnable);
        }
    }

    // 初始化视频渲染
    private void initRenderer() {
        freeRenderer();
        videoRenderer = new SurfaceViewRenderer(mFTEngine.mContext);
        videoRenderer.init(mFTEngine.mEglBase.getEglBaseContext(), null);
        videoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        videoRenderer.setEnableHardwareScaler(false);
        videoRenderer.setMirror(false);
        videoProxy.setTarget(videoRenderer);
        if (mVideoView != null) {
            mVideoView.addView(videoRenderer);
        }
    }

    // 释放视频渲染
    private void freeRenderer() {
        videoProxy.setTarget(null);
        if (videoRenderer != null) {
            videoRenderer.release();
            videoRenderer = null;
        }
    }

    // 设置渲染对象
    private void setRenderer() {
        mVideoView.removeAllViews();
        if (videoRenderer != null) {
            if (videoRenderer.getParent() != null) {
                ((ViewGroup) videoRenderer.getParent()).removeView(videoRenderer);
            }
            mVideoView.addView(videoRenderer);
        }
    }

    // 初始化屏幕采集
    private void initCapture() {
        freeCapture();
        initScreenCapture();
    }

    // 释放屏幕采集
    private void freeCapture() {
        stopVideoSource();
        freeScreenCapture();
    }

    // 创建录屏采集对象
    private void initScreenCapture() {
        if (mIntent != null) {
            mVideoCapturer = createScreenCapture(mIntent);
        }
    }

    // 释放录屏采集对象
    private void freeScreenCapture() {
        if (mVideoCapturer != null) {
            mVideoCapturer.dispose();
            mVideoCapturer = null;
        }
    }

    // 创建录屏采集对象
    private VideoCapturer createScreenCapture(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new ScreenCapturerAndroid(intent, new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                }
            });
        } else {
            return null;
        }
    }

    // 启动屏幕采集
    private void startVideoSource() {
        if (mVideoCapturer != null && !bCapture) {
            FTLog.e("FTPeerScreen startVideoSource");
            DisplayMetrics displayMetrics = getDisplayMetrics();
            mVideoCapturer.startCapture(displayMetrics.widthPixels, displayMetrics.heightPixels, nFrame);
            bCapture = true;
        }
    }

    // 停止屏幕采集
    private void stopVideoSource() {
        if (mVideoCapturer != null && bCapture) {
            FTLog.e("FTPeerScreen stopVideoSource");
            try {
                mVideoCapturer.stopCapture();
                bCapture = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 获取屏幕的尺寸
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) mFTEngine.mContext.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        }
        return displayMetrics;
    }

    // 创建PeerConnection
    private void initPeerConnection() {
        freePeerConnection();
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        // 创建PeerConnect对象
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(mFTEngine.iceServers);
        rtcConfig.disableIpv6 = true;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        mPeerConnection = mFTEngine.mPeerConnectionFactory.createPeerConnection(rtcConfig, pcObserver);
        // 添加Track
        if (mVideoCapturer != null) {
            createVideoTrack(mVideoCapturer);
            List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
            mPeerConnection.addTrack(mVideoTrack, mediaStreamLabels);
            startVideoSource();
            findVideoSender();
        }
        // 状态赋值
        nLive = 1;
        bClose = false;
    }

    // 删除PeerConnection
    private void freePeerConnection() {
        if (bClose) {
            return;
        }

        nLive = 0;
        bClose = true;
        localSdp = null;
        localVideoSender = null;
        if (mVideoTrack != null) {
            mVideoTrack.dispose();
            mVideoTrack = null;
        }
        if (mVideoSource != null) {
            mVideoSource.dispose();
            mVideoSource = null;
        }
        if (mSurfaceTextureHelper != null) {
            //mSurfaceTextureHelper.dispose();
            mSurfaceTextureHelper = null;
        }
        if (mPeerConnection != null) {
            mPeerConnection.dispose();
            mPeerConnection = null;
        }
    }

    // 创建本地视频Track
    private void createVideoTrack(VideoCapturer videoCapturer) {
        mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mFTEngine.mEglBase.getEglBaseContext());
        mVideoSource = mFTEngine.mPeerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(mSurfaceTextureHelper, mFTEngine.mContext, mVideoSource.getCapturerObserver());
        mVideoTrack = mFTEngine.mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, mVideoSource);
        mVideoTrack.setEnabled(mFTEngine.bScreenEnable);
        mVideoTrack.addSink(videoProxy);
    }

    // 查询视频的Send
    private void findVideoSender() {
        for (RtpSender sender : mPeerConnection.getSenders()) {
            MediaStreamTrack mediaStreamTrack = sender.track();
            if (mediaStreamTrack != null) {
                String trackType = mediaStreamTrack.kind();
                if (trackType.equals(VIDEO_TRACK_TYPE)) {
                    localVideoSender = sender;
                }
            }
        }
    }

    // 设置视频参数
    private void setVideoBitrate() {
        if (mPeerConnection == null || localVideoSender == null) {
            return;
        }

        RtpParameters parameters = localVideoSender.getParameters();
        if (parameters.encodings.size() == 0) {
            FTLog.e("RtpParameters are not ready.");
            return;
        }

        for (RtpParameters.Encoding encoding : parameters.encodings) {
            encoding.minBitrateBps = nMinBps * 1000;
            encoding.maxBitrateBps = nMaxBps * 1000;
            encoding.maxFramerate = 15;
        }

        if (!localVideoSender.setParameters(parameters)) {
            FTLog.e("RtpSender.setParameters failed.");
        }
    }

    // 创建Offer SDP
    private void createOffer() {
        if (mPeerConnection != null) {
            mPeerConnection.createOffer(sdpOfferObserver, sdpMediaConstraints);
        }
    }

    // 推流
    private void sendPublish(final SessionDescription sdp) {
        if (bClose) {
            return;
        }
        new Thread(() -> {
            if (mFTEngine != null && sdp != null) {
                if (mFTEngine.mFTClient.SendPublish(sdp.description, false, true, 0, 1)) {
                    nLive = 2;
                    // 处理返回
                    strMid = mFTEngine.mFTClient.strMid;
                    strSfu = mFTEngine.mFTClient.sfuId;
                    SessionDescription mSessionDescription = new SessionDescription(ANSWER, mFTEngine.mFTClient.strSdp);
                    onRemoteDescription(mSessionDescription);
                    return;
                }
            }
            if (bClose) {
                return;
            }
            nLive = 0;
        }).start();
    }

    // 取消推流
    private void sendUnPublish() {
        if (strMid.equals("")) {
            return;
        }
        new Thread(() -> {
            mFTEngine.mFTClient.SendUnpublish(strMid, strSfu);
            strMid = "";
            strSfu = "";
        }).start();
    }

    // 设置本地offer sdp回调处理
    private void onLocalDescription(final SessionDescription sdp) {
        setVideoBitrate();
        sendPublish(sdp);
    }

    // 接受到远端answer sdp处理
    private void onRemoteDescription(final SessionDescription sdp) {
        FTLog.e("FTPeerScreen setRemoteDescription = " + strMid);
        if (mPeerConnection != null) {
            mPeerConnection.setRemoteDescription(sdpAnswerObserver, sdp);
        }
    }

    // 连接回调
    private class PCObserver implements PeerConnection.Observer {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {

        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            FTLog.e("FTPeerScreen PeerConnection.Observer onConnectionChange = " + newState);
            if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                nLive = 4;
            }
            if (newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                nLive = 0;
            }
            if (newState == PeerConnection.PeerConnectionState.FAILED) {
                nLive = 0;
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {

        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {

        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        @Override
        public void onTrack(RtpTransceiver transceiver) {

        }
    }

    // 设置offer sdp的回调
    private class SDPOfferObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            FTLog.e("FTPeerScreen create offer sdp ok");
            if (bClose) {
                return;
            }
            localSdp = origSdp;
            if (mPeerConnection != null) {
                mPeerConnection.setLocalDescription(sdpOfferObserver, origSdp);
            }
        }

        @Override
        public void onSetSuccess() {
            FTLog.e("FTPeerScreen set offer sdp ok");
            if (bClose) {
                return;
            }
            onLocalDescription(localSdp);
        }

        @Override
        public void onCreateFailure(final String error) {
            FTLog.e("FTPeerScreen create offer sdp fail = " + error);
            nLive = 0;
        }

        @Override
        public void onSetFailure(final String error) {
            FTLog.e("FTPeerScreen offer sdp set error = " + error);
            nLive = 0;
        }
    }

    // 设置answer sdp的回调
    private class SDPAnswerObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {

        }

        @Override
        public void onSetSuccess() {
            FTLog.e("FTPeerScreen set remote sdp ok");
            if (bClose) {
                return;
            }
            nLive = 3;
        }

        @Override
        public void onCreateFailure(final String error) {

        }

        @Override
        public void onSetFailure(final String error) {
            FTLog.e("FTPeerScreen answer sdp set error = " + error);
            nLive = 0;
        }
    }
}

package com.fangte.sdk.peer;

import com.fangte.sdk.FTEngine;
import com.fangte.sdk.util.FTLog;

import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import static org.webrtc.SessionDescription.Type.ANSWER;

import android.view.ViewGroup;

// 拉流对象
public class FTPeerRemote {
    // 当前流标记
    public String strUid = "";
    public String strMid = "";
    public String sfuId = "";
    private String strSid = "";
    // 上层对象
    public FTEngine mFTEngine = null;

    // 音视频标记
    public boolean bAudio = false;
    public boolean bVideo = false;
    public int audio_type = 0;
    public int video_type = 0;

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

    private PeerConnection mPeerConnection = null;
    // sdp media 对象
    private MediaConstraints sdpMediaConstraints = null;

    // 渲染对象
    public ViewGroup mVideoView = null;
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

    // 设置视频渲染窗口
    public void setVideoRenderer(ViewGroup view) {
        mVideoView = view;
        if (bVideo) {
            if (mFTEngine.mHandler != null) {
                mFTEngine.mHandler.post(this::setRenderer);
            }
        }
    }

    // 启动拉流
    public void startSubscribe() {
        if (bVideo) {
            if (mFTEngine.mHandler != null) {
                mFTEngine.mHandler.post(this::initRenderer);
            }
        }
        initPeerConnection();
        createOffer();
    }

    // 取消拉流
    public void stopSubscribe() {
        sendUnSubscribe();
        freePeerConnection();
        if (bVideo) {
            if (mFTEngine.mHandler != null) {
                mFTEngine.mHandler.post(this::freeRenderer);
            }
        }
    }

    // 初始化视频渲染
    private void initRenderer() {
        freeRenderer();
        //
        videoRenderer = new SurfaceViewRenderer(mFTEngine.mContext);
        videoRenderer.init(mFTEngine.mEglBase.getEglBaseContext(), null);
        videoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        videoRenderer.setEnableHardwareScaler(false);
        if (video_type == 0) {
            videoRenderer.setMirror(true);
        }
        if (video_type == 1) {
            videoRenderer.setMirror(false);
        }
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

    // 创建PeerConnection
    private void initPeerConnection() {
        freePeerConnection();
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        // 创建PeerConnect对象
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(mFTEngine.iceServers);
        rtcConfig.disableIpv6 = true;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        mPeerConnection = mFTEngine.mPeerConnectionFactory.createPeerConnection(rtcConfig, pcObserver);
        // 增加接收
        if (mPeerConnection != null) {
            RtpTransceiver.RtpTransceiverInit init = new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
            mPeerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, init);
            mPeerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, init);
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
        if (mPeerConnection != null) {
            mPeerConnection.dispose();
            mPeerConnection = null;
        }
    }

    // 创建Offer SDP
    private void createOffer() {
        if (mPeerConnection != null) {
            mPeerConnection.createOffer(sdpOfferObserver, sdpMediaConstraints);
        }
    }

    // 拉流
    private void sendSubscribe(final SessionDescription sdp) {
        if (bClose) {
            return;
        }
        new Thread(() -> {
            if (mFTEngine != null && sdp != null) {
                if (mFTEngine.mFTClient.SendSubscribe(sdp.description, strMid, sfuId)) {
                    nLive = 2;
                    strSid = mFTEngine.mFTClient.strSid;
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

    // 取消拉流
    private void sendUnSubscribe() {
        if (strSid.equals("")) {
            return;
        }
        new Thread(() -> {
            mFTEngine.mFTClient.SendUnsubscribe(strMid, strSid, sfuId);
            strSid = "";
        }).start();
    }

    // 设置本地offer sdp回调处理
    private void onLocalDescription(final SessionDescription sdp) {
        sendSubscribe(sdp);
    }

    // 接受到远端answer sdp处理
    private void onRemoteDescription(final SessionDescription sdp) {
        FTLog.e("FTPeerRemote setRemoteDescription = " + strMid);
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
            FTLog.e("FTPeerRemote PeerConnection.Observer onConnectionChange = " + newState + ", mid = " + strMid);
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
            if (mediaStream.videoTracks.size() > 0) {
                VideoTrack mVideoTrack = mediaStream.videoTracks.get(0);
                if (mVideoTrack != null) {
                    mVideoTrack.addSink(videoProxy);
                }
            }
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
            if (bClose) {
                return;
            }
            onLocalDescription(localSdp);
        }

        @Override
        public void onCreateFailure(final String error) {
            FTLog.e("FTPeerRemote create offer sdp error = " + error);
            nLive = 0;
        }

        @Override
        public void onSetFailure(final String error) {
            FTLog.e("FTPeerRemote offer sdp set error = " + error);
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
            FTLog.e("FTPeerRemote set remote sdp ok");
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
            FTLog.e("FTPeerRemote answer sdp set error = " + error);
            nLive = 0;
        }
    }
}

package com.fangte.sdk.peer;

import com.fangte.sdk.KLAudio;
import com.fangte.sdk.KLEngine;
import com.fangte.sdk.KLFrame;
import com.fangte.sdk.util.KLLog;

import org.webrtc.AudioSink;
import org.webrtc.AudioTrack;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;
import org.webrtc.YuvHelper;

import java.nio.ByteBuffer;

import static org.webrtc.SessionDescription.Type.ANSWER;

public class KLPeerRemote {
    // 当前流标记
    public String strUid = "";
    public String strMid = "";
    public String sfuId = "";
    public String strSid = "";
    public int audio_type = 0;
    public int video_type = 0;
    // 上层对象
    public KLEngine mKLEngine = null;

    // 连接状态
    public int nLive = 0;
    // 退出标记
    public boolean bClose = false;

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

    // 本地音频处理
    private final KLAudio klAudio = new KLAudio();
    private final ProxyAudioSink audioProxy = new ProxyAudioSink();
    private static class ProxyAudioSink implements AudioSink {
        public KLPeerRemote klPeerRemote = null;

        @Override
        public void onFrame(byte[] data, int bits, int samples, int channels, int frames) {
            if (klPeerRemote != null && klPeerRemote.mKLEngine != null && klPeerRemote.mKLEngine.mKLListen != null) {
                if (frames > 0) {
                    klPeerRemote.klAudio.uid = klPeerRemote.strUid;
                    klPeerRemote.klAudio.audio_type = klPeerRemote.audio_type;
                    klPeerRemote.klAudio.bits = bits;
                    klPeerRemote.klAudio.samples = samples;
                    klPeerRemote.klAudio.channels = channels;
                    klPeerRemote.klAudio.frames = frames;
                    int len = bits / 8 * channels * frames;
                    klPeerRemote.klAudio.data = new byte[len];
                    System.arraycopy(data, 0, klPeerRemote.klAudio.data, 0, len);

                    klPeerRemote.mKLEngine.mKLListen.OnRemoteAudio(klPeerRemote.klAudio);
                }
            }
        }
    }

    // 本地渲染处理
    private final KLFrame klFrame = new KLFrame();
    private final ProxyVideoSink videoProxy = new ProxyVideoSink();

    private static class ProxyVideoSink implements VideoSink {
        public KLPeerRemote klPeerRemote = null;

        @Override
        public void onFrame(VideoFrame frame) {
            if (klPeerRemote != null && klPeerRemote.mKLEngine != null && klPeerRemote.mKLEngine.mKLListen != null) {
                if (frame != null) {
                    if (frame.getBuffer() != null) {
                        VideoFrame.I420Buffer i420Buffer = frame.getBuffer().toI420();
                        if (i420Buffer != null) {
                            int nWidth = i420Buffer.getWidth();
                            int nHeight = i420Buffer.getHeight();
                            ByteBuffer dst = ByteBuffer.allocateDirect(nWidth * nHeight * 3 / 2);

                            YuvHelper.I420Copy(i420Buffer.getDataY(), i420Buffer.getStrideY(),
                                    i420Buffer.getDataU(), i420Buffer.getStrideU(),
                                    i420Buffer.getDataV(), i420Buffer.getStrideV(), dst, nWidth, nHeight);

                            int nYlen = nWidth * nHeight;
                            int nUlen = nWidth * nHeight / 4;
                            int nVlen = nWidth * nHeight / 4;
                            byte[] ybytes = new byte[nYlen];
                            byte[] ubytes = new byte[nUlen];
                            byte[] vbytes = new byte[nVlen];

                            dst.position(0);
                            dst.get(ybytes);
                            dst.get(ubytes);
                            dst.get(vbytes);

                            klPeerRemote.klFrame.uid = klPeerRemote.strUid;
                            klPeerRemote.klFrame.video_type = klPeerRemote.video_type;
                            klPeerRemote.klFrame.sy = i420Buffer.getStrideY();
                            klPeerRemote.klFrame.su = i420Buffer.getStrideU();
                            klPeerRemote.klFrame.sv = i420Buffer.getStrideV();
                            klPeerRemote.klFrame.yb = new byte[nYlen];
                            klPeerRemote.klFrame.ub = new byte[nUlen];
                            klPeerRemote.klFrame.vb = new byte[nVlen];
                            klPeerRemote.klFrame.width = i420Buffer.getWidth();
                            klPeerRemote.klFrame.height = i420Buffer.getHeight();
                            System.arraycopy(ybytes, 0, klPeerRemote.klFrame.yb, 0, nYlen);
                            System.arraycopy(ubytes, 0, klPeerRemote.klFrame.ub, 0, nUlen);
                            System.arraycopy(vbytes, 0, klPeerRemote.klFrame.vb, 0, nVlen);
                            klPeerRemote.mKLEngine.mKLListen.OnRemoteVideo(klPeerRemote.klFrame);

                            i420Buffer.release();
                        }
                    }
                }
            }
        }
    }

    // 返回远端音频Track
    private AudioTrack getRemoteAudioTrack() {
        for (RtpTransceiver transceiver : mPeerConnection.getTransceivers()) {
            MediaStreamTrack track = transceiver.getReceiver().track();
            if (track instanceof AudioTrack) {
                return (AudioTrack) track;
            }
        }
        return null;
    }

    // 返回远端视频Track
    private VideoTrack getRemoteVideoTrack() {
        for (RtpTransceiver transceiver : mPeerConnection.getTransceivers()) {
            MediaStreamTrack track = transceiver.getReceiver().track();
            if (track instanceof VideoTrack) {
                return (VideoTrack) track;
            }
        }
        return null;
    }

    // 启动拉流
    public void startSubscribe() {
        initPeerConnection();
        createOffer();
    }

    // 取消拉流
    public void stopSubscribe() {
        sendUnSubscribe();
        freePeerConnection();
    }

    // 创建PeerConnection
    private void initPeerConnection() {
        freePeerConnection();
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        // 创建PeerConnect对象
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(mKLEngine.iceServers);
        rtcConfig.disableIpv6 = true;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        mPeerConnection = mKLEngine.mPeerConnectionFactory.createPeerConnection(rtcConfig, pcObserver);
        // 增加接收
        if (mPeerConnection != null) {
            RtpTransceiver.RtpTransceiverInit init = new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
            mPeerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, init);
            mPeerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, init);
            videoProxy.klPeerRemote = this;
            audioProxy.klPeerRemote = this;
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
            if (mKLEngine != null && mKLEngine.mKLClient != null && sdp != null) {
                if (mKLEngine.mKLClient.SendSubscribe(sdp.description, strMid, sfuId)) {
                    nLive = 2;
                    strSid = mKLEngine.mKLClient.strSid;
                    SessionDescription mSessionDescription = new SessionDescription(ANSWER, mKLEngine.mKLClient.strSdp);
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
            mKLEngine.mKLClient.SendUnsubscribe(strMid, strSid, sfuId);
            strSid = "";
        }).start();
    }

    // 设置本地offer sdp回调处理
    private void onLocalDescription(final SessionDescription sdp) {
        sendSubscribe(sdp);
    }

    // 接受到远端answer sdp处理
    private void onRemoteDescription(final SessionDescription sdp) {
        KLLog.e("KLPeerRemote setRemoteDescription = " + strMid);
        if (mPeerConnection != null) {
            mPeerConnection.setRemoteDescription(sdpAnswerObserver, sdp);
        }
    }

    // 连接回调
    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onSignalingChange = " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onIceConnectionChange = " + iceConnectionState);
        }

        @Override
        public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onStandardizedIceConnectionChange = " + newState);
        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            KLLog.e("KLPeerRemote PeerConnection.Observer onConnectionChange = " + newState + ", mid = " + strMid);
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
            //KLLog.e("KLPeerRemote PeerConnection.Observer onIceConnectionReceivingChange = " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onIceGatheringChange = " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onIceCandidate = " + iceCandidate.toString());
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            /*
            for (IceCandidate iceCandidate : iceCandidates) {
                KLLog.e("KLPeerRemote PeerConnection.Observer onIceCandidatesRemoved = " + iceCandidate.toString());
            }*/
        }

        @Override
        public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onSelectedCandidatePairChanged = " + event.toString());
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onAddStream = " + mediaStream.toString());
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onRemoveStream = " + mediaStream.toString());
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onAddTrack = " + rtpReceiver.toString());
        }

        @Override
        public void onTrack(RtpTransceiver transceiver) {
            //KLLog.e("KLPeerRemote PeerConnection.Observer onTrack = " + transceiver.toString());
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
            KLLog.e("KLPeerRemote create offer sdp error = " + error);
            nLive = 0;
        }

        @Override
        public void onSetFailure(final String error) {
            KLLog.e("KLPeerRemote offer sdp set error = " + error);
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
            KLLog.e("KLPeerRemote set remote sdp ok");
            if (bClose) {
                return;
            }
            nLive = 3;

            mKLEngine.WriteDebugLog("获取视频track = " + strMid);
            VideoTrack mVideoTrack = getRemoteVideoTrack();
            if (mVideoTrack != null) {
                mKLEngine.WriteDebugLog("增加视频帧回调 = " + strMid);
                mVideoTrack.addSink(videoProxy);
            }

            AudioTrack mAudioTrack = getRemoteAudioTrack();
            if (mAudioTrack != null) {
                mKLEngine.WriteDebugLog("增加音频帧回调 = " + strMid);
                mAudioTrack.addSink(audioProxy);
                mAudioTrack.setEnabled(!mKLEngine.bAudioLive);
            }
        }

        @Override
        public void onCreateFailure(final String error) {

        }

        @Override
        public void onSetFailure(final String error) {
            KLLog.e("KLPeerRemote answer sdp set error = " + error);
            nLive = 0;
        }
    }
}

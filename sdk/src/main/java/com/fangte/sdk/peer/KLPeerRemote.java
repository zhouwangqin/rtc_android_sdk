package com.fangte.sdk.peer;

import com.fangte.sdk.KLEngine;
import com.fangte.sdk.util.KLLog;

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

import static com.fangte.sdk.KLEngine.mExecutor;
import static org.webrtc.SessionDescription.Type.ANSWER;

public class KLPeerRemote {
    // 当前流标记
    public String strUid = "";
    public String strMid = "";
    public String sfuId = "";
    public String strSid = "";
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
        //sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        //sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        // 创建PeerConnect对象
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(mKLEngine.iceServers);
        rtcConfig.disableIpv6 = true;
        rtcConfig.enableDtlsSrtp = true;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        mPeerConnection = mKLEngine.mPeerConnectionFactory.createPeerConnection(rtcConfig, pcObserver);
        // 目前只有音频
        if (mPeerConnection != null) {
            RtpTransceiver.RtpTransceiverInit init = new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
            mPeerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, init);
        }
        // 状态赋值
        bClose = false;
        nLive = 1;
    }

    // 删除PeerConnection
    private void freePeerConnection() {
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
        mExecutor.execute(() -> {
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
            if (mKLEngine != null) {
                mKLEngine.OnPeerSubscribe(strMid, false, "KLPeerRemote send subscribe fail");
            }
        });
    }

    // 取消拉流
    private void sendUnSubscribe() {
        if (strSid.equals("")) {
            return;
        }
        mExecutor.execute(() -> {
            mKLEngine.mKLClient.SendUnsubscribe(strMid, strSid, sfuId);
            strSid = "";
        });
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

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {

        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            KLLog.e("KLPeerRemote PeerConnection.Observer onConnectionChange = " + newState + ", mid = " + strMid);
            if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                nLive = 4;
            }
            if (newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                if (bClose) {
                    return;
                }
                if (nLive != 0) {
                    if (nLive == 4) {
                        if (mKLEngine != null) {
                            mKLEngine.OnPeerSubscribeError(strMid);
                        }
                    }
                    if (nLive != 4) {
                        if (mKLEngine != null) {
                            mKLEngine.OnPeerSubscribe(strMid, false, "KLPeerRemote ice disconnect");
                        }
                    }
                    nLive = 0;
                }
            }
            if (newState == PeerConnection.PeerConnectionState.FAILED) {
                if (bClose) {
                    return;
                }
                if (nLive != 0) {
                    if (nLive == 4) {
                        if (mKLEngine != null) {
                            mKLEngine.OnPeerSubscribeError(strMid);
                        }
                    }
                    if (nLive != 4) {
                        if (mKLEngine != null) {
                            mKLEngine.OnPeerSubscribe(strMid, false, "KLPeerRemote ice disconnect");
                        }
                    }
                    nLive = 0;
                }
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
            if (bClose) {
                return;
            }
            nLive = 0;
            if (mKLEngine != null) {
                mKLEngine.OnPeerSubscribe(strMid, false, error);
            }
        }

        @Override
        public void onSetFailure(final String error) {
            KLLog.e("KLPeerRemote offer sdp set error = " + error);
            if (bClose) {
                return;
            }
            nLive = 0;
            if (mKLEngine != null) {
                mKLEngine.OnPeerSubscribe(strMid, false, error);
            }
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
            if (mKLEngine != null) {
                mKLEngine.OnPeerSubscribe(strMid, true, "");
            }
        }

        @Override
        public void onCreateFailure(final String error) {

        }

        @Override
        public void onSetFailure(final String error) {
            KLLog.e("KLPeerRemote answer sdp set error = " + error);
            if (bClose) {
                return;
            }
            nLive = 0;
            if (mKLEngine != null) {
                mKLEngine.OnPeerSubscribe(strMid, false, error);
            }
        }
    }
}

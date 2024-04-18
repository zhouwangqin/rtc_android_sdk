package com.fangte.sdk.peer;

import com.fangte.sdk.FTEngine;
import com.fangte.sdk.util.FTLog;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.Collections;
import java.util.List;

import static com.fangte.sdk.FTBase.AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT;
import static com.fangte.sdk.FTBase.AUDIO_ECHO_CANCELLATION_CONSTRAINT;
import static com.fangte.sdk.FTBase.AUDIO_HIGH_PASS_FILTER_CONSTRAINT;
import static com.fangte.sdk.FTBase.AUDIO_NOISE_SUPPRESSION_CONSTRAINT;
import static com.fangte.sdk.FTBase.AUDIO_TRACK_ID;
import static org.webrtc.SessionDescription.Type.ANSWER;

// 音频推流对象
public class FTPeerAudio {
    // 当前流标记
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

    // 音频对象
    private AudioSource mAudioSource = null;
    private AudioTrack mAudioTrack = null;

    // 启动推流
    public void startPublish() {
        initPeerConnection();
        createOffer();
    }

    // 取消推流
    public void stopPublish() {
        sendUnPublish();
        freePeerConnection();
    }

    // 设置音频可用
    public void setAudioEnable(boolean bEnable) {
        if (mAudioTrack != null) {
            mAudioTrack.setEnabled(bEnable);
        }
    }

    // 设置音频音量
    public void setAudioVolume(int nVolume) {
        if (mAudioTrack != null) {
            if (nVolume > 10) {
                nVolume = 10;
            }
            if (nVolume < 0) {
                nVolume = 0;
            }
            double dVolume = nVolume * 1.0;
            mAudioTrack.setVolume(dVolume);
        }
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
        createAudioTrack();
        List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
        mPeerConnection.addTrack(mAudioTrack, mediaStreamLabels);
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
        if (mAudioTrack != null) {
            mAudioTrack.dispose();
            mAudioTrack = null;
        }
        if (mAudioSource != null) {
            mAudioSource.dispose();
            mAudioSource = null;
        }
        if (mPeerConnection != null) {
            mPeerConnection.dispose();
            mPeerConnection = null;
        }
    }

    // 创建本地音频Track
    private void createAudioTrack() {
        // 音视功能开关
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        // 创建Track
        mAudioSource = mFTEngine.mPeerConnectionFactory.createAudioSource(audioConstraints);
        mAudioTrack = mFTEngine.mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, mAudioSource);
        mAudioTrack.setEnabled(mFTEngine.bAudioEnable);
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
                if (mFTEngine.mFTClient.SendPublish(sdp.description, true, false, 0, 0)) {
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
        sendPublish(sdp);
    }

    // 接受到远端answer sdp处理
    private void onRemoteDescription(final SessionDescription sdp) {
        FTLog.e("FTPeerAudio setRemoteDescription = " + strMid);
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
            FTLog.e("FTPeerAudio PeerConnection.Observer onConnectionChange = " + newState);
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
            FTLog.e("FTPeerAudio create offer sdp ok");
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
            FTLog.e("FTPeerAudio set offer sdp ok");
            if (bClose) {
                return;
            }
            onLocalDescription(localSdp);
        }

        @Override
        public void onCreateFailure(final String error) {
            FTLog.e("FTPeerAudio create offer sdp fail = " + error);
            nLive = 0;
        }

        @Override
        public void onSetFailure(final String error) {
            FTLog.e("FTPeerAudio offer sdp set error = " + error);
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
            FTLog.e("FTPeerAudio set remote sdp ok");
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
            FTLog.e("FTPeerAudio answer sdp set error = " + error);
            nLive = 0;
        }
    }
}

package com.fangte.sdk.peer;

import com.fangte.sdk.KLEngine;
import com.fangte.sdk.KLFrame;
import com.fangte.sdk.util.KLLog;

import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.YuvHelper;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static com.fangte.sdk.KLBase.VIDEO_TRACK_ID;
import static com.fangte.sdk.KLBase.VIDEO_TRACK_TYPE;
import static org.webrtc.SessionDescription.Type.ANSWER;

public class KLPeerCamera {
    // 参数
    public String strUid = "";
    public String strMid = "";
    public String sfuId = "";
    // 上层对象
    public KLEngine mKLEngine = null;

    // 连接状态
    public int nLive = 0;
    // 退出标记
    public boolean bClose = false;

    // 临时变量
    private SessionDescription localSdp = null;

    // 回调对象
    private final KLPeerCamera.PCObserver pcObserver = new KLPeerCamera.PCObserver();
    private final KLPeerCamera.SDPOfferObserver sdpOfferObserver = new KLPeerCamera.SDPOfferObserver();
    private final KLPeerCamera.SDPAnswerObserver sdpAnswerObserver = new KLPeerCamera.SDPAnswerObserver();

    // peer对象
    private PeerConnection mPeerConnection = null;
    // sdp media 对象
    private MediaConstraints sdpMediaConstraints = null;

    // 视频对象
    private boolean bCapture = false;
    private RtpSender localVideoSender = null;
    private VideoSource mVideoSource = null;
    private VideoCapturer mVideoCapturer = null;
    private SurfaceTextureHelper mSurfaceTextureHelper = null;

    // 本地渲染处理
    private final KLFrame klFrame = new KLFrame();
    private final KLPeerCamera.ProxyVideoSink videoProxy = new KLPeerCamera.ProxyVideoSink();

    private static class ProxyVideoSink implements VideoSink {
        public KLPeerCamera klPeerCamera = null;

        @Override
        public void onFrame(VideoFrame frame) {
            if (klPeerCamera != null && klPeerCamera.mKLEngine != null && klPeerCamera.mKLEngine.mKLListen != null) {
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

                            klPeerCamera.klFrame.uid = klPeerCamera.strUid;
                            klPeerCamera.klFrame.video_type = 0;
                            klPeerCamera.klFrame.sy = i420Buffer.getStrideY();
                            klPeerCamera.klFrame.su = i420Buffer.getStrideU();
                            klPeerCamera.klFrame.sv = i420Buffer.getStrideV();
                            klPeerCamera.klFrame.yb = new byte[nYlen];
                            klPeerCamera.klFrame.ub = new byte[nUlen];
                            klPeerCamera.klFrame.vb = new byte[nVlen];
                            klPeerCamera.klFrame.width = i420Buffer.getWidth();
                            klPeerCamera.klFrame.height = i420Buffer.getHeight();
                            System.arraycopy(ybytes, 0, klPeerCamera.klFrame.yb, 0, nYlen);
                            System.arraycopy(ubytes, 0, klPeerCamera.klFrame.ub, 0, nUlen);
                            System.arraycopy(vbytes, 0, klPeerCamera.klFrame.vb, 0, nVlen);
                            klPeerCamera.mKLEngine.mKLListen.OnLocalVideo(klPeerCamera.klFrame);

                            i420Buffer.release();
                        }
                    }
                }
            }
        }
    }

    // 切换前后摄像头
    public void switchCapture(boolean switchCapture) {
        if (mVideoCapturer != null && mVideoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) mVideoCapturer;
            cameraVideoCapturer.switchCamera(null);
        }
    }

    // 初始化视频采集
    private void initCapture() {
        freeCapture();
        // 启动
        initCameraCapture();
        startVideoSource();
    }

    // 释放采集对象
    private void freeCapture() {
        stopVideoSource();
        freeCameraCapture();
    }

    // 启动视频采集
    private void startVideoSource() {
        if (mVideoCapturer != null && !bCapture) {
            KLLog.e("KLPeerCamera startVideoSource");
            mVideoCapturer.startCapture(352, 288, 15);
            bCapture = true;
        }
    }

    // 停止视频采集
    private void stopVideoSource() {
        if (mVideoCapturer != null && bCapture) {
            KLLog.e("KLPeerCamera stopVideoSource");
            try {
                mVideoCapturer.stopCapture();
                bCapture = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 初始化摄像头采集
    private void initCameraCapture() {
        mVideoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        if (mVideoCapturer == null) {
            if (mKLEngine != null) {
                mKLEngine.WriteDebugLog("initCapture fail, camera is null");
            }
        }
    }

    // 释放摄像头采集
    private void freeCameraCapture() {
        if (mVideoCapturer != null) {
            mVideoCapturer.dispose();
            mVideoCapturer = null;
        }
    }

    // 创建摄像头采集对象
    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    // 启动推流
    public void startPublish() {
        initCapture();
        initPeerConnection();
        if (mKLEngine != null && mKLEngine.bCameraPub) {
            createOffer();
        }
    }

    // 取消推流
    public void stopPublish() {
        sendUnPublish();
        freePeerConnection();
        freeCapture();
    }

    // 创建PeerConnection
    private void initPeerConnection() {
        freePeerConnection();
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        // 创建PeerConnect对象
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(mKLEngine.iceServers);
        rtcConfig.disableIpv6 = true;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        mPeerConnection = mKLEngine.mPeerConnectionFactory.createPeerConnection(rtcConfig, pcObserver);
        // 添加Track
        List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
        mPeerConnection.addTrack(createVideoTrack(mVideoCapturer), mediaStreamLabels);
        videoProxy.klPeerCamera = this;
        findVideoSender();
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
    private VideoTrack createVideoTrack(VideoCapturer videoCapturer) {
        mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mKLEngine.mEglBase.getEglBaseContext());
        mVideoSource = mKLEngine.mPeerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(mSurfaceTextureHelper, mKLEngine.mContext, mVideoSource.getCapturerObserver());
        VideoTrack localVideoTrack = mKLEngine.mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, mVideoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addSink(videoProxy);
        return localVideoTrack;
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
            KLLog.e("RtpParameters are not ready.");
            return;
        }

        for (RtpParameters.Encoding encoding : parameters.encodings) {
            encoding.minBitrateBps = 100 * 1000;
            encoding.maxBitrateBps = 300 * 1000;
            encoding.maxFramerate = 15;
        }

        if (!localVideoSender.setParameters(parameters)) {
            KLLog.e("RtpSender.setParameters failed.");
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
            if (mKLEngine != null && mKLEngine.mKLClient != null && sdp != null) {
                if (mKLEngine.mKLClient.SendPublish(sdp.description, false, true, 0, 0)) {
                    nLive = 2;
                    // 处理返回
                    strMid = mKLEngine.mKLClient.strMid;
                    sfuId = mKLEngine.mKLClient.sfuId;
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

    // 取消推流
    private void sendUnPublish() {
        if (strMid.equals("")) {
            return;
        }
        new Thread(() -> {
            mKLEngine.mKLClient.SendUnpublish(strMid, sfuId);
            strMid = "";
            sfuId = "";
        }).start();
    }

    // 设置本地offer sdp回调处理
    private void onLocalDescription(final SessionDescription sdp) {
        setVideoBitrate();
        sendPublish(sdp);
    }

    // 接受到远端answer sdp处理
    private void onRemoteDescription(final SessionDescription sdp) {
        KLLog.e("KLPeerCamera setRemoteDescription = " + strMid);
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
            KLLog.e("KLPeerCamera PeerConnection.Observer onConnectionChange = " + newState);
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
            KLLog.e("KLPeerCamera create offer sdp ok");
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
            KLLog.e("KLPeerCamera set offer sdp ok");
            if (bClose) {
                return;
            }
            onLocalDescription(localSdp);
        }

        @Override
        public void onCreateFailure(final String error) {
            KLLog.e("KLPeerCamera create offer sdp fail = " + error);
            nLive = 0;
        }

        @Override
        public void onSetFailure(final String error) {
            KLLog.e("KLPeerCamera offer sdp set error = " + error);
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
            KLLog.e("KLPeerCamera set remote sdp ok");
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
            KLLog.e("KLPeerCamera answer sdp set error = " + error);
            nLive = 0;
        }
    }
}

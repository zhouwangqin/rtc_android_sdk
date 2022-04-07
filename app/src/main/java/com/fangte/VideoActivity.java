package com.fangte;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fangte.sdk.KLSdk;
import com.fangte.sdk.util.KLLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;

public class VideoActivity extends Activity {
    // 参数
    private boolean bScreen = false;
    private boolean bReLogin = false;
    private final ArrayList<VideoView> mViews = new ArrayList<>();
    // 定时器
    private boolean bExit = false;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            // 判断当前连接
            if (KLSdk.getInstance().getConnect()) {
                // 重连加入会议
                if (bReLogin) {
                    for (int i = 2; i < 9; i++) {
                        freeRemotePeer(i);
                    }
                    KLApplication.getInstance().streamParamHashMap.clear();
                    KLSdk.getInstance().joinRoom(KLApplication.getInstance().strRid);
                    bReLogin = false;

                    if (!bExit) {
                        mHandler.postDelayed(this, 2000);
                    }
                    return;
                }
                // 查询现有的流
                for (VideoView videoView : mViews) {
                    if (videoView.bUse && !videoView.bLocal) {
                        // 查询视频拉流
                        if (KLApplication.getInstance().streamParamHashMap.get(videoView.klPeerRemote.strMid) == null) {
                            // 流已经不存在了，删除这个Peer
                            KLLog.e("====== 删除流");
                            freeRemotePeer(videoView.nIndex);
                        }
                    }
                }
                // 查询流对象
                for (Map.Entry<String, KLApplication.StreamParam> stringStreamParamEntry : KLApplication.getInstance().streamParamHashMap.entrySet()) {
                    String key = (String) ((Map.Entry) stringStreamParamEntry).getKey();
                    KLApplication.StreamParam value = (KLApplication.StreamParam) ((Map.Entry) stringStreamParamEntry).getValue();
                    KLLog.e("====== 现有的拉流 = " + key);
                    // 查询现在是否存在该流
                    boolean bFind = false;
                    for (VideoView videoView : mViews) {
                        if (videoView.bUse && !videoView.bLocal) {
                            if (key.equals(videoView.klPeerRemote.strMid)) {
                                bFind = true;
                                break;
                            }
                        }
                    }
                    if (!bFind) {
                        for (VideoView videoView : mViews) {
                            if (!videoView.bUse && !videoView.bLocal) {
                                initRemotePeer(videoView.nIndex, value.uid, value.mid, value.sfuid, value.mInfo);
                                break;
                            }
                        }
                    }
                }
            } else {
                // socket 连接断开了
                KLLog.e("====== socket重连");
                bReLogin = true;
                KLSdk.getInstance().start();
            }

            if (!bExit) {
                mHandler.postDelayed(this, 2000);
            }
        }
    };

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1000) {
                // 停止定时器
                stopTimer();
                // 关闭媒体连接
                for (VideoView videoView : mViews) {
                    if (videoView.bUse) {
                        if (videoView.bLocal) {
                            if (videoView.nIndex == 0) {
                                freeLocalPeer();
                            }
                            if (videoView.nIndex == 1) {
                                freeScreenPeer();
                            }
                        } else {
                            freeRemotePeer(videoView.nIndex);
                        }
                    }
                }
                // 发送关闭会议通知
                KLSdk.getInstance().leaveRoom();
                // 关闭页面
                finish();
            }
        }
    };

    // 初始化按键
    private void initImageView(String rid) {
        TextView mTextView = findViewById(R.id.text_room);
        mTextView.setText(rid);
        mTextView.setTextColor(Color.rgb(255, 255, 255));

        ImageView mImageSpeaker = findViewById(R.id.image_Speaker);
        if (KLSdk.getInstance().getSpeakerphoneOn()) {
            mImageSpeaker.setBackgroundResource(R.mipmap.speaker_on);
        } else {
            mImageSpeaker.setBackgroundResource(R.mipmap.speaker_off);
        }
        mImageSpeaker.setOnClickListener(v -> {
            if (KLSdk.getInstance().getSpeakerphoneOn()) {
                KLSdk.getInstance().setSpeakerphoneOn(false);
                mImageSpeaker.setBackgroundResource(R.mipmap.speaker_off);
            } else {
                KLSdk.getInstance().setSpeakerphoneOn(true);
                mImageSpeaker.setBackgroundResource(R.mipmap.speaker_on);
            }
        });

        ImageView mImageSwitch = findViewById(R.id.image_Switch);
        mImageSwitch.setOnClickListener(v -> {
            VideoView videoView = mViews.get(0);
            if (videoView.bUse) {
                videoView.klPeerLocal.switchCapture(true);
            }
        });

        ImageView mImageLeave = findViewById(R.id.image_Leave);
        mImageLeave.setOnClickListener(v -> mHandler.sendEmptyMessage(1000));

        ImageView mImageMic = findViewById(R.id.image_Mic);
        if (KLSdk.getInstance().getMicrophoneMute()) {
            mImageMic.setBackgroundResource(R.mipmap.mic_mute);
        } else {
            mImageMic.setBackgroundResource(R.mipmap.mic_unmute);
        }
        mImageMic.setOnClickListener(v -> {
            if (KLSdk.getInstance().getMicrophoneMute()) {
                KLSdk.getInstance().setMicrophoneMute(false);
                VideoView videoView = mViews.get(0);
                if (videoView.bUse) {
                    videoView.klPeerLocal.setAudioEnable(true);
                }
                mImageMic.setBackgroundResource(R.mipmap.mic_unmute);
            } else {
                KLSdk.getInstance().setMicrophoneMute(true);
                VideoView videoView = mViews.get(0);
                if (videoView.bUse) {
                    videoView.klPeerLocal.setAudioEnable(false);
                }
                mImageMic.setBackgroundResource(R.mipmap.mic_mute);
            }
        });

        ImageView mImageCamera = findViewById(R.id.image_Camera);
        mImageCamera.setOnClickListener(v -> {
            VideoView videoView = mViews.get(0);
            if (videoView.bUse) {
                if (videoView.klPeerLocal.getCapture()) {
                    videoView.klPeerLocal.setCapture(false);
                    videoView.klPeerLocal.setVideoEnable(false);
                    mImageCamera.setBackgroundResource(R.mipmap.camera_off);
                } else {
                    videoView.klPeerLocal.setCapture(true);
                    videoView.klPeerLocal.setVideoEnable(true);
                    mImageCamera.setBackgroundResource(R.mipmap.camera_on);
                }
            }
        });

        ImageView mImageScreen = findViewById(R.id.image_Screen);
        mImageScreen.setOnClickListener(v -> {
            if (bScreen) {
                mImageScreen.setBackgroundResource(R.mipmap.screen_off);
                freeScreenPeer();
                bScreen = false;
                return;
            }
            startScreenCapture();
            bScreen = true;
            mImageScreen.setBackgroundResource(R.mipmap.screen_on);
        });

        ImageView mImageMember = findViewById(R.id.image_Member);
        mImageMember.setOnClickListener(v -> {
            //
            //KLSdk.getInstance().getParticipant();
        });
        ImageView mImageMore = findViewById(R.id.image_More);
        mImageMore.setOnClickListener(v -> {

        });
    }

    // 初始化视图
    private void initVideoView() {
        // 第1行第1个
        VideoView videoView = new VideoView();
        videoView.nIndex = 0;
        videoView.bLocal = true;
        videoView.mRelativeLayout = findViewById(R.id.video_line1_view1);
        mViews.add(videoView);
        // 第1行第2个
        videoView = new VideoView();
        videoView.nIndex = 1;
        videoView.bLocal = true;
        videoView.mRelativeLayout = findViewById(R.id.video_line1_view2);
        mViews.add(videoView);
        // 第1行第3个
        videoView = new VideoView();
        videoView.nIndex = 2;
        videoView.bLocal = false;
        videoView.mRelativeLayout = findViewById(R.id.video_line1_view3);
        mViews.add(videoView);
        // 第2行第1个
        videoView = new VideoView();
        videoView.nIndex = 3;
        videoView.bLocal = false;
        videoView.mRelativeLayout = findViewById(R.id.video_line2_view1);
        mViews.add(videoView);
        // 第2行第2个
        videoView = new VideoView();
        videoView.nIndex = 4;
        videoView.bLocal = false;
        videoView.mRelativeLayout = findViewById(R.id.video_line2_view2);
        mViews.add(videoView);
        // 第2行第3个
        videoView = new VideoView();
        videoView.nIndex = 5;
        videoView.bLocal = false;
        videoView.mRelativeLayout = findViewById(R.id.video_line2_view3);
        mViews.add(videoView);
        // 第3行第1个
        videoView = new VideoView();
        videoView.nIndex = 6;
        videoView.bLocal = false;
        videoView.mRelativeLayout = findViewById(R.id.video_line3_view1);
        mViews.add(videoView);
        // 第3行第2个
        videoView = new VideoView();
        videoView.nIndex = 7;
        videoView.bLocal = false;
        videoView.mRelativeLayout = findViewById(R.id.video_line3_view2);
        mViews.add(videoView);
        // 第3行第3个
        videoView = new VideoView();
        videoView.nIndex = 8;
        videoView.bLocal = false;
        videoView.mRelativeLayout = findViewById(R.id.video_line3_view3);
        mViews.add(videoView);
    }

    // 启动定时器
    private void startTimer() {
        // 停止以前的
        stopTimer();
        // 启动新的
        bExit = false;
        mHandler.postDelayed(runnable, 2000);
    }

    // 停止定时器
    private void stopTimer() {
        bExit = true;
        mHandler.removeCallbacks(runnable);
    }

    // 启动推流
    private void initLoaclPeer() {
        VideoView videoView = mViews.get(0);
        videoView.bUse = true;
        videoView.bLocal = true;
        videoView.klPeerLocal = KLSdk.getInstance().createLocalPeer();
        videoView.klPeerLocal.setPeerListen(videoView);
        videoView.klPeerLocal.setVideoRenderer(videoView.mRelativeLayout);
        videoView.klPeerLocal.initPeerParam(true, true, 0);
        videoView.klPeerLocal.startPublish();
    }

    // 释放推流
    private void freeLocalPeer() {
        VideoView videoView = mViews.get(0);
        if (videoView.bUse) {
            videoView.klPeerLocal.stopPublish();
            videoView.klPeerLocal = null;
            videoView.bUse = false;
        }
    }

    // 启动屏幕共享
    private void initScreenPeer(Intent intent) {
        VideoView videoView = mViews.get(1);
        videoView.bUse = true;
        videoView.bLocal = true;
        videoView.klPeerLocal = KLSdk.getInstance().createLocalPeer();
        videoView.klPeerLocal.setCaptureIntent(intent);
        videoView.klPeerLocal.setPeerListen(videoView);
        videoView.klPeerLocal.setVideoRenderer(videoView.mRelativeLayout);
        videoView.klPeerLocal.initPeerParam(false, true, 1);
        videoView.klPeerLocal.startPublish();
    }

    // 释放屏幕共享
    private void freeScreenPeer() {
        VideoView videoView = mViews.get(1);
        if (videoView.bUse) {
            videoView.klPeerLocal.stopPublish();
            videoView.klPeerLocal = null;
            videoView.bUse = false;
        }
    }

    // 启动拉流
    private void initRemotePeer(int index, String uid, String mid, String sfu, JSONObject mInfo) {
        VideoView videoView = mViews.get(index);
        videoView.bUse = true;
        videoView.bLocal = false;
        videoView.klPeerRemote = KLSdk.getInstance().createRemotePeer(uid, mid, sfu, mInfo);
        videoView.klPeerRemote.setPeerListen(videoView);
        videoView.klPeerRemote.setVideoRenderer(videoView.mRelativeLayout);
        videoView.klPeerRemote.initPeerParam(true, true);
        videoView.klPeerRemote.startSubscribe();
    }

    // 释放拉流
    private void freeRemotePeer(int index) {
        VideoView videoView = mViews.get(index);
        if (videoView.bUse) {
            videoView.klPeerRemote.stopSubscribe();
            videoView.klPeerRemote = null;
            videoView.bUse = false;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        // 初始化控件
        initImageView(KLApplication.getInstance().strRid);
        initVideoView();
        mHandler.post(() -> {
            // 启动推流
            initLoaclPeer();
            // 启动定时器检查流
            startTimer();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        KLSdk.getInstance().stop();
        KLSdk.getInstance().freeSdk();
        KLApplication.getInstance().bSdk = false;
        KLApplication.getInstance().peerParamHashMap.clear();
        KLApplication.getInstance().streamParamHashMap.clear();
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        mHandler.sendEmptyMessage(1000);
    }

    // 录制权限支持
    private void startScreenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaProjectionManager mMediaProjectionManager = (MediaProjectionManager) getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mMediaProjectionManager != null) {
                startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), 1000);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1000 && resultCode == RESULT_OK) {
            initScreenPeer(data);

            Intent service = new Intent(this, ScreenRecorder.class);
            service.putExtra("code", resultCode);
            service.putExtra("data", data);
            Log.e("onActivityResult : ", "code:" + resultCode + ",data:" + data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    static class ScreenRecorder extends Service {

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            createNotificationChannel();
            return super.onStartCommand(intent, flags, startId);
        }

        private void createNotificationChannel() {
            Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
            Intent nfIntent = new Intent(this, MainActivity.class); //点击后跳转的界面，可以设置跳转数据

            builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0)) // 设置PendingIntent
                    .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                    //.setContentTitle("SMI InstantView") // 设置下拉列表里的标题
                    .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                    .setContentText("is running......") // 设置上下文内容
                    .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间

            /*以下是对Android 8.0的适配*/
            //普通notification适配
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId("notification_id");
            }
            //前台服务notification适配
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                }
            }

            Notification notification = builder.build(); // 获取构建好的Notification
            notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音

        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}

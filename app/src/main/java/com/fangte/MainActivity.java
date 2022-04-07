package com.fangte;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.fangte.sdk.KLSdk;

import java.util.ArrayList;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import static com.fangte.DataBase.MEET_JOIN_RESP;
import static com.fangte.sdk.KLBase.EVENT_JOIN_ROOM_OK;

public class MainActivity extends AppCompatActivity {
    // 参数
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST = 10;
    private boolean bShow = false;

    // 控件
    private Button mJoinMeet = null;
    private EditText mEditText1 = null;
    private EditText mEditText2 = null;
    private EditText mEditText3 = null;
    private EditText mEditText4 = null;
    private SharedPreferences mSharedPreferences = null;

    // 初始化sdk
    private void initSdk() {
        // 初始化
        String strServerIp = mEditText1.getText().toString();
        String strServerPort = mEditText2.getText().toString();
        String strName = mEditText4.getText().toString();
        // 判断是否输入新的值
        String strKey = mSharedPreferences.getString("name", "");
        String strValue = mSharedPreferences.getString("uid", "");
        // 参数值
        String strUid;
        if (strKey.equals(strName) && !strValue.equals("")) {
            strUid = strValue;
        } else {
            strUid = UUID.randomUUID().toString();
            mSharedPreferences.edit().putString("name", strName).apply();
            mSharedPreferences.edit().putString("uid", strUid).apply();
        }
        // 初始化sdk
        KLSdk.getInstance().setServerIp(strServerIp, Integer.parseInt(strServerPort));
        KLSdk.getInstance().initSdk(strUid, getApplicationContext(), KLApplication.getInstance().mHandler);
        KLSdk.getInstance().start();
        KLApplication.getInstance().strUid = strUid;
        KLApplication.getInstance().bSdk = true;
    }

    // 释放sdk
    private void freeSdk() {
        KLApplication.getInstance().bSdk = false;
        KLSdk.getInstance().stop();
        KLSdk.getInstance().freeSdk();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSharedPreferences = getSharedPreferences("user", Context.MODE_PRIVATE);
        // 初始化控件
        mEditText1 = findViewById(R.id.edit_1);
        mEditText1.setText(R.string.main_text_1_ts);
        mEditText2 = findViewById(R.id.edit_2);
        mEditText2.setText(R.string.main_text_2_ts);
        mEditText3 = findViewById(R.id.edit_3);
        mEditText3.setText(R.string.main_text_3_ts);
        // 判断是否上次保存用户名
        mEditText4 = findViewById(R.id.edit_4);
        String strTmp = mSharedPreferences.getString("name", "");
        if (strTmp.equals("")) {
            mEditText4.setText(R.string.main_text_4_ts);
        } else {
            mEditText4.setText(strTmp);
        }

        mJoinMeet = findViewById(R.id.btn_join_meet);
        mJoinMeet.setOnClickListener(v -> {
            if (!KLApplication.getInstance().bSdk) {
                initSdk();
                return;
            }
            // 加入会议
            mJoinMeet.setClickable(false);
            if (KLSdk.getInstance().getConnect()) {
                String strRid = mEditText3.getText().toString();
                KLApplication.getInstance().strRid = strRid;
                KLSdk.getInstance().joinRoom(strRid);
            } else {
                Toast.makeText(getApplicationContext(), "网络连接失败", Toast.LENGTH_SHORT).show();
                KLSdk.getInstance().start();
                mJoinMeet.setClickable(true);
            }
        });

        // 注册广播
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(MEET_JOIN_RESP);
        registerReceiver(mBroadcastReceiver, mIntentFilter);
        // 请求权限
        requestMissingPermissions();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mBroadcastReceiver);
        freeSdk();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        bShow = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        bShow = true;
    }

    // 广播接收对象
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String strAction = intent.getAction();
            if (strAction != null) {
                if (strAction.equals(MEET_JOIN_RESP)) {
                    // 加入会议
                    mJoinMeet.setClickable(true);
                    if (intent.getIntExtra("result", -1) == EVENT_JOIN_ROOM_OK) {
                        // 加入会议成功,进入到视频界面
                        if (bShow) {
                            Intent mIntent = new Intent(MainActivity.this, VideoActivity.class);
                            startActivity(mIntent);
                        }
                    }
                }
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST) {
            String[] missingPermissions = getMissingPermissions();
            if (missingPermissions.length != 0) {
                // User didn't grant all the permissions. Warn that the application might not work correctly.
                AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
                mBuilder.setMessage(R.string.main_try_again);
                mBuilder.setPositiveButton(R.string.main_yes, (dialog, which) -> {
                    // User wants to try giving the permissions again.
                    dialog.cancel();
                    requestMissingPermissions();
                });
                mBuilder.setNegativeButton(R.string.main_no, (dialog, which) -> {
                    // User doesn't want to give the permissions.
                    dialog.cancel();
                });
                mBuilder.show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // 请求权限
    private void requestMissingPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        String[] missingPermissions = getMissingPermissions();
        if (missingPermissions.length != 0) {
            requestPermissions(missingPermissions, PERMISSION_REQUEST);
        }
    }

    // 获取缺失的权限
    private String[] getMissingPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return new String[0];
        }

        PackageInfo info;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Failed to retrieve permissions.");
            return new String[0];
        }

        if (info.requestedPermissions == null) {
            Log.i(TAG, "No requested permissions.");
            return new String[0];
        }

        ArrayList<String> missingPermissions = new ArrayList<>();
        for (int i = 0; i < info.requestedPermissions.length; i++) {
            if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
                missingPermissions.add(info.requestedPermissions[i]);
            }
        }
        Log.i(TAG, "Missing permissions: " + missingPermissions);

        String[] sPermissions = new String[missingPermissions.size()];
        return missingPermissions.toArray(sPermissions);
    }
}

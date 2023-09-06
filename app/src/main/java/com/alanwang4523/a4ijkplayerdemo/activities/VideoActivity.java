/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alanwang4523.a4ijkplayerdemo.activities;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alanwang4523.a4ijkplayerdemo.R;
import com.alanwang4523.a4ijkplayerdemo.application.Settings;
import com.alanwang4523.a4ijkplayerdemo.content.RecentMediaStorage;
import com.alanwang4523.a4ijkplayerdemo.fragments.TracksFragment;
import com.alanwang4523.a4ijkplayerdemo.widget.AndroidMediaController;
import com.alanwang4523.a4ijkplayerdemo.widget.IjkVideoView;
import com.alanwang4523.a4ijkplayerdemo.widget.MeasureHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

public class VideoActivity extends AppCompatActivity implements TracksFragment.ITrackHolder {
    private static final String TAG = "VideoActivity";

    private String mVideoPath;
    private Uri mVideoUri;

    private AndroidMediaController mMediaController;
    private IjkVideoView mVideoView;
    private TextView mToastTextView;
    private TableLayout mHudView;
    private DrawerLayout mDrawerLayout;
    private ViewGroup mRightDrawer;
    private Button mBtnRecord;
    private Button mBtnScreenShot;
    private TextView mTvTime;

    private Settings mSettings;
    private boolean mBackPressed;


    //是否正在录屏
    private boolean mIsRecording;

    private String recordingFilePath;

    private CountDownTimer countDownTimer;
    private long totalTimeInMillis = 1000 * 60 * 2; // 总计时
    private long intervalInMillis = 1000; // 每隔1秒触发一次

    public static Intent newIntent(Context context, String videoPath, String videoTitle) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("videoPath", videoPath);
        intent.putExtra("videoTitle", videoTitle);
        return intent;
    }

    public static void intentTo(Context context, String videoPath, String videoTitle) {
        context.startActivity(newIntent(context, videoPath, videoTitle));
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        countDownTimer = new CountDownTimer(totalTimeInMillis, intervalInMillis) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = (totalTimeInMillis - millisUntilFinished) / 1000;
                mTvTime.setText("录制中：" + seconds + "秒");
            }

            @Override
            public void onFinish() {
                // 当计时结束时触发
                mBtnRecord.setText("开始录制");
                stopRecord();
            }
        };

        mSettings = new Settings(this);

        // handle arguments
        mVideoPath = getIntent().getStringExtra("videoPath");

        Intent intent = getIntent();
        String intentAction = intent.getAction();
        if (!TextUtils.isEmpty(intentAction)) {
            if (intentAction.equals(Intent.ACTION_VIEW)) {
                mVideoPath = intent.getDataString();
            } else if (intentAction.equals(Intent.ACTION_SEND)) {
                mVideoUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    String scheme = mVideoUri.getScheme();
                    if (TextUtils.isEmpty(scheme)) {
                        Log.e(TAG, "Null unknown scheme\n");
                        finish();
                        return;
                    }
                    if (scheme.equals(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
                        mVideoPath = mVideoUri.getPath();
                    } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                        Log.e(TAG, "Can not resolve content below Android-ICS\n");
                        finish();
                        return;
                    } else {
                        Log.e(TAG, "Unknown scheme " + scheme + "\n");
                        finish();
                        return;
                    }
                }
            }
        }

        if (!TextUtils.isEmpty(mVideoPath)) {
            new RecentMediaStorage(this).saveUrlAsync(mVideoPath);
        }

        // init UI
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, false);
        mMediaController.setSupportActionBar(actionBar);

        mToastTextView = (TextView) findViewById(R.id.toast_text_view);
        mHudView = (TableLayout) findViewById(R.id.hud_view);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mRightDrawer = (ViewGroup) findViewById(R.id.right_drawer);
        mTvTime = findViewById(R.id.tv_time);
        mBtnScreenShot = findViewById(R.id.btn_screen_shot);
        mBtnScreenShot.setOnClickListener(v -> {
//            if (new Settings(this).getUsingMediaCodec() == MediaCodecType.SOFT) {
//                if (!mPlayerView.isPlaying()) {
//                    Toast.makeText(MainActivity.this, "视频尚未成功播放", Toast.LENGTH_SHORT).show();
//                    return;
//                }
            String shotPath = getExternalFilesDir("").getAbsolutePath() + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".jpg";
            File file = new File(shotPath);
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                try {
                    file.createNewFile();
                    screenShot(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Toast.makeText(VideoActivity.this, "截图保存：" + shotPath, Toast.LENGTH_SHORT).show();
//            } else {
//                Toast.makeText(MainActivity.this, "截图不支持硬解", Toast.LENGTH_SHORT).show();
//            }
        });
        mBtnRecord = findViewById(R.id.btn_record);
        mBtnRecord.setOnClickListener(v -> {
            if (!mVideoView.isPlaying()) {
                Toast.makeText(this, "未播放视频", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isRecording()) {
                stopRecord();
                Toast.makeText(this, "视频已保存：" + recordingFilePath, Toast.LENGTH_SHORT).show();
                mBtnRecord.setText("开始录制");
                countDownTimer.cancel();
            } else {

                recordingFilePath = getExternalFilesDir(null).getAbsolutePath() +
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ".mov";

                startRecord(recordingFilePath);
                mBtnRecord.setText("停止录制");
                countDownTimer.start();
            }
        });

        mDrawerLayout.setScrimColor(Color.TRANSPARENT);

        // init player
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);
        mVideoView.setMediaController(mMediaController);
        mVideoView.setHudView(mHudView);
        // prefer mVideoPath
        if (mVideoPath != null)
            mVideoView.setVideoPath(mVideoPath);
        else if (mVideoUri != null)
            mVideoView.setVideoURI(mVideoUri);
        else {
            Log.e(TAG, "Null Data Source\n");
            finish();
            return;
        }

        mVideoView.start();
    }


    /**
     * 录屏相关===========================================================
     */

    public boolean startRecord(String path) {
        mIsRecording = true;
        boolean rel = mVideoView.startRecord(path);
        if (!rel) {
            mIsRecording = false;
        }
        return rel;
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    public void stopRecord() {
        mIsRecording = false;
        mVideoView.stopRecord();
    }

    public void screenShot(File file) {
        mVideoView.snapshotPicture(file);
    }


    @Override
    public void onBackPressed() {
        mBackPressed = true;

        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mBackPressed || !mVideoView.isBackgroundPlayEnabled()) {
            mVideoView.stopPlayback();
            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        } else {
            mVideoView.enterBackground();
        }
        IjkMediaPlayer.native_profileEnd();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_ratio) {
            int aspectRatio = mVideoView.toggleAspectRatio();
            String aspectRatioText = MeasureHelper.getAspectRatioText(this, aspectRatio);
            mToastTextView.setText(aspectRatioText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_toggle_player) {
            int player = mVideoView.togglePlayer();
            String playerText = IjkVideoView.getPlayerText(this, player);
            mToastTextView.setText(playerText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_toggle_render) {
            int render = mVideoView.toggleRender();
            String renderText = IjkVideoView.getRenderText(this, render);
            mToastTextView.setText(renderText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_show_info) {
            mVideoView.showMediaInfo();
        } else if (id == R.id.action_show_tracks) {
            if (mDrawerLayout.isDrawerOpen(mRightDrawer)) {
                Fragment f = getSupportFragmentManager().findFragmentById(R.id.right_drawer);
                if (f != null) {
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.remove(f);
                    transaction.commit();
                }
                mDrawerLayout.closeDrawer(mRightDrawer);
            } else {
                Fragment f = TracksFragment.newInstance();
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.right_drawer, f);
                transaction.commit();
                mDrawerLayout.openDrawer(mRightDrawer);
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public ITrackInfo[] getTrackInfo() {
        if (mVideoView == null)
            return null;

        return mVideoView.getTrackInfo();
    }

    @Override
    public void selectTrack(int stream) {
        mVideoView.selectTrack(stream);
    }

    @Override
    public void deselectTrack(int stream) {
        mVideoView.deselectTrack(stream);
    }

    @Override
    public int getSelectedTrack(int trackType) {
        if (mVideoView == null)
            return -1;

        return mVideoView.getSelectedTrack(trackType);
    }
}

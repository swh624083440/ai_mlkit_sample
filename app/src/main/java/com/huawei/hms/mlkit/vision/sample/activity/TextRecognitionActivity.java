/**
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.huawei.hms.mlkit.vision.sample.activity;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.hms.mlkit.vision.sample.R;
import com.huawei.hms.mlkit.vision.sample.activity.dialog.AddPictureDialog;
import com.huawei.hms.mlkit.vision.sample.camera.CameraConfiguration;
import com.huawei.hms.mlkit.vision.sample.camera.LensEngine;
import com.huawei.hms.mlkit.vision.sample.camera.LensEnginePreview;
import com.huawei.hms.mlkit.vision.sample.util.BitmapUtils;
import com.huawei.hms.mlkit.vision.sample.manager.LocalDataManager;
import com.huawei.hms.mlkit.vision.sample.transactor.LocalTextTransactor;
import com.huawei.hms.mlkit.vision.sample.util.Constant;
import com.huawei.hms.mlkit.vision.sample.util.SharedPreferencesUtil;
import com.huawei.hms.mlkit.vision.sample.views.overlay.ZoomImageView;
import com.huawei.hms.mlkit.vision.sample.views.overlay.GraphicOverlay;

import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;

public final class TextRecognitionActivity extends BaseActivity
        implements OnRequestPermissionsResultCallback, View.OnClickListener {
    private static final String TAG = "TextRecognitionActivity";
    private LensEngine lensEngine = null;
    private LensEnginePreview preview;
    private GraphicOverlay graphicOverlay;
    private ImageButton takePicture;
    private ImageButton imageSwitch;
    private RelativeLayout zoomImageLayout;
    private ZoomImageView zoomImageView;
    private ImageButton zoomImageClose;
    CameraConfiguration cameraConfiguration = null;
    private int facing = CameraConfiguration.CAMERA_FACING_BACK;
    private Camera mCamera;
    private boolean isLandScape;
    private Bitmap bitmap, bitmapCopy;
    private LocalTextTransactor localTextTransactor;
    private Handler mHandler = new MsgHandler(this);
    private Dialog languageDialog;
    private AddPictureDialog addPictureDialog;
    private TextView textCN;
    private TextView textEN;
    private TextView textJN ;
    private TextView textKN;
    private TextView textLN;
    private String textType = Constant.POSITION_CN;

    private static class MsgHandler extends Handler {
        WeakReference<TextRecognitionActivity> mMainActivityWeakReference;

        public MsgHandler(TextRecognitionActivity mainActivity) {
            this.mMainActivityWeakReference = new WeakReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            TextRecognitionActivity mainActivity = this.mMainActivityWeakReference.get();
            if (mainActivity == null) {
                return;
            }
            Log.d(TextRecognitionActivity.TAG, "msg what :" + msg.what);
            if (msg.what == Constant.SHOW_TAKE_PHOTO_BUTTON) {
                mainActivity.setVisible();
            } else if (msg.what == Constant.HIDE_TAKE_PHOTO_BUTTON) {
                mainActivity.setGone();
            }
        }
    }

    private void setVisible() {
        if (this.takePicture.getVisibility() == View.GONE) {
            this.takePicture.setVisibility(View.VISIBLE);
        }
    }

    private void setGone() {
        if (this.takePicture.getVisibility() == View.VISIBLE) {
            this.takePicture.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_text_recognition);
        if (savedInstanceState != null) {
            this.facing = savedInstanceState.getInt(Constant.CAMERA_FACING);
        }
        this.preview = this.findViewById(R.id.live_preview);
        this.graphicOverlay = this.findViewById(R.id.live_overlay);
        this.cameraConfiguration = new CameraConfiguration();
        this.cameraConfiguration.setCameraFacing(this.facing);
        this.initViews();
        this.isLandScape = (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        this.createLensEngine();
        this.startLensEngine();
        this.setStatusBar();
    }

    private void initViews() {
        this.takePicture = this.findViewById(R.id.takePicture);
        this.takePicture.setOnClickListener(this);
        this.imageSwitch = this.findViewById(R.id.text_imageSwitch);
        this.imageSwitch.setOnClickListener(this);
        this.zoomImageLayout = this.findViewById(R.id.zoomImageLayout);
        this.zoomImageView = this.findViewById(R.id.take_picture_overlay);
        this.zoomImageClose = this.findViewById(R.id.zoomImageClose);
        this.zoomImageClose.setOnClickListener(this);
        this.findViewById(R.id.back).setOnClickListener(this);
        this.findViewById(R.id.language_setting).setOnClickListener(this);
        this.createLanguageDialog();
        this.createAddPictureDialog();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.takePicture) {
            this.takePicture();
        } else if (view.getId() == R.id.zoomImageClose) {
            this.zoomImageLayout.setVisibility(View.GONE);
            this.recycleBitmap();
        } else if (view.getId() == R.id.text_imageSwitch) {
            showAddPictureDialog();
        } else if (view.getId() == R.id.language_setting) {
            this.showLanguageDialog();
        } else if (view.getId() == R.id.simple_cn) {
            SharedPreferencesUtil.getInstance(this).
                    putStringValue(Constant.POSITION_KEY, Constant.POSITION_CN);
            languageDialog.dismiss();
            restartLensEngine(Constant.POSITION_CN);
        } else if (view.getId() == R.id.english) {
            SharedPreferencesUtil.getInstance(this).
                    putStringValue(Constant.POSITION_KEY, Constant.POSITION_EN);
            languageDialog.dismiss();
            restartLensEngine(Constant.POSITION_EN);
        } else if (view.getId() == R.id.japanese) {
            SharedPreferencesUtil.getInstance(this).
                    putStringValue(Constant.POSITION_KEY, Constant.POSITION_JA);
            languageDialog.dismiss();
            restartLensEngine(Constant.POSITION_JA);
        } else if (view.getId() == R.id.korean) {
            SharedPreferencesUtil.getInstance(this).
                    putStringValue(Constant.POSITION_KEY, Constant.POSITION_KO);
            languageDialog.dismiss();
            restartLensEngine(Constant.POSITION_KO);
        } else if (view.getId() == R.id.latin) {
            SharedPreferencesUtil.getInstance(this).
                    putStringValue(Constant.POSITION_KEY, Constant.POSITION_LA);
            languageDialog.dismiss();
            restartLensEngine(Constant.POSITION_LA);
        } else if (view.getId() == R.id.back) {
            finish();
        }
    }

    private void restartLensEngine(String type) {
        if(textType.equals(type)){
            return;
        }
        lensEngine.release();
        lensEngine = null;
        createLensEngine();
        startLensEngine();
        if (null != this.lensEngine) {
            this.mCamera = this.lensEngine.getCamera();
            try {
                this.mCamera.setPreviewDisplay(this.preview.getSurfaceHolder());
            } catch (IOException e) {
                Log.d(TextRecognitionActivity.TAG, "initViews IOException");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (this.zoomImageLayout.getVisibility() == View.VISIBLE) {
            this.zoomImageLayout.setVisibility(View.GONE);
            this.recycleBitmap();
        } else {
            super.onBackPressed();
        }
    }

    private void createLanguageDialog(){
        languageDialog = new Dialog(this, R.style.MyDialogStyle);
        View view = View.inflate(this, R.layout.dialog_language_setting, null);
        // Set up a custom layout
        languageDialog.setContentView(view);
        textCN = view.findViewById(R.id.simple_cn);
        textCN.setOnClickListener(this);
        textEN = view.findViewById(R.id.english);
        textEN.setOnClickListener(this);
        textJN = view.findViewById(R.id.japanese);
        textJN.setOnClickListener(this);
        textKN = view.findViewById(R.id.korean);
        textKN.setOnClickListener(this);
        textLN = view.findViewById(R.id.latin);
        textLN.setOnClickListener(this);
        languageDialog.setCanceledOnTouchOutside(true);
        // Set the size of the dialog
        Window dialogWindow = languageDialog.getWindow();
        WindowManager.LayoutParams layoutParams = dialogWindow.getAttributes();
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.BOTTOM;
        dialogWindow.setAttributes(layoutParams);
    }

    private void showLanguageDialog() {
        initDialogViews();
        languageDialog.show();
    }

    private void createAddPictureDialog(){
        addPictureDialog = new AddPictureDialog(this);
        final Intent intent = new Intent(TextRecognitionActivity.this, RemoteDetectionActivity.class);
        intent.putExtra(Constant.MODEL_TYPE, Constant.CLOUD_TEXT_DETECTION);
        addPictureDialog.setClickListener(new AddPictureDialog.ClickListener() {
            @Override
            public void takePicture() {
                intent.putExtra(Constant.ADD_PICTURE_TYPE, Constant.TYPE_TAKE_PHOTO);
                startActivity(intent);
            }

            @Override
            public void selectImage() {
                intent.putExtra(Constant.ADD_PICTURE_TYPE, Constant.TYPE_SELECT_IMAGE);
                startActivity(intent);
            }
        });
    }
    private void showAddPictureDialog() {
        addPictureDialog.show();
    }

    private void initDialogViews() {
        String position = SharedPreferencesUtil.getInstance(this).getStringValue(Constant.POSITION_KEY);
        textType = position;
        textCN.setSelected(false);
        textEN.setSelected(false);
        textJN.setSelected(false);
        textLN.setSelected(false);
        textKN.setSelected(false);
        switch (position) {
            case Constant.POSITION_CN:
                textCN.setSelected(true);
                break;
            case Constant.POSITION_EN:
                textEN.setSelected(true);
                break;
            case Constant.POSITION_LA:
                textLN.setSelected(true);
                break;
            case Constant.POSITION_JA:
                textJN.setSelected(true);
                break;
            case Constant.POSITION_KO:
                textKN.setSelected(true);
                break;
            default:
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(Constant.CAMERA_FACING, this.facing);
        super.onSaveInstanceState(outState);
    }


    private void createLensEngine() {
        if (this.lensEngine == null) {
            this.lensEngine = new LensEngine(this, this.cameraConfiguration, this.graphicOverlay);
        }
        try {
            this.localTextTransactor = new LocalTextTransactor(this.mHandler, this);
            this.lensEngine.setMachineLearningFrameTransactor(this.localTextTransactor);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Can not create image transactor: " + e.getMessage(),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void startLensEngine() {
        if (this.lensEngine != null) {
            try {
                this.preview.start(this.lensEngine, false);
            } catch (IOException e) {
                Log.e(TextRecognitionActivity.TAG, "Unable to start lensEngine.", e);
                this.lensEngine.release();
                this.lensEngine = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        this.startLensEngine();
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (this.lensEngine != null) {
            this.lensEngine.release();
        }
        this.recycleBitmap();
        this.facing = CameraConfiguration.CAMERA_FACING_BACK;
        this.cameraConfiguration.setCameraFacing(this.facing);
    }

    private void recycleBitmap() {
        if (this.bitmap != null && !this.bitmap.isRecycled()) {
            this.bitmap.recycle();
            this.bitmap = null;
        }
        if (this.bitmapCopy != null && !this.bitmapCopy.isRecycled()) {
            this.bitmapCopy.recycle();
            this.bitmapCopy = null;
        }
    }

    private void takePicture() {
        this.zoomImageLayout.setVisibility(View.VISIBLE);
        LocalDataManager localDataManager = new LocalDataManager();
        localDataManager.setLandScape(this.isLandScape);
        this.bitmap = BitmapUtils.getBitmap(this.localTextTransactor.getTransactingImage(), this.localTextTransactor.getTransactingMetaData());

        float previewWidth = localDataManager.getMaxWidthOfImage(this.localTextTransactor.getTransactingMetaData());
        float previewHeight = localDataManager.getMaxHeightOfImage(this.localTextTransactor.getTransactingMetaData());
        if (this.isLandScape) {
            previewWidth = localDataManager.getMaxHeightOfImage(this.localTextTransactor.getTransactingMetaData());
            previewHeight = localDataManager.getMaxWidthOfImage(this.localTextTransactor.getTransactingMetaData());
        }
        this.bitmapCopy = Bitmap.createBitmap(this.bitmap).copy(Bitmap.Config.ARGB_8888, true);

        Canvas canvas = new Canvas(this.bitmapCopy);
        float min = Math.min(previewWidth, previewHeight);
        float max = Math.max(previewWidth, previewHeight);

        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            localDataManager.setCameraInfo(this.graphicOverlay, canvas, min, max);
        } else {
            localDataManager.setCameraInfo(this.graphicOverlay, canvas, max, min);
        }
        localDataManager.drawHmsMLVisionText(canvas, this.localTextTransactor.getLastResults().getBlocks());
        this.zoomImageView.setImageBitmap(this.bitmapCopy);
    }
}

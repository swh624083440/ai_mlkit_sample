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

package com.huawei.hms.mlkit.vision.sample.transactor;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import com.huawei.hmf.tasks.Task;
import com.huawei.hms.mlkit.vision.sample.camera.FrameMetadata;
import com.huawei.hms.mlkit.vision.sample.util.Constant;
import com.huawei.hms.mlkit.vision.sample.views.overlay.GraphicOverlay;
import com.huawei.hms.mlkit.vision.sample.views.graphic.RemoteImageClassificationGraphic;
import com.huawei.hms.mlsdk.MLAnalyzerFactory;
import com.huawei.hms.mlsdk.common.MLFrame;
import com.huawei.hms.mlsdk.classification.MLRemoteClassificationAnalyzerSetting;
import com.huawei.hms.mlsdk.classification.MLImageClassification;
import com.huawei.hms.mlsdk.classification.MLImageClassificationAnalyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RemoteImageClassificationTransactor extends BaseTransactor<List<MLImageClassification>> {
    private static final String TAG = "RemoteImgClassification";

    private final MLImageClassificationAnalyzer detector;

    private Handler handler;

    public RemoteImageClassificationTransactor(Handler handler) {
        super();
        MLRemoteClassificationAnalyzerSetting options = new MLRemoteClassificationAnalyzerSetting.Factory().setMinAcceptablePossibility(0f).create();
        this.detector = MLAnalyzerFactory.getInstance().getRemoteImageClassificationAnalyzer(options);
        this.handler = handler;
    }

    @Override
    public void stop() {
        super.stop();
        try {
            this.detector.stop();
        } catch (IOException e) {
            Log.e(RemoteImageClassificationTransactor.TAG, "Exception thrown while trying to close cloud image classifier!", e);
        }
    }

    @Override
    protected Task<List<MLImageClassification>> detectInImage(MLFrame image) {
        return this.detector.asyncAnalyseFrame(image);
    }

    @Override
    protected void onSuccess(
            Bitmap originalCameraImage,
            List<MLImageClassification> classifications,
            FrameMetadata frameMetadata,
            GraphicOverlay graphicOverlay) {
        this.handler.sendEmptyMessage(Constant.GET_DATA_SUCCESS);
        graphicOverlay.clear();
        List<String> classificationList = new ArrayList<>();
        for (int i = 0; i < classifications.size(); ++i) {
            MLImageClassification classification = classifications.get(i);
            if (classification.getName() != null) {
                classificationList.add(classification.getName());
            }
        }
        RemoteImageClassificationGraphic remoteImageClassificationGraphic = new RemoteImageClassificationGraphic(graphicOverlay, classificationList);
        graphicOverlay.addGraphic(remoteImageClassificationGraphic);
        graphicOverlay.postInvalidate();
    }

    @Override
    protected void onFailure(Exception e) {
        Log.e(RemoteImageClassificationTransactor.TAG, "Cloud Image Classification failed " + e);
        this.handler.sendEmptyMessage(Constant.GET_DATA_FAILED);
    }
}
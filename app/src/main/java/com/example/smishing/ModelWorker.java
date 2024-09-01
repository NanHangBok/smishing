package com.example.smishing;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.DownloadType;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.flex.FlexDelegate;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class ModelWorker extends Worker {
    private static final String TAG = "ModelWorker";

    public ModelWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "ModelWorker 시작됨");

        if (FirebaseApp.getApps(getApplicationContext()).isEmpty()) {
            FirebaseApp.initializeApp(getApplicationContext());
            Log.d(TAG, "FirebaseApp 초기화됨");
        }

        String inputString = getInputData().getString("sequencesResult");
        if (inputString == null) {
            return Result.failure();
        }

        String[] stringArray = inputString.replaceAll("\\[", "").replaceAll("\\]", "").split(", ");
        float[] inputSequence = new float[stringArray.length];
        for (int i = 0; i < stringArray.length; i++) {
            inputSequence[i] = Float.parseFloat(stringArray[i]);
        }

        float[][] inputData = new float[1][inputSequence.length];
        System.arraycopy(inputSequence, 0, inputData[0], 0, inputSequence.length);

        CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder()
                .requireWifi()
                .build();

        try {
            CustomModel model = Tasks.await(
                    FirebaseModelDownloader.getInstance().getModel(
                            "smshing_2", DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, conditions));

            File modelFile = model.getFile();
            if (modelFile != null) {
                FlexDelegate flexDelegate = new FlexDelegate();
                Interpreter.Options options = new Interpreter.Options().addDelegate(flexDelegate);
                Interpreter interpreter = new Interpreter(modelFile, options);

                float[][] outputData = new float[1][1];
                interpreter.run(inputData, outputData);

                Log.d(TAG, "모델 실행 중");
                float result = outputData[0][0];
                int binaryOutput = result >= 0.7f ? 1 : 0;

                Data output = new Data.Builder()
                        .putInt("binaryOutput", binaryOutput)
                        .build();

                interpreter.close();
                flexDelegate.close();

                Log.d(TAG, "binaryOutput : " + binaryOutput);
                Log.d(TAG, "ModelWorker 작업 완료 결과 반환 : "+output);

                return Result.success(output);
            } else {
                return Result.failure();
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Result.failure();
        }
    }
}
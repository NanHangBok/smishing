package com.example.smishing;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import android.util.Base64;
import android.util.Log;

public class WordSequenceWorker extends Worker {

    public WordSequenceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String[] tokenizedTextArray = getInputData().getStringArray("tokens");

        float[] sequencesResult;

        try {
            URL url = new URL("https://us-central1-smishing-21d6a.cloudfunctions.net/word_sequence");

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("texts", new JSONArray(Arrays.asList(tokenizedTextArray)));

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                JSONObject jsonResponse = new JSONObject(response.toString());

                String base64EncodedString = jsonResponse.getString("sequences");

                byte[] decodedBytes = Base64.decode(base64EncodedString, Base64.DEFAULT);

                ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();

                sequencesResult = new float[floatBuffer.remaining()];
                floatBuffer.get(sequencesResult);

                // 결과 데이터 생성
                Data outputData = new Data.Builder()
                        .putString("sequencesResult", Arrays.toString(sequencesResult))
                        .build();

                Log.d("WordSequenceWorker","정수 시퀀스 : "+Arrays.toString(sequencesResult));
                return Result.success(outputData);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }
}
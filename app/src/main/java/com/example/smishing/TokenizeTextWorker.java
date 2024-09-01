package com.example.smishing;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TokenizeTextWorker extends Worker {

    public TokenizeTextWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 이전 작업에서 정제된 텍스트 받기
        String cleanedText = getInputData().getString("cleanedText");
        String functionUrl = "https://us-central1-smishing-21d6a.cloudfunctions.net/getTokens";

        Map<String, String> data = new HashMap<>();
        data.put("cleanedText", cleanedText);

        Gson gson = new Gson();
        String jsonData = gson.toJson(data);

        try {
            Log.d("WorkManager", "서버로 보내는 메시지: " + jsonData);
            URL url = new URL(functionUrl);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = httpURLConnection.getOutputStream()) {
                byte[] input = jsonData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                Map responseMap = gson.fromJson(response.toString(), Map.class);
                ArrayList<String> tokensList = (ArrayList<String>) responseMap.get("tokens");

                String[] tokens = tokensList.toArray(new String[0]);

                Data outputData = new Data.Builder()
                        .putStringArray("tokens", tokens)
                        .build();
                Log.d("WorkManager", "서버로부터 받은 응답: " + response.toString());

                Log.d("TokenizeWorker","토큰화 : " + Arrays.toString(tokens));
                return Result.success(outputData);
            }
        } catch (Exception e) {
            Log.e("TokenizeWorker", "Error: " + e.toString());
            return Result.failure();
        }
    }
}
package com.example.smishing;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.lifecycle.LifecycleService;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SmsProcessingService extends LifecycleService {
    private static final String TAG = "SmsProcessingService";
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand 호출됨");
        super.onStartCommand(intent, flags, startId);

        // 실시간 보호가 활성화된 경우에만 작업 수행
        if (isProtectionEnabled()) {
            if (intent != null) {
                FirebaseApp.initializeApp(this);
                String messageBody = intent.getStringExtra("messageBody");
                String senderNumber = intent.getStringExtra("senderNumber");
                String receiverNumber = intent.getStringExtra("receiverNumber");
                workEnqueue(this, messageBody,senderNumber,receiverNumber);
            }
        } else {
            Log.d(TAG, "실시간 보호 기능이 비활성화되어 있습니다.");
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private boolean isProtectionEnabled() {
        SharedPreferences preferences = getSharedPreferences("ProtectionSettings", MODE_PRIVATE);
        return preferences.getBoolean("ProtectionEnabled", true); // 기본값은 true로 설정
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void showNotification(String messageBody) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "sms_channel";
        String channelName = "SMS Channel";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
        notificationManager.createNotificationChannel(channel);

        Intent fullScreenIntent = new Intent(this, MainActivity.class);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
                fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("스미싱 경고")
                .setContentText(messageBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(fullScreenPendingIntent, true);

        // 알림 ID를 고유하게 생성하여 덮어쓰지 않도록 함
        int notificationId = (int) System.currentTimeMillis();  // 고유한 ID 생성
        notificationManager.notify(notificationId, builder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void workEnqueue(Context context, String messageBody, String senderNumber, String receiverNumber) {

        OneTimeWorkRequest cleanTextWorkRequest = new OneTimeWorkRequest.Builder(CleanTextWorker.class)
                .setInputData(new Data.Builder().putString("message", messageBody).build())
                .addTag("cleanTextTag")
                .build();

        // 토큰화 로직
        OneTimeWorkRequest tokenizeTextWorkRequest = new OneTimeWorkRequest.Builder(TokenizeTextWorker.class)
                .build();
        // 워드시퀀스 로직 -> 토큰화된 단어를 정수값으로 변환
        OneTimeWorkRequest wordSequenceWorkRequest = new OneTimeWorkRequest.Builder(WordSequenceWorker.class)
                .build();
        // 머신러닝 모델 실행
        OneTimeWorkRequest modelWorkRequest = new OneTimeWorkRequest.Builder(ModelWorker.class)
                .build();

        Log.d(TAG, "WorkManager: 작업 예약 전");

        WorkManager.getInstance(context)
                .beginWith(cleanTextWorkRequest)
                .then(tokenizeTextWorkRequest)
                .then(wordSequenceWorkRequest)
                .then(modelWorkRequest)
                .enqueue();


        Log.d(TAG, "WorkManager: 작업 예약됨");

        LiveData<WorkInfo> workInfoLiveData = WorkManager.getInstance(context).getWorkInfoByIdLiveData(modelWorkRequest.getId());

        workInfoLiveData.observe((LifecycleOwner) context, workInfo -> {
            if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                Log.d(TAG, "WorkManager 작업 완료됨");

                Data outputData = workInfo.getOutputData();
                int modelResult = outputData.getInt("binaryOutput", -1);

                Log.d(TAG, "ModelResultCallback 호출 예정: " + modelResult);

                handleResult(modelResult,messageBody,senderNumber,receiverNumber);
            }
        });
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void handleResult(int modelResult,String messageBody,String senderNumber, String receiverNumber) {
        Log.d(TAG, "모델 결과: " + modelResult);
        String strResult = modelResult==1?"스팸 위험":"안전한 메시지";
        Log.d(TAG, "NOTIFICATION 전송: " + modelResult);
        showNotification(strResult);
        if (modelResult == 1) { // 스미싱으로 판정된 경우
            uploadSpamMessageToFirestore(messageBody, senderNumber, receiverNumber); // 실행결과 스미싱 판정 시 DB 업로드
        }
    }

    private void uploadSpamMessageToFirestore(String messageBody, String senderNumber, String receiverNumber) {
        String currentTime = sdf.format(new Date());
        Map<String, Object> spamMessage = new HashMap<>();
        spamMessage.put("message", messageBody);
        spamMessage.put("sender", senderNumber);
        spamMessage.put("date", currentTime);
        spamMessage.put("receiver",receiverNumber);

        db.collection("auto_report_msg")
                .add(spamMessage);
    }
}

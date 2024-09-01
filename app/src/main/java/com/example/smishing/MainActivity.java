package com.example.smishing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;

    private TextView body_view;
    private TextView sender_view;
    private TextView report_count;
    private TextView auto_report_count;
    private Switch protectionSwitch;
    private Button report_button;
    private String receiverNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);

        // DB 연동 초기화
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // DB에 저장될 DATE 포맷
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // UI 요소 초기화
        protectionSwitch = findViewById(R.id.protectionSwitch);
        body_view = findViewById(R.id.body_view);
        sender_view = findViewById(R.id.sender_view);
        report_count = findViewById(R.id.report_count);
        auto_report_count = findViewById(R.id.auto_report_count);
        report_button = findViewById(R.id.report_button);

        // SMS 수신 권한 확인 및 요청
        checkAndRequestPermissions();

        // 본인 전화번호 가져오기
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            receiverNumber = null;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQUEST_CODE);
        } else {
            receiverNumber = tm.getLine1Number();
            if (receiverNumber != null && receiverNumber.startsWith("+")) {
                receiverNumber = receiverNumber.substring(1); // '+' 기호 제거
            }
            // 본인 번호로 DB에 저장되어 있는 내역 가져오기
            updateAutoReportCount("report_msg",receiverNumber); // REPORT_MSG >> 신고버튼을 통해 저장 >> 신고 개수
            updateAutoReportCount("auto_report_msg",receiverNumber); // AUTO_REPORT_MSG >> 머신러닝 결과를 통해 저장 >> 추정 개수
        }



        // SharedPreferences에서 현재 상태를 불러와 스위치 초기화
        SharedPreferences preferences = getSharedPreferences("ProtectionSettings", MODE_PRIVATE);
        boolean isProtectionEnabled = preferences.getBoolean("ProtectionEnabled", true);
        protectionSwitch.setChecked(isProtectionEnabled);

        protectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 상태 변경 시 SharedPreferences에 저장
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("ProtectionEnabled", isChecked);
            editor.apply();
        });

        // 신고하기 버튼 클릭 시
        report_button.setOnClickListener(v -> {
            // DB에 저장하기 위한 정보
            String currentTime = sdf.format(new Date());
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("sender", sender_view.getText()); // 발신자
            messageData.put("receiver", receiverNumber); // 수신자
            messageData.put("date", currentTime); // 버튼을 클릭한 현재 시간
            messageData.put("message", body_view.getText()); // MESSAGE BODY

            // DB에 저장
            db.collection("report_msg") // REPORT_MSG 에 저장
                    .add(messageData);

            // DB에 저장 후 COUNT 수정
            updateAutoReportCount("report_msg", receiverNumber);
            updateAutoReportCount("auto_report_msg", receiverNumber);
        });

        registerReceiver(smsReceiver, new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
    }
    // 메시지 수신 시 로직
    private BroadcastReceiver smsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                StringBuilder fullMessage = new StringBuilder();
                String senderNumber = messages[0].getDisplayOriginatingAddress(); // 발신자 번호 가져오기

                // 메시지가 길어질 경우 한번에 수신하지 못하는 경우가 있을 수 있음
                for (SmsMessage message : messages) {
                    fullMessage.append(message.getDisplayMessageBody());
                }

                String messageBody = fullMessage.toString();
                Log.d(TAG, "messageBody : " + messageBody);

                // 실시간 보호 기능이 활성화된 경우에만 SmsProcessingService 실행
                SharedPreferences preferences = getSharedPreferences("ProtectionSettings", MODE_PRIVATE);
                boolean isProtectionEnabled = preferences.getBoolean("ProtectionEnabled", true);

                if (isProtectionEnabled) {
                    // 받은 메시지를 표시
                    sender_view.setText(senderNumber);
                    body_view.setText(messageBody);

                    // SmsProcessingService로 메시지 전달하여 서비스 시작
                    Intent serviceIntent = new Intent(context, SmsProcessingService.class);
                    serviceIntent.putExtra("messageBody", messageBody); // MESSAGE BODY
                    serviceIntent.putExtra("senderNumber", senderNumber); // 발신자
                    serviceIntent.putExtra("receiverNumber", receiverNumber); // 수신자
                    context.startService(serviceIntent);  // 서비스 시작

                    // COUNT 수정
                    updateAutoReportCount("report_msg", receiverNumber);
                    updateAutoReportCount("auto_report_msg", receiverNumber);
                } else {
                    Log.d(TAG, "실시간 보호 기능이 비활성화되어 있어서 서비스가 시작되지 않음");
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(smsReceiver);
    }

    // 필수 퍼미션 체크
    private void checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_NUMBERS
        };

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 모든 권한이 승인되었는지 확인
            Map<String, Integer> perms = new HashMap<>();
            perms.put(Manifest.permission.RECEIVE_SMS, PackageManager.PERMISSION_GRANTED);
            perms.put(Manifest.permission.READ_PHONE_STATE, PackageManager.PERMISSION_GRANTED);

            // 결과를 채우기
            for (int i = 0; i < permissions.length; i++) {
                perms.put(permissions[i], grantResults[i]);
            }

            // 권한이 모두 승인되었는지 확인
            if (perms.get(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                    && perms.get(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                // 권한이 모두 승인되었을 때의 처리
                Toast.makeText(this, "모든 권한이 승인되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                // 권한 중 일부 또는 전체가 거부된 경우의 처리
                Toast.makeText(this, "권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateAutoReportCount(String tableName,String phoneNumber) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 현재 날짜를 가져와 Firestore의 날짜 필드 형식에 맞게 포맷
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        int currentMonth = calendar.get(Calendar.MONTH) + 1; // Calendar에서 월은 0부터 시작하므로 +1
        String startDate = String.format("%04d-%02d-01", currentYear, currentMonth); // EX) 2024-08-01

        // Firestore에서 현재 사용자의 전화번호와 이번 달에 해당하는 문서를 쿼리
        db.collection(tableName)
                .whereEqualTo("receiver", phoneNumber) // 수신자의 번호로
                .whereGreaterThanOrEqualTo("date", startDate) // 이번달 1일보다 크거나 같은
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int count = task.getResult().size(); // 일치하는 문서 개수 가져오기
                        if(tableName.equals("report_msg")) // REPORT_MSG 개수
                            report_count.setText(String.valueOf(count));
                        else auto_report_count.setText(String.valueOf(count)); // AUTO_REPORT_MSG 개수
                    } else {
                        Log.w(TAG, "문서 가져오기 실패.", task.getException());
                    }
                });
    }

}
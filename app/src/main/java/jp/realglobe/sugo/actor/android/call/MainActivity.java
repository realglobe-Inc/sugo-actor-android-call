package jp.realglobe.sugo.actor.android.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jp.realglobe.sugo.actor.Actor;
import jp.realglobe.sugo.actor.Emitter;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getName();

    private static final long LOCATION_DETECTOR_INTERVAL = 10_000L;

    // 送信イベント
    private static final String EVENT_EMERGENCY = "emergency";

    // 送信データのキー
    private static final String KEY_LOCATION = "location";
    private static final String KEY_DATE = "date";
    private static final String KEY_ID = "id";
    private static final String KEY_PHONE_NUMBER = "phoneNumber";

    private static final int PERMISSION_REQUEST_CODE = 24876;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_SMS,
            Manifest.permission.CALL_PHONE,
    };

    private GoogleApiClient googleApiClient;

    private LinkedList<String> errors;

    // 現在位置
    private volatile Location location;
    // 電話番号
    private volatile String phoneNumber;

    private Actor actor;
    private int reportId;

    private TextView messageView;
    private Button callButton;
    private Button talkButton;
    private Button resetButton;
    private TextView errorView;
    private Handler handler;

    /**
     * 位置情報取得モジュールを設定
     *
     * @param interval      位置情報を何ミリ秒ごとに更新するか
     * @param context       コンテクスト
     * @param listener      位置情報を受け取る関数
     * @param errorCallback エラー時に呼ばれる関数
     * @return 位置情報取得モジュール
     */
    private static GoogleApiClient setupLocationClient(long interval, Context context, LocationListener listener, StringCallback errorCallback) {
        final AtomicReference<GoogleApiClient> client = new AtomicReference<>();
        client.set((new GoogleApiClient.Builder(context))
                .addApi(LocationServices.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        LocationServices.FusedLocationApi.requestLocationUpdates(
                                client.get(),
                                LocationRequest.create()
                                        .setInterval(interval)
                                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY),
                                location -> {
                                    Log.d(LOG_TAG, "Location changed to " + location);
                                    if (listener != null) {
                                        listener.onLocationChanged(location);
                                    }
                                });
                        Log.d(LOG_TAG, "Location monitor started");
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.d(LOG_TAG, "Location monitor suspended");
                    }
                })
                .addOnConnectionFailedListener(connectionResult -> {
                    final String warning = "Location detector error: " + connectionResult;
                    Log.w(LOG_TAG, warning);
                    if (errorCallback != null) {
                        errorCallback.call(connectionResult.getErrorMessage());
                    }
                })
                .build());
        return client.get();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初回に actor ID を生成する
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String actorSuffix = preferences.getString(getString(R.string.key_actor_suffix), null);
        if (actorSuffix == null) {
            preferences.edit().putString(getString(R.string.key_actor_suffix), String.valueOf(Math.abs((new Random(System.currentTimeMillis())).nextInt()))).apply();
        }

        // 位置情報の取得準備
        this.googleApiClient = setupLocationClient(LOCATION_DETECTOR_INTERVAL, this, location -> this.location = location, this::showError);

        this.errors = new LinkedList<>();

        this.reportId = Math.abs((new Random(System.currentTimeMillis())).nextInt());

        setContentView(R.layout.activity_main);

        this.messageView = (TextView) findViewById(R.id.text_message);
        this.callButton = (Button) findViewById(R.id.button_call);
        this.talkButton = (Button) findViewById(R.id.button_talk);
        this.resetButton = (Button) findViewById(R.id.button_reset);
        this.errorView = (TextView) findViewById(R.id.text_error);
        this.handler = new Handler();

        this.callButton.setOnClickListener(view -> call());
        this.talkButton.setOnClickListener(view -> talk());
        this.resetButton.setOnClickListener(view -> reset());

        reset();

        checkPermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.item_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (item.getItemId() == R.id.item_allow) {
            checkPermission();
        }
        return true;
    }

    /**
     * エラーを表示する
     *
     * @param error エラーを表す文言
     */
    private void showError(String error) {
        this.errors.add(error);
        this.errorView.post(() -> this.errorView.setText(error));
    }

    /**
     * 必要な許可を取得しているか調べて、取得していなかったら要求する
     */
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 位置情報には許可が必要。
            this.requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        } else {
            showPermissionStatus(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }

        final Set<String> required = new HashSet<>(Arrays.asList(REQUIRED_PERMISSIONS));
        for (int i = 0; i < permissions.length; i++) {
            if (!required.contains(permissions[i])) {
                continue;
            }
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                continue;
            }
            required.remove(permissions[i]);
        }

        if (!required.contains(Manifest.permission.READ_SMS)) {
            final TelephonyManager manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            this.phoneNumber = manager.getLine1Number();
            Log.i(LOG_TAG, "Phone number is " + this.phoneNumber);
        }

        showPermissionStatus(required.isEmpty());
    }

    /**
     * 許可の取得状態を表示する
     *
     * @param allowed 取得できているなら true
     */
    private void showPermissionStatus(boolean allowed) {
        final String message;
        if (allowed) {
            message = "適切な情報を利用できます";
        } else {
            message = "適切な情報を利用できません\nメニューから許可設定を行ってください";
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private synchronized void reset() {
        if (this.actor != null) {
            this.actor.disconnect();
            this.actor = null;
        }
        this.googleApiClient.disconnect();

        this.messageView.setText("");

        this.callButton.setEnabled(true);
        this.callButton.setVisibility(View.VISIBLE);

        this.talkButton.setEnabled(false);
        this.talkButton.setVisibility(View.INVISIBLE);

        this.resetButton.setEnabled(false);
        this.resetButton.setVisibility(View.INVISIBLE);
    }

    private synchronized void call() {
        this.messageView.setText(getString(R.string.message_called));

        this.callButton.setEnabled(false);
        this.callButton.setVisibility(View.VISIBLE);

        this.talkButton.setEnabled(true);
        this.talkButton.setVisibility(View.VISIBLE);

        this.resetButton.setEnabled(true);
        this.resetButton.setVisibility(View.VISIBLE);

        if (!(this.googleApiClient.isConnecting() || this.googleApiClient.isConnected())) {
            this.googleApiClient.connect();
        }
        report();
        talk();
    }

    /**
     * 通報データを hub に送る
     */
    private void report() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String server = preferences.getString(getString(R.string.key_server), getString(R.string.default_server));
        final String key = getString(R.string.actor_prefix) + preferences.getString(getString(R.string.key_actor_suffix), getString(R.string.default_actor_suffix));

        this.actor = new Actor(key, getString(R.string.name), null);
        final Emitter emitter;
        try {
            emitter = this.actor.addModule(getString(R.string.name), getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName, getString(R.string.description), new Object());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException();
        }
        this.actor.setOnConnect(() -> reportRoutine(emitter, nextReportId()));
        this.actor.connect(server);
    }

    private synchronized int nextReportId() {
        if (this.reportId < 0) {
            this.reportId = 0;
        }
        return this.reportId++;
    }

    private void reportRoutine(Emitter emitter, int reportId) {
        if (this.actor == null) {
            // 終了
            return;
        }

        final Map<String, Object> data = new HashMap<>();
        data.put(KEY_ID, reportId);
        data.put(KEY_DATE, (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.US)).format(new Date()));
        final Location curLocation = this.location;
        if (curLocation != null) {
            data.put(KEY_LOCATION, Arrays.asList(curLocation.getLatitude(), curLocation.getLongitude(), curLocation.getAltitude()));
        } else {
            data.put(KEY_LOCATION, null);
        }
        data.put(KEY_PHONE_NUMBER, this.phoneNumber);
        emitter.emit(EVENT_EMERGENCY, data);
        Log.d(LOG_TAG, "Sent report");

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final long interval = 1_000L * Long.parseLong(preferences.getString(getString(R.string.key_report_interval), String.valueOf(getResources().getInteger(R.integer.default_report_interval))));
        this.handler.postDelayed(() -> reportRoutine(emitter, reportId), interval);
    }

    /**
     * 電話を掛ける
     */
    private void talk() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String phoneNumber = preferences.getString(getString(R.string.key_phone_number), null);
        if (phoneNumber == null) {
            Log.e(LOG_TAG, "No phone number");
            showError("通報先電話番号が設定されていません");
            return;
        }
        Log.d(LOG_TAG, "Call " + phoneNumber);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber)));
            return;
        }
        startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber)));
    }


    private interface StringCallback {
        void call(String error);
    }

}

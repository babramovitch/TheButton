package com.nebulights.thebutton;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import de.greenrobot.event.EventBus;

import java.io.IOException;
import java.net.URI;
import java.util.Random;

public class MainActivity extends Activity {

    private TextView timer;
    private TextView participants;

    private EditText alertEditText;

    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;

    private int notificationId = new Random().nextInt();
    private TypedArray timeImages;
    private URI theButton;

    private boolean shutDownSocket = false;
    private boolean fullShutDown = false;

    private OkHttpClient client = new OkHttpClient();

    private CheckBox checkBox;
    private SharedPreferences prefs;

    int[] buttonColors;
    int alertInt;
    private Context context;

    Ringtone ringtone;

    boolean alertInitiated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;

        //Crashlytics.start(this);
        EventBus.getDefault().register(this);
        setContentView(R.layout.activity_main);

        buttonColors = this.getResources().getIntArray(R.array.colors);

        prefs = this.getSharedPreferences("com.nebulights.thebutton", Context.MODE_PRIVATE);
        timer = (TextView) findViewById(R.id.timer);
        participants = (TextView) findViewById(R.id.participants);
        timeImages = getResources().obtainTypedArray(R.array.time_images);
        checkBox = (CheckBox) findViewById(R.id.checkBoxUpdates);

        boolean foundMusic = prefs.getBoolean("foundMusic", false);
        if (!foundMusic)
            Toast.makeText(this, "Press the notes to change the alert tone", Toast.LENGTH_LONG).show();

        String ringToneString = prefs.getString("ringtone", "");

        Uri notification;
        if (!ringToneString.equals("")) {
            notification = Uri.parse(ringToneString);
        } else {
            notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);

        ImageView button = (ImageView) findViewById(R.id.imageButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectRingTones();
            }
        });

        ImageView button2 = (ImageView) findViewById(R.id.imageButton2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectRingTones();
            }
        });

        boolean disableUpdates = prefs.getBoolean("disable", true);

        if (disableUpdates) {
            checkBox.setChecked(true);
            registerReceiver(mybroadcast, new IntentFilter(Intent.ACTION_SCREEN_ON));
            registerReceiver(mybroadcast, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }

        alertEditText = (EditText) findViewById(R.id.edit_text);
        String savedAlertValue = prefs.getString("alert", "");
        alertEditText.setText(savedAlertValue);

        if (savedAlertValue.length() > 0) {
            alertInt = Integer.valueOf(savedAlertValue);
        } else {
            alertInt = -1;
        }

        alertEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString("alert", s.toString()).apply();
                if (s.length() > 0) {
                    alertInt = Integer.valueOf(s.toString());
                } else {
                    alertInt = -1;
                }
            }
        });

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                @Override
                                                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                    if (isChecked) {
                                                        registerReceiver(mybroadcast, new IntentFilter(Intent.ACTION_SCREEN_ON));
                                                        registerReceiver(mybroadcast, new IntentFilter(Intent.ACTION_SCREEN_OFF));
                                                        prefs.edit().putBoolean("disable", true).apply();
                                                    } else {
                                                        unregisterReceiver(mybroadcast);
                                                        prefs.edit().putBoolean("disable", false).apply();
                                                    }
                                                }
                                            }
        );

        setupNotification();

        GetWebSocketLink getWebSocketLink = new GetWebSocketLink();
        getWebSocketLink.execute(getString(R.string.socket_url));

    }

    private void selectRingTones() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
        startActivityForResult(intent, 5);
    }

    private void setupNotification() {

        PendingIntent dismissIntent = NotificationActivity.getDismissIntent(notificationId, this);
        PendingIntent gotoButtonIntent = NotificationActivity.gotoButton(this);



        notificationBuilder =
                new NotificationCompat.Builder(this)
                        .addAction(R.drawable.ic_action_web_site, getString(R.string.goto_button), gotoButtonIntent)
                        .addAction(R.drawable.ic_action_cancel, getString(R.string.close_button), dismissIntent)
                        .setSmallIcon(R.drawable.sixty)
                        .setContentIntent(gotoButtonIntent)
                        .setContentTitle(getString(R.string.thebutton));


        notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notificationManager.notify(notificationId, notificationBuilder.build());

    }

    private class GetWebSocketLink extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {

            try {
                Request request = new Request.Builder()
                        .url(urls[0])
                        .build();

                Response response = client.newCall(request).execute();
                return response.body().string();
            } catch (IOException e) {
                Log.i("OKHTTP", "Error reading webpage", e);
            }

            return "error";
        }

        @Override
        protected void onPostExecute(String result) {
            if (!result.equals("error")) {
                theButton = URI.create(result);
                initWebSocket();
            } else {
                timer.setText(getString(R.string.cannot_get_socket_link));
            }
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {

        prefs.edit().putBoolean("foundMusic", true).apply();

        if (resultCode == Activity.RESULT_OK && requestCode == 5) {
            Uri uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

            if (uri != null) {
                ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
                prefs.edit().putString("ringtone", uri.toString()).apply();

            }
        }
    }

    private void initWebSocket() {

        //{"type": "ticking", "payload": {"participants_text": "605,765", "tick_mac": "2736490ef88a6bc53b5d6ae57a0caf0684aeee5b", "seconds_left": 58.0, "now_str": "2015-04-06-00-57-00"}}


        AsyncHttpClient.getDefaultInstance().websocket(String.valueOf(theButton), "my-protocol", new AsyncHttpClient.WebSocketConnectCallback() {

            @Override
            public void onCompleted(Exception ex, final WebSocket webSocket) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }

                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    public void onStringAvailable(String s) {
                        //System.out.println(s);
                        JsonObject newObj = new JsonParser().parse(s).getAsJsonObject();
                        final String time = newObj.get("payload").getAsJsonObject().get("seconds_left").getAsString().substring(0, 2);
                        final String users = newObj.get("payload").getAsJsonObject().get("participants_text").getAsString();
                        final int intTime = newObj.get("payload").getAsJsonObject().get("seconds_left").getAsInt();

                        if (shutDownSocket) {
                            webSocket.end();

                            if (fullShutDown) {
                                notificationManager.cancelAll();
                                finish();
                            }
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    timer.setText(time);

                                    if (intTime != 0) {
                                        timer.setTextColor(buttonColors[(intTime / 10)]);
                                    }

                                    participants.setText(users + getString(R.string.participants));
                                    notificationBuilder.setContentText(time);
                                    notificationBuilder.setSmallIcon(timeImages.getResourceId(60 - intTime, -1));

                                    if (!shutDownSocket)
                                        notificationManager.notify(notificationId, notificationBuilder.build());

                                    if (alertInitiated && intTime >= alertInt) {
                                        alertInitiated = false;
                                    }

                                    if (alertInt != -1 && intTime <= alertInt && !alertInitiated) {
                                        alertInitiated = true;
                                        try {
                                            ringtone.play();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                    }
                                }
                            });
                        }
                    }
                });

                webSocket.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                        Log.i("test", "Did something happen here?");
                        byteBufferList.recycle();
                    }
                });

            }
        });
    }

    BroadcastReceiver mybroadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                shutDownSocket = false;
                fullShutDown = false;
                GetWebSocketLink task = new GetWebSocketLink();
                task.execute(getString(R.string.socket_url));
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                EventBus.getDefault().post(new ShutDownEvent(false));
            }
        }
    };

    public void onEvent(InternetConnectedEvent event) {

        if (event.isConnected()) {
            timer.setText(getString(R.string.connecting));
            GetWebSocketLink task = new GetWebSocketLink();
            task.execute(getString(R.string.socket_url));
        } else {
            timer.setText(getString(R.string.disconnected));
        }
    }

    public void onEvent(ShutDownEvent event) {
        shutDownSocket = true;
        fullShutDown = event.isFullshutdown();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        shutDownSocket = true;
        notificationManager.cancelAll();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        notificationManager.cancelAll();
        ringtone = null;
        if (checkBox.isChecked()) {
            unregisterReceiver(mybroadcast);
        }
    }
}

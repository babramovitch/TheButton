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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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

public class MainActivity extends ActionBarActivity {

    private Toolbar toolbar;

    private TextView timer;
    private TextView participants;

    private EditText alertEditText;

    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;

    private int notificationId = new Random().nextInt();
    private TypedArray timeImages;
    private URI theButtonURL;

    private boolean shutDownSocket = false;
    private boolean fullShutDown = false;
    private boolean screenOn = true;

    private OkHttpClient client = new OkHttpClient();

    private CheckBox disableUpdatesIfScreenOffCheckBox;
    private CheckBox enableUpdatesWhenPowerConnected;
    private SharedPreferences prefs;
    SharedPreferences prefsFromSettings;

    boolean disableUpdates;
    boolean enableUpdatesWhenConnectedToPower;

    int[] buttonColors;
    int alertInt;
    int actionBarColor;

    Ringtone ringtone;

    boolean alertInitiated = false;
    boolean powerConnected = false;

    ImageView musicNoteOne, musicNoteTwo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Crashlytics.start(this);
        EventBus.getDefault().register(this);
        setContentView(R.layout.activity_main);


        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("The Button");

        setSupportActionBar(toolbar);

        buttonColors = this.getResources().getIntArray(R.array.colors);


        Drawable colorDrawable = new ColorDrawable(buttonColors[6]);
        getSupportActionBar().setBackgroundDrawable(colorDrawable);

        prefsFromSettings = PreferenceManager.getDefaultSharedPreferences(this);


        prefs = this.getSharedPreferences("com.nebulights.thebutton", Context.MODE_PRIVATE);
        timer = (TextView) findViewById(R.id.timer);
        participants = (TextView) findViewById(R.id.participants);
        timeImages = getResources().obtainTypedArray(R.array.time_images);
        disableUpdatesIfScreenOffCheckBox = (CheckBox) findViewById(R.id.checkBoxUpdates);
        enableUpdatesWhenPowerConnected = (CheckBox) findViewById(R.id.checkBoxEnableUpdatesWhenPowerConnected);

        if (PowerUtil.isConnected(this)) {
            powerConnected = true;
        }

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

        musicNoteOne = (ImageView) findViewById(R.id.imageButton);
        musicNoteOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectRingTones();
            }
        });

        musicNoteTwo = (ImageView) findViewById(R.id.imageButton2);
        musicNoteTwo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectRingTones();
            }
        });

        disableUpdates = prefs.getBoolean("disable", true);
        enableUpdatesWhenConnectedToPower = prefs.getBoolean("enableUpdatesWhenConnectedToPower", false);

        registerReceiver(screenBroadcast, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        registerReceiver(screenBroadcast, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));
        registerReceiver(screenBroadcast, new IntentFilter(Intent.ACTION_SCREEN_ON));
        registerReceiver(screenBroadcast, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        if (disableUpdates) {
            disableUpdatesIfScreenOffCheckBox.setChecked(true);

            if (enableUpdatesWhenConnectedToPower) {
                enableUpdatesWhenPowerConnected.setChecked(true);
            }

        } else {
            enableUpdatesWhenPowerConnected.setVisibility(View.GONE);
            enableUpdatesWhenPowerConnected.setChecked(false);
        }

        alertEditText = (EditText) findViewById(R.id.edit_text);
        String savedAlertValue = prefs.getString("alert", "");

        alertEditText.setText(savedAlertValue);

        if (savedAlertValue.length() > 0) {
            alertInt = Integer.valueOf(savedAlertValue);
            if(alertInt <= 60) {
                setMusicNoteColors(buttonColors[(alertInt / 10)]);
            }
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

                    if (alertInt <= 60 && alertInt > 0) {
                        setMusicNoteColors(buttonColors[(alertInt / 10)]);
                    }else{
                        setMusicNoteColors(Color.BLACK);
                    }

                } else {
                    alertInt = -1;
                    setMusicNoteColors(Color.BLACK);
                }
            }
        });

        disableUpdatesIfScreenOffCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                                         @Override
                                                                         public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                                             if (isChecked) {//
                                                                                 disableUpdates = true;
                                                                                 enableUpdatesWhenPowerConnected.setVisibility(View.VISIBLE);
                                                                                 prefs.edit().putBoolean("disable", true).apply();
                                                                             } else {
                                                                                 disableUpdates = false;
                                                                                 enableUpdatesWhenPowerConnected.setVisibility(View.GONE);
                                                                                 if (enableUpdatesWhenPowerConnected.isChecked()) {
                                                                                     enableUpdatesWhenPowerConnected.setChecked(false);
                                                                                 }
                                                                                 prefs.edit().putBoolean("disable", false).apply();
                                                                             }
                                                                         }
                                                                     }
        );

        enableUpdatesWhenPowerConnected.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                                       @Override
                                                                       public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                                           if (isChecked) {
                                                                               enableUpdatesWhenConnectedToPower = true;
                                                                               prefs.edit().putBoolean("enableUpdatesWhenConnectedToPower", true).apply();
                                                                           } else {
                                                                               enableUpdatesWhenConnectedToPower = false;
                                                                               prefs.edit().putBoolean("enableUpdatesWhenConnectedToPower", false).apply();
                                                                           }
                                                                       }
                                                                   }
        );

        setupNotification();

        GetWebSocketLink getWebSocketLink = new GetWebSocketLink();
        getWebSocketLink.execute(getString(R.string.socket_url));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        //Launch our settings activity here
        if (id == R.id.ic_action_settings) {
            Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class);
            MainActivity.this.startActivity(myIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setMusicNoteColors(int color){
        musicNoteOne.setColorFilter(color);
        musicNoteTwo.setColorFilter(color);
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
               try {
                   theButtonURL = URI.create(result);
                   initWebSocket();
               }catch(Exception e){
                   Log.e("TheButton", "Exception creating URI", e);
                   timer.setText("Error Connecting. Please close the app with back button and try again in 10 seconds. There is a known issue with me updating the websocket link twice daily, and looking into solutions.  If this persits for more than a moment please contact us");
                   timer.setTextSize(10f);
               }
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


        AsyncHttpClient.getDefaultInstance().websocket(String.valueOf(theButtonURL), "my-protocol", new AsyncHttpClient.WebSocketConnectCallback() {

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
                                    int color = buttonColors[6];
                                    if (intTime != 0) {
                                        color = buttonColors[(intTime / 10)];
                                        timer.setTextColor(color);
                                        notificationBuilder.setColor(color);
                                        EventBus.getDefault().postSticky(new ActionBarColorEvent(color));

                                    }

                                    participants.setText(users + getString(R.string.participants));
                                    notificationBuilder.setContentText(time);
                                    notificationBuilder.setSmallIcon(timeImages.getResourceId(60 - intTime, -1));

                                    //Experimental.
                                    //I cancel the notification since not cancelling it caused
                                    //notifications from different colors to bleed into each other.
                                    if(prefsFromSettings.getBoolean("pref_led",false) && !screenOn){
                                        if(intTime != 0) {
                                            notificationBuilder.setLights(buttonColors[(intTime / 10)], 500, 1000);
                                            notificationManager.cancel(notificationId);
                                        }
                                    }


                                    if (!shutDownSocket)
                                        notificationManager.notify(notificationId, notificationBuilder.build());

                                    if (alertInitiated && intTime >= alertInt) {
                                        alertInitiated = false;
                                    }

                                    if (alertInt != -1 && intTime <= alertInt && !alertInitiated) {
                                        alertInitiated = true;
                                        try {
                                            ringtone.play();
                                            if(prefsFromSettings.getBoolean("pref_sync",false)) {
                                                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                                                v.vibrate(750);
                                            }
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

    BroadcastReceiver screenBroadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            switch (intent.getAction()) {

                case Intent.ACTION_SCREEN_ON:
                    screenOn = true;
                    if (disableUpdates) {
                        shutDownSocket = false;
                        fullShutDown = false;
                        GetWebSocketLink task = new GetWebSocketLink();
                        task.execute(getString(R.string.socket_url));
                    }
                    break;

                case Intent.ACTION_SCREEN_OFF:
                    screenOn = false;
                    if (disableUpdates) {
                        if (!enableUpdatesWhenConnectedToPower || (enableUpdatesWhenConnectedToPower && !powerConnected)) {
                            EventBus.getDefault().post(new ShutDownEvent(false));
                        }
                    }
                    break;

                case Intent.ACTION_POWER_CONNECTED:

                    if (enableUpdatesWhenConnectedToPower) {
                        powerConnected = true;
                    }
                    break;

                case Intent.ACTION_POWER_DISCONNECTED:

                /*
                Note: The screen turns itself ON when you disconnect the power, so tracking
                what happens if it's off and then disconnected isn't required.
                */
                    if (enableUpdatesWhenConnectedToPower) {
                        powerConnected = false;
                    }

                    break;
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

    public void onEventMainThread(ActionBarColorEvent event) {
        actionBarColor = event.getColor();
        Drawable colorDrawable = new ColorDrawable(actionBarColor);
        getSupportActionBar().setBackgroundDrawable(colorDrawable);
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
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(screenBroadcast);
        EventBus.getDefault().unregister(this);
        notificationManager.cancelAll();
        ringtone = null;


    }
}

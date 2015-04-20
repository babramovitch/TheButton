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

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import com.nebulights.thebutton.events.SetCurrentColorEvent;
import com.nebulights.thebutton.events.CurrentTimeEvent;
import com.nebulights.thebutton.events.InternetConnectedEvent;
import com.nebulights.thebutton.events.ShutDownEvent;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import de.greenrobot.event.EventBus;

import java.io.IOException;
import java.net.URI;
import java.util.Random;

public class MainActivity extends ActionBarActivity {

    private TextView timer;
    private TextView participants;

    private ImageView musicNoteOne, musicNoteTwo;

    private CheckBox disableUpdatesIfScreenOffCheckBox;
    private CheckBox enableUpdatesWhenPowerConnectedCheckBox;

    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;

    private TypedArray timeImages;
    private URI theButtonURL;

    private boolean shutDownSocket = false;
    private boolean fullShutDown = false;
    private boolean screenOn = true;
    private boolean alertInitiated = false;
    private boolean powerConnected = false;
    private boolean disableUpdates;
    private boolean enableUpdatesWhenConnectedToPower;

    private int notificationId = new Random().nextInt();
    private int alertInt;
    private int currentColor;

    private OkHttpClient client = new OkHttpClient();
    private final JsonParser jsonParser = new JsonParser();
    private Ringtone ringtone;

    private SharedPreferences prefs;
    private SharedPreferences prefsFromSettings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Crashlytics.start(this);
        EventBus.getDefault().register(this);
        setContentView(R.layout.activity_main);

        try {
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            toolbar.setTitle("The Button");
            setSupportActionBar(toolbar);

            Drawable colorDrawable = new ColorDrawable(ButtonColors.getButtonColor(60));
            getSupportActionBar().setBackgroundDrawable(colorDrawable);
        } catch (Exception e) {
            //Issue in Appcompat library impacting Samsung 4.2.2 devices.
            //https://code.google.com/p/android/issues/detail?can=2&start=0&num=100&q=&colspec=ID%20Type%20Status%20Owner%20Summary%20Stars&groupby=&sort=&id=78377
            Toast.makeText(this, "Android issue with actionbar on Samsung 4.2.2 devices. Fix failed. WHOOPS!", Toast.LENGTH_LONG).show();
            Crashlytics.logException(e);
        }

        prefsFromSettings = PreferenceManager.getDefaultSharedPreferences(this);
        prefs = this.getSharedPreferences("com.nebulights.thebutton", Context.MODE_PRIVATE); //Whoops, made this, then did preference fragments later and now have both.

        timeImages = getResources().obtainTypedArray(R.array.time_images);
        timer = (TextView) findViewById(R.id.timer);
        participants = (TextView) findViewById(R.id.participants);

        registerReceiver(screenBroadcast, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        registerReceiver(screenBroadcast, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));
        registerReceiver(screenBroadcast, new IntentFilter(Intent.ACTION_SCREEN_ON));
        registerReceiver(screenBroadcast, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        setSavedButtonLink();
        setupAlerts();
        setupPowerOptions();
        setupNotification();

        if (savedInstanceState == null) {
            ScrapeWebSocketLink getWebSocketLink = new ScrapeWebSocketLink();
            getWebSocketLink.execute(getString(R.string.socket_url));
            timer.setText("Connecting...");
        } else {
            timer.setText(savedInstanceState.getString("timer", "Connecting..."));
            participants.setText(savedInstanceState.getString("participants", ""));
            EventBus.getDefault().post(new SetCurrentColorEvent(savedInstanceState.getInt("currentColor", Color.BLACK)));
        }


    }

    private void setSavedButtonLink() {
        String url = prefs.getString("wsslink", null);
        if (url != null && url.length() != 0) {
            try {
                theButtonURL = URI.create(url);
            } catch (Exception e) {
                Log.e("TheButton", "Error generating URI", e);
            }
        }
    }

    private void setupAlerts() {

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

        String savedAlertValue = prefs.getString("alert", "");

        if (savedAlertValue.length() > 0) {
            alertInt = Integer.valueOf(savedAlertValue);
            if (alertInt <= 60) {
                setMusicNoteColors(ButtonColors.getButtonColor(alertInt));
            }
        } else {
            alertInt = -1;
        }

        EditText alertEditText = (EditText) findViewById(R.id.edit_text);
        alertEditText.setText(savedAlertValue);
        alertEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString("alert", s.toString()).apply();
                if (s.length() > 0 && isNumeric(s)) {
                    alertInt = Integer.valueOf(s.toString());

                    if (alertInt <= 60 && alertInt > 0) {
                        setMusicNoteColors(ButtonColors.getButtonColor(alertInt));
                    } else {
                        setMusicNoteColors(Color.BLACK);
                    }

                } else {
                    alertInt = -1;
                    setMusicNoteColors(Color.BLACK);
                }
            }
        });

    }

    private boolean isNumeric(CharSequence str) {
        //Somehow, someone managed to paste/insert a letter into the number only edit text.
        //I'm not sure how but this is here because of YOU.
        try {
            int i = Integer.valueOf(str.toString());
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }

    private void setupPowerOptions() {

        if (PowerUtil.isConnected(this)) {
            powerConnected = true;
        }

        disableUpdatesIfScreenOffCheckBox = (CheckBox) findViewById(R.id.checkBoxUpdates);
        enableUpdatesWhenPowerConnectedCheckBox = (CheckBox) findViewById(R.id.checkBoxEnableUpdatesWhenPowerConnected);

        disableUpdates = prefs.getBoolean("disable", true);
        enableUpdatesWhenConnectedToPower = prefs.getBoolean("enableUpdatesWhenConnectedToPower", false);

        if (disableUpdates) {
            disableUpdatesIfScreenOffCheckBox.setChecked(true);

            if (enableUpdatesWhenConnectedToPower) {
                enableUpdatesWhenPowerConnectedCheckBox.setChecked(true);
            }

        } else {
            enableUpdatesWhenPowerConnectedCheckBox.setVisibility(View.GONE);
            enableUpdatesWhenPowerConnectedCheckBox.setChecked(false);
        }

        disableUpdatesIfScreenOffCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                                         @Override
                                                                         public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                                             if (isChecked) {
                                                                                 disableUpdates = true;
                                                                                 enableUpdatesWhenPowerConnectedCheckBox.setVisibility(View.VISIBLE);
                                                                                 prefs.edit().putBoolean("disable", true).apply();
                                                                             } else {
                                                                                 disableUpdates = false;
                                                                                 enableUpdatesWhenPowerConnectedCheckBox.setVisibility(View.GONE);
                                                                                 if (enableUpdatesWhenPowerConnectedCheckBox.isChecked()) {
                                                                                     enableUpdatesWhenPowerConnectedCheckBox.setChecked(false);
                                                                                 }
                                                                                 prefs.edit().putBoolean("disable", false).apply();
                                                                             }
                                                                         }
                                                                     }
        );

        enableUpdatesWhenPowerConnectedCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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

    }

    private void setMusicNoteColors(int color) {
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

        if (prefs.getInt("notificationId", -1) == -1) {
            prefs.edit().putInt("notificationId", notificationId).apply();
        } else {
            notificationId = prefs.getInt("notificationId", -1);
        }

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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.ic_action_settings) {
            Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class);
            MainActivity.this.startActivity(myIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
    }


    private class ScrapeWebSocketLink extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {

            long minute = 1000 * 10;
            long hour = minute * 60;
            long twelvehours = hour * 12;

            Long wssLastUpdatedTime = prefs.getLong("wsstimestamp", 0);
            Long timeDifference = System.currentTimeMillis() - wssLastUpdatedTime;

            if (timeDifference < twelvehours && theButtonURL != null) {
                return theButtonURL.toString();
            } else {
                try {

                    //Special thanks to https://github.com/artganify for the connection data that reddit doesn't block as a bot.
                    Request request = new Request.Builder()
                            .url(urls[0])
                            .addHeader("Accept", "text/html")
                            .addHeader("User-Agent", "TheButtonForReddit/" + notificationId)
                            .addHeader("Accept-Language", "en-US;q=0.6,en;q=0.4")
                            .addHeader("Host", "www.reddit.com")
                            .addHeader("Connection", "keep-alive")
                            .build();

                    Response response = client.newCall(request).execute();
                    String responseString = response.body().string();

                    String startLookup = "wss://";
                    String endLookup = "\"";
                    String part = responseString.substring(responseString.indexOf(startLookup));
                    String uri = part.substring(0, part.indexOf(endLookup));

                    prefs.edit().putLong("wsstimestamp", System.currentTimeMillis()).apply();
                    prefs.edit().putString("wsslink", uri).apply();

                    return uri;

                } catch (IOException e) {
                    Log.i("OKHTTP", "Error reading webpage", e);
                }
            }

            return "error";
        }

        @Override
        protected void onPostExecute(String result) {
            if (!result.equals("error")) {
                try {
                    theButtonURL = URI.create(result);
                    initWebSocket();
                } catch (Exception e) {
                    GetWebSocketLink getWebSocketLink = new GetWebSocketLink();
                    getWebSocketLink.execute(getString(R.string.socket_url_backup));
                }
            } else {
                timer.setText(getString(R.string.cannot_get_socket_link));
            }
        }
    }

    /*
      GetWebSocketLink was my original method of getting the websocket link and avoiding reddit
      calling me a bot. With the new ScrapeWebSocketLink method it appears to work correctly
      but I'm leaving this old method as a backup in case it suddenly starts to fail.
     */

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
                    prefs.edit().putLong("wsstimestamp", 0).apply();
                    prefs.edit().putString("wsslink", "").apply();
                    initWebSocket();
                } catch (Exception e) {
                    Log.e("TheButton", "Exception creating URI", e);
                    timer.setText("Could not get websocket link from Reddit or backup source.  Please close app and try again. If this issue persists contact me via email");
                    timer.setTextSize(20f);
                }
            } else {
                timer.setText(getString(R.string.cannot_get_socket_link));
            }
        }
    }

    /*
     *  This is to get the results from the ring tone selection.
     */
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

                        if (shutDownSocket) {
                            webSocket.end();

                            if (fullShutDown) {
                                notificationManager.cancelAll();
                                finish();
                            }
                        } else {
                            EventBus.getDefault().post(new CurrentTimeEvent(s));
                        }
                    }
                });
            }
        });
    }

    public void onEventMainThread(CurrentTimeEvent event) {

        JsonObject currentPing = jsonParser.parse(event.getCurrentTime()).getAsJsonObject();

        final String users = currentPing.get("payload").getAsJsonObject().get("participants_text").getAsString();
        final int intTime = currentPing.get("payload").getAsJsonObject().get("seconds_left").getAsInt();

        timer.setText(String.valueOf(intTime));
        participants.setText(users + getString(R.string.participants));

        EventBus.getDefault().postSticky(new SetCurrentColorEvent(ButtonColors.getButtonColor(intTime)));
        updateNotification(intTime);
        alertIfNeeded(intTime);

    }

    public void onEventMainThread(SetCurrentColorEvent event) {

        currentColor = event.getColor();
        timer.setTextColor(currentColor);

        Drawable colorDrawable = new ColorDrawable(currentColor);
        getSupportActionBar().setBackgroundDrawable(colorDrawable);
    }

    private void updateNotification(int intTime) {

        int color = ButtonColors.getButtonColor(intTime);

        notificationBuilder.setColor(color);
        notificationBuilder.setContentText(String.valueOf(intTime));
        notificationBuilder.setSmallIcon(timeImages.getResourceId(60 - intTime, -1));

        //Experimental - setting the led color
        //I cancel the notification since not cancelling it caused
        //notifications from different colors to bleed into each other.
        if (prefsFromSettings.getBoolean("pref_led", false) && !screenOn) {
            if (intTime != 0) {
                notificationBuilder.setLights(color, 500, 1000);
                notificationManager.cancel(notificationId);
            }
        }

        if (!shutDownSocket)
            notificationManager.notify(notificationId, notificationBuilder.build());

    }

    private void alertIfNeeded(int intTime) {

        if (alertInitiated && intTime >= alertInt) {
            alertInitiated = false;
        }

        if (alertInt != -1 && intTime <= alertInt && !alertInitiated) {
            alertInitiated = true;
            try {
                ringtone.play();
                if (prefsFromSettings.getBoolean("pref_sync", false)) {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(750);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
                        ScrapeWebSocketLink task = new ScrapeWebSocketLink();
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
            ScrapeWebSocketLink task = new ScrapeWebSocketLink();
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
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        notificationManager.cancelAll();
        shutDownSocket = true;

        super.onBackPressed();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("timer", timer.getText().toString());
        outState.putString("participants", participants.getText().toString());
        outState.putInt("currentColor", currentColor);
    }

    @Override
    public void onDestroy() {

        unregisterReceiver(screenBroadcast);
        EventBus.getDefault().unregister(this);
        notificationManager.cancelAll();
        ringtone = null;

        super.onDestroy();

    }
}

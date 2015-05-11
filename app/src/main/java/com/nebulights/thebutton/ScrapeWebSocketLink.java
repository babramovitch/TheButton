package com.nebulights.thebutton;

import android.os.AsyncTask;

import com.crashlytics.android.Crashlytics;
import com.nebulights.thebutton.events.GetWebSocketLinkCompleteEvent;
import com.nebulights.thebutton.events.GetWebSocketLinkFailureEvent;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.OkHttpClient;

import java.io.IOException;
import java.net.URI;

import de.greenrobot.event.EventBus;

/**
 * Created by Ben on 10/05/2015.
 */

class ScrapeWebSocketLink extends AsyncTask<String, Void, String> {

    OkHttpClient client;
    int notificationId;

    public ScrapeWebSocketLink(OkHttpClient client, int notificationId) {
        this.client = client;
        this.notificationId = notificationId;
    }

    @Override
    protected String doInBackground(String... urls) {

        String responseString = "";

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
            responseString = response.body().string();

            String startLookup = "wss://";
            String endLookup = "\"";
            String part = responseString.substring(responseString.indexOf(startLookup));
            String uri = part.substring(0, part.indexOf(endLookup));

            return uri;

        } catch (IOException e) {
            Crashlytics.logException(e);
        } catch (Exception e) {
            Crashlytics.log(responseString);
            Crashlytics.logException(e);
        }

        return "error";
    }

    @Override
    protected void onPostExecute(String result) {
        if (!result.equals("error")) {
            try {
                URI uri = URI.create(result);
                EventBus.getDefault().post(new GetWebSocketLinkCompleteEvent(uri));
            } catch (Exception e) {
                Crashlytics.logException(e);
                EventBus.getDefault().post(new GetWebSocketLinkFailureEvent());
            }
        } else {
            EventBus.getDefault().post(new GetWebSocketLinkFailureEvent());
        }
    }
}

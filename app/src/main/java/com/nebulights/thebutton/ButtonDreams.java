package com.nebulights.thebutton;

import android.animation.TimeAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.PointF;
import android.os.Build;
import android.service.dreams.DreamService;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.nebulights.thebutton.events.CurrentTimeEvent;

import de.greenrobot.event.EventBus;

/**
 * Created by Ben on 11/04/2015.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class ButtonDreams extends DreamService {

    TextView dreamTime, dreamParticipants, lastPress;
    int lastPressInt;
    Bouncer bouncer;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        EventBus.getDefault().register(this);

        bouncer = new Bouncer(this);

        FrameLayout.LayoutParams lp
                = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);

        dreamTime = new TextView(this);
        dreamTime.setTextSize(150f);

        dreamParticipants = new TextView(this);
        dreamParticipants.setTextSize(25f);

        lastPress = new TextView(this);
        lastPress.setTextSize(25f);

        bouncer.setLayoutParams(new
                ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        bouncer.setSpeed(150); // pixels/sec

        bouncer.addView(dreamTime, lp);
        bouncer.addView(dreamParticipants, lp);
        bouncer.addView(lastPress, lp);

        setContentView(bouncer);

    }

    @Override
    public void onDetachedFromWindow() {
        EventBus.getDefault().unregister(this);
    }


    public void onEventMainThread(CurrentTimeEvent event) {

        JsonObject currentPing = event.getCurrentTime();

        final String time = currentPing.get("payload").getAsJsonObject().get("seconds_left").getAsString().substring(0, 2);
        final String users = currentPing.get("payload").getAsJsonObject().get("participants_text").getAsString();
        final int intTime = currentPing.get("payload").getAsJsonObject().get("seconds_left").getAsInt();
        int color = ButtonColors.getButtonColor(intTime);

        if(lastPressInt != 0 && lastPressInt < intTime){
            lastPress.setText("last press " + lastPressInt);
            lastPress.setTextColor(ButtonColors.getButtonColor(lastPressInt));
        }

        lastPressInt = intTime;

        dreamTime.setText(time);
        dreamParticipants.setText(users + getString(R.string.participants));

        dreamTime.setTextColor(color);
        dreamParticipants.setTextColor(color);

    }

    //http://android-developers.blogspot.com/2012/12/daydream-interactive-screen-savers.html
    public class Bouncer extends FrameLayout implements TimeAnimator.TimeListener {
        private float mMaxSpeed;
        private final TimeAnimator mAnimator;
        private int mWidth, mHeight;

        public Bouncer(Context context) {
            this(context, null);
        }

        public Bouncer(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public Bouncer(Context context, AttributeSet attrs, int flags) {
            super(context, attrs, flags);
            mAnimator = new TimeAnimator();
            mAnimator.setTimeListener(this);
        }

        /**
         * Start the bouncing as soon as we’re on screen.
         */
        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            mAnimator.start();
        }

        /**
         * Stop animations when the view hierarchy is torn down.
         */
        @Override
        public void onDetachedFromWindow() {
            mAnimator.cancel();
            super.onDetachedFromWindow();
        }

        /**
         * Whenever a view is added, place it randomly.
         */
        @Override
        public void addView(View v, ViewGroup.LayoutParams lp) {
            super.addView(v, lp);
            setupView(v);
        }

        /**
         * Reposition all children when the container size changes.
         */
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            mWidth = w;
            mHeight = h;
            for (int i = 0; i < getChildCount(); i++) {
                setupView(getChildAt(i));
            }
        }

        /**
         * Bouncing view setup: random placement, random velocity.
         */
        private void setupView(View v) {
            final PointF p = new PointF();
            final float a = (float) (Math.random() * 360);
            p.x = mMaxSpeed * (float) (Math.cos(a));
            p.y = mMaxSpeed * (float) (Math.sin(a));
            v.setTag(p);
            v.setX((float) (Math.random() * (mWidth - v.getWidth())));
            v.setY((float) (Math.random() * (mHeight - v.getHeight())));

        }

        /**
         * Every TimeAnimator frame, nudge each bouncing view along.
         */
        public void onTimeUpdate(TimeAnimator animation, long elapsed, long dt_ms) {
            final float dt = dt_ms / 1000f; // seconds
            for (int i = 0; i < getChildCount(); i++) {
                final View view = getChildAt(i);
                final PointF v = (PointF) view.getTag();

                // step view for velocity * time
                view.setX(view.getX() + v.x * dt);
                view.setY(view.getY() + v.y * dt);

                // handle reflections
                final float l = view.getX();
                final float t = view.getY();
                final float r = l + view.getWidth();
                final float b = t + view.getHeight();
                boolean flipX = false, flipY = false;
                if (r > mWidth) {
                    view.setX(view.getX() - 2 * (r - mWidth));
                    flipX = true;
                } else if (l < 0) {
                    view.setX(-l);
                    flipX = true;
                }
                if (b > mHeight) {
                    view.setY(view.getY() - 2 * (b - mHeight));
                    flipY = true;
                } else if (t < 0) {
                    view.setY(-t);
                    flipY = true;
                }
                if (flipX) v.x *= -1;
                if (flipY) v.y *= -1;
            }
        }

        public void setSpeed(float s) {
            mMaxSpeed = s;
        }

    }
}



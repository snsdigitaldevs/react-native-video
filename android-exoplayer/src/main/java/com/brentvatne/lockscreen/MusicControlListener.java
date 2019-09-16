package com.brentvatne.lockscreen;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.RatingCompat;

import androidx.media.VolumeProviderCompat;

import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.KeyEvent;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Iterator;
import java.util.Set;

public class MusicControlListener extends MediaSessionCompat.Callback {
    private static final String TAG = MusicControlListener.class.getSimpleName();

    private static void sendEvent(ReactApplicationContext context, String type, Object value) {
        WritableMap data = Arguments.createMap();
        data.putString("name", type);

        if (value == null) {
            // NOOP
        } else if (value instanceof Double || value instanceof Float) {
            data.putDouble("value", (double) value);
        } else if (value instanceof Boolean) {
            data.putBoolean("value", (boolean) value);
        } else if (value instanceof Integer) {
            data.putInt("value", (int) value);
        }

        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RNMusicControlEvent", data);
    }

    private final ReactApplicationContext context;

    MusicControlListener(ReactApplicationContext context) {
        this.context = context;
    }

    @Override
    public void onPlay() {
        Log.d(TAG, "sendPlay Event");
        sendEvent(context, "play", null);
    }

    @Override
    public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
        // dumpIntent(mediaButtonEvent);
        String intentAction = mediaButtonEvent.getAction();
        if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            KeyEvent event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && KeyEvent.ACTION_UP == event.getAction()) {
                if (KeyEvent.KEYCODE_MEDIA_PREVIOUS == event.getKeyCode()) {
                    sendEvent(context, "skipBackward", null);
                } else if (KeyEvent.KEYCODE_MEDIA_NEXT == event.getKeyCode()) {
                    sendEvent(context, "skipForward", null);
                }
            }
        }
        return super.onMediaButtonEvent(mediaButtonEvent);
    }

    private void dumpIntent(Intent i) {
        Bundle bundle = i.getExtras();
        if (bundle != null) {
            Set<String> keys = bundle.keySet();
            Iterator<String> it = keys.iterator();
            Log.e(TAG, "Dumping Intent start");
            while (it.hasNext()) {
                String key = it.next();
                Log.e(TAG, "[" + key + "=" + bundle.get(key) + "]");
            }
            Log.e(TAG, "Dumping Intent end");
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "sendPause Event");
        sendEvent(context, "pause", null);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "stop Event");
        sendEvent(context, "stop", null);
    }

    @Override
    public void onSkipToNext() {
        Log.d(TAG, "nextTrack Event");
        sendEvent(context, "nextTrack", null);
    }

    @Override
    public void onSkipToPrevious() {
        sendEvent(context, "previousTrack", null);
    }

    @Override
    public void onSeekTo(long pos) {
        sendEvent(context, "seek", pos / 1000D);
    }

    @Override
    public void onFastForward() {
        Log.d(TAG, "skipForward Event");
        sendEvent(context, "skipForward", null);
    }

    @Override
    public void onRewind() {
        Log.d(TAG, "skipBackward Event");
        sendEvent(context, "skipBackward", null);
    }

    @Override
    public void onSetRating(RatingCompat rating) {
        if (MusicControlModule.INSTANCE == null) return;
        int type = MusicControlModule.INSTANCE.ratingType;

        if (type == RatingCompat.RATING_PERCENTAGE) {
            sendEvent(context, "setRating", rating.getPercentRating());
        } else if (type == RatingCompat.RATING_HEART) {
            sendEvent(context, "setRating", rating.hasHeart());
        } else if (type == RatingCompat.RATING_THUMB_UP_DOWN) {
            sendEvent(context, "setRating", rating.isThumbUp());
        } else {
            sendEvent(context, "setRating", rating.getStarRating());
        }
    }

    public static class VolumeListener extends VolumeProviderCompat {

        private final ReactApplicationContext context;

        public VolumeListener(ReactApplicationContext context, boolean changeable, int maxVolume, int currentVolume) {
            super(changeable ? VOLUME_CONTROL_ABSOLUTE : VOLUME_CONTROL_FIXED, maxVolume, currentVolume);
            this.context = context;
        }

        public boolean isChangeable() {
            return getVolumeControl() != VolumeProviderCompat.VOLUME_CONTROL_FIXED;
        }

        @Override
        public void onSetVolumeTo(int volume) {
            setCurrentVolume(volume);
            sendEvent(context, "volume", volume);
        }

        @Override
        public void onAdjustVolume(int direction) {
            int maxVolume = getMaxVolume();
            int tick = direction * (maxVolume / 10);
            int volume = Math.max(Math.min(getCurrentVolume() + tick, maxVolume), 0);

            setCurrentVolume(volume);
            sendEvent(context, "volume", volume);
        }

        public VolumeListener create(Boolean changeable, Integer maxVolume, Integer currentVolume) {
            if (currentVolume == null) {
                currentVolume = getCurrentVolume();
            } else {
                setCurrentVolume(currentVolume);
            }

            if (changeable == null) changeable = isChangeable();
            if (maxVolume == null) maxVolume = getMaxVolume();

            if (changeable == isChangeable() && maxVolume == getMaxVolume()) return this;
            return new VolumeListener(context, changeable, maxVolume, currentVolume);
        }
    }

}
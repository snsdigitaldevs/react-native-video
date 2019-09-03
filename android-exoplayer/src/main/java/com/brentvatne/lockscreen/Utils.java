package com.brentvatne.lockscreen;

import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();
    /**
     * Code taken from newer version of the support library located in PlaybackStateCompat.toKeyCode
     * Replace this to PlaybackStateCompat.toKeyCode when React Native updates the support library
     */
    public static int toKeyCode(long action) {
        if(action == PlaybackStateCompat.ACTION_PLAY) {
            return KeyEvent.KEYCODE_MEDIA_PLAY;
        } else if(action == PlaybackStateCompat.ACTION_PAUSE) {
            return KeyEvent.KEYCODE_MEDIA_PAUSE;
        } else if(action == PlaybackStateCompat.ACTION_SKIP_TO_NEXT) {
            Log.e(TAG,"PlaybackStateCompat.ACTION_SKIP_TO_NEXT");
            return KeyEvent.KEYCODE_MEDIA_NEXT;
        } else if(action == PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) {
            Log.e(TAG,"PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS");
            return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        } else if(action == PlaybackStateCompat.ACTION_STOP) {
            return KeyEvent.KEYCODE_MEDIA_STOP;
        } else if(action == PlaybackStateCompat.ACTION_FAST_FORWARD) {
            return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        } else if(action == PlaybackStateCompat.ACTION_REWIND) {
            return KeyEvent.KEYCODE_MEDIA_REWIND;
        } else if(action == PlaybackStateCompat.ACTION_PLAY_PAUSE) {
            return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }
}

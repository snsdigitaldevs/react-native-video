package com.brentvatne.lockscreen;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.brentvatne.react.R;
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.datasource.BaseBitmapReferenceDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class MusicControlModule extends ReactContextBaseJavaModule implements ComponentCallbacks2 {
    private static final String TAG = MusicControlModule.class.getSimpleName();

    static MusicControlModule INSTANCE;

    private boolean init = false;
    protected MediaSessionCompat session;

    private MediaMetadataCompat.Builder md;
    private PlaybackStateCompat.Builder pb;
    public NotificationCompat.Builder nb;

    private PlaybackStateCompat state;

    public MusicControlNotification notification;
    private MusicControlListener.VolumeListener volume;
    private MusicControlReceiver receiver;

    public ReactApplicationContext context;

    private boolean remoteVolume = false;
    private boolean isPlaying = false;
    private long controls = 0;
    private CloseableReference<Bitmap> bitmapRef = null;

    public NotificationClose notificationClose = NotificationClose.PAUSED;

    public static final String CHANNEL_ID = "react-native-music-control";

    public static final int NOTIFICATION_ID = 100;

    public MusicControlModule(ReactApplicationContext context) {
        super(context);
        this.context = context;
    }

    @Override
    public String getName() {
        return "MusicControlManager";
    }

    @Override
    public Map<String, Object> getConstants() {
        Map<String, Object> map = new HashMap<>();
        map.put("STATE_ERROR", PlaybackStateCompat.STATE_ERROR);
        map.put("STATE_STOPPED", PlaybackStateCompat.STATE_STOPPED);
        map.put("STATE_PLAYING", PlaybackStateCompat.STATE_PLAYING);
        map.put("STATE_PAUSED", PlaybackStateCompat.STATE_PAUSED);
        map.put("STATE_BUFFERING", PlaybackStateCompat.STATE_BUFFERING);
        return map;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel(ReactApplicationContext context) {
        NotificationManager mNotificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, "Media playback",
                NotificationManager.IMPORTANCE_LOW);
        mChannel.setDescription("Media playback controls");
        mChannel.setShowBadge(false);
        mChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        mNotificationManager.createNotificationChannel(mChannel);
    }

    public boolean hasControl(long control) {
        return (controls & control) == control;
    }

    private void updateNotificationMediaStyle() {
        if (nb == null) {
            return;
        }
        MediaStyle style = new MediaStyle();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            style.setMediaSession(session.getSessionToken());
        }
        if (hasControl(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
                || hasControl(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)) {
            // according to LockScreenView enableControl()
            style.setShowActionsInCompactView(0, 2, 4);
        } else {
            int controlCount = 0;
            if (hasControl(PlaybackStateCompat.ACTION_PLAY)
                    || hasControl(PlaybackStateCompat.ACTION_PAUSE)
                    || hasControl(PlaybackStateCompat.ACTION_PLAY_PAUSE)) {
                controlCount += 1;
            }
            if (hasControl(PlaybackStateCompat.ACTION_FAST_FORWARD)) {
                controlCount += 1;
            }
            if (hasControl(PlaybackStateCompat.ACTION_REWIND)) {
                controlCount += 1;
            }
            int[] actions = new int[controlCount];
            for (int i = 0; i < actions.length; i++) {
                actions[i] = i;
            }
            style.setShowActionsInCompactView(actions);
        }
        nb.setStyle(style);
    }

    private void init() {
        if (init) {
            return;
        }
        try {
            INSTANCE = this;

            //TODO for Samsung use "public MediaSessionCompat(Context context, String tag) "
            ComponentName compName = new ComponentName(context, MusicControlReceiver.class);
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            // the associated intent will be handled by the component being registered
            mediaButtonIntent.setComponent(compName);
            PendingIntent mbrIntent = PendingIntent.getBroadcast(context,
                    0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
            session = new MediaSessionCompat(context, "MusicControl", compName, mbrIntent);
            session.setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            session.setCallback(new MusicControlListener(context));

            volume = new MusicControlListener.VolumeListener(context, true, 100, 100);
            if (remoteVolume) {
                session.setPlaybackToRemote(volume);
            } else {
                session.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
            }

            md = new MediaMetadataCompat.Builder();
            pb = new PlaybackStateCompat.Builder();
            pb.setActions(controls);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createChannel(context);
            }
            nb = new NotificationCompat.Builder(context, CHANNEL_ID);
            nb.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            state = pb.build();

            notification = new MusicControlNotification(this, context);
            notification.updateActions(controls, null);

            IntentFilter filter = new IntentFilter();
            filter.addAction(MusicControlNotification.REMOVE_NOTIFICATION);
            filter.addAction(MusicControlNotification.MEDIA_BUTTON);
            filter.addAction(Intent.ACTION_MEDIA_BUTTON);
            filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            receiver = new MusicControlReceiver(this, context);
            if (Build.VERSION.SDK_INT >= 34) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            Intent myIntent = new Intent(context, MusicControlNotification.NotificationService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(myIntent);
            } else {
                context.startService(myIntent);
            }

            context.registerComponentCallbacks(this);

            isPlaying = false;
            init = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public synchronized void stopControl() {
        if (notification != null) {
            notification.hide();
            notification = null;
        }
        if (session != null) {
            session.release();
            session = null;
        }

        try {
            if (receiver != null) {
                context.unregisterReceiver(receiver);
                receiver = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        context.unregisterComponentCallbacks(this);

        volume = null;
        state = null;
        md = null;
        pb = null;
        nb = null;
        if (bitmapRef != null) {
            CloseableReference.closeSafely(bitmapRef);
            bitmapRef = null;
        }
        init = false;
    }

    synchronized public void destroy() {
        stopControl();
    }

    @ReactMethod
    public void enableBackgroundMode(boolean enable) {
        // Nothing?
    }

    @ReactMethod
    synchronized public void setNowPlaying(ReadableMap metadata) {
        if (metadata == null) {
          return;
        }
        init();

        String title = metadata.hasKey("title") ? metadata.getString("title") : null;
        String artist = metadata.hasKey("artist") ? metadata.getString("artist") : null;
        String album = metadata.hasKey("album") ? metadata.getString("album") : null;
        String genre = metadata.hasKey("genre") ? metadata.getString("genre") : null;
        String description = metadata.hasKey("description") ? metadata.getString("description") : null;
        String date = metadata.hasKey("date") ? metadata.getString("date") : null;
        long duration = metadata.hasKey("duration") ? (long) (metadata.getDouble("duration") * 1000) : 0;
        int notificationColor = metadata.hasKey("color") ? metadata.getInt("color") : NotificationCompat.COLOR_DEFAULT;

        // If a color is supplied, we need to clear the MediaStyle set during init().
        // Otherwise, the color will not be used for the notification's background.
        boolean removeFade = metadata.hasKey("color");
        if (removeFade) {
            nb.setStyle(new MediaStyle());
        }

        md.putText(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        md.putText(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        md.putText(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
        md.putText(MediaMetadataCompat.METADATA_KEY_GENRE, genre);
        md.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, description);
        md.putText(MediaMetadataCompat.METADATA_KEY_DATE, date);
        md.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        nb.setContentTitle(title);
        nb.setContentText(artist);
        nb.setContentInfo(album);
        nb.setColor(notificationColor);

        if ((metadata.hasKey("artwork") && ReadableType.String == metadata.getType("artwork"))) {
            String artworkUrl = metadata.getString("artwork");

            ImageRequest imageRequest = ImageRequest.fromUri(artworkUrl);
            DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null);
            dataSource.subscribe(new BaseBitmapReferenceDataSubscriber() {
                @Override
                protected void onNewResultImpl(@Nullable CloseableReference<Bitmap> bitmapReference) {
                    Log.d(TAG, "onNewResultImpl load bitmap complete");
                    bitmapRef = bitmapReference.clone();
                    Bitmap bitmap;
                    try {
                        if (bitmapRef != null) {
                            bitmap = bitmapRef.get();
                            if (bitmap == null || bitmap.isRecycled()) {
                                bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.pimsleuricon11);
                            }
                        } else {
                            bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.pimsleuricon11);
                        }
                        setupBitmap(bitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        CloseableReference.closeSafely(bitmapReference);
                    }
                }

                @Override
                protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                    Log.d(TAG, "onFailureImpl load bitmap failed");
                    Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.pimsleuricon11);
                    setupBitmap(bitmap);
                }
            }, UiThreadImmediateExecutorService.getInstance());
        } else if ((metadata.hasKey("artwork") && ReadableType.Map == metadata.getType("artwork"))) {
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.pimsleuricon11);
            setupBitmap(bitmap);
        }

        //session.setMetadata(md.build());
        session.setActive(true);
        notification.show(nb, isPlaying);
    }

    private void setupBitmap(Bitmap bitmap) {
        if (md != null) {
            md.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap);
        }
        if (nb != null && notification != null) {
            nb.setLargeIcon(bitmap);
            notification.show(nb, isPlaying);
        }
    }

    @ReactMethod
    synchronized public void updatePlayback(ReadableMap info) {
        if (state == null || volume == null || pb == null || session == null || notification == null || nb == null || info == null) {
            Log.e(TAG, "updatePlayback did not work, somethingwrong..");
            dump();
            return;
        }

        long updateTime;
        long elapsedTime;
        long bufferedTime = info.hasKey("bufferedTime") ? (long) (info.getDouble("bufferedTime") * 1000)
                : state.getBufferedPosition();
        float speed = info.hasKey("speed") ? (float) info.getDouble("speed") : state.getPlaybackSpeed();
        int pbState = info.hasKey("state") ? info.getInt("state") : state.getState();
        int maxVol = info.hasKey("maxVolume") ? info.getInt("maxVolume") : volume.getMaxVolume();
        int vol = info.hasKey("volume") ? info.getInt("volume") : volume.getCurrentVolume();

        if (info.hasKey("elapsedTime")) {
            elapsedTime = (long) (info.getDouble("elapsedTime") * 1000);
            updateTime = SystemClock.elapsedRealtime();
        } else {
            elapsedTime = state.getPosition();
            updateTime = state.getLastPositionUpdateTime();
        }

        pb.setState(pbState, elapsedTime, speed, updateTime);
        pb.setBufferedPosition(bufferedTime);
        pb.setActions(controls);

        isPlaying = pbState == PlaybackStateCompat.STATE_PLAYING || pbState == PlaybackStateCompat.STATE_BUFFERING;
        if (session.isActive() && bitmapRef != null) {
            notification.show(nb, isPlaying);
        }

        state = pb.build();
        session.setPlaybackState(state);

        updateNotificationMediaStyle();

        if (session.isActive()) {
            notification.show(nb, isPlaying);
        }

        if (remoteVolume) {
            session.setPlaybackToRemote(volume.create(null, maxVol, vol));
        } else {
            session.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
        }
    }

    @ReactMethod
    synchronized public void resetNowPlaying() {
        if (!init) {
            return;
        }
        md = new MediaMetadataCompat.Builder();
        if (notification != null) {
            notification.hide();
        }
        session.setActive(false);
    }

    @ReactMethod
    synchronized public void enableControl(String control, boolean enable, ReadableMap options) {
        if (session == null || notification == null || pb == null || options == null) {
            Log.e(TAG, "enableControl after stop, return..");
            dump();
            return;
        }
        Map<String, Integer> skipOptions = new HashMap<>();
        long controlValue;
        switch (control) {
            case "skipForward":
                if (options.hasKey("interval"))
                    skipOptions.put("skipForward", options.getInt("interval"));
                controlValue = PlaybackStateCompat.ACTION_FAST_FORWARD;
                break;
            case "skipBackward":
                if (options.hasKey("interval"))
                    skipOptions.put("skipBackward", options.getInt("interval"));
                controlValue = PlaybackStateCompat.ACTION_REWIND;
                break;
            case "nextTrack":
                controlValue = PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
                break;
            case "previousTrack":
                controlValue = PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
                break;
            case "play":
                Log.d(TAG, "play..");
                controlValue = PlaybackStateCompat.ACTION_PLAY;
                break;
            case "pause":
                Log.d(TAG, "setPause..");
                controlValue = PlaybackStateCompat.ACTION_PAUSE;
                break;
            case "togglePlayPause":
                controlValue = PlaybackStateCompat.ACTION_PLAY_PAUSE;
                break;
            case "stop":
                controlValue = PlaybackStateCompat.ACTION_STOP;
                break;
            case "seek":
                controlValue = PlaybackStateCompat.ACTION_SEEK_TO;
                break;
            case "volume":
                volume = volume.create(enable, null, null);
                if (remoteVolume)
                    session.setPlaybackToRemote(volume);
                return;
            case "remoteVolume":
                remoteVolume = enable;
                if (enable) {
                    session.setPlaybackToRemote(volume);
                } else {
                    session.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
                }
                return;
            case "closeNotification":
                if (enable) {
                    if (options.hasKey("when")) {
                        if ("always".equals(options.getString("when"))) {
                            this.notificationClose = notificationClose.ALWAYS;
                        } else if ("paused".equals(options.getString("when"))) {
                            this.notificationClose = notificationClose.PAUSED;
                        } else {
                            this.notificationClose = notificationClose.NEVER;
                        }
                    }
                    return;
                }
            default:
                // Unknown control type, let's just ignore it
                return;
        }

        if (enable) {
            controls |= controlValue;
        } else {
            controls &= ~controlValue;
        }

        notification.updateActions(controls, skipOptions);
        pb.setActions(controls);

        state = pb.build();
        session.setPlaybackState(state);
    }

    private void dump() {
        Log.i(TAG, ">DUMP START<");
        if (session != null) Log.d(TAG, "session = " + session);
        if (notification != null) Log.d(TAG, "notification = " + notification);
        if (pb != null) Log.d(TAG, "pb = " + pb);
        if (nb != null) Log.d(TAG, "nb = " + nb);
        if (state != null) Log.d(TAG, "state = " + state);
        if (volume != null) Log.d(TAG, "volume = " + volume);
        Log.i(TAG, ">DUMP END<");
    }

    @Override
    public void onTrimMemory(int level) {
        switch (level) {
            // Trims memory when it reaches a moderate level and the session is inactive
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
                if (session != null && session.isActive())
                    break;

                // Trims memory when it reaches a critical level
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:

                Log.e(TAG, "Control resources are being removed due to system's low memory (Level: " + level + ")");

//                destroy();
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

    }

    @Override
    public void onLowMemory() {
        Log.e(TAG, "Control resources are being removed due to system's low memory (Level: MEMORY_COMPLETE)");
        destroy();
    }

    public enum NotificationClose {
        ALWAYS, PAUSED, NEVER
    }
}

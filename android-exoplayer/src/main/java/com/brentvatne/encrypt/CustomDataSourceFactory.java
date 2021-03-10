package com.brentvatne.encrypt;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import androidx.annotation.Nullable;

/**
 * Refer to {@link DefaultDataSourceFactory}
 */
public class CustomDataSourceFactory implements DataSource.Factory {
    private final Context context;
    private final @Nullable
    TransferListener listener;
    private final DataSource.Factory baseDataSourceFactory;

    /**
     * @param context   A context.
     * @param userAgent The User-Agent string that should be used.
     */
    public CustomDataSourceFactory(Context context, String userAgent) {
        this(context, userAgent, /* listener= */ null);
    }

    /**
     * @param context   A context.
     * @param userAgent The User-Agent string that should be used.
     * @param listener  An optional listener.
     */
    public CustomDataSourceFactory(
            Context context, String userAgent, @Nullable TransferListener listener) {
        this(context, listener, new DefaultHttpDataSourceFactory(userAgent, listener));
    }

    /**
     * @param context               A context.
     * @param baseDataSourceFactory A {@link DataSource.Factory} to be used to create a base {@link DataSource}
     *                              for {@link DefaultDataSource}.
     * @see DefaultDataSource#DefaultDataSource(Context, TransferListener, DataSource)
     */
    public CustomDataSourceFactory(Context context, DataSource.Factory baseDataSourceFactory) {
        this(context, /* listener= */ null, baseDataSourceFactory);
    }

    /**
     * @param context               A context.
     * @param listener              An optional listener.
     * @param baseDataSourceFactory A {@link DataSource.Factory} to be used to create a base {@link DataSource}
     *                              for {@link DefaultDataSource}.
     * @see DefaultDataSource#DefaultDataSource(Context, TransferListener, DataSource)
     */
    public CustomDataSourceFactory(
            Context context,
            @Nullable TransferListener listener,
            DataSource.Factory baseDataSourceFactory) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.baseDataSourceFactory = baseDataSourceFactory;
    }

    @Override
    public DataSource createDataSource() {
        TransferDataSource dataSource = new TransferDataSource(context, baseDataSourceFactory.createDataSource());
        if (listener != null) {
            dataSource.addTransferListener(listener);
        }
        return dataSource;
    }
}

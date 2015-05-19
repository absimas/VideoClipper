package com.simas.vc.editor.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.VideoView;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Simas Abramovas on 2015 May 01.
 */

// ToDo "no preview available for this file" message!

/**
 * Wrapper class for VideoView. Allows the use of multiple {@code OnPreparedListener}s. Also
 * limits the use of video setting to local strings. Allows checking if it's prepared.
 */
public final class Player extends VideoView implements MediaPlayer.OnPreparedListener {

	private static final String CURRENT_POSITION = "video_player_position";
	private static final String IS_PLAYING = "video_player_playing";
	private final String TAG = getClass().getName();
	/**
	 * Default timeout in seconds
	 */
	private final CopyOnWriteArrayList<MediaPlayer.OnPreparedListener>
			mPreparedListeners = new CopyOnWriteArrayList<>();
	private boolean mPrepared;
	private GestureDetector mGestureDetector;

	public Player(Context context) {
		super(context);
		init();
	}

	public Player(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public Player(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public Player(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	private void init() {
		mPrepared = false;
		// Multiple prepared listener implementation
		setOnPreparedListener(this);
	}

	@Override
	public void setVideoPath(final String path) {
		mPrepared = false;
		super.setVideoPath(path);
	}

	public Bundle getSavedState() {
		Bundle bundle = new Bundle();
		bundle.putInt(CURRENT_POSITION, getCurrentPosition());
		bundle.putBoolean(IS_PLAYING, isPlaying());
		return bundle;
	}

	public void restoreToState(@NonNull Bundle bundle) {
		final int position = bundle.getInt(CURRENT_POSITION);
		final boolean isPlaying = bundle.getBoolean(IS_PLAYING);

		// Runnable to do the job
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				seekTo(position);
				if (isPlaying) {
					start();
				} else {
					pause();
				}
			}
		};

		// Do the job when VideoView has been prepared
		if (isPrepared()) {
			runnable.run();
		} else {
			addOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mp) {
					removeOnPreparedListener(this);
					runnable.run();
				}
			});
		}
	}

	/* Prepared listener re-implementation */
	public boolean isPrepared() {
		return mPrepared;
	}

	public void addOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
		mPreparedListeners.add(listener);
	}

	public void removeOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
		mPreparedListeners.remove(listener);
	}

	public void removeAllOnPreparedListeners() {
		mPreparedListeners.clear();
	}

	@Override
	public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
		if (l != this) {
			throw new IllegalArgumentException("Use addOnPreparedListener instead!");
		}
		super.setOnPreparedListener(l);
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		mPrepared = true;
		for (MediaPlayer.OnPreparedListener listener : mPreparedListeners) {
			listener.onPrepared(mp);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (mGestureDetector != null) {
			return mGestureDetector.onTouchEvent(ev);
		} else {
			return super.onTouchEvent(ev);
		}
	}

	public void setGestureDetector(GestureDetector gestureDetector) {
		mGestureDetector = gestureDetector;
	}

}
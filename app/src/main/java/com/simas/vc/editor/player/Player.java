/*
 * Copyright (c) 2015. Simas Abramovas
 *
 * This file is part of VideoClipper.
 *
 * VideoClipper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VideoClipper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VideoClipper. If not, see <http://www.gnu.org/licenses/>.
 */
package com.simas.vc.editor.player;

import android.media.MediaPlayer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.simas.vc.R;
import com.simas.vc.Utils;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Notes:
 * - Do not call {@link #setOnPreparedListener(OnPreparedListener)} and
 * {@link #setOnErrorListener(OnErrorListener)}. Instead use
 * {@link #addOnPreparedListener(OnPreparedListener)} and
 * {@link #addOnErrorListener(OnErrorListener)} accordingly.
 */
public class Player extends MediaPlayer implements MediaPlayer.OnPreparedListener,
		MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

	public enum State {
		ERROR, RELEASED, IDLE, INITIALIZED, PREPARING, PREPARED, STARTED, STOPPED, PAUSED
	}

	private State mState = State.IDLE;

	private final String TAG = getClass().getName();
	private final CopyOnWriteArraySet<OnPreparedListener> mPrepareListeners =
			new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<OnErrorListener> mErrorListeners = new CopyOnWriteArraySet<>();
	private Controls mControls;
	private OnStateChangedListener mStateListener;

	public Player(View container) {
		super.setOnPreparedListener(this);
		super.setOnErrorListener(this);
		setOnCompletionListener(this);
		mControls = new Controls(container);
	}

	private synchronized void setState(State newState) {
		final State previousState = mState;
		mState = newState;
		if (mStateListener != null) {
			mStateListener.onStateChanged(previousState, newState);
		}
	}

	public synchronized State getState() {
		return mState;
	}

	@Override
	public void start() throws IllegalStateException {
		super.start();
		setState(State.STARTED);
		getControls().setPlaying(true);
	}

	@Override
	public void stop() throws IllegalStateException {
		super.stop();
		setState(State.STOPPED);
		getControls().setPlaying(false);
	}

	@Override
	public void pause() throws IllegalStateException {
		super.pause();
		setState(State.PAUSED);
		getControls().setPlaying(false);
	}

	@Override
	public void release() {
		super.release();
		if (getState().ordinal() < State.RELEASED.ordinal())
		setState(State.RELEASED);
		getControls().setPlaying(false);
	}

	@Override
	public void reset() {
		super.reset();
		setState(State.IDLE);
		getControls().setPlaying(false);
	}

	@Override
	public void seekTo(int msec) throws IllegalStateException {
		super.seekTo(msec);
		getControls().setCurrent(msec);
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		setState(State.PREPARED);
		getControls().setCurrent(0);
		getControls().setTotal(mp.getDuration());
		getControls().setPlaying(false);
		getControls().show();

		for (OnPreparedListener listener : mPrepareListeners) {
			listener.onPrepared(mp);
		}
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		setState(State.ERROR);
		for (OnErrorListener listener : mErrorListeners) {
			listener.onError(mp, what, extra);
		}
		return false;
	}

	@Override
	public void prepareAsync() throws IllegalStateException {
		setState(State.PREPARING);
		super.prepareAsync();
	}

	@Override
	public void prepare() throws IOException, IllegalStateException {
		setState(State.PREPARING);
		super.prepare();
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		setState(State.PAUSED);
		getControls().setPlaying(false);
	}

	public Controls getControls() {
		return mControls;
	}

	public void addOnPreparedListener(OnPreparedListener listener) {
		mPrepareListeners.add(listener);
	}

	public void removeOnPreparedListener(OnPreparedListener listener) {
		mPrepareListeners.remove(listener);
	}

	public void addOnErrorListener(OnErrorListener listener) {
		mErrorListeners.add(listener);
	}

	public void removeOnErrorListener(OnErrorListener listener) {
		mErrorListeners.remove(listener);
	}

	@Override
	public void setOnPreparedListener(OnPreparedListener listener) {
		throw new IllegalStateException("Player cannot have its prepared listener set! Use " +
				"addOnPreparedListener instead.");
	}

	@Override
	public void setOnErrorListener(OnErrorListener listener) {
		throw new IllegalStateException("Player cannot have its error listener set! Use " +
				"addOnErrorListener instead.");
	}

	public void setOnStateChangedListener(OnStateChangedListener listener) {
		mStateListener = listener;
	}

	public interface OnStateChangedListener {
		void onStateChanged(@Nullable State previousState, @NonNull State newState);
	}

	public class Controls implements SeekBar.OnSeekBarChangeListener, View.OnClickListener,
			View.OnTouchListener {

		private static final int SHOW_DURATION = 3000;
		private static final int CURRENT_TIME_RECHECK_INTERVAL = 300;
		private final String TAG = getClass().getName();

		private RelativeLayout mContainer;
		private SeekBar mSeekBar;
		private ImageView mPlayButton;
		private TextView mCurrentTime, mTotalTime;
		private Handler mVisibilityHandler = new Handler();
		private Handler mSeekHandler = new Handler();

		public Controls(View container) {
			mContainer = (RelativeLayout) container.findViewById(R.id.player_controls);
			mSeekBar = (SeekBar) mContainer.findViewById(R.id.seek_bar);
			mSeekBar.setOnSeekBarChangeListener(this);
			mPlayButton = (ImageView) mContainer.findViewById(R.id.play_button);
			mPlayButton.setOnClickListener(this);
			mCurrentTime = (TextView) mContainer.findViewById(R.id.current_time);
			mTotalTime = (TextView) mContainer.findViewById(R.id.total_time);
		}

		public void setTotal(int msec) {
			int secs = msec / 1000;
			mTotalTime.setText(Utils.secsToTime(secs));
			mSeekBar.setMax(msec);
		}

		public void setCurrent(int msec) {
			int secs = msec / 1000;
			mCurrentTime.setText(Utils.secsToTime(secs));
			mSeekBar.setProgress(msec);
		}

		public void setPlaying(boolean isPlaying) {
			mSeekHandler.removeCallbacksAndMessages(null);
			if (isPlaying) {
				mSeekHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						mSeekHandler.postDelayed(this, CURRENT_TIME_RECHECK_INTERVAL);
						setCurrent(getCurrentPosition());
					}
				}, CURRENT_TIME_RECHECK_INTERVAL);

				mPlayButton.setImageResource(R.drawable.ic_action_error);
			} else {
				mPlayButton.setImageResource(R.drawable.ic_action_play_dark);
			}
		}

		/**
		 * Show controls and dismisses them in {@link #SHOW_DURATION}.
		 */
		public void show() {
			mVisibilityHandler.removeCallbacksAndMessages(null);
			// When controls are shown, touching children will just reset the visibility timer
			setChildrenTouchListener(this);
			mContainer.setVisibility(View.VISIBLE);
			mVisibilityHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					hide();
				}
			}, SHOW_DURATION);
		}

		/**
		 * If container is disabled, do nothing.
		 */
		public void hide() {
			mContainer.setVisibility(View.INVISIBLE);
			// When controls are hidden, children can't receive any touch events
			setChildrenTouchListener(null);
		}

		private void setChildrenTouchListener(View.OnTouchListener listener) {
			mContainer.setOnTouchListener(listener);
			mPlayButton.setOnTouchListener(listener);
			mCurrentTime.setOnTouchListener(listener);
			mSeekBar.setOnTouchListener(listener);
			mTotalTime.setOnTouchListener(listener);
		}

		public boolean isVisible() {
			return mContainer.getVisibility() == View.VISIBLE;
		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (fromUser) {
				seekTo(progress);
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {}

		@Override
		public void onClick(View v) {
			if (isPlaying()) {
				pause();
			} else {
				start();
			}
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			show();
			return false;
		}

	}

}
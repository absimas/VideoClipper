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

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.simas.vc.R;
import com.simas.vc.helpers.Utils;

/**
 * Controls have a circular connection with {@link Player}. The player is only called when:
 * <ul>
 *  <li>Manual seek (from user)</li>
 *  <li>Play/Pause button is pressed</li>
 *  <li>SeekBar update is called and it needs to know the current {@link Player}'s position.</li>
 * </ul>
 * If in any of above circumstances the player is not connected, nothing will be done.
 */
public class Controls extends Fragment
		implements SeekBar.OnSeekBarChangeListener, View.OnClickListener, View.OnTouchListener {

	private static final int SHOW_DURATION = 3000;
	private static final int CURRENT_TIME_RECHECK_INTERVAL = 300;
	private final String TAG = getClass().getName();

	/**
	 * If set, will override the default behaviour of the play button click event.
	 */
	private PlayClickOverrider mPlayClickOverrider;
	private Player mPlayer;
	private RelativeLayout mContainer;
	private SeekBar mSeekBar;
	private ImageView mPlayButton;
	private TextView mCurrentTime, mDuration;
	private View mProgressContainer;
	private final Handler mVisibilityHandler = new Handler();
	private final Handler mSeekHandler = new Handler();

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mContainer = (RelativeLayout) inflater
				.inflate(R.layout.fragment_player_controls, container, false);
		mSeekBar = (SeekBar) mContainer.findViewById(R.id.seek_bar);
		mSeekBar.setOnSeekBarChangeListener(this);
		mPlayButton = (ImageView) mContainer.findViewById(R.id.play_button);
		mPlayButton.setOnClickListener(this);
		mCurrentTime = (TextView) mContainer.findViewById(R.id.current_time);
		mDuration = (TextView) mContainer.findViewById(R.id.duration);
		mProgressContainer = mContainer.findViewById(R.id.progress_bar_container);

		return mContainer;
	}

	/**
	 * Set an overrider to provide custom functionality when the play button is pressed. If set
	 * to null will return to the default (play/stop according to {@link Player}'s state).
	 */
	public void setPlayClickOverrider(PlayClickOverrider overrider) {
		mPlayClickOverrider = overrider;
	}

	public void setPlayer(Player player) {
		mPlayer = player;
	}

	public Player getPlayer() {
		return mPlayer;
	}

	public void setDuration(int msec) {
		int secs = msec / 1000;
		mDuration.setText(Utils.secsToTime(secs));
		mSeekBar.setMax(msec);
	}

	public void setCurrent(int msec) {
		int secs = msec / 1000;
		mCurrentTime.setText(Utils.secsToTime(secs));
		mSeekBar.setProgress(msec);
	}

	public int getCurrent() {
		return mSeekBar.getProgress();
	}

	public int getDuration() {
		return mSeekBar.getMax();
	}

	public void setPlaying(boolean isPlaying) {
		mSeekHandler.removeCallbacksAndMessages(null);
		if (isPlaying) {
			mSeekHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (getPlayer() != null) {
						mSeekHandler.postDelayed(this, CURRENT_TIME_RECHECK_INTERVAL);
						setCurrent(getPlayer().getCurrentPosition());
					}
				}
			}, CURRENT_TIME_RECHECK_INTERVAL);
			mPlayButton.setImageResource(R.drawable.ic_action_pause);
		} else {
			mPlayButton.setImageResource(R.drawable.ic_action_play);
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
		mDuration.setOnTouchListener(listener);
	}

	public boolean isShown() {
		return mContainer.getVisibility() == View.VISIBLE;
	}

	/**
	 * This will overlay the controls with a {@link android.widget.ProgressBar}. To reveal the
	 * controls, this must be hidden once again. By default the loading overlay is hidden.
	 * @param visible    true if the overlay should be shown, false otherwise
	 */
	public void setLoading(boolean visible) {
		mProgressContainer.setVisibility((visible) ? View.VISIBLE : View.GONE);
	}

	public void reset() {
		setLoading(false);
		setCurrent(0);
		setDuration(0);
		setPlaying(false);
		hide();
	}

	public boolean isReset() {
		return getCurrent() == 0 && getDuration() == 0;
	}



	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		// If user invoked a seek, apply it to the player
		if (fromUser && getPlayer() != null) {
			getPlayer().seekTo(progress);
		} else {
			setCurrent(progress);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onClick(View v) {
		// Invoke the Player's start/pause method if the overrider is not set
		if (mPlayClickOverrider == null) {
			if (getPlayer() != null) {
				if (getPlayer().isPlaying()) {
					getPlayer().pause();
				} else {
					getPlayer().start();
				}
			}
		} else {
			mPlayClickOverrider.onPlayClicked();
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		show();
		return false;
	}

	/**
	 * Listener that's invoked when the play/pause button is clicked.
	 */
	public interface PlayClickOverrider {
		void onPlayClicked();
	}

}
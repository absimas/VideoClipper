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

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.simas.vc.R;
import com.simas.vc.Utils;

public class Controls implements SeekBar.OnSeekBarChangeListener, View.OnClickListener,
		View.OnTouchListener {

	private static final int SHOW_DURATION = 3000;
	private static final int CURRENT_TIME_RECHECK_INTERVAL = 300;
	private final String TAG = getClass().getName();

	private Player mPlayer;
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

	public void setPlayer(Player player) {
		mPlayer = player;
	}

	public Player getPlayer() {
		return mPlayer;
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
			if (getPlayer() != null) {
				mSeekHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						mSeekHandler.postDelayed(this, CURRENT_TIME_RECHECK_INTERVAL);
						setCurrent(getPlayer().getCurrentPosition());
					}
				}, CURRENT_TIME_RECHECK_INTERVAL);
			}

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
		if (fromUser && getPlayer() != null) {
			getPlayer().seekTo(progress);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onClick(View v) {
		if (getPlayer() != null) {
			if (getPlayer().isPlaying()) {
				getPlayer().pause();
			} else {
				getPlayer().start();
			}
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		show();
		return false;
	}

}
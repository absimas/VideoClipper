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
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.simas.vc.DelayedHandler;
import com.simas.vc.R;

import java.io.IOException;

public class PlayerFragment extends Fragment implements SurfaceHolder.Callback,
		View.OnTouchListener, MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {

	private final String TAG = getClass().getName();
	private Player mPlayer;
	private RelativeLayout mContainer;
	private SurfaceHolder mHolder;
	private ImageView mDeadSmiley;
	private GestureDetector mGestureDetector;
	/**
	 * Handler that runs the queued messages at the end of
	 * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
	 */
	private DelayedHandler mDelayedHandler = new DelayedHandler(new Handler());

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mContainer = (RelativeLayout) inflater.inflate(R.layout.fragment_player, container, false);
		getContainer().setOnTouchListener(this);

		mDeadSmiley = (ImageView) getContainer().findViewById(R.id.dead_smiley);
		SurfaceView surfaceView = (SurfaceView) getContainer().findViewById(R.id.player_surface);

		// Player references controls, so create them in order
		mPlayer = new Player(getContainer());

		// Custom listeners to show and hide the dead smiley overlay
		getPlayer().setOnErrorListener(this);
		getPlayer().addOnPreparedListener(this);

		// Gesture detector for root dialog (to detect double clicks)
		mGestureDetector = new GestureDetector(getActivity(),
				new GestureDetector.SimpleOnGestureListener() {
					@Override
					public boolean onDown(MotionEvent e) {
						return true;
					}

					@Override
					public boolean onSingleTapConfirmed(MotionEvent e) {
						if (getControls().isVisible()) {
							getControls().hide();
						} else {
							getControls().show();
						}
						return true;
					}

					@Override
					public boolean onDoubleTap(MotionEvent e) {
						toggleFullscreen();
						return true;
					}
				});

		mHolder = surfaceView.getHolder();
		mHolder.addCallback(this);

		mDelayedHandler.resume();

		return getContainer();
	}

	private void toggleFullscreen() {
		// ToDo Move mContainer to decor or back to def parent
	}

	public void setVideo(String path) {
		MediaPlayer.OnPreparedListener listener = new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				getPlayer().removeOnPreparedListener(this);
				getContainer().setOnTouchListener(PlayerFragment.this);
			}
		};
		try {
			getContainer().setOnTouchListener(null);
			getPlayer().setDataSource(path);
			getPlayer().addOnPreparedListener(listener);
			getPlayer().prepareAsync();
		} catch (IOException | IllegalStateException e) {
			e.printStackTrace();
			showDeathOverlay();
			getPlayer().removeOnPreparedListener(listener);
		}
	}

	/**
	 * Displays a dead smiley face on top of the container.
	 */
	private void showDeathOverlay() {
		// Remove touch listener, so GestureDetector is not invoked
		getContainer().setOnTouchListener(null);
		getControls().hide();
		mDeadSmiley.setVisibility(View.VISIBLE);
	}

	/**
	 * Makes sure the dead smiley from {@link #showDeathOverlay()} isn't showing.
	 */
	private void hideDeathOverlay() {
		// Reset the touch listener, so GestureDetector is invoked
		getContainer().setOnTouchListener(this);
		mDeadSmiley.setVisibility(View.GONE);
	}

	public void post(Runnable runnable) {
		mDelayedHandler.add(runnable);
	}

	private void setHolder(SurfaceHolder surfaceHolder) {
		mHolder = surfaceHolder;
		getPlayer().setDisplay(mHolder);
	}

	public Player.Controls getControls() {
		return getPlayer().getControls();
	}

	public Player getPlayer() {
		return mPlayer;
	}

	public RelativeLayout getContainer() {
		return mContainer;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		setHolder(holder);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		setHolder(holder);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		setHolder(null);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		return mGestureDetector != null && mGestureDetector.onTouchEvent(event);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.e(TAG, String.format("Error: %d : %d", what, extra));
		showDeathOverlay();
		return false;
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		hideDeathOverlay();
	}

}

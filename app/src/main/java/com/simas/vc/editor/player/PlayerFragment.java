package com.simas.vc.editor.player;

import android.annotation.TargetApi;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.simas.vc.VC;
import com.simas.wvc.R;
import com.simas.vc.ResumableHandler;

/**
 * Created by Simas Abramovas on 2015 May 03.
 */

// ToDo double tap not working when pressing the container

public class PlayerFragment extends Fragment implements View.OnKeyListener {

	private static final String PLAYER_STATE = "player_state";
	private static final String PLAYER_FULLSCREEN_STATE = "player_container_fs";
	private final String TAG = getClass().getName();

	// Views
	private Player mPlayer;
	private ProgressBar mProgressBar;
	private Controls mControls;
	private PlayerContainer mContainer;
	private GestureDetector mGestureDetector;
	private ResumableHandler mResumableHandler = new ResumableHandler(new Handler());

	public PlayerFragment() {}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedState) {
		/* Container */
		mContainer = (PlayerContainer) inflater.inflate(R.layout.player_container, container, false);
		mContainer.setOnKeyListener(this);

		// Send container key events to the player
		mContainer.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return mPlayer.onTouchEvent(event);
			}
		});

		mProgressBar = (ProgressBar) mContainer.findViewById(R.id.progress_bar);

		/* Player */
		mPlayer = (Player) mContainer.findViewById(R.id.player);
		mPlayer.setOnKeyListener(this);

		// Player gesture controller
		mGestureDetector = new GestureDetector(getActivity(), new GestureListener());
		mPlayer.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return mGestureDetector.onTouchEvent(event);
			}
		});

		/* Controls */
		// Create manually, and add to the container
		mControls = new Controls(getActivity());
		mContainer.addView(mControls);
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
		params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
		mControls.setLayoutParams(params);

		mPlayer.setMediaController(mControls);

		// Lower APIs won't have the control visibility listener, as they have nothing to hide )
		if (Build.VERSION.SDK_INT >= 14) {
			// Listen to controller visibility changes
			mControls.setOnVisibilityChangedListener(new Controls.OnVisibilityChangedListener() {
				@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				@Override
				public void onVisibilityChanged(final boolean shown) {
					// Do nothing when not in fullscreen
					if (mContainer != null && !mContainer.isFullscreen()) return;

					if (shown) {
						getActivity().getWindow().getDecorView()
								.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
					} else {
						getActivity().getWindow().getDecorView()
								.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
					}
				}
			});
		}


		// Restore Player and PlayerContainer states
		if (savedState != null) {
			final boolean fs = savedState.getBoolean(PLAYER_FULLSCREEN_STATE, false);
			if (fs) {
				// Restore to fullscreen when the layout is first measured,
				// and only then restore player's state
				mResumableHandler.add(new Runnable() {
					@Override
					public void run() {
						toggleFullscreen();
					}
				});
			}

			if (savedState.getBundle(PLAYER_STATE) != null) {
				mResumableHandler.add(new Runnable() {
					@Override
					public void run() {
						// Restore player state Player
						mPlayer.restoreToState(savedState.getBundle(PLAYER_STATE));
					}
				});
			}
		}

		mContainer.setVisibility(View.VISIBLE);

		return mContainer;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		// Resume the handler once the layout is created
		mResumableHandler.resume();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Save PlayerContainer state here
		if (mContainer == null || mPlayer == null) return;

		outState.putBundle(PLAYER_STATE, mPlayer.getSavedState());
		outState.putBoolean(PLAYER_FULLSCREEN_STATE, mContainer.isFullscreen());
		mControls.removeOnVisibilityChangedListener();
	}

	private class GestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDown(MotionEvent e) {
			return true;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			// Show controls with each tap
			if (mControls.isShown()) {
				mControls.hide();
			} else {
				mControls.show();
			}

			return true;
		}

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			toggleFullscreen();
			return true;
		}
	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && mContainer.isFullscreen()) {
			toggleFullscreen();
			// Need to re-show controls, if already visible, otherwise they're not re-drawn
			if (mControls.isShowing()) {
				mControls.show();
			}
			return true;
		}
		return false;
	}

	public void setProgressVisible(final boolean visible) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// Remove the cover
				if (visible) {
					mProgressBar.setVisibility(View.VISIBLE);
				} else {
					mProgressBar.setVisibility(View.GONE);
				}
			}
		});
	}

	public void toggleFullscreen() {
		Bundle state = mPlayer.getSavedState();
		mContainer.toggleFullscreen(); // ToDo toggle bugovas
		mPlayer.restoreToState(state);
	}

	public void setVideoPath(String path) {
		// Cover up and show a ProgressBar
		mControls.hide();
		mPlayer.setBackgroundColor(VC.getAppResources().getColor(R.color.player_container_bg));
		setProgressVisible(true);

		mPlayer.addOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				mPlayer.removeOnPreparedListener(this);
				// Move to the first frame and pause only if nothing is shown yet
				if (mPlayer.getCurrentPosition() == 0) {
					mPlayer.seekTo(1);
					mPlayer.pause();
				}

				// ToDo should do a proper surface clear instead (possibly with Grafika)
				mPlayer.postDelayed(new Runnable() {
					@Override
					public void run() {
						// Remove the ProgressBar and cover
						setProgressVisible(false);
						mPlayer.setBackgroundColor(0);
						mControls.show();
					}
				}, 200);
			}
		});

		mPlayer.setVideoPath(path);
	}

	/**
	 * Will run the given runnable once the fragment has created the view.
	 * @param runnable    {@code Runnable} to be run
	 */
	public void post(Runnable runnable) {
		mResumableHandler.add(runnable);
	}

}

package com.simas.vc.editor.player;

import android.annotation.TargetApi;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
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
import com.simas.vc.R;
import com.simas.vc.DelayedHandler;
import com.simas.vc.nav_drawer.NavDrawerFragment;

/**
 * Created by Simas Abramovas on 2015 May 03.
 */

// ToDo double tap not working when pressing the controls, do interceptTouchEvent
	// Perhaps should just scale controls for lower density devices

public class PlayerFragment extends Fragment implements View.OnKeyListener {

	private static final String PLAYER_STATE = "player_state";
	private static final String PLAYER_FULLSCREEN_STATE = "player_container_fs";
	private static final String PLAYER_CONTROLS_VISIBILITY = "player_controls_visibility";
	private final String TAG = getClass().getName();

	// Views
	private Player mPlayer;
	private ProgressBar mProgressBar;
	private Controls mControls;
	private PlayerContainer mContainer;
	private boolean mIsRestoring = false;

	/**
	 * Handler runs all the messages posted to it only when the fragment is ready, i.e. at the end
	 * of {@code onActivityCreate}. Messages can be added by calling fragment's {@code post} method.
	 */
	private DelayedHandler mDelayedHandler = new DelayedHandler(new Handler());

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

		// Player gesture detector
		mPlayer.setGestureDetector(new GestureDetector(getActivity(), new GestureListener()));

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
			mIsRestoring = true;
			final boolean fs = savedState.getBoolean(PLAYER_FULLSCREEN_STATE, false);
			if (fs) {
				// Restore to fullscreen when the layout is first measured,
				// and only then restore player's state
				mDelayedHandler.add(new Runnable() {
					@Override
					public void run() {
						toggleFullscreen();
					}
				});
			}

			// Player state
			if (savedState.getBundle(PLAYER_STATE) != null) {
				mDelayedHandler.add(new Runnable() {
					@Override
					public void run() {
						mPlayer.restoreToState(savedState.getBundle(PLAYER_STATE));
					}
				});
			}

			// Controls visibility
			if (savedState.getBoolean(PLAYER_CONTROLS_VISIBILITY, false)) {
				mControls.post(new Runnable() {
					@Override
					public void run() {
						mControls.show();
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
		mDelayedHandler.resume();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Save PlayerContainer state here
		if (mContainer == null || mPlayer == null) return;

		outState.putBundle(PLAYER_STATE, mPlayer.getSavedState());
		outState.putBoolean(PLAYER_FULLSCREEN_STATE, mContainer.isFullscreen());
		outState.putBoolean(PLAYER_CONTROLS_VISIBILITY, mControls.isShowing());
		// Remove visibility listener
		mControls.removeOnVisibilityChangedListener();
		// Remove prepared listeners
		mPlayer.removeAllOnPreparedListeners();
		// Clear player's message queue
		Handler playerHandler = mPlayer.getHandler();
		if (playerHandler != null) playerHandler.removeCallbacksAndMessages(null);
	}

	private class GestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDown(MotionEvent e) {
			return true;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			// Toggle control visibility with a single tap
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
		// Save states
		Bundle state = mPlayer.getSavedState();

		// Switch to/from fullscreen
		mContainer.toggleFullscreen();

		// Restore player state
		mPlayer.restoreToState(state);
	}

	public void setVideoPath(final String path) {
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
						// Make sure we're not restoring player's state, e.g. rotating the device
						if (!mIsRestoring) {
							mControls.show();
						} else {
							mIsRestoring = false;
						}
					}
				}, 200);
			}
		});

		final NavDrawerFragment drawerFragment = (NavDrawerFragment) getActivity()
				.getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
		// If drawer is open or is closing, wait for it to be closed, then change the player's
		// video path
		if (drawerFragment.isDrawerOpen() || drawerFragment.isDrawerClosing()) {
			drawerFragment.addDrawerStateListener(new DrawerLayout.DrawerListener() {
				@Override
				public void onDrawerClosed(View drawerView) {
					drawerFragment.removeDrawerStateListener(this);
					mPlayer.setVideoPath(path);
				}

				@Override
				public void onDrawerSlide(View drawerView, float slideOffset) {}

				@Override
				public void onDrawerOpened(View drawerView) {}

				@Override
				public void onDrawerStateChanged(int newState) {}
			});
		} else {
			mPlayer.setVideoPath(path);
		}
	}

	/**
	 * Queues the given runnable to be run after the fragment is ready.
	 * @param runnable    message to be queued
	 */
	public void post(Runnable runnable) {
		mDelayedHandler.add(runnable);
	}

}

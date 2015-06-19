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

import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.simas.vc.DelayedHandler;
import com.simas.vc.R;
import com.simas.vc.Utils;
import com.simas.vc.nav_drawer.NavItem;
import java.io.IOException;

// ToDo while toggling FS, show a loading overlay (this will fill the space for controls :D)
// ToDo save fullscreen/playing/controls visibility for the active EditorFragment
// ToDo orientation change state saving: FS, Playing, Controls visibility.
	// I presume seek should already be OK, as long as the right page is shown.
// ToDo when paused, continue showing surface? I.E. return to using updatePreview()

public class PlayerFragment extends Fragment implements	View.OnTouchListener,
		MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, View.OnKeyListener,
		SurfaceHolder.Callback, Player.OnStateChangedListener, Controls.PlayClickOverrider {

	/* Instance state variables */
	private static final String STATE_FULLSCREEN = "state_fullscreen";
	private static final String STATE_SEEK_POS = "state_seek_pos";
	private static final String STATE_DURATION = "state_duration";
	private static final String STATE_CONTROLS_VISIBLE = "state_controls_visible";
	private static final String STATE_PLAYING = "state_player_state";

	/* Full screen variables */
	private boolean mFullscreen;
	private LinearLayout.LayoutParams mDefaultContainerParams;
	private ViewGroup mDefaultContainerParent;
	private int mDefaultContainerBottomPadding;

	private final String TAG = getClass().getName();
	private RelativeLayout mContainer;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mHolder;
	private ImageView mErrorOverlay;
	private ImageView mPreview;
	private NavItem mItem;
	/**
	 * Initialized = player connected, data source set and the player is prepared.
	 * This is enable by calling  {@link #setItem(NavItem)} and disabled by
	 * {@link #initializePlayer()}.
	 */
	private boolean mInitialized;
	private Boolean mPlaying;
	private final GestureDetector mGestureDetector = new GestureDetector(getActivity(),
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
			}
	);
	/**
	 * {@link Controls} are fragment specific.
	 */
	private Controls mControls;
	/**
	 * A single {@link Player} is shared between all the {@link PlayerFragment}s.
	 */
	private static Player sPlayer = new Player();
	/**
	 * Handler that runs the queued messages when {@link #mContainer} is ready. I.e. at the end of
	 * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)} and through the post method of
	 * {@link #mContainer}.
	 */
	private DelayedHandler mDelayedHandler = new DelayedHandler();

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
		Log.e(TAG, "onCreateView");
		/* Container */
		mContainer = (RelativeLayout) inflater.inflate(R.layout.fragment_player, container, false);
		getContainer().setOnTouchListener(this);
		getContainer().setOnKeyListener(this);

		/* Overlays */
		mErrorOverlay = (ImageView) getContainer().findViewById(R.id.error_image);
		mPreview = (ImageView) getContainer().findViewById(R.id.preview);

		/* SurfaceView and SurfaceHolder*/
		mSurfaceView = (SurfaceView) getContainer().findViewById(R.id.player_surface);
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(this);

		/* Controls */
		mControls = new Controls(getContainer());
		mControls.setPlayClickOverrider(this);

		/* Saved states */
		if (savedInstanceState != null) {
			mPlaying = savedInstanceState.getBoolean(STATE_PLAYING);

			// Total duration
			int duration = savedInstanceState.getInt(STATE_DURATION, -1);
			if (duration != -1) {
				mControls.setDuration(duration);
			}

			// Seek
			int seekPosition = savedInstanceState.getInt(STATE_SEEK_POS, -1);
			if (seekPosition != -1) {
				mControls.setCurrent(seekPosition);
			}
		}

		getContainer().post(new Runnable() {
			@Override
			public void run() {
//				if (savedInstanceState != null) {
//					if (savedInstanceState.getBoolean(STATE_FULLSCREEN, false) && !isFullscreen()) {
//						toggleFullscreen();
//					}
//				}

//				getPlayer().addOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//					@Override
//					public void onPrepared(MediaPlayer mp) {
//						getPlayer().removeOnPreparedListener(this);
//						if (savedInstanceState != null) {
//							// Playing
//							if (savedInstanceState.getBoolean(STATE_PLAYING, false)) {
//								getPlayer().start();
//							}
//							// Seek
//							final int msec = savedInstanceState.getInt(STATE_SEEK_POS, 0);
//							if (msec > 0) {
//								updatePreview(msec);
//							}
//							// Controls visibility
//							if (savedInstanceState.getBoolean(STATE_CONTROLS_VISIBLE, false)) {
//								getControls().show();
//							} else {
//								getControls().hide();
//							}
//						}
//					}
//				});

				mDelayedHandler.resume();
			}
		});

		return mContainer;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Fullscreen
		outState.putBoolean(STATE_FULLSCREEN, isFullscreen());

		// Controls visibility
		outState.putBoolean(STATE_CONTROLS_VISIBLE, getControls().isVisible());

		// Player state
		if (mControls.getPlayer() != null && getPlayer().getState() != Player.State.ERROR) {
			outState.putBoolean(STATE_PLAYING, mControls.getPlayer().isPlaying());
		}

		// Seek pos
		outState.putInt(STATE_SEEK_POS, mControls.getCurrent());

		// Duration
		outState.putInt(STATE_DURATION, mControls.getDuration());
	}

	void toggleFullscreen() {
		final Runnable toggle = new Runnable() {
			@Override
			public void run() {
				// Controls should be hidden while working
				final boolean controlsWereShown = getControls().isVisible();
				getControls().hide();

				// Video should be paused while working
				boolean playing = false;
				if (getPlayer().getState() == Player.State.STARTED) {
					// Pause if started
					playing = true;
					getPlayer().pause();
				}
				final boolean wasPlaying = playing;

				// Hide surface view while doing all the work, this is to make sure it's not being re-drawn
				mSurfaceView.setVisibility(View.GONE);

				// Toggle state
				setFullscreen(!isFullscreen());

				// Expand or collapse the PlayerView
				if (isFullscreen()) {
					if (Build.VERSION.SDK_INT >= 14) {
						// For higher APIs go into the low profile mode
						getActivity().getWindow().getDecorView()
								.setSystemUiVisibility(ViewGroup.SYSTEM_UI_FLAG_LOW_PROFILE);
					} else {
						// For lower APIs just go for fullscreen flag
						getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					}

					// Save current params
					mDefaultContainerParams = new LinearLayout.LayoutParams(getContainer().getLayoutParams());

					// Save current bottom padding
					mDefaultContainerBottomPadding = getContainer().getPaddingBottom();

					// Remove from current parent
					mDefaultContainerParent = (ViewGroup) getContainer().getParent();
					mDefaultContainerParent.removeView(getContainer());

					// Set new params
					ViewGroup.LayoutParams params = getContainer().getLayoutParams();
					params.width = LinearLayout.LayoutParams.MATCH_PARENT;
					params.height = LinearLayout.LayoutParams.MATCH_PARENT;

					// Set bottom padding, so controls don't appear underneath the nav bar
					getContainer()
							.setPadding(getContainer().getPaddingLeft(), getContainer().getPaddingTop(),
									getContainer().getPaddingRight(), Utils.getNavigationBarHeight());

					// Re-measure the SurfaceView
					getContainer().setRight(0);
					getContainer().setLeft(0);
					invalidateSurface();

					// Add to the root view
					ViewGroup rootView = (ViewGroup) getActivity().getWindow().getDecorView().getRootView();
					rootView.addView(getContainer()); // Add as last view, so it's on top of everything else
					getContainer().requestFocus();
				} else {
					if (Build.VERSION.SDK_INT >= 14) {
						// For higher APIs remove the low profile mode
						getActivity().getWindow().getDecorView()
								.setSystemUiVisibility(ViewGroup.SYSTEM_UI_FLAG_VISIBLE);
					} else {
						// For lower APIs just remove the fullscreen flag
						getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					}

					if (mDefaultContainerParams != null && mDefaultContainerParent != null) {
						// Remove from current parent
						((ViewGroup)getContainer().getParent()).removeView(getContainer());

						// Restore params
						getContainer().setLayoutParams(mDefaultContainerParams);

						// Remove bottom padding
						getContainer()
								.setPadding(getContainer().getPaddingLeft(), getContainer().getPaddingTop(),
										getContainer().getPaddingRight(), mDefaultContainerBottomPadding);

						// Re-measure the SurfaceView
						getContainer().setRight(0);
						getContainer().setLeft(0);
						invalidateSurface();

						// Add as the first child to the default parent
						mDefaultContainerParent.addView(getContainer(), 0);
					}
				}

				getContainer().post(new Runnable() {
					@Override
					public void run() {
						mSurfaceView.setVisibility(View.VISIBLE);
						switch (getPlayer().getState()) {
							case PAUSED: case PREPARED: case STARTED:
								if (wasPlaying) {
									getPlayer().start();
								} else {
									updatePreview();
								}
								if (controlsWereShown) {
									getControls().show();
								}
								break;
						}
					}
				});
			}
		};

		// If player is currently preparing, delay toggling fullscreen
		if (getPlayer().getState() == Player.State.PREPARING) {
			getPlayer().addOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mp) {
					toggle.run();
				}
			});
		} else {
			toggle.run();
		}
	}

	/**
	 * Will update the preview by seeking to the current position. Won't do anything if in
	 * {@link Player.State#ERROR} state.
	 */
	private void updatePreview() {
//		if (getPlayer().getState() != Player.State.ERROR) {
//			updatePreview(getPlayer().getCurrentPosition());
//		}
	}

	/**
	 * Update preview to the given time. Won't change the state but will
	 * {@link MediaPlayer#seekTo(int)} to the given position. Will do nothing if the state is not
	 * one of: {@link Player.State#PREPARED}, {@link Player.State#STARTED} or
	 * {@link Player.State#PAUSED}.
	 * @param msec    time at which the duration should be taken.
	 */
	private void updatePreview(final int msec) {
		switch (getPlayer().getState()) {
			case PREPARED: case STARTED: case PAUSED:
				getContainer().post(new Runnable() {
					@Override
					public void run() {
						// Make sure the state hasn't changed while posting
						switch (getPlayer().getState()) {
							case PREPARED: case STARTED: case PAUSED:
								getPlayer().seekTo(msec);
						}
					}
				});
		}
	}

	public boolean isFullscreen() {
		return mFullscreen;
	}

	private void setFullscreen(boolean fullscreen) {
		mFullscreen = fullscreen;
	}

	public void setPreview(Bitmap preview) {
		mPreview.setImageBitmap(preview);
	}

	public void setPreviewVisible(boolean visible) {
		mPreview.setVisibility((visible) ? View.VISIBLE : View.GONE);
	}

	public NavItem getItem() {
		return mItem;
	}

	public void setInitialized(boolean required) {
		mInitialized = required;
	}

	public boolean isInitialized() {
		return mInitialized;
	}

	public void setItem(NavItem item) {
		NavItem previous = mItem;
		mItem = item;
		// If it's a new item, player needs re-initialization
		if (previous != mItem) {
			setInitialized(false);
			if (previous != null) {
				getControls().reset();
			}
		}
	}

	public void initializePlayer() {
		// Connect Player to this fragment's Controls and SurfaceView
		connectPlayer();

		// Modify player's data source (if it's new)
		if (getPlayer().getDataSource() != getItem().getFile().getPath()) {
			// Switch player to IDLE state
			Player.State state = getPlayer().getState();
			if (state != Player.State.IDLE) {
				if (state != Player.State.INITIALIZED && state != Player.State.ERROR) {
					getPlayer().stop();
				}
				getPlayer().reset();
			}
			try {
				getPlayer().setDataSource(getItem().getFile().getPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// If Controls are reset, update them according to Player, otherwise update Player
		final Runnable updateControlsOrPlayer = new Runnable() {
			@Override
			public void run() {
				if (getControls().isReset()) {
					getPlayer().updateControls();
				} else {
					getPlayer().seekTo(getControls().getCurrent());
				}
			}
		};

		/* Prepare and start the player */
		final MediaPlayer.OnPreparedListener prepListener = new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				getPlayer().removeOnPreparedListener(this);
				updateControlsOrPlayer.run();
				setInitialized(true);
				getPlayer().start();
			}
		};

		switch (getPlayer().getState()) {
			case INITIALIZED: case STOPPED:
				// Prepare the Player
				getPlayer().addOnPreparedListener(prepListener);
				getPlayer().prepareAsync();
				break;
			case PREPARING:
				getPlayer().addOnPreparedListener(prepListener);
				break;
			case ERROR: case RELEASED: case IDLE:
				// Make sure the error overlay is shown on error
				showErrorOverlay();
				break;
			case STARTED:
				// The video is already playing, do nothing
				break;
			case PREPARED: case PAUSED:
				updateControlsOrPlayer.run();
				setInitialized(true);
				getPlayer().start();
				break;
		}
	}

	@Override
	public void onStateChanged(@Nullable Player.State previous, @NonNull Player.State newState) {
		// hide preview in: STARTED, ERROR, RELEASED, PAUSED
		// show preview in: STOPPED, PREPARED, PREPARING, IDLE, INITIALIZED
		switch (newState) {
			case STARTED: case ERROR: case RELEASED: /*case PAUSED:*/
				setPreviewVisible(false);
				break;
			default:
				setPreviewVisible(true);
		}
	}

	/**
	 * Removes any previous connections with other fragments and re-connects to this one. Will do
	 * nothing if this fragment is already connected to the player.
	 */
	private void connectPlayer() {
		// If the player is already connected to this fragment's Controls don't re-connect it
		if (getPlayer().getControls() != getControls()) {
			// Reset the previous MediaPlayer listeners and states
			getPlayer().setDisplay(null);

			// Remove previous listeners from the Player
			getPlayer().removeOnErrorListeners();
			getPlayer().removeOnPreparedListeners();

			// Cancel the previous Player-Controls combo
			if (getPlayer().getControls() != null) {
				getPlayer().getControls().setPlayer(null);
				getPlayer().setControls(null);
			}

			// Set the Player to draw to this fragment's SurfaceHolder
			if (mHolder.getSurface().isValid()) {
				getPlayer().setDisplay(mHolder);
			}

//			if (mHolder.getSurface().isValid()) {
//				getPlayer().setDisplay(mHolder);
//			} else {
//				mHolder.addCallback(new SurfaceHolder.Callback() {
//					@Override
//					public void surfaceCreated(SurfaceHolder holder) {
//						if (holder.getSurface().isValid()) {
//							holder.removeCallback(this);
//							Player player = getControls().getPlayer();
//							if (player != null) {
//								player.setDisplay(holder);
//							}
//						}
//					}
//
//					@Override
//					public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//					@Override
//					public void surfaceDestroyed(SurfaceHolder holder) {}
//				});
//			}


			// Add listeners applicable to this fragment
			getPlayer().addOnErrorListener(this);
			getPlayer().addOnPreparedListener(this);
			getPlayer().setOnStateChangedListener(this);

			// Set the new Player-Controls combo
			getControls().setPlayer(getPlayer());
			getPlayer().setControls(getControls());
		}
	}

	/**
	 * Displays a dead smiley face on top of the container.
	 */
	private void showErrorOverlay() {
		// Remove touch listener, so GestureDetector is not invoked
		getContainer().setOnTouchListener(null);
		getControls().hide();
		mErrorOverlay.setVisibility(View.VISIBLE);
	}

	/**
	 * Makes sure the dead smiley from {@link #showErrorOverlay()} isn't showing.
	 */
	private void hideErrorOverlay() {
		// Reset the touch listener, so GestureDetector is invoked
		getContainer().setOnTouchListener(this);
		mErrorOverlay.setVisibility(View.GONE);
	}

	public void post(Runnable runnable) {
		mDelayedHandler.add(runnable);
	}

	public Controls getControls() {
		return mControls;
	}

	public static Player getPlayer() {
		return sPlayer;
	}

	public RelativeLayout getContainer() {
		return mContainer;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		return mGestureDetector != null && mGestureDetector.onTouchEvent(event);
	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && isFullscreen()) {
			toggleFullscreen();
			return true;
		}
		return false;
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.e(TAG, String.format("Error: %d : %d", what, extra));
//		showErrorOverfaclay(); // ToDo retry button
		return false;
	}

	@Override
	public void onPrepared(final MediaPlayer mp) {
		// Re-measure the SurfaceView
		invalidateSurface();
		// Show controls
		getControls().show();
		// Hide overlay (if any)
		hideErrorOverlay();
	}

	/**
	 * Update surface's dimensions according to the {@link #sPlayer} loaded video.
	 */
	private void invalidateSurface() {
		final int iw = getPlayer().getVideoWidth(), ih = getPlayer().getVideoHeight();
		final Runnable containerUpdater = new Runnable() {
			@Override
			public void run() {
				// iw/ih - input, cw/ch - container, w/h - final output sizes
				int w, h;
				int cw = getContainer().getWidth(), ch = getContainer().getHeight();
				if (cw == 0 || ch == 0) {
					// If container still has 0 width or height use the video's dimensions
					w = iw;
					h = ih;
				} else {
					if (iw > ih) {
						double modifier = (double) cw / iw;
						w = cw;
						h = (int) (ih * modifier);
					} else {
						double modifier = (double) ch / ih;
						h = ch;
						w = (int) (iw * modifier);
					}
				}

				// Rescale the surface to fit the prepared video
				ViewGroup.LayoutParams params = mSurfaceView.getLayoutParams();
				params.width = w;
				params.height = h;

				// Re-draw with the new dimensions
				mSurfaceView.invalidate();
			}
		};
		// If container's height or width are 0 or it has no parent, wait for it drawn/added
		if (getContainer().getWidth() <= 0 || getContainer().getHeight() <= 0 ||
				getContainer().getParent() == null) {
			getContainer().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom,
				                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
					if (getContainer().getWidth() > 0 && getContainer().getHeight() > 0 &&
							getContainer().getParent() != null) {
						getContainer().removeOnLayoutChangeListener(this);
						containerUpdater.run();
					}
				}
			});
		} else {
			containerUpdater.run();
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder.getSurface().isValid()) {
			Player player = getControls().getPlayer();
			if (player != null) {
				player.setDisplay(holder);
			}
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (holder.getSurface().isValid()) {
			Player player = getControls().getPlayer();
			if (player != null) {
				player.setDisplay(holder);
			}
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
//		getPlayer().setDisplay(null);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		Log.e(TAG, "destroy view");
	}

	@Override
	public void onPlayClicked() {
		if (!isInitialized()) {
			initializePlayer();
		} else {
			if (getPlayer().isPlaying()) {
				getPlayer().pause();
			} else {
				getPlayer().start();
			}
		}
	}
}

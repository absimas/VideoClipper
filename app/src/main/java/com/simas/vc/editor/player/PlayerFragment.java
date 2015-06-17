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

import java.io.IOException;

// ToDo! PlayerFragment shouldn't handle sPlayer lifecycle.
// ToDo! this frag should only save seek position in the instance state, then restore it once video is prepared
	// FS, and play states are more difficult but might still be pretty easy, as view adapter
		// probly properly saves its and all the pages state on orientation changes too
// ToDo! First create a Fragment that doesn't have any state saves. So it uses a static player
	// shared properly. Only then, save states like seek, opened file, fs and re-create accordingly.

// ToDo test holder modifications and callbacks properly
// ToDo restore the use of deathOverlay

public class PlayerFragment extends Fragment implements	View.OnTouchListener,
		MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, View.OnKeyListener, SurfaceHolder.Callback, Player.OnStateChangedListener {

	/* Instance state variables */
	private static final String STATE_FULLSCREEN = "state_fullscreen";
	private static final String STATE_SEEK_POS = "state_seek_pos";
	private static final String STATE_CONTROLS_VISIBLE = "state_controls_visible";
	private static final String STATE_PLAYING = "state_player_state";
	private Integer mSeekPosition;
	private Boolean mPlaying;

	/* Full screen variables */
	private boolean mFullscreen;
	private LinearLayout.LayoutParams mDefaultContainerParams;
	private ViewGroup mDefaultContainerParent;
	private int mDefaultContainerBottomPadding;

	private final String TAG = getClass().getName();
	private RelativeLayout mContainer;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mHolder;
	private ImageView mDeadSmiley;
	private ImageView mPreview;
	private GestureDetector mGestureDetector;
	/**
	 * {@link Controls} are fragment specific.
	 */
	private Controls mControls;
	/**
	 * A single {@link Player} is shared between all the {@link PlayerFragment}s.
	 */
	private static final Player sPlayer = new Player();

	/**
	 * The preview visibility state. False by default.
	 */
	private boolean mPreviewVisible;
	/**
	 * Handler that runs the queued messages when {@link #mContainer} is ready. I.e. at the end of
	 * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)} and through the post method of
	 * {@link #mContainer}.
	 */
	private DelayedHandler mDelayedHandler = new DelayedHandler();

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
		mContainer = (RelativeLayout) inflater.inflate(R.layout.fragment_player, container, false);
		getContainer().setOnTouchListener(this);
		getContainer().setOnKeyListener(this);

		mDeadSmiley = (ImageView) getContainer().findViewById(R.id.dead_smiley);
		mPreview = (ImageView) getContainer().findViewById(R.id.preview);
		mSurfaceView = (SurfaceView) getContainer().findViewById(R.id.player_surface);
		mHolder = mSurfaceView.getHolder();

		mControls = new Controls(getContainer());

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

		if (savedInstanceState != null) {
			mSeekPosition = savedInstanceState.getInt(STATE_SEEK_POS);
			mPlaying = savedInstanceState.getBoolean(STATE_PLAYING);
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

		// Seek pos
		try {
			int pos = getPlayer().getCurrentPosition();
			if (pos > 0) {
				outState.putInt(STATE_SEEK_POS, pos);
			}
		} catch (IllegalStateException ignored) {}

		// Player state
		if (getPlayer().getState() == Player.State.STARTED) {
			outState.putBoolean(STATE_PLAYING, true);
		}
	}

	/**
	 * Should be called only when {@link Player} is in a prepared (or higher) state.
	 * @see
	 * <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#StateDiagram">
	 *     MediaPlayer</a>
	 */
	void toggleFullscreen() {
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

				Player.State state = getPlayer().getState();
				switch (state) {
					case PAUSED: case PREPARED: case STARTED:
						if (wasPlaying) {
							getPlayer().start();
						} else {
							updatePreview();
						}
				}
			}
		});
	}

	/**
	 * Will update the preview by seeking to the current position. Won't do anything if in
	 * {@link Player.State#ERROR} state.
	 */
	private void updatePreview() {
		if (getPlayer().getState() != Player.State.ERROR) {
			updatePreview(getPlayer().getCurrentPosition());
		}
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

//	@Override
//	public void onStop() {
//		super.onStop();
//		if (getActivity() != null && getActivity().isFinishing()) {
//			getPlayer().release();
//		} else {
//			Player.State state = getPlayer().getState();
//			switch (state) {
//				case PREPARED: case STARTED: case PAUSED:
//					getPlayer().stop();
//			}
//		}
//	}
//
//	@Override
//	public void onResume() {
//		super.onResume();
//		Player.State state = getPlayer().getState();
//		switch (state) {
//			case INITIALIZED: case STOPPED:
//				try {
//					getPlayer().prepare();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//		}
//	}

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

	public void setVideo(String path) {
		Log.e(TAG, "set video : " + path);
		// Remove touch listener temporarily
		getContainer().setOnTouchListener(null);

		// Connect to this fragment's Controls and Surface.
		setupPlayer();

		// Change data source (if it's new)
		if (setDataSource(path)) {
			// If the source was changed, delete the saved state
			clearState();
		}

		// ToDo controls need to be updated when fragment is re-used, coz total time is bullshitted
		// ToDo also somethings wrong with the restoration of the current time when page is scrolled a little

		Player.State state = getPlayer().getState();
		switch (state) {
			case INITIALIZED: case STOPPED:
				// Prepare if necessary
				getPlayer().addOnPreparedListener(new MediaPlayer.OnPreparedListener() {
					@Override
					public void onPrepared(MediaPlayer mp) {
						getPlayer().removeOnPreparedListener(this);
						getPlayer().resetControls();
						restoreState();
						getContainer().setOnTouchListener(PlayerFragment.this);
					}
				});
				getPlayer().prepareAsync();
				break;
			case ERROR: case RELEASED:
				// Do nothing on error
				break;
			default:
				getPlayer().resetControls();
				// Restore states and touch listener if player is alright
				restoreState();
				getContainer().setOnTouchListener(this);
		}
	}



	private void clearState() {
		mSeekPosition = null;
		mPlaying = null;
	}

	private void restoreState() {
		if (mSeekPosition != null) {
			getPlayer().seekTo(mSeekPosition);
		}
		if (mPlaying != null && mPlaying) {
			getPlayer().start();
		}
	}

	/**
	 * Sets the player's data source.
	 * @return true if changed data source was changed, false if it wasn't
	 */
	private boolean setDataSource(String path) {
		if (getPlayer().getDataSource() == null || !getPlayer().getDataSource().equals(path)) {
			// Switch player to IDLE state
			Player.State state = getPlayer().getState();
			if (state != Player.State.IDLE) {
				if (state != Player.State.INITIALIZED && state != Player.State.ERROR) {
					getPlayer().stop();
				}
				getPlayer().reset();
			}
			// Set the source
			try {
				getPlayer().setDataSource(path);
			} catch (IOException e) {
				e.printStackTrace();
				showDeathOverlay();
			}

			return true;
		} else {
			return false;
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
	private void setupPlayer() {
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

			// Update the MediaPlayer so it conforms to this fragment
			Log.e(TAG, "mholder: " + mHolder.isCreating());
			Log.e(TAG, "mholder: " + mHolder.getSurface());
			Log.e(TAG, "mholder: " + mHolder.getSurface().isValid());

			if (mHolder.getSurface().isValid()) {
				getPlayer().setDisplay(mHolder);
			} else {
				mHolder.addCallback(this);
			}

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
		showDeathOverlay();
		return false;
	}

	@Override
	public void onPrepared(final MediaPlayer mp) {
		// Re-measure the SurfaceView
		invalidateSurface();
		// Show controls
		getControls().show();
		// Hide overlay (if any)
		hideDeathOverlay();
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
			mHolder.removeCallback(this);
			getPlayer().setDisplay(holder);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//		getPlayer().setDisplay(holder);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
//		getPlayer().setDisplay(null);
	}


}

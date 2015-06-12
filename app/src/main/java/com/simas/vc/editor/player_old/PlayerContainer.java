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
package com.simas.vc.editor.player_old;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.simas.vc.Utils;

// ToDo test for APIs < 14
// ToDo Animate PlayerContainer when switching to/from fullscreen
	// Skip animation when restoring state in EditorFragment, though

/**
 * Since this container can be moved to the rootView (when going fullscreen), the state is saved by
 * it's parent, {@code EditorFragment}.
 */
public final class PlayerContainer extends RelativeLayout {

	private final String TAG = getClass().getName();
	private boolean mFullscreen;
	private Activity mActivity;
	private LinearLayout.LayoutParams mDefaultLayoutParams;
	private ViewGroup mDefaultParent;
	private int mDefaultBottomPadding;

	public PlayerContainer(Context context) {
		super(context);
		init();
	}

	public PlayerContainer(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public PlayerContainer(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public PlayerContainer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	private void init() {
		try {
			mActivity = (Activity) getContext();
		} catch (ClassCastException e) {
			e.printStackTrace();
		}
	}

	void toggleFullscreen() {
		// Toggle state
		setFullscreen(!isFullscreen());

		// Expand or collapse the PlayerView
		if (mFullscreen) {
			if (Build.VERSION.SDK_INT >= 14) {
				// For higher APIs go into the low profile mode
				mActivity.getWindow().getDecorView()
						.setSystemUiVisibility(SYSTEM_UI_FLAG_LOW_PROFILE);
			} else {
				// For lower APIs just go for fullscreen flag
				mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}

			// Save current params
			mDefaultLayoutParams = new LinearLayout.LayoutParams(getLayoutParams());

			// Save current bottom padding
			mDefaultBottomPadding = getPaddingBottom();

			// Set bottom padding, so controls don't appear underneath the nav bar
			setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
					Utils.getNavigationBarHeight());

			// Set new params
			ViewGroup.LayoutParams params = getLayoutParams();
			params.width = LinearLayout.LayoutParams.MATCH_PARENT;
			params.height = LinearLayout.LayoutParams.MATCH_PARENT;
			setLayoutParams(params);

			// Remove from current parent
			mDefaultParent = (ViewGroup) getParent();
			mDefaultParent.removeView(this);

			// Add to the root view
			ViewGroup rootView = (ViewGroup) mActivity.getWindow().getDecorView().getRootView();
			rootView.addView(this); // Add as last view, so it's on top of everything else
			// In order for onKeyDown to be called, focus this view after adding it to root
			requestFocus();
		} else {
			if (Build.VERSION.SDK_INT >= 14) {
				// For higher APIs remove the low profile mode
				mActivity.getWindow().getDecorView()
						.setSystemUiVisibility(SYSTEM_UI_FLAG_VISIBLE);
			} else {
				// For lower APIs just remove the fullscreen flag
				mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}

			if (mDefaultLayoutParams != null && mDefaultParent != null) {
				// Restore params
				setLayoutParams(mDefaultLayoutParams);

				// Remove bottom padding
				setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
						mDefaultBottomPadding);

				// Remove from current parent
				((ViewGroup)getParent()).removeView(this);

				// Add as the first child to the default parent
				mDefaultParent.addView(this, 0);
			}
		}
	}

	public boolean isFullscreen() {
		return mFullscreen;
	}

	private void setFullscreen(boolean fullscreen) {
		mFullscreen = fullscreen;
	}

}

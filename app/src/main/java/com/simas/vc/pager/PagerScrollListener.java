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
package com.simas.vc.pager;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.simas.vc.MainActivity;
import com.simas.vc.R;
import com.simas.vc.VC;
import com.simas.vc.editor.EditorFragment;
import com.simas.vc.editor.player.Player;
import com.simas.vc.editor.player.PlayerFragment;

// ToDo try locking the surface instead of displaying an overlay preview.
	// or perhaps parsing the surface's locked canvas to the overlay
		// which would be done each time pause is called (not only though)

public class PagerScrollListener implements ViewPager.OnPageChangeListener {

	private final String TAG = getClass().getName();
	private static final float MIN_SCROLL_OFFSET = 0.01f;
	private ViewPager mPager;
	private PagerAdapter mPagerAdapter;
	private Activity mActivity;
	private int mPosition;
	private float mCurrentOffset;

	public PagerScrollListener(Activity activity, PagerAdapter pagerAdapter, ViewPager pager) {
		mActivity = activity;
		mPagerAdapter = pagerAdapter;
		mPager = pager;
	}

	@Override
	public void onPageSelected(final int position) {
		try {
			mActivity.setTitle(MainActivity.sItems.get(position).getFile().getName());
		} catch (NullPointerException ignored) {
			mActivity.setTitle(VC.getStr(R.string.app_name));
		}

		Log.e(TAG, "Selected: " + position + " pagers item is: " + mPager.getCurrentItem());

		Fragment fragment = mPagerAdapter.getCreatedItem(position);
		if (fragment != null && fragment instanceof EditorFragment) {
			final PlayerFragment playerFrag = ((EditorFragment)fragment).getPlayerFragment();
			if (playerFrag != null) {
				playerFrag.post(new Runnable() {
					@Override
					public void run() {
						playerFrag.setVideo(MainActivity.sItems.get(position).getFile().getPath());
					}
				});
			}
		}
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPxs) {
		final float previousOffset = mCurrentOffset;
		mCurrentOffset = positionOffset;

		// On low offsets restore the preview
		if (Math.abs(mCurrentOffset) < MIN_SCROLL_OFFSET) {
			resetPreview(position);
		} else {
			// currentOffset >= MIN_SCROLL_OFFSET && previousOffset == invalid
			if (Math.abs(previousOffset) < MIN_SCROLL_OFFSET) {
				// Re-invoke onPageScrollStateChanged to notify about the change to a valid offset
				onPageScrollStateChanged(ViewPager.SCROLL_STATE_DRAGGING);
			}
		}
		// Set position *after* possibly re-invoking onPageScrollStateChanged, so it catches the
		// original position
		mPosition = position;
	}

	@Override
	public void onPageScrollStateChanged(int state) {
		switch (state) {
			case ViewPager.SCROLL_STATE_IDLE:
				// Hide player preview for currently visible page
				resetPreview(mPosition);
				break;
			case ViewPager.SCROLL_STATE_DRAGGING:
				// Ignore low offsets, to avoid invalid drags
				if (Math.abs(mCurrentOffset) < MIN_SCROLL_OFFSET) {
					return;
				}

				// Pause player for current page
				pausePlayer(mPosition);

				// Show player preview for current page
				showTemporaryPreview(mPosition);

				// Show player preview for the next visible page
				int nextPosition = (mCurrentOffset > 0) ? mPosition + 1 : mPosition - 1;
				showTemporaryPreview(nextPosition);
				break;
		}
	}

	private void pausePlayer(int position) {
		Fragment fragment = mPagerAdapter.getCreatedItem(position);
		if (fragment != null && fragment instanceof EditorFragment) {
			final PlayerFragment playerFragment = ((EditorFragment)fragment).getPlayerFragment();
			if (playerFragment != null) {
				playerFragment.post(new Runnable() {
					@Override
					public void run() {
						Player player = playerFragment.getPlayer();
						if (player.getState() == Player.State.STARTED) {
							player.pause();
						}
					}
				});
			}
		}
	}

	private void resetPreview(int position) {
		Fragment fragment = mPagerAdapter.getCreatedItem(position);
		if (fragment != null && fragment instanceof EditorFragment) {
			final PlayerFragment playerFragment = ((EditorFragment)fragment).getPlayerFragment();
			if (playerFragment != null) {
				playerFragment.post(new Runnable() {
					@Override
					public void run() {
						playerFragment.resetPreviewVisibility();
					}
				});
			}
		}
	}

	private void showTemporaryPreview(int position) {
		Fragment fragment = mPagerAdapter.getCreatedItem(position);
		if (fragment != null && fragment instanceof EditorFragment) {
			final PlayerFragment playerFragment = ((EditorFragment)fragment).getPlayerFragment();
			if (playerFragment != null) {
				playerFragment.post(new Runnable() {
					@Override
					public void run() {
						playerFragment.setPreviewTemporarilyVisible(true);
					}
				});
			}
		}
	}

}
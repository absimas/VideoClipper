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

import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.*;
import android.support.v4.view.PagerAdapter;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.simas.vc.MainActivity;
import com.simas.vc.helpers.ObservableList;
import com.simas.vc.helpers.Utils;

public class ItemViewPager extends ViewPager {

	private final String TAG = getClass().getName();
	private ImageView mOverlayImage;
	private ViewGroup mPagerParent, mPreviewOverlay;
	private int mRemovedPosition;
	private ViewPager.SimpleOnPageChangeListener mTemporarySwitchListener = new ViewPager
			.SimpleOnPageChangeListener() {
		@Override
		public void onPageScrollStateChanged(int state) {
			super.onPageScrollStateChanged(state);
			if (state == ViewPager.SCROLL_STATE_IDLE) {
				removeOnPageChangeListener(this);

				// Overlay and image while working (prevent flickering)
				mOverlayImage.setImageBitmap(Utils.screenshot2(ItemViewPager.this));
				mPagerParent.addView(mPreviewOverlay);

				// Remove data associated with the unused position
				getAdapter().onItemRemoved(mRemovedPosition);

				// Change the inner count and notify
				getAdapter().setCount(MainActivity.sItems.size());
				getAdapter().notifyDataSetChanged();

				// Switch to the unused page, it will be populated after notifyDataSetChanged
				setCurrentItem(mRemovedPosition, false);

				// When switches have settled, remove the preview and re-enable scrolling
				post(new Runnable() {
					@Override
					public void run() {
						mPagerParent.removeView(mPreviewOverlay);
						setEnabled(true);
					}
				});
			}
		}
	};

	public ItemViewPager(Context context) {
		super(context);
		init();
	}

	public ItemViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		// Create an overlay layout
		mOverlayImage = new ImageView(getContext());
		mOverlayImage.setLayoutParams(new ViewGroup
				.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		mPreviewOverlay = new RelativeLayout(getContext());
		mPreviewOverlay.setBackgroundColor(Color.parseColor("#EEEEEE"));
		mPreviewOverlay.setLayoutParams(new ViewGroup
				.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		mPreviewOverlay.addView(mOverlayImage);

		// Delay until parent is known
		getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				if (getParent() != null) {
					getViewTreeObserver().removeGlobalOnLayoutListener(this);
					mPagerParent = (ViewGroup) getParent();
				}
			}
		});

		// Create a listener
		final String PAGER_OBSERVER = "pager_observer";
		MainActivity.sItems.registerDataSetObserver(new ObservableList.FancyObserver() {
			@Override
			public void onChanged(int position) {

				// If no item was removed or there are no more, just notify we have nothing to do
				if (position < 0 || MainActivity.sItems.size() == 0) {
					getAdapter().setCount(MainActivity.sItems.size());
					getAdapter().notifyDataSetChanged();
					return;
				}

				final int currentPosition = getCurrentItem();
				// If the currently selected position is removed
				if (currentPosition == position && position < getAdapter().getCount()) {
					// Disable scrolling
					setEnabled(false);

					mRemovedPosition = position;
					addOnPageChangeListener(mTemporarySwitchListener);
					// Animate to the previous item if last one removed, forward otherwise
					if (position == getAdapter().getCount()-1) {
						setCurrentItem(position - 1);
					} else {
						setCurrentItem(position + 1);
					}
				} else {
					getAdapter().onItemRemoved(mRemovedPosition);
					getAdapter().setCount(MainActivity.sItems.size());
					getAdapter().notifyDataSetChanged();
				}
			}
		}, PAGER_OBSERVER);
	}

	@Override
	public void setAdapter(final PagerAdapter adapter) {
		if (!(adapter instanceof ItemPagerAdapter)) {
			throw new IllegalArgumentException("ItemViewPager can only use an ItemPagerAdapter.");
		}
		super.setAdapter(adapter);
	}

	@Override
	public ItemPagerAdapter getAdapter() {
		return (ItemPagerAdapter) super.getAdapter();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return !isEnabled() || super.onTouchEvent(event);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		return isEnabled() && super.onInterceptTouchEvent(event);
	}

}

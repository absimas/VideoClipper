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

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.simas.vc.MainActivity;
import com.simas.vc.editor.EditorFragment;
import com.simas.vc.helpers.ObservableList;
import com.simas.vc.helpers.Utils;

/**
 * Custom pager adapter that is based on {@link android.support.v4.app.FragmentStatePagerAdapter}.
 * This also allows to fetch the created Fragment and also to delete one freely.
 */
public class ItemPagerAdapter extends AccessibleFragmentPagerAdapter {

	private final String TAG = getClass().getName();
	private int mCount;

	public ItemPagerAdapter(FragmentManager fm) {
		super(fm);
	}

	@Override
	public Fragment createItem(final int position) {
		// Because of caching and the custom page switching, we may be asked for an item that is
				// no longer needed, just return null there.
		if (position >= MainActivity.sItems.size()) {
			return null;
		}

		final EditorFragment editor = new EditorFragment();
		editor.post(new Runnable() {
			@Override
			public void run() {
				editor.setItem(MainActivity.sItems.get(position));
			}
		});

		return editor;
	}

	@Override
	public int getCount() {
		return mCount;
	}

	/**
	 * Set the internal counter for the adapter. This will invoke {@link #notifyDataSetChanged()}.
	 */
	public void setCount(int count) {
		mCount = count;
	}

}

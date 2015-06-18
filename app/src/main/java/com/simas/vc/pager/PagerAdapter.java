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

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.Log;
import android.util.SparseArray;
import android.view.ViewGroup;

import com.simas.vc.MainActivity;
import com.simas.vc.ObservableSynchronizedList;
import com.simas.vc.editor.EditorFragment;

// ToDo copy FragmentStatePagerAdapter code and re-use mFragments instead of making 2 separate arrays

public class PagerAdapter extends FragmentStatePagerAdapter {

	private final String TAG = getClass().getName();
	private SparseArray<Fragment> mFragments = new SparseArray<>();

	public PagerAdapter(FragmentManager fm) {
		super(fm);
		final String PAGER_OBSERVER = "pager_observer";
		MainActivity.sItems.registerDataSetObserver(new ObservableSynchronizedList.Observer() {
			@Override
			public void onChanged() {
				notifyDataSetChanged();
			}
		}, PAGER_OBSERVER);
	}

	@Override
	public Fragment getItem(final int position) {
		final EditorFragment editor = new EditorFragment();

		mFragments.put(position, editor);

		editor.post(new Runnable() {
			@Override
			public void run() {
				editor.setItem(MainActivity.sItems.get(position));
			}
		});

		return editor;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		super.destroyItem(container, position, object);
		mFragments.put(position, null);
	}

	/**
	 * Returns the fragment associated with this position or null if the fragment hasn't been
	 * set yet or was destroyed.
	 */
	public Fragment getCreatedItem(int position) {
		return mFragments.get(position);
	}

	@Override
	public int getCount() {
		return MainActivity.sItems.size();
	}

}

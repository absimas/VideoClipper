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
package com.simas.vc;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import com.simas.vc.editor.EditorFragment;
import com.simas.versatileviewpager.EmptyFragment;
import com.simas.versatileviewpager.VersatilePagerAdapter;

public class MyPagerAdapter extends VersatilePagerAdapter {

	private final String TAG = getClass().getName();

	public MyPagerAdapter(FragmentManager fm) {
		super(fm);
	}

	@Override
	public Fragment createItem(final int position) {
		if (position < 1) {
			return new EmptyFragment();
		} else {
			final EditorFragment editor = new EditorFragment();
			editor.post(new Runnable() {
				@Override
				public void run() {
					editor.setItem(MainActivity.sItems.get(position-1));
				}
			});
			return editor;
		}
	}

}

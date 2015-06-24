/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simas.vc.pager;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Custom pager adapter that is based on {@link android.support.v4.app.FragmentStatePagerAdapter}.
 * This also allows to fetch the created Fragment and also to delete one freely.
 */
public abstract class AccessibleFragmentPagerAdapter extends PagerAdapter {

	private final String TAG = getClass().getName();
	private static final String ITEMS = "states";

	private final FragmentManager mFragmentManager;
	private FragmentTransaction mCurTransaction = null;

	private ArrayList<Item> mItems = new ArrayList<Item>() {
		@Override
		public Item get(int index) {
			// Return null for negative indexes
			if (index < 0) return null;
			// Lazy instantiate items if they're not present in the list
			while (index >= size()) {
				add(new Item());
			}
			return super.get(index);
		}
	};

	public AccessibleFragmentPagerAdapter(FragmentManager fm) {
		mFragmentManager = fm;
	}

	/**
	 * Create a fragment associated with a specific value
	 */
	public abstract Fragment createItem(int position);

	/**
	 * Get the item for the specific position. If it's not yet created, returns null.
	 */
	public Fragment getItem(int position) {
		return mItems.get(position).fragment;
	}

	/**
	 * Remove the fragment that is connected to this position, also clear the fragment's state.<br/>
	 * This method <b>must</b> be called after an item has been removed <b>and</b> before calling
	 * {@link #notifyDataSetChanged()}. This could possibly be done in an overridden
	 * {@link #notifyDataSetChanged()} method by determining if an item was removed and if so, at
	 * which position.
	 * @param position    the position of the item (before it was removed)
	 */
	public void onItemRemoved(int position) {
		Log.e(TAG, "on removed: " + position);
		if (mCurTransaction == null) {
			mCurTransaction = mFragmentManager.beginTransaction();
		}
		if (getItem(position) != null) {
			mCurTransaction.remove(getItem(position));
		}

		mItems.remove(position);
	}

	@Override
	public int getItemPosition(Object object) {
		// ToDo position unchanged
		for (int i=0; i<mItems.size(); ++i) {
			Item item = mItems.get(i);
			if (item.fragment == object) {
				return i;
			}
		}
		return POSITION_NONE;
	}

	@Override
	public void startUpdate(ViewGroup container) {}

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		Item item = mItems.get(position);

		if (item.fragment != null) {
			return item.fragment;
		}

		if (mCurTransaction == null) {
			mCurTransaction = mFragmentManager.beginTransaction();
		}

		item.fragment = createItem(position);
		if (item.fragment == null) return null;

		Fragment.SavedState fss = item.state;
		if (fss != null) {
			item.fragment.setInitialSavedState(fss);
		}
		item.fragment.setMenuVisibility(false);
		item.fragment.setUserVisibleHint(false);
		mCurTransaction.add(container.getId(), item.fragment);

		return item.fragment;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, @Nullable Object object) {
		if (mCurTransaction == null) {
			mCurTransaction = mFragmentManager.beginTransaction();
		}

		Item item = mItems.get(position);
		if (item.fragment != null) {
			item.state = mFragmentManager.saveFragmentInstanceState(item.fragment);
			mCurTransaction.remove(item.fragment);
			item.fragment = null;
		}
	}

	@Override
	public void finishUpdate(ViewGroup container) {
		if (mCurTransaction != null) {
			mCurTransaction.commitAllowingStateLoss();
			mCurTransaction = null;
			mFragmentManager.executePendingTransactions();
		}
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return ((Fragment)object).getView() == view;
	}

	@Override
	public Parcelable saveState() {
		Bundle state = null;
		// Save items
		if (mItems.size() > 0) {
			state = new Bundle();
			state.putParcelableArrayList(ITEMS, mItems);

			// Save fragment references
			for (int i=0; i<mItems.size(); i++) {
				Fragment f = mItems.get(i).fragment;
				if (f != null && f.isAdded()) {
					String key = "f" + i;
					mFragmentManager.putFragment(state, key, f);
				}
			}
		}
		return state;
	}

	@Override
	public void restoreState(Parcelable state, ClassLoader loader) {
		if (state != null) {
			Bundle bundle = (Bundle)state;
			bundle.setClassLoader(loader);
			mItems.clear();
			if (bundle.getParcelableArrayList(ITEMS) != null) {
				mItems = bundle.getParcelableArrayList(ITEMS);
			}
			Iterable<String> keys = bundle.keySet();
			for (String key: keys) {
				if (key.startsWith("f")) {
					int index = Integer.parseInt(key.substring(1));
					Fragment f = mFragmentManager.getFragment(bundle, key);
					if (f != null) {
						f.setMenuVisibility(false);
						mItems.get(index).fragment = f;
					} else {
						Log.w(TAG, "Bad fragment at key " + key);
					}
				}
			}
		}
	}

	private static class Item implements Parcelable {
		private Fragment fragment;
		private Fragment.SavedState state;

		public Item() {}

		protected Item(Parcel in) {
			state = in.readParcelable(Fragment.SavedState.class.getClassLoader());
		}

		public static final Creator<Item> CREATOR = new Creator<Item>() {
			@Override
			public Item createFromParcel(Parcel in) {
				return new Item(in);
			}

			@Override
			public Item[] newArray(int size) {
				return new Item[size];
			}
		};

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeParcelable(state, flags);
		}
	}

}

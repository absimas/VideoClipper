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
package com.simas.vc.nav_drawer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.simas.vc.MainActivity;
import com.simas.vc.ObservableSynchronizedList;
import com.simas.vc.R;
import com.simas.vc.Utils;
import com.simas.vc.VC;

// ToDo choosing a failed item is permitted and the editor doesn't get updated.
	// should display an error message when selected but previous selection shouldn't be changed
// ToDo test out new bitmap parcelling

/**
 * Custom adapter for navigation list, that's located inside of the drawer.
 */
public class NavAdapter extends BaseAdapter {

	private final String TAG = getClass().getName();
	private LayoutInflater mInflater;
	private ListView mListView;

	private static class ViewHolder {
		private ImageView mImageView;
		private ProgressBar mProgressBar;
		private NavItem mConnectedItem;

		private final NavItem.OnUpdatedListener previewListener = new NavItem.OnUpdatedListener() {
			@Override
			public void onUpdated(final NavItem.ItemAttribute attribute,
			                      final Object oldValue, final Object newValue) {
				if (attribute == NavItem.ItemAttribute.PREVIEW) {
					final ImageView preview = mImageView;
					final ProgressBar progress = mProgressBar;
					final NavItem item = mConnectedItem;

					Utils.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (item.getState() == NavItem.State.INPROGRESS) {
								// Still loading
								preview.setVisibility(View.GONE);
								progress.setVisibility(View.VISIBLE);
							} else {
								preview.setImageBitmap((Bitmap) newValue);
								preview.setVisibility(View.VISIBLE);
								progress.setVisibility(View.GONE);
							}

							if (item.getState() == NavItem.State.INVALID) {
								preview.setBackgroundResource(R.drawable.nav_item_failed);
							} else {
								preview.setBackgroundResource(R.drawable.nav_item_default);
							}
						}
					});
				}
			}
		};

		public void listenTo(NavItem item) {
			// Remove existing connection (if present)
			if (mConnectedItem != null) {
				mConnectedItem.unregisterUpdateListener(previewListener);
			}

			// Specify which item is connected to this listener (so it can be removed later)
			mConnectedItem = item;

			// Add a preview listener to this item
			item.registerUpdateListener(previewListener);
		}
	}

	/**
	 * Default constructor, creating an empty adapter
	 */
	public NavAdapter(Context context, ListView listView) {
		mInflater = LayoutInflater.from(context);
		mListView = listView;

		// Register a listener on the sItems
		final String NAV_ADAPTER_OBSERVER = "nav_adapter_observer";
		MainActivity.sItems.registerDataSetObserver(new ObservableSynchronizedList.Observer() {
			@Override
			public void onChanged() {
				notifyDataSetChanged();
			}
		}, NAV_ADAPTER_OBSERVER);
	}

	@Override
	public int getCount() {
		return MainActivity.sItems.size();
	}

	@Override
	public NavItem getItem(int position) {
		return MainActivity.sItems.get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		if (convertView == null) {
			// Inflate
			convertView = getInflater().inflate(R.layout.nav_item, parent, false);
			((NavItemLayout)convertView).setOnCheckedChangedListener(new NavItemLayout
					.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(LinearLayout view, boolean checked) {
					if (checked) {
						if (getListView() != null && getListView()
								.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE_MODAL) {
							view.setBackgroundColor(VC.getAppResources()
									.getColor(R.color.nav_item_checked));
						} else {
							view.setBackgroundColor(VC.getAppResources()
									.getColor(R.color.nav_item_selected));
						}
					} else {
						view.setBackgroundColor(Color.TRANSPARENT);
					}
				}
			});

			// Save the ViewHolder for re-use
			holder = new ViewHolder();
			holder.mImageView = (ImageView) convertView.findViewById(R.id.preview_image);
			holder.mProgressBar = (ProgressBar) convertView.findViewById(R.id.progress_bar);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		// When calling via the ListView, item count needs to include the header count

		if (getListView().isItemChecked(position + getListView().getHeaderViewsCount())) {
			if (getListView().getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE_MODAL) {
				convertView.setBackgroundColor(VC.getAppResources()
						.getColor(R.color.nav_item_checked));
			} else {
				convertView.setBackgroundColor(VC.getAppResources()
						.getColor(R.color.nav_item_selected));
			}
		} else {
			convertView.setBackgroundColor(Color.TRANSPARENT);
		}

		NavItem item = getItem(position);

		if (item.getState() == NavItem.State.INPROGRESS) {
			// Still loading
			holder.mImageView.setVisibility(View.GONE);
			holder.mProgressBar.setVisibility(View.VISIBLE);
		} else {
			holder.mImageView.setImageBitmap(item.getPreview());
			holder.mImageView.setVisibility(View.VISIBLE);
			holder.mProgressBar.setVisibility(View.GONE);
		}

		if (item.getState() == NavItem.State.INVALID) {
			// Fetching preview/attributes failed => item unusable
			// Fail image already set above, now just add a red border
			holder.mImageView.setBackgroundResource(R.drawable.nav_item_failed);
		} else {
			holder.mImageView.setBackgroundResource(R.drawable.nav_item_default);
		}

		// Listen to preview changes on this item
		holder.listenTo(item);

		return convertView;
	}

	private LayoutInflater getInflater() {
		return mInflater;
	}

	private ListView getListView() {
		return mListView;
	}


}
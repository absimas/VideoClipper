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
import com.simas.vc.R;
import com.simas.vc.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 06.
 */

// ToDo kazkaip pratestint parcel creator. Ar neimanoma, nes bundle pats readina fieldus?

public class NavAdapter extends BaseAdapter {

	private final String TAG = getClass().getName();
	private Context mContext;
	private LayoutInflater mInflater;
	private ListView mListView;
	private List<NavItem> mItems = new ArrayList<>();

	private static class ViewHolder {
		ImageView imageView;
		ProgressBar progressBar;
		private final NavItem.OnUpdatedListener previewListener = new NavItem.OnUpdatedListener() {
			@Override
			public void onUpdated(NavItem.ItemAttribute attribute,
			                      Object oldValue, final Object newValue) {
				if (attribute == NavItem.ItemAttribute.PREVIEW) {
					final ImageView iv = imageView;
					final ProgressBar pb = progressBar;
					final NavItem ni = mConnectedItem;

					Utils.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (ni.getState() == NavItem.State.INPROGRESS) {
								// Still loading
								iv.setVisibility(View.GONE);
								pb.setVisibility(View.VISIBLE);
							} else {
								iv.setImageBitmap((Bitmap) newValue);
								iv.setVisibility(View.VISIBLE);
								pb.setVisibility(View.GONE);
							}

							if (ni.getState() == NavItem.State.INVALID) {
								iv.setBackgroundResource(R.drawable.nav_item_failed);
							} else {
								iv.setBackgroundResource(R.drawable.nav_item_default);
							}
						}
					});
				}
			}
		};
		NavAdapter a;
		private NavItem mConnectedItem;

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
	public NavAdapter(Context context) {
		initForContext(context);
	}

	public void initForContext(Context ctx) {
		mContext = ctx;
		mInflater = LayoutInflater.from(mContext);
	}

	/**
	 * Append given items to the list. If new items actually provided, notify the ListView.
	 * @param items    items to be appended
	 */
	public void addItems(NavItem... items) {
		addItems(Arrays.asList(items));
	}

	/**
	 * Append given items to the list. If new items actually provided, notify the ListView.
	 * @param items    items to be appended
	 */
	public void addItems(List<NavItem> items) {
		if (items != null && items.size() > 0) {
			mItems.addAll(items);
		}
	}

	/**
	 * Change the old items with the new ones. If new items actually provided, notify the ListView.
	 * @param items    items to be initialized with (can be {@code null})
	 */
	public void changeItems(NavItem... items) {
		changeItems(Arrays.asList(items));
	}

	/**
	 * Change the old items with the new ones. If new items actually provided, notify the ListView.
	 * @param items    items to be initialized with (can be {@code null})
	 */
	public void changeItems(List<NavItem> items) {
		if (items != null && items.size() > 0) {
			mItems = items;
		}
	}

	public void addItem(NavItem item) {
		if (mItems == null) {
			// Init array if necessary
			mItems = new ArrayList<>();
		}
		mItems.add(item);
	}

	public void attachToList(ListView listView) {
		mListView = listView;
	}


	@Override
	public int getCount() {
		return (mItems == null) ? 0 : mItems.size();
	}

	@Override
	public NavItem getItem(int position) {
		if (getCount() <= position) {
			return null;
		} else {
			return mItems.get(position);
		}
	}

	public List<NavItem> getItems() {
		return mItems;
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
						view.setBackgroundColor(Color.DKGRAY);
					} else {
						view.setBackgroundColor(Color.TRANSPARENT);
					}
				}
			});
			// Save the ViewHolder for re-use
			holder = new ViewHolder();
			holder.imageView = (ImageView) convertView.findViewById(R.id.preview_image);
			holder.progressBar = (ProgressBar) convertView.findViewById(R.id.progress_bar);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		// When calling via the ListView, item count needs to include the header count
		if (getListView().isItemChecked(position + getListView().getHeaderViewsCount())) {
			convertView.setBackgroundColor(Color.DKGRAY);
		} else {
			convertView.setBackgroundColor(Color.TRANSPARENT);
		}

		NavItem item = getItem(position);

		if (item.getState() == NavItem.State.INPROGRESS) {
			// Still loading
			holder.imageView.setVisibility(View.GONE);
			holder.progressBar.setVisibility(View.VISIBLE);
		} else {
			holder.imageView.setImageBitmap(item.getPreview());
			holder.imageView.setVisibility(View.VISIBLE);
			holder.progressBar.setVisibility(View.GONE);
		}

		if (item.getState() == NavItem.State.INVALID) {
			// Fetching preview/attributes failed => item unusable
			// Fail image already set above, now just add a red border
			holder.imageView.setBackgroundResource(R.drawable.nav_item_failed);
		} else {
			holder.imageView.setBackgroundResource(R.drawable.nav_item_default);
		}

		// Listen to preview changes on this item
		holder.listenTo(item);

		return convertView;
	}

	private Context getContext() {
		return mContext;
	}

	private LayoutInflater getInflater() {
		return mInflater;
	}

	private ListView getListView() {
		return mListView;
	}


}
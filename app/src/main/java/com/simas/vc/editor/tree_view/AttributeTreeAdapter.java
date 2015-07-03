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

package com.simas.vc.editor.tree_view;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.simas.vc.R;
import com.simas.vc.attributes.AudioStream;
import com.simas.vc.attributes.Stream;
import com.simas.vc.attributes.VideoStream;
import com.simas.vc.helpers.Utils;
import com.simas.vc.nav_drawer.NavItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttributeTreeAdapter extends TreeAdapter {

	private final String TAG = getClass().getName();
	private final List<Object> mData = new ArrayList<>(2);;
	private final NavItem mItem;

	public AttributeTreeAdapter(NavItem item) {
		mItem = item;
		// Order (Audio, then Video) is important so we can distinguish the types properly!
		mData.add(item.getAttributes().getAudioStreams());
		mData.add(item.getAttributes().getVideoStreams());
	}

	@Override
	public int getChildCount(int level, Object node) {
		switch (level) {
			case 0:
				List<?> data = (List<?>) mData;
				return data.size();
			case 1:
				// Can either be List<AudioStream> or List<VideoStream>
				// However, we don't really care about the real type, we just need the size
				List<?> streams = (List<?>) node;
				return streams.size();
			case 2:
				Stream stream = (Stream) node;
				return stream.attributes.size();
			case 3:
				// No node at this level can have any children
				return -1;
			default:
				return 0;
		}
	}

	@Override
	public Object getNode(int level, int position, Object parentNode) {
		switch (level) {
			case 0:
				return mData.get(position);
			case 1:
				List<?> streams = (List<?>) parentNode;
				return streams.get(position);
			case 2:
				Stream stream = (Stream) parentNode;
				Integer keyIndex = stream.attributes.keyAt(position);
				String key = stream.getAttributePriorities().get(keyIndex);
				Object value = stream.attributes.valueAt(position);
				// Return a pair of key (String) and value (Object)
				return new Pair<>(key, value);
			default:
				return null;
		}
	}

	/**
	 * Children views of a specific node. Added in
	 * {@link #getNodeView(int, int, Object, Object, ViewGroup)}
	 */
	private Map<Object, List<View>> mNodeChildrenView = new HashMap<>();

	@Override
	View getNodeView(int level, int position, final Object node,
	                 final Object parentNode, ViewGroup parent) {
		View view;
		switch (level) {
			case 0:
				view = getInflater().inflate(R.layout.tree_group, parent, false);
				TextView root = (TextView) view;
				if (position == 0) {
					root.setText(Utils.getString(R.string.audio));
				} else if (position == 1) {
					root.setText(Utils.getString(R.string.video));
				}
				break;
			case 1:
				view = getInflater().inflate(R.layout.tree_group, parent, false);
				((TextView) view).setText(String.valueOf(position));

				// If this audio/video stream is selected, add a color filter to the view
				if (mItem.getSelectedAudioStream() == node ||
						mItem.getSelectedVideoStream() == node) {
					view.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.DARKEN);
				}

				// Create the child views array for this node (if it doesn't exist)
				List<View> childViews = mNodeChildrenView.get(parentNode);
				if (childViews == null) {
					childViews = new ArrayList<>();
					mNodeChildrenView.put(parentNode, childViews);
				}

				// Add the child view to the array
				childViews.add(view);

				// Register a listener that will select this stream in the item and deselect all
				// the other views of this parent
				view.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						// Remove the selection on other views of this parentNode
						List<View> childViews = mNodeChildrenView.get(parentNode);
						for (View view : childViews) {
							view.getBackground().clearColorFilter();
						}
						// Add the color filter on the (newly) selected view
						v.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.DARKEN);


						// Select stream in NavItem
						if (node instanceof AudioStream) {
							// Make sure it's a new stream so setSelectedAudioStream doesn't loop all
							AudioStream audioStream = (AudioStream) node;
							if (mItem.getSelectedAudioStream() != audioStream) {
								mItem.setSelectedAudioStream((AudioStream) node);
							}
						} else if (node instanceof VideoStream) {
							// Make sure it's a new stream so setSelectedAudioStream doesn't loop all
							VideoStream videoStream = (VideoStream) node;
							if (mItem.getSelectedVideoStream() != videoStream) {
								mItem.setSelectedVideoStream((VideoStream) node);
							}
						} else {
							Log.e(TAG, "Unrecognized child in level 1! Node: " + node);
						}

						return true;
					}
				});
				break;
			case 2:
				view = getInflater().inflate(R.layout.tree_child, parent, false);
				TextView key = (TextView) view.findViewById(R.id.key);
				TextView value = (TextView) view.findViewById(R.id.value);

				@SuppressWarnings("unchecked")
				Pair<String, Object> attribute = (Pair<String, Object>) node;
				key.setText(attribute.first);
				value.setText(String.valueOf(attribute.second));
				break;
			default:
				view = null;
		}

		return view;
	}

}

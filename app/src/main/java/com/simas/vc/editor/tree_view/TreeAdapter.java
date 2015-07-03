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

import android.support.v4.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.simas.vc.R;
import com.simas.vc.attributes.Stream;
import com.simas.vc.helpers.Utils;
import java.util.List;

/**
 * The most important things to note about this adapter, are these:
 * <ul>
 *  <li>Root nodes are located in the 0th level</li>
 *  <li>The parent of the root nodes is {@code null}</li>
 * </ul>
 * Levels and parent nodes are used to distinguish between the tree branches.
 * The tree can have multiple root branches, which sort of makes the TreeView a MultipleTreeView.
 */
public class TreeAdapter {

	private final String TAG = getClass().getName();
	private TreeView mTreeView;
	private LayoutInflater mInflater;
	private Object mData;

	public TreeAdapter(Object data) {
		mData = data;
	}

	final void connect(TreeView treeView) {
		mTreeView = treeView;
		mInflater = LayoutInflater.from(mTreeView.getContext());
	}

	final void disconnect() {
		mTreeView = null;
		mInflater = null;
	}

	protected final LayoutInflater getInflater() {
		return mInflater;
	}

	/**
	 * Get the children node count for the given node. Node's that can't have any child must
	 * return -1 instead of a 0.
	 * @param level   tree level in which the children reside.
	 * @param node    node whose children count must be returned. (null for root)
	 */
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

	/**
	 * Return object that represents the node
	 * @param level         level in which the node resides
	 * @param position      position of the node among other siblings
	 * @param parentNode    parent of this node (null for root nodes)
	 * @return object representing the node at the given position in the parent
	 */
	public Object getNode(int level, int position, Object parentNode) {
		switch (level) {
			case 0:
				List<?> data = (List<?>) mData;
				return data.get(position);
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
	 * Return the view for the given node
	 * @param level     tree level in which the leaf resides.
	 * @param node      node whose view must be returned
	 * @return node's view representation
	 */
	View getNodeView(int level, int position, Object node, ViewGroup parent) {
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
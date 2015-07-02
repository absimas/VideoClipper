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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import com.simas.vc.R;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.attributes.Stream;
import com.simas.vc.helpers.Utils;

import java.util.List;

/**
 * ToDo talk about how levels are used to determine everything instead of instanceof
 * ToDo talk how this can have multiple root nodes which makes it a multiple tree view
 */
public class TreeAdapter {

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
	 * Get the root node count
	 * @return the count of root nodes
	 */
	public int getRootNodeCount() {
		List<?> data = (List<?>) mData;
		return data.size();
	}

	/**
	 * Get the children node count for the given parent
	 * @param level         tree level in which the parent's children reside.
	 * @param parentNode    parent whose children count must be returned.
	 */
	public int getChildrenNodeCount(int level, int position, Object parentNode) {
		switch (level) {
			case 0:
				// Can either be List<AudioStream> or List<VideoStream>
				// However, we don't really care about the real type, we just need the size
				List<?> streams = (List<?>) parentNode;
				return streams.size();
			case 1:
				Stream stream = (Stream) parentNode;
				return stream.attributes.size();
			default:
				return 0;
		}
	}

	/**
	 * Get the object that represents the root node
	 */
	public Object getRootNode(int position) {
		List<?> data = (List<?>) mData;
		return data.get(position);
	}

	/**
	 * Return object that represents the node
	 * @param level         level in which the node resides
	 * @param position      position of the node among other siblings
	 * @param parentNode    parent of this node
	 * @return object representing the node at the given position in the parent
	 */
	public Object getChildNode(int level, int position, Object parentNode) {
		switch (level) {
			case 0:
				List<?> streams = (List<?>) parentNode;
				return streams.get(position);
			case 1:
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

	View getRootNodeView(int position, ViewGroup parent) {
		TextView root = (TextView) getInflater().inflate(R.layout.tree_group, parent, false);
		if (position == 0) {
			root.setText(Utils.getString(R.string.audio));
		} else if (position == 1) {
			root.setText(Utils.getString(R.string.video));
		} else {
			throw new IllegalArgumentException("Too many root leaves detected!");
		}

		return root;
	}

	/**
	 * Return the view for the given node
	 * @param level     tree level in which the leaf resides. (0 for root)
	 * @param leaf      leaf whose view must be returned
	 * @return leaf's view representation
	 */
	View getChildNodeView(int level, int position, Object leaf, ViewGroup parent) {
		View view;
		switch (level) {
			case 0:
				view = getInflater().inflate(R.layout.tree_group, parent, false);
				((TextView) view).setText(String.valueOf(position));
				break;
			case 1:
				view = getInflater().inflate(R.layout.tree_child, parent, false);
				TextView key = (TextView) view.findViewById(R.id.key);
				TextView value = (TextView) view.findViewById(R.id.value);

				@SuppressWarnings("unchecked")
				Pair<String, Object> attribute = (Pair<String, Object>) leaf;
				key.setText(attribute.first);
				value.setText(String.valueOf(attribute.second));
				break;
			default:
				view = null;
		}

		return view;
	}

}












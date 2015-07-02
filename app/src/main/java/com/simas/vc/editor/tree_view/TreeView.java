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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// ToDo TreeLinearLayout private inner class of this one

/**
 * This layout adds the children views as soon as an adapter is connected. It does not re-use
 * them because of the possible diversity of the types (and the lines of code :)).
 * // ToDo talk about expanding and saving the children states (ofc first implement it xD)
 */
public class TreeView extends LinearLayout {

	private static final String TAG = TreeView.class.getName();
	private TreeAdapter mAdapter;
	private LayoutInflater mInflater;

	public TreeView(Context context) {
		super(context);
		init();
	}

	public TreeView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public TreeView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public TreeView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	public void setAdapter(TreeAdapter adapter) {
		// Disconnect from the previous adapter
		if (getAdapter() != null) {
			getAdapter().disconnect();
		}
		mAdapter = adapter;
		adapter.connect(this);
		initTree();
	}

	public TreeAdapter getAdapter() {
		return mAdapter;
	}

	protected LayoutInflater getInflater() {
		return mInflater;
	}

	private void init() {
		mInflater = LayoutInflater.from(getContext());
	}

	private void initTree() {
		// Loop root nodes
		for (int i=0; i<getAdapter().getRootNodeCount(); ++i) {
			// Add root node view
			View rootView = mRoot = getAdapter().getRootNodeView(i, this);
			addView(rootView);

			// Add children recursively for this root node
			Object rootNode = getAdapter().getRootNode(i);
			int childrenCount = getAdapter().getChildrenNodeCount(0, i, rootNode);
			for (int j=0; j<childrenCount; ++j) {
				loopChildNodes(0, j, rootNode, j == childrenCount - 1, rootView);
			}
		}
	}

	private View mRoot;

	private Set<Integer> mDrawnLevels = new HashSet<>();

	/**
	 * Loop children of the node located at the given level and at the position of the parentNode
	 * @param level          level of the node, whose children will be looped
	 * @param pos            position of the node in the parent
	 * @param parentNode     parent node will be used to look up its child
	 * @param last           specifies whether this is the last the siblings
	 */
	private void loopChildNodes(int level, int pos, Object parentNode, boolean last, View parent) {
		Object node = getAdapter().getChildNode(level, pos, parentNode);
		int childrenCount = getAdapter().getChildrenNodeCount(level + 1, pos, node);

		if (!last) {
			Log.e(TAG, "add: " + level);
			mDrawnLevels.add(level);
		} else {
			mDrawnLevels.remove(level);
		}

		// Create a container for this node
		Log.e(TAG, "Container @ " + level + " draws for: " + Arrays.toString(mDrawnLevels.toArray()));
		TreeLinearLayout2 container = new TreeLinearLayout2(getContext(), level, last, mDrawnLevels);

		// Add this node's view to the container
		container.addView(getAdapter().getChildNodeView(level, pos, node, container));

		// Add the container to the TreeView
		addView(container);

		// Container's left margin needs to fill the space the parent made, to look like a child
		/**
		 * This can cause problems, if the container contains children with padding.
		 * E.g. a Button inside a TreeLinearLayout. For that reason, TreeLinearLayout has it's
		 * getPaddingLeft() overridden to return combined (child's and parent's) padding
		 */
		LayoutParams parentParams = (LayoutParams) parent.getLayoutParams();
		LayoutParams containerParams = (LayoutParams) container.getLayoutParams();
		int leftSpace = (level+1) * mRoot.getPaddingLeft() + parent.getPaddingLeft() +
				parentParams.leftMargin + containerParams.leftMargin;
		container.setPadding(leftSpace, 0, 0, 0);

		// Loop this node's children
		for (int i=0; i<childrenCount; ++i) {
			loopChildNodes(level + 1, i, node, i == childrenCount - 1, container);
		}
	}

}












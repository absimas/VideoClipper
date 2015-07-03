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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * The most important things to note about this adapter, are these:
 * <ul>
 *  <li>Root nodes are located in the 0th level</li>
 *  <li>The parent of the root nodes is {@code null}</li>
 * </ul>
 * Levels and parent nodes are used to distinguish between the tree branches.
 * The tree can have multiple root branches, which sort of makes the TreeView a MultipleTreeView.
 */
public abstract class TreeAdapter {

	private final String TAG = getClass().getName();
	private TreeView mTreeView;
	private LayoutInflater mInflater;
	private Context mContext;

	final void connect(TreeView treeView) {
		mTreeView = treeView;
		mContext = mTreeView.getContext();
		mInflater = LayoutInflater.from(getContext());
	}

	final void disconnect() {
		mTreeView = null;
		mInflater = null;
		mContext = null;
	}

	protected final LayoutInflater getInflater() {
		return mInflater;
	}

	protected final Context getContext() {
		return mContext;
	}

	/**
	 * Get the children node count for the given node. Node's that can't have any child must
	 * return -1 instead of a 0.
	 * @param level   tree level in which the children reside.
	 * @param node    node whose children count must be returned. (null for root)
	 */
	public abstract int getChildCount(int level, Object node);

	/**
	 * Return object that represents the node
	 * @param level         level in which the node resides
	 * @param position      position of the node among other siblings
	 * @param parentNode    parent of this node (null for root nodes)
	 * @return object representing the node at the given position in the parent
	 */
	public abstract Object getNode(int level, int position, Object parentNode);

	/**
	 * Return the view for the given node
	 * @param level           tree level in which the leaf resides.
	 * @param node            node whose view must be returned
	 * @param parentNode      node's parent
	 * @return node's view representation
	 */
	abstract View getNodeView(int level, int position, Object node,
	                          Object parentNode, ViewGroup parent);

}
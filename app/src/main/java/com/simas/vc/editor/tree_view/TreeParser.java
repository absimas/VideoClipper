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
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.simas.vc.R;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.attributes.Stream;
import java.util.ArrayList;
import java.util.List;

public class TreeParser {

	private final String TAG = getClass().getName();
	private final LayoutInflater mInflater;
	private final LinearLayout mContainer;
	private final TreeOverlay mOverlay;
	public final FrameLayout layout;

	public class Node {

		public List<Node> children = new ArrayList<>();
		public View view;
		public boolean expanded;
		public int parentLeftPadding;
		public int level;

		public Node(int level) {
			this.level = level;
		}

		public void setChildrenVisibility(boolean visible) {
			expanded = visible;
			if (visible) {
				// Remove/add line
				mOverlay.addLineForNode(this);

				// Show children
				for (Node child : children) {
					child.view.setVisibility(View.VISIBLE);

					// Show children's children too if there's only 1 direct child
					if (children.size() == 1) {
						child.setChildrenVisibility(true);
					}
				}
			} else {
				// Remove line
				mOverlay.removeLinesForNode(this);

				// Hide the children and their children too
				for (Node child : children) {
					child.setChildrenVisibility(false);
					child.view.setVisibility(View.GONE);
				}
			}
		}

	}

	public TreeParser(@NonNull Context context, @NonNull FileAttributes attributes) {
		mInflater = LayoutInflater.from(context);
		layout = (FrameLayout) mInflater.inflate(R.layout.stream_tree, null);
		mOverlay = (TreeOverlay) layout.findViewById(R.id.overlays);
		mContainer = (LinearLayout) layout.findViewById(R.id.field_container);

		createStreamList(attributes.getAudioStreams(), "Audio");
		createStreamList(attributes.getVideoStreams(), "Video");
	}

	private void createStreamList(List<? extends Stream> streams, String rootName) {
		final Node root = new Node(0);

		// Stream button
		// View
		TextView rootView = (TextView) mInflater.inflate(R.layout.stream_button, mContainer, false);
		root.view = rootView;
		mContainer.addView(rootView);

		// Values
		rootView.setText(rootName);

		// Expansion listener
		rootView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// Set expanded flag
				root.expanded = !root.expanded;

				// Update children visibility
				root.setChildrenVisibility(root.expanded);
			}
		});

		// Groups (streams)
		int rootLeftPadding = rootView.getPaddingLeft();
		int groupCount = streams.size();
		for (int i=0; i<groupCount; ++i) {
			final Node group = new Node(1);
			root.children.add(group);
			Stream stream = streams.get(i);

			// View
			TreeLinearLayout groupView = (TreeLinearLayout) mInflater
					.inflate(R.layout.stream_group, mContainer, false);
			group.parentLeftPadding = rootLeftPadding;
			groupView.setNode(group);
			group.view = groupView;
			mContainer.addView(groupView);

			// Values
			TextView groupText = (TextView) groupView.findViewById(R.id.group_title);
			groupText.setText(String.format("#%d", i));

			// Expansion listener
			groupText.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// Set expanded flag
					group.expanded = !group.expanded;

					// Update children visibility
					group.setChildrenVisibility(group.expanded);
				}
			});

			// Children (fields)
			int groupLeftPadding = groupText.getPaddingLeft() + groupView.getPaddingLeft();
			int childCount = stream.fields.size();
			for (int j=0; j<childCount; ++j) {
				final Node child = new Node(2);
				group.children.add(child);

				// View
				TreeLinearLayout childView = (TreeLinearLayout) mInflater
						.inflate(R.layout.stream_child, mContainer, false);
				child.parentLeftPadding = groupLeftPadding;
				childView.setNode(child);
				child.view = childView;
				mContainer.addView(childView);

				// Values
				TextView keyView = (TextView) childView.findViewById(R.id.key);
				TextView valueView = (TextView) childView.findViewById(R.id.value);

				Integer keyIndex = stream.fields.keyAt(j);
				String key = stream.getKeyPriorities().get(keyIndex);
				Object value = stream.fields.valueAt(j);

				keyView.setText(key);
				valueView.setText(String.valueOf(value));
			}
		}
	}

}

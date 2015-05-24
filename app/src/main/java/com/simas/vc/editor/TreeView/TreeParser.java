package com.simas.vc.editor.TreeView;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.simas.vc.R;
import com.simas.vc.attributes.AudioStream;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.attributes.Stream;
import com.simas.vc.attributes.VideoStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 May 24.
 */

public class TreeParser {

	private final String TAG = getClass().getName();
	private final Context mContext;
	private final LayoutInflater mInflater;
	private final List<AudioStream> mAudioStreams;
	private final List<VideoStream> mVideoStreams;
	private final LinearLayout mContainer;
	private final TreeOverlay mOverlay;
	public final FrameLayout layout;

	public class Node {

		public List<Node> children = new ArrayList<>();
		public View view;
		public boolean expanded;
		public int parentLeftPadding;
		public int level;

		public void setChildrenVisibility(boolean visible) {
			expanded = visible;
			if (visible) {
				for (Node child : children) {
					child.view.setVisibility(View.VISIBLE);
				}
			} else {
				// When hiding also hide all the children's children
				for (Node child : children) {
					child.setChildrenVisibility(false);
					child.view.setVisibility(View.GONE);
				}
			}
		}

		public Node(int level) {
			this.level = level;
		}

	}

	public TreeParser(Context context, @NonNull FileAttributes attributes) {
		mContext = context;
		mInflater = LayoutInflater.from(context);
		mAudioStreams = attributes.getAudioStreams();
		mVideoStreams = attributes.getVideoStreams();

		layout = (FrameLayout) mInflater.inflate(R.layout.stream_tree, null);
		mOverlay = (TreeOverlay) layout.findViewById(R.id.overlays);
		mContainer = (LinearLayout) layout.findViewById(R.id.field_container);

		createStreamList(mAudioStreams, "Audio");
		createStreamList(mVideoStreams, "Video");
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

				// Remove/add line
				if (root.expanded) {
					mOverlay.addLineForNode(root);
				} else {
					mOverlay.removeLinesForNode(root);
				}

				// Update children visibility
				root.setChildrenVisibility(root.expanded);
			}
		});

		// Groups (streams)
		int parentLeftPadding = rootView.getPaddingLeft();
		int groupCount = streams.size();
		for (int i=0; i<groupCount; ++i) {
			final Node group = new Node(1);
			root.children.add(group);
			Stream stream = streams.get(i);

			// View
			TreeLinearLayout groupView = (TreeLinearLayout) mInflater
					.inflate(R.layout.stream_group, mContainer, false);
			group.parentLeftPadding = parentLeftPadding;
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

					// Remove/add line
					if (group.expanded) {
						mOverlay.addLineForNode(group);
					} else {
						mOverlay.removeLinesForNode(group);
					}

					// Update children visibility
					group.setChildrenVisibility(group.expanded);
				}
			});

			// Children (fields)
			parentLeftPadding = groupText.getPaddingLeft() + groupView.getPaddingLeft();
			int childCount = stream.fields.size();
			for (int j=0; j<childCount; ++j) {
				final Node child = new Node(2);
				group.children.add(child);

				// View
				TreeLinearLayout childView = (TreeLinearLayout) mInflater
						.inflate(R.layout.stream_child, mContainer, false);
				child.parentLeftPadding = parentLeftPadding;
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

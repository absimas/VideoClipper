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
package com.simas.vc.file_chooser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.simas.vc.R;
import com.simas.vc.VC;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.helpers.Utils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// ToDo save scroll position when going deeper
// ToDo on-click-outside doesn't dismiss on Galaxy S2

/**
 * Custom dialog for choosing a file from the device's drive.
 */
public class FileChooser extends DialogFragment
		implements AdapterView.OnItemClickListener, DialogInterface.OnKeyListener {

	public static final String TAG = "FileChooser";
	public static final String ARG_FROM_TOOLBAR = "from_toolbar";
	private LayoutInflater mInflater;
	private FileChooserAdapter mAdapter;
	private OnFileChosenListener mChoiceListener;
	private static File sCurrentPath = new File("/sdcard/Movies"); // ToDo do not hardcode path
	private static FileChooser sInstance;

	public static FileChooser getInstance() {
		if (sInstance == null) {
			sInstance = new FileChooser();
		}

		return sInstance;
	}

	public FileChooser() {}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		// (Re)init the inflater
		mInflater = LayoutInflater.from(activity);

		// If previous directory was deleted/doesn't exist, go back to the root
		if (!sCurrentPath.exists()) sCurrentPath = new File("/");
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		mAdapter = new FileChooserAdapter();
		AlertDialog dialog = new AlertDialog.Builder(getActivity())
				.setAdapter(mAdapter, null)
				.create();

		/* Configure the dialog */
		// Back listener
		dialog.setOnKeyListener(this);

		// Item click listener
		dialog.getListView().setOnItemClickListener(this);

		// Add header (up navigation) // Do it with the adapter removed, so lower APIs don't crash!
		dialog.getListView().setAdapter(null);
		View header = createHeader(dialog.getListView());
		dialog.getListView().addHeaderView(header);
		dialog.getListView().setAdapter(mAdapter);

		// Show sub-files
		mAdapter.setFiles(getSubFiles(sCurrentPath));

		return dialog;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);

		// Make the window always appear on the top of the screen
		try {
			WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
			lp.copyFrom(getDialog().getWindow().getAttributes());
			lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
			getDialog().getWindow().setAttributes(lp);
		} catch (NullPointerException e) {
			Log.e(TAG, "Failed to set FileChooser window gravity!", e);
		}

		return view;
	}

	private View createHeader(ViewGroup parent) {
		View header = mInflater.inflate(R.layout.file_chooser_header, parent, false);
		header.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				navigateUp();
			}
		});
		header.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				dismiss();
				return true;
			}
		});
		return header;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		parent.setSelected(false);
		view.setSelected(false);
		if (position != 0) {
			File file = mAdapter.getItem(position-1);

			if (file.isDirectory()) {
				navigateTo(file);
			} else {
				getDialog().dismiss();
				if (mChoiceListener != null) {
					mChoiceListener.onFileChosen(file);
				}
			}
		}
	}

	/**
	 * Will go up by a level if possible, otherwise will dismiss the dialog.
	 */
	private void navigateUp() {
		File parent = sCurrentPath.getParentFile();
		if (parent != null) {
			navigateTo(parent);
		} else {
			dismiss();
		}
	}

	private void navigateTo(File directory) {
		// Set the current path
		sCurrentPath = directory;
		// Add all it's sub-files
		mAdapter.setFiles(getSubFiles(directory));
	}

	private List<File> getSubFiles(File root) {
		List<File> validSubFiles = new ArrayList<>();

		File[] files = root.listFiles();
		if (files == null || files.length == 0) return validSubFiles;

		for (File file : files) {
			if (file.isDirectory()) {
				// Add all dirs
				validSubFiles.add(file);
			} else {
				// Add only files with specific extensions
				NavItem.Type type = NavItem.determineExtensionType(file);
				if (type == NavItem.Type.VIDEO || type == NavItem.Type.PICTURE) {
					validSubFiles.add(file);
				} // ToDo audio extension
			}
		}

		return validSubFiles;
	}

	@Override
	public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.isLongPress()) {
			dismiss();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
			navigateUp();
			return true;
		} else {
			return false;
		}
	}

	private static final Drawable DIRECTORY_DRAWABLE, VIDEO_DRAWABLE, PICTURE_DRAWABLE;

	static {
		// Load drawables
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			DIRECTORY_DRAWABLE = VC.getAppResources().getDrawable(R.drawable.ic_menu_archive, null);
			VIDEO_DRAWABLE = VC.getAppResources().getDrawable(R.drawable.ic_media_video_poster, null);
			PICTURE_DRAWABLE = VC.getAppResources().getDrawable(R.drawable.ic_menu_gallery, null);
		} else {
			DIRECTORY_DRAWABLE = VC.getAppResources().getDrawable(R.drawable.ic_menu_archive);
			VIDEO_DRAWABLE = VC.getAppResources().getDrawable(R.drawable.ic_media_video_poster);
			PICTURE_DRAWABLE = VC.getAppResources().getDrawable(R.drawable.ic_menu_gallery);
		}

		int pixels = (int) Utils.dpToPx(30);
		if (DIRECTORY_DRAWABLE != null && VIDEO_DRAWABLE != null && PICTURE_DRAWABLE != null) {
			DIRECTORY_DRAWABLE.setBounds(0, 0, pixels, pixels);
			VIDEO_DRAWABLE.setBounds(0, 0, pixels, pixels);
			PICTURE_DRAWABLE.setBounds(0, 0, pixels, pixels);
		}
	}

	private class FileChooserAdapter extends BaseAdapter {

		private List<File> mFiles;

		private class ViewHolder {
			TextView textView;
		}

		public void setFiles(List<File> files) {
			mFiles = files;
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return (mFiles == null) ? 0 : mFiles.size();
		}

		@Override
		public File getItem(int position) {
			return mFiles.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				// Inflate the layout
				convertView = mInflater.inflate(R.layout.file_chooser_item, parent, false);

				// Save holder for later re-use
				holder = new ViewHolder();
				holder.textView = (TextView) convertView.findViewById(R.id.file_item);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			if (getItem(position).isDirectory()) {
				holder.textView.setCompoundDrawables(DIRECTORY_DRAWABLE, null, null, null);
			} else {
				// Drawable based on the extension
				switch (NavItem.determineExtensionType(getItem(position))) {
					case VIDEO:
						holder.textView.setCompoundDrawables(VIDEO_DRAWABLE, null, null, null);
						break;
					case PICTURE:
						holder.textView.setCompoundDrawables(PICTURE_DRAWABLE, null, null, null);
						break;
					case AUDIO:
						// ToDo audio drawable
						break;
					default:
						throw new IllegalArgumentException("Unrecognized extension!");
				}
			}

			holder.textView.setText(getItem(position).getName());

			return convertView;
		}
	}

	public void setOnFileChosenListener(OnFileChosenListener choiceListener) {
		mChoiceListener = choiceListener;
	}

	public interface OnFileChosenListener {
		void onFileChosen(File file);
	}

}

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

import com.simas.vc.VC;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.wvc.R;
import com.simas.vc.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 09.
 */

// ToDo save scroll position when going deeper
// ToDo on-click-outside doesn't dismiss on Galaxy S2

public class FileChooser extends DialogFragment
		implements AdapterView.OnItemClickListener, DialogInterface.OnKeyListener {

	public static final String TAG = "FileChooser";
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
				.setTitle("File chooser")
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
		final String up = VC.getAppContext().getString(R.string.file_chooser_up);
		View header = mInflater.inflate(R.layout.file_chooser_item, parent, false);
		TextView tv = (TextView) header.findViewById(R.id.file_item);
		tv.setCompoundDrawables(mAdapter.mDirectoryDrawable, null, null, null);
		tv.setText(up);

		return header;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		parent.setSelected(false);
		view.setSelected(false);
		if (position == 0) {
			// Header (up nav)
			navigateUp();
		} else {
			File file = mAdapter.getItem(position-1);

			if (file.isDirectory()) {
				navigateTo(file);
			} else {
				getDialog().dismiss();
				if (mChoiceListener != null) {
					mChoiceListener.onChosen(file);
				}
			}
		}
	}

	/**
	 * Will go up by a level if possible, otherwise will do nothing
	 * @return true if navigation up was successful
	 */
	private boolean navigateUp() {
		File parent = sCurrentPath.getParentFile();
		if (parent != null) {
			navigateTo(parent);
			return true;
		} else {
			return false;
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
		// Override the back key only if not at the root yet
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
			return navigateUp();
		} else {
			return false;
		}
	}

	private class FileChooserAdapter extends BaseAdapter {

		private List<File> mFiles;
		private Drawable mDirectoryDrawable;
		private Drawable mVideoDrawable;
		private Drawable mPictureDrawable;

		private class ViewHolder {
			TextView textView;
		}

		public FileChooserAdapter() {
			// ToDo do not hardcode (30)
			// ToDo audio drawable
			// ToDo init drawables elsewhere?  // mby not
			int pixels = (int) Utils.dpToPx(30);
			mDirectoryDrawable = VC.getAppResources().getDrawable(R.drawable.ic_menu_archive);
			mDirectoryDrawable.setBounds(0, 0, pixels, pixels);
			mVideoDrawable = VC.getAppResources().getDrawable(R.drawable.ic_media_video_poster);
			mVideoDrawable.setBounds(0, 0, pixels, pixels);
			mPictureDrawable = VC.getAppResources().getDrawable(R.drawable.ic_menu_gallery);
			mPictureDrawable.setBounds(0, 0, pixels, pixels);
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
				holder.textView.setCompoundDrawables(mDirectoryDrawable, null, null, null);
			} else {
				// Drawable based on the extension
				switch (NavItem.determineExtensionType(getItem(position))) {
					case VIDEO:
						holder.textView.setCompoundDrawables(mVideoDrawable, null, null, null);
						break;
					case PICTURE:
						holder.textView.setCompoundDrawables(mPictureDrawable, null, null, null);
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
		void onChosen(File file);
	}

}

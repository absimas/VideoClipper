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
package com.simas.vc.nav_drawer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;
import com.simas.vc.VCException;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.background_tasks.VarRunnable;
import com.simas.vc.R;
import com.simas.vc.Utils;
import com.simas.vc.VC;
import com.simas.vc.attributes.Stream;
import com.simas.vc.background_tasks.Ffmpeg;
import com.simas.vc.background_tasks.Ffprobe;
import com.simas.vc.editor.EditorFragment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

// ToDo instead of re-parsing duplicate attrs perhaps they should be copied (just like the preview)

public class NavItem implements Parcelable, Cloneable {

	private final String TAG = getClass().getName();
	/**
	 * Item preview bitmap.
	 */
	private Bitmap mPreview;
	/**
	 * Pointer to the item file
	 */
	private final File mFile;
	/**
	 * File type (enum{@code Type})
	 */
	private final Type mType;
	/**
	 * Item state enum{@code State})
	 */
	private State mState = State.INPROGRESS;
	/**
	 * Attributes fetched with FFprobe
	 */
	private FileAttributes mAttributes;
	/**
	 * Item's parent, i.e. the NavAdapter that contains it
	 */
	private NavAdapter mParent;

	/**
	 * Thread-safe list holding all the update listeners. These can be called from a <b>non-UI
	 * thread</b>, so make sure to check that in your runnables
	 */
	private final CopyOnWriteArraySet<OnUpdatedListener> mUpdateListeners =
			new CopyOnWriteArraySet<>();

	private static Bitmap FAIL_IMAGE;

	static {
		FAIL_IMAGE = BitmapFactory.decodeResource(VC.getAppResources(), R.drawable.fail);
	}

	// ToDo extension list documentation
	private static final List<String> VALID_AUDIO_EXTENSIONS = new ArrayList<String>() { {
//		add("mp3");
	}};

	private static final List<String> VALID_VIDEO_EXTENSIONS = new ArrayList<String>() { {
		add("mp4");
		add("mkv");
		add("avi");
	}};

	private static final List<String> VALID_PICTURE_EXTENSIONS = new ArrayList<String>() { {
//		add("jpg");
//		add("jpeg");
//		add("png");
//		add("bmp");
	}};

	/**
	 * Available types for each {@code NavItem}
	 */
	public enum Type {
		AUDIO, VIDEO, PICTURE, UNSUPPORTED
	}

	/**
	 * Available types for each {@code NavItem}'s preview
	 */
	public enum State {
		VALID, INVALID, INPROGRESS
	}

	public enum ItemAttribute {
		STATE, PREVIEW, OTHER
	}

	/**
	 * Creates a NavItem and its preview
	 * @param file       file to which NavItem is pointing to
	 */
	public NavItem(NavAdapter parent, File file) {
		setParent(parent);
		mFile = file;
		mType = determineExtensionType(file);
		if (mType == Type.UNSUPPORTED) {
			setState(State.INVALID);
		} else {
			parseItem();
		}
	}

	/**
	 * {@code mFile} must be set before this is called.
	 */
	private void parseItem() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				// Attributes
				FileAttributes attributes = null;
				try {
					attributes = Ffprobe.parseAttributes(getFile());
				} catch (VCException e) {
					e.printStackTrace();
					// ToDo display to user
				}
				if (attributes == null) {
					setState(State.INVALID);
					return;
				}
				setAttributes(attributes);

				// Preview
				Bitmap preview = parsePreview();
				if (preview == null) {
					setState(State.INVALID);
					return;
				}
				setPreview(preview);
				setState(State.VALID);
			}
		}).start();
	}

	/**
	 * Blocking method that fetches and scales the preview image.<br/>
	 * <b>Note:</b> if this item is a duplicate of another one (file paths match), then existing
	 * preview is used.
	 */
	private Bitmap parsePreview() {
		// Loop existing items
		for (NavItem item : mParent.getItems()) {
			// Check if the other item has a preview and if the files match
			if (item.getPreview() != null && item.getFile().compareTo(getFile()) == 0) {
				/*// Deep copy the preview and use it for this item
				return item.getPreview().copy(item.getPreview().getConfig(), true);*/
				// Use the already parsed image
				return item.getPreview();
			}
		}

		Bitmap preview = Ffmpeg.createPreview(getFile().getPath());
		if (preview == null) {
			Log.e(TAG, "Couldn't parse the preview!");
			return null;
		} else if (NavDrawerFragment.sPreviewSize == 0) {
			Log.e(TAG, "Preview size is unset! Using full size.");
			return preview;
		}

		// Scale preview
		Bitmap scaledPreview = scaleDown(preview, Utils.dpToPx(NavDrawerFragment.sPreviewSize));
		// Recycle the un-scaled version
		preview.recycle();

		return scaledPreview;
	}

	public static Bitmap scaleDown(Bitmap realImage, float maxSizePx) {
		float ratio = Math.min(maxSizePx / realImage.getWidth(),
				maxSizePx / realImage.getHeight());
		int width = Math.round(ratio * realImage.getWidth());
		int height = Math.round(ratio * realImage.getHeight());

		return Bitmap.createScaledBitmap(realImage, width, height, false);
	}

	/**
	 * Based on the extension of a given file, will determine the type ({@code AUDIO},
	 * {@code VIDEO} or {@code PICTURE}. This will be based on {@code VALID_AUDIO_EXTENSIONS},
	 * {@code VALID_VIDEO_EXTENSIONS} and {@code VALID_PICUTRE_EXTENSIONS}. If the extension is
	 * unrecognized will return null.
	 * @param file    file whose extension will be checked
	 * @return file the {@code Type} or {@code null} if the extension is not recognized
	 */
	public static Type determineExtensionType(File file) {
		// Save the type based on extension
		String[] split = file.getName().split("\\."); // split fails
		if (split.length > 1) {
			String extension = split[split.length - 1].toLowerCase();
			if (VALID_VIDEO_EXTENSIONS.contains(extension)) {
				return Type.VIDEO;
			} else if (VALID_PICTURE_EXTENSIONS.contains(extension)) {
				return Type.PICTURE;
			}
		}

		return Type.UNSUPPORTED;
	}

	/* Setters/Getters */
	public void setParent(NavAdapter newParent) {
		final NavAdapter oldParent = mParent;
		mParent = newParent;

		// Announce to listeners
		for (OnUpdatedListener listener : mUpdateListeners) {
			listener.onUpdated(ItemAttribute.OTHER, oldParent, newParent);
		}
	}

	public void setPreview(Bitmap newPreview) {
		final Bitmap oldPreview = mPreview;
		mPreview = newPreview;

		// Announce to listeners
		for (OnUpdatedListener listener : mUpdateListeners) {
			listener.onUpdated(ItemAttribute.PREVIEW, oldPreview, newPreview);
		}
	}

	private void setAttributes(@NonNull FileAttributes newAttributes) {
		final FileAttributes oldAttributes = mAttributes;
		mAttributes = newAttributes;

		// Announce to listeners
		for (OnUpdatedListener listener : mUpdateListeners) {
			listener.onUpdated(ItemAttribute.OTHER, oldAttributes, newAttributes);
		}
	}

	private void setState(State newState) {
		if (newState == State.INVALID) {
			setPreview(FAIL_IMAGE);
		}

		final State oldState = mState;
		mState = newState;

		for (OnUpdatedListener mUpdateListener : mUpdateListeners) {
			mUpdateListener.onUpdated(ItemAttribute.STATE, oldState, newState);
		}
	}

	public File getFile() {
		return mFile;
	}

	public Type getType() {
		return mType;
	}

	public NavAdapter getParent() {
		return mParent;
	}

	public Bitmap getPreview() {
		return mPreview;
	}

	public FileAttributes getAttributes() {
		return mAttributes;
	}

	public State getState() {
		return mState;
	}

	/* Parcelable */
	public void writeToParcel(Parcel out, int flags) {
		Log.e(TAG, "Write to parcel: " + toString());
		out.writeString(mFile.getAbsolutePath());
		out.writeSerializable(mType);
		out.writeParcelable(mAttributes, flags);
		out.writeSerializable(mState);
	}

	public static final Parcelable.Creator<NavItem> CREATOR = new Parcelable.Creator<NavItem>() {
		public NavItem createFromParcel(Parcel in) {
			return new NavItem(in);
		}

		public NavItem[] newArray(int size) {
			return new NavItem[size];
		}
	};

	private NavItem(Parcel in) {
		mFile = new File(in.readString());
		mType = (Type) in.readSerializable();
		mAttributes = in.readParcelable(Stream.class.getClassLoader());
		mState = (State) in.readSerializable();
		// Parse preview
		mPreview = parsePreview();
	}

	public int describeContents() {
		return 0;
	}

	/* Cloneable */
	public NavItem(NavItem otherItem) {
		mParent = otherItem.mParent;
		mType = otherItem.mType;
		mAttributes = otherItem.mAttributes;
		mFile = otherItem.mFile;
		mState = otherItem.mState;
		// Use the existing preview
		mPreview = otherItem.mPreview;
	}

	/* Update listener */
	public interface OnUpdatedListener {
		void onUpdated(final ItemAttribute attribute, final Object oldValue, final Object newValue);
	}

	public void registerUpdateListener(OnUpdatedListener listener) {
		mUpdateListeners.add(listener);
	}

	public void unregisterUpdateListener(OnUpdatedListener listener) {
		mUpdateListeners.remove(listener);
	}

	@Override
	public String toString() {
		return "File: " + getFile().getPath() + "\n" + "Preview: " + getPreview() + "\n" +
				"Type: " + getType() + "\n" + "Attributes: " + mAttributes + "\n" +
				"State: " + mState.name() + "\n" + "Parent: " + getParent() + "\n";
	}

}
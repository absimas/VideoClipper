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

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;

import com.simas.vc.MainActivity;
import com.simas.vc.VCException;
import com.simas.vc.attributes.AudioStream;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.R;
import com.simas.vc.attributes.VideoStream;
import com.simas.vc.helpers.Utils;
import com.simas.vc.VC;
import com.simas.vc.attributes.Stream;
import com.simas.vc.background_tasks.FFmpeg;
import com.simas.vc.background_tasks.FFprobe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

// ToDo NavItem should probly be renamed. No longer only for Navigation.
// ToDo instead of re-parsing duplicate attrs perhaps they should be copied (just like the preview)

/**
 * By default first audio and video streams are selected. Later they can fetched with {@link
 * #getSelectedAudioStream()} and {@link #getSelectedVideoStream()} and they can be modified with
 * {@link #setSelectedAudioStream(AudioStream)} and {@link #setSelectedVideoStream(VideoStream)}
 */
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
	private AudioStream mSelectedAudioStream;
	private VideoStream mSelectedVideoStream;
	/**
	 * Exception message that caused this item to enter an {@link State#INVALID} state.
	 */
	volatile private String mError;

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

	private static final List<String> VALID_VIDEO_EXTENSIONS = new ArrayList<String>() { {
		add("mp4");
		add("mkv");
		add("avi");
		add("divx");
	}};

	private static final List<String> VALID_AUDIO_EXTENSIONS = new ArrayList<String>() { {
//		add("mp3");
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
	public NavItem(File file) {
		mFile = file;
		mType = determineExtensionType(file);
		if (mType == Type.UNSUPPORTED) {
			setState(State.INVALID);
		} else {
			parseItem();
		}
	}

	public void setSelectedAudioStream(AudioStream selectedStream) {
		// Look for the given audio stream
		for (AudioStream stream : getAttributes().getAudioStreams()) {
			if (stream == selectedStream) {
				// If found, select it and quit
				mSelectedAudioStream = stream;
				return;
			}
		}
		// If wasn't found throw
		throw new IllegalStateException("No streams have been selected! Are you sure you're " +
				"passing a stream that belongs to the attributes of this item?");
	}

	public void setSelectedVideoStream(VideoStream selectedStream) {
		// Look for the given audio stream
		for (VideoStream stream : getAttributes().getVideoStreams()) {
			if (stream == selectedStream) {
				// If found, select it and quit
				mSelectedVideoStream = stream;
				return;
			}
		}
		// If wasn't found throw
		throw new IllegalStateException("No streams have been selected! Are you sure you're " +
				"passing a stream that belongs to the attributes of this item?");
	}

	public AudioStream getSelectedAudioStream() {
		return mSelectedAudioStream;
	}

	public VideoStream getSelectedVideoStream() {
		return mSelectedVideoStream;
	}

	/**
	 * @return -1 if there is no selected audio stream.
	 * @throws IllegalStateException if the selected stream cannot be found in file
	 */
	public int getSelectedAudioStreamIndex() {
		if (getSelectedAudioStream() != null) {
			int index = getAttributes().getStreams().indexOf(getSelectedAudioStream());

			if (index < 0){
				throw new IllegalStateException("The selected audio stream wasn't found!");
			}

			return index;
		} else {
			return -1;
		}
	}

	/**
	 * @return -1 if there is no selected video stream.
	 * @throws IllegalStateException if the selected stream cannot be found in file
	 */
	public int getSelectedVideoStreamIndex() {
		if (getSelectedVideoStream() != null) {
			int index = getAttributes().getStreams().indexOf(getSelectedVideoStream());

			if (index < 0){
				throw new IllegalStateException("The selected video stream wasn't found!");
			}

			return index;
		} else {
			return -1;
		}
	}

	/**
	 * Fetches the error message that occurred and made the item enter an {@link State#INVALID}
	 * state. Only a single call is permitted, subsequent calls will return null.
	 * @return null if no such exception had occurred or if the exception was already fetched
	 */
	public String getError() {
		return mError;
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
					attributes = FFprobe.parseAttributes(getFile());
				} catch (final VCException e) {
					Log.e(TAG, "Attribute parse error:", e);
					mError = e.getMessage();
				}
				if (attributes == null) {
					setState(State.INVALID);
					return;
				}
				setAttributes(attributes);
				// By default, select first audio and first video streams (if they exist)
				if (attributes.getAudioStreams().size() > 0) {
					setSelectedAudioStream(attributes.getAudioStreams().get(0));
				}
				if (attributes.getVideoStreams().size() > 0) {
					setSelectedVideoStream(attributes.getVideoStreams().get(0));
				}

				// Preview
				final Bitmap preview = parsePreview();
				if (preview == null) {
					setState(State.INVALID);
				} else {
					setState(State.VALID);
					// Update the preview on the UI thread
					Utils.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							setPreview(preview);
						}
					});
				}
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
		for (NavItem item : MainActivity.sItems) {
			// Check if the other item has a preview and if the files match
			if (item.getPreview() != null && item.getFile().compareTo(getFile()) == 0) {
				/*// Deep copy the preview and use it for this item
				return item.getPreview().copy(item.getPreview().getConfig(), true);*/
				// Use the already parsed image
				return item.getPreview();
			}
		}

		Bitmap preview = FFmpeg.createPreview(getFile().getPath());
		if (preview == null) {
			Log.e(TAG, "Couldn't parse the preview!");
			return null;
		} else if (MainActivity.sPreviewSize == 0) {
			Log.e(TAG, "Preview size is unset! Using full size.");
			return preview;
		}

		// Scale preview
		Bitmap scaledPreview = scaleDown(preview, Utils.dpToPx(MainActivity.sPreviewSize));
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
		out.writeString(mFile.getAbsolutePath());
		out.writeSerializable(mType);
		out.writeParcelable(mAttributes, flags);
		out.writeSerializable(mState);
		out.writeParcelable(mSelectedAudioStream, flags);
		out.writeParcelable(mSelectedVideoStream, flags);
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
		mSelectedAudioStream = in.readParcelable(AudioStream.class.getClassLoader());
		mSelectedVideoStream = in.readParcelable(VideoStream.class.getClassLoader());
		// Re-Parse the preview
		mPreview = parsePreview();
	}

	public int describeContents() {
		return 0;
	}

	/* Cloneable */
	public NavItem(NavItem otherItem) {
		mType = otherItem.mType;
		mAttributes = otherItem.mAttributes;
		mFile = otherItem.mFile;
		mState = otherItem.mState;
		mSelectedAudioStream = otherItem.mSelectedAudioStream;
		mSelectedVideoStream = otherItem.mSelectedVideoStream;
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
				"State: " + mState.name();
	}

}
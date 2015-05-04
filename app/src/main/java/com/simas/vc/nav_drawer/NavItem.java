package com.simas.vc.nav_drawer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.simas.vc.background_tasks.VarRunnable;
import com.simas.wvc.R;
import com.simas.vc.Utils;
import com.simas.vc.VC;
import com.simas.vc.attributes.Attributes;
import com.simas.vc.background_tasks.Ffmpeg;
import com.simas.vc.background_tasks.Ffprobe;
import com.simas.vc.editor.EditorFragment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class NavItem implements Parcelable, Cloneable {

	private final String TAG = getClass().getName();
	/**
	 * Item preview bitmap. // ToDo mPreview shouldn't be parcelled but instead re-created after un-parcelling
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
	private Attributes mAttributes;
	/**
	 * Item's parent, i.e. the NavAdapter that contains it
	 */
	private NavAdapter mParent;

	/**
	 * Thread-safe list holding all the update listeners
	 */
	private final CopyOnWriteArraySet<OnUpdatedListener> mUpdateListeners =
			new CopyOnWriteArraySet<>();

	private static Bitmap FAIL_IMAGE;

	static {
		FAIL_IMAGE = BitmapFactory.decodeResource(VC.getAppResources(), R.drawable.fail);
	}

	// ToDo extension list documentation
	private static final List<String> VALID_AUDIO_EXTENSIONS = new ArrayList<String>() { {
		add("mp3");
	}};

	private static final List<String> VALID_VIDEO_EXTENSIONS = new ArrayList<String>() { {
		add("mp4");
		add("mkv");
		add("avi");
	}};

	private static final List<String> VALID_PICTURE_EXTENSIONS = new ArrayList<String>() { {
		add("jpg");
		add("jpeg");
		add("png");
		add("bmp");
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
	 * Must be called after setting {@code mFile}
	 */
	private void parseItem() {
		try {
			parseAttributes();
			// The above method will invoke a preview parser, which will invoke the state validator
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
			setState(State.INVALID);
		}
	}

	private void parseAttributes() throws IOException, InterruptedException {
		Ffprobe.getFileAttributes(getFile(), new VarRunnable() {
			@Override
			public void run() {
				if (getVariable() == null) {
					setState(State.INVALID);
				} else {
					setAttributes((Attributes) getVariable());

					// Now fetch the preview in a separate thread and validate the item
					new Thread(new Runnable() {
						@Override
						public void run() {
							Bitmap preview = parsePreview();
							if (preview == null) {
								setState(State.INVALID);
							} else {
								setPreview(preview);
								setState(State.VALID);
							}
						}
					}).start();
				}
			}
		});
	}

	/**
	 * Blocking method that fetches and scales the preview image
	 */
	private Bitmap parsePreview() {
		Bitmap preview = Ffmpeg.createPreview(getFile().getPath());
		if (preview == null || EditorFragment.sPreviewSize == 0) {
			return null;
		}
		// Scale preview
		Bitmap scaledPreview = scaleDown(preview, Utils.dpToPx(EditorFragment.sPreviewSize));
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

	private void setAttributes(Attributes newAttributes) {
		final Attributes oldAttributes = mAttributes;
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

	public Attributes getAttributes() {
		return mAttributes;
	}

	public State getState() {
		return mState;
	}

	/* Parcelable */
	public void writeToParcel(Parcel out, int flags) {
		Log.e(TAG, "Write to parcel: " + toString());
		out.writeParcelable(mPreview, flags);
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
		mPreview = in.readParcelable(Bitmap.class.getClassLoader());
		mFile = new File(in.readString());
		mType = (Type) in.readSerializable();
		mAttributes = in.readParcelable(Attributes.class.getClassLoader());
		mState = (State) in.readSerializable();
	}

	public int describeContents() {
		return 0;
	}

	/* Cloneable */
	public NavItem(NavItem otherItem) {
		mParent = otherItem.mParent;
		mType = otherItem.mType;
		mAttributes = otherItem.mAttributes;
		mFile = otherItem.getFile();
		mState = otherItem.mState;
		// Deep copy bitmap
		mPreview = otherItem.mPreview.copy(otherItem.mPreview.getConfig(), true);
	}


	/* Update listener */
	public interface OnUpdatedListener {
		void onUpdated(ItemAttribute attribute, Object oldValue, Object newValue);
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
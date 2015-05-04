package com.simas.vc.attributes;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

public class VideoAttributes extends Attributes {

	private Integer mWidth, mHeight;
	private String mAspectRatio;

	public VideoAttributes() {
		setType(Type.VIDEO);
	}

	public Integer getWidth() {
		return mWidth;
	}

	public Integer getHeight() {
		return mHeight;
	}

	public String getAspectRatio() {
		return mAspectRatio;
	}

	public void setWidth(Integer width) {
		mWidth = width;
	}

	public void setHeight(Integer height) {
		mHeight = height;
	}

	public void setAspectRatio(String aspectRation) {
		mAspectRatio = aspectRation;
	}

	public void setSize(Integer width, Integer height) {
		setWidth(width);
		setHeight(height);
	}

	@Override
	public String toString() {
		return String.format("%s\nVideoAttributes Width: %d, Height: %d, AspectRation: %s",
				super.toString(), getWidth(), getHeight(), getAspectRatio());
	}

	/* Parcelable */

	public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		out.writeValue(mWidth);
		out.writeValue(mHeight);
		out.writeValue(mAspectRatio);
	}

	public static final Parcelable.Creator<VideoAttributes> CREATOR
			= new Parcelable.Creator<VideoAttributes>() {
		public VideoAttributes createFromParcel(Parcel in) {
			return new VideoAttributes(in);
		}

		public VideoAttributes[] newArray(int size) {
			return new VideoAttributes[size];
		}
	};

	private VideoAttributes(Parcel in) {
		super(in);
		mWidth = (Integer) in.readValue(Integer.class.getClassLoader());
		mHeight = (Integer) in.readValue(Integer.class.getClassLoader());
		mAspectRatio = (String) in.readValue(String.class.getClassLoader());
	}

	public int describeContents() {
		return 0;
	}

}

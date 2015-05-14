package com.simas.vc.attributes;

import android.os.Parcel;
import android.os.Parcelable;

import com.simas.vc.VCException;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

/**
 * Required fields are specified as the constructor parameters. The setters used in the
 * constructor should throw exceptions if an invalid value is given.
 */
public class VideoStream extends Stream {

	private Integer mWidth, mHeight;
	private String mTBN, mTBC, mTBR;
	private String mAspectRatio;

	public VideoStream(Integer width, Integer height, String codecName) throws VCException {
		super(Type.VIDEO, codecName);
		setWidth(width);
		setHeight(height);
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

	public String getTBN() {
		return mTBN;
	}

	public String getTBR() {
		return mTBR;
	}

	public String getTBC() {
		return mTBC;
	}

	public VideoStream setWidth(Integer width) throws VCException {
		if (width == null) throw new VCException("Vide stream width must be valid!");
		mWidth = width;
		return this;
	}

	public VideoStream setHeight(Integer height) throws VCException {
		if (height == null) throw new VCException("Video stream height must be valid!");
		mHeight = height;
		return this;
	}

	public VideoStream setAspectRatio(String aspectRation) {
		mAspectRatio = aspectRation;
		return this;
	}

	public VideoStream setTBN(String tbn) {
		mTBN = tbn;
		return this;
	}

	public VideoStream setTBC(String tbc) {
		mTBC = tbc;
		return this;
	}

	public VideoStream setTBR(String tbr) {
		mTBR = tbr;
		return this;
	}

	@Override
	public String toString() {
		return String.format("%s\nVideoStream Width: %d, Height: %d, AspectRation: %s",
				super.toString(), getWidth(), getHeight(), getAspectRatio());
	}

	/* Parcelable */
	@Override
	public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		out.writeValue(mWidth);
		out.writeValue(mHeight);
		out.writeValue(mAspectRatio);
	}

	public static final Parcelable.Creator<VideoStream> CREATOR
			= new Parcelable.Creator<VideoStream>() {
		public VideoStream createFromParcel(Parcel in) {
			return new VideoStream(in);
		}

		public VideoStream[] newArray(int size) {
			return new VideoStream[size];
		}
	};

	private VideoStream(Parcel in) {
		super(in);
		mWidth = (Integer) in.readValue(Integer.class.getClassLoader());
		mHeight = (Integer) in.readValue(Integer.class.getClassLoader());
		mAspectRatio = (String) in.readValue(String.class.getClassLoader());
	}

}

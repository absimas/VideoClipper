package com.simas.vc.attributes;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

public abstract class Attributes implements Parcelable {

	public enum Type {
		AUDIO, VIDEO, FILE
	}

	protected Type mType;
	private String mCodecName, mCodecLongName;
	private Double mDuration;

	protected Attributes() {

	}

	public String getCodecName() {
		return mCodecName;
	}

	public String getCodecLongName() {
		return mCodecLongName;
	}

	public Double getDuration() {
		return mDuration;
	}

	public Type getType() {
		return mType;
	}

	public void setCodecName(String codecName) {
		mCodecName = codecName;
	}

	public void setCodecLongName(String codecLongName) {
		mCodecLongName = codecLongName;
	}

	public void setDuration(Double duration) {
		mDuration = duration;
	}

	protected void setType(Type type) {
		mType = type;
	}

	@Override
	public String toString() {
		return String.format("Attributes Type: %s, CodecName: %s, CodecLongName: %s, Duration: %f",
				getType().name(), getCodecName(), getCodecLongName(), getDuration());
	}

	/* Parcelable */

	public void writeToParcel(Parcel out, int flags) {
		out.writeSerializable(mType);
		out.writeValue(mCodecName);
		out.writeValue(mCodecLongName);
		out.writeValue(mDuration);
	}

	public static final Parcelable.Creator<Attributes> CREATOR = new Parcelable
			.Creator<Attributes>() {
		public Attributes createFromParcel(Parcel in) {
			try {
				Class<?> parceledClass = Class.forName(in.readString());
				return (Attributes) parceledClass.newInstance();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		public Attributes[] newArray(int size) {
			return new Attributes[size];
		}
	};

	protected Attributes(Parcel in) {
		mType = (Type) in.readSerializable();
		mCodecName = (String) in.readValue(String.class.getClassLoader());
		mCodecLongName = (String) in.readValue(String.class.getClassLoader());
		mDuration = (Double) in.readValue(Double.class.getClassLoader());
	}

	public int describeContents() {
		return 0;
	}

}

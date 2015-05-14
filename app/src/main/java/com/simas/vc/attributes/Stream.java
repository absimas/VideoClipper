package com.simas.vc.attributes;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.simas.vc.VCException;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

// ToDo use resources instead of strings in VCException in Stream classes

/**
 * Required fields are specified as the constructor parameters. The setters used in the
 * constructor should throw exceptions if an invalid value is given.
 */
public abstract class Stream implements Parcelable {

	public enum Type {
		AUDIO, VIDEO
	}

	private Integer mCodecTag = 0;
	private Type mType;
	private String mCodecName, mCodecLongName;
	private Double mDuration;

	protected Stream(Type type, String codecName) throws VCException {
		setCodecName(codecName);
		setType(type);
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

	public Integer getCodecTag() {
		return mCodecTag;
	}

	public Stream setCodecName(String codecName) throws VCException {
		if (TextUtils.isEmpty(codecName)) throw new VCException("Streams must have a codec!");
		mCodecName = codecName;
		return this;
	}

	public Stream setCodecLongName(String codecLongName) {
		mCodecLongName = codecLongName;
		return this;
	}

	public Stream setDuration(Double duration) {
		mDuration = duration;
		return this;
	}

	protected Stream setType(Type type) throws VCException {
		if (type == null) throw new VCException("Streams must be of a valid type!");
		mType = type;
		return this;
	}

	public Stream setCodecTag(Integer codecTag) {
		mCodecTag = codecTag;
		return this;
	}

	@Override
	public String toString() {
		return String.format("Stream Type: %s, CodecName: %s, CodecLongName: %s, Duration: %f",
				getType().name(), getCodecName(), getCodecLongName(), getDuration());
	}

	/* Parcelable */

	public void writeToParcel(Parcel out, int flags) {
		out.writeSerializable(mType);
		out.writeValue(mCodecName);
		out.writeValue(mCodecLongName);
		out.writeValue(mDuration);
	}

	public static final Parcelable.Creator<Stream> CREATOR = new Parcelable
			.Creator<Stream>() {
		public Stream createFromParcel(Parcel in) {
			try {
				Class<?> parceledClass = Class.forName(in.readString());
				return (Stream) parceledClass.newInstance();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		public Stream[] newArray(int size) {
			return new Stream[size];
		}
	};

	protected Stream(Parcel in) {
		mType = (Type) in.readSerializable();
		mCodecName = (String) in.readValue(String.class.getClassLoader());
		mCodecLongName = (String) in.readValue(String.class.getClassLoader());
		mDuration = (Double) in.readValue(Double.class.getClassLoader());
	}

	public int describeContents() {
		return 0;
	}

}

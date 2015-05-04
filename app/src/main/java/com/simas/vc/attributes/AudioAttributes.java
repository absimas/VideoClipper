package com.simas.vc.attributes;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */
public class AudioAttributes extends Attributes {

	private Integer mChannelCount, mSampleRate;

	public AudioAttributes() {
		setType(Type.AUDIO);
	}

	public Integer getChannelCount() {
		return mChannelCount;
	}
	public Integer getSampleRate() {
		return mSampleRate;
	}

	public void setChannelCount(Integer channelCount) {
		mChannelCount = channelCount;
	}
	public void setSampleRate(Integer sampleRate) {
		mSampleRate = sampleRate;
	}

	@Override
	public String toString() {
		return String.format("%s\nAudioAttributes ChannelCount: %d, SampleRate: %d",
				super.toString(), getChannelCount(), getSampleRate());
	}

	/* Parcelable */

	public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		out.writeValue(mChannelCount);
		out.writeValue(mSampleRate);
	}

	public static final Parcelable.Creator<AudioAttributes> CREATOR
			= new Parcelable.Creator<AudioAttributes>() {
		public AudioAttributes createFromParcel(Parcel in) {
			return new AudioAttributes(in);
		}

		public AudioAttributes[] newArray(int size) {
			return new AudioAttributes[size];
		}
	};

	private AudioAttributes(Parcel in) {
		super(in);
		mChannelCount = (Integer) in.readValue(Integer.class.getClassLoader());
		mSampleRate = (Integer) in.readValue(Integer.class.getClassLoader());
	}

	public int describeContents() {
		return 0;
	}

}

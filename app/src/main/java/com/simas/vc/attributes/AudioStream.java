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
public class AudioStream extends Stream {

	private Integer mChannelCount, mSampleRate;

	public AudioStream(String codecName) throws VCException {
		super(Type.AUDIO, codecName);
	}

	public Integer getChannelCount() {
		return mChannelCount;
	}
	public Integer getSampleRate() {
		return mSampleRate;
	}

	public AudioStream setChannelCount(Integer channelCount) {
		mChannelCount = channelCount;
		return this;
	}
	public AudioStream setSampleRate(Integer sampleRate) {
		mSampleRate = sampleRate;
		return this;
	}

	@Override
	public String toString() {
		return String.format("%s\nAudioStream ChannelCount: %d, SampleRate: %d",
				super.toString(), getChannelCount(), getSampleRate());
	}

	/* Parcelable */
	@Override
	public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		out.writeValue(mChannelCount);
		out.writeValue(mSampleRate);
	}

	public static final Parcelable.Creator<AudioStream> CREATOR
			= new Parcelable.Creator<AudioStream>() {
		public AudioStream createFromParcel(Parcel in) {
			return new AudioStream(in);
		}

		public AudioStream[] newArray(int size) {
			return new AudioStream[size];
		}
	};

	private AudioStream(Parcel in) {
		super(in);
		mChannelCount = (Integer) in.readValue(Integer.class.getClassLoader());
		mSampleRate = (Integer) in.readValue(Integer.class.getClassLoader());
	}

}

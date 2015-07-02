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
package com.simas.vc.attributes;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.SparseArray;
import com.simas.vc.VCException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// ToDo use resources instead of strings in VCException in Stream classes

/**
 * Class containing attribute values for any typed stream.
 * Required attributes are specified as the constructor parameters. The setters used in the
 * constructor should throw exceptions if an invalid value is given.
 */
public abstract class Stream implements Parcelable {

	public static final String ATTRIBUTE_CODEC_NAME = "Cocec name";
	public static final String ATTRIBUTE_CODEC_LONG_NAME = "Codec long name";
	public static final String ATTRIBUTE_DURATION = "Duration";
	private static final String ATTRIBUTE_CODEC_TAG = "Codec tag";

	/**
	 * Values are displayed to the user in a specific order. This order is saved in an ArrayList.
	 * Index 0 will be displayed first.
	 */
	public static final List<String> ATTRIBUTE_PRIORITIES = new ArrayList<String>() {{
		add(ATTRIBUTE_CODEC_NAME);
		add(ATTRIBUTE_CODEC_LONG_NAME);
		add(ATTRIBUTE_DURATION);
	}};

	/**
	 * Attributes that are displayable to the user.
	 */
	public SerializableSparseArray<Object> attributes = new SerializableSparseArray<>();
	/**
	 * Attributes that are for programming purposes only.
	 */
	private HashMap<String, Object> mAttributes = new HashMap<>();

	protected Stream(String codecName) throws VCException {


		// Set the values
		setCodecName(codecName);
		setCodecTag(0);
	}

	public String getCodecName() {
		return (String) getValue(ATTRIBUTE_CODEC_NAME);
	}

	public String getCodecLongName() {
		return (String) getValue(ATTRIBUTE_CODEC_LONG_NAME);
	}

	public Double getDuration() {
		return (Double) getValue(ATTRIBUTE_DURATION);
	}

	public Integer getCodecTag() {
		return (Integer) mAttributes.get(ATTRIBUTE_CODEC_TAG);
	}

	public Stream setCodecName(String codecName) throws VCException {
		if (TextUtils.isEmpty(codecName)) throw new VCException("Streams must have a codec!");
		setValue(ATTRIBUTE_CODEC_NAME, codecName);
		return this;
	}

	public Stream setCodecLongName(String codecLongName) {
		setValue(ATTRIBUTE_CODEC_LONG_NAME, codecLongName);
		return this;
	}

	public Stream setDuration(Double duration) {
		setValue(ATTRIBUTE_DURATION, duration);
		return this;
	}


	public Stream setCodecTag(Integer codecTag) {
		mAttributes.put(ATTRIBUTE_CODEC_TAG, codecTag);
		return this;
	}

	public List<String> getAttributePriorities() {
		return ATTRIBUTE_PRIORITIES;
	}

	/**
	 * Convenience method that decides whether the value should be put into a public {@code
	 * attributes} sparse array or the private {@code mAttributes} HashMap.
	 */
	protected void setValue(String key, Object value) {
		int keyIndex = getAttributePriorities().indexOf(key);
		if (keyIndex == -1) {
			mAttributes.put(key, value);
		} else {
			attributes.put(keyIndex, value);
		}
	}

	/**
	 * Determines whether the request item is located in the public {@code attributes} sparse array
	 * or the private {@code mAttributes} HashMap and returns it.
	 */
	protected Object getValue(String key) {
		int keyIndex = getAttributePriorities().indexOf(key);
		if (keyIndex == -1) {
			return mAttributes.get(key);
		} else {
			return attributes.get(keyIndex);
		}
	}

	@Override
	public String toString() {
		return String.format("CodecName: %s, CodecLongName: %s, Duration: %f",
				getCodecName(), getCodecLongName(), getDuration());
	}

	/* Parcelable */

	public void writeToParcel(Parcel out, int flags) {
		out.writeSerializable(attributes);
		out.writeSerializable(mAttributes);
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
		//noinspection unchecked
		attributes = (SerializableSparseArray<Object>) in.readSerializable();
		//noinspection unchecked
		mAttributes = (HashMap<String, Object>) in.readSerializable();
	}

	public int describeContents() {
		return 0;
	}

	public class SerializableSparseArray<E> extends SparseArray<E> implements Serializable {

		public SerializableSparseArray() {
			super();
		}

		private void writeObject(ObjectOutputStream oos) throws IOException {
			Object[] data = new  Object[size()];

			for (int i=data.length-1;i>=0;i--){
				Object[] pair = {keyAt(i),valueAt(i)};
				data[i] = pair;
			}
			oos.writeObject(data);
		}

		private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
			Object[] data = (Object[]) ois.readObject();
			for (Object aData : data) {
				Object[] pair = (Object[]) aData;
				//noinspection unchecked
				this.append((Integer) pair[0], (E) pair[1]);
			}
		}

	}

}

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

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

// ToDo use resources instead of strings in VCException in Stream classes

/**
 * Class containing field values for any typed stream.
 * Required fields are specified as the constructor parameters. The setters used in the
 * constructor should throw exceptions if an invalid value is given.
 */
public abstract class Stream implements Parcelable {

	public static final String FIELD_CODEC_NAME = "Cocec name";
	public static final String FIELD_CODEC_LONG_NAME = "Codec long name";
	public static final String FIELD_DURATION = "Duration";
	private static final String FIELD_CODEC_TAG = "Codec tag";

	/**
	 * Values are displayed to the user in a specific order. This order is saved in an ArrayList.
	 * Index 0 will be displayed first.
	 */
	public static final List<String> KEY_PRIORITIES = new ArrayList<String>() {{
		add(FIELD_CODEC_NAME);
		add(FIELD_CODEC_LONG_NAME);
		add(FIELD_DURATION);
	}};

	/**
	 * Fields that are displayable to the user.
	 */
	public SerializableSparseArray<Object> fields = new SerializableSparseArray<>();
	/**
	 * Fields that are for programming purposes only.
	 */
	private HashMap<String, Object> mFields = new HashMap<>();

	protected Stream(String codecName) throws VCException {


		// Set the values
		setCodecName(codecName);
		setCodecTag(0);
	}

	public String getCodecName() {
		return (String) getValue(FIELD_CODEC_NAME);
	}

	public String getCodecLongName() {
		return (String) getValue(FIELD_CODEC_LONG_NAME);
	}

	public Double getDuration() {
		return (Double) getValue(FIELD_DURATION);
	}

	public Integer getCodecTag() {
		return (Integer) mFields.get(FIELD_CODEC_TAG);
	}

	public Stream setCodecName(String codecName) throws VCException {
		if (TextUtils.isEmpty(codecName)) throw new VCException("Streams must have a codec!");
		setValue(FIELD_CODEC_NAME, codecName);
		return this;
	}

	public Stream setCodecLongName(String codecLongName) {
		setValue(FIELD_CODEC_LONG_NAME, codecLongName);
		return this;
	}

	public Stream setDuration(Double duration) {
		setValue(FIELD_DURATION, duration);
		return this;
	}


	public Stream setCodecTag(Integer codecTag) {
		mFields.put(FIELD_CODEC_TAG, codecTag);
		return this;
	}

	public List<String> getKeyPriorities() {
		return KEY_PRIORITIES;
	}

	/**
	 * Convenience method that decides whether the value should be put into a public {@code
	 * fields} sparse array or the private {@code mFields} HashMap.
	 */
	protected void setValue(String key, Object value) {
		int keyIndex = getKeyPriorities().indexOf(key);
		if (keyIndex == -1) {
			mFields.put(key, value);
		} else {
			fields.put(keyIndex, value);
		}
	}

	/**
	 * Determines whether the request item is located in the public {@code fields} sparse array
	 * or the private {@code mFields} HashMap and returns it.
	 */
	protected Object getValue(String key) {
		int keyIndex = getKeyPriorities().indexOf(key);
		if (keyIndex == -1) {
			return mFields.get(key);
		} else {
			return fields.get(keyIndex);
		}
	}

	@Override
	public String toString() {
		return String.format("CodecName: %s, CodecLongName: %s, Duration: %f",
				getCodecName(), getCodecLongName(), getDuration());
	}

	/* Parcelable */

	public void writeToParcel(Parcel out, int flags) {
		out.writeSerializable(fields);
		out.writeSerializable(mFields);
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
		fields = (SerializableSparseArray<Object>) in.readSerializable();
		//noinspection unchecked
		mFields = (HashMap<String, Object>) in.readSerializable();
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

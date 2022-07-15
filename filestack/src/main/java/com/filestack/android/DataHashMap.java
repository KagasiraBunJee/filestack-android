package com.filestack.android;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

public class DataHashMap extends HashMap<String, Object> implements Parcelable {

    public DataHashMap() {
        super();
    }

    protected DataHashMap(Parcel in) {
        int size = in.readInt();
        for(int i = 0; i < size; i++){
            String key = in.readString();
            if (in.readString() == null) {
                this.put(key,in.readInt());
            } else {
                this.put(key,in.readString());
            }
        }
    }

    public static final Creator<DataHashMap> CREATOR = new Creator<DataHashMap>() {
        @Override
        public DataHashMap createFromParcel(Parcel in) {
            return new DataHashMap(in);
        }

        @Override
        public DataHashMap[] newArray(int size) {
            return new DataHashMap[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(size());
        for(Map.Entry<String,Object> entry : this.entrySet()){
            parcel.writeString(entry.getKey());
            if (entry.getValue() instanceof String) {
                parcel.writeString((String)entry.getValue());
            } else {
                parcel.writeInt((int)entry.getValue());
            }
        }
    }
}

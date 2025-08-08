package com.example.relmusic.ui.artist;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class ArtistItem implements Parcelable {
    private String artistName;
    private Uri artistImageUri;
    private int songCount;

    public ArtistItem(String artistName, Uri artistImageUri, int songCount) {
        this.artistName = artistName != null ? artistName : "Unknown Artist";
        this.artistImageUri = artistImageUri;
        this.songCount = songCount;
    }

    protected ArtistItem(Parcel in) {
        artistName = in.readString();
        artistImageUri = in.readParcelable(Uri.class.getClassLoader());
        songCount = in.readInt();
    }

    public static final Creator<ArtistItem> CREATOR = new Creator<ArtistItem>() {
        @Override
        public ArtistItem createFromParcel(Parcel in) {
            return new ArtistItem(in);
        }

        @Override
        public ArtistItem[] newArray(int size) {
            return new ArtistItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(artistName);
        dest.writeParcelable(artistImageUri, flags);
        dest.writeInt(songCount);
    }

    public String getArtistName() { return artistName; }
    public Uri getArtistImageUri() { return artistImageUri; }
    public int getSongCount() { return songCount; }

    public void setArtistName(String artistName) { this.artistName = artistName; }
    public void setArtistImageUri(Uri artistImageUri) { this.artistImageUri = artistImageUri; }
    public void setSongCount(int songCount) { this.songCount = songCount; }

    public String getFormattedSongCount() {
        return songCount == 1 ? songCount + " song" : songCount + " songs";
    }
}
package com.example.relmusic.ui.artist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.relmusic.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder> {

    private List<ArtistItem> artistList;
    private Context context;
    private OnArtistItemClickListener listener;

    public interface OnArtistItemClickListener {
        void onArtistItemClick(ArtistItem artistItem);
        void onPlayButtonClick(ArtistItem artistItem);
    }

    public ArtistAdapter(List<ArtistItem> artistList, Context context) {
        this.artistList = artistList;
        this.context = context;
    }

    public void setOnArtistItemClickListener(OnArtistItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_artist, parent, false);
        return new ArtistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
        ArtistItem artistItem = artistList.get(position);

        holder.artistNameTextView.setText(artistItem.getArtistName());
        holder.songCountTextView.setText(artistItem.getFormattedSongCount());

        // Load artist image (using album art as placeholder for artist image)
        Glide.with(context)
                .load(artistItem.getArtistImageUri())
                .apply(new RequestOptions()
                        .placeholder(R.drawable.ic_outline_person_24)
                        .error(R.drawable.ic_outline_person_24)
                        .centerCrop()
                        .circleCrop()) // Make it circular for artist images
                .into(holder.artistImageView);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onArtistItemClick(artistItem);
            }
        });

        holder.playButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayButtonClick(artistItem);
            }
        });
    }

    @Override
    public int getItemCount() {
        return artistList.size();
    }

    public static class ArtistViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ImageView artistImageView;
        TextView artistNameTextView;
        TextView songCountTextView;
        MaterialButton playButton;

        public ArtistViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.artist_card);
            artistImageView = itemView.findViewById(R.id.artist_image);
            artistNameTextView = itemView.findViewById(R.id.artist_name);
            songCountTextView = itemView.findViewById(R.id.song_count);
            playButton = itemView.findViewById(R.id.play_button);
        }
    }
}
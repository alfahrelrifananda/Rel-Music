package com.example.relmusic.ui.artist;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.example.relmusic.R;
import com.example.relmusic.ui.music.MusicAdapter;
import com.example.relmusic.ui.music.MusicItem;
import com.example.relmusic.ui.pages.nowplaying.NowPlayingActivity;
import com.example.relmusic.service.MusicService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArtistDetailActivity extends AppCompatActivity {

    private static final String TAG = "ArtistDetailActivity";

    private MaterialToolbar toolbar;
    private ImageView artistImageView;
    private TextView artistNameTextView;
    private TextView songCountTextView;
    private RecyclerView songsRecyclerView;
    private View loadingLayout;
    private View emptyState;

    private MaterialCardView miniPlayerContainer;
    private ImageView miniAlbumArt;
    private TextView miniSongTitle;
    private TextView miniArtistName;
    private MaterialButton miniPlayPauseButton;
    private MaterialButton miniNextButton;
    private MaterialButton miniCloseButton;

    private ArtistItem artistItem;
    private List<MusicItem> artistSongs = new ArrayList<>();
    private MusicAdapter musicAdapter;
    private ExecutorService executorService;
    private boolean isLoading = false;

    private MusicItem currentPlayingItem;
    private boolean isPlaying = false;
    private boolean isMiniPlayerVisible = false;
    private boolean isReceiverRegistered = false;
    private boolean isActivityDestroyed = false;

    private ObjectAnimator albumArtRotationAnimator;

    private BroadcastReceiver musicUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isActivityDestroyed || isFinishing() || isDestroyed()) {
                return;
            }

            try {
                String action = intent.getAction();
                if (action != null) {
                    switch (action) {
                        case MusicService.ACTION_MUSIC_UPDATED:
                            MusicItem musicItem = intent.getParcelableExtra("music_item");
                            boolean playing = intent.getBooleanExtra("is_playing", false);
                            if (musicItem != null) {
                                showMiniPlayer(musicItem);
                                updateMiniPlayerState(playing);
                            }
                            break;
                        case MusicService.ACTION_PLAYBACK_STATE_CHANGED:
                            boolean playingState = intent.getBooleanExtra("is_playing", false);
                            updateMiniPlayerState(playingState);
                            break;
                        case MusicService.ACTION_HIDE_MINI_PLAYER:
                            hideMiniPlayer();
                            break;
                        case "MINI_PLAYER_VISIBILITY_CHANGED":
                            boolean isVisible = intent.getBooleanExtra("is_visible", false);
                            int height = intent.getIntExtra("height", 0);
                            adjustRecyclerViewPadding(isVisible, height);
                            break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling broadcast: " + e.getMessage(), e);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist_detail);

        try {
            executorService = Executors.newSingleThreadExecutor();

            if (!getArtistDataFromIntent()) {
                finish();
                return;
            }

            if (!initializeViews()) {
                Log.e(TAG, "Failed to initialize views");
                finish();
                return;
            }

            if (!initializeMiniPlayer()) {
                Log.e(TAG, "Failed to initialize mini player");
                finish();
                return;
            }

            setupToolbar();
            setupArtistHeader();
            setupRecyclerView();

            loadArtistSongs();

            registerMusicUpdateReceiver();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            finish();
        }
    }

    private boolean getArtistDataFromIntent() {
        artistItem = getIntent().getParcelableExtra("artist_item");
        if (artistItem == null) {
            Toast.makeText(this, "Error: Artist not found", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean initializeViews() {
        try {
            toolbar = findViewById(R.id.toolbar);
            artistImageView = findViewById(R.id.artistBackgroundImageView);
            artistNameTextView = findViewById(R.id.artistNameTextView);
            songCountTextView = findViewById(R.id.songCountTextView);
            songsRecyclerView = findViewById(R.id.songsRecyclerView);
            loadingLayout = findViewById(R.id.loadingLayout);
            emptyState = findViewById(R.id.emptyState);

            if (toolbar == null || artistImageView == null || artistNameTextView == null ||
                    songCountTextView == null || songsRecyclerView == null ||
                    loadingLayout == null || emptyState == null) {
                Log.e(TAG, "One or more artist views are null");
                return false;
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage(), e);
            return false;
        }
    }

    private boolean initializeMiniPlayer() {
        try {
            miniPlayerContainer = findViewById(R.id.miniPlayerContainer);
            miniAlbumArt = findViewById(R.id.miniAlbumArt);
            miniSongTitle = findViewById(R.id.miniSongTitle);
            miniArtistName = findViewById(R.id.miniArtistName);
            miniPlayPauseButton = findViewById(R.id.miniPlayPauseButton);
            miniNextButton = findViewById(R.id.miniNextButton);
            miniCloseButton = findViewById(R.id.miniCloseButton);

            if (miniPlayerContainer == null || miniAlbumArt == null ||
                    miniSongTitle == null || miniArtistName == null ||
                    miniPlayPauseButton == null || miniNextButton == null || miniCloseButton == null) {
                Log.e(TAG, "One or more mini player components are null");
                return false;
            }

            setupAlbumArtRotationAnimator();

            miniPlayerContainer.setOnClickListener(v -> {
                if (!isActivityDestroyed) {
                    openNowPlayingActivity();
                }
            });

            miniPlayPauseButton.setOnClickListener(v -> {
                if (!isActivityDestroyed) {
                    try {
                        Intent serviceIntent = new Intent(this, MusicService.class);
                        serviceIntent.setAction(MusicService.ACTION_TOGGLE_PLAY_PAUSE);
                        startService(serviceIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error toggling play/pause: " + e.getMessage(), e);
                    }
                }
            });

            miniNextButton.setOnClickListener(v -> {
                if (!isActivityDestroyed) {
                    try {
                        Intent serviceIntent = new Intent(this, MusicService.class);
                        serviceIntent.setAction(MusicService.ACTION_NEXT);
                        startService(serviceIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error playing next: " + e.getMessage(), e);
                    }
                }
            });

            miniCloseButton.setOnClickListener(v -> {
                if (!isActivityDestroyed) {
                    try {
                        Intent serviceIntent = new Intent(this, MusicService.class);
                        serviceIntent.setAction(MusicService.ACTION_STOP);
                        startService(serviceIntent);
                        hideMiniPlayer();
                    } catch (Exception e) {
                        Log.e(TAG, "Error stopping music: " + e.getMessage(), e);
                    }
                }
            });

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing mini player: " + e.getMessage(), e);
            return false;
        }
    }

    private void setupAlbumArtRotationAnimator() {
        try {
            if (miniAlbumArt != null) {
                albumArtRotationAnimator = ObjectAnimator.ofFloat(miniAlbumArt, "rotation", 0f, 360f);
                albumArtRotationAnimator.setDuration(5000);
                albumArtRotationAnimator.setInterpolator(new LinearInterpolator());
                albumArtRotationAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                albumArtRotationAnimator.setRepeatMode(ObjectAnimator.RESTART);

            } else {
                Log.e(TAG, "miniAlbumArt is null when setting up rotation animator");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up album art rotation animator: " + e.getMessage(), e);
        }
    }

    private void startAlbumArtRotation() {
        try {
            if (miniAlbumArt != null) {
                stopAlbumArtRotation();

                miniAlbumArt.animate()
                        .rotation(360f)
                        .setDuration(8000)
                        .setInterpolator(new LinearInterpolator())
                        .withEndAction(() -> {
                            if (!isActivityDestroyed && isPlaying && isMiniPlayerVisible) {
                                miniAlbumArt.setRotation(0f);
                                startAlbumArtRotation();
                            }
                        })
                        .start();
            } else {
                Log.w(TAG, "Cannot start rotation - miniAlbumArt is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting album art rotation: " + e.getMessage(), e);
        }
    }

    private void stopAlbumArtRotation() {
        try {
            if (miniAlbumArt != null) {
                miniAlbumArt.animate().cancel();
                miniAlbumArt.clearAnimation();

                if (albumArtRotationAnimator != null && albumArtRotationAnimator.isRunning()) {
                    albumArtRotationAnimator.cancel();
                }
            } else {
                Log.w(TAG, "miniAlbumArt is null when trying to stop rotation");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping album art rotation: " + e.getMessage(), e);
        }
    }

    private void setupToolbar() {
        try {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setTitle("");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar: " + e.getMessage(), e);
        }
    }

    private void setupArtistHeader() {
        try {
            artistNameTextView.setText(artistItem.getArtistName());

            if (artistItem.getArtistImageUri() != null) {
                Glide.with(this)
                        .load(artistItem.getArtistImageUri())
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_outline_person_24)
                                .error(R.drawable.ic_outline_person_24)
                                .centerCrop())
                        .into(artistImageView);
            } else {
                artistImageView.setImageResource(R.drawable.ic_outline_person_24);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up artist header: " + e.getMessage(), e);
        }
    }

    private void setupRecyclerView() {
        try {
            musicAdapter = new MusicAdapter(artistSongs, this);
            songsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            songsRecyclerView.setAdapter(musicAdapter);

            musicAdapter.setOnMusicItemClickListener(new MusicAdapter.OnMusicItemClickListener() {
                @Override
                public void onMusicItemClick(MusicItem musicItem) {
                    startMusicServiceAndOpenNowPlaying(musicItem);
                }

                @Override
                public void onPlayButtonClick(MusicItem musicItem) {
                    startMusicService(musicItem);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerView: " + e.getMessage(), e);
        }
    }

    private void loadArtistSongs() {
        if (!hasStoragePermission()) {
            Toast.makeText(this, "Storage permission required to load songs", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isLoading) return;

        isLoading = true;
        showLoading(true);

        executorService.execute(() -> {
            List<MusicItem> tempSongsList = new ArrayList<>();

            ContentResolver contentResolver = getContentResolver();
            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TRACK
            };

            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                    MediaStore.Audio.Media.ARTIST + " = ?";
            String[] selectionArgs = {artistItem.getArtistName()};
            String sortOrder = MediaStore.Audio.Media.ALBUM + " ASC, " +
                    MediaStore.Audio.Media.TRACK + " ASC, " +
                    MediaStore.Audio.Media.TITLE + " ASC";

            try (Cursor cursor = contentResolver.query(musicUri, projection, selection, selectionArgs, sortOrder)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                    int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                    int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                    int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

                    do {
                        long id = cursor.getLong(idColumn);
                        String title = cursor.getString(titleColumn);
                        String artist = cursor.getString(artistColumn);
                        String album = cursor.getString(albumColumn);
                        long duration = cursor.getLong(durationColumn);
                        String path = cursor.getString(pathColumn);
                        long albumId = cursor.getLong(albumIdColumn);

                        Uri albumArtUri = Uri.parse("content://media/external/audio/albumart/" + albumId);

                        MusicItem musicItem = new MusicItem(id, title, artist, album, duration, path, albumArtUri);
                        tempSongsList.add(musicItem);

                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    showLoading(false);
                    isLoading = false;
                    Toast.makeText(this, "Error loading artist songs", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            runOnUiThread(() -> {
                showLoading(false);
                isLoading = false;

                artistSongs.clear();
                artistSongs.addAll(tempSongsList);

                songCountTextView.setText(artistSongs.size() + " songs");

                if (musicAdapter != null) {
                    musicAdapter.notifyDataSetChanged();
                }

                updateUI();
            });
        });
    }

    private boolean hasStoragePermission() {
        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void startMusicServiceAndOpenNowPlaying(MusicItem musicItem) {
        startMusicServiceWithPlaylist(musicItem);

        new android.os.Handler().postDelayed(() -> {
            openNowPlaying(musicItem);
        }, 200);
    }

    private void startMusicService(MusicItem musicItem) {
        startMusicServiceWithPlaylist(musicItem);
    }

    private void startMusicServiceWithPlaylist(MusicItem selectedSong) {
        if (artistSongs.isEmpty()) {
            return;
        }

        int selectedIndex = -1;
        for (int i = 0; i < artistSongs.size(); i++) {
            if (artistSongs.get(i).getId() == selectedSong.getId()) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex == -1) {
            selectedIndex = 0;
        }

        Intent playlistIntent = new Intent(this, MusicService.class);
        playlistIntent.setAction(MusicService.ACTION_SET_PLAYLIST);
        playlistIntent.putParcelableArrayListExtra("playlist", new ArrayList<>(artistSongs));
        playlistIntent.putExtra("start_index", selectedIndex);
        startService(playlistIntent);

        Intent playIntent = new Intent(this, MusicService.class);
        playIntent.setAction(MusicService.ACTION_PLAY);
        playIntent.putExtra("music_item", selectedSong);
        startService(playIntent);
    }

    private void openNowPlaying(MusicItem musicItem) {
        Intent intent = new Intent(this, NowPlayingActivity.class);
        intent.putExtra("music_item", (Parcelable) musicItem);
        startActivity(intent);

        overridePendingTransition(
                R.anim.slide_in_bottom,
                R.anim.slide_out_top
        );
    }

    private void openNowPlayingActivity() {
        if (isActivityDestroyed || currentPlayingItem == null) {
            return;
        }

        try {
            Intent intent = new Intent(this, NowPlayingActivity.class);
            intent.putExtra("music_item", currentPlayingItem);
            startActivity(intent);

            overridePendingTransition(
                    R.anim.slide_in_bottom,
                    R.anim.slide_out_top
            );
        } catch (Exception e) {
            Log.e(TAG, "Error opening now playing activity: " + e.getMessage(), e);
        }
    }

    private void updateUI() {
        if (artistSongs.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            songsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            songsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean show) {
        if (show) {
            loadingLayout.setVisibility(View.VISIBLE);
            songsRecyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
        } else {
            loadingLayout.setVisibility(View.GONE);
        }
    }

    private void adjustRecyclerViewPadding(boolean isVisible, int height) {
        if (songsRecyclerView != null) {
            songsRecyclerView.setPadding(
                    songsRecyclerView.getPaddingLeft(),
                    songsRecyclerView.getPaddingTop(),
                    songsRecyclerView.getPaddingRight(),
                    isVisible ? height : 0
            );

            songsRecyclerView.post(() -> {
                if (songsRecyclerView.getAdapter() != null) {
                    songsRecyclerView.getAdapter().notifyDataSetChanged();
                }
            });
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMusicUpdateReceiver() {
        try {
            if (!isReceiverRegistered && musicUpdateReceiver != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(MusicService.ACTION_MUSIC_UPDATED);
                filter.addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED);
                filter.addAction(MusicService.ACTION_HIDE_MINI_PLAYER);
                filter.addAction("MINI_PLAYER_VISIBILITY_CHANGED");

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(musicUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(musicUpdateReceiver, filter);
                }

                isReceiverRegistered = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering broadcast receiver: " + e.getMessage(), e);
            isReceiverRegistered = false;
        }
    }

    public void showMiniPlayer(MusicItem musicItem) {
        if (isActivityDestroyed || isFinishing() || isDestroyed() || musicItem == null) {
            return;
        }

        try {
            currentPlayingItem = musicItem;

            if (miniSongTitle == null || miniArtistName == null ||
                    miniAlbumArt == null || miniPlayerContainer == null) {
                Log.e(TAG, "Mini player components are null");
                return;
            }

            miniSongTitle.setText(musicItem.getTitle());
            miniArtistName.setText(musicItem.getArtist());

            try {
                Glide.with(this)
                        .load(musicItem.getAlbumArtUri())
                        .placeholder(R.drawable.ic_outline_music_note_24)
                        .error(R.drawable.ic_outline_music_note_24)
                        .into(miniAlbumArt);

                if (albumArtRotationAnimator == null) {
                    setupAlbumArtRotationAnimator();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading album art: " + e.getMessage(), e);
            }

            if (!isMiniPlayerVisible) {
                isMiniPlayerVisible = true;
                miniPlayerContainer.setVisibility(View.VISIBLE);
                miniPlayerContainer.setTranslationY(miniPlayerContainer.getHeight());
                miniPlayerContainer.animate()
                        .translationY(0)
                        .setDuration(300)
                        .start();
            }

            updateMiniPlayerPlayButton();
        } catch (Exception e) {
            Log.e(TAG, "Error showing mini player: " + e.getMessage(), e);
        }
    }

    public void hideMiniPlayer() {
        if (isActivityDestroyed || isFinishing() || isDestroyed()) {
            return;
        }

        try {
            stopAlbumArtRotation();

            if (isMiniPlayerVisible && miniPlayerContainer != null) {
                isMiniPlayerVisible = false;
                miniPlayerContainer.animate()
                        .translationY(miniPlayerContainer.getHeight())
                        .setDuration(300)
                        .withEndAction(() -> {
                            if (!isActivityDestroyed && miniPlayerContainer != null) {
                                miniPlayerContainer.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding mini player: " + e.getMessage(), e);
        }
    }

    public void updateMiniPlayerState(boolean playing) {
        if (isActivityDestroyed) {
            return;
        }

        try {
            isPlaying = playing;
            updateMiniPlayerPlayButton();

            if (playing && isMiniPlayerVisible) {
                startAlbumArtRotation();
            } else {
                stopAlbumArtRotation();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating mini player state: " + e.getMessage(), e);
        }
    }

    private void updateMiniPlayerPlayButton() {
        if (isActivityDestroyed || miniPlayPauseButton == null) {
            return;
        }

        try {
            int iconRes = isPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24;
            miniPlayPauseButton.setIconResource(iconRes);
        } catch (Exception e) {
            Log.e(TAG, "Error updating play button: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            Intent serviceIntent = new Intent(this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_REQUEST_STATE);
            startService(serviceIntent);

            if (isPlaying && isMiniPlayerVisible) {
                startAlbumArtRotation();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (miniPlayerContainer != null) {
                miniPlayerContainer.clearAnimation();
            }
            if (albumArtRotationAnimator != null && albumArtRotationAnimator.isRunning()) {
                albumArtRotationAnimator.pause();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        isActivityDestroyed = true;

        try {
            if (albumArtRotationAnimator != null) {
                albumArtRotationAnimator.cancel();
                albumArtRotationAnimator = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up rotation animator: " + e.getMessage(), e);
        }

        if (isReceiverRegistered && musicUpdateReceiver != null) {
            try {
                unregisterReceiver(musicUpdateReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver was not registered or already unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver: " + e.getMessage(), e);
            } finally {
                isReceiverRegistered = false;
            }
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        try {
            currentPlayingItem = null;
            musicUpdateReceiver = null;
            artistSongs = null;

            if (!isDestroyed()) {
                Glide.with(this).clear(miniAlbumArt);
                Glide.with(this).clear(artistImageView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing references: " + e.getMessage(), e);
        }
    }
}
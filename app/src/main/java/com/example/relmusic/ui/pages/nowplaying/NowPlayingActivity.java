package com.example.relmusic.ui.pages.nowplaying;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.relmusic.R;
import com.example.relmusic.databinding.ActivityNowPlayingBinding;
import com.example.relmusic.service.MusicService;
import com.example.relmusic.ui.music.MusicItem;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;

import java.util.concurrent.TimeUnit;

public class NowPlayingActivity extends AppCompatActivity {

    private ActivityNowPlayingBinding binding;
    private MusicService musicService;
    private boolean serviceBound = false;
    private MusicItem currentSong;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBar;
    private boolean isPlaying = false;
    private boolean isDraggingSeekBar = false;

    private boolean isShuffleEnabled = false;
    private int repeatMode = MusicService.REPEAT_OFF;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            serviceBound = true;

            updateUIFromService();
            startSeekBarUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            musicService = null;
        }
    };

    private BroadcastReceiver musicUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case MusicService.ACTION_PLAYBACK_STATE_CHANGED:
                        boolean playing = intent.getBooleanExtra("is_playing", false);
                        isPlaying = playing;
                        updatePlayPauseButton();
                        if (playing) {
                            startSeekBarUpdates();
                        } else {
                            stopSeekBarUpdates();
                        }
                        break;
                    case MusicService.ACTION_MUSIC_UPDATED:
                        MusicItem updatedSong = intent.getParcelableExtra("music_item");
                        if (updatedSong != null) {
                            currentSong = updatedSong;
                            setupNowPlaying();
                        }
                        break;
                    case MusicService.ACTION_SHUFFLE_STATE_CHANGED:
                        isShuffleEnabled = intent.getBooleanExtra("is_shuffle_enabled", false);
                        updateShuffleButton();
                        break;
                    case MusicService.ACTION_REPEAT_STATE_CHANGED:
                        repeatMode = intent.getIntExtra("repeat_mode", MusicService.REPEAT_OFF);
                        updateRepeatButton();
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);
        binding = ActivityNowPlayingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent intent = getIntent();
        if (intent.hasExtra("music_item")) {
            currentSong = intent.getParcelableExtra("music_item");
            setupNowPlaying();
        } else {
            finish();
            return;
        }

        setupClickListeners();
        setupSlider();
        bindToMusicService();
        registerMusicUpdateReceiver();
    }

    private void bindToMusicService() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void registerMusicUpdateReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED);
            filter.addAction(MusicService.ACTION_MUSIC_UPDATED);
            filter.addAction(MusicService.ACTION_SHUFFLE_STATE_CHANGED);
            filter.addAction(MusicService.ACTION_REPEAT_STATE_CHANGED);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(musicUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(musicUpdateReceiver, filter);
            }

        } catch (Exception e) {
            Log.e("NowPlayingActivity", "Error registering broadcast receiver: " + e.getMessage(), e);
        }
    }

    private void setupNowPlaying() {
        if (currentSong == null) return;

        binding.songTitle.setText(currentSong.getTitle());
        binding.artistName.setText(currentSong.getArtist());
        binding.albumName.setText(currentSong.getAlbum());

        binding.totalDuration.setText(formatDuration(currentSong.getDuration()));

        loadAlbumArt();
    }

    private void updateUIFromService() {
        if (musicService != null) {
            MusicItem serviceSong = musicService.getCurrentSong();
            if (serviceSong != null) {
                if (currentSong == null || serviceSong.getId() != currentSong.getId()) {
                    currentSong = serviceSong;
                    setupNowPlaying();
                }
                isPlaying = musicService.isPlaying();
                updatePlayPauseButton();
            }

            isShuffleEnabled = musicService.isShuffleEnabled();
            repeatMode = musicService.getRepeatMode();
            updateShuffleButton();
            updateRepeatButton();
        }
    }

    private void loadAlbumArt() {
        Glide.with(this)
                .asBitmap()
                .load(currentSong.getAlbumArtUri())
                .placeholder(R.drawable.ic_outline_music_note_24)
                .error(R.drawable.ic_outline_music_note_24)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> transition) {
                        binding.albumArt.setImageBitmap(bitmap);

                        Palette.from(bitmap).generate(palette -> {
                            if (palette != null) {
                                applyDynamicColors(palette);
                            }
                        });
                    }

                    @Override
                    public void onLoadCleared(Drawable placeholder) {
                        binding.albumArt.setImageDrawable(placeholder);
                    }
                });
    }

    private void applyDynamicColors(Palette palette) {
        int primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0);
        int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);

        Palette.Swatch vibrantSwatch = palette.getVibrantSwatch();
        Palette.Swatch dominantSwatch = palette.getDominantSwatch();

        int accentColor = primaryColor;
        if (vibrantSwatch != null) {
            accentColor = vibrantSwatch.getRgb();
        } else if (dominantSwatch != null) {
            accentColor = dominantSwatch.getRgb();
        }

        int blackColor = Color.BLACK;

        binding.seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.seekBar.setTrackActiveTintList(android.content.res.ColorStateList.valueOf(accentColor));

        binding.playPauseButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.playPauseButton.setIconTint(android.content.res.ColorStateList.valueOf(blackColor));

        binding.previousButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.previousButton.setIconTint(android.content.res.ColorStateList.valueOf(blackColor));

        binding.nextButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.nextButton.setIconTint(android.content.res.ColorStateList.valueOf(blackColor));

        binding.shuffleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.shuffleButton.setIconTint(android.content.res.ColorStateList.valueOf(blackColor));

        binding.repeatButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.repeatButton.setIconTint(android.content.res.ColorStateList.valueOf(blackColor));

        binding.nowPlayingContainer.setBackgroundColor(surfaceColor);
    }

    private void setupClickListeners() {
        binding.playPauseButton.setOnClickListener(v -> togglePlayPause());
        binding.previousButton.setOnClickListener(v -> playPrevious());
        binding.nextButton.setOnClickListener(v -> playNext());
        binding.backButton.setOnClickListener(v -> onBackPressed());

        binding.shuffleButton.setOnClickListener(v -> toggleShuffle());
        binding.repeatButton.setOnClickListener(v -> toggleRepeat());
    }

    private void setupSlider() {
        binding.seekBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                isDraggingSeekBar = true;
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                if (musicService != null && musicService.getMediaPlayer() != null) {
                    MediaPlayer mediaPlayer = musicService.getMediaPlayer();
                    int seekPosition = (int) ((slider.getValue() / 100.0) * mediaPlayer.getDuration());
                    mediaPlayer.seekTo(seekPosition);
                }
                isDraggingSeekBar = false;
            }
        });

        binding.seekBar.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser) {
                if (fromUser && musicService != null && musicService.getMediaPlayer() != null) {
                    MediaPlayer mediaPlayer = musicService.getMediaPlayer();
                    int seekPosition = (int) ((value / 100.0) * mediaPlayer.getDuration());
                    binding.currentTime.setText(formatDuration(seekPosition));
                }
            }
        });

        binding.seekBar.setValueFrom(0f);
        binding.seekBar.setValueTo(100f);
        binding.seekBar.setValue(0f);
    }

    private void togglePlayPause() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_TOGGLE_PLAY_PAUSE);
        startService(serviceIntent);
    }

    private void playNext() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_NEXT);
        startService(serviceIntent);
    }

    private void playPrevious() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_PREVIOUS);
        startService(serviceIntent);
    }

    private void toggleShuffle() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_TOGGLE_SHUFFLE);
        startService(serviceIntent);
    }

    private void toggleRepeat() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_TOGGLE_REPEAT);
        startService(serviceIntent);
    }

    private void updatePlayPauseButton() {
        int iconRes = isPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24;
        Drawable icon = ContextCompat.getDrawable(this, iconRes);
        binding.playPauseButton.setIcon(icon);
    }

    private void updateShuffleButton() {
        if (isShuffleEnabled) {
            binding.shuffleButton.setAlpha(1.0f);
        } else {
            binding.shuffleButton.setAlpha(0.6f);
        }
    }

    private void updateRepeatButton() {
        switch (repeatMode) {
            case MusicService.REPEAT_OFF:
                binding.repeatButton.setAlpha(0.6f);
                binding.repeatButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_repeat_24));
                break;
            case MusicService.REPEAT_ALL:
                binding.repeatButton.setAlpha(1.0f);
                binding.repeatButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_repeat_24));
                break;
            case MusicService.REPEAT_ONE:
                binding.repeatButton.setAlpha(1.0f);
                binding.repeatButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_repeat_one_24));
                break;
        }
    }

    private void startSeekBarUpdates() {
        stopSeekBarUpdates();

        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (musicService != null && musicService.getMediaPlayer() != null && isPlaying && !isDraggingSeekBar) {
                    MediaPlayer mediaPlayer = musicService.getMediaPlayer();
                    try {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        int duration = mediaPlayer.getDuration();

                        if (duration > 0) {
                            float progress = ((float) currentPosition / duration) * 100;
                            binding.seekBar.setValue(progress);
                            binding.currentTime.setText(formatDuration(currentPosition));
                        }
                    } catch (IllegalStateException e) {
                    }
                }

                if (isPlaying) {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(updateSeekBar);
    }

    private void stopSeekBarUpdates() {
        if (updateSeekBar != null) {
            handler.removeCallbacks(updateSeekBar);
        }
    }

    private String formatDuration(long duration) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void onBackPressedDispatcher() {
        super.onBackPressed();
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopSeekBarUpdates();

        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        if (musicUpdateReceiver != null) {
            unregisterReceiver(musicUpdateReceiver);
        }
    }
}
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
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.MotionEvent;
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
import com.google.android.material.progressindicator.LinearProgressIndicator;

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
        setupProgressIndicator();
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
                updateProgressFromService();
            }

            isShuffleEnabled = musicService.isShuffleEnabled();
            repeatMode = musicService.getRepeatMode();
            updateShuffleButton();
            updateRepeatButton();
        }
    }
    private void updateProgressFromService() {
        if (musicService != null && musicService.getMediaPlayer() != null) {
            try {
                MediaPlayer mediaPlayer = musicService.getMediaPlayer();
                int currentPosition = mediaPlayer.getCurrentPosition();
                int duration = mediaPlayer.getDuration();

                if (duration > 0) {
                    int progress = (int) (((float) currentPosition / duration) * 100);
                    binding.seekBar.setProgress(progress);
                    binding.currentTime.setText(formatDuration(currentPosition));
                }
            } catch (IllegalStateException e) {
                Log.e("NowPlayingActivity", "Error getting playback position: " + e.getMessage());
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupProgressIndicator() {
        binding.seekBar.setIndeterminate(false);
        binding.seekBar.setMax(100);

        binding.seekBar.setOnTouchListener((v, event) -> {
            int leftPadding = v.getPaddingLeft();
            int rightPadding = v.getPaddingRight();
            int usableWidth = v.getWidth() - leftPadding - rightPadding;
            float adjustedX = event.getX() - leftPadding;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDraggingSeekBar = true;
                    handleProgressTouch(adjustedX, usableWidth);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isDraggingSeekBar) {
                        handleProgressTouch(adjustedX, usableWidth);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDraggingSeekBar) {
                        handleProgressTouch(adjustedX, usableWidth);
                        seekToPosition(adjustedX, usableWidth);
                        isDraggingSeekBar = false;
                    }
                    return true;
            }
            return false;
        });
    }

    private void setupNowPlaying() {
        if (currentSong == null) return;

        String name = currentSong.getTitle();
        String band = currentSong.getArtist();
        binding.songTitle.setText(name + " - " + band);
        binding.songTitle.setSelected(true);
        binding.totalDuration.setText(formatDuration(currentSong.getDuration()));

        if (musicService == null) {
            binding.currentTime.setText("0:00");
            binding.seekBar.setProgress(0);
        }

        loadAlbumArt();
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

                        applyBlurredBackground(bitmap);

//                        Palette.from(bitmap).generate(palette -> {
//                            if (palette != null) {
//                                applyDynamicColors(palette);
//                            }
//                        });
                    }

                    @Override
                    public void onLoadCleared(Drawable placeholder) {
                        binding.albumArt.setImageDrawable(placeholder);
                        binding.blurredBackground.setImageResource(R.drawable.ic_outline_music_note_24);
                    }
                });
    }



    private void applyBlurredBackground(Bitmap originalBitmap) {
        try {
            Bitmap blurredBitmap = blurBitmap(originalBitmap, 25f);
            binding.blurredBackground.setImageBitmap(blurredBitmap);

        } catch (Exception e) {
            Log.e("NowPlayingActivity", "Error applying blur effect: " + e.getMessage(), e);
            binding.blurredBackground.setImageBitmap(originalBitmap);
            binding.blurredBackground.setAlpha(0.3f);
        }
    }

    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        Bitmap outputBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());

        RenderScript rs = RenderScript.create(this);

        try {
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));

            Allocation inputAllocation = Allocation.createFromBitmap(rs, bitmap);
            Allocation outputAllocation = Allocation.createFromBitmap(rs, outputBitmap);

            blurScript.setRadius(Math.min(25f, Math.max(1f, radius)));

            blurScript.setInput(inputAllocation);

            blurScript.forEach(outputAllocation);

            outputAllocation.copyTo(outputBitmap);

            inputAllocation.destroy();
            outputAllocation.destroy();
            blurScript.destroy();

        } catch (Exception e) {
            Log.e("NowPlayingActivity", "RenderScript blur failed: " + e.getMessage(), e);
            return bitmap;
        } finally {
            rs.destroy();
        }

        return outputBitmap;
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

        int whiteColor = Color.WHITE;
        int blackColor = Color.BLACK;

        binding.seekBar.setIndicatorColor(accentColor);
        binding.seekBar.setTrackColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0));

        // Apply colors to buttons
        binding.playPauseButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.playPauseButton.setTextColor(whiteColor);

        binding.previousButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        // For regular Button with compound drawable, use setCompoundDrawableTintList
        binding.previousButton.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(whiteColor));

        binding.nextButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.nextButton.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(whiteColor));

        binding.shuffleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.shuffleButton.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(whiteColor));

        binding.repeatButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.repeatButton.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(whiteColor));
    }

    private void setupClickListeners() {
        binding.playPauseButton.setOnClickListener(v -> togglePlayPause());
        binding.previousButton.setOnClickListener(v -> playPrevious());
        binding.nextButton.setOnClickListener(v -> playNext());
        binding.backButton.setOnClickListener(v -> onBackPressed());

        binding.shuffleButton.setOnClickListener(v -> toggleShuffle());
        binding.repeatButton.setOnClickListener(v -> toggleRepeat());
    }

    private void handleProgressTouch(float adjustedX, int usableWidth) {
        if (musicService != null && musicService.getMediaPlayer() != null) {
            MediaPlayer mediaPlayer = musicService.getMediaPlayer();

            float progressPercent = Math.max(0, Math.min(1, adjustedX / usableWidth));

            int newProgress = (int) (progressPercent * 100);
            binding.seekBar.setProgress(newProgress);

            int seekPosition = (int) (progressPercent * mediaPlayer.getDuration());
            binding.currentTime.setText(formatDuration(seekPosition));
        }
    }

    private void seekToPosition(float adjustedX, int usableWidth) {
        if (musicService != null && musicService.getMediaPlayer() != null) {
            MediaPlayer mediaPlayer = musicService.getMediaPlayer();

            float progressPercent = Math.max(0, Math.min(1, adjustedX / usableWidth));
            int seekPosition = (int) (progressPercent * mediaPlayer.getDuration());

            Intent serviceIntent = new Intent(this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_SEEK);
            serviceIntent.putExtra("seek_position", seekPosition);
            startService(serviceIntent);
        }
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
        String buttonText = isPlaying ? "PAUSE" : "PLAY";
        binding.playPauseButton.setText(buttonText);
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
                binding.repeatButton.setText("REPEAT");
                break;
            case MusicService.REPEAT_ALL:
                binding.repeatButton.setAlpha(1.0f);
                binding.repeatButton.setText("REPEAT");
                break;
            case MusicService.REPEAT_ONE:
                binding.repeatButton.setAlpha(1.0f);
                binding.repeatButton.setText("REPEAT ONE");
                break;
        }

        binding.repeatButton.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        binding.repeatButton.setCompoundDrawablePadding(0);
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
                            int progress = (int) (((float) currentPosition / duration) * 100);
                            binding.seekBar.setProgress(progress);
                            binding.currentTime.setText(formatDuration(currentPosition));
                        }
                    } catch (IllegalStateException e) {
                        // Handle media player state exceptions
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
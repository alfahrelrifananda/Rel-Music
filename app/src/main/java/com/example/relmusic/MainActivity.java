package com.example.relmusic;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.example.relmusic.databinding.ActivityMainBinding;
import com.example.relmusic.ui.music.MusicItem;
import com.example.relmusic.ui.pages.nowplaying.NowPlayingActivity;
import com.example.relmusic.service.MusicService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private TextView toolbarTitle;
    private CollapsingToolbarLayout collapsingToolbar;
    private String currentTitle = "RelMusic";

    private MaterialCardView miniPlayerContainer;
    private ImageView miniAlbumArt;
    private TextView miniSongTitle;
    private TextView miniArtistName;
    private MaterialButton miniPlayPauseButton;
    private MaterialButton miniNextButton;
    private MaterialButton miniCloseButton;

    private ObjectAnimator albumArtRotationAnimator;

    private MusicItem currentPlayingItem;
    private boolean isPlaying = false;
    private boolean isMiniPlayerVisible = false;
    private boolean isReceiverRegistered = false;
    private boolean isActivityDestroyed = false;

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

        try {
            enableEdgeToEdge();

            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            setupWindowInsets();

            if (!initializeToolbarComponents()) {
                Log.e(TAG, "Failed to initialize toolbar components");
                return;
            }

            if (!initializeMiniPlayer()) {
                Log.e(TAG, "Failed to initialize mini player components");
                return;
            }

            setupNavigation();
            setupToolbarActions();

            registerMusicUpdateReceiver();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            finish();
        }
    }

    private boolean initializeToolbarComponents() {
        try {
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            toolbarTitle = findViewById(R.id.toolbar_title_main);
            collapsingToolbar = findViewById(R.id.collapsing_toolbar);
            AppBarLayout appBarLayout = findViewById(R.id.app_bar_layout);

            if (toolbar == null || toolbarTitle == null || collapsingToolbar == null || appBarLayout == null) {
                Log.e(TAG, "One or more toolbar components are null");
                return false;
            }

            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }

            collapsingToolbar.setTitle(currentTitle);
            setupCollapsingToolbarTitleAnimation(appBarLayout);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing toolbar: " + e.getMessage(), e);
            return false;
        }
    }

    private void setupNavigation() {
        try {
            BottomNavigationView navView = findViewById(R.id.nav_view);
            if (navView == null) {
                Log.e(TAG, "BottomNavigationView is null");
                return;
            }

            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
            NavigationUI.setupWithNavController(binding.navView, navController);

            navController.addOnDestinationChangedListener(new NavController.OnDestinationChangedListener() {
                @Override
                public void onDestinationChanged(NavController controller, NavDestination destination, Bundle arguments) {
                    if (!isActivityDestroyed) {
                        updateTitle(destination.getId());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up navigation: " + e.getMessage(), e);
        }
    }

    private void setupToolbarActions() {
        try {
            MaterialButton refreshButton = findViewById(R.id.refresh_button);

            if (refreshButton != null) {
                refreshButton.setOnClickListener(v -> {
                    refreshMusicFragmentData();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar actions: " + e.getMessage(), e);
        }
    }

    private void refreshMusicFragmentData() {
        try {
            MaterialButton refreshButton = findViewById(R.id.refresh_button);

            if (refreshButton != null) {
                refreshButton.animate()
                        .rotation(360f)
                        .setDuration(1000)
                        .setInterpolator(new LinearInterpolator())
                        .withEndAction(() -> {
                            if (!isActivityDestroyed && refreshButton != null) {
                                refreshButton.setRotation(0f);
                            }
                        })
                        .start();

                refreshButton.setEnabled(false);
                new android.os.Handler().postDelayed(() -> {
                    if (!isActivityDestroyed && refreshButton != null) {
                        refreshButton.setEnabled(true);
                    }
                }, 2000);
            }

            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

            com.example.relmusic.ui.album.AlbumFragment.clearCache();

            if (navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() == R.id.navigation_music) {

                refreshCurrentFragment("MusicFragment");
                refreshAlbumFragmentInBackground();
                Toast.makeText(this, "Refreshing music and album library...", Toast.LENGTH_SHORT).show();

            } else if (navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() == R.id.navigation_album) {

                refreshCurrentFragment("AlbumFragment");
                refreshMusicFragmentInBackground();
                Toast.makeText(this, "Refreshing album and music library...", Toast.LENGTH_SHORT).show();

            } else {
                refreshAllFragments();
                Toast.makeText(this, "Refreshing music and album library...", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error refreshing fragments: " + e.getMessage(), e);
            Toast.makeText(this, "Error refreshing library", Toast.LENGTH_SHORT).show();

            MaterialButton refreshButton = findViewById(R.id.refresh_button);
            if (refreshButton != null) {
                refreshButton.setEnabled(true);
                refreshButton.setRotation(0f);
            }
        }
    }

    private void refreshCurrentFragment(String expectedFragmentType) {
        try {
            androidx.fragment.app.Fragment navHostFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_activity_main);

            if (navHostFragment != null) {
                androidx.fragment.app.Fragment currentFragment = navHostFragment.getChildFragmentManager()
                        .getPrimaryNavigationFragment();

                if (expectedFragmentType.equals("MusicFragment") &&
                        currentFragment instanceof com.example.relmusic.ui.music.MusicFragment) {

                    com.example.relmusic.ui.music.MusicFragment musicFragment =
                            (com.example.relmusic.ui.music.MusicFragment) currentFragment;
                    musicFragment.refreshData();

                } else if (expectedFragmentType.equals("AlbumFragment") &&
                        currentFragment instanceof com.example.relmusic.ui.album.AlbumFragment) {

                    com.example.relmusic.ui.album.AlbumFragment albumFragment =
                            (com.example.relmusic.ui.album.AlbumFragment) currentFragment;
                    albumFragment.refreshData();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing current fragment: " + e.getMessage(), e);
        }
    }

    private void refreshAlbumFragmentInBackground() {
        try {
            androidx.fragment.app.Fragment navHostFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_activity_main);

            if (navHostFragment != null) {
                java.util.List<androidx.fragment.app.Fragment> childFragments =
                        navHostFragment.getChildFragmentManager().getFragments();

                for (androidx.fragment.app.Fragment fragment : childFragments) {
                    if (fragment instanceof com.example.relmusic.ui.album.AlbumFragment) {
                        com.example.relmusic.ui.album.AlbumFragment albumFragment =
                                (com.example.relmusic.ui.album.AlbumFragment) fragment;
                        albumFragment.refreshData();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing album fragment in background: " + e.getMessage(), e);
        }
    }

    private void refreshMusicFragmentInBackground() {
        try {
            androidx.fragment.app.Fragment navHostFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_activity_main);

            if (navHostFragment != null) {
                java.util.List<androidx.fragment.app.Fragment> childFragments =
                        navHostFragment.getChildFragmentManager().getFragments();

                for (androidx.fragment.app.Fragment fragment : childFragments) {
                    if (fragment instanceof com.example.relmusic.ui.music.MusicFragment) {
                        com.example.relmusic.ui.music.MusicFragment musicFragment =
                                (com.example.relmusic.ui.music.MusicFragment) fragment;
                        musicFragment.refreshData();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing music fragment in background: " + e.getMessage(), e);
        }
    }

    private void refreshAllFragments() {
        try {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

            int currentDestination = navController.getCurrentDestination() != null ?
                    navController.getCurrentDestination().getId() : R.id.navigation_music;

            navController.navigate(R.id.navigation_music);

            new android.os.Handler().postDelayed(() -> {
                refreshCurrentFragment("MusicFragment");

                navController.navigate(R.id.navigation_album);

                new android.os.Handler().postDelayed(() -> {
                    refreshCurrentFragment("AlbumFragment");

                    if (currentDestination != R.id.navigation_album) {
                        new android.os.Handler().postDelayed(() -> {
                            try {
                                navController.navigate(currentDestination);
                            } catch (Exception e) {
                                Log.e(TAG, "Error navigating back: " + e.getMessage(), e);
                            }
                        }, 300);
                    }
                }, 300);
            }, 300);

        } catch (Exception e) {
            Log.e(TAG, "Error refreshing all fragments: " + e.getMessage(), e);
        }
    }

    private void enableEdgeToEdge() {
        try {
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                getWindow().setNavigationBarContrastEnforced(false);
            }

            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling edge-to-edge: " + e.getMessage(), e);
        }
    }

    private void setupWindowInsets() {
        try {
            View rootView = binding.getRoot();
            if (rootView == null) {
                return;
            }

            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                androidx.core.graphics.Insets navigationBar = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                return WindowInsetsCompat.CONSUMED;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up window insets: " + e.getMessage(), e);
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
                        .withEndAction(() -> {
                            if (!isActivityDestroyed) {
                                broadcastMiniPlayerVisibility(true);
                            }
                        })
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
                                broadcastMiniPlayerVisibility(false);
                            }
                        })
                        .start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding mini player: " + e.getMessage(), e);
        }
    }

    private void broadcastMiniPlayerVisibility(boolean isVisible) {
        if (isActivityDestroyed) {
            return;
        }

        try {
            Intent intent = new Intent("MINI_PLAYER_VISIBILITY_CHANGED");
            intent.putExtra("is_visible", isVisible);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting mini player visibility: " + e.getMessage(), e);
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

    public void startMusicService(MusicItem musicItem) {
        if (isActivityDestroyed || musicItem == null) {
            return;
        }

        try {
            Intent serviceIntent = new Intent(this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_PLAY);
            serviceIntent.putExtra("music_item", musicItem);
            startService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting music service: " + e.getMessage(), e);
        }
    }

    private void updateTitle(int destinationId) {
        if (isActivityDestroyed) {
            return;
        }

        try {
            if (destinationId == R.id.navigation_music) {
                currentTitle = "Music";
            } else if (destinationId == R.id.navigation_album) {
                currentTitle = "Albums";
            } else if (destinationId == R.id.navigation_settings) {
                currentTitle = "Settings";
            } else if (destinationId == R.id.navigation_artist) {
                currentTitle = "Artist";
            } else {
                currentTitle = "RelMusic";
            }

            if (toolbarTitle != null) {
                toolbarTitle.setText(currentTitle);
            }
            if (collapsingToolbar != null) {
                collapsingToolbar.setTitle(currentTitle);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating title: " + e.getMessage(), e);
        }
    }

    private void setupCollapsingToolbarTitleAnimation(AppBarLayout appBarLayout) {
        if (appBarLayout == null) {
            return;
        }

        try {
            appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
                @Override
                public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                    if (isActivityDestroyed || toolbarTitle == null || collapsingToolbar == null) {
                        return;
                    }

                    try {
                        int totalScrollRange = appBarLayout.getTotalScrollRange();
                        float percentage = Math.abs(verticalOffset) / (float) totalScrollRange;

                        if (percentage > 0.7f) {
                            toolbarTitle.setVisibility(View.VISIBLE);
                            float alpha = (percentage - 0.7f) / 0.3f;
                            toolbarTitle.setAlpha(alpha);
                            collapsingToolbar.setTitle("");
                        } else {
                            toolbarTitle.setVisibility(View.INVISIBLE);
                            toolbarTitle.setAlpha(0f);
                            collapsingToolbar.setTitle(currentTitle);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in toolbar animation: " + e.getMessage(), e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar animation: " + e.getMessage(), e);
        }
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

        try {
            currentPlayingItem = null;
            musicUpdateReceiver = null;

            if (!isDestroyed()) {
                Glide.with(this).clear(miniAlbumArt);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing references: " + e.getMessage(), e);
        }
    }
}
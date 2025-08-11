package com.example.relmusic.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.relmusic.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        SettingsViewModel settingsViewModel =
                new ViewModelProvider(this).get(SettingsViewModel.class);

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupCardListeners();

        return root;
    }

    private void setupCardListeners() {
        binding.scanFoldersCard.setOnClickListener(v -> {
            // TODO: Implement scan folders functionality
        });

        binding.themeCard.setOnClickListener(v -> {
            // TODO: Implement theme selection functionality
        });

        binding.helpCard.setOnClickListener(v -> {
            // TODO: Implement help & FAQ functionality
        });

        binding.feedbackCard.setOnClickListener(v -> {
            // TODO: Implement send feedback functionality
        });

        binding.aboutCard.setOnClickListener(v -> {
            // TODO: Implement about page functionality
        });

        binding.privacyCard.setOnClickListener(v -> {
            // TODO: Implement privacy policy functionality
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
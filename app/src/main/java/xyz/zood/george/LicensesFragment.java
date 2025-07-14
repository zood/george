package xyz.zood.george;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.text.util.Linkify;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.pijun.george.L;
import xyz.zood.george.databinding.FragmentLicensesBinding;
import xyz.zood.george.databinding.FragmentSettingsBinding;

public class LicensesFragment extends Fragment implements LibrariesAdapter.LibrariesAdapterListener {

    public LicensesFragment() {}

    public static LicensesFragment newInstance() {
        return new LicensesFragment();
    }

    // applySystemUIInsets applies the system view insets. It should only be called once after the fragment's view is created.
    private void applySystemUIInsets(FragmentLicensesBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int statusBarHeight;
            statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            ConstraintLayout.LayoutParams sbLP = (ConstraintLayout.LayoutParams) binding.statusBarPlaceholder.getLayoutParams();
            sbLP.height = statusBarHeight;
            binding.statusBarPlaceholder.setLayoutParams(sbLP);

            return insets;
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentLicensesBinding binding = FragmentLicensesBinding.inflate(inflater, container, false);
        binding.librariesList.setAdapter(new LibrariesAdapter(this));
        binding.back.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        applySystemUIInsets(binding);

        return binding.getRoot();
    }

    //region LibrariesAdapterListener

    @Override
    public void onLibraryItemSelected(Pair<String, Integer> libItem) {
        LicenseTextFragment f = LicenseTextFragment.newInstance(libItem.second);
        FragmentManager mgr = getParentFragmentManager();
        mgr.beginTransaction()
                .setCustomAnimations(R.animator.new_enter_from_right,
                        R.animator.new_exit_to_left,
                        R.animator.new_enter_from_left,
                        R.animator.new_exit_to_right)
                .replace(R.id.fragment_host, f)
                .addToBackStack(null)
                .commit();
    }
}
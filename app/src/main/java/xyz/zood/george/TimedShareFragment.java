package xyz.zood.george;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import io.pijun.george.L;
import io.pijun.george.service.TimedShareService;
import xyz.zood.george.databinding.FragmentTimedShareBinding;
import xyz.zood.george.time.HoursMinutesSeconds;
import xyz.zood.george.viewmodels.Event;
import xyz.zood.george.viewmodels.MainViewModel;

public class TimedShareFragment extends Fragment {

    private enum Position {
        Hidden,
        Peeking,
        Expanded
    }

    private FragmentTimedShareBinding binding;
    private boolean ignoreNextToggle = false;
    private boolean isDown = false;
    private boolean isRunning = false;
    private float totalOffset = 0;
    private boolean skipSettle = false;
    private MainViewModel viewModel;
    private float peekHeight;

    @UiThread
    private void close() {
        if (isRunning) {
            setPosition(Position.Peeking);
        } else {
            setPosition(Position.Hidden);
        }
    }

    private boolean isSheetHidden() {
        if (binding == null) {
            return true;
        }
        return binding.sheet.getTranslationY() == 0;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        peekHeight = -getResources().getDimensionPixelSize(R.dimen.timed_share_sheet_peek_height);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timed_share, container, false);

        // Add rounded corners to the top of the sheet
        binding.sheet.setClipToOutline(true);
        int eightDp = getResources().getDimensionPixelSize(R.dimen.eight);
        binding.sheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight()+eightDp, eightDp);
            }
        });
        // Add rounded corners on the thumb decoration
        binding.thumb.setClipToOutline(true);
        int twoDp = getResources().getDimensionPixelSize(R.dimen.two);
        binding.thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), twoDp);
            }
        });

        // Set up the gesture detector to handle moving the sheet up and down
        GestureDetectorCompat detector = new GestureDetectorCompat(requireContext(), gestureListener);
        detector.setIsLongpressEnabled(false);
        binding.sheet.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isDown && event.getAction() == MotionEvent.ACTION_UP) {
                    isDown = false;
                    settle();
                }

                return detector.onTouchEvent(event);
            }
        });

        // hook up the buttons
        binding.close.setOnClickListener(v -> close());
        binding.toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (ignoreNextToggle) {
                    ignoreNextToggle = false;
                    return;
                }

                if (isChecked) {
                    startTimedShare();
                } else {
                    Context ctx = requireContext();
                    ctx.stopService(new Intent(ctx, TimedShareService.class));
                }
            }
        });
        binding.copyLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = viewModel.getTimedShareUrl().getValue();
                ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    ClipData clipData = ClipData.newRawUri("Zood Location URL", Uri.parse(url));
                    cm.setPrimaryClip(clipData);
                    // create a toast to let the user know it's done
                    Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        });
        binding.shareLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = viewModel.getTimedShareUrl().getValue();
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, url);
                sendIntent.setType("text/plain");
                startActivity(Intent.createChooser(sendIntent, requireContext().getString(R.string.send_to)));
            }
        });
        binding.addTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimedShareService svc = TimedShareService.get();
                if (svc == null) {
                    return;
                }
                Long remainingMs = viewModel.getTimedShareTimeRemaining().getValue();
                if (remainingMs == null) {
                    return;
                }
                long remainingMins = remainingMs / DateUtils.MINUTE_IN_MILLIS;
                long newDuration = remainingMs;
                if (remainingMins < 20) {
                    newDuration += 5 * DateUtils.MINUTE_IN_MILLIS;
                } else if (remainingMins < 60) {
                    newDuration += 10 * DateUtils.MINUTE_IN_MILLIS;
                } else {
                    newDuration += DateUtils.HOUR_IN_MILLIS;
                }
                svc.updateDuration(newDuration);
            }
        });
        binding.subtractTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimedShareService svc = TimedShareService.get();
                if (svc == null) {
                    return;
                }
                Long remainingMs = viewModel.getTimedShareTimeRemaining().getValue();
                if (remainingMs == null) {
                    return;
                }
                long remainingMins = remainingMs / DateUtils.MINUTE_IN_MILLIS;
                if (remainingMins <= 1) {
                    Context ctx = requireContext();
                    ctx.stopService(new Intent(ctx, TimedShareService.class));
                    return;
                }
                long newDuration = remainingMs;
                if (remainingMins > 120) {
                    newDuration -= DateUtils.HOUR_IN_MILLIS;
                } else if (remainingMins > 60) {
                    newDuration -= 30 * DateUtils.MINUTE_IN_MILLIS;
                } else if (remainingMins > 20) {
                    newDuration -= 10 * DateUtils.MINUTE_IN_MILLIS;
                } else if (remainingMins > 10) {
                    newDuration -= 5 * DateUtils.MINUTE_IN_MILLIS;
                } else {
                    newDuration -= DateUtils.MINUTE_IN_MILLIS;
                }
                svc.updateDuration(newDuration);
            }
        });

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        viewModel.getTimedShareIsRunning().observe(getViewLifecycleOwner(), this::setTimedShareRunning);
        viewModel.getOnTimedShareClicked().observe(getViewLifecycleOwner(), clickEvent -> {
            Boolean didClick = clickEvent.getEventIfNotHandled();
            if (didClick == null) {
                return;
            }
            onTimedShareFabClicked();
        });
        viewModel.getTimedShareTimeRemaining().observe(getViewLifecycleOwner(), this::setTimeRemaining);
        viewModel.getOnCloseTimedSheet().observe(getViewLifecycleOwner(), new Observer<Event<Boolean>>() {
            @Override
            public void onChanged(Event<Boolean> booleanEvent) {
                close();
            }
        });

        return binding.sheet;
    }

    @Override
    public void onDestroyView() {
        binding = null;

        super.onDestroyView();
    }

    @UiThread
    private void onTimedShareFabClicked() {
        setPosition(Position.Expanded);
    }

    @UiThread
    private void setPosition(@NonNull Position position) {
        switch (position) {
            case Expanded:
                totalOffset = -binding.sheet.getHeight();
                viewModel.setTimedShareSheetDismissable(true);
                break;
            case Hidden:
                totalOffset = 0;
                viewModel.setTimedShareSheetDismissable(false);
                break;
            case Peeking:
                totalOffset = -getResources().getDimensionPixelSize(R.dimen.timed_share_sheet_peek_height);
                viewModel.setTimedShareSheetDismissable(false);
                break;
        }

        updateBannerViews(totalOffset, true);
        binding.sheet.animate().translationY(totalOffset);
    }

    @UiThread
    private void setTimeRemaining(long seconds) {
        if (seconds < 1) {
            binding.timeRemaining.setText(R.string.off);
            binding.burnDown.setTimeRemaining(0);
        } else {
            HoursMinutesSeconds hm = new HoursMinutesSeconds(seconds);
            if (hm.hours > 0) {
                binding.timeRemaining.setText(getString(R.string.hours_abbreviated_msg, hm.hours));
            } else if (hm.minutes > 0) {
                binding.timeRemaining.setText(getString(R.string.minutes_abbreviated_msg, hm.minutes));
            } else {
                binding.timeRemaining.setText(getString(R.string.seconds_abbreviated_msg, hm.seconds));
            }

            float remaining = (float)seconds / (float)viewModel.getTimedShareDuration();
            binding.burnDown.setTimeRemaining(remaining);
        }
    }

    @UiThread
    private void setTimedShareRunning(boolean isRunning) {
        this.isRunning = isRunning;

        // Check if our updating of the toggle will change anything. If so,
        // tell the onCheck listener to ignore it.
        if (binding.toggle.isChecked() != this.isRunning) {
            ignoreNextToggle = true;
            binding.toggle.setChecked(this.isRunning);
        }
        binding.copyLink.setEnabled(this.isRunning);
        binding.shareLink.setEnabled(this.isRunning);
        binding.subtractTime.setEnabled(this.isRunning);
        binding.addTime.setEnabled(this.isRunning);
        binding.timeRemaining.setEnabled(this.isRunning);
        binding.burnDown.setEnabled(this.isRunning);
        binding.thumb.setEnabled(this.isRunning);

        if (this.isRunning) {
            binding.mapTexture.setImageResource(R.drawable.ic_timed_share_map_texture_on);
            // If the sheet is not in view, have it peek out
            if (isSheetHidden()) {
                setPosition(Position.Peeking);
            }
        } else {
            binding.mapTexture.setImageResource(R.drawable.ic_timed_share_map_texture_off);
            setPosition(Position.Hidden);
        }
        updateBannerViews(binding.sheet.getTranslationY(), false);
    }

    @UiThread
    private void settle() {
        if (skipSettle) {
            skipSettle = false;
            return;
        }
        float offset = binding.sheet.getTranslationY();
        float midOffset = -binding.sheet.getHeight() / 2.0f;
        if (offset < midOffset) {
            setPosition(Position.Expanded);
        } else {
            if (isRunning) {
                setPosition(Position.Peeking);
            } else {
                setPosition(Position.Hidden);
            }
        }
    }

    @UiThread
    private void startTimedShare() {
        Context ctx = requireContext();
        if (!Permissions.checkForegroundLocationPermission(ctx)) {
            Toast.makeText(ctx, R.string.location_permission_needed, Toast.LENGTH_LONG).show();
            binding.toggle.setChecked(false);
            return;
        }
        Intent i = TimedShareService.newIntent(ctx, TimedShareService.ACTION_START);
        ContextCompat.startForegroundService(ctx, i);
    }

    @UiThread
    private void updateBannerViews(float translationY, boolean animate) {
        if (!isRunning) {
            if (animate) {
                binding.burnDown.animate().alpha(1.0f);
                binding.timeRemaining.animate().translationY(0);
                binding.subtractTime.animate().translationY(0).translationX(0);
                binding.addTime.animate().translationY(0).translationX(0);
            } else {
                binding.burnDown.setAlpha(1.0f);
                binding.timeRemaining.setTranslationY(0);
                binding.subtractTime.setTranslationY(0);
                binding.subtractTime.setTranslationX(0);
                binding.addTime.setTranslationY(0);
                binding.addTime.setTranslationX(0);
            }
            return;
        }

        float minY = -binding.sheet.getHeight();
        float range = Math.abs(minY) - Math.abs(peekHeight);
        float progress = 1.0f - (peekHeight - translationY)/range;
        progress = Math.min(progress, 1.0f);
        float verticalShift = -getResources().getDimensionPixelSize(R.dimen.twentyFour);
        verticalShift *= progress;
        float horizontalShift = getResources().getDimensionPixelSize(R.dimen.eight);
        horizontalShift *= progress;

        if (animate) {
            binding.burnDown.animate().alpha(1.0f - progress);
            binding.timeRemaining.animate().translationY(verticalShift);
            binding.subtractTime.animate().translationY(verticalShift).translationX(horizontalShift);
            binding.addTime.animate().translationY(verticalShift).translationX(-horizontalShift);
        } else {
            binding.burnDown.setAlpha(1.0f - progress);
            binding.timeRemaining.setTranslationY(verticalShift);
            binding.subtractTime.setTranslationY(verticalShift);
            binding.subtractTime.setTranslationX(horizontalShift);
            binding.addTime.setTranslationY(verticalShift);
            binding.addTime.setTranslationX(-horizontalShift);
        }
    }

    private final GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {

        /*
        The accumulator exists because when we move the sheet, future events from the gesture
        detector include the amount that we moved the sheet. For example, without the accumulator:
        1) We get an onScroll with a distanceY of -50, so we translate the sheet by 50.
        2) On the next move event, the gesture detector looks at the position of the gesture
        relative to the view's NEW position, so if the user finger moved an extra -10, the gesture
        event would report a change of 40, because (-10 + 50).

        The accumulator just remembers what the previous distanceY was and removes that amount
        from future gesture events, so we don't get a sheet that's always bouncing back and forth.
        */
        float accumulator;

        @Override
        public boolean onDown(MotionEvent e) {
            // We have to intercept this in order for the detector to continue trying to detect
            // a scroll or a fling
            isDown = true;
            accumulator = 0;
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//            L.i("onFling - velY: " + velocityY);
//            isDown = false;
//            skipSettle = true;
//            if (velocityY < 0) {
//                close();
//            } else {
//                setPosition(Position.Expanded);
//            }
//            return true;
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            distanceY += accumulator;
            accumulator = distanceY;
            totalOffset -= distanceY;

            // we don't want to go beyond the 'expanded' position, so we use Math.max
            float transY = Math.max(totalOffset, -binding.sheet.getHeight());
            updateBannerViews(transY, false);
            binding.sheet.setTranslationY(transY);
            viewModel.setTimedShareSheetDismissable(false);

            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            L.i("single tap up");
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            L.i("single tap confirmed");
            return true;
        }
    };
}

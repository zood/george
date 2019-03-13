package xyz.zood.george.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import xyz.zood.george.R;

public class RadialMenu {

    private float oneDip;
    private float twoDips;
    private boolean isExpanded = false;
    private float openLength;
    private RadialMenuButton addFriend;
    private RadialMenuButton limitedShare;
    private final RadialMenuButton primary;
    private RadialMenuButton settings;
    private final float velocity = 1000;
    private final float stiffness = SpringForce.STIFFNESS_MEDIUM;
    private final float dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY;
    private boolean isVisible = true;
    private final ArrayList<RadialMenuButton> buttons = new ArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    public RadialMenu(@NonNull ViewGroup root) {
        Context context = root.getContext();
        primary = root.findViewById(R.id.radial_primary);
        if (primary == null) {
            throw new IllegalArgumentException("This root does not contain the radial primary button");
        }
        settings = root.findViewById(R.id.radial_settings);
        if (settings == null) {
            throw new IllegalArgumentException("This root does not contain the radial settings button");
        }
        addFriend = root.findViewById(R.id.radial_add_friend);
        if (addFriend == null) {
            throw new IllegalArgumentException("This root does not contain the radial add friend button");
        }
        limitedShare = root.findViewById(R.id.radial_limited_share);
        if (limitedShare == null) {
            throw new IllegalArgumentException("This root does not contain the radial limited share button");
        }
        buttons.add(primary);
        buttons.add(settings);
        buttons.add(addFriend);
        buttons.add(limitedShare);

        oneDip = context.getResources().getDimension(R.dimen.four);
        twoDips = context.getResources().getDimension(R.dimen.eight);
        openLength = context.getResources().getDimension(R.dimen.radial_expanded);

        primary.setOnPressListener(new RadialMenuButton.OnPressListener() {
            @Override
            public void onRadialButtonDown() {
                new SpringAnimation(primary, DynamicAnimation.TRANSLATION_Z)
                        .animateToFinalPosition(twoDips);

                if (isExpanded) {
                    flyBack();
                } else {
                    flyOut();
                }
            }

            @Override
            public void onRadialButtonUp() {
                new SpringAnimation(primary, DynamicAnimation.TRANSLATION_Z)
                        .animateToFinalPosition(oneDip);
            }
        });
        primary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                if (isExpanded) {
//                    flyBack();
//                } else {
//                    flyOut();
//                }
            }
        });

        settings.setOnPressListener(new RadialMenuButton.OnPressListener() {
            @Override
            public void onRadialButtonDown() {
                new SpringAnimation(settings, DynamicAnimation.TRANSLATION_Z)
                        .animateToFinalPosition(twoDips);
            }

            @Override
            public void onRadialButtonUp() {
                new SpringAnimation(settings, DynamicAnimation.TRANSLATION_Z)
                        .animateToFinalPosition(oneDip);
            }
        });

        addFriend.setOnPressListener(new RadialMenuButton.OnPressListener() {
            @Override
            public void onRadialButtonDown() {
                new SpringAnimation(addFriend, DynamicAnimation.TRANSLATION_Z)
                        .animateToFinalPosition(twoDips);
            }

            @Override
            public void onRadialButtonUp() {
                new SpringAnimation(addFriend, DynamicAnimation.TRANSLATION_Z)
                        .animateToFinalPosition(oneDip);
            }
        });

        limitedShare.setOnPressListener(new RadialMenuButton.OnPressListener() {
            @Override
            public void onRadialButtonDown() {
                new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_Z)
                        .animateToFinalPosition(twoDips);
            }

            @Override
            public void onRadialButtonUp() {
                new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_Z)
                        .animateToFinalPosition(oneDip);
            }
        });
    }

    public void flyBack() {
        if (!isExpanded) {
            return;
        }
        isExpanded = false;

        SpringForce xySpring = new SpringForce(0)
                .setDampingRatio(dampingRatio)
                .setStiffness(stiffness);
        SpringForce zSpring = new SpringForce(0)
                .setDampingRatio(dampingRatio)
                .setStiffness(stiffness);

        // primary
        new SpringAnimation(primary, DynamicAnimation.ROTATION)
                .setStartVelocity(velocity)
                .animateToFinalPosition(0f);

        // settings
        new SpringAnimation(settings, DynamicAnimation.TRANSLATION_X)
                .setSpring(xySpring)
                .setStartVelocity(velocity)
                .start();
        new SpringAnimation(settings, DynamicAnimation.TRANSLATION_Z)
                .setSpring(zSpring)
                .start();

        // add friend
        new SpringAnimation(addFriend, DynamicAnimation.TRANSLATION_Y)
                .setSpring(xySpring)
                .setStartVelocity(velocity)
                .start();
        new SpringAnimation(addFriend, DynamicAnimation.TRANSLATION_Z)
                .setSpring(zSpring)
                .start();

        // limited share
        new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_X)
                .setSpring(xySpring)
                .setStartVelocity(velocity)
                .start();
        new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_Y)
                .setSpring(xySpring)
                .setStartVelocity(velocity)
                .start();
        new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_Z)
                .setSpring(zSpring)
                .start();
    }

    private void flyOut() {
        if (isExpanded) {
            return;
        }
        isExpanded = true;

        SpringForce xySpring = new SpringForce(-openLength)
                .setDampingRatio(dampingRatio)
                .setStiffness(stiffness);
        SpringForce zSpring = new SpringForce(oneDip)
                .setDampingRatio(dampingRatio)
                .setStiffness(stiffness);

        // primary
        new SpringAnimation(primary, DynamicAnimation.ROTATION)
                .animateToFinalPosition(-90f);


        // settings
        new SpringAnimation(settings, DynamicAnimation.TRANSLATION_X)
                .setSpring(xySpring)
                .setStartVelocity(velocity)
                .start();
        new SpringAnimation(settings, DynamicAnimation.TRANSLATION_Z)
                .setSpring(zSpring)
                .start();

        // add friend
        new SpringAnimation(addFriend, DynamicAnimation.TRANSLATION_Y)
                .setSpring(xySpring)
                .setStartVelocity(velocity)
                .start();
        new SpringAnimation(addFriend, DynamicAnimation.TRANSLATION_Z)
                .setSpring(zSpring)
                .start();

        // limited share
        SpringForce diagSpring = new SpringForce(-openLength * 0.70710678f)   // multiply by √2/2
                .setDampingRatio(dampingRatio)
                .setStiffness(stiffness);
        new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_Y)
                .setSpring(diagSpring)
                .setStartVelocity(velocity)
                .start();
        new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_X)
                .setSpring(diagSpring)
                .setStartVelocity(velocity)
                .start();
        new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_Z)
                .setSpring(zSpring)
                .start();
    }

    public void setVisible(boolean visible) {
        if (visible == isVisible) {
            return;
        }

        isVisible = visible;
        if (isVisible) {
            SpringForce xSpring = new SpringForce(0)
                    .setDampingRatio(dampingRatio)
                    .setStiffness(stiffness);
            new SpringAnimation(primary, DynamicAnimation.TRANSLATION_X)
                    .setSpring(xSpring)
                    .setStartVelocity(velocity)
                    .start();
            new SpringAnimation(addFriend, DynamicAnimation.TRANSLATION_X)
                    .setSpring(xSpring)
                    .setStartVelocity(velocity)
                    .start();
            new SpringAnimation(limitedShare, DynamicAnimation.TRANSLATION_X)
                    .setSpring(xSpring)
                    .setStartVelocity(velocity)
                    .start();
            new SpringAnimation(settings, DynamicAnimation.TRANSLATION_X)
                    .setSpring(xSpring)
                    .setStartVelocity(velocity)
                    .start();
        } else {
            flyBack();
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) primary.getLayoutParams();
            float xOffset = params.getMarginEnd() + primary.getWidth();
            SpringForce xSpring = new SpringForce(xOffset)
                    .setDampingRatio(dampingRatio)
                    .setStiffness(stiffness);

            for (RadialMenuButton b : buttons) {
                new SpringAnimation(b, DynamicAnimation.TRANSLATION_X)
                        .setSpring(xSpring)
                        .setStartVelocity(velocity)
                        .start();
            }
        }
    }

}

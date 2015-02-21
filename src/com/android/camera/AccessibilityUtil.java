package com.android.camera;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import com.android.camera.debug.Log;
import com.android.camera.ui.MainActivityLayout;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.util.AndroidServices;
import com.android.camera2.R;

import java.util.List;

/**
 * AccessibilityUtil provides methods for use when the device is in
 * accessibility mode
 */
public class AccessibilityUtil {
    private final static Log.Tag TAG = new Log.Tag("AccessibilityUtil");
    private static final float MIN_ZOOM = 1f;

    // Filters for Google accessibility services
    private static final String ACCESSIBILITY_PACKAGE_NAME_PREFIX = "com.google";
    private PreviewOverlay mPreviewOverlay;
    private Button mZoomPlusButton;
    private Button mZoomMinusButton;
    private float mMaxZoom;

    private View.OnClickListener zoomInListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            float currentZoom = mPreviewOverlay.zoomIn(view, mMaxZoom);

            // Zooming in implies that you must be able to subsequently zoom
            // out.
            mZoomMinusButton.setEnabled(true);

            // Make sure it doesn't go above max zoom.
            if (currentZoom == mMaxZoom) {
                mZoomPlusButton.setEnabled(false);
            }
        }
    };

    private View.OnClickListener zoomOutListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            float currentZoom = mPreviewOverlay.zoomOut(view, mMaxZoom);

            // Zooming out implies that you must be able to subsequently zoom
            // in.
            mZoomPlusButton.setEnabled(true);

            // Make sure it doesn't go below min zoom.
            if (currentZoom == MIN_ZOOM) {
                mZoomMinusButton.setEnabled(false);
            }
        }
    };

    public AccessibilityUtil(PreviewOverlay previewOverlay, View view) {
        mPreviewOverlay = previewOverlay;
        mZoomPlusButton = (Button) view.findViewById(R.id.accessibility_zoom_plus_button);
        mZoomMinusButton = (Button) view.findViewById(R.id.accessibility_zoom_minus_button);
        mZoomPlusButton.setOnClickListener(zoomInListener);
        mZoomMinusButton.setOnClickListener(zoomOutListener);
    }

    /**
     * Enables the zoom UI with zoom in/zoom out buttons.
     *
     * @param maxZoom is maximum zoom on the given device
     */
    public void showZoomUI(float maxZoom) {
        mMaxZoom = maxZoom;
        mZoomPlusButton.setVisibility(View.VISIBLE);
        mZoomMinusButton.setVisibility(View.VISIBLE);
        mZoomMinusButton.setEnabled(false);
    }

    public void hideZoomUI() {
        mZoomPlusButton.setVisibility(View.GONE);
        mZoomMinusButton.setVisibility(View.GONE);
    }

    /**
     * Returns the accessibility manager.
     */
    private android.view.accessibility.AccessibilityManager getAccessibilityManager() {
        return AndroidServices.instance().provideAccessibilityManager();
    }

    /**
     * Returns whether touch exploration is enabled.
     */
    private boolean isTouchExplorationEnabled() {
        android.view.accessibility.AccessibilityManager accessibilityManager = getAccessibilityManager();
        return accessibilityManager.isTouchExplorationEnabled();
    }

    /**
     * Checks whether Google accessibility services are enabled, including
     * TalkBack, Switch Access, and others
     *
     * @return boolean
     */
    private boolean containsGoogleAccessibilityService() {
        android.view.accessibility.AccessibilityManager accessibilityManager = getAccessibilityManager();
        List<AccessibilityServiceInfo> enabledServices =
                accessibilityManager
                        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (enabledServices != null) {
            for (AccessibilityServiceInfo enabledService : enabledServices) {
                String serviceId = enabledService.getId();
                if (serviceId != null && serviceId.startsWith(ACCESSIBILITY_PACKAGE_NAME_PREFIX)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether a touch exploration is enabled or a Google accessibility
     * service is enabled.
     */
    public boolean isAccessibilityEnabled() {
        return isTouchExplorationEnabled()
                || containsGoogleAccessibilityService();
    }
}

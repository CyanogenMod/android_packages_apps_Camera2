// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.canvas.data.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;

/**
 * Utilities for working with URIs.
 */
public final class UriUtils {

    private static final String SCHEME_SHORTCUT_ICON_RESOURCE = "shortcut.icon.resource";
    private static final String SCHEME_DELIMITER = "://";
    private static final String URI_PATH_DELIMITER = "/";
    private static final String URI_PACKAGE_DELIMITER = ":";
    private static final String HTTP_PREFIX = "http";
    private static final String HTTPS_PREFIX = "https";

    /**
     * Non instantiable.
     */
    private UriUtils() {}

    /**
     * get resource uri representation for a resource of a package
     */
    public static String getAndroidResourceUri(Context context, int resourceId) {
        return getAndroidResourceUri(context.getResources(), resourceId);
    }

    /**
     * get resource uri representation for a resource
     */
    public static String getAndroidResourceUri(Resources resources, int resourceId) {
        return ContentResolver.SCHEME_ANDROID_RESOURCE
                + SCHEME_DELIMITER + resources.getResourceName(resourceId)
                        .replace(URI_PACKAGE_DELIMITER, URI_PATH_DELIMITER);
    }

    /**
     * load drawable from resource
     * TODO: move to a separate class to handle bitmap and drawables
     */
    public static Drawable getDrawable(Context context, ShortcutIconResource r)
            throws NameNotFoundException {
        Resources resources = context.getPackageManager().getResourcesForApplication(r.packageName);
        if (resources == null) {
            return null;
        }
        final int id = resources.getIdentifier(r.resourceName, null, null);
        return resources.getDrawable(id);
    }

    /**
     * Gets a URI with short cut icon scheme.
     */
    public static Uri getShortcutIconResourceUri(ShortcutIconResource iconResource) {
        return Uri.parse(SCHEME_SHORTCUT_ICON_RESOURCE + SCHEME_DELIMITER + iconResource.packageName
                + URI_PATH_DELIMITER
                + iconResource.resourceName.replace(URI_PACKAGE_DELIMITER, URI_PATH_DELIMITER));
    }

    /**
     * Gets a URI with scheme = {@link ContentResolver#SCHEME_ANDROID_RESOURCE}.
     */
    public static Uri getAndroidResourceUri(String resourceName) {
        Uri uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + SCHEME_DELIMITER
                + resourceName.replace(URI_PACKAGE_DELIMITER, URI_PATH_DELIMITER));
        return uri;
    }

    /**
     * Checks if the URI refers to an Android resource.
     */
    public static boolean isAndroidResourceUri(Uri uri) {
        return ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme());
    }

    /**
     * Checks if the URI refers to an shortcut icon resource.
     */
    public static boolean isShortcutIconResourceUri(Uri uri) {
        return SCHEME_SHORTCUT_ICON_RESOURCE.equals(uri.getScheme());
    }

    /**
     * Creates a shortcut icon resource object from an Android resource URI.
     */
    public static ShortcutIconResource getIconResource(Uri uri) {
        if(isAndroidResourceUri(uri)) {
            ShortcutIconResource iconResource = new ShortcutIconResource();
            iconResource.packageName = uri.getAuthority();
            // Trim off the scheme + 3 extra for "://", then replace the first "/" with a ":"
            iconResource.resourceName = uri.toString().substring(
                    ContentResolver.SCHEME_ANDROID_RESOURCE.length() + SCHEME_DELIMITER.length())
                    .replaceFirst(URI_PATH_DELIMITER, URI_PACKAGE_DELIMITER);
            return iconResource;
        } else if(isShortcutIconResourceUri(uri)) {
            ShortcutIconResource iconResource = new ShortcutIconResource();
            iconResource.packageName = uri.getAuthority();
            iconResource.resourceName = uri.toString().substring(
                    SCHEME_SHORTCUT_ICON_RESOURCE.length() + SCHEME_DELIMITER.length()
                    + iconResource.packageName.length() + URI_PATH_DELIMITER.length())
                    .replaceFirst(URI_PATH_DELIMITER, URI_PACKAGE_DELIMITER);
            return iconResource;
        } else {
            throw new IllegalArgumentException("Invalid resource URI. " + uri);
        }
    }

    /**
     * Returns {@code true} if this is a web URI.
     */
    public static boolean isWebUri(Uri resourceUri) {
        String scheme = resourceUri.getScheme().toLowerCase();
        return HTTP_PREFIX.equals(scheme) || HTTPS_PREFIX.equals(scheme);
    }
}

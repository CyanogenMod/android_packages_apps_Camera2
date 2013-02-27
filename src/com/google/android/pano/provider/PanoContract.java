package com.google.android.pano.provider;

import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * The contract between Pano and ContentProviders that allow access to Pano
 * browsing data. All apps that wish to interact with Pano should use these
 * definitions.
 *
 * TODO add more details
 */
public final class PanoContract {

    // Base for content uris
    public static final String CONTENT = "content://";
    /**
     * Path to items within a single cluster in the launcher. This can be used
     * when setting up a UriMatcher. Queries will use the form
     * content://<authority>/<path>/<#> where the # is the _id of the cluster
     * being queried
     */
    public static final String PATH_LAUNCHER_ITEM = "items";
    /**
     * Path to the header meta-data. This can be used when setting up a
     * UriMatcher. Queries will use the form content://<authority>/<path>
     */
    public static final String PATH_BROWSE_HEADERS = "headers";

    /**
     * Pano will search for activities with the action {@link Intent#ACTION_MAIN} and this
     * category in order to find the activities for the home screen.
     */
    public static final String CATEGORY_BROWSE_LAUNCHER =
            "com.google.android.pano.category.BROWSE_LAUNCHER";

    /**
     * This tag is used to identify the launcher info data file to be used for a Pano
     * launcher app.
     */
    public static final String METADATA_LAUNCHER_INFO_TAG =
            "com.google.android.pano.data.launcher_info";

    /**
     * An intent action for browsing app content in Pano. Apps receiving this
     * intent should call {@link Intent#getData()} to retrieve the base Uri and
     * {@link #EXTRA_START_INDEX} or {@link #EXTRA_START_ID} to find which header
     * to start at (default 0).
     */
    public static final String ACTION_BROWSE = "com.google.android.pano.action.BROWSE";

    /**
     * An intent action for picking app content in Pano.
     * <p>
     * Any intents launched from here will be returned to the calling activity instead of being
     * directly launched.
     * <p>
     * This can be used to select an item.
     */
    public static final String ACTION_BROWSE_PICKER =
            "com.google.android.pano.action.BROWSE_PICKER";

    /**
     * An intent action for picking app content while keeping the launching app running.
     * <p>
     * Pano Browse will appear over the app.
     * <p>
     * Any intents launched from here will be returned to the calling activity instead of being
     * directly launched.
     * <p>
     * This can be used to select an item.
     */
    public static final String ACTION_BROWSE_PICKER_TRANSLUCENT =
            "com.google.android.pano.action.BROWSE_PICKER_TRANSLUCENT";

    /**
     * The index of the header to focus on initially when the browse is launched.
     * This extra is optional and defaults to 0. If {@link #EXTRA_START_ID} is present
     * this value will not be used.
     */
    public static final String EXTRA_START_INDEX = "start_index";

    /**
     * The _id of the header to focus on initially when the browse is launched.
     * This extra is optional and {@link #EXTRA_START_ID} is used by default.
     */
    public static final String EXTRA_START_ID = "start_id";

    /**
     * An intent action for viewing detail content in Pano. Apps receiving this
     * intent should call {@link Intent#getData()} to retrieve the base Uri.
     */
    public static final String ACTION_DETAIL = "com.google.android.pano.action.DETAIL";

    /**
     * The name of the section to focus on initially when {@link #ACTION_DETAIL} is launched.
     * <p>
     * Using name allows targeting a sub section.
     */
    public static final String EXTRA_START_NAME = "start_name";

    /**
     * Index of a child to focus on initially when {@link #ACTION_DETAIL} is launched.
     * <p>
     * Requires a start section to be specified using {@link #EXTRA_START_INDEX} or
     * {@link #EXTRA_START_NAME}.
     */
    public static final String EXTRA_START_CHILD_INDEX = "start_child_index";

    /**
     * Path for querying details for an item.
     */
    public static final String PATH_DETAIL_ITEM = "details";

    /**
     * Path for querying sections for a detail item. This can be used when setting up a
     * UriMatcher. Queries will use the form content://<authority>/details/<item_id>/sections.
     */
    public static final String PATH_DETAIL_SECTIONS = "sections";

    /**
     * Path for querying detail actions. This can be used when setting up a UriMatcher. Queries will
     * use the form content://<authority>/details/<item_id>/actions.
     */
    public static final String PATH_DETAIL_ACTIONS = "actions";

    /**
     * Action for searching a Pano provider. Apps receiving this
     * intent should call {@link Intent#getData()} to retrieve the base Uri and
     * {@link #EXTRA_QUERY} to find query.
     */
    public static final String ACTION_SEARCH = "com.google.android.pano.action.SEARCH";

    /**
     * Action for searching a Pano provider.
     * <p>
     * Any intent selected off this activity will be returned to the calling activity instead of
     * being launched directly.
     */
    public static final String ACTION_SEARCH_PICKER =
            "com.google.android.pano.action.SEARCH_PICKER";

    /**
     * An intent action for searching app content while keeping the launching app running.
     * <p>
     * Pano Search will appear over the app.
     * <p>
     * Any intents launched from here will be returned to the calling activity instead of being
     * directly launched.
     * <p>
     * This can be used to select an item.
     */
    public static final String ACTION_SEARCH_PICKER_TRANSLUCENT =
            "com.google.android.pano.action.SEARCH_PICKER_TRANSLUCENT";

    /**
     * The query to be executed when search activity is launched
     * This extra is optional and defaults to null.
     */
    public static final String EXTRA_QUERY = "query";

    /**
     * Optional String extra for meta information. This must be supplied as a string, but must be a valid URI.
     * <p>
     * Used with {@link #ACTION_SEARCH}.
     */
    public static final String EXTRA_META_URI = "meta_uri";

    /**
     * Optional int extra for setting the display mode of the search activity.
     *
     * @see #DISPLAY_MODE_ROW
     * @see #DISPLAY_MODE_GRID
     */
    public static final String EXTRA_DISPLAY_MODE = "display_mode";

    public static final int DISPLAY_MODE_ROW = 0;
    public static final int DISPLAY_MODE_GRID = 1;
    public static final int DISPLAY_MODE_BROWSE = 2;

    /**
     * Value for the root Pano URI when this activity should be excluded from the Pano top level
     * and the legacy apps area.
     *
     * TODO: this is obsolete: remove.
     */
    public static final String EXCLUDED_ROOT_URI = "excluded";

    protected interface LauncherColumns {

        /**
         * The name of the cluster. Generally used for debugging and not shown
         * to the user.
         *
         * <P>Type: String</P>
         */
        public static final String NAME = "name";

        /**
         * An optional name to display with the cluster. This value will be user
         * visible. Example: "Recently Watched"
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_NAME = "display_name";

        /**
         * How important this cluster is. The higher the value the more important
         * it will be relative to other clusters. The importance is a relative
         * weighting and not an absolute priority. If this value is left blank
         * it will default to 0 (not important).
         *
         * <P>Type: INTEGER</P>
         */
        public static final String IMPORTANCE = "importance";

        /**
         * The number of items that should be shown in this cluster. If this
         * value is more than there is space for fewer items may be shown.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String VISIBLE_COUNT = "visible_count";

        /**
         * Whether or not the image may be cropped to fit the display area
         * better. 1 means cropping is allowed, 0 means the item should be
         * shrunk or stretched to fit instead.
         *
         * <P>Type: INTEGER (0 or 1)</P>
         */
        public static final String IMAGE_CROP_ALLOWED = "image_crop_allowed";

        /**
         * The amount of time it is safe to assume the data for this
         * cluster will remain valid. For example, if this cluster is
         * advertising a daily special this should return the time until the
         * special ends. A best effort will be made to not display data past
         * this point but some data may not be requeried immediately.
         *
         * <P>Type: INTEGER (long)</P>
         */
        public static final String CACHE_TIME_MS = "cache_time_ms";

        /**
         * Content URI pointing to a list of items in the {@link PanoContract.BrowseItemsColumns}
         * schema.
         * <p>
         * This is optional but highly recommended if the cluster represents a browse row.
         * <p>
         * In this case, the cluster items will be read from this URI instead of from the cluster
         * items.
         * <p>
         * If this is filled in, the intent_uri must have the action
         * {@link PanoContract#ACTION_BROWSE}. The row with this URI will automatically be
         * selected when the activity starts up.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String BROWSE_ITEMS_URI = "browse_items_uri";

        /**
         * A standard Intent Uri to be launched when this cluster is selected.
         * This may be a {@link PanoContract#ACTION_BROWSE} intent or an
         * intent to launch directly into an app. You can also use
         * {@link PanoContract#getBrowseIntent(Uri, int)} to generate a
         * browse intent for a given root Uri. Use {@link Intent#toUri(int)}
         * with a flag of {@link Intent#URI_INTENT_SCHEME}.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String INTENT_URI = "intent_uri";

        /**
         * A String to display as a notification on the launcher. This may also
         * cause a visual indication to be shown on the launcher when this app
         * is not in view.
         *
         * <P>Type: String</P>
         */
        public static final String NOTIFICATION_TEXT = "notification_text";

        /**
         * An optional Uri for querying progress for any ongoing actions, such
         * as an active download.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String PROGRESS_URI = "progress_uri";

    }

    public static final class Launcher implements BaseColumns, LauncherColumns {

        /**
         * This utility class cannot be instantiated
         */
        private Launcher() {}
    }

    protected interface ProgressColumns {
        /**
         * The current progress as an integer in the range [0-100] inclusive.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String PROGRESS = "progress";

        /**
         * The smallest value that is a valid {@link #PROGRESS}.
         */
        public static final int PROGRESS_MIN = 0;

        /**
         * The largest value that is a valid {@link #PROGRESS}.
         */
        public static final int PROGRESS_MAX = 100;
    }

    public static final class Progress implements BaseColumns, ProgressColumns {
        /**
         * This utility class cannot be instantiated
         */
        private Progress() {}
    }

    protected interface LauncherItemColumns {
        /**
         * The _id of the cluster this item is a member of.
         *
         * <P>Type: INTEGER (long)</P>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * The uri for retrieving the image to show for this item. This
         * String should be generated using {@link Uri#toString()}
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String IMAGE_URI = "image_uri";

    }

    public static final class LauncherItem implements BaseColumns, LauncherItemColumns {

        /**
         * Returns a Uri that can be used to query the individual items in a
         * cluster.
         *
         * @param root The Uri base path to query against.
         * @param clusterId the _id of the cluster returned by querying the Launcher
         *            Uri for this provider.
         */
        public static final Uri getLauncherItemsUri(Uri root, long clusterId) {
            return Uri.withAppendedPath(root, PATH_LAUNCHER_ITEM + "/" + clusterId);
        }

        /**
         * Returns a Uri that can be used to query the all items across clusters.
         *
         * @param root The ContentProvider authority that this Uri should query
         *            against.
         */
        public static final Uri getLauncherItemsUri(Uri root) {
            return Uri.withAppendedPath(root, PATH_LAUNCHER_ITEM);
        }

        /**
         * This utility class cannot be instantiated
         */
        private LauncherItem() {};
    }

    protected interface BrowseHeadersColumns {
        /**
         * Reference name of the header, not used for display to the users.
         *
         * <P>Type: String</P>
         */
        public static final String NAME = "name";

        /**
         * The name to show for the header. User visible.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_NAME = "display_name";

        /**
         * Optional Uri pointing to the data for the items for this header.
         * <p>
         * If this is not provided, a Uri will be constructed using
         * {@link BrowseItems#getBrowseItemsUri(Uri, long)}.
         *
         * <P>Type: String</P>
         */
        public static final String ITEMS_URI = "items_uri";

        /**
         * Uri pointing to an icon to be used as part of the header. This
         * String should be generated using {@link Uri#toString()}
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String ICON_URI = "icon_uri";

        /**
         * Uri pointing to an icon to be used for app branding on this tab.
         * This String should be generated using {@link Uri#toString()}
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String BADGE_URI = "badge_uri";

        /**
         * A 0xAARRGGBB color that should be applied to the background when on
         * this tab.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String COLOR_HINT = "color_hint";

        /**
         * A 0xAARRGGBB color that should be applied to the text when on this
         * tab.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String TEXT_COLOR_HINT = "text_color_hint";

        /**
         * Uri pointing to an image to display in the background when on this
         * tab. Be sure the image contrasts enough with the text color hint and
         * is of high enough quality to be displayed at 1080p. This String
         * should be generated using {@link Uri#toString()}.  The URI will be either
         * a resource uri in format of android:resource:// or an external URL
         * like file://, http://, https://.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String BACKGROUND_IMAGE_URI = "background_image_uri";

        /**
         * Uri pointing to an image to display in the background when on this
         * tab. Be sure the image contrasts enough with the text color hint and
         * is of high enough quality to be displayed at 1080p. This String
         * should be generated using {@link Uri#toString()}.  The URI will be either
         * a resource uri in format of android:resource:// or an external URL
         * like file://, http://, https://.
         *
         * <P>Type: String (Uri)</P>
         * <P>This is the obsolete version of {@link #BACKGROUND_IMAGE_URI}</P>
         * TODO: remove obsolete version when all clients have upgraded.
         */
        public static final String BG_IMAGE_URI = "bg_image_uri";

        /**
         * The default width of the expanded image
         *
         * <P>Type: INTEGER</P>
         */
        public static final String DEFAULT_ITEM_WIDTH = "default_item_width";

        /**
         * The default height of the expanded image
         *
         * <P>Type: INTEGER</P>
         */
        public static final String DEFAULT_ITEM_HEIGHT = "default_item_height";

        /**
         * 1 to show a lane below images for description, 0 to hide.
         * Default value is 1.
         *
         * <P>Type: INTEGER (0 or 1)</P>
         */
        public static final String SHOW_DESCRIPTIONS = "show_descriptions";

        /**
         * A group id. If this is not 0, contiguous headers with the same
         * expand group will be expanded together. Non-contiguous headers with
         * the same expand group is an error.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String EXPAND_GROUP = "expand_group";

        /**
         * Controls whether the items in this row will wrap around back to the
         * beginning when the user scrolls to the last item. 0 to not wrap items,
         * 1 to wrap.
         *
         * <P>Type: INTEGER (0 or 1)</P>
         */
        public static final String WRAP_ITEMS = "wrap_items";

    }

    public static final class BrowseHeaders implements BaseColumns, BrowseHeadersColumns {
        /**
         * Returns a uri for retrieving a list of browse header meta-data items that
         * describe the categories for this browse path (name, badge, color hint,
         * background image, etc.)
         *
         * @param root The base content Uri to browse.
         * @return
         */
        public static final Uri getBrowseHeadersUri(Uri root) {
            return Uri.withAppendedPath(root, PATH_BROWSE_HEADERS);
        }

        /**
         * This utility class cannot be instantiated
         */
        private BrowseHeaders(){}
    }

    protected interface BrowseItemsColumns {
        /**
         * The _id of the header this item belongs to.
         *
         * <P>Type: INTEGER (long)</P>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * Text that may be shown to the user along with the image or instead
         * of an image if no image was specified.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_NAME = "display_name";

        /**
         * Long description text of this item.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_DESCRIPTION = "display_description";

        /**
         * The uri for retrieving the image to show for this item. This string
         * should be created using {@link Uri#toString()}.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String IMAGE_URI = "image_uri";

        /**
         * The width of the image for this item in pixels.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String WIDTH = "width";

        /**
         * The height of the image for this item in pixels.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String HEIGHT = "height";

        /**
         * An intent to launch when this item is selected. It may be another
         * browse intent or a deep link into the app. This String should be
         * generated using {@link Intent#toUri(int)} with
         * {@link Intent#URI_INTENT_SCHEME}.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String INTENT_URI = "intent_uri";
    }

    public static final class BrowseItems implements BaseColumns, BrowseItemsColumns {
        /**
         * Returns a Uri that can be used to query the items within a browse
         * category.
         *
         * @param root The base content Uri that is being browsed.
         * @param headerId The _id of the header that will be queried.
         * @return
         */
        public static final Uri getBrowseItemsUri(Uri root, long headerId) {
            return ContentUris.withAppendedId(root, headerId);
        }
    }

    protected interface UserRatingColumns {
        /**
         * A custom rating String for this item, such as "78 points" or
         * "20/100". (Optional)
         * <p>
         * A null or the absence of this column indicates there is no custom
         * rating available.
         * <P>Type: String</P>
         */
        public static final String USER_RATING_CUSTOM = "user_rating_custom";

        /**
         * A scaled rating for this item as a float in the range [0-10]
         * inclusive. Pano will be responsible for visualizing this
         * value.(Optional)
         * <p>
         * A -1 or the absence of this column indicates there is no rating
         * available.
         * <P>Type: FLOAT</P>
         */
        public static final String USER_RATING = "user_rating";

        /**
         * The number of reviews included in the average rating. (Optional)
         * <p>
         * A value of 0 indicates the count is not available.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String USER_RATING_COUNT = "user_rating_count";
    }

    protected interface DetailItemColumns {

        /**
         * Title of the item.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_NAME = "display_name";

        /**
         * Long description text of this item.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_DESCRIPTION = "display_description";

        /**
         * The uri for retrieving the foreground image to show for this item. This string
         * should be created using {@link Uri#toString()}.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String FOREGROUND_IMAGE_URI = "foreground_image_uri";

        /**
         * The uri for retrieving the background image to show for this item. This string
         * should be created using {@link Uri#toString()}.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String BACKGROUND_IMAGE_URI = "background_image_uri";

        /**
         * A 0xAARRGGBB color that should be applied to the background.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String COLOR_HINT = "color_hint";

        /**
         * The uri for the badge
         */
        public static final String BADGE_URI = "badge_uri";

        /**
         * A 0xAARRGGBB color that should be applied to rendered text so as no
         * not conflict with the {@link #COLOR_HINT} or
         * {@link #BACKGROUND_IMAGE_URI}.
         *
         *<P>Type: INTEGER</P>
         */
        public static final String TEXT_COLOR_HINT = "text_color_hint";
    }

    public static final class DetailItem implements BaseColumns, DetailItemColumns {

        /**
         * Non instantiable.
         */
        private DetailItem() {}
    }

    protected interface DetailSectionsColumns {

        /**
         * Text ID for a section. Can be used to target the section
         *
         * <P>Type: String</P>
         */
        public static final String NAME = "name";

        /**
         * Text that will be shown to the user for navigating between sections.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_HEADER = "display_header";

        /**
         * Primary text for display when a section is visible, such as
         * an artist name or movie title. (Optional)
         * <p>
         * This is only valid if {@link #SECTION_TYPE} is
         * {@link #SECTION_TYPE_LIST} or {@link #SECTION_TYPE_SECTIONS}.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_NAME = "display_name";

        /**
         * Secondary text for display when a section is visible, such as
         * a release date or album title. (Optional)
         * <p>
         * This is only valid if {@link #SECTION_TYPE} is
         * {@link #SECTION_TYPE_LIST} or {@link #SECTION_TYPE_SECTIONS}.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_SUBNAME = "display_subname";

        /**
         * Type of item.
         * <p>
         * One of {@link #SECTION_TYPE_BLOB}, {@link #SECTION_TYPE_LIST},
         * {@link #SECTION_TYPE_BLURB}, {@link #SECTION_TYPE_REVIEWS}, or
         * {@link #SECTION_TYPE_SECTIONS}.
         *
         * <P>Type: Integer</P>
         */
        public static final String SECTION_TYPE = "section_type";

        /**
         * Value for {@link #SECTION_TYPE} if this section has HTML content.
         * This should only be used as a last resort if the other formats can't
         * be made to work.
         */
        public static final int SECTION_TYPE_BLOB = 0;

        /**
         * Value for {@link #SECTION_TYPE} if this section uses the default list
         * formatting for its content.
         */
        public static final int SECTION_TYPE_LIST = 1;

        /**
         * Value for {@link #SECTION_TYPE} if this section uses the default
         * formatting for its content.
         */
        public static final int SECTION_TYPE_BLURB = 2;

        /**
         * Value for {@link #SECTION_TYPE} if this section has review style
         * formatting for its content.
         */
        public static final int SECTION_TYPE_REVIEWS = 3;

        /**
         * Value for {@link #SECTION_TYPE} if this section is multiple related
         * sections that can be grouped. For example, seasons of a TV show
         * should use this type.
         */
        public static final int SECTION_TYPE_SECTIONS = 4;

        /**
         * Blob content, either a string or HTML.
         * <p>
         * If HTML, this will be sanitized before displaying in a web view.
         * <p>
         * JavaScript is not allowed.
         *
         * <P>Type: String</P>
         */
        public static final String BLOB_CONTENT = "blob_content";

        /**
         * Action or list of actions available for this section.
         * <p>
         * Only valid if {@link #SECTION_TYPE} = {@link #SECTION_TYPE_BLOB}.
         * <p>
         * This is either a single intent URI or a content URI pointing to a list of
         * {@link DetailActions}.
         */
        public static final String ACTION_URI = "action_uri";

        /**
         * Content URI. This must be nested under the detail item ID.
         * <p>
         * Only valid if {@link #SECTION_TYPE} is one of
         * {@link #SECTION_TYPE_LIST}, {@link #SECTION_TYPE_REVIEWS},
         * {@link #SECTION_TYPE_SECTIONS}.
         */
        public static final String CONTENT_URI = "content_uri";
    }

    /**
     * A top level listing of the sections for this item, such as "Ratings" or
     * "Related." The BLOB_CONTENT column is only valid if
     * {@link DetailSectionsColumns#SECTION_TYPE} =
     * {@link DetailSectionsColumns#SECTION_TYPE_BLOB}. USER_RATING columns are
     * only valid if {@link DetailSectionsColumns#SECTION_TYPE} =
     * {@link DetailSectionsColumns#SECTION_TYPE_REVIEWS}.
     */
    public static final class DetailSections
            implements BaseColumns, DetailSectionsColumns, UserRatingColumns {
        /**
         * Non instantiable.
         */
        private DetailSections() {}

        /**
         * Gets a content URI suitable for loading the sections for an item.
         */
        public static Uri getSectionsUri(Uri itemUri) {
            return itemUri.buildUpon().appendPath(PATH_DETAIL_SECTIONS).build();
        }
    }

    protected interface DetailBlurbColumns {
        /**
         * Primary text that will be shown to the user, generally the title.
         * (Optional)
         *
         *<P>Type: String</P>
         */
        public static final String DISPLAY_NAME = "display_name";

        /**
         * A secondary title with more importance than the description, such as
         * the artist or director. (Optional)
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_SUBNAME = "display_subname";

        /**
         * Long description text of this item. (Optional)
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_DESCRIPTION = "display_description";

        /**
         * The category of the item to show to the user, such as "Apps" or
         * "Folk". (Optional)
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_CATEGORY = "display_category";

        /**
         * The URI for retrieving an image to show. This should be a rating
         * badge or other similar icon. This string should be created using
         * {@link Uri#toString()}. (Optional)
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String BADGE_URI = "badge_uri";
    }

    public static final class DetailBlurb
            implements BaseColumns, DetailBlurbColumns, UserRatingColumns {
        /**
         * Non instantiable.
         */
        private DetailBlurb() {}
    }

    protected interface ItemChildrenColumns {
        /**
         * Primary text that will be shown to the user, generally the title.
         * (Optional)
         *
         *<P>Type: String</P>
         */
        public static final String DISPLAY_NAME = "display_name";

        /**
         * A secondary title with more importance than the description, such as
         * the artist or director. (Optional)
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_SUBNAME = "display_subname";

        /**
         * Long description text of this item. (Optional)
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_DESCRIPTION = "display_description";

        /**
         * A number to display next to the item, such as the track or
         * episode number. 0 is not allowed. (Optional)
         *
         * <P>Type: INTEGER</P>
         */
        public static final String DISPLAY_NUMBER = "display_number";

        /**
         * Hint for how to display the item. This is either {@link #ITEM_DISPLAY_TYPE_NORMAL} or
         * {@link #ITEM_DISPLAY_TYPE_SINGLE_LINE}.
         */
        public static final String ITEM_DISPLAY_TYPE = "item_display_type";

        /**
         * Value for {@link #ITEM_DISPLAY_TYPE} which allows for multiple lines.
         */
        public static final int ITEM_DISPLAY_TYPE_NORMAL = 0;

        /**
         * Value for {@link #ITEM_DISPLAY_TYPE} which hints that the content should be displayed
         * on a single line.
         */
        public static final int ITEM_DISPLAY_TYPE_SINGLE_LINE = 1;

        /**
         * The uri for retrieving the image to show for this item. This string
         * should be created using {@link Uri#toString()}. (Optional)
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String IMAGE_URI = "image_uri";

        /**
         * Either an intent URI for an intent that should be triggered or else a content URI
         * pointing to a list of actions for this item.
         * <p>
         * If the list has only 1 action per item, it is more efficient to supply an intent URI
         * here. (Optional)
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String ACTION_URI = "action_uri";
    }

    protected interface SearchBrowseResult {
        public static final String RESULTS_URI = "results_uri";
        /**
         * The default width of the expanded image
         *
         * <P>Type: INTEGER</P>
         */
        public static final String DEFAULT_ITEM_WIDTH = "default_item_width";

        /**
         * The default height of the expanded image
         *
         * <P>Type: INTEGER</P>
         */
        public static final String DEFAULT_ITEM_HEIGHT = "default_item_height";
    }

    public static final class DetailChildren
            implements BaseColumns, ItemChildrenColumns, UserRatingColumns {

        /**
         * Non instantiable.
         */
        private DetailChildren() {}
    }

    public static final class SearchResults
            implements BaseColumns, ItemChildrenColumns, UserRatingColumns, SearchBrowseResult {

        /**
         * Non instantiable.
         */
        private SearchResults() {}
    }

    protected interface MetaColumns {

        /**
         * The uri for retrieving the background image to show for this item. This string
         * should be created using {@link Uri#toString()}.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String BACKGROUND_IMAGE_URI = "background_image_uri";
        /**
         * Uri pointing to an icon to be used for app branding on this tab.
         * This String should be generated using {@link Uri#toString()}
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String BADGE_URI = "badge_uri";

        /**
         * A 0xAARRGGBB color that should be applied to the background when on
         * this tab.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String COLOR_HINT = "color_hint";
    }

    public static final class MetaSchema implements BaseColumns, MetaColumns {

        /**
         * Non instantiable.
         */
        private MetaSchema() {}
    }

    protected interface DetailActionsColumns {

        /**
         * Text that will be shown to the user.
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_NAME = "display_name";

        /**
         * Secondary text that will be shown to the user, such as "from $2.99".
         * (Optional)
         *
         * <P>Type: String</P>
         */
        public static final String DISPLAY_SUBNAME = "display_subname";

        /**
         * Intent URI of the form intent:// which will be triggered.
         *
         * <P>Type: String (Uri)</P>
         */
        public static final String INTENT_URI = "intent_uri";
    }

    public static final class DetailActions implements BaseColumns, DetailActionsColumns {

        /**
         * Non instantiable.
         */
        private DetailActions() {}
    }

    /**
     * Returns a browse intent with the root included. The root should be a
     * base Uri that the browse queries can build off of.
     *
     * Example: "content://com.google.movies/browse/action"
     *
     * @param root The authority + path that will be browsed on.
     * @param start The index of the header to display expanded first.
     * @return
     */
    public static Intent getBrowseIntent(Uri root, int start) {
        Intent intent = new Intent(ACTION_BROWSE);
        intent.setData(root);
        intent.putExtra(EXTRA_START_INDEX, start);
        return intent;
    }

    /**
     * Returns a browse intent with the root included. The root should be a
     * base Uri that the browse queries can build off of.
     *
     * Example: "content://com.google.movies/browse/action"
     *
     * @param root The authority + path that will be browsed on.
     * @param startId The _id of the header to display expanded first.
     * @return
     */
    public static Intent getBrowseIntentById(Uri root, long startId) {
        Intent intent = new Intent(ACTION_BROWSE);
        intent.setData(root);
        intent.putExtra(EXTRA_START_ID, startId);
        return intent;
    }

    /**
     * Returns a details intent for the given root URI. The root should be a URI
     * specific to the item being viewed that can be appended to for details
     * queries.
     *
     * Example: "content://com.google.movies/details/75289"
     */
    public static Intent getDetailsIntent(Uri root) {
        Intent intent = new Intent(ACTION_DETAIL);
        intent.setData(root);
        return intent;
    }
}

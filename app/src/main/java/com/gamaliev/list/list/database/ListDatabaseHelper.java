package com.gamaliev.list.list.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.gamaliev.list.common.CommonUtils;
import com.gamaliev.list.common.ProgressNotificationHelper;
import com.gamaliev.list.common.database.DatabaseHelper;
import com.gamaliev.list.common.database.DatabaseQueryBuilder;
import com.gamaliev.list.list.ListEntry;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import static com.gamaliev.list.common.CommonUtils.EXTRA_DATES_FROM_DATE;
import static com.gamaliev.list.common.CommonUtils.EXTRA_DATES_TO_DATETIME;
import static com.gamaliev.list.common.CommonUtils.getDateFromProfileMap;
import static com.gamaliev.list.common.CommonUtils.getDefaultColor;
import static com.gamaliev.list.common.CommonUtils.getStringDateFormatSqlite;
import static com.gamaliev.list.common.CommonUtils.showToast;
import static com.gamaliev.list.common.database.DatabaseHelper.BASE_COLUMN_ID;
import static com.gamaliev.list.common.database.DatabaseHelper.FAVORITE_COLUMN_COLOR;
import static com.gamaliev.list.common.database.DatabaseHelper.LIST_ITEMS_COLUMN_COLOR;
import static com.gamaliev.list.common.database.DatabaseHelper.LIST_ITEMS_COLUMN_CREATED;
import static com.gamaliev.list.common.database.DatabaseHelper.LIST_ITEMS_COLUMN_DESCRIPTION;
import static com.gamaliev.list.common.database.DatabaseHelper.LIST_ITEMS_COLUMN_EDITED;
import static com.gamaliev.list.common.database.DatabaseHelper.LIST_ITEMS_COLUMN_IMAGE_URL;
import static com.gamaliev.list.common.database.DatabaseHelper.LIST_ITEMS_COLUMN_TITLE;
import static com.gamaliev.list.common.database.DatabaseHelper.LIST_ITEMS_COLUMN_VIEWED;
import static com.gamaliev.list.common.database.DatabaseHelper.LIST_ITEMS_TABLE_NAME;
import static com.gamaliev.list.common.database.DatabaseHelper.SQL_LIST_ITEMS_CREATE_TABLE;
import static com.gamaliev.list.common.database.DatabaseHelper.SQL_LIST_ITEMS_DROP_TABLE;
import static com.gamaliev.list.common.database.DatabaseQueryBuilder.OPERATOR_BETWEEN;
import static com.gamaliev.list.common.database.DatabaseQueryBuilder.OPERATOR_EQUALS;
import static com.gamaliev.list.common.database.DatabaseQueryBuilder.OPERATOR_LIKE;
import static com.gamaliev.list.list.ListActivity.SEARCH_COLUMNS;

/**
 * @author Vadim Gamaliev
 * <a href="mailto:gamaliev-vadim@yandex.com">(e-mail: gamaliev-vadim@yandex.com)</a>
 */

public class ListDatabaseHelper {

    /* Logger */
    private static final String TAG = ListDatabaseHelper.class.getSimpleName();

    /* ... */
    private static final DatabaseHelper DB_HELPER;

    @NonNull private static final String[] DATES_COLUMNS = {
            LIST_ITEMS_COLUMN_CREATED,
            LIST_ITEMS_COLUMN_EDITED,
            LIST_ITEMS_COLUMN_VIEWED
    };


    /*
        Init
     */

    static {
        DB_HELPER = DatabaseHelper.getInstance(null);
    }

    private ListDatabaseHelper() {}


    /*
        Methods
     */

    /**
     * Insert new entry in database.
     * @param entry Entry, contains title, description, color.
     */
    public static boolean insertEntry(
            @NonNull final Context context,
            @NonNull final ListEntry entry) {

        try {
            final SQLiteDatabase db = DB_HELPER.getWritableDatabase();
            insertEntry(context, entry, db);

            // If ok
            return true;

        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
            showToast(context, DB_HELPER.getDbFailMessage(), Toast.LENGTH_SHORT);
            return false;
        }
    }

    /**
     * Insert new entry in database.
     * @param entry Entry, contains title, description, color.
     * @param db    Opened writable database.
     */
    public static void insertEntry(
            @NonNull final Context context,
            @NonNull final ListEntry entry,
            @NonNull final SQLiteDatabase db) throws SQLiteException {

        // Variables
        final String title          = entry.getTitle();
        final String description    = entry.getDescription();
        final int color             = entry.getColor() == null
                ? getDefaultColor(context)
                : entry.getColor();
        final String imageUrl       = entry.getImageUrl();

        // Content values
        final ContentValues cv = new ContentValues();
        cv.put(LIST_ITEMS_COLUMN_TITLE,         title);
        cv.put(LIST_ITEMS_COLUMN_DESCRIPTION,   description);
        cv.put(LIST_ITEMS_COLUMN_COLOR,         color);
        cv.put(LIST_ITEMS_COLUMN_IMAGE_URL,     imageUrl);

        String utcCreatedDate =
                getStringDateFormatSqlite(
                        context,
                        entry.getCreated() == null ? new Date() : entry.getCreated(),
                        true);
        String utcEditedDate =
                getStringDateFormatSqlite(
                        context,
                        entry.getEdited() == null ? new Date() : entry.getEdited(),
                        true);
        String utcViewedDate =
                getStringDateFormatSqlite(
                        context,
                        entry.getViewed() == null ? new Date() : entry.getViewed(),
                        true);

        cv.put(LIST_ITEMS_COLUMN_CREATED,   utcCreatedDate);
        cv.put(LIST_ITEMS_COLUMN_EDITED,    utcEditedDate);
        cv.put(LIST_ITEMS_COLUMN_VIEWED,    utcViewedDate);

        // Insert
        if (db.insert(LIST_ITEMS_TABLE_NAME, null, cv) == -1) {
            final String error = String.format(Locale.ENGLISH,
                    "[ERROR] Insert entry {%s: %s, %s: %s, %s: %d, %s: %s}",
                    LIST_ITEMS_COLUMN_TITLE, title,
                    LIST_ITEMS_COLUMN_DESCRIPTION, description,
                    LIST_ITEMS_COLUMN_COLOR, color,
                    LIST_ITEMS_COLUMN_IMAGE_URL, imageUrl);
            throw new SQLiteException(error);
        }
    }

    /**
     * Update entry in database.
     * @param entry                 Entry, must contains non-null id.
     * @param editedViewedColumn    Which column should updated with current date and time.
     *
     * {@link com.gamaliev.list.common.database.DatabaseHelper#LIST_ITEMS_COLUMN_EDITED} or
     * {@link com.gamaliev.list.common.database.DatabaseHelper#LIST_ITEMS_COLUMN_VIEWED}.
     * If null, then default is
     * {@link com.gamaliev.list.common.database.DatabaseHelper#LIST_ITEMS_COLUMN_EDITED}.
     */
    public static boolean updateEntry(
            @NonNull final Context context,
            @NonNull final ListEntry entry,
            @Nullable String editedViewedColumn) {

        if (editedViewedColumn == null) {
            editedViewedColumn = LIST_ITEMS_COLUMN_EDITED;
        }

        try {
            final SQLiteDatabase db = DB_HELPER.getWritableDatabase();

            // Variables
            final long id               = entry.getId();
            final String title          = entry.getTitle();
            final String description    = entry.getDescription();
            final int color             = entry.getColor() == null
                    ? getDefaultColor(context)
                    : entry.getColor();
            final String imageUrl       = entry.getImageUrl();

            // Content values
            final ContentValues cv = new ContentValues();
            cv.put(LIST_ITEMS_COLUMN_TITLE,         title);
            cv.put(LIST_ITEMS_COLUMN_DESCRIPTION,   description);
            cv.put(LIST_ITEMS_COLUMN_COLOR,         color);
            cv.put(LIST_ITEMS_COLUMN_IMAGE_URL,     imageUrl);
            cv.put(editedViewedColumn,              getStringDateFormatSqlite(context, new Date(), true));

            // Update
            final int updateResult = db.update(
                    LIST_ITEMS_TABLE_NAME,
                    cv,
                    BASE_COLUMN_ID + " = ?",
                    new String[]{Long.toString(id)});

            if (updateResult == 0) {
                throw new SQLiteException("[ERROR] The number of rows affected is 0");
            }

            // If ok
            return true;

        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
            showToast(context, DB_HELPER.getDbFailMessage(), Toast.LENGTH_SHORT);
            return false;
        }
    }

    /**
     * Get entries from database with specified parameters.
     * @return Result cursor.
     */
    @Nullable
    public static Cursor getEntries(
            @NonNull final Context context,
            @NonNull final DatabaseQueryBuilder queryBuilder) {

        try {
            final SQLiteDatabase db = DB_HELPER.getReadableDatabase();

            // Make query and return cursor, if ok;
            return db.query(
                    LIST_ITEMS_TABLE_NAME,
                    null,
                    queryBuilder.getSelectionResult(),
                    queryBuilder.getSelectionArgs(),
                    null,
                    null,
                    queryBuilder.getSortOrder());

        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
            showToast(context, DB_HELPER.getDbFailMessage(), Toast.LENGTH_SHORT);
            return null;
        }
    }

    /**
     * Get count of entries from database with specified parameters.
     * @return Count of rows.
     */
    public static long getEntriesCount(
            @NonNull final Context context,
            @NonNull final DatabaseQueryBuilder queryBuilder) {

        try {
            final SQLiteDatabase db = DB_HELPER.getReadableDatabase();

            // Make query and return count of result rows;
            return DatabaseUtils.queryNumEntries(
                    db,
                    LIST_ITEMS_TABLE_NAME,
                    queryBuilder.getSelectionResult(),
                    queryBuilder.getSelectionArgs());

        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
            showToast(context, DB_HELPER.getDbFailMessage(), Toast.LENGTH_SHORT);
            return -1;
        }
    }

    /**
     * Get filled object from database, with given id.
     * @param id    Id of entry.
     * @return      Filled object. See {@link com.gamaliev.list.list.ListEntry}
     */
    @Nullable
    public static ListEntry getEntry(
            @NonNull final Context context,
            @NonNull final Long id) {

        // Create new entry.
        final ListEntry entry = new ListEntry();

        //
        final SQLiteDatabase db = DB_HELPER.getReadableDatabase();

        try (Cursor cursor = db.query(
                LIST_ITEMS_TABLE_NAME,
                null,
                BASE_COLUMN_ID + " = ?",
                new String[] {id.toString()},
                null,
                null,
                null)) {

            // Fill new entry.
            if (cursor.moveToFirst()) {
                final int indexId           = cursor.getColumnIndex(BASE_COLUMN_ID);
                final int indexTitle        = cursor.getColumnIndex(LIST_ITEMS_COLUMN_TITLE);
                final int indexDescription  = cursor.getColumnIndex(LIST_ITEMS_COLUMN_DESCRIPTION);
                final int indexColor        = cursor.getColumnIndex(LIST_ITEMS_COLUMN_COLOR);
                final int indexImageUrl     = cursor.getColumnIndex(LIST_ITEMS_COLUMN_IMAGE_URL);
                final int indexCreated      = cursor.getColumnIndex(LIST_ITEMS_COLUMN_CREATED);
                final int indexEdited       = cursor.getColumnIndex(LIST_ITEMS_COLUMN_EDITED);
                final int indexViewed       = cursor.getColumnIndex(LIST_ITEMS_COLUMN_VIEWED);

                entry.setId(            cursor.getLong(     indexId));
                entry.setTitle(         cursor.getString(   indexTitle));
                entry.setDescription(   cursor.getString(   indexDescription));
                entry.setColor(         cursor.getInt(      indexColor));
                entry.setImageUrl(      cursor.getString(   indexImageUrl));

                // Parse sqlite format (yyyy-MM-dd HH:mm:ss, UTC), in localtime.
                DateFormat df = CommonUtils.getDateFormatSqlite(context, true);
                try {
                    Date created    = df.parse(cursor.getString(indexCreated));
                    Date edited     = df.parse(cursor.getString(indexEdited));
                    Date viewed     = df.parse(cursor.getString(indexViewed));

                    entry.setCreated(created);
                    entry.setEdited(edited);
                    entry.setViewed(viewed);

                } catch (ParseException e) {
                    Log.e(TAG, e.toString());
                }
            }

            return entry;

        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
            showToast(context, DB_HELPER.getDbFailMessage(), Toast.LENGTH_SHORT);
            return null;
        }
    }

    /**
     * Delete entry from database, with given id.
     * @param id    Id of entry to be deleted.
     * @return      True if success, otherwise false.
     */
    public static boolean deleteEntry(
            @NonNull final Context context,
            @NonNull final Long id) {

        try {
            final SQLiteDatabase db = DB_HELPER.getWritableDatabase();

            // Delete query.
            final int deleteResult = db.delete(
                    LIST_ITEMS_TABLE_NAME,
                    BASE_COLUMN_ID + " = ?",
                    new String[]{id.toString()});

            // If error.
            if (deleteResult == 0) {
                throw new SQLiteException("[ERROR] The number of rows affected is 0");
            }

            // If ok.
            return true;

        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
            showToast(context, DB_HELPER.getDbFailMessage(), Toast.LENGTH_SHORT);
            return false;
        }
    }

    /**
     * Delete all rows from list table. See: {@link com.gamaliev.list.list.ListActivity}
     * @return true if ok, otherwise false.
     */
    public static boolean removeAllEntries(
            @NonNull final Context context) {

        try {
            final SQLiteDatabase db = DB_HELPER.getWritableDatabase();

            // Begin transaction.
            db.beginTransaction();

            try {
                // Exec SQL queries.
                db.execSQL(SQL_LIST_ITEMS_DROP_TABLE);
                db.execSQL(SQL_LIST_ITEMS_CREATE_TABLE);

                // If ok.
                db.setTransactionSuccessful();
                return true;

            } finally {
                db.endTransaction();
            }

        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
            showToast(context, DB_HELPER.getDbFailMessage(), Toast.LENGTH_SHORT);
        }

        return false;
    }

    /**
     * Add mock entries in list activity. See: {@link com.gamaliev.list.list.ListActivity}
     * @return Number of added entries. If error, then return "-1".
     */
    public static int addMockEntries(
            @NonNull final Context context,
            @Nullable final ProgressNotificationHelper notification,
            final int numberOfEntries) {

        try {
            final SQLiteDatabase db = DB_HELPER.getWritableDatabase();

            // Begin transaction.
            db.beginTransaction();

            try {
                // Helper method for add entries.
                ListDatabaseMockHelper.addMockEntries(
                        context,
                        numberOfEntries,
                        db,
                        notification,
                        true);

                // If ok.
                db.setTransactionSuccessful();
                return numberOfEntries;

            } finally {
                db.endTransaction();
            }

        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
            showToast(context, DB_HELPER.getDbFailMessage(), Toast.LENGTH_SHORT);
        }

        return -1;
    }

    /**
     * @param context       Context.
     * @param constraint    Search text.
     * @param profileMap    Profile parameters.
     * @return              Cursor, with given params.
     */
    @Nullable
    public static Cursor getCursorWithParams(
            @NonNull final Context context,
            @Nullable final CharSequence constraint,
            @NonNull final Map<String, String> profileMap) {

        //
        final DatabaseQueryBuilder resultQueryBuilder =
                convertToQueryBuilder(
                        context, constraint, profileMap);

        //
        return getEntries(context, resultQueryBuilder);
    }

    /**
     * @param context       Context.
     * @param constraint    Search text.
     * @param profileMap    Profile parameters.
     * @return              Filled database query builder.
     */
    @NonNull
    public static DatabaseQueryBuilder convertToQueryBuilder(
            @NonNull final Context context,
            @Nullable final CharSequence constraint,
            @NonNull final Map<String, String> profileMap) {

        // Create and fill query builder for text search.
        final DatabaseQueryBuilder searchTextQueryBuilder = new DatabaseQueryBuilder();

        // Add text for search in 'Name' and 'Description' columns, if not empty or null.
        if (!TextUtils.isEmpty(constraint)) {
            searchTextQueryBuilder
                    .addOr( SEARCH_COLUMNS[0],
                            OPERATOR_LIKE,
                            new String[] {constraint.toString()})

                    .addOr( SEARCH_COLUMNS[1],
                            OPERATOR_LIKE,
                            new String[] {constraint.toString()});
        }

        // Create and fill query result builder.
        final DatabaseQueryBuilder resultQueryBuilder = new DatabaseQueryBuilder();

        // Add color filter, if not empty or null.
        if (!TextUtils.isEmpty(profileMap.get(FAVORITE_COLUMN_COLOR))) {
            resultQueryBuilder.addAnd(
                    FAVORITE_COLUMN_COLOR,
                    OPERATOR_EQUALS,
                    new String[]{profileMap.get(FAVORITE_COLUMN_COLOR)});
        }

        // For each column.
        for (int i = 0; i < DATES_COLUMNS.length; i++) {

            // Add +1 day to dateTo.

            // Get dateTo from profile.
            final String dates = getDateFromProfileMap(
                    context,
                    profileMap,
                    DATES_COLUMNS[i],
                    EXTRA_DATES_TO_DATETIME);

            if (TextUtils.isEmpty(dates)) {
                continue;
            }

            // Parse DateTo.
            final DateFormat df = CommonUtils.getDateFormatSqlite(context, false);
            Date dateUtc = null;
            try {
                dateUtc = df.parse(dates);
            } catch (ParseException e) {
                Log.e(TAG, e.toString());
            }

            // Add +1 day to dateTo.
            final Calendar newDateTo = Calendar.getInstance();
            newDateTo.setTime(dateUtc);
            newDateTo.add(Calendar.DATE, 1);

            // Create array for queryBuilder.
            final String[] datesArray = new String[2];
            datesArray[0] = getDateFromProfileMap(
                    context,
                    profileMap,
                    DATES_COLUMNS[i],
                    EXTRA_DATES_FROM_DATE);
            datesArray[1] = getStringDateFormatSqlite(
                    context,
                    newDateTo.getTime(),
                    false);

            // Add viewed filter, if not empty or null.
            if (!TextUtils.isEmpty(profileMap.get(DATES_COLUMNS[i]))) {
                resultQueryBuilder.addAnd(
                        DATES_COLUMNS[i],
                        OPERATOR_BETWEEN,
                        datesArray);
            }
        }

        // Add search text inner filter, if not empty or null.
        if (!TextUtils.isEmpty(constraint)) {
            resultQueryBuilder.addAndInner(searchTextQueryBuilder);
        }

        // Set sort order.
        resultQueryBuilder.setOrder(profileMap.get(ListActivitySharedPreferencesUtils.SP_FILTER_ORDER));
        resultQueryBuilder.setAscDesc(profileMap.get(ListActivitySharedPreferencesUtils.SP_FILTER_ORDER_ASC));

        return resultQueryBuilder;
    }
}
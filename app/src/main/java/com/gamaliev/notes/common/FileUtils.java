package com.gamaliev.notes.common;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.gamaliev.notes.R;
import com.gamaliev.notes.entity.ListEntry;
import com.gamaliev.notes.list.db.ListDbHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static com.gamaliev.notes.common.CommonUtils.showToastRunOnUiThread;
import static com.gamaliev.notes.common.codes.ResultCode.RESULT_CODE_NOTES_EXPORTED;
import static com.gamaliev.notes.common.codes.ResultCode.RESULT_CODE_NOTES_IMPORTED;
import static com.gamaliev.notes.common.db.DbHelper.LIST_ITEMS_TABLE_NAME;
import static com.gamaliev.notes.common.db.DbHelper.getDbFailMessage;
import static com.gamaliev.notes.common.db.DbHelper.getEntries;
import static com.gamaliev.notes.common.db.DbHelper.getWritableDb;
import static com.gamaliev.notes.common.observers.ObserverHelper.FILE_EXPORT;
import static com.gamaliev.notes.common.observers.ObserverHelper.FILE_IMPORT;
import static com.gamaliev.notes.common.observers.ObserverHelper.notifyObservers;
import static com.gamaliev.notes.common.shared_prefs.SpUsers.getProgressNotificationTimerForCurrentUser;
import static com.gamaliev.notes.entity.ListEntry.convertJsonToListEntry;
import static com.gamaliev.notes.entity.ListEntry.getJsonObjectFromCursor;

/**
 * Class, for working with files, for exporting/importing entries from/to database.<br>
 * Supported asynchronous mode with message queue.<br>
 * If the operation time is longer than the specified time, then progress notification enable.<br>
 * <br>
 * The following file template is used:<br><br>
 *     {"title":"...",<br>
 *     "description":"...",<br>
 *     "color":"#...",<br>
 *     "created":"2017-04-24T12:00:00+05:00",<br>
 *     "edited":"...",<br>
 *     "viewed":"..."}<br>
 *     <br>
 *     Date format: YYYY-MM-DDThh:mm:ss±hh:mm (https://ru.wikipedia.org/wiki/ISO_8601)
 *
 * @author Vadim Gamaliev
 *         <a href="mailto:gamaliev-vadim@yandex.com">(e-mail: gamaliev-vadim@yandex.com)</a>
 */

public final class FileUtils {

    /* Logger */
    @NonNull private static final String TAG = FileUtils.class.getSimpleName();

    /* ... */
    @NonNull public static final String FILE_NAME_EXPORT_DEFAULT = "item_list.ili";
    @NonNull private static final CommonUtils.LooperHandlerThread IMPORT_EXPORT_HANDLER_LOOPER_THREAD;


    /*
        Init
     */

    static {
        IMPORT_EXPORT_HANDLER_LOOPER_THREAD = new CommonUtils.LooperHandlerThread();
        IMPORT_EXPORT_HANDLER_LOOPER_THREAD.start();
    }

    private FileUtils() {}


    /*
        EXPORT
     */

    /**
     * Starting the export in asynchronous mode,
     * using a dedicated shared thread, with looper and message queue.<br>
     *
     * @param context       Context.
     * @param selectedFile  Selected file.
     */
    public static void exportEntriesAsync(
            @NonNull final Context context,
            @NonNull final Uri selectedFile) {

        IMPORT_EXPORT_HANDLER_LOOPER_THREAD.getHandler().post(new Runnable() {
            @Override
            public void run() {
                exportEntries(context, selectedFile);
            }
        });
    }

    /**
     * Export entries from database to file with Json-format.<br>
     * If the operation time is longer than the specified time, then progress notification enable.
     *
     * @param context               Context.
     * @param selectedFile          Selected file.
     */
    private static void exportEntries(
            @NonNull final Context context,
            @NonNull final Uri selectedFile) {

        showToastRunOnUiThread(
                context.getString(R.string.file_utils_export_notification_start),
                Toast.LENGTH_SHORT);

        final ProgressNotificationHelper notification =
                new ProgressNotificationHelper(
                        context,
                        context.getString(R.string.file_utils_export_notification_panel_title),
                        context.getString(R.string.file_utils_export_notification_panel_text),
                        context.getString(R.string.file_utils_export_notification_panel_finish));
        notification.startTimerToEnableNotification(
                getProgressNotificationTimerForCurrentUser(context.getApplicationContext()),
                false);

        final JSONArray jsonArray = new JSONArray();
        final String result = getEntriesFromDatabase(
                context,
                jsonArray,
                notification);

        if (result == null) {
            Log.e(TAG, "getEntriesFromDatabase() is null.");
        } else {
            saveStringToFile(
                    context,
                    jsonArray,
                    result,
                    selectedFile,
                    notification);
        }
    }

    /**
     * Get all entries from database.
     *
     * @param context       Context.
     * @param jsonArray     JSONArray-object to fill.
     * @param notification  Notification helper.
     *
     * @return String in needed Json-format, containing all entries from database.
     */
    @Nullable
    private static String getEntriesFromDatabase(
            @NonNull final Context context,
            @NonNull final JSONArray jsonArray,
            @NonNull final ProgressNotificationHelper notification) {

        final Cursor cursor = getEntries(
                context,
                LIST_ITEMS_TABLE_NAME,
                null);

        if (cursor == null) {
            return null;
        }

        final int size = cursor.getCount();
        int percent = 0;
        int counter = 0;
        JSONObject jsonObject;

        while (true) {
            try {
                if (!cursor.moveToNext()) {
                    break;
                }

                jsonObject = getJsonObjectFromCursor(context, cursor);

            // Catch clause is used, because during the export there can be changes.
            } catch (IllegalStateException e) {
                Log.e(TAG, e.toString());
                continue;
            }

            jsonArray.put(jsonObject);

            // Update progress. Without flooding. 0-100%
            final int percentNew = counter++ * 100 / size;
            if (percentNew > percent) {
                percent = percentNew;
                notification.setProgress(100, percentNew);
            }
        }

        cursor.close();
        return jsonArray.toString();
    }

    /**
     * Saving given string to file, and notifying.
     */
    private static void saveStringToFile(
            @NonNull final Context context,
            @NonNull final JSONArray jsonArray,
            @NonNull final String result,
            @NonNull final Uri selectedFile,
            @NonNull final ProgressNotificationHelper notification) {

        try {
            final OutputStream os = context
                    .getContentResolver()
                    .openOutputStream(selectedFile);
            if (os == null) {
                throw new IOException("OutputStream is null.");
            }
            os.write(result.getBytes(StandardCharsets.UTF_8));
            os.close();

            showToastRunOnUiThread(
                    context.getString(R.string.file_utils_export_toast_message_success)
                            + " (" + jsonArray.length() + ")",
                    Toast.LENGTH_LONG);

            notifyObservers(
                    FILE_EXPORT,
                    RESULT_CODE_NOTES_EXPORTED,
                    null);

            notification.endProgress();

        } catch (IOException e) {
            Log.e(TAG, e.toString());
            showToastRunOnUiThread(
                    context.getString(R.string.file_utils_export_toast_message_failed),
                    Toast.LENGTH_SHORT);
        }
    }


    /*
        IMPORT
     */

    /**
     * Starting the import in asynchronous mode,
     * using a dedicated shared thread, with looper and message queue.<br>
     *
     * @param context       Context.
     * @param selectedFile  Selected file.
     */
    public static void importEntriesAsync(
            @NonNull final Context context,
            @NonNull final Uri selectedFile) {

        IMPORT_EXPORT_HANDLER_LOOPER_THREAD.getHandler().post(new Runnable() {
            @Override
            public void run() {
                importEntries(context, selectedFile);
            }
        });
    }

    /**
     * Import entries from file to database.<br>
     * If the operation time is longer than the specified time, then progress notification enable.
     *
     * @param context       Context.
     * @param selectedFile  Selected file.
     */
    private static void importEntries(
            @NonNull final Context context,
            @NonNull final Uri selectedFile) {

        showToastRunOnUiThread(
                context.getString(R.string.file_utils_import_notification_start),
                Toast.LENGTH_SHORT);

        final ProgressNotificationHelper notification =
                new ProgressNotificationHelper(
                        context,
                        context.getString(R.string.file_utils_import_notification_panel_title),
                        context.getString(R.string.file_utils_import_notification_panel_text),
                        context.getString(R.string.file_utils_import_notification_panel_finish));
        notification.startTimerToEnableNotification(
                getProgressNotificationTimerForCurrentUser(context.getApplicationContext()),
                false);

        final String inputJson = getStringFromFile(context, selectedFile);
        if (inputJson == null) {
            Log.e(TAG, "getStringFromFile() is null.");
        } else {
            parseAndSaveToDatabase(
                    context,
                    inputJson,
                    notification);
        }
    }

    /**
     * @param context       Context.
     * @param selectedFile  File with string.
     */
    @Nullable
    private static String getStringFromFile(
            @NonNull final Context context,
            @NonNull final Uri selectedFile) {

        String inputJson = null;
        try {
            final InputStream is = context
                    .getContentResolver()
                    .openInputStream(selectedFile);
            if (is == null) {
                throw new IOException("InputStream is null.");
            }
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] buffer = new byte[1024];
            int count;
            while ((count = is.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }

            bytes.close();
            is.close();

            inputJson = bytes.toString("UTF-8");

        } catch (IOException e) {
            Log.e(TAG, e.toString());
            showToastRunOnUiThread(
                    context.getString(R.string.file_utils_import_toast_message_failed),
                    Toast.LENGTH_SHORT);
        }

        return inputJson;
    }

    /**
     * Parse given string in Json-format, and save to database.
     *
     * @param context   Context.
     * @param inputJson String in Json-format.
     */
    private static void parseAndSaveToDatabase(
            @NonNull final Context context,
            @NonNull final String inputJson,
            @NonNull final ProgressNotificationHelper notification) {

        try {
            final SQLiteDatabase db = getWritableDb(context.getApplicationContext());
            if (db == null) {
                throw new SQLiteException(getDbFailMessage());
            }

            final JSONArray jsonArray = new JSONArray(inputJson);
            final int size = jsonArray.length();
            int percent = 0;

            db.beginTransaction();
            try {
                for (int i = 0; i < size; i++) {
                    final JSONObject jsonObject = jsonArray.getJSONObject(i);
                    final ListEntry entry = convertJsonToListEntry(context, jsonObject);
                    ListDbHelper.insertUpdateEntry(context, entry, db, false);

                    // Update progress. Without flooding. 0-100%
                    final int percentNew = i * 100 / size;
                    if (percentNew > percent) {
                        percent = percentNew;
                        notification.setProgress(100, percentNew);
                    }

                    //
                    if (i % 100 == 0) {
                        db.yieldIfContendedSafely();
                    }
                }
                db.setTransactionSuccessful();

            } finally {
                db.endTransaction();
            }

            makeSuccessImportOperations(
                    context,
                    jsonArray,
                    notification);

        } catch (JSONException | SQLiteException e) {
            Log.e(TAG, e.toString());
            showToastRunOnUiThread(
                    context.getString(R.string.file_utils_import_toast_message_failed),
                    Toast.LENGTH_SHORT);
        }
    }

    private static void makeSuccessImportOperations(
            @NonNull final Context context,
            @NonNull final JSONArray jsonArray,
            @NonNull final ProgressNotificationHelper notification) {

        notification.endProgress();

        showToastRunOnUiThread(
                context.getString(R.string.file_utils_import_toast_message_success)
                        + " (" + jsonArray.length() + ")",
                Toast.LENGTH_LONG);

        notifyObservers(
                FILE_IMPORT,
                RESULT_CODE_NOTES_IMPORTED,
                null);
    }


    /*
        Getters
     */

    /**
     * Typically used to initialize a variable, and running thread.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static CommonUtils.LooperHandlerThread getImportExportHandlerLooperThread() {
        return IMPORT_EXPORT_HANDLER_LOOPER_THREAD;
    }
}

package com.gamaliev.notes.conflict;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gamaliev.notes.R;
import com.gamaliev.notes.common.db.DbQueryBuilder;
import com.gamaliev.notes.common.shared_prefs.SpCommon;
import com.gamaliev.notes.common.shared_prefs.SpUsers;
import com.gamaliev.notes.list.db.ListDbHelper;
import com.gamaliev.notes.model.ListEntry;
import com.gamaliev.notes.model.SyncEntry;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import retrofit2.Response;

import static android.app.Activity.RESULT_OK;
import static com.gamaliev.notes.common.CommonUtils.EXTRA_REVEAL_ANIM_CENTER_CENTER;
import static com.gamaliev.notes.common.CommonUtils.showToastRunOnUiThread;
import static com.gamaliev.notes.common.DialogFragmentUtils.initCircularRevealAnimation;
import static com.gamaliev.notes.common.db.DbHelper.LIST_ITEMS_COLUMN_SYNC_ID;
import static com.gamaliev.notes.common.db.DbHelper.SYNC_CONFLICT_COLUMN_SYNC_ID;
import static com.gamaliev.notes.common.db.DbHelper.SYNC_CONFLICT_TABLE_NAME;
import static com.gamaliev.notes.common.db.DbHelper.getWritableDb;
import static com.gamaliev.notes.list.db.ListDbHelper.deleteEntryWithSingleSyncIdColumn;
import static com.gamaliev.notes.list.db.ListDbHelper.insertUpdateEntry;
import static com.gamaliev.notes.rest.NoteApiUtils.getNoteApi;
import static com.gamaliev.notes.sync.SyncUtils.API_KEY_DATA;
import static com.gamaliev.notes.sync.SyncUtils.API_KEY_EXTRA;
import static com.gamaliev.notes.sync.SyncUtils.API_KEY_ID;
import static com.gamaliev.notes.sync.SyncUtils.API_KEY_STATUS;
import static com.gamaliev.notes.sync.SyncUtils.API_STATUS_OK;
import static com.gamaliev.notes.sync.SyncUtils.RESULT_CODE_SUCCESS;
import static com.gamaliev.notes.sync.SyncUtils.makeOperations;
import static com.gamaliev.notes.sync.db.SyncDbHelper.ACTION_ADDED_TO_LOCAL;
import static com.gamaliev.notes.sync.db.SyncDbHelper.ACTION_TEXT;
import static com.gamaliev.notes.sync.db.SyncDbHelper.STATUS_OK;

/**
 * @author Vadim Gamaliev
 *         <a href="mailto:gamaliev-vadim@yandex.com">(e-mail: gamaliev-vadim@yandex.com)</a>
 */

public class ConflictSelectDialogFragment extends DialogFragment {

    /* Logger */
    private static final String TAG = ConflictSelectDialogFragment.class.getSimpleName();

    /* ... */
    private static final String ARG_SYNC_ID = "syncId";
    private static final String ARG_POSITION = "position";

    @Nullable private View mDialog;
    @NonNull private String mSyncId;
    @NonNull private int mPosition;


    /*
        Init
     */

    public ConflictSelectDialogFragment() {}

    public static ConflictSelectDialogFragment newInstance(
            @NonNull final String syncId,
            final int position) {

        final Bundle args = new Bundle();
        args.putString(ARG_SYNC_ID, syncId);
        args.putInt(ARG_POSITION, position);

        final ConflictSelectDialogFragment fragment = new ConflictSelectDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }


    /*
        Lifecycle
     */

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSyncId = getArguments().getString(ARG_SYNC_ID);
        mPosition = getArguments().getInt(ARG_POSITION);
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            Bundle savedInstanceState) {

        mDialog = inflater.inflate(R.layout.fragment_dialog_conflict_select, null);
        initCircularRevealAnimation(
                mDialog,
                true,
                EXTRA_REVEAL_ANIM_CENTER_CENTER);

        // Disable title for more space.
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        initServerLayoutAsync();
        initLocalLayout();

        return mDialog;
    }

    @Override
    public void onResume() {

        // Set max size of dialog. ( XML is not work :/ )
        final DisplayMetrics displayMetrics = getActivity().getResources().getDisplayMetrics();
        final ViewGroup.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = Math.min(
                displayMetrics.widthPixels,
                getActivity().getResources().getDimensionPixelSize(
                        R.dimen.fragment_dialog_conflict_select_max_width));
        params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        getDialog().getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

        super.onResume();
    }


    /*
        ...
     */

    private void initServerLayoutAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                initServerLayout();
            }
        }).start();
    }

    private void initServerLayout() {

        // Get entry from server.
        try {
            final Response<String> response = getNoteApi()
                    .get(SpUsers.getSyncIdForCurrentUser(getContext()), mSyncId)
                    .execute();

            if (response.isSuccessful()) {
                final JSONObject jsonResponse = new JSONObject(response.body());
                final String status = jsonResponse.optString(API_KEY_STATUS);

                if (status.equals(API_STATUS_OK)) {
                    final String data = jsonResponse.getString(API_KEY_DATA);

                    if (!TextUtils.isEmpty(data)) {
                        final Map<String, String> mapServer = SpCommon.convertJsonToMap(data);
                        mapServer.remove(API_KEY_ID);
                        mapServer.remove(API_KEY_EXTRA);

                        // Header and body text.
                        final TextView serverHeaderTv = (TextView) mDialog
                                .findViewById(R.id.fragment_dialog_conflict_select_server_header_tv);
                        final TextView serverBodyTv = (TextView) mDialog
                                .findViewById(R.id.fragment_dialog_conflict_select_server_body_tv);

                        final String textServer = SpCommon.convertMapToString(mapServer);
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                serverHeaderTv.setText(
                                        getString(R.string.fragment_dialog_conflict_select_server_header_prefix)
                                                + ": "
                                                + mSyncId);
                                serverBodyTv.setText(textServer);
                            }
                        });

                        // Save button.
                        final Button serverSaveBtn = (Button) mDialog
                                .findViewById(R.id.fragment_dialog_conflict_select_server_save_btn);
                        serverSaveBtn.setOnClickListener(getSrvBtnSaveOnClickListener(data));
                    }
                }
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void initLocalLayout() {

        // Get entry from database
        final DbQueryBuilder queryBuilder = new DbQueryBuilder();
        queryBuilder.addOr(
                LIST_ITEMS_COLUMN_SYNC_ID,
                DbQueryBuilder.OPERATOR_EQUALS,
                new String[]{mSyncId});
        final Cursor entryCursor = ListDbHelper.getEntries(getContext(), queryBuilder);
        entryCursor.moveToFirst();
        final JSONObject jsonObject = ListEntry.getJsonObject(getContext(), entryCursor);
        final String textLocal = SpCommon.convertJsonToString(jsonObject.toString());
        entryCursor.close();

        // Header and body text.
        final TextView localHeaderTv = (TextView) mDialog
                .findViewById(R.id.fragment_dialog_conflict_select_local_header_tv);
        final TextView localBodyTv = (TextView) mDialog
                .findViewById(R.id.fragment_dialog_conflict_select_local_body_tv);

        localHeaderTv.setText(
                getString(R.string.fragment_dialog_conflict_select_local_header_prefix)
                        + ": "
                        + mSyncId);
        localBodyTv.setText(textLocal);

        // Save button.
        final Button localSaveBtn = (Button) mDialog
                .findViewById(R.id.fragment_dialog_conflict_select_local_save_btn);
        localSaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    @NonNull
    private View.OnClickListener getSrvBtnSaveOnClickListener(@NonNull final String data) {

        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create entry
                final ListEntry entry = ListEntry
                        .convertJsonToListEntry(getContext(), data);
                entry.setSyncId(Long.parseLong(mSyncId));

                // Begin transaction.
                final SQLiteDatabase db = getWritableDb(getContext());
                db.beginTransaction();

                try {
                    // Update in local.
                    insertUpdateEntry(
                            getContext(),
                            entry,
                            db,
                            true);

                    // Delete from conflicted table.
                    final boolean result = deleteEntryWithSingleSyncIdColumn(
                            getContext(),
                            mSyncId,
                            SYNC_CONFLICT_TABLE_NAME,
                            SYNC_CONFLICT_COLUMN_SYNC_ID,
                            db,
                            true);

                    if (!result) {
                        throw new SQLiteException(
                                "[ERROR] Delete entry from conflict table is failed.");
                    }

                    // If ok.
                    db.setTransactionSuccessful();

                    // Add to journal. Add to local finish.
                    final SyncEntry addToLocalFinish = new SyncEntry();
                    addToLocalFinish.setFinished(new Date());
                    addToLocalFinish.setAction(ACTION_ADDED_TO_LOCAL);
                    addToLocalFinish.setStatus(STATUS_OK);
                    addToLocalFinish.setAmount(1);
                    makeOperations(
                            getContext(),
                            addToLocalFinish,
                            true,
                            getContext().getString(ACTION_TEXT[ACTION_ADDED_TO_LOCAL]),
                            RESULT_CODE_SUCCESS);

                    // Callback
                    getTargetFragment().onActivityResult(
                            getTargetRequestCode(),
                            RESULT_OK,
                            ConflictFragment.getResultIntent(mPosition));

                } catch (SQLiteException e) {
                    Log.e(TAG, e.toString());
                    showToastRunOnUiThread(
                            getActivity(),
                            getString(R.string.fragment_dialog_conflict_resolution_failed),
                            Toast.LENGTH_LONG);

                } finally {
                    db.endTransaction();
                }

                //
                dismiss();
            }
        };
    }
}

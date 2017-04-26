package com.gamaliev.list.list;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.transition.TransitionInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.gamaliev.list.R;
import com.gamaliev.list.colorpicker.ColorPickerActivity;

import static com.gamaliev.list.common.CommonUtils.getDefaultColor;
import static com.gamaliev.list.common.CommonUtils.getResourceColorApi;

public class ItemDetailsActivity extends AppCompatActivity {

    /* Action */
    private static final String ACTION_ADD      = "ItemDetailsActivity.ACTION_ADD";
    private static final String ACTION_EDIT     = "ItemDetailsActivity.ACTION_EDIT";

    /* Extra */
    private static final String EXTRA_ID        = "ItemDetailsActivity.EXTRA_ID";
    private static final String EXTRA_ENTRY     = "ItemDetailsActivity.EXTRA_ENTRY";

    /* Request code */
    private static final int REQUEST_CODE_COLOR = 1;

    /* */
    @NonNull private ListDatabaseHelper dbHelper;
    @NonNull private ActionBar actionBar;
    @NonNull private View colorView;
    @NonNull private EditText titleEditText;
    @NonNull private EditText descEditText;
    @NonNull private ListEntry entry;
    @Nullable private Bundle savedInstanceState;
    private int color;


    /*
        Init
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details);
        init(savedInstanceState);
    }

    private void init(@Nullable final Bundle savedInstanceState) {
        actionBar       = getSupportActionBar();
        colorView       = findViewById(R.id.activity_item_details_color);
        titleEditText   = (EditText) findViewById(R.id.activity_item_details_text_view_title);
        descEditText    = (EditText) findViewById(R.id.activity_item_details_text_view_description);
        this.savedInstanceState = savedInstanceState;

        enableEnterSharedTransition();
        setColorBoxListener();
        processAction();
    }


    /*
        Methods
     */

    /**
     * Enable shared transition. Work if API >= 21.
     */
    private void enableEnterSharedTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setSharedElementEnterTransition(
                    TransitionInflater
                            .from(this)
                            .inflateTransition(R.transition.transition_activity_1));
            findViewById(android.R.id.content).invalidate();
        }
    }

    /**
     * Set color box listener.<br>
     * Start choosing color activity on click.<br>
     * If API >= 21, then enable shared transition color box.
     */
    private void setColorBoxListener() {
        colorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // After start another activity - refreshed entry will be use in
                // onSaveInstanceState()-method.
                refreshEntry();

                // Start activity for result.
                // If API >= 21, then use shared transition animation.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                    // Prepare.
                    View colorBox = findViewById(R.id.activity_item_details_color);
                    colorBox.setTransitionName(
                            getString(R.string.shared_transition_name_color_box));
                    Pair<View, String> icon = new Pair<>(colorBox, colorBox.getTransitionName());
                    ActivityOptionsCompat aoc =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(
                                    ItemDetailsActivity.this, icon);

                    // Start activity for result with shared transition animation.
                    ColorPickerActivity.startIntent(
                            ItemDetailsActivity.this,
                            color,
                            REQUEST_CODE_COLOR,
                            aoc.toBundle());

                } else {
                    // Start activity for result without shared transition animation.
                    ColorPickerActivity.startIntent(
                            ItemDetailsActivity.this,
                            color,
                            REQUEST_CODE_COLOR,
                            null);
                }
            }
        });
    }

    /**
     * Process Action, when start activity.<br>
     * Filling in the necessary views.<br>
     * See also: {@link #ACTION_ADD}, {@link #ACTION_EDIT}.
     */
    private void processAction() {
        switch (getIntent().getAction()) {

            // Start activity with Add action.
            case ACTION_ADD:
                actionBar.setTitle(getResources().getString(R.string.activity_item_details_title_add));

                if (savedInstanceState == null) {

                    // On first start activity.
                    // Set color box with default color, and create new entry.
                    refreshColorBox(getDefaultColor(this));
                    entry = new ListEntry();
                    refreshEntry();
                } else {

                    // On restart activity.
                    // Fill activity views values with restored entry-values.
                    entry = savedInstanceState.getParcelable(EXTRA_ENTRY);
                    fillActivityViews();
                }
                break;

            // Start activity with Edit action.
            case ACTION_EDIT:
                actionBar.setTitle(getResources().getString(R.string.activity_item_details_title_edit));

                if (savedInstanceState == null) {

                    // On first start activity. Get entry from database, with given id.
                    long id     = getIntent().getLongExtra(EXTRA_ID, -1);
                    dbHelper    = new ListDatabaseHelper(this);
                    entry       = dbHelper.getEntry(id);
                    dbHelper.close();

                    if (entry != null) {
                        // Fill activity views values with received values.
                        fillActivityViews();

                    } else {
                        // If received object is null, then fill by default values.
                        refreshColorBox(getDefaultColor(this));
                        entry = new ListEntry();
                        refreshEntry();
                    }

                } else {

                    // On restart activity.
                    // Fill activity views values with restored entry-values.
                    entry = savedInstanceState.getParcelable(EXTRA_ENTRY);
                    fillActivityViews();
                }
                break;

            default:
                break;
        }
    }

    /**
     * Fill all activity views with values from the entry-object.
     */
    private void fillActivityViews() {
        titleEditText.setText(entry.getTitle());
        descEditText.setText(entry.getDescription());
        refreshColorBox(entry.getColor());
    }

    /**
     * Fill entry-fields with values from all activity views.
     */
    private void refreshEntry() {
        entry.setTitle(titleEditText.getText().toString());
        entry.setDescription(descEditText.getText().toString());
        entry.setColor(color);
    }

    /**
     * Refresh color box, with given color, and color-variable of object.
     * @param color New color.
     */
    private void refreshColorBox(final int color) {
        this.color = color;
        colorView.setBackground(
                getGradientDrawableCircleWithBorder(color));
    }

    /**
     * @param color Color of drawable.
     * @return New gradient drawable, circle, with border.
     */
    @NonNull
    private GradientDrawable getGradientDrawableCircleWithBorder(final int color) {
        final GradientDrawable g = new GradientDrawable();
        g.setStroke(
                (int) getResources().getDimension(R.dimen.activity_item_details_ff_color_stroke_width),
                getResourceColorApi(this, android.R.color.primary_text_dark));
        g.setCornerRadius(getResources().getDimension(R.dimen.activity_item_details_ff_color_radius));
        g.setColor(color);
        return g;
    }


    /*
        Intents
     */

    /**
     * Start intent with Add action.
     * @param context       Context.
     * @param requestCode   This code will be returned in onActivityResult() when the activity exits.
     * See {@link #ACTION_ADD}.
     */
    public static void startAdd(
            @NonNull final Context context,
            final int requestCode) {

        Intent starter = new Intent(context, ItemDetailsActivity.class);
        starter.setAction(ACTION_ADD);
        ((Activity) context).startActivityForResult(starter, requestCode);
    }

    /**
     * Start intent with Edit action.
     * @param context       Context.
     * @param id            Id of entry, that link with intent, see: {@link #EXTRA_ID}.
     * @param requestCode   This code will be returned in onActivityResult() when the activity exits.
     * @param bundle        Additional options for how the Activity should be started.
     *                      If null, then start {@link android.app.Activity#startActivityForResult(Intent, int)},
     *                      otherwise start {@link android.app.Activity#startActivityForResult(Intent, int, Bundle)},
     * See also: {@link #ACTION_EDIT}.
     */
    public static void startEdit(
            @NonNull final Context context,
            final long id,
            final int requestCode,
            @Nullable final Bundle bundle) {

        Intent starter = new Intent(context, ItemDetailsActivity.class);
        starter.setAction(ACTION_EDIT);
        starter.putExtra(EXTRA_ID, id);
        if (bundle == null) {
            ((Activity) context).startActivityForResult(starter, requestCode);
        } else {
            ((Activity) context).startActivityForResult(starter, requestCode, bundle);
        }
    }

    /**
     * @param color Color.
     * @return Intent, with given color.
     * See {@link com.gamaliev.list.colorpicker.ColorPickerActivity#EXTRA_COLOR}
     */
    @NonNull
    public static Intent getResultColorIntent(final int color) {
        Intent intent = new Intent();
        intent.putExtra(ColorPickerActivity.EXTRA_COLOR, color);
        return intent;
    }

    /**
     * If color was selected, then refresh activity views.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_COLOR && resultCode == RESULT_OK) {
            if (data != null) {

                // Get selected color. If null, using default color.
                int color = data.getIntExtra(
                        ColorPickerActivity.EXTRA_COLOR,
                        getDefaultColor(ItemDetailsActivity.this));

                // Update entry, then activity views.
                entry.setColor(color);
                fillActivityViews();
            }
        }
    }


    /*
        Options menu
     */

    /**
     * Inflate action bar menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_list_item_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Action bar menu item selection handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            // On save action.
            // Create database -> process action -> close database.
            case R.id.menu_list_item_details_done:
                dbHelper = new ListDatabaseHelper(this);

                switch (getIntent().getAction()) {

                    // If new, then add to database, and finish activity with RESULT_OK.
                    // TODO: return result with notify;
                    case ACTION_ADD:
                        refreshEntry();
                        dbHelper.insertEntry(entry);
                        setResult(RESULT_OK);
                        finish();
                        break;

                    // If edit, then update entry in database, and finish activity with RESULT_OK.
                    // TODO: return result with notify;
                    case ACTION_EDIT:
                        refreshEntry();
                        dbHelper.updateEntry(entry);
                        setResult(RESULT_OK);
                        finish();
                        break;

                    default:
                        break;
                }

                dbHelper.close();
                break;

            // On cancel action. Finish activity.
            case R.id.menu_list_item_details_cancel:
                finish();
                break;

            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }


    /*
        On Restore / Save instance state
     */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(EXTRA_ENTRY, entry);
        super.onSaveInstanceState(outState);
    }
}
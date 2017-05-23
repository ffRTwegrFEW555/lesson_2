package com.gamaliev.notes.list.db;

import android.content.Context;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gamaliev.notes.R;
import com.gamaliev.notes.common.CommonUtils;
import com.gamaliev.notes.common.db.DbHelper;

/**
 * @author Vadim Gamaliev
 * <a href="mailto:gamaliev-vadim@yandex.com">(e-mail: gamaliev-vadim@yandex.com)</a>
 */

public final class ListCursorAdapter extends CursorAdapter {

    public ListCursorAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {

        // Create new view, new view holder, and binding.
        final View view = LayoutInflater
                .from(context)
                .inflate(R.layout.fragment_list_item, parent, false);
        final ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get view holder.
        final ViewHolder viewHolder = (ViewHolder) view.getTag();

        // Get values from current row.
        final int indexTitle        = cursor.getColumnIndex(DbHelper.LIST_ITEMS_COLUMN_TITLE);
        final int indexDescription  = cursor.getColumnIndex(DbHelper.LIST_ITEMS_COLUMN_DESCRIPTION);
        final int indexEdited       = cursor.getColumnIndex(DbHelper.LIST_ITEMS_COLUMN_EDITED);
        final int indexColor        = cursor.getColumnIndex(DbHelper.LIST_ITEMS_COLUMN_COLOR);

        final String title          = cursor.getString(indexTitle);
        final String description    = cursor.getString(indexDescription);
        final String edited         = CommonUtils
                .convertUtcToLocal(context, cursor.getString(indexEdited))
                .split(" ")[0];
        final int color             = cursor.getInt(indexColor);

        // Fill view holder values.
        viewHolder.mTitleView.setText(title);
        viewHolder.mDescriptionView.setText(description);
        viewHolder.mEditedView.setText(edited);
        viewHolder.mIconView
                .getBackground()
                .setColorFilter(color, PorterDuff.Mode.SRC);
    }

    private static class ViewHolder {
        private final TextView  mTitleView;
        private final TextView  mDescriptionView;
        private final TextView  mEditedView;
        private final View      mIconView;

        ViewHolder(View view) {
            mTitleView          = (TextView) view.findViewById(R.id.fragment_list_item_title);
            mDescriptionView    = (TextView) view.findViewById(R.id.fragment_list_item_description);
            mEditedView         = (TextView) view.findViewById(R.id.fragment_list_item_edited);
            mIconView           = view.findViewById(R.id.fragment_list_item_color);
        }
    }
}

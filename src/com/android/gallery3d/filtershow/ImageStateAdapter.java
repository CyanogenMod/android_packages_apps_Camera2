
package com.android.gallery3d.filtershow;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class ImageStateAdapter extends ArrayAdapter<ImageFilter> {
    private static final String LOGTAG = "ImageStateAdapter";

    public ImageStateAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.filtershow_imagestate_row, null);
        }
        ImageFilter filter = getItem(position);
        if (filter != null) {
            TextView itemLabel = (TextView) view.findViewById(R.id.imagestate_label);
            itemLabel.setText(filter.getName());
            TextView itemParameter = (TextView) view.findViewById(R.id.imagestate_parameter);
            itemParameter.setText("" + filter.getParameter());
        }
        return view;
    }
}

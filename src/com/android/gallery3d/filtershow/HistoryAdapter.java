
package com.android.gallery3d.filtershow;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.util.Vector;

public class HistoryAdapter extends ArrayAdapter<ImagePreset> {
    private static final String LOGTAG = "HistoryAdapter";
    private int mCurrentPresetPosition = 0;

    public void setCurrentPreset(int n) {
        mCurrentPresetPosition = n;
        this.notifyDataSetChanged();
    }

    public HistoryAdapter(Context context, int resource, int textViewResourceId) {
        super(context, resource, textViewResourceId);
    }

    public void reset() {
        if (getCount() == 0) {
            return;
        }
        ImagePreset first = getItem(getCount() - 1);
        clear();
        addHistoryItem(first);
    }

    public ImagePreset getLast() {
        if (getCount() == 0) {
            return null;
        }
        return getItem(0);
    }

    public void addHistoryItem(ImagePreset preset) {
        if (canAddHistoryItem(preset)) {
            insert(preset, 0);
        }
    }

    public boolean canAddHistoryItem(ImagePreset preset) {
        if (getCount() > 0 && getLast().same(preset)) {
            // we may still want to insert if the previous
            // history element isn't the same
            if (getLast().historyName().equalsIgnoreCase(preset.historyName())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void insert(ImagePreset preset, int position) {
        if (mCurrentPresetPosition != 0) {
            // in this case, let's discount the presets before the current one
            Vector<ImagePreset> oldItems = new Vector<ImagePreset>();
            for (int i = mCurrentPresetPosition; i < getCount(); i++) {
                oldItems.add(getItem(i));
            }
            clear();
            for (int i = 0; i < oldItems.size(); i++) {
                add(oldItems.elementAt(i));
            }
            mCurrentPresetPosition = position;
            this.notifyDataSetChanged();
            if (!canAddHistoryItem(preset)) {
                return;
            }
        }
        super.insert(preset, position);
        mCurrentPresetPosition = position;
        this.notifyDataSetChanged();
    }

    public int redo() {
        mCurrentPresetPosition--;
        if (mCurrentPresetPosition < 0) {
            mCurrentPresetPosition = 0;
        }
        this.notifyDataSetChanged();
        return mCurrentPresetPosition;
    }

    public int undo() {
        mCurrentPresetPosition++;
        if (mCurrentPresetPosition >= getCount()) {
            mCurrentPresetPosition = getCount() - 1;
        }
        this.notifyDataSetChanged();
        return mCurrentPresetPosition;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.filtershow_history_operation_row, null);
        }

        ImagePreset item = getItem(position);
        if (item != null) {
            TextView itemView = (TextView) view.findViewById(R.id.rowTextView);
            if (itemView != null) {
                itemView.setText(item.historyName());
            }
            ImageView markView = (ImageView) view.findViewById(R.id.selectedMark);
            if (position == mCurrentPresetPosition) {
                markView.setVisibility(View.VISIBLE);
            } else {
                markView.setVisibility(View.INVISIBLE);
            }
            ImageView typeView = (ImageView) view.findViewById(R.id.typeMark);
            // TODO: use type of last filter, not a string, to discriminate.
            if (position == getCount() - 1) {
                typeView.setImageResource(R.drawable.ic_photoeditor_effects);
            } else if (item.historyName().equalsIgnoreCase("Border")) {
                typeView.setImageResource(R.drawable.ic_photoeditor_border);
            } else if (item.historyName().equalsIgnoreCase("Straighten")) {
                typeView.setImageResource(R.drawable.ic_photoeditor_fix);
            } else if (item.historyName().equalsIgnoreCase("Crop")) {
                typeView.setImageResource(R.drawable.ic_photoeditor_fix);
            } else if (item.historyName().equalsIgnoreCase("Rotation")) {
                typeView.setImageResource(R.drawable.ic_photoeditor_fix);
            } else if (item.historyName().equalsIgnoreCase("Mirror")) {
                typeView.setImageResource(R.drawable.ic_photoeditor_fix);
           } else if (item.isFx()) {
                typeView.setImageResource(R.drawable.ic_photoeditor_effects);
            } else {
                typeView.setImageResource(R.drawable.ic_photoeditor_color);
            }
        }

        return view;
    }

}

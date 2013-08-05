package com.android.gallery3d.filtershow.controller;

import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.RenderingRequest;
import com.android.gallery3d.filtershow.cache.RenderingRequestCaller;
import com.android.gallery3d.filtershow.editors.Editor;

import java.util.Vector;

public class StyleChooser implements Control {
    private final String LOGTAG = "StyleChooser";
    protected ParameterStyles mParameter;
    protected LinearLayout mLinearLayout;
    protected Editor mEditor;
    private View mTopView;
    private Vector<ImageButton> mIconButton = new Vector<ImageButton>();
    protected int mLayoutID = R.layout.filtershow_control_style_chooser;

    @Override
    public void setUp(ViewGroup container, Parameter parameter, Editor editor) {
        container.removeAllViews();
        mEditor = editor;
        Context context = container.getContext();
        mParameter = (ParameterStyles) parameter;
        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mTopView = inflater.inflate(mLayoutID, container, true);
        mLinearLayout = (LinearLayout) mTopView.findViewById(R.id.listStyles);
        mTopView.setVisibility(View.VISIBLE);
        int n = mParameter.getNumberOfStyles();
        mIconButton.clear();
        LayoutParams lp = new LayoutParams(120, 120);
        for (int i = 0; i < n; i++) {
            final ImageButton button = new ImageButton(context);
            button.setScaleType(ScaleType.CENTER_CROP);
            button.setLayoutParams(lp);
            button.setBackgroundResource(android.R.color.transparent);
            mIconButton.add(button);
            final int buttonNo = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    mParameter.setSelected(buttonNo);
                }
            });
            mLinearLayout.addView(button);
            mParameter.getIcon(i, new RenderingRequestCaller() {
                @Override
                public void available(RenderingRequest request) {
                    Bitmap bmap = request.getBitmap();
                    if (bmap == null) {
                        return;
                    }
                    button.setImageBitmap(bmap);
                }
            });
        }
    }

    @Override
    public View getTopView() {
        return mTopView;
    }

    @Override
    public void setPrameter(Parameter parameter) {
        mParameter = (ParameterStyles) parameter;
        updateUI();
    }

    @Override
    public void updateUI() {
        if (mParameter == null) {
            return;
        }
    }

}

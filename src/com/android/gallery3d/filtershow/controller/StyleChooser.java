package com.android.gallery3d.filtershow.controller;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.RenderingRequest;
import com.android.gallery3d.filtershow.cache.RenderingRequestCaller;
import com.android.gallery3d.filtershow.editors.Editor;

import java.util.Vector;

public class StyleChooser implements Control, RenderingRequestCaller {
    private final String LOGTAG = "StyleChooser";
    protected ParameterStyles mParameter;
    protected LinearLayout mLinearLayout;
    protected Editor mEditor;
    private View mTopView;
    private int mProcessingButton = 0;
    private Vector<Button> mIconButton = new Vector<Button>();
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
        for (int i = 0; i < n; i++) {
            Button button = new Button(context);
            mIconButton.add(button);
            final int buttonNo = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    mParameter.setSelected(buttonNo);
                }
            });
            mLinearLayout.addView(button);
        }
        mProcessingButton = 0;
        mParameter.getIcon(mProcessingButton, this);
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

    @Override
    public void available(RenderingRequest request) {
        Bitmap bmap = request.getBitmap();
        if (bmap == null) {
            return;
        }

        try {
            Button button = mIconButton.get(mProcessingButton);
            Resources res = mLinearLayout.getContext().getResources();
            BitmapDrawable drawable = new BitmapDrawable(res, bmap);
            float scale = 12000 / (float) button.getWidth();
            ScaleDrawable sd = new ScaleDrawable(drawable, 0, scale, scale);

            button.setCompoundDrawablesWithIntrinsicBounds(null, sd, null, null);
        } catch (Exception e) {
            return;
        }

        mProcessingButton++;
        if (mProcessingButton < mParameter.getNumberOfStyles())
            mParameter.getIcon(mProcessingButton, this);
    }

}

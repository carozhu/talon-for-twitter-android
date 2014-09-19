package com.klinker.android.twitter_l.manipulations.widgets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ShareActionProvider;
import com.klinker.android.twitter_l.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AlwaysLightShareActionProvider extends ShareActionProvider {

    private final Context mContext;

    /**
     * Creates a new instance.
     *
     * @param context Context for accessing resources.
     */
    public AlwaysLightShareActionProvider(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public View onCreateActionView() {
        View chooserView =
                super.onCreateActionView();

        // Set your drawable here
        Drawable icon =
                mContext.getResources().getDrawable(R.drawable.ic_action_share_dark);

        Class clazz = chooserView.getClass();

        //reflect all of this shit so that I can change the icon
        try {
            Method method = clazz.getMethod("setExpandActivityOverflowButtonDrawable", Drawable.class);
            method.invoke(chooserView, icon);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return chooserView;
    }
}
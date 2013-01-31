package com.google.android.canvas.provider;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.os.Bundle;

import com.android.gallery3d.provider.CanvasProvider;
import com.android.gallery3d.provider.CanvasProviderBase;


public class EnableSyncActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Account[] accounts = AccountManager.get(this).getAccountsByType(
                CanvasProvider.ACCOUNT_TYPE);
        for (Account account : accounts) {
            ContentResolver.setSyncAutomatically(account,
                    CanvasProvider.PHOTO_AUTHORITY, true);
        }
        finish();
        getContentResolver().notifyChange(CanvasProviderBase.NOTIFY_CHANGED_URI,
                null, false);
    }
}

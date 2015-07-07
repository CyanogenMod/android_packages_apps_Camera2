package com.android.camera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.android.camera.app.CameraServicesImpl;
import com.android.camera.debug.Log;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera2.R;

/**
 * Activity that shows permissions request dialogs and handles lack of critical permissions.
 */
public class PermissionsActivity extends Activity {
    private static final Log.Tag TAG = new Log.Tag("PermissionsActivity");

    private static int PERMISSION_REQUEST_CODE = 1;
    private static int RESULT_CODE_OK = 0;
    private static int RESULT_CODE_FAILED = 1;

    private int mIndexPermissionRequestCamera;
    private int mIndexPermissionRequestMicrophone;
    private int mIndexPermissionRequestLocation;
    private int mIndexPermissionRequestStorage;
    private boolean mShouldRequestCameraPermission;
    private boolean mShouldRequestMicrophonePermission;
    private boolean mShouldRequestLocationPermission;
    private boolean mShouldRequestStoragePermission;
    private int mNumPermissionsToRequest;
    private boolean mFlagHasCameraPermission;
    private boolean mFlagHasMicrophonePermission;
    private boolean mFlagHasStoragePermission;
    private SettingsManager mSettingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettingsManager = CameraServicesImpl.instance().getSettingsManager();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNumPermissionsToRequest = 0;
        checkPermissions();
    }

    private void checkPermissions() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            mNumPermissionsToRequest++;
            mShouldRequestCameraPermission = true;
        } else {
            mFlagHasCameraPermission = true;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            mNumPermissionsToRequest++;
            mShouldRequestMicrophonePermission = true;
        } else {
            mFlagHasMicrophonePermission = true;
        }

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            mNumPermissionsToRequest++;
            mShouldRequestStoragePermission = true;
        } else {
            mFlagHasStoragePermission = true;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            mNumPermissionsToRequest++;
            mShouldRequestLocationPermission = true;
        }

        if (mNumPermissionsToRequest != 0) {
            if (!mSettingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_HAS_SEEN_PERMISSIONS_DIALOGS)) {
                buildPermissionsRequest();
            } else {
                //Permissions dialog has already been shown, and we're still missing permissions.
                handlePermissionsFailure();
            }
        } else {
            handlePermissionsSuccess();
        }
    }

    private void buildPermissionsRequest() {
        String[] permissionsToRequest = new String[mNumPermissionsToRequest];
        int permissionsRequestIndex = 0;

        if (mShouldRequestCameraPermission) {
            permissionsToRequest[permissionsRequestIndex] = Manifest.permission.CAMERA;
            mIndexPermissionRequestCamera = permissionsRequestIndex;
            permissionsRequestIndex++;
        }
        if (mShouldRequestMicrophonePermission) {
            permissionsToRequest[permissionsRequestIndex] = Manifest.permission.RECORD_AUDIO;
            mIndexPermissionRequestMicrophone = permissionsRequestIndex;
            permissionsRequestIndex++;
        }
        if (mShouldRequestStoragePermission) {
            permissionsToRequest[permissionsRequestIndex] = Manifest.permission.READ_EXTERNAL_STORAGE;
            mIndexPermissionRequestStorage = permissionsRequestIndex;
            permissionsRequestIndex++;
        }
        if (mShouldRequestLocationPermission) {
            permissionsToRequest[permissionsRequestIndex] = Manifest.permission.ACCESS_COARSE_LOCATION;
            mIndexPermissionRequestLocation = permissionsRequestIndex;
        }

        requestPermissions(permissionsToRequest, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        mSettingsManager.set(
                SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_HAS_SEEN_PERMISSIONS_DIALOGS,
                true);

        if (mShouldRequestCameraPermission) {
            if (grantResults[mIndexPermissionRequestCamera] == PackageManager.PERMISSION_GRANTED) {
                mFlagHasCameraPermission = true;
            } else {
                handlePermissionsFailure();
            }
        }
        if (mShouldRequestMicrophonePermission) {
            if (grantResults[mIndexPermissionRequestMicrophone] == PackageManager.PERMISSION_GRANTED) {
                mFlagHasMicrophonePermission = true;
            } else {
                handlePermissionsFailure();
            }
        }
        if (mShouldRequestStoragePermission) {
            if (grantResults[mIndexPermissionRequestStorage] == PackageManager.PERMISSION_GRANTED) {
                mFlagHasStoragePermission = true;
            } else {
                handlePermissionsFailure();
            }
        }

        if (mShouldRequestLocationPermission) {
            if (grantResults[mIndexPermissionRequestLocation] == PackageManager.PERMISSION_GRANTED) {
                // Do nothing
            } else {
                // Do nothing
            }
        }

        if (mFlagHasCameraPermission && mFlagHasMicrophonePermission && mFlagHasStoragePermission) {
            handlePermissionsSuccess();
        }
    }

    private void handlePermissionsSuccess() {
        setResult(RESULT_CODE_OK, null);
        finish();
    }

    private void handlePermissionsFailure() {
        new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.camera_error_title))
                .setMessage(getResources().getString(R.string.error_permissions))
                .setPositiveButton(getResources().getString(R.string.dialog_dismiss), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(RESULT_CODE_FAILED, null);
                        finish();
                    }
                })
                .show();
    }
}

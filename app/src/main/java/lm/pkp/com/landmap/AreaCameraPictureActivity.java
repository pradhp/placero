package lm.pkp.com.landmap;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import lm.pkp.com.landmap.area.AreaContext;
import lm.pkp.com.landmap.area.AreaElement;
import lm.pkp.com.landmap.drive.DriveResource;
import lm.pkp.com.landmap.sync.LocalFolderStructureManager;
import lm.pkp.com.landmap.user.UserContext;

/**
 * Created by USER on 11/1/2017.
 */
public class AreaCameraPictureActivity extends Activity {

    // LogCat tag
    private static final String TAG = AreaCameraPictureActivity.class.getSimpleName();


    // Camera activity request codes
    private static final int CAMERA_CAPTURE_IMAGE_REQUEST_CODE = 100;
    public static final int MEDIA_TYPE_IMAGE = 1;

    private Uri fileUri; // file url to store image/video

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        captureImage();
    }

    /**
     * Launching camera app to capture image
     */
    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        fileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(intent, CAMERA_CAPTURE_IMAGE_REQUEST_CODE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("file_uri", fileUri);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        fileUri = savedInstanceState.getParcelable("file_uri");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // if the result is capturing Image
        if (requestCode == CAMERA_CAPTURE_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                File imageFile = new File(fileUri.getPath());
                AreaElement ae = AreaContext.getInstance().getAreaElement();

                DriveResource dr = new DriveResource();
                dr.setName(imageFile.getName());
                dr.setPath(fileUri.getPath());
                dr.setType("file");
                dr.setUserId(UserContext.getInstance().getUserElement().getEmail());
                dr.setSize(imageFile.length() + "");
                dr.setUniqueId(UUID.randomUUID().toString());
                dr.setAreaId(ae.getUniqueId());

                AreaContext.getInstance().addNewDriveResource(dr);

                Intent i = new Intent(AreaCameraPictureActivity.this, AreaAddResourcesActivity.class);
                startActivity(i);
            } else if (resultCode == RESULT_CANCELED) {
                // Cancelled case
                finish();
            } else {
                // Error case
                finish();
            }
        }
    }

    public Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * returning image / video
     */
    private static File getOutputMediaFile(int type) {
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File mediaFile = new File(LocalFolderStructureManager.getImageStorageDir().getPath()
                + File.separator + "IMG_" + timeStamp + ".jpg");
        return mediaFile;
    }
}

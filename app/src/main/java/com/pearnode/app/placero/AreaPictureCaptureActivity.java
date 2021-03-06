package com.pearnode.app.placero;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.pearnode.app.placero.area.AreaContext;
import com.pearnode.app.placero.area.model.Area;
import com.pearnode.app.placero.custom.GenericActivityExceptionHandler;
import com.pearnode.app.placero.custom.LocationPositionReceiver;
import com.pearnode.app.placero.media.model.Media;
import com.pearnode.app.placero.position.Position;
import com.pearnode.app.placero.provider.GPSLocationProvider;
import com.pearnode.common.DirtyActions;

/**
 * Created by USER on 11/1/2017.
 */
public class AreaPictureCaptureActivity extends Activity implements LocationPositionReceiver {


    // Camera activity request codes
    private static final int CAMERA_CAPTURE_IMAGE_REQUEST_CODE = 100;

    private Uri fileUri; // file url to store image/video_map
    private final Media media = new Media();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new GenericActivityExceptionHandler(this);
        startPositioning();
        captureImage();
    }

    private void startPositioning() {
        new GPSLocationProvider(this, this, 30).getLocation();
    }

    /**
     * Launching camera app to capture image
     */
    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        fileUri = getOutputMediaFileUri();
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
            if (resultCode == Activity.RESULT_OK) {
                File imageFile = new File(fileUri.getPath());
                AreaContext areaContext = AreaContext.INSTANCE;
                Area ae = areaContext.getArea();

                media.setName(imageFile.getName());
                media.setRfPath(imageFile.getAbsolutePath());
                media.setType("picture");
                media.setDirty(1);
                media.setDirtyAction("upload");

                areaContext.addMediaToQueue(media);
                Intent i = new Intent(this, AreaAddResourcesActivity.class);
                startActivity(i);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // Cancelled case
                finish();
            } else {
                // Error case
                finish();
            }
        }
    }

    public Uri getOutputMediaFileUri() {
        return Uri.fromFile(getOutputMediaFile());
    }

    /**
     * returning image / video_map
     */
    private static File getOutputMediaFile() {
        Area area = AreaContext.INSTANCE.getArea();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File localRoot = AreaContext.INSTANCE.getAreaLocalImageRoot(area.getId());
        return new File(localRoot.getAbsolutePath() + File.separator + "IMG_" + timeStamp + ".jpg");
    }

    @Override
    public void receivedLocationPostion(Position pe) {
        pe.setType("Media");
        pe.setDirty(1);
        pe.setDirtyAction(DirtyActions.CREATE);
        media.setLat(pe.getLat() + "");
        media.setLng(pe.getLng() + "");
    }

    @Override
    public void locationFixTimedOut() {
    }

    @Override
    public void providerDisabled() {
    }
}

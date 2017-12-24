package lm.pkp.com.landmap;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.MetadataChangeSet.Builder;
import com.google.android.gms.drive.events.ChangeEvent;
import com.google.android.gms.drive.events.ChangeListener;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import lm.pkp.com.landmap.R.layout;
import lm.pkp.com.landmap.area.AreaContext;
import lm.pkp.com.landmap.area.db.AreaDBHelper;
import lm.pkp.com.landmap.area.model.AreaElement;
import lm.pkp.com.landmap.custom.ApiClientAsyncTask;
import lm.pkp.com.landmap.custom.ThumbnailCreator;
import lm.pkp.com.landmap.drive.DriveDBHelper;
import lm.pkp.com.landmap.drive.DriveResource;
import lm.pkp.com.landmap.position.PositionElement;
import lm.pkp.com.landmap.position.PositionsDBHelper;
import lm.pkp.com.landmap.util.FileUtil;


/**
 * An activity to create a file inside a folder.
 */
public class UploadResourcesActivity extends BaseDriveActivity {

    private final Stack<DriveResource> processStack = new Stack<>();
    private List<String> uploadedResources = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(layout.activity_upload_area_resources);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        super.onConnected(connectionHint);
        ArrayList<DriveResource> resources = AreaContext.INSTANCE.getUploadedQueue();
        for (int i = 0; i < resources.size(); i++) {
            this.processStack.push(resources.get(i));
        }
        this.processResources();
    }

    @Override
    protected void handleConnectionIssues() {
        Intent areaDetailsIntent = new Intent(getApplicationContext(), AreaDetailsActivity.class);
        areaDetailsIntent.putExtra("action", "Upload");
        areaDetailsIntent.putExtra("outcome_type", "error");
        areaDetailsIntent.putExtra("outcome", "Connection issues.");
        startActivity(areaDetailsIntent);
    }

    private void processResources() {
        if (!this.processStack.isEmpty()) {
            this.processResource(this.processStack.pop());
        } else {
            Intent addResourcesIntent = new Intent(this, ShareUploadedResourcesActivity.class);
            addResourcesIntent.putExtra("uploaded_resource_ids", uploadedResources.toArray());
            getGoogleApiClient().disconnect();
            startActivity(addResourcesIntent);

            finish();
        }
    }

    private void processResource(DriveResource res) {
        new FileProcessingTask(res).execute();
    }

    private class FileProcessingTask extends AsyncTask {

        private DriveResource resource;

        public FileProcessingTask(DriveResource dr) {
            this.resource = dr;
        }

        @Override
        protected Object doInBackground(Object[] params) {

            DriveApi.DriveIdResult idResult
                    = Drive.DriveApi.fetchDriveId(getGoogleApiClient(), this.resource.getContainerId()).await();
            DriveId driveId = idResult.getDriveId();
            if(driveId == null){
                Intent addResourcesIntent = new Intent(getApplicationContext(), AreaAddResourcesActivity.class);
                addResourcesIntent.putExtra("action", "Upload");
                addResourcesIntent.putExtra("outcome_type", "error");
                addResourcesIntent.putExtra("outcome", "Upload location corrupted.");
                getGoogleApiClient().disconnect();
                startActivity(addResourcesIntent);

                finish();
            }else {
                DriveFolder folder = driveId.asDriveFolder();
                DriveApi.DriveContentsResult contentsResult
                        = Drive.DriveApi.newDriveContents(getGoogleApiClient()).await();
                DriveContents contents = contentsResult.getDriveContents();
                MetadataChangeSet changeSet = new Builder()
                        .setTitle(this.resource.getName())
                        .setMimeType(this.resource.getMimeType())
                        .build();
                DriveFolder.DriveFileResult driveFileResult = folder.createFile(getGoogleApiClient(), changeSet, contents).await();
                DriveFile createdFile = driveFileResult.getDriveFile();
                createdFile.addChangeListener(getGoogleApiClient(), new FileMetaChangeListener(this.resource, createdFile));
            }
            return null;
        }
    }

    private class FileMetaChangeListener implements ChangeListener {

        private DriveResource resource;
        private DriveFile driveFile;

        public FileMetaChangeListener(DriveResource res, DriveFile dFile) {
            this.resource = res;
            this.driveFile = dFile;
        }

        @Override
        public void onChange(ChangeEvent changeEvent) {
            if (changeEvent.hasMetadataChanged()) {
                DriveId driveId = changeEvent.getDriveId();
                this.resource.setResourceId(driveId.getResourceId());

                DriveDBHelper ddh = new DriveDBHelper(getApplicationContext());
                ddh.insertResourceLocally(resource);
                ddh.insertResourceToServer(resource);

                PositionsDBHelper pdh = new PositionsDBHelper(getApplicationContext());
                PositionElement position = resource.getPosition();
                if(position != null){
                    position.setUniqueAreaId(resource.getAreaId());
                    pdh.insertPositionLocally(position);
                    pdh.insertPositionToServer(position);
                }
                this.driveFile.removeChangeListener(getGoogleApiClient(), this);
                new CopyContentsAsyncTask(getApplicationContext(), this.resource).execute(this.driveFile);
            }
        }
    }

    public class CopyContentsAsyncTask extends ApiClientAsyncTask<DriveFile, Void, Boolean> {
        private DriveResource resource;

        public CopyContentsAsyncTask(Context context, DriveResource resource) {
            super(context);
            this.resource = resource;
        }

        @Override
        protected Boolean doInBackgroundConnected(DriveFile... args) {
            DriveFile file = args[0];
            DriveApi.DriveContentsResult driveContentsResult = file.open(
                    getGoogleApiClient(), DriveFile.MODE_WRITE_ONLY, null).await();
            if (!driveContentsResult.getStatus().isSuccess()) {
                return false;
            }

            DriveContents driveContents = driveContentsResult.getDriveContents();
            OutputStream outputStream = driveContents.getOutputStream();
            try {
                File inputFile = new File(resource.getPath());
                FileInputStream fileInputStream = new FileInputStream(inputFile);
                IOUtils.copyLarge(fileInputStream, outputStream);
                IOUtils.closeQuietly(fileInputStream);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            driveContents.commit(this.getGoogleApiClient(), null).await();
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            AreaContext areaContext = AreaContext.INSTANCE;
            AreaElement areaElement = areaContext.getAreaElement();

            areaContext.removeResourceFromQueue(resource);
            areaElement.getMediaResources().add(resource);

            PositionElement position = resource.getPosition();
            if(position != null){
                areaElement.getPositions().add(position);
            }
            // Create thumbnails of the uploaded files for display.
            String resourcePath = resource.getPath();
            File resourceFile = new File(resourcePath);

            ThumbnailCreator tCreator = new ThumbnailCreator(getApplicationContext());
            if(FileUtil.isImageFile(resourceFile)){
                tCreator.createImageThumbnail(resourceFile, areaElement.getUniqueId());
            }else if(FileUtil.isVideoFile(resourceFile)){
                tCreator.createVideoThumbnail(resourceFile, areaElement.getUniqueId());
            }else {
                tCreator.createDocumentThumbnail(resourceFile, areaElement.getUniqueId());
            }
            uploadedResources.add(resource.getResourceId());
            processResources();
        }
    }
}

package com.pearnode.app.placero.area.res.disp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.pearnode.app.placero.area.AreaContext;
import com.pearnode.app.placero.area.model.AreaElement;
import com.pearnode.app.placero.drive.DriveResource;

/**
 * Created by USER on 11/6/2017.
 */

final class PictureDataHolder {

    public static final PictureDataHolder INSTANCE = new PictureDataHolder();

    public ArrayList<PictureDisplayElement> getData() {
        ArrayList<PictureDisplayElement> imageItems = new ArrayList<>();
        AreaContext ac = AreaContext.INSTANCE;
        AreaElement ae = ac.getAreaElement();

        List<DriveResource> driveResources = ae.getMediaResources();
        String imgRootPath = ac.getAreaLocalImageRoot(ae.getUniqueId()).getAbsolutePath() + File.separatorChar;
        String thumbnailRoot = ac.getAreaLocalPictureThumbnailRoot(ae.getUniqueId()).getAbsolutePath();

        for (int i = 0; i < driveResources.size(); i++) {
            DriveResource resource = driveResources.get(i);
            if (resource.getType().equals("file")) {
                if (resource.getContentType().equals("Image")) {
                    PictureDisplayElement imageDisplayElement = new PictureDisplayElement();
                    imageDisplayElement.setName(resource.getName());
                    imageDisplayElement.setAbsPath(imgRootPath + resource.getName());
                    imageDisplayElement.setResourceId(resource.getResourceId());
                    imageDisplayElement.setThumbnailFile(new File(thumbnailRoot + File.separatorChar + resource.getName()));
                    imageDisplayElement.setImageFile(new File(imgRootPath + File.separatorChar + resource.getName()));
                    imageItems.add(imageDisplayElement);
                }
            }
        }
        return imageItems;
    }
}
package com.pearnode.app.placero.position.tasks;

import android.content.Context;
import android.os.AsyncTask;

import com.pearnode.app.placero.area.db.AreaDatabaseHandler;
import com.pearnode.app.placero.area.model.Area;
import com.pearnode.app.placero.position.Position;
import com.pearnode.app.placero.position.PositionDatabaseHandler;
import com.pearnode.common.TaskFinishedListener;
import com.pearnode.common.URlUtils;
import com.pearnode.constants.APIRegistry;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by Rinky on 21-10-2017.
 */

public class DirtyPositionsSyncTask extends AsyncTask<Object, Void, String> {

    private Context context;
    private TaskFinishedListener finishedListener;
    private List<Position> dirtyPositions;

    public DirtyPositionsSyncTask(Context context, TaskFinishedListener listener) {
        this.context = context;
        this.finishedListener = listener;
    }

    protected String doInBackground(Object... params) {
        try {
            PositionDatabaseHandler pdh = new PositionDatabaseHandler(context);
            dirtyPositions = pdh.getDirtyPositions();
            if(dirtyPositions.size() == 0){
                return "";
            }

            URL url = new URL(APIRegistry.OFFLINE_POSITION_SYNC);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(15000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);

            Map<String, Object> urlParams = new HashMap<>();
            urlParams.put("positions", dirtyPositions);

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer
                    = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            String dataStr = URlUtils.getPostDataString(urlParams);
            writer.write(dataStr);

            writer.flush();
            writer.close();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuffer sb = new StringBuffer("");
                String line = "";
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                    break;
                }
                in.close();
                return sb.toString();
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(String result) {
        if(result != null){
            PositionDatabaseHandler pdh = new PositionDatabaseHandler(context);
            try {
                JSONObject respObj = new JSONObject(result);
                JSONObject retAreas = respObj.getJSONObject("ret_obj");
                for (int i = 0; i < dirtyPositions.size(); i++) {
                    Position dirtyPosition = dirtyPositions.get(i);
                    String id = dirtyPosition.getId();
                    String posStatus = (String) retAreas.get(id);
                    if(posStatus != null && posStatus.equalsIgnoreCase("SUCCESS")){
                        dirtyPosition.setDirty(0);
                        dirtyPosition.setDirtyAction("");
                        pdh.updatePosition(dirtyPosition);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if(finishedListener != null){
            finishedListener.onTaskFinished(result);
        }
    }
}

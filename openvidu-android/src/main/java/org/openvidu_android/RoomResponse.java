/*
 * (C) Copyright 2016 VTT (http://www.vtt.fi)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.openvidu_android;

import android.support.annotation.Nullable;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.util.*;
import org.openvidu_android.OpenVidu.Method;

/**
 * Room response class
 */
public class RoomResponse {

    private static final String TAG = "RoomResponse";

    private int id = 0;
    @Nullable
    private String sessionId;
    @Nullable
    private List<HashMap<String, String>> values;
    @Nullable
    private List<HashMap<String, String>> remoteUsers;
    @Nullable
    private HashMap<String, Boolean> users;
    @Nullable
    private String sdpAnswer;
    @Nullable
    private String userId;//it will

    private OpenVidu.Method method;

    public RoomResponse(String id, JSONObject obj) {
        this.id = Integer.valueOf(id);
        this.sessionId = this.getJSONObjectSessionId(obj);
        this.userId = this.getJSONObjectUserId(obj);
        this.values = this.getJSONObjectValues(obj);
    }
    @Nullable
    public List<HashMap<String, String>>
        getRemoteParticipants() {
        return remoteUsers;
    }
    @Nullable
    public List<HashMap<String, String>> getValues() {
        return values;
    }

    @SuppressWarnings("unused")
    public int getId() {
        return this.id;
    }

    @SuppressWarnings("unused")
    public Map<String, Boolean> getUsers() {
        return this.users;
    }

    @SuppressWarnings("unused")
    public String getSdpAnswer() {
        return this.sdpAnswer;
    }

    @SuppressWarnings("unused")
    public String getSessionId() {
        return sessionId;
    }
    @Nullable
    public String getUsertId() {
        return userId;
    }
    @SuppressWarnings("unused")
    public OpenVidu.Method getMethod() {
        return method;
    }

    @SuppressWarnings("unused")
    public List<String> getValue(String key) {
        List<String> result = new Vector<>();
        for (HashMap<String, String> aMap : values) {
            result.add(aMap.get(key));
        }
        return result;
    }

    public String toString() {
        return "RoomResponse: " + id + " - " + sessionId + " - " + valuesToString();
    }

    private String getJSONObjectSessionId(JSONObject obj) {
        if (obj.containsKey("sessionId")) {
            return obj.get("sessionId").toString();
        } else {
            return null;
        }
    }

    private String getJSONObjectUserId(JSONObject obj) {
        if (obj.containsKey("sessionId")
                && obj.containsKey("id")) {//for userId
            return obj.get("id").toString();
        } else {
            return null;
        }
    }

    /*{"id":1,"result":{
        "id":"otgo5adsru9rgrt7",  this is for userId
                "metadata":"{\"clientData\": \"Participant50\"}",
                "value":[
        {"id":"vxmyimm111nzqk2n","metadata":"","streams":[{"id":"vxmyimm111nzqk2n_CAMERA_OVWOU","hasAudio":true,"hasVideo":true,"videoActive":true,"audioActive":true,"typeOfVideo":"CAMERA","frameRate":-1,"videoDimensions":"{\"width\":640,\"height\":480}","filter":{}}]},
        {"id":"1hhnsphhvyt7smhu","metadata":"","streams":[{"id":"1hhnsphhvyt7smhu_CAMERA_TVXKC","hasAudio":true,"hasVideo":true,"videoActive":true,"audioActive":true,"typeOfVideo":"CAMERA","frameRate":-1,"videoDimensions":"{\"width\":640,\"height\":480}","filter":{}}]}],
        "sessionId":"1q3sbm0ghmd07qpio4slj1v2l5" },
        "jsonrpc":"2.0"
    }

    {"id":1,"result":{
        "id":"du3xope5siiojyhg",this is for userId
                "metadata":"{\"clientData\": \"Participant87\"}",
                "value":[
        {"id":"otgo5adsru9rgrt7","metadata":"{\"clientData\": \"Participant50\"}","streams":[{"id":"otgo5adsru9rgrt7_CAMERA_PYAPF","hasAudio":true,"hasVideo":true,"videoActive":true,"audioActive":true,"typeOfVideo":"CAMERA","frameRate":30,"filter":{}}]},
        {"id":"1hhnsphhvyt7smhu","metadata":"","streams":[{"id":"1hhnsphhvyt7smhu_CAMERA_TVXKC","hasAudio":true,"hasVideo":true,"videoActive":true,"audioActive":true,"typeOfVideo":"CAMERA","frameRate":-1,"videoDimensions":"{\"width\":640,\"height\":480}","filter":{}}]}],
        "sessionId":"pdg8vinmpns9952ubublp3mdr4" },
        "jsonrpc":"2.0"
    }
    */

    private List<HashMap<String, String>>
        getJSONObjectValues(JSONObject obj) {
        List<HashMap<String, String>> result = new Vector<>();
        // Try to find value field. Value is specific to room join response
        // and contains a list of all existing users
        if (obj.containsKey("value")) {
            JSONArray valueArray = (JSONArray) obj.get("value");
            method = Method.JOIN_ROOM;
            users = new HashMap<>();
            // Iterate through the user array. The user array contains a list of
            // dictionaries, each dict containing field "id" and "streams"
            // where "id" is the username and "streams" a list of dictionary.
            // Each stream dictionary contains "id" key and the type of the
            // stream as the value, which is currently aways "webcam"
            for (int i = 0; i < valueArray.size(); i++) {//表示房间服务器有人,如果大于1
                if (remoteUsers == null)
                    remoteUsers = new ArrayList<>();

                HashMap<String, String> vArrayElement = new HashMap<>();
                JSONObject jo = (JSONObject) valueArray.get(i);
                Set<String> keys = jo.keySet();
                for (String key : keys) {
                    vArrayElement.put(key, jo.get(key).toString());

                }
                result.add(vArrayElement);
                // Fill in the users dictionary
                if (jo.containsKey("id")) {//remoteParticipantId
                    HashMap<String,String> remoteUser = new HashMap<>();
                    String username = jo.get("id").toString();
                    remoteUser.put("id",username);
                    Boolean webcamPublished;
                    // If the array entry contains both id and streams then from the
                    // current implementation we already know that the webcam stream has
                    // been published
                    webcamPublished = jo.containsKey("streams");
                    if (webcamPublished)
                        remoteUser.put("streams",jo.get("streams").toString());
                    else
                        remoteUser.put("streams","");

                    if (jo.containsKey("metadata"))
                        remoteUser.put("metadata",jo.get("metadata").toString());
                    else
                        remoteUser.put("metadata","");

                    users.put(username, webcamPublished);

                    remoteUsers.add(remoteUser);
                }
            }
        }

        if (obj.containsKey("sdpAnswer")) {
            sdpAnswer = (String) obj.get("sdpAnswer");
            HashMap<String, String> vArrayElement = new HashMap<>();
            vArrayElement.put("sdpAnswer", sdpAnswer);
            result.add(vArrayElement);
        }
        if (result.isEmpty()) {
            result = null;
        }

        return result;
    }

    private String valuesToString() {
        StringBuilder sb = new StringBuilder();
        if (this.values != null) {
            for (HashMap<String, String> aValueMap : values) {
                sb.append("{");
                for (Map.Entry<String, String> entry : aValueMap.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    sb.append(key).append("=").append(value).append(", ");
                }
                sb.append("},");
            }
            return sb.toString();
        } else return null;
    }
}
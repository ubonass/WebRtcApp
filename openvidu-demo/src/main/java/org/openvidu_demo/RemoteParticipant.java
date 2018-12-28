package org.openvidu_demo;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

/**
 * Created by sergiopaniegoblanco on 08/02/2018.
 */

public class RemoteParticipant {

    private String id;//participant id
    private String metadata;//json string for {"clientData": "27"}
    private MediaStream mediaStream;
    private PeerConnection peerConnection;
    private AudioTrack audioTrack;
    private VideoTrack videoTrack;
    private SurfaceViewRenderer videoView;
    private View view;
    private TextView participantNameText;

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getMetadata() {
        return metadata;
    }

    public String getClientData() {
        String clientData = null;
        if (metadata != null) {
            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject(metadata);
                clientData =
                        jsonObject.getString("clientData");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return clientData;
    }

    public MediaStream getMediaStream() {
        return mediaStream;
    }

    public void setMediaStream(MediaStream mediaStream) {
        this.mediaStream = mediaStream;
    }

    public PeerConnection getPeerConnection() {
        return peerConnection;
    }

    public void setPeerConnection(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }

    public AudioTrack getAudioTrack() {
        return audioTrack;
    }

    public void setAudioTrack(AudioTrack audioTrack) {
        this.audioTrack = audioTrack;
    }

    public VideoTrack getVideoTrack() {
        return videoTrack;
    }

    public void setVideoTrack(VideoTrack videoTrack) {
        this.videoTrack = videoTrack;
    }

    public SurfaceViewRenderer getVideoView() {
        return videoView;
    }

    public void setVideoView(SurfaceViewRenderer videoView) {
        this.videoView = videoView;
    }

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
    }

    public TextView getParticipantNameText() {
        return participantNameText;
    }

    public void setParticipantNameText(TextView participantNameText) {
        this.participantNameText = participantNameText;
    }
}

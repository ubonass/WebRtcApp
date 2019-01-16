package org.ubonass.utils;

import java.util.ArrayList;
import java.util.List;

public class AEvent {
    private static List<EventObj> callBackList = new ArrayList<EventObj>();

    public static void addListener(String eventID, IEventListener eventListener){
        for(EventObj eventObj:callBackList){
            if(eventObj.eventID.equals(eventID)&&eventObj.eventListener.getClass()==eventListener.getClass()) {
                return;
            }
        }
        EventObj event = new EventObj();
        event.eventListener = eventListener;
        event.eventID = eventID;
        callBackList.add(event);
    }

    public static void removeListener(String eventID, IEventListener eventListener){
        int i;
        EventObj event;
        for(i=0;i<callBackList.size();i++){
            event = callBackList.get(i);
            if(event.eventID.equals(eventID) && event.eventListener == eventListener){
                callBackList.remove(i);
                return;
                //i--;
            }
        }
    }

    public static void notifyListener(String eventID, boolean success, Object object){
        int i;
        EventObj event;
        for(i=0;i<callBackList.size();i++){
            event = callBackList.get(i);
            if(event.eventID.equals(eventID)){
                event.eventListener.dispatchEvent(eventID,success,object);
            }
        }
    }

    private static class EventObj{
        IEventListener eventListener;
        String eventID;
    }
    //事件类型在这里定义
    public static final String AEVENT_LOGIN                     = "AEVENT_LOGIN";
    public static final String AEVENT_RESET                     = "AEVENT_RESET";
    public static final String AEVENT_SIGNAL_PARAMETER_READY    = "AEVENT_SIGNAL_PARAMETER_READY";
}

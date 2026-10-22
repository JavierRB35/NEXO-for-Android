package com.jruiz.nexo.connect.vault;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Metadata-only helper. Never takes ciphertext, content or private keys.
 * A network inbox is not a trusted user interface: validate every ID.
 */
public final class NexoConnectNotificationLedger {
    private NexoConnectNotificationLedger() {}

    public static List<String> unseenIds(JSONArray inbox,Set<String> previouslySeen) {
        if(inbox==null||previouslySeen==null||inbox.length()>20)
            throw new IllegalArgumentException("Invalid inbox metadata");
        ArrayList<String> unseen=new ArrayList<>();
        HashSet<String> inPage=new HashSet<>();
        for(int i=0;i<inbox.length();i++){
            JSONObject info=inbox.optJSONObject(i);
            if(info==null)throw new IllegalArgumentException("Invalid delivery");
            String id=info.optString("deliveryId","");
            try {
                if(!id.equals(UUID.fromString(id).toString()))
                    throw new IllegalArgumentException("Noncanonical delivery ID");
            }catch(IllegalArgumentException bad){
                throw new IllegalArgumentException("Invalid delivery ID",bad);
            }
            if(!inPage.add(id))throw new IllegalArgumentException("Repeated delivery");
            if(!previouslySeen.contains(id))unseen.add(id);
        }
        return unseen;
    }
}

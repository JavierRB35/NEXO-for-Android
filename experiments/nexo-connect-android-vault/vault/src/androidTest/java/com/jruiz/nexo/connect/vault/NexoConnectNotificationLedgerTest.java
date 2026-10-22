package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class NexoConnectNotificationLedgerTest {
    private static final String A="11111111-1111-4111-8111-111111111111";
    private static final String B="22222222-2222-4222-8222-222222222222";

    private JSONArray items(String... ids)throws Exception {
        JSONArray data=new JSONArray();
        for(String id:ids)data.put(new JSONObject().put("deliveryId",id));
        return data;
    }

    @Test public void newPendingItemsNotifiedOnlyOnce()throws Exception {
        List<String> first=NexoConnectNotificationLedger.unseenIds(
                items(A,B),Collections.emptySet());
        assertEquals(Arrays.asList(A,B),first);
        assertTrue(NexoConnectNotificationLedger.unseenIds(
                items(A,B),new HashSet<>(first)).isEmpty());
    }
    @Test public void oneNewItemWithPreviouslySeenItem()throws Exception {
        assertEquals(Collections.singletonList(B),
                NexoConnectNotificationLedger.unseenIds(
                        items(A,B),Collections.singleton(A)));
    }
    @Test public void emptyInboxNeverNotifies()throws Exception {
        assertTrue(NexoConnectNotificationLedger.unseenIds(
                items(),Collections.emptySet()).isEmpty());
    }
    @Test public void duplicateOrMalformedIdsFailClosed()throws Exception {
        for(JSONArray bad:Arrays.asList(
                items(A,A),items("untrusted"),items(A.toUpperCase()))){
            try{
                NexoConnectNotificationLedger.unseenIds(bad,Collections.emptySet());
                fail("Invalid metadata should never trigger a notification");
            }catch(IllegalArgumentException expected){}
        }
    }
    @Test public void neverTrustOversizedInbox()throws Exception {
        JSONArray invalid=new JSONArray();
        for(int i=0;i<21;i++)invalid.put(new JSONObject().put(
                "deliveryId",String.format("11111111-1111-4111-8111-%012d",i)));
        try{
            NexoConnectNotificationLedger.unseenIds(invalid,Collections.emptySet());
            fail("Unbounded inbox accepted");
        }catch(IllegalArgumentException expected){}
    }
}

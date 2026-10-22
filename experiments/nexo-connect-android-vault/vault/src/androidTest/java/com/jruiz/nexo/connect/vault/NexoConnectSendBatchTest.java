package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** No network, identifiers, external accounts or Android Keystore in tests. */
@RunWith(AndroidJUnit4.class)
public final class NexoConnectSendBatchTest {
    private static final UUID OWN=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID A=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID B=UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID C=UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test public void multipleRecipientsEachGetOneSeparateAttempt(){
        List<UUID> attempted=new ArrayList<>();
        NexoConnectSendBatch.Result result=NexoConnectSendBatch.sendOnce(
            Arrays.asList(A,B,C),OWN,true,attempted::add);
        assertEquals(Arrays.asList(A,B,C),attempted);
        assertEquals(3,result.requested);
        assertEquals(3,result.confirmed);
        assertEquals(3,result.attempted);
        assertFalse(result.interrupted);
    }

    @Test public void uncertainSendStopsAndDoesNotRetryOrSkipAhead(){
        List<UUID> attempted=new ArrayList<>();
        NexoConnectSendBatch.Result result=NexoConnectSendBatch.sendOnce(
            Arrays.asList(A,B,C),OWN,true,id->{
                attempted.add(id);
                if(id.equals(B))throw new java.io.IOException("Uncertain network reply");
            });
        assertEquals(Arrays.asList(A,B),attempted);
        assertEquals(1,result.confirmed);
        assertEquals(2,result.attempted);
        assertEquals(3,result.requested);
        assertTrue(result.interrupted);
    }

    @Test public void consentIsAlwaysRequired(){
        try{
            NexoConnectSendBatch.sendOnce(Arrays.asList(A),OWN,false,id->{
                fail("Never send without consent");
            });
            fail("Approval required");
        }catch(SecurityException expected){}
    }

    @Test public void duplicatesSelfAndMissingRecipientsFailBeforeSend(){
        for(List<UUID> bad:Arrays.asList(
                Arrays.asList(A,A),Arrays.asList(OWN),new ArrayList<UUID>())){
            try{
                NexoConnectSendBatch.sendOnce(bad,OWN,true,id->
                        fail("Duplicate or invalid selection must not send"));
                fail("Bad recipient list accepted");
            }catch(IllegalArgumentException expected){}
        }
    }

    @Test public void limitPreventsAccidentalMassDelivery(){
        List<UUID> ids=new ArrayList<>();
        for(int i=0;i<=NexoConnectSendBatch.MAX_RECIPIENTS;i++)
            ids.add(new UUID(10,i+100));
        try{
            NexoConnectSendBatch.sendOnce(ids,OWN,true,id->
                    fail("Oversize selection must be rejected before sending"));
            fail("Should limit recipients");
        }catch(IllegalArgumentException expected){}
    }
}

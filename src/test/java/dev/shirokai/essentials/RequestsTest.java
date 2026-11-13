package dev.shirokai.essentials;

import dev.shirokai.essentials.teleport.Requests;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RequestsTest {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    @Test void expiresAtBoundaryAndCannotBeAccepted() {
        var q = new Requests(); assertTrue(q.add(new Requests.Request(a,b,false,100),3,10,0));
        assertTrue(q.find(b,a,99).isPresent()); assertTrue(q.find(b,a,100).isEmpty());
        assertEquals(1,q.expire(100).size()); assertNull(q.remove(a));
    }
    @Test void enforcesLimitsAndOneRequestPerSender() {
        var q = new Requests(); assertTrue(q.add(new Requests.Request(a,b,false,100),1,10,0));
        assertFalse(q.add(new Requests.Request(a,c,true,100),1,10,0));
        assertFalse(q.add(new Requests.Request(c,b,false,100),1,10,0));
    }
    @Test void disconnectRemovesBothDirections() {
        var q = new Requests(); q.add(new Requests.Request(a,b,false,100),3,10,0); q.add(new Requests.Request(c,a,false,100),3,10,0);
        q.disconnect(a); assertTrue(q.find(b,a,0).isEmpty()); assertTrue(q.find(a,c,0).isEmpty());
    }
    @Test void explicitSenderSelectsCorrectRequestAndClearCancelsAll() {
        var q = new Requests(); q.add(new Requests.Request(a,b,false,100),3,10,0); q.add(new Requests.Request(c,b,true,101),3,10,0);
        assertTrue(q.find(b,c,0).orElseThrow().here()); assertEquals(a,q.find(b,null,0).orElseThrow().sender());
        q.clear(); assertTrue(q.find(b,null,0).isEmpty());
    }
}

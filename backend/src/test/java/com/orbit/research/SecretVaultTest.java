package com.orbit.research;

import com.orbit.research.common.ApiException;
import com.orbit.research.llm.SecretVault;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SecretVaultTest {
    @Test void keyFileSurvivesReopenAndCiphertextIsBoundToItsOwner() throws Exception {
        String path=Path.of("target","vault-"+UUID.randomUUID(),"config.key").toString();
        var vault=new SecretVault("",path);String value="fixture-secret-never-a-real-key",cipher=vault.encrypt("owner",value);
        assertNotEquals(value,cipher);assertFalse(Files.readString(Path.of(path)).contains(value));
        var reopened=new SecretVault("",path);assertEquals(value,reopened.decrypt("owner",cipher));
        assertThrows(ApiException.class,()->reopened.decrypt("other-owner",cipher));
        assertNotEquals(cipher,reopened.encrypt("owner",value));
    }
}

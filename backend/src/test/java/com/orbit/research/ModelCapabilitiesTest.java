package com.orbit.research;

import com.orbit.research.common.ApiException;
import com.orbit.research.llm.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelCapabilitiesTest {
    LlmProperties model(String url,String name){return new LlmProperties(url,"fixture",name,false,20,100,false,"max_tokens");}
    @Test void reasoningParametersAreLimitedToVerifiedProviderAndModels(){
        var supported=model("https://api.deepseek.com/v1","deepseek-flash");
        for(String level:List.of("none","low","high","max")){Map<String,Object> body=new HashMap<>();ModelCapabilities.apply(supported,level,body);assertEquals(level,body.get("reasoning_effort"));assertFalse(body.containsKey("thinking"));}
        assertThrows(ApiException.class,()->ModelCapabilities.validate(supported,"medium"));
        assertThrows(ApiException.class,()->ModelCapabilities.validate(model("https://example.com/v1","deepseek-flash"),"high"));
        assertThrows(ApiException.class,()->ModelCapabilities.validate(model("https://api.deepseek.com/v1","unknown"),"high"));
    }
}

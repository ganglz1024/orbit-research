package com.orbit.research.llm;

import com.orbit.research.common.ApiException;
import java.net.URI;
import java.util.*;

/** Only officially documented model and provider combinations are enabled. */
public final class ModelCapabilities {
    private ModelCapabilities() {}
    public static boolean deepseek(LlmProperties p){try{return "api.deepseek.com".equalsIgnoreCase(URI.create(p.baseUrl()).getHost())&&Set.of("deepseek-flash","deepseek-v4-pro").contains(p.model());}catch(Exception e){return false;}}
    public static void validate(LlmProperties p,String effort){if(effort==null||effort.isBlank())return;if(!deepseek(p)||!Set.of("none","low","high","max").contains(effort))throw new ApiException(422,"REASONING_UNSUPPORTED","当前模型或供应商未验证支持该思考档位。");}
    public static void apply(LlmProperties p,String effort,Map<String,Object> body){validate(p,effort);if(effort!=null&&!effort.isBlank())body.put("reasoning_effort",effort);}
}

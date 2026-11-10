package com.orbit.research;

import com.orbit.research.common.ApiException;
import com.orbit.research.common.Json;
import com.orbit.research.llm.*;
import com.orbit.research.run.RunStore;
import com.orbit.research.run.Runs;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real embedded HTTP + SQL + filesystem tests. The model supplier is a local protocol fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendIntegrationTest {
    static final HttpServer MODEL;
    static final ExecutorService MODEL_THREADS = Executors.newCachedThreadPool();
    static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    static {
        try {
            MODEL = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MODEL.setExecutor(MODEL_THREADS);
            MODEL.createContext("/v1/chat/completions", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); REQUESTS.add(request);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
                if (request.contains("UPSTREAM_429")) { exchange.sendResponseHeaders(429, 0); exchange.close(); return; }
                exchange.sendResponseHeaders(200, 0);
                try (var output = exchange.getResponseBody()) {
                    var fixtureJson = new tools.jackson.databind.json.JsonMapper();
                    var body = fixtureJson.readTree(request);
                    String prompt=body.path("messages").path(body.path("messages").size()-1).path("content").asString();
                    if(prompt.startsWith("执行科研分析任务 ")){
                        var sourceNodes=fixtureJson.readTree(prompt.substring(prompt.lastIndexOf("明确提供的来源：")+"明确提供的来源：".length()));
                        var first=sourceNodes.path(0);String sid=first.path("id").asString(),eid=first.path("excerpts").path(0).path("id").asString(),quote=first.path("excerpts").path(0).path("text").asString();quote=quote.substring(0,Math.min(16,quote.length()));
                        List<Object> evidence=List.of(Map.of("sourceId",sid,"excerptId",eid,"quote",quote,"page",999,"explanation","片段支持"),Map.of("sourceId",sid,"excerptId",eid,"quote","FABRICATED_QUOTE","page",555,"explanation","不能支持"));
                        List<Object> comparison=new ArrayList<>();for(String d:List.of("研究问题","核心方法","主要贡献","实验设置","关键结论","研究局限")){List<Object> cells=new ArrayList<>();for(var s:sourceNodes)cells.add(Map.of("paperId",s.path("id").asString(),"text","需要核对的模型归纳，实验数字 42%","evidence",evidence));comparison.add(Map.of("dimension",d,"cells",cells));}
                        String content=fixtureJson.writeValueAsString(Map.of("title","受控科研分析","summary","从提供资料中归纳证据。","details","说明研究问题、方法和证据边界。","evidence",evidence,"comparison",comparison,"commonalities",List.of("关注可靠性"),"differences",List.of("技术条件不同"),"directions",List.of("需继续验证的候选假设"),"suggestions",List.of("回到原文核验")));
                        output.write(("data: "+fixtureJson.writeValueAsString(Map.of("choices",List.of(Map.of("delta",Map.of("content",content),"finish_reason","stop"))))+"\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));return;
                    }
                    if(request.contains("INSTALL_GITHUB")&&body.has("tools")&&!request.contains("tool_call_id")){
                        var function=Map.of("name","install_research_skill","arguments","{}");
                        var call=Map.of("index",0,"id","github_install_fixture","type","function","function",function);
                        output.write(("data: "+fixtureJson.writeValueAsString(Map.of("choices",List.of(Map.of("delta",Map.of("tool_calls",List.of(call)),"finish_reason","tool_calls"))))+"\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));return;
                    }
                    if (request.contains("TOOL_SELECT") && body.has("tools") && !request.contains("tool_call_id")) {
                        String skill = body.path("tools").path(0).path("function").path("parameters").path("properties").path("skillId").path("enum").path(0).asString();
                        if (request.contains("TOOL_FORBIDDEN")) skill = "custom_not_allowed";
                        String arguments = request.contains("TOOL_BAD_JSON") ? "not-json" : fixtureJson.writeValueAsString(Map.of("skillId", skill));
                        int middle = arguments.length()/2;
                        for (int n=0; n<2; n++) {
                            Map<String,Object> call = new LinkedHashMap<>(); call.put("index",0);
                            if (n==0) { call.put("id","call_fixture"); call.put("type","function"); }
                            Map<String,Object> function = new LinkedHashMap<>(); if (n==0) function.put("name","load_research_skill");
                            function.put("arguments", n==0?arguments.substring(0,middle):arguments.substring(middle)); call.put("function",function);
                            output.write(("data: "+fixtureJson.writeValueAsString(Map.of("choices",List.of(Map.of("delta",Map.of("tool_calls",List.of(call))))))+"\n\n").getBytes(StandardCharsets.UTF_8));
                        }
                        output.write("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":8}}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8)); return;
                    }
                    if(request.contains("TRUNCATED_OUTPUT")){
                        output.write("data: {\"choices\":[{\"delta\":{\"content\":\"未完整的分析\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));return;
                    }
                    if (request.contains("LONG_REASONING_FIXTURE")) for(int r=0;r<220;r++)output.write(("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\""+"x".repeat(10000)+"\"}}]}\n\n").getBytes(StandardCharsets.UTF_8));
                    if (request.contains("SLOW_RESPONSE")) Thread.sleep(5000);
                    output.write("data: {\"choices\":[{\"delta\":{\"content\":\"证据\"},\"finish_reason\":null}]}\n\n".getBytes(StandardCharsets.UTF_8)); output.flush();
                    if (request.contains("BROKEN_STREAM")) return;
                    output.write("data: {\"choices\":[{\"delta\":{\"content\":\"与分析应区分。\"},\"finish_reason\":null}]}\n\n".getBytes(StandardCharsets.UTF_8));
                    output.write("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n".getBytes(StandardCharsets.UTF_8));
                    output.write("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":8}}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {} finally { exchange.close(); }
            });
            MODEL.start();
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    @DynamicPropertySource static void settings(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:orbit-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        properties.add("orbit.storage-path", () -> "./target/test-files");
        properties.add("orbit.config-key-path", () -> "./target/test-vault/config.key");
        properties.add("orbit.llm.base-url", () -> "http://127.0.0.1:" + MODEL.getAddress().getPort() + "/v1");
        properties.add("orbit.llm.api-key", () -> "fixture-key-never-a-real-secret");
        properties.add("orbit.llm.model", () -> "fixture-model");
        properties.add("orbit.llm.allow-private-hosts", () -> true);
        properties.add("orbit.llm.timeout-seconds", () -> 2);
        properties.add("orbit.llm.include-usage", () -> true);
    }
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.orbit.research.skill.GithubRepositoryClient github;
    @LocalServerPort int port;
    @Autowired Json json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Runs runs;
    @Autowired RunStore store;
    HttpClient client; String csrf;
    @AfterEach void clearPersonalConfiguration() { jdbc.update("DELETE FROM github_skill_import WHERE owner_id='owner'"); jdbc.update("DELETE FROM model_configuration WHERE owner_id='owner'"); jdbc.update("DELETE FROM research_preference WHERE owner_id='owner'"); jdbc.update("DELETE FROM custom_skill WHERE owner_id='owner'"); }
    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/api/v1" + path); }
    @BeforeAll void login() throws Exception {
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        refreshCsrf();
        assertFalse(json.read(request("GET", "/setup", null, null).body()).path("initialized").asBoolean());
        assertEquals(403, client.send(HttpRequest.newBuilder(uri("/setup")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.write(Map.of("username", "researcher", "password", "fixture-password-123")))).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(400, request("POST", "/setup", Map.of("username", "researcher", "password", "short"), null).statusCode());
        assertEquals(400, request("POST", "/setup", Map.of("username", "!", "password", "fixture-password-123"), null).statusCode());
        assertFalse(json.read(request("GET", "/setup", null, null).body()).path("initialized").asBoolean());
        assertEquals(201, request("POST", "/setup", Map.of("username", "researcher", "password", "fixture-password-123"), null).statusCode());
        assertTrue(json.read(request("GET", "/setup", null, null).body()).path("initialized").asBoolean());
        authenticate("fixture-password-123");
    }
    void authenticate(String password) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri("/auth/login")).header("Content-Type", "application/x-www-form-urlencoded").header("X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofString("username=researcher&password=" + password)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), response.body()); refreshCsrf();
    }
    void refreshCsrf() throws Exception { csrf = json.read(client.send(HttpRequest.newBuilder(uri("/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()).path("token").asString(); }
    HttpResponse<String> request(String method, String path, Object value, String key) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).header("X-CSRF-TOKEN", csrf);
        if (key != null) builder.header("Idempotency-Key", key);
        if (value != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, value == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.write(value), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    String conversation() throws Exception {
        var response = request("POST", "/conversations", Map.of("title", "集成测试 " + UUID.randomUUID()), null);
        assertEquals(201, response.statusCode(), response.body()); return json.read(response.body()).path("id").asString();
    }
    Map<String,Object> modelInput(int revision, String key) { return Map.of("baseUrl","http://127.0.0.1:"+MODEL.getAddress().getPort()+"/v1", "model","saved-fixture-model","apiKey",key,"timeoutSeconds",5,"maxOutputTokens",333,"includeUsage",true,"tokenParameter","max_completion_tokens","revision",revision); }
    JsonNode skill(String instructions) throws Exception {
        var response=request("POST","/skills",Map.of("name","证据核验","description","核查证据与局限","instructions",instructions,"enabled",true,"revision",0),null);
        assertEquals(201,response.statusCode(),response.body());return json.read(response.body());
    }
    Map<String,Object> analysisPaper(String id,String title){return Map.of("id",id,"title",title,"metadata","明确提供的测试元数据。","url","https://example.com/paper");}
    JsonNode awaitAnalysis(String id)throws Exception {for(int i=0;i<150;i++){var v=json.read(request("GET","/analyses/"+id,null,null).body());if(!v.path("state").asString().equals("loading"))return v;Thread.sleep(40);}throw new AssertionError("analysis did not settle");}
    void githubFixture(){
        String base="/repos/research/test-skill",sha="abcdef0123456789abcdef0123456789abcdef01";
        org.mockito.Mockito.when(github.get(base)).thenReturn(json.read("{\"default_branch\":\"main\"}"));
        org.mockito.Mockito.when(github.get(base+"/commits/main")).thenReturn(json.read("{\"sha\":\""+sha+"\"}"));
        String content="---\nname: github-evidence-check\n---\n请先给结论，再区分证据与推断。\n[阅读规则](references/rules.md)";
        org.mockito.Mockito.when(github.get(base+"/contents/SKILL.md?ref="+sha)).thenReturn(json.read(json.write(Map.of("type","file","encoding","base64","size",content.length(),"content",Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8))))));
        org.mockito.Mockito.when(github.get(base+"/contents/references/rules.md?ref="+sha)).thenReturn(json.read(json.write(Map.of("type","file","encoding","base64","size",20,"content",Base64.getEncoder().encodeToString("不得编造论文与实验数字。".getBytes(StandardCharsets.UTF_8))))));
    }
    @Test void githubImportPinsRevisionAndIsIdempotent()throws Exception{
        githubFixture();var input=Map.of("url","https://github.com/research/test-skill");
        var response=request("POST","/skills/import/github",input,null);assertEquals(200,response.statusCode(),response.body());var imported=json.read(response.body());
        assertEquals("github-evidence-check",imported.path("skill").path("name").asString());assertEquals(40,imported.path("commitSha").asString().length());assertTrue(imported.path("skill").path("instructions").asString().contains("不得编造"));
        var repeated=json.read(request("POST","/skills/import/github",input,null).body());assertTrue(repeated.path("reused").asBoolean());assertEquals(imported.path("skill").path("id").asString(),repeated.path("skill").path("id").asString());
        assertEquals("markdown_instructions",imported.path("mode").asString());
    }
    @Test void modelCanInstallOnlyExplicitGithubTarget()throws Exception{
        githubFixture();String workspace=conversation();var body=Map.of("prompt","INSTALL_GITHUB 帮我安装这个 Skill","skillId","chat","installSkillUrl","https://github.com/research/test-skill");
        var response=request("POST","/conversations/"+workspace+"/runs",body,UUID.randomUUID().toString());assertEquals(202,response.statusCode(),response.body());String id=json.read(response.body()).path("runId").asString();var done=await(id);assertEquals("succeeded",done.path("status").asString(),done.toString());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM github_skill_import WHERE owner_id='owner'",Integer.class));assertTrue(request("GET","/runs/"+id+"/events",null,null).body().contains("event:skill_installed"));
        int before=REQUESTS.size();assertEquals(400,request("POST","/conversations/"+conversation()+"/runs",Map.of("prompt","安装 Skill","skillId","chat","installSkillUrl","http://127.0.0.1/secret"),UUID.randomUUID().toString()).statusCode());assertEquals(before,REQUESTS.size());
    }
    @Test void invalidGithubUrlsNeverReachNetwork()throws Exception{
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM custom_skill",Integer.class);
        for(String url:List.of("https://github.com.evil.test/a/b","https://user@github.com/a/b","http://github.com/a/b","https://github.com/a/b/tree/main/../secret","https://github.com/a/b?token=secret"))assertEquals(400,request("POST","/skills/import/github",Map.of("url",url),null).statusCode(),url);
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM custom_skill",Integer.class));org.mockito.Mockito.verifyNoInteractions(github);
    }
    @Test void truncatedOutputCannotBecomeACompletedAnalysis()throws Exception{
        var done=await(run(conversation(),"TRUNCATED_OUTPUT",List.of(),UUID.randomUUID().toString()));
        assertEquals("partial",done.path("status").asString());assertEquals("OUTPUT_TRUNCATED",done.path("errorCode").asString());assertEquals("未完整的分析",done.path("answer").asString());
    }
    @Test void organizationSkillsAreRegisteredAndHonorEnabledState()throws Exception{
        for(String skill:List.of("paper_metadata","paper_relations")){
            var registered=request("GET","/skills/"+skill,null,null);assertEquals(200,registered.statusCode(),registered.body());assertEquals("ready",json.read(registered.body()).path("status").asString());
            String workspace=conversation();var body=Map.of("prompt","验证提供资料的整理流程，不补全缺失事实。","skillId",skill);
            var response=request("POST","/conversations/"+workspace+"/runs",body,UUID.randomUUID().toString());assertEquals(202,response.statusCode(),response.body());String id=json.read(response.body()).path("runId").asString();assertEquals("succeeded",await(id).path("status").asString());assertTrue(request("GET","/runs/"+id+"/events",null,null).body().contains(skill));
            try{assertEquals(200,request("PATCH","/skills/"+skill+"/enabled",Map.of("enabled",false),null).statusCode());assertEquals(409,request("POST","/conversations/"+conversation()+"/runs",body,UUID.randomUUID().toString()).statusCode());}finally{request("PATCH","/skills/"+skill+"/enabled",Map.of("enabled",true),null);}
        }
    }
    @Test void academicResearchAdaptersAreRegisteredAndReachTheModel()throws Exception{
        var catalog=com.orbit.research.skill.AcademicResearchSkillCatalog.all();
        assertEquals(5,catalog.size());
        for(var spec:catalog){
            var response=request("GET","/skills/"+spec.id(),null,null);
            assertEquals(200,response.statusCode(),response.body());
            var listed=json.read(response.body());
            assertEquals("builtin",listed.path("kind").asString());
            assertEquals("ready",listed.path("status").asString());
            String marker="ARS_ADAPTER_"+spec.id(),conversation=conversation();
            var submitted=request("POST","/conversations/"+conversation+"/runs",Map.of("prompt",marker+" 请只分析明确提供的资料。","skillId",spec.id()),UUID.randomUUID().toString());
            assertEquals(202,submitted.statusCode(),submitted.body());
            String runId=json.read(submitted.body()).path("runId").asString();
            assertEquals("succeeded",await(runId).path("status").asString());
            assertTrue(REQUESTS.stream().anyMatch(body->body.contains(marker)&&body.contains(spec.instructions())),"Model did not receive "+spec.id());
            String snapshot=jdbc.queryForObject("SELECT skill_snapshot FROM run_record WHERE id=?",String.class,runId);
            assertTrue(snapshot.contains(spec.id()));
        }
        String id="ars_research";
        assertEquals(200,request("PATCH","/skills/"+id+"/enabled",Map.of("enabled",false),null).statusCode());
        assertEquals(409,request("POST","/conversations/"+conversation()+"/runs",Map.of("prompt","blocked","skillId",id),UUID.randomUUID().toString()).statusCode());
        assertEquals(200,request("PATCH","/skills/"+id+"/enabled",Map.of("enabled",true),null).statusCode());
        assertEquals(200,request("PUT","/settings/preferences",Map.of("researchContext","","defaultSkillId",id,"allowSkillTools",false,"assistantMode","model"),null).statusCode());
        String marker="ARS_DEFAULT_SELECTION";
        var submitted=request("POST","/conversations/"+conversation()+"/runs",Map.of("prompt",marker,"skillId","default"),UUID.randomUUID().toString());
        assertEquals(202,submitted.statusCode(),submitted.body());
        assertEquals("succeeded",await(json.read(submitted.body()).path("runId").asString()).path("status").asString());
        assertTrue(REQUESTS.stream().anyMatch(body->body.contains(marker)&&body.contains(catalog.get(0).instructions())));
    }
    @Test void largeReasoningDoesNotConsumeVisibleAnswerBudget() throws Exception {
        String c=conversation();var response=request("POST","/conversations/"+c+"/runs",Map.of("prompt","LONG_REASONING_FIXTURE","skillId","chat"),UUID.randomUUID().toString());assertEquals(202,response.statusCode());var done=await(json.read(response.body()).path("runId").asString());assertEquals("succeeded",done.path("status").asString());assertFalse(done.path("answer").asString().contains("xxxx"));
    }
    @Test void metadataSummaryRoutesToReadingAndKeepsStyleSeparate() throws Exception {
        String c=conversation();var result=request("POST","/conversations/"+c+"/runs",Map.of("prompt","帮我总结一下这篇论文 ORBIT_SUMMARY_TEST","skillId","chat","responseStyle","concise"),UUID.randomUUID().toString());
        assertEquals(202,result.statusCode(),result.body());String id=json.read(result.body()).path("runId").asString();assertEquals("succeeded",await(id).path("status").asString());
        String sent=REQUESTS.stream().filter(r->r.contains("ORBIT_SUMMARY_TEST")).reduce((a,b)->b).orElseThrow();assertTrue(sent.contains("论文概览"));assertTrue(sent.contains("优先简短直接回答"));assertFalse(sent.contains("reasoning_effort"));
        assertTrue(request("GET","/runs/"+id+"/events",null,null).body().contains("automatic"));
        var saved=json.read(request("GET","/conversations/"+c,null,null).body());assertEquals("paper_read",saved.path("messages").path(0).path("execution").path("skill").path("primary").path("id").asString());assertFalse(saved.toString().contains("fixture-key"));
    }
    @Test void englishTitleSummaryUsesOnlyCurrentPaperAndRespectsExplicitSkill() throws Exception {
        String title="Attention Is All You Need";
        String envelope="\n\nORBIT_CONTEXT_V1\n"+json.write(Map.of("version",1,"sources",List.of(Map.of("id","qa-paper","kind","paper","name",title,"scope","用户提供的元数据"))));
        String exact="帮我用200字总结 Attention Is All You Need，重点说明研究问题、核心方法和贡献。";
        String custom=skill("按用户要求撰写相关工作，不编造引用").path("id").asString();
        String[] prompts={exact+envelope,"简单总结过拟合的原因"+envelope,exact,exact+envelope,exact+"\n\nORBIT_CONTEXT_V1\ninvalid"};
        String[] requested={"chat","chat","chat",custom,"chat"};
        String[] expected={"paper_read","chat","chat",custom,"chat"};
        for(int i=0;i<prompts.length;i++){
            String c=conversation();var result=request("POST","/conversations/"+c+"/runs",Map.of("prompt",prompts[i],"skillId",requested[i]),UUID.randomUUID().toString());
            assertEquals(202,result.statusCode(),result.body());assertEquals("succeeded",await(json.read(result.body()).path("runId").asString()).path("status").asString());
            var saved=json.read(request("GET","/conversations/"+c,null,null).body());
            assertEquals(expected[i],saved.path("messages").path(0).path("execution").path("skill").path("primary").path("id").asString(),"routing case "+i);
        }
    }
    String scopedConversation(String title,String space)throws Exception{var response=request("POST","/conversations",Map.of("title",title,"workspaceId",space),null);assertEquals(201,response.statusCode(),response.body());return json.read(response.body()).path("id").asString();}
    @Test void managedConversationLifecyclePreservesAssetsAndRejectsStaleChanges()throws Exception{
        String space="lifecycle-"+UUID.randomUUID(),c=scopedConversation("QA initial",space);
        var renamed=request("PATCH","/conversations/"+c,Map.of("title","我的科研对话","revision",0),null);assertEquals(200,renamed.statusCode());
        assertEquals(409,request("PATCH","/conversations/"+c,Map.of("title","stale","revision",0),null).statusCode());
        request("PATCH","/conversations/"+c,Map.of("title","automatic overwrite","automatic",true),null);
        assertEquals("我的科研对话",json.read(request("GET","/conversations/"+c,null,null).body()).path("conversation").path("title").asString());
        for(int i=0;i<4;i++){var r=request("POST","/conversations/"+c+"/runs",Map.of("prompt","history QA "+i,"skillId","chat"),UUID.randomUUID().toString());assertEquals(202,r.statusCode());await(json.read(r.body()).path("runId").asString());}
        var checkpoint=request("POST","/conversations/"+c+"/compact/preview",null,null);assertEquals(200,checkpoint.statusCode());String cid=json.read(checkpoint.body()).path("id").asString();assertEquals(200,request("POST","/conversations/"+c+"/compact/"+cid+"/apply",null,null).statusCode());
        var note=request("POST","/conversations/"+c+"/notes",Map.of("title","QA independent note","body","source snapshot preserved"),UUID.randomUUID().toString());assertEquals(201,note.statusCode(),note.body());String nid=json.read(note.body()).path("id").asString();
        int messages=jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE conversation_id=?",Integer.class,c);
        assertEquals(200,request("POST","/conversations/"+c+"/manage",Map.of("action","archive"),null).statusCode());
        assertEquals(0,json.read(request("GET","/conversations?workspaceId="+space,null,null).body()).path("total").asInt());
        assertEquals(1,json.read(request("GET","/conversations?workspaceId="+space+"&state=archived",null,null).body()).path("total").asInt());
        assertEquals(409,request("POST","/conversations/"+c+"/runs",Map.of("prompt","not allowed","skillId","chat"),UUID.randomUUID().toString()).statusCode());
        request("POST","/conversations/"+c+"/manage",Map.of("action","delete"),null);
        assertEquals(404,request("GET","/conversations/"+c,null,null).statusCode());assertEquals(1,json.read(request("GET","/conversations?workspaceId="+space+"&state=deleted",null,null).body()).path("total").asInt());
        assertTrue(request("GET","/notes",null,null).body().contains(nid));
        request("POST","/conversations/"+c+"/manage",Map.of("action","restore"),null);request("POST","/conversations/"+c+"/manage",Map.of("action","unarchive"),null);
        var restored=json.read(request("GET","/conversations/"+c,null,null).body());assertEquals(c,restored.path("conversation").path("id").asString());assertEquals(space,restored.path("conversation").path("workspaceId").asString());assertEquals(messages,restored.path("messages").size());assertEquals(cid,json.read(request("GET","/conversations/"+c+"/compact",null,null).body()).path("checkpoint").path("id").asString());
    }
    @Test void fullSearchIsPagedScopedLiteralAndPinPersists()throws Exception{
        String a="search-"+UUID.randomUUID(),b="other-"+UUID.randomUUID(),first=scopedConversation("old record",a);
        var r=request("POST","/conversations/"+first+"/runs",Map.of("prompt","UNIQUE_SEARCH_7821 100%_literal","skillId","chat"),UUID.randomUUID().toString());await(json.read(r.body()).path("runId").asString());
        for(int i=0;i<22;i++)scopedConversation("QA newer "+i,a);scopedConversation("UNIQUE_SEARCH_7821",b);
        var initial=json.read(request("GET","/conversations?workspaceId="+a+"&size=20",null,null).body());assertEquals(23,initial.path("total").asInt());assertFalse(initial.path("items").toString().contains(first));
        var found=json.read(request("GET","/conversations?workspaceId="+a+"&q=UNIQUE_SEARCH_7821",null,null).body());assertEquals(1,found.path("total").asInt());assertEquals(first,found.path("items").path(0).path("id").asString());
        assertEquals(1,json.read(request("GET","/conversations?workspaceId="+a+"&q=100%25_literal",null,null).body()).path("total").asInt());
        request("POST","/conversations/"+first+"/manage",Map.of("action","pin"),null);var pinned=json.read(request("GET","/conversations?workspaceId="+a,null,null).body()).path("items").path(0);assertEquals(first,pinned.path("id").asString());assertFalse(pinned.path("pinnedAt").isNull());
        assertEquals(409,request("PATCH","/conversations/"+first+"/workspace",Map.of("workspaceId",b),null).statusCode());
        assertEquals(404,request("POST","/conversations/nonexistent/manage",Map.of("action","delete"),null).statusCode());
    }
    @Test void deletionDuringGenerationCancelsAndCannotReceiveLateMessages()throws Exception{
        String c=scopedConversation("QA slow deletion","delete-"+UUID.randomUUID());var r=request("POST","/conversations/"+c+"/runs",Map.of("prompt","SLOW_RESPONSE","skillId","chat"),UUID.randomUUID().toString());assertEquals(202,r.statusCode());String run=json.read(r.body()).path("runId").asString();
        assertEquals(200,request("POST","/conversations/"+c+"/manage",Map.of("action","delete"),null).statusCode());
        assertEquals("cancelled",await(run).path("status").asString());Thread.sleep(5200);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE conversation_id=? AND role='assistant'",Integer.class,c));
        assertEquals(404,request("POST","/conversations/"+c+"/runs",Map.of("prompt","ghost","skillId","chat"),UUID.randomUUID().toString()).statusCode());
        request("POST","/conversations/"+c+"/manage",Map.of("action","restore"),null);assertEquals(1,json.read(request("GET","/conversations/"+c,null,null).body()).path("messages").size());
    }
    @Test void compactionRequiresConfirmationAndChangesOnlyFutureContext() throws Exception {
        String workspace=conversation();
        for(int i=0;i<4;i++){var response=request("POST","/conversations/"+workspace+"/runs",Map.of("prompt","COMPACT_MARKER_"+i,"skillId","chat"),UUID.randomUUID().toString());assertEquals(202,response.statusCode());assertEquals("succeeded",await(json.read(response.body()).path("runId").asString()).path("status").asString());}
        var preview=request("POST","/conversations/"+workspace+"/compact/preview",null,null);assertEquals(200,preview.statusCode(),preview.body());String id=json.read(preview.body()).path("id").asString();
        assertEquals(8,json.read(request("GET","/conversations/"+workspace,null,null).body()).path("messages").size());
        assertEquals(200,request("POST","/conversations/"+workspace+"/compact/"+id+"/apply",null,null).statusCode());
        var next=request("POST","/conversations/"+workspace+"/runs",Map.of("prompt","COMPACT_NEXT","skillId","chat"),UUID.randomUUID().toString());assertEquals(202,next.statusCode());assertEquals("succeeded",await(json.read(next.body()).path("runId").asString()).path("status").asString());
        String sent=REQUESTS.stream().filter(s->s.contains("COMPACT_NEXT")).reduce((a,b)->b).orElseThrow();assertFalse(sent.contains("COMPACT_MARKER_0"));assertTrue(sent.contains("COMPACT_MARKER_3"));assertTrue(sent.contains("压缩摘要"));
        assertEquals(10,json.read(request("GET","/conversations/"+workspace,null,null).body()).path("messages").size());
        assertEquals(404,request("POST","/conversations/"+workspace+"/compact/"+id+"/apply",null,null).statusCode());
        var stale=request("POST","/conversations/"+workspace+"/compact/preview",null,null);assertEquals(200,stale.statusCode(),stale.body());String staleId=json.read(stale.body()).path("id").asString();
        var changed=request("POST","/conversations/"+workspace+"/runs",Map.of("prompt","CHANGED_AFTER_PREVIEW","skillId","chat"),UUID.randomUUID().toString());assertEquals(202,changed.statusCode());assertEquals("succeeded",await(json.read(changed.body()).path("runId").asString()).path("status").asString());
        assertEquals(409,request("POST","/conversations/"+workspace+"/compact/"+staleId+"/apply",null,null).statusCode());
        assertEquals(id,json.read(request("GET","/conversations/"+workspace+"/compact",null,null).body()).path("checkpoint").path("id").asString());
    }
    @Test void longPaperTitlesAndAuthorListsCanBeAnalyzed()throws Exception{
        var paper=Map.of("id","long-paper","title","Research title ".repeat(24),"authors","Author Name, ".repeat(40),"metadata","明确提供的论文元数据，没有全文实验依据。");
        var response=request("POST","/conversations/"+conversation()+"/analyses",Map.of("task","plain","papers",List.of(paper)),UUID.randomUUID().toString());
        assertEquals(202,response.statusCode(),response.body());assertEquals("insufficient_data",awaitAnalysis(json.read(response.body()).path("id").asString()).path("state").asString());
    }
    @Test void metadataComparisonAndNotesRemainScopedAndImmutable()throws Exception{
        String workspace=conversation(),key=UUID.randomUUID().toString();var input=Map.of("task","compare","papers",List.of(analysisPaper("a","论文A"),analysisPaper("b","论文B")));
        var created=request("POST","/conversations/"+workspace+"/analyses",input,key);assertEquals(202,created.statusCode(),created.body());String id=json.read(created.body()).path("id").asString();var done=awaitAnalysis(id);
        assertEquals("insufficient_data",done.path("state").asString(),done.toString());assertEquals(6,done.path("result").path("comparison").size());assertEquals(2,done.path("result").path("comparison").path(0).path("cells").size());assertTrue(done.path("result").path("comparison").path(3).toString().contains("资料不足"));
        assertEquals("provided_metadata",done.path("result").path("evidence").path(0).path("kind").asString());assertEquals("unverified",done.path("result").path("evidence").path(1).path("kind").asString());assertTrue(done.path("result").path("evidence").path(1).path("page").isNull());
        assertEquals(id,json.read(request("POST","/conversations/"+workspace+"/analyses",input,key).body()).path("id").asString());
        var note=request("POST","/conversations/"+workspace+"/notes",Map.of("analysisId",id,"title","比较记录","body","我的判断"),"save-"+id);assertEquals(201,note.statusCode(),note.body());String noteId=json.read(note.body()).path("id").asString();
        assertEquals(200,request("PATCH","/notes/"+noteId,Map.of("title","修订笔记","body","补充人工判断","revision",1),null).statusCode());assertEquals(409,request("PATCH","/notes/"+noteId,Map.of("title","旧版本覆盖","body","bad","revision",1),null).statusCode());
        var saved=json.read(request("GET","/notes?conversationId="+workspace,null,null).body()).path("items").path(0);assertEquals("论文A",saved.path("snapshot").path("sources").path(0).path("title").asString());assertEquals("补充人工判断",saved.path("body").asString());
        assertEquals(404,request("POST","/conversations/"+conversation()+"/notes",Map.of("analysisId",id,"title","wrong workspace","body","bad"),UUID.randomUUID().toString()).statusCode());
    }
    @Test void pdfAnalysisVerifiesQuoteAndUsesActualPageInsteadOfModelPage()throws Exception{
        String workspace=conversation();String doc=upload(workspace,new String[]{"evidence.pdf"},new byte[][]{pdf(true,false)}).path("items").path(0).path("document").path("id").asString();
        var input=Map.of("task","method","papers",List.of(Map.of("id","a","title","受控PDF","documentId",doc)));
        var created=request("POST","/conversations/"+workspace+"/analyses",input,UUID.randomUUID().toString());assertEquals(202,created.statusCode(),created.body());var done=awaitAnalysis(json.read(created.body()).path("id").asString());assertEquals("success",done.path("state").asString(),done.toString());
        var evidence=done.path("result").path("evidence");assertEquals("original_support",evidence.path(0).path("kind").asString());assertEquals(1,evidence.path(0).path("page").asInt());assertTrue(evidence.path(1).path("page").isNull());
        assertEquals(409,request("POST","/conversations/"+conversation()+"/analyses",input,UUID.randomUUID().toString()).statusCode());
    }
    @Test void missingEvidenceAndInvalidAnalysisDoNotPretendToRun()throws Exception{
        int before=REQUESTS.size();String workspace=conversation();var created=request("POST","/conversations/"+workspace+"/analyses",Map.of("task","plain","papers",List.of(Map.of("id","empty","title","只有标题"))),UUID.randomUUID().toString());assertEquals(202,created.statusCode());var value=json.read(created.body());assertEquals("insufficient_data",value.path("state").asString());assertTrue(value.path("runId").isNull());assertEquals(before,REQUESTS.size());
        assertEquals(422,request("POST","/conversations/"+workspace+"/analyses",Map.of("task","formula","papers",List.of(analysisPaper("a","论文"))),UUID.randomUUID().toString()).statusCode());assertEquals(422,request("POST","/conversations/"+workspace+"/analyses",Map.of("task","compare","papers",List.of(analysisPaper("a","论文"))),UUID.randomUUID().toString()).statusCode());
        assertEquals(404,request("GET","/analyses/00000000-0000-0000-0000-000000000000",null,null).statusCode());assertEquals(404,request("PATCH","/notes/unknown",Map.of("title","x","body","y","revision",1),null).statusCode());
    }
    String skillRun(String prompt, String primary, List<String> tools) throws Exception {
        var response=request("POST","/conversations/"+conversation()+"/runs",Map.of("prompt",prompt,"skillId",primary,"toolSkillIds",tools),UUID.randomUUID().toString());
        assertEquals(202,response.statusCode(),response.body());return json.read(response.body()).path("runId").asString();
    }
    @Test void modelConfigurationEncryptsSecretsAndPreservesBlankKeyWithoutReturningIt() throws Exception {
        String secret="fixture-personal-key-not-a-real-key";
        var saved=request("PUT","/settings/model",modelInput(0,secret),null);assertEquals(200,saved.statusCode(),saved.body());assertFalse(saved.body().contains(secret));
        assertTrue(json.read(saved.body()).path("hasApiKey").asBoolean());
        String cipher=jdbc.queryForObject("SELECT api_key_cipher FROM model_configuration WHERE owner_id='owner'",String.class);
        assertNotEquals(secret,cipher);assertTrue(cipher.startsWith("v1:"));
        assertFalse(request("GET","/settings/model",null,null).body().contains(secret));
        assertEquals(409,request("PUT","/settings/model",modelInput(0,secret),null).statusCode());
        assertEquals(200,request("PUT","/settings/model",modelInput(1,""),null).statusCode());
        assertTrue(json.read(request("GET","/settings/model",null,null).body()).path("hasApiKey").asBoolean());
        int before=REQUESTS.size();assertEquals(200,request("POST","/settings/model/test",null,null).statusCode());assertEquals(before+1,REQUESTS.size());
        var sent=json.read(REQUESTS.get(REQUESTS.size()-1));assertEquals("saved-fixture-model",sent.path("model").asString());assertEquals(16,sent.path("max_completion_tokens").asInt());
    }
    @Test void clearingKeyBlocksRunsAndInvalidEndpointCannotBeSaved() throws Exception {
        assertEquals(200,request("PUT","/settings/model",modelInput(0,"fixture-secret"),null).statusCode());
        var clear=new LinkedHashMap<>(modelInput(1,""));clear.put("clearApiKey",true);assertEquals(200,request("PUT","/settings/model",clear,null).statusCode());
        assertFalse(json.read(request("GET","/settings/model",null,null).body()).path("configured").asBoolean());
        assertEquals(503,request("POST","/conversations/"+conversation()+"/runs",Map.of("prompt","must not call"),UUID.randomUUID().toString()).statusCode());
        var bad=new LinkedHashMap<>(modelInput(2,"fixture"));bad.put("baseUrl","file:///etc/passwd");assertEquals(503,request("PUT","/settings/model",bad,null).statusCode());
    }
    @Test void customSkillRevisionDisabledAndDeletionAreEnforced() throws Exception {
        var s=skill("先核验证据，禁止编造。" );String id=s.path("id").asString();
        assertEquals(200,request("GET","/skills/"+id,null,null).statusCode());
        var update=Map.of("name","证据核验新版","description","比较条件","instructions","按方法、证据、局限输出。","enabled",true,"revision",1);
        assertEquals(200,request("PUT","/skills/"+id,update,null).statusCode());assertEquals(409,request("PUT","/skills/"+id,update,null).statusCode());
        assertEquals(200,request("PATCH","/skills/"+id+"/enabled",Map.of("enabled",false),null).statusCode());
        assertEquals(409,request("POST","/conversations/"+conversation()+"/runs",Map.of("prompt","分析","skillId",id),UUID.randomUUID().toString()).statusCode());
        assertEquals(204,request("DELETE","/skills/"+id,null,null).statusCode());assertEquals(404,request("GET","/skills/"+id,null,null).statusCode());
        assertEquals(422,request("POST","/conversations/"+conversation()+"/runs",Map.of("prompt","检索","skillId","paper_search"),UUID.randomUUID().toString()).statusCode());
    }
    @Test void savedPreferenceAndExplicitSkillReachTheSupplierAndAuditSnapshot() throws Exception {
        String id=skill("EVIDENCE_ONLY: 逐项核验原文事实和推断。").path("id").asString();
        var preference=Map.of("researchContext","研究背景：因果 TCN，回答先给结论。","defaultSkillId",id,"allowSkillTools",false,"assistantMode","model");
        assertEquals(200,request("PUT","/settings/preferences",preference,null).statusCode());
        assertEquals("model",json.read(request("GET","/settings/preferences",null,null).body()).path("assistantMode").asString());
        String run=run(conversation(),"分析局限",List.of(),UUID.randomUUID().toString());assertEquals("succeeded",await(run).path("status").asString());
        String sent=REQUESTS.get(REQUESTS.size()-1);assertTrue(sent.contains("EVIDENCE_ONLY"));assertTrue(sent.contains("因果 TCN"));
        assertTrue(jdbc.queryForObject("SELECT skill_snapshot FROM run_record WHERE id=?",String.class,run).contains(id));
        assertFalse(jdbc.queryForObject("SELECT model_snapshot FROM run_record WHERE id=?",String.class,run).contains("fixture-key"));
    }
    @Test void fragmentedToolCallLoadsOnlyAllowedSkillAndResumesStreaming() throws Exception {
        String id=skill("TOOL_EVIDENCE_MARKER: 先找证据，再给分析。").path("id").asString();int before=REQUESTS.size();
        String run=skillRun("TOOL_SELECT 选择证据检查 Skill", "chat",List.of(id));var done=await(run);
        assertEquals("succeeded",done.path("status").asString(),done.toString());assertEquals(before+2,REQUESTS.size());assertEquals(40,done.path("promptTokens").asInt());
        assertTrue(REQUESTS.get(REQUESTS.size()-1).contains("TOOL_EVIDENCE_MARKER"));assertTrue(REQUESTS.get(REQUESTS.size()-1).contains("tool_call_id"));
        assertTrue(request("GET","/runs/"+run+"/events",null,null).body().contains("event:skill_called"));
    }
    @Test void unauthorizedAndMalformedModelSkillArgumentsDoNotExecute() throws Exception {
        String id=skill("只核验证据。").path("id").asString();int before=REQUESTS.size();
        var denied=await(skillRun("TOOL_SELECT TOOL_FORBIDDEN", "chat",List.of(id)));assertEquals("SKILL_NOT_ALLOWED",denied.path("errorCode").asString());
        var malformed=await(skillRun("TOOL_SELECT TOOL_BAD_JSON", "chat",List.of(id)));assertEquals("TOOL_ARGUMENT_INVALID",malformed.path("errorCode").asString());
        assertEquals(before+2,REQUESTS.size());
    }
    @Test void queuedRunUsesItsOriginalSkillVersionAfterEditing() throws Exception {
        String skill=skill("ORIGINAL_SKILL_VERSION").path("id").asString();
        String blocker=run(conversation(),"SLOW_RESPONSE blocker",List.of(),UUID.randomUUID().toString());
        String queued=skillRun("分析证据",skill,List.of());
        assertEquals(200,request("PUT","/skills/"+skill,Map.of("name","新版","description","新版","instructions","CHANGED_SKILL_VERSION","enabled",true,"revision",1),null).statusCode());
        await(blocker);assertEquals("succeeded",await(queued).path("status").asString());
        String sent=REQUESTS.get(REQUESTS.size()-1);assertTrue(sent.contains("ORIGINAL_SKILL_VERSION"));assertFalse(sent.contains("CHANGED_SKILL_VERSION"));
    }
    @Test void foreignOwnerSkillsAreInvisibleAndUnavailableForRuns() throws Exception {
        String foreign="custom_foreign_"+UUID.randomUUID().toString().replace("-","").substring(0,12),now=Instant.now().toString();
        String owner=UUID.randomUUID().toString();jdbc.update("INSERT INTO orbit_user VALUES(?,?,?,?)",owner,owner,"not-valid-hash",now);
        jdbc.update("INSERT INTO custom_skill VALUES(?,?,?,?,?,1,TRUE,FALSE,?,?)",foreign,owner,"private","private","SECRET_FOREIGN_INSTRUCTION",now,now);
        assertEquals(404,request("GET","/skills/"+foreign,null,null).statusCode());assertFalse(request("GET","/skills",null,null).body().contains("SECRET_FOREIGN_INSTRUCTION"));
        assertEquals(404,request("POST","/conversations/"+conversation()+"/runs",Map.of("prompt","analysis","toolSkillIds",List.of(foreign)),UUID.randomUUID().toString()).statusCode());
    }
    JsonNode await(String id) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < until) {
            var response = request("GET", "/runs/" + id, null, null); assertEquals(200, response.statusCode());
            JsonNode value = json.read(response.body());
            if (RunStore.terminal(value.path("status").asString())) return value;
            Thread.sleep(25);
        }
        fail("Run did not settle within deadline"); return null;
    }
    String run(String conversation, String prompt, List<String> documents, String key) throws Exception {
        var response = request("POST", "/conversations/" + conversation + "/runs", Map.of("prompt", prompt, "documentIds", documents), key);
        assertEquals(202, response.statusCode(), response.body()); return json.read(response.body()).path("runId").asString();
    }
    byte[] pdf(boolean text, boolean encrypted) throws Exception {
        try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage(); pdf.addPage(page);
            if (text) try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12); content.newLineAtOffset(50, 700); content.showText("Evidence from a controlled PDF fixture."); content.endText();
            }
            if (encrypted) pdf.protect(new StandardProtectionPolicy("owner-secret", "reader-secret", new AccessPermission()));
            pdf.save(out); return out.toByteArray();
        }
    }
    JsonNode upload(String conversation, String[] names, byte[][] files) throws Exception {
        String boundary = "Orbit" + UUID.randomUUID(); var out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"conversationId\"\r\n\r\n" + conversation + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (int index = 0; index < files.length; index++) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + names[index] + "\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(files[index]); out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var response = client.send(HttpRequest.newBuilder(uri("/documents")).header("X-CSRF-TOKEN", csrf).header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(Set.of(201,207,422).contains(response.statusCode()), response.body()); return json.read(response.body());
    }
    @Test void healthAuthenticationCsrfAndOneTimeSetup() throws Exception {
        var anonymous = HttpClient.newHttpClient();
        assertEquals(200, anonymous.send(HttpRequest.newBuilder(uri("/health")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        var denied = anonymous.send(HttpRequest.newBuilder(uri("/conversations")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, denied.statusCode()); assertNotEquals("null", json.read(denied.body()).path("requestId").asString());
        assertEquals(403, client.send(HttpRequest.newBuilder(uri("/conversations")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"missing csrf\"}")).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        int accountCount=jdbc.queryForObject("SELECT COUNT(*) FROM orbit_user", Integer.class);
        String ownerHash=jdbc.queryForObject("SELECT password_hash FROM orbit_user WHERE id='owner'", String.class);
        assertEquals(409, request("POST", "/setup", Map.of("username", "other", "password", "fixture-other-123"), null).statusCode());
        assertEquals(accountCount,jdbc.queryForObject("SELECT COUNT(*) FROM orbit_user", Integer.class));
        assertEquals(ownerHash,jdbc.queryForObject("SELECT password_hash FROM orbit_user WHERE id='owner'", String.class));
    }
    @Test void conversationPersistenceRenameValidationAndOwnership() throws Exception {
        String id = conversation();
        assertEquals(200, request("PATCH", "/conversations/" + id, Map.of("title", "持久化研究"), null).statusCode());
        assertEquals("持久化研究", json.read(request("GET", "/conversations/" + id, null, null).body()).path("conversation").path("title").asString());
        assertEquals(400, request("POST", "/conversations", Map.of("title", " "), null).statusCode());
        assertEquals(400, request("GET", "/conversations?size=1000", null, null).statusCode());
        jdbc.update("INSERT INTO orbit_user VALUES(?,?,?,?)", "other-owner", "other-owner", "not-a-valid-login-hash", Instant.now().toString());
        jdbc.update("INSERT INTO conversation(id,owner_id,title,created_at,updated_at) VALUES(?,?,?,?,?)", "private-conversation", "other-owner", "private", Instant.now().toString(), Instant.now().toString());
        assertEquals(404, request("GET", "/conversations/private-conversation", null, null).statusCode());
        for(String action:List.of("delete","restore","archive","unarchive","pin","unpin"))assertEquals(404,request("POST","/conversations/private-conversation/manage",Map.of("action",action),null).statusCode());
        assertEquals(404,request("PATCH","/conversations/private-conversation/workspace",Map.of("workspaceId","foreign-space"),null).statusCode());
    }
    @Test void streamingRoundTripUsageIdempotencyAndReplay() throws Exception {
        String conversation = conversation(), key = UUID.randomUUID().toString(); int before = REQUESTS.size();
        String id = run(conversation, "解释证据边界", List.of(), key); var done = await(id);
        assertEquals("succeeded", done.path("status").asString(), done.toString()); assertEquals("证据与分析应区分。", done.path("answer").asString());
        assertEquals(20, done.path("promptTokens").asInt());
        assertEquals(id, run(conversation, "解释证据边界", List.of(), key)); assertEquals(before + 1, REQUESTS.size());
        assertEquals(409, request("POST", "/conversations/" + conversation + "/runs", Map.of("prompt", "不同问题"), key).statusCode());
        var events = request("GET", "/runs/" + id + "/events?after=1", null, null);
        assertEquals(200, events.statusCode()); assertFalse(events.body().contains("id:1\n")); assertTrue(events.body().contains("event:answer_delta")); assertTrue(events.body().contains("event:run_completed"));
        var messages = json.read(request("GET", "/conversations/" + conversation, null, null).body()).path("messages"); assertEquals(2, messages.size());
    }
    @Test void brokenStreamKeepsPartialAnswerWithoutSuccessEvent() throws Exception {
        String id = run(conversation(), "BROKEN_STREAM", List.of(), UUID.randomUUID().toString()); var done = await(id);
        assertEquals("partial", done.path("status").asString()); assertEquals("证据", done.path("answer").asString()); assertEquals("LLM_INCOMPLETE", done.path("errorCode").asString());
        var events = request("GET", "/runs/" + id + "/events", null, null).body(); assertFalse(events.contains("event:run_completed")); assertTrue(events.contains("event:run_failed"));
    }
    @Test void cancelBusyConversationAndUpstreamTimeout() throws Exception {
        String conversation = conversation(), id = run(conversation, "SLOW_RESPONSE cancel", List.of(), UUID.randomUUID().toString());
        assertEquals(409, request("POST", "/conversations/" + conversation + "/runs", Map.of("prompt", "第二个请求"), UUID.randomUUID().toString()).statusCode());
        assertEquals("cancelled", json.read(request("POST", "/runs/" + id + "/cancel", null, null).body()).path("status").asString());
        String timeout = run(conversation(), "SLOW_RESPONSE timeout", List.of(), UUID.randomUUID().toString());
        assertEquals("LLM_TIMEOUT", await(timeout).path("errorCode").asString());
    }
    @Test void supplierErrorsAreNotReportedAsSuccessOrLeakedSecrets() throws Exception {
        String id = run(conversation(), "UPSTREAM_429", List.of(), UUID.randomUUID().toString());
        var done = await(id); assertEquals("failed", done.path("status").asString()); assertEquals("LLM_UPSTREAM_429", done.path("errorCode").asString());
        assertFalse(request("GET", "/providers", null, null).body().contains("fixture-key"));
        var blank = new LlmGateway(new LlmProperties("", "", "", false, 120, 2048, false, "max_tokens"), json);
        assertEquals("LLM_NOT_CONFIGURED", assertThrows(ApiException.class, blank::requireConfigured).code());
    }
    @Test void mixedUploadDeduplicationRangeAndScannedPdf() throws Exception {
        String conversation = conversation(); byte[] paper = pdf(true, false);
        var result = upload(conversation, new String[]{"研究论文.pdf", "invalid.pdf", "locked.pdf", "scan.pdf"}, new byte[][]{paper, "not PDF".getBytes(), pdf(false,true), pdf(false,false)}).path("items");
        assertEquals("研究论文.pdf", result.path(0).path("document").path("name").asString());
        assertEquals("INVALID_PDF", result.path(1).path("code").asString()); assertEquals("PDF_ENCRYPTED", result.path(2).path("code").asString());
        assertEquals("readable_only", result.path(3).path("document").path("parseStatus").asString());
        String id = result.path(0).path("document").path("id").asString();
        var duplicate = upload(conversation(), new String[]{"renamed.pdf"}, new byte[][]{paper}).path("items").path(0);
        assertTrue(duplicate.path("reused").asBoolean()); assertEquals(id, duplicate.path("document").path("id").asString());
        var range = client.send(HttpRequest.newBuilder(uri("/documents/" + id + "/versions/" + id + "/content")).header("Range", "bytes=0-4").GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.US_ASCII));
        assertEquals(206, range.statusCode(), range.body()); assertEquals("%PDF-", range.body());
        assertEquals(404, request("GET", "/documents/" + id + "/versions/not-this-version/content", null, null).statusCode());
    }
    @Test void annotationsUseImmutableFileAndOptimisticRevision() throws Exception {
        var doc = upload(conversation(), new String[]{"annotate.pdf"}, new byte[][]{pdf(true,false)}).path("items").path(0).path("document");
        String id = doc.path("id").asString();
        Map<String,Object> input = new LinkedHashMap<>(Map.of("versionId", id, "hash", doc.path("hash").asString(), "page", 1, "type", "note", "quote", "Evidence", "comment", "核对证据", "color", "#B69AFF", "origin", "user", "rects", List.of(List.of(50,700,200,700,50,680,200,680))));
        var response = request("POST", "/documents/" + id + "/annotations", input, null); assertEquals(201, response.statusCode(), response.body()); String annotation = json.read(response.body()).path("id").asString();
        assertEquals(200, request("PATCH", "/annotations/" + annotation, Map.of("revision",1,"comment","已复核","color","#70D8BE"), null).statusCode());
        assertEquals(409, request("PATCH", "/annotations/" + annotation, Map.of("revision",1,"comment","旧版本","color","#70D8BE"), null).statusCode());
        input.put("rects", List.of(List.of(900,700,1000,700,900,680,1000,680)));
        assertEquals(400, request("POST", "/documents/" + id + "/annotations", input, null).statusCode());
        input.put("hash", "wrong-version"); assertEquals(409, request("POST", "/documents/" + id + "/annotations", input, null).statusCode());
        assertEquals(200, request("PUT", "/documents/" + id + "/reading-position", Map.of("versionId",id,"page",1,"zoom",1.5,"rotation",90), null).statusCode());
        assertEquals(90, json.read(request("GET", "/documents/" + id + "/reading-position", null, null).body()).path("rotation").asInt());
        assertEquals(409, request("DELETE", "/annotations/" + annotation + "?revision=1", null, null).statusCode());
        assertEquals(204, request("DELETE", "/annotations/" + annotation + "?revision=2", null, null).statusCode());
    }
    @Test void selectedDocumentsReachModelOnlyAfterExplicitRequest() throws Exception {
        String conversation = conversation(); int before = REQUESTS.size();
        var doc = upload(conversation, new String[]{"source.pdf"}, new byte[][]{pdf(true,false)}).path("items").path(0).path("document");
        assertEquals(before, REQUESTS.size(), "Uploading must not call a model");
        String id = doc.path("id").asString();
        assertEquals("succeeded", await(run(conversation, "只解释这份材料", List.of(id), UUID.randomUUID().toString())).path("status").asString());
        String sent = REQUESTS.get(REQUESTS.size()-1); assertTrue(sent.contains(id)); assertTrue(sent.contains("Evidence from a controlled PDF fixture")); assertTrue(sent.contains("PDF 第 1 页"));
        assertEquals(409, request("POST", "/conversations/" + conversation() + "/runs", Map.of("prompt","越界材料","documentIds",List.of(id)), UUID.randomUUID().toString()).statusCode());
    }
    @Test void concurrentNetworkRetriesProduceOneSupplierCall() throws Exception {
        String conversation = conversation(), key = UUID.randomUUID().toString(); int before = REQUESTS.size();
        var threads = Executors.newFixedThreadPool(6);
        try {
            List<Future<String>> retries = new ArrayList<>();
            for (int n = 0; n < 6; n++) retries.add(threads.submit(() -> run(conversation, "并发幂等请求", List.of(), key)));
            Set<String> ids = new HashSet<>(); for (var retry : retries) ids.add(retry.get(10, TimeUnit.SECONDS));
            assertEquals(1, ids.size()); assertEquals("succeeded", await(ids.iterator().next()).path("status").asString());
            assertEquals(before + 1, REQUESTS.size());
        } finally { threads.shutdownNow(); }
    }
    @Test void restartMarksPersistedTasksInterruptedWithoutReplayingSupplierCalls() throws Exception {
        String conversation = conversation(), id = UUID.randomUUID().toString(), now = Instant.now().toString(); int before = REQUESTS.size();
        jdbc.update("INSERT INTO run_record(id,owner_id,conversation_id,idempotency_key,request_hash,prompt,document_ids,status,answer,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'queued','',?,?)", id, "owner", conversation, id, "hash", "pending before restart", "[]", now, now);
        runs.interrupted();
        assertEquals("interrupted", store.get("owner", id).status()); assertEquals("SERVER_RESTARTED", store.get("owner", id).errorCode()); assertEquals(before, REQUESTS.size());
    }
    @Test void contextOverflowRollsBackAdmissionAndPrivateEndpointsRequireDeploymentOptIn() throws Exception {
        String conversation = conversation(); int before = REQUESTS.size();
        for (int n = 0; n < 4; n++) jdbc.update("INSERT INTO chat_message VALUES(?,?,?,?,?,?)", UUID.randomUUID().toString(), conversation, null, "user", "a".repeat(8000), Instant.now().toString());
        assertEquals(422, request("POST", "/conversations/" + conversation + "/runs", Map.of("prompt", "over limit"), UUID.randomUUID().toString()).statusCode());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE conversation_id=?", Integer.class, conversation)); assertEquals(before, REQUESTS.size());
        var privateGateway = new LlmGateway(new LlmProperties("https://127.0.0.1/v1", "fixture", "fixture", false, 120, 2048, false, "max_tokens"), json);
        assertEquals("LLM_CONFIG_INVALID", assertThrows(ApiException.class, privateGateway::requireConfigured).code());
        assertEquals(400, request("GET", "/conversations?page=bad", null, null).statusCode());
        assertEquals(404, request("GET", "/not-an-api", null, null).statusCode());
    }
    @Test void passwordChangeRequiresCurrentSecretAndInvalidatesTheSession() throws Exception {
        assertEquals(403, request("POST", "/auth/password", Map.of("currentPassword", "wrong-secret", "newPassword", "fixture-new-password-456"), null).statusCode());
        assertEquals(204, request("POST", "/auth/password", Map.of("currentPassword", "fixture-password-123", "newPassword", "fixture-new-password-456"), null).statusCode());
        assertEquals(401, request("GET", "/auth/me", null, null).statusCode());
        refreshCsrf(); authenticate("fixture-new-password-456");
        assertEquals(204, request("POST", "/auth/password", Map.of("currentPassword", "fixture-new-password-456", "newPassword", "fixture-password-123"), null).statusCode());
        refreshCsrf(); authenticate("fixture-password-123");
    }
    @AfterAll void stop() { MODEL.stop(0); MODEL_THREADS.shutdownNow(); }

    String readingConversation(String space,String hash)throws Exception{
        var c=request("POST","/conversations",Map.of("title","QA 阅读对话","workspaceId",space),null);assertEquals(201,c.statusCode(),c.body());String id=json.read(c.body()).path("id").asString();
        var bound=request("PUT","/reading-conversations/"+id,Map.of("documentId","pdf-"+hash.substring(0,24),"documentVersion",hash,"initialPage",5),null);assertEquals(200,bound.statusCode(),bound.body());return id;
    }
    Map<String,Object> readingInput(String hash,List<?> evidence){return Map.of("documentId","pdf-"+hash.substring(0,24),"documentVersion",hash,"evidence",evidence);}
    @Test void readingBindingIsImmutableAndWorkspaceScoped()throws Exception{
        String hash="a".repeat(64),space="qa-reading-"+UUID.randomUUID(),c=readingConversation(space,hash);
        var same=request("PUT","/reading-conversations/"+c,Map.of("documentId","pdf-"+hash.substring(0,24),"documentVersion",hash,"initialPage",8),null);assertEquals(200,same.statusCode());assertEquals(5,json.read(same.body()).path("initialPage").asInt());
        String other="b".repeat(64);assertEquals(409,request("PUT","/reading-conversations/"+c,Map.of("documentId","pdf-"+other.substring(0,24),"documentVersion",other,"initialPage",1),null).statusCode());
        String query="/reading-conversations?workspaceId="+space+"&documentId=pdf-"+hash.substring(0,24)+"&documentVersion="+hash;
        assertEquals(c,json.read(request("GET",query,null,null).body()).path("items").path(0).path("id").asString());
        assertEquals(0,json.read(request("GET",query.replace(space,"other-space"),null,null).body()).path("items").size());
        assertEquals(0,json.read(request("GET",query.replace(hash,other),null,null).body()).path("items").size());
    }
    @Test void readingMultiTurnEvidenceIsExplicitAndRetained()throws Exception{
        String hash="c".repeat(64),c=readingConversation("qa-evidence",hash),marker="QA_AUTHORIZED_7821";int before=REQUESTS.size();
        var noEvidence=Map.of("prompt","本轮未引用原文，说明来源范围。","skillId","chat","readingContext",readingInput(hash,List.of()));
        var first=request("POST","/conversations/"+c+"/runs",noEvidence,UUID.randomUUID().toString());assertEquals(202,first.statusCode(),first.body());await(json.read(first.body()).path("runId").asString());assertFalse(REQUESTS.get(REQUESTS.size()-1).contains(marker));
        var evidence=Map.of("chunkId","p4-b1","pageIndex",4,"quote",marker+" The low rank matrices are trained while base weights are frozen.","scope","selection","rects",List.of(List.of(1.,2.,3.,2.,1.,4.,3.,4.)));
        var second=request("POST","/conversations/"+c+"/runs",Map.of("prompt","解释明确引用的这段方法。","skillId","paper_read","readingContext",readingInput(hash,List.of(evidence))),UUID.randomUUID().toString());assertEquals(202,second.statusCode(),second.body());String run=json.read(second.body()).path("runId").asString();assertEquals("succeeded",await(run).path("status").asString());
        assertTrue(REQUESTS.get(REQUESTS.size()-1).contains(marker));assertFalse(REQUESTS.get(REQUESTS.size()-1).contains("UNAUTHORIZED_PRIVATE_17"));
        var third=request("POST","/conversations/"+c+"/runs",Map.of("prompt","为什么这会影响Adam状态开销？","skillId","chat","readingContext",readingInput(hash,List.of())),UUID.randomUUID().toString());assertEquals(202,third.statusCode(),third.body());await(json.read(third.body()).path("runId").asString());assertEquals(before+3,REQUESTS.size());assertTrue(REQUESTS.get(REQUESTS.size()-1).contains(marker));
        var restored=json.read(request("GET","/conversations/"+c,null,null).body());assertEquals(6,restored.path("messages").size());assertTrue(restored.toString().contains(marker));assertTrue(restored.toString().contains("reading"));
        assertTrue(jdbc.queryForObject("SELECT snapshot FROM reading_request_snapshot WHERE run_id=?",String.class,run).contains(marker));
    }
    @Test void readingRejectsWrongVersionAndImplicitFileScopeBeforeSupplier()throws Exception{
        String hash="d".repeat(64),c=readingConversation("qa-guard",hash);int before=REQUESTS.size();
        var wrong=request("POST","/conversations/"+c+"/runs",Map.of("prompt","wrong version","readingContext",readingInput("e".repeat(64),List.of())),UUID.randomUUID().toString());assertEquals(409,wrong.statusCode(),wrong.body());
        var files=request("POST","/conversations/"+c+"/runs",Map.of("prompt","implicit file","readingContext",readingInput(hash,List.of()),"documentIds",List.of("should-never-upload")),UUID.randomUUID().toString());assertEquals(422,files.statusCode());assertEquals(before,REQUESTS.size());
        var invalid=Map.of("chunkId","bad","pageIndex",-1,"quote","bad","scope","selection","rects",List.of());
        assertEquals(400,request("POST","/conversations/"+c+"/runs",Map.of("prompt","bad anchor","readingContext",readingInput(hash,List.of(invalid))),UUID.randomUUID().toString()).statusCode());assertEquals(before,REQUESTS.size());
        var missingScope=Map.of("chunkId","missing-scope","pageIndex",0,"quote","not authorized scope","rects",List.of());
        assertEquals(400,request("POST","/conversations/"+c+"/runs",Map.of("prompt","missing scope","readingContext",readingInput(hash,List.of(missingScope))),UUID.randomUUID().toString()).statusCode());
        assertEquals(400,request("POST","/conversations/"+c+"/runs",Map.of("prompt","null evidence","readingContext",readingInput(hash,java.util.Collections.singletonList(null))),UUID.randomUUID().toString()).statusCode());assertEquals(before,REQUESTS.size());
    }
    @Test void readingAcceptsLongMixedFontSelectionWithMoreThan100Anchors()throws Exception{
        String hash="f".repeat(64),c=readingConversation("qa-long-selection",hash);int before=REQUESTS.size();
        var rects=new java.util.ArrayList<List<Double>>();for(int i=0;i<118;i++)rects.add(List.of(10.0+i,20.0,11.0+i,20.0,10.0+i,21.0,11.0+i,21.0));
        String quote="QA_LONG_SELECTION "+"x".repeat(1789);
        var evidence=Map.of("chunkId","selection-3","pageIndex",2,"quote",quote,"scope","selection","rects",rects);
        var result=request("POST","/conversations/"+c+"/runs",Map.of("prompt","解释合法的长选区","skillId","chat","readingContext",readingInput(hash,List.of(evidence))),UUID.randomUUID().toString());
        assertEquals(202,result.statusCode(),result.body());String run=json.read(result.body()).path("runId").asString();assertEquals("succeeded",await(run).path("status").asString());assertEquals(before+1,REQUESTS.size());assertTrue(REQUESTS.get(REQUESTS.size()-1).contains(quote));
        var snapshot=json.read(jdbc.queryForObject("SELECT snapshot FROM reading_request_snapshot WHERE run_id=?",String.class,run));assertEquals(118,snapshot.path("evidence").path(0).path("rects").size());assertEquals(quote,snapshot.path("evidence").path(0).path("quote").asString());
    }
    @Test void readingStillBoundsExcessiveAnchorsWithoutCallingSupplier()throws Exception{
        String hash="9".repeat(64),c=readingConversation("qa-anchor-limit",hash);int before=REQUESTS.size();
        var rect=List.of(1.0,2.0,3.0,2.0,1.0,4.0,3.0,4.0);
        var evidence=Map.of("chunkId","selection-1","pageIndex",0,"quote","bounded selection","scope","selection","rects",java.util.Collections.nCopies(8193,rect));
        var response=request("POST","/conversations/"+c+"/runs",Map.of("prompt","too many positions","readingContext",readingInput(hash,List.of(evidence))),UUID.randomUUID().toString());
        assertEquals(422,response.statusCode(),response.body());assertEquals("READING_ANCHOR_LIMIT",json.read(response.body()).path("code").asString());assertEquals(before,REQUESTS.size());
    }
    @Test void readingCannotRebrandExistingChatContext()throws Exception{
        String c=conversation();await(run(c,"other task history",List.of(),UUID.randomUUID().toString()));String hash="f".repeat(64);
        assertEquals(409,request("PUT","/reading-conversations/"+c,Map.of("documentId","pdf-"+hash.substring(0,24),"documentVersion",hash,"initialPage",1),null).statusCode());
    }
}

package com.orbit.research.skill;

import com.orbit.research.common.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Public GitHub metadata only. Never follows redirects or sends model/user credentials. */
@Component
public class GithubRepositoryClient {
    private final Json json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    public GithubRepositoryClient(Json json){this.json=json;}
    public JsonNode get(String path){
        if(!path.startsWith("/repos/")||path.contains("\\")||path.contains("#"))throw new ApiException(400,"GITHUB_URL_INVALID","GitHub 请求路径无效。");
        var request=HttpRequest.newBuilder(URI.create("https://api.github.com"+path)).timeout(Duration.ofSeconds(15)).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2026-03-10").header("User-Agent","Orbit-Research-Skill-Importer").GET().build();
        try{var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());try(var body=response.body()){
            if(response.statusCode()==403||response.statusCode()==429)throw new ApiException(502,"GITHUB_RATE_LIMIT","GitHub 请求受限，请稍后重试。");
            if(response.statusCode()!=200)throw new ApiException(422,"GITHUB_SOURCE_UNAVAILABLE","无法读取公开 GitHub 仓库、分支或 SKILL.md，请检查链接。");
            byte[] data=body.readNBytes(180001);if(data.length>180000)throw new ApiException(422,"GITHUB_FILE_LIMIT","GitHub 返回内容过大。");
            return json.read(new String(data,StandardCharsets.UTF_8));
        }}catch(ApiException e){throw e;}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new ApiException(502,"GITHUB_CONNECTION_FAILED","GitHub 连接失败或超时，Skill 尚未安装。");}
    }
}

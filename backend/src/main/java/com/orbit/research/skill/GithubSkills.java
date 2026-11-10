package com.orbit.research.skill;

import com.orbit.research.common.*;
import com.orbit.research.document.Documents;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class GithubSkills {
    public record Target(String owner,String repo,String ref,String file){}
    public record Installed(Skills.Skill skill,String sourceUrl,String commitSha,String contentHash,boolean reused,String mode){}
    private final GithubRepositoryClient github;private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final Skills skills;
    public GithubSkills(GithubRepositoryClient github,JdbcTemplate jdbc,TransactionTemplate tx,Skills skills){this.github=github;this.jdbc=jdbc;this.tx=tx;this.skills=skills;}
    public static Target parse(String url){
        try{var uri=URI.create(url);if(!"https".equals(uri.getScheme())||!"github.com".equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException();
            var parts=new ArrayList<>(Arrays.asList(uri.getPath().split("/")));parts.removeIf(String::isEmpty);
            if(parts.size()<2||!parts.get(0).matches("[A-Za-z0-9_.-]{1,100}")||!parts.get(1).matches("[A-Za-z0-9_.-]{1,100}"))throw new IllegalArgumentException();
            String repo=parts.get(1).replaceFirst("\\.git$",""),ref="",file="SKILL.md";
            if(parts.size()>2){if(parts.size()<4||!Set.of("tree","blob").contains(parts.get(2)))throw new IllegalArgumentException();ref=parts.get(3);if(!ref.matches("[A-Za-z0-9_.-]{1,150}"))throw new IllegalArgumentException();file=String.join("/",parts.subList(4,parts.size()));if(!file.endsWith("SKILL.md"))file=(file.isEmpty()?"":file+"/")+"SKILL.md";}
            for(String p:file.split("/"))if(p.equals(".")||p.equals("..")||!p.matches("[\\p{L}\\p{N}_. @()+-]+"))throw new IllegalArgumentException();
            return new Target(parts.get(0),repo,ref,file);
        }catch(Exception e){throw new ApiException(400,"GITHUB_URL_INVALID","请提供公开 GitHub 仓库、Skill 目录或 SKILL.md 链接。");}
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    private String read(Target t,String sha,String file){
        String path=Arrays.stream(file.split("/")).map(GithubSkills::encode).reduce((a,b)->a+"/"+b).orElseThrow();
        JsonNode node=github.get("/repos/"+t.owner()+"/"+t.repo()+"/contents/"+path+"?ref="+sha);
        if(!node.path("type").asString().equals("file")||!node.path("encoding").asString().equals("base64")||node.path("size").asInt(100000)>24000)throw new ApiException(422,"SKILL_FILE_INVALID","需要有效的 Markdown 文件，单份不超过 24 KB。");
        try{return new String(Base64.getMimeDecoder().decode(node.path("content").asString()),StandardCharsets.UTF_8);}catch(Exception e){throw new ApiException(422,"SKILL_FILE_INVALID","Skill 文件编码无效。");}
    }
    public Installed install(String owner,String url){
        Target target=parse(url);String base="/repos/"+target.owner()+"/"+target.repo();String ref=target.ref();
        if(ref.isEmpty())ref=github.get(base).path("default_branch").asString();
        String sha=github.get(base+"/commits/"+encode(ref)).path("sha").asString();if(!sha.matches("[a-fA-F0-9]{40,64}"))throw new ApiException(422,"GITHUB_REVISION_INVALID","未取得可追溯的仓库版本。");
        String main=read(target,sha,target.file());if(main.isBlank())throw new ApiException(422,"SKILL_FILE_INVALID","SKILL.md 为空。");
        StringBuilder instructions=new StringBuilder(main);var links=Pattern.compile("\\[[^\\]]*\\]\\(([^):#]+\\.md)\\)").matcher(main);Set<String> loaded=new HashSet<>();
        while(links.find()&&loaded.size()<3){String link=links.group(1);var root=new ArrayList<>(Arrays.asList(target.file().split("/")));root.remove(root.size()-1);boolean valid=true;for(String part:link.split("/")){if(part.equals("."))continue;if(part.equals("..")){if(root.isEmpty()){valid=false;break;}root.remove(root.size()-1);}else if(part.matches("[\\p{L}\\p{N}_. @()+-]+"))root.add(part);else{valid=false;break;}}if(!valid)throw new ApiException(422,"SKILL_REFERENCE_INVALID","Skill 引用路径无效。");String file=String.join("/",root);if(loaded.add(file))instructions.append("\n\n参考资料 ").append(file).append("：\n").append(read(target,sha,file));}
        if(instructions.length()>12000)throw new ApiException(422,"SKILL_INSTRUCTIONS_LIMIT","Skill 指令及引用超过 12000 字符，尚未安装。请精简后再导入。");
        var nameMatch=Pattern.compile("(?m)^name:\\s*[\"']?([^\\r\\n\"']+)").matcher(main);String name=nameMatch.find()?nameMatch.group(1).trim():target.repo();if(name.length()>80)name=name.substring(0,80);
        String source="https://github.com/"+target.owner()+"/"+target.repo()+"/blob/"+sha+"/"+target.file();String hash=Documents.hash(instructions.toString().getBytes(StandardCharsets.UTF_8));String key=Documents.hash((source+"|"+hash).getBytes(StandardCharsets.UTF_8));String finalName=name;
        return tx.execute(status->{jdbc.queryForObject("SELECT id FROM orbit_user WHERE id=? FOR UPDATE",String.class,owner);var previous=jdbc.query("SELECT skill_id FROM github_skill_import WHERE owner_id=? AND source_key=?",(rs,row)->rs.getString(1),owner,key);
            if(!previous.isEmpty()){var found=skills.list(owner).stream().filter(s->s.id().equals(previous.get(0))).findFirst();if(found.isPresent())return new Installed(found.get(),source,sha,hash,true,"markdown_instructions");}
            var skill=skills.create(owner,new Skills.Input(finalName,"GitHub："+target.owner()+"/"+target.repo()+" · "+sha.substring(0,8),instructions.toString(),true,0));
            if(previous.isEmpty())jdbc.update("INSERT INTO github_skill_import VALUES(?,?,?,?,?,?,?)",owner,key,source,sha,hash,skill.id(),Instant.now().toString());else jdbc.update("UPDATE github_skill_import SET skill_id=?,created_at=? WHERE owner_id=? AND source_key=?",skill.id(),Instant.now().toString(),owner,key);
            return new Installed(skill,source,sha,hash,false,"markdown_instructions");});
    }
}

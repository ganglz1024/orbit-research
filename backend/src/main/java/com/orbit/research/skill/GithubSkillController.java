package com.orbit.research.skill;
import com.orbit.research.auth.Accounts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/skills/import")
public class GithubSkillController {
    public record Input(@NotBlank @Size(max=1000) String url){}
    private final Accounts accounts;private final GithubSkills github;
    public GithubSkillController(Accounts accounts,GithubSkills github){this.accounts=accounts;this.github=github;}
    @PostMapping("/github") public GithubSkills.Installed install(Authentication auth,@Valid @RequestBody Input input){return github.install(accounts.owner(auth),input.url());}
}

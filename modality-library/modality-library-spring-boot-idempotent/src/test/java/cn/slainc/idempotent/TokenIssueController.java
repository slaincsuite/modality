package cn.slainc.idempotent;

import cn.slainc.core.result.Result;
import cn.slainc.idempotent.token.IdempotentToken;
import cn.slainc.idempotent.token.IdempotentTokenRequest;
import cn.slainc.idempotent.token.IdempotentTokenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TokenIssueController {

    private final IdempotentTokenService tokenService;

    TokenIssueController(IdempotentTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/idempotent/token")
    Result<IdempotentToken> issue(@RequestBody(required = false) IdempotentTokenRequest request) {
        return Result.ok(tokenService.issue(request == null ? null : request.ttlSeconds()));
    }
}

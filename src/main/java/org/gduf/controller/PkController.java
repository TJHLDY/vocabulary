package org.gduf.controller;

import org.gduf.common.R;
import org.gduf.security.UserContext;
import org.gduf.service.PkService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pk")
public class PkController {

    private final PkService pkService;

    public PkController(PkService pkService) {
        this.pkService = pkService;
    }

    @GetMapping("/arena/users")
    public R arenaUsers() {
        try {
            Long userId = UserContext.getUserId();
            return R.ok(pkService.getArenaUsers(userId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/settings")
    public R settings() {
        try {
            Long userId = UserContext.getUserId();
            return R.ok(pkService.getSettings(userId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @PutMapping("/settings/invisible")
    public R setInvisible(@RequestBody Map<String, Object> body) {
        try {
            Long userId = UserContext.getUserId();
            boolean invisible = Boolean.TRUE.equals(body.get("invisible"));
            pkService.setInvisible(userId, invisible);
            return R.ok(null);
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @PutMapping("/settings/declarations")
    public R updateDeclarations(@RequestBody Map<String, Object> body) {
        try {
            Long userId = UserContext.getUserId();
            pkService.updateDeclarations(userId, body);
            return R.ok(null);
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/matches/recent")
    public R recentMatches() {
        try {
            Long userId = UserContext.getUserId();
            return R.ok(pkService.getRecentMatches(userId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/matches/replay")
    public R replay(@RequestParam Long matchId) {
        try {
            Long userId = UserContext.getUserId();
            return R.ok(pkService.getReplay(userId, matchId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/leaderboard")
    public R leaderboard(@RequestParam(defaultValue = "day") String scope) {
        try {
            return R.ok(pkService.getLeaderboard(scope));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/me")
    public R me() {
        try {
            Long userId = UserContext.getUserId();
            return R.ok(pkService.getMyStats(userId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }
}

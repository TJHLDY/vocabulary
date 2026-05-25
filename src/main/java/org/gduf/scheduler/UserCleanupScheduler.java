package org.gduf.scheduler;

import org.gduf.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserCleanupScheduler.class);
    private final UserService userService;

    public UserCleanupScheduler(UserService userService) {
        this.userService = userService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanExpiredDisabledUsers() {
        int deleted = userService.deleteDisabledExpired(3);
        if (deleted > 0) {
            log.info("清理停用超3天用户: {} 个", deleted);
        }
    }
}

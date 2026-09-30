package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.log.LoginRecordService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/upms/login-record")
public class LoginRecordController {

    private final LoginRecordService loginRecordService;

    public LoginRecordController(LoginRecordService loginRecordService) {
        this.loginRecordService = loginRecordService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_LOG_QUERY)
    public Object page(@RequestParam(required = false) Long userId, @RequestParam(required = false) String username,
                       @RequestParam(required = false) String loginType, @RequestParam(required = false) String clientIp,
                       @RequestParam(required = false) Boolean succeeded, @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to, @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return loginRecordService.search(userId, username, loginType, clientIp, succeeded, from, to, current, size);
    }

    @GetMapping("/top-ips")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_LOG_QUERY)
    public List<Map<String, Object>> topIps(@RequestParam(defaultValue = "10") int limit) {
        return loginRecordService.topIps(limit);
    }

    @GetMapping("/top-devices")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_LOG_QUERY)
    public List<Map<String, Object>> topDevices(@RequestParam(defaultValue = "10") int limit) {
        return loginRecordService.topDevices(limit);
    }
}

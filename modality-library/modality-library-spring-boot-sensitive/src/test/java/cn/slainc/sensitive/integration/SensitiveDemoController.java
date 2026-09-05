package cn.slainc.sensitive.integration;

import cn.slainc.sensitive.demo.CycleNode;
import cn.slainc.sensitive.demo.OrgTree;
import cn.slainc.sensitive.demo.UserProfile;
import cn.slainc.sensitive.xss.XssSanitizer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/sensitive")
public class SensitiveDemoController {

    public static final UserProfile PROFILE = new UserProfile();

    public static final OrgTree TREE = new OrgTree();

    public static final CycleNode SELF_CYCLE = CycleNode.selfCycle();

    public static final CycleNode MUTUAL_CYCLE = CycleNode.mutualCycle();

    private final XssSanitizer xssSanitizer;

    public SensitiveDemoController(XssSanitizer xssSanitizer) {
        this.xssSanitizer = xssSanitizer;
    }

    @GetMapping("/profile")
    public UserProfile profile() {
        return PROFILE;
    }

    @GetMapping("/tree")
    public OrgTree tree() {
        return TREE;
    }

    @GetMapping("/cycle")
    public CycleNode cycle() {
        return SELF_CYCLE;
    }

    @GetMapping("/mutual")
    public CycleNode mutual() {
        return MUTUAL_CYCLE;
    }

    @GetMapping("/xss")
    public Map<String, Object> xss() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cleaned", xssSanitizer.clean("正常文本<script>alert('xss')</script>"));
        data.put("rich", xssSanitizer.cleanRich("<p onclick=\"steal()\">段落</p><script>alert(1)</script>"));
        data.put("escaped", xssSanitizer.escape("<script>alert(1)</script>"));
        return data;
    }
}

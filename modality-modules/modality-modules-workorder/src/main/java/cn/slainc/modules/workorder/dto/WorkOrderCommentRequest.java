package cn.slainc.modules.workorder.dto;


public record WorkOrderCommentRequest(String content, String visibility, Long parentId,
                                      Long quotedCommentId) {
}

package cn.slainc.services.livemonitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiveStreamRequest(
        @NotBlank(message = "直播源名称不能为空")
        @Size(max = 128, message = "直播源名称长度不能超过128")
        String name,

        @NotBlank(message = "直播源地址不能为空")
        @Size(max = 1000, message = "直播源地址长度不能超过1000")
        String url,

        Boolean enabled) {
}

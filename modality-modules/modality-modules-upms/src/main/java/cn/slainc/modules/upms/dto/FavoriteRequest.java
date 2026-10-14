package cn.slainc.modules.upms.dto;

public record FavoriteRequest(String targetType, String targetKey, String name, String path, String icon,
                              Integer sort) {
}

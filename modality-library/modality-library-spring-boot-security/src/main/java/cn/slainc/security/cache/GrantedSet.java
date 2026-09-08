package cn.slainc.security.cache;

import java.util.List;

public record GrantedSet(List<String> permissions, List<String> roles) {

    public static final GrantedSet EMPTY = new GrantedSet(List.of(), List.of());
}

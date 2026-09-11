package cn.slainc.session.query;

import cn.slainc.core.enums.LoginType;

import java.util.ArrayList;
import java.util.List;

public record RawScanOutcome(LoginType type, List<RawTerminal> terminals, boolean truncated, String message,
                             int scanned) {

    public RawScanOutcome {
        terminals = terminals == null ? new ArrayList<>() : new ArrayList<>(terminals);
    }
}

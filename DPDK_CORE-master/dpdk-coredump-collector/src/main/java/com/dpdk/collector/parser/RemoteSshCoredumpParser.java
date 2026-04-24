package com.dpdk.collector.parser;

import com.dpdk.collector.util.GdbParserUtil;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class RemoteSshCoredumpParser implements CoredumpParser {
    @Override
    public ParseBackend backend() {
        return ParseBackend.REMOTE_SSH;
    }

    @Override
    public GdbParserUtil.GdbParseResult parse(File coreFile, String programPath) {
        throw new UnsupportedOperationException("REMOTE_SSH 解析尚未接入：后续可通过 SSH 在 Linux 虚拟机执行 gdb 并回传结果");
    }
}


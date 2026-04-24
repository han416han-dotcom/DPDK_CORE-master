package com.dpdk.collector.parser;

import com.dpdk.collector.util.GdbParserUtil;
import com.dpdk.collector.util.WslGdbParserUtil;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class Wsl2GdbCoredumpParser implements CoredumpParser {
    @Override
    public ParseBackend backend() {
        return ParseBackend.WSL2_GDB;
    }

    @Override
    public GdbParserUtil.GdbParseResult parse(File coreFile, String programPath) throws Exception {
        return WslGdbParserUtil.parseCoredumpViaWsl(coreFile, programPath);
    }
}


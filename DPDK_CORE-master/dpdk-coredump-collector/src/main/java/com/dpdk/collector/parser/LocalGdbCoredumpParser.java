package com.dpdk.collector.parser;

import com.dpdk.collector.util.GdbParserUtil;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class LocalGdbCoredumpParser implements CoredumpParser {
    @Override
    public ParseBackend backend() {
        return ParseBackend.LOCAL_GDB;
    }

    @Override
    public GdbParserUtil.GdbParseResult parse(File coreFile, String programPath) throws Exception {
        return GdbParserUtil.parseCoredump(coreFile, programPath);
    }
}


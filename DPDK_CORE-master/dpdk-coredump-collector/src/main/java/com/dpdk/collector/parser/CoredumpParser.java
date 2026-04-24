package com.dpdk.collector.parser;

import com.dpdk.collector.util.GdbParserUtil;

import java.io.File;

public interface CoredumpParser {
    ParseBackend backend();

    GdbParserUtil.GdbParseResult parse(File coreFile, String programPath) throws Exception;
}


package com.dpdk.collector.util;

public class WslPathUtil {
    private WslPathUtil() {
    }

    /**
     * Convert a Windows absolute path like "D:\a\b\c" into WSL mount path "/mnt/d/a/b/c".
     */
    public static String windowsToWslMountPath(String windowsPath) {
        if (windowsPath == null || windowsPath.isBlank()) {
            throw new IllegalArgumentException("windowsPath 不能为空");
        }
        String p = windowsPath.trim();
        if (p.length() < 3 || p.charAt(1) != ':' || (p.charAt(2) != '\\' && p.charAt(2) != '/')) {
            throw new IllegalArgumentException("不是 Windows 绝对路径: " + windowsPath);
        }
        char drive = Character.toLowerCase(p.charAt(0));
        String rest = p.substring(2).replace('\\', '/');
        return "/mnt/" + drive + rest;
    }
}


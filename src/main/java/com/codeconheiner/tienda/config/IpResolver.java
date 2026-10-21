package com.codeconheiner.tienda.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IpResolver {

    private static final Pattern FORWARDED_FOR = Pattern.compile("for=\"?([0-9a-fA-F:.]+)");

    private IpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            int comma = xForwardedFor.indexOf(',');
            String first = (comma > 0 ? xForwardedFor.substring(0, comma) : xForwardedFor).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            Matcher matcher = FORWARDED_FOR.matcher(forwarded);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return request.getRemoteAddr();
    }
}

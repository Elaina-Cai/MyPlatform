package org.example.myplatform.utils;

import org.springframework.stereotype.Component;


/**
 * XSS 防御工具类
 * 用于转义 HTML 特殊字符，防止 XSS 攻击
 */
@Component
public class XssUtil {

    private XssUtil() {}

    public static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#x27;")
                    .replace("/", "&#x2F;");
    }
}
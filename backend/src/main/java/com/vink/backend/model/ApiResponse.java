package com.vink.backend.model;

import java.util.HashMap;
import java.util.Map;

/** Generic envelope matching the {success, data, error, message, meta} JSON shape used throughout the Express API. */
public class ApiResponse<T> {
    public boolean success;
    public T data;
    public String error;
    public String message;
    public Map<String, Object> meta;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(T data, Map<String, Object> meta) {
        ApiResponse<T> r = ok(data);
        r.meta = meta;
        return r;
    }

    public static <T> ApiResponse<T> fail(String error) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.error = error;
        return r;
    }

    public static <T> ApiResponse<T> msg(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        return r;
    }

    public static Map<String, Object> meta(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}

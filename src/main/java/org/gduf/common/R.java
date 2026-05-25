package org.gduf.common;

import lombok.Data;

@Data
public class R {
    private int code;
    private String msg;
    private Object data;

    public static R ok(Object data) {
        R r = new R();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static R fail(String msg) {
        return fail(500, msg);
    }

    public static R fail(int code, String msg) {
        R r = new R();
        r.code = code;
        r.msg = msg;
        r.data = null;
        return r;
    }
}

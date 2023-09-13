package net.shihome.nt.comm.utils;

import org.springframework.util.IdGenerator;

import javax.annotation.Resource;

public class IdUtil {

    private static IdUtil INSTANCE;
    @Resource
    private IdGenerator idGenerator;

    protected IdUtil() {
        INSTANCE = this;
    }

    public static String getNextId() {
        return INSTANCE.idGenerator.generateId().toString();
    }
}

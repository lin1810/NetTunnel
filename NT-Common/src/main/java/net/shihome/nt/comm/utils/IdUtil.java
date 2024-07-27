package net.shihome.nt.comm.utils;

import jakarta.annotation.Resource;
import org.springframework.util.IdGenerator;


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

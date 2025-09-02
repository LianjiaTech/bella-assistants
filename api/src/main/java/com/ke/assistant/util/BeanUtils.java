package com.ke.assistant.util;

import java.lang.reflect.Field;

/**
 * Bean 工具类
 */
public class BeanUtils {

    /**
     * 拷贝非空字段从源对象到目标对象
     *
     * @param source 源对象
     * @param target 目标对象
     * @param <T>    对象类型
     */
    public static <T> void copyNonNullProperties(T source, T target) {
        if(source == null || target == null) {
            return;
        }

        Class<?> clazz = source.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(source);
                if(value != null) {
                    field.set(target, value);
                }
            } catch (IllegalAccessException e) {
                // 忽略无法访问的字段
            }
        }
    }

    /**
     * 拷贝所有属性从源对象到目标对象 直接调用Spring的BeanUtils.copyProperties方法
     *
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyProperties(Object source, Object target) {
        org.springframework.beans.BeanUtils.copyProperties(source, target);
    }

}

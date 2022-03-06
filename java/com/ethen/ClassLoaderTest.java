package com.ethen;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * @author ethenyang@126.com
 * @since 2022/03/06
 */
public class ClassLoaderTest {
    public static void main(String[] args) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        System.err.println(classLoader);
        ClassLoader extClassLoader = classLoader.getParent();
        System.err.println(classLoader.getParent());
        System.err.println(classLoader.getParent().getParent());

        if (classLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
            for (URL url : urlClassLoader.getURLs()) {
                System.out.println(url);
            }
            System.out.println("-------------------------------------");
        }
        if (extClassLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) extClassLoader;
            for (URL url : urlClassLoader.getURLs()) {
                System.out.println(url);
            }
            System.out.println("-------------------------------------");
        }
    }
}

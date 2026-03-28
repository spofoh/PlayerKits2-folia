package pk.ajneb97.versions;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public class Version {
    private ConcurrentHashMap<String,Class<?>> classes;
    private ConcurrentHashMap<String, Method> methods;

    public Version(){
        this.classes = new ConcurrentHashMap<>();
        this.methods = new ConcurrentHashMap<>();
    }

    public void addClass(String name,Class<?> classType){
        classes.put(name,classType);
    }

    public void addMethod(String name,Method methodType){
        methods.put(name,methodType);
    }

    public Class<?> getClassRef(String name){
        return classes.get(name);
    }

    public Method getMethodRef(String name){
        return methods.get(name);
    }
}

package org.juke.remix.service;

import java.util.HashMap;

public class TestData {
    public static String greeting1="{\n" +
            "  \"id\" : 1,\n" +
            "  \"content\" : \"Hello, test!\"\n" +
            "}";
    public static String greeting2="{\n" +
            "  \"id\" : 1,\n" +
            "  \"content\" : \"Hello, World!\"\n" +
            "}";
    public static String greeting3="{\n" +
            "  \"id\" : 1,\n" +
            "  \"content\" : \"Hello, cruel world!\"\n" +
            "}";
    public static String juke1="{\"com.example.IGreetingsService\":{\"className\":\"com.example.IGreetingsService\",\"methods\":[{\"method\":\"greeting\",\"inputParameters\":[{\"className\":\"java.lang.String\",\"isParameterized\":false,\"isArray\":false,\"type\":null,\"list\":[],\"array\":false,\"parameterized\":false}],\"outputResult\":{\"className\":\"com.example.Greeting\",\"isParameterized\":false,\"isArray\":false,\"type\":{\"rawType\":\"com.example.Greeting\",\"actualTypeArguments\":[],\"array\":false,\"parameterized\":false},\"list\":[],\"array\":false,\"parameterized\":false}}]}}";
    public static HashMap<String, String> map= null;
    static{
        map=new HashMap<String, String>();
        map.put("com.example.IGreetingsService.$greeting.1.json",greeting1);
        map.put("com.example.IGreetingsService.$greeting.2.json",greeting2);
        map.put("com.example.IGreetingsService.$greeting.3.json",greeting3);
        map.put("juke.json",juke1);


    }


}

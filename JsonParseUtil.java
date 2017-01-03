package com.mogu.international.spider.util;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Created by mx on 16/5/9. JSON 解析匹配工具
 */
public class JsonParseUtil {

    private static final Logger logger = LoggerFactory.getLogger(JsonParseUtil.class.getCanonicalName());

    private JsonParseUtil() {
    }

    /**
     * JSON expresson 表达式转化为MAP
     *
     * @param expression
     * @return map
     */
    public static Map<String, String> expressionParseToMap(String expression) {

        Map<String, String> resultMap = Maps.newLinkedHashMap();
        // 验证expression 中是否存在 ">"
        if (!RegexUtil.isMatch("(?:arrays|object|text):[\\w-]+((\\s+)?([^>]))+(?:arrays|object|text):[\\w-]+", expression)) {
            String expressions[] = expression.split(">");
            for (int i = 0; i < expressions.length; i++) {
                // 类型:arrays|object|text
                String key = RegexUtil.regexMatch("(.*):", expressions[i]).trim();
                // 配置的JSON字段
                String value = RegexUtil.regexMatch(":(.*)", expressions[i]).trim();
                key = key + ":" + value;
                if (!resultMap.containsKey(key)) {
                    resultMap.put(key, value);
                } else
                    resultMap.put(key + ":" + i, value);
            }
        }

        return resultMap;
    }

    /**
     * JSON 解析工具
     *
     * @param expression 表达式
     * @param jsonstr
     * @return text
     */
    public static String parseHtmlJson(String expression, String jsonstr) {

        StringBuilder stringBuilder = new StringBuilder();
        Object sourceObj = JSON.parse(jsonstr);
        String[] expressions = expression.split(",");
        int length = expressions.length;

        try {
            for (String expre : expressions) {
                _extractValue(expre, stringBuilder, sourceObj);
                if (length-- > 1) {
                    stringBuilder.append("<br>");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error("JSON-HTML PARSE EXCEPTION:", ex);
            return "";
        }

        return stringBuilder.toString();
    }

    private static void _extractValue(String expression, StringBuilder stringBuilder, Object sourceObj){

        Map<String, String> maps = JsonParseUtil.expressionParseToMap(expression);
        List<JSONObject> resultJsonObj = Lists.newArrayList();

        Object obj = null;
        JSONArray jsonArray = null;

        Iterator<String> iterator = maps.keySet().iterator();

        while (iterator.hasNext()) {
            String key = iterator.next();
            String field = maps.get(key);
            key = key.split(":")[0];

            // 初始化
            if (obj == null) {
                String isArrays = sourceObj instanceof JSONArray ? "arrays" : "object";
                switch (key) {
                    case "arrays":
                        obj = sourceObj;
                        if (isArrays.contains("arrays")) {
                            jsonArray = (JSONArray) sourceObj;
                            for (int i = 0; i < jsonArray.size(); i++) {
                                resultJsonObj.add(jsonArray.getJSONObject(i));
                            }
                        } else
                            resultJsonObj.add((JSONObject) sourceObj);
                        break;
                    case "object":
                        obj = sourceObj;
                        if (isArrays.contains("arrays")) {
                            jsonArray = (JSONArray) sourceObj;
                            for (int i = 0; i < jsonArray.size(); i++) {
                                resultJsonObj.add(jsonArray.getJSONObject(i));
                            }
                        } else
                            resultJsonObj.add((JSONObject) sourceObj);
                        break;
                    case "text":
                        obj = sourceObj;
                        if (isArrays.contains("arrays")) {
                            jsonArray = (JSONArray) sourceObj;
                            for (int i = 0; i < jsonArray.size(); i++) {
                                JSONObject jsonObject = jsonArray.getJSONObject(i);
                                resultJsonObj.add(jsonObject);
                            }
                        } else
                            resultJsonObj.add((JSONObject) sourceObj);
                        break;
                }
            }

            // 解析
            switch (key) {
                case "arrays":
                    List<JSONObject> defJsonsObj = Lists.newLinkedList();
                    for (JSONObject resultobj : resultJsonObj) {
                        if (!resultobj.containsKey(field)) {
                            throw new IllegalArgumentException("Json parse Key [" + field + "] not exists!!!");
                        }

                        // json 字符数组
                        JSONArray defstrs = new JSONArray();

                        JSONArray defArrays = resultobj.getJSONArray(field);
                        for (int i = 0; i < defArrays.size(); i++) {
                            Object objs = defArrays.get(i);
                            if (objs instanceof JSONObject) {
                                defJsonsObj.add(defArrays.getJSONObject(i));
                            } else
                                defstrs.add(objs.toString());
                        }

                        // 对应str:str ,设置
                        jsonArray = defstrs;
                    }

                    resultJsonObj.clear();
                    for (JSONObject json : defJsonsObj) {
                        resultJsonObj.add(json);
                    }
                    break;
                case "object":
                    List<JSONObject> defTypeObj = Lists.newLinkedList();
                    for (JSONObject typeobj : resultJsonObj) {
                        if (!typeobj.containsKey(field)) {
                            throw new IllegalArgumentException("Json parse Key [" + field + "] not exists!!!");
                        }
                        defTypeObj.add(typeobj.getJSONObject(field));
                    }

                    resultJsonObj.clear();
                    for (JSONObject json : defTypeObj) {
                        resultJsonObj.add(json);
                    }
                    break;
                case "text":
                    for (int i = 0; i < resultJsonObj.size(); i++) {
                        JSONObject textObj = resultJsonObj.get(i);
                        if (!textObj.containsKey(field)) {
                            throw new IllegalArgumentException("Json parse Key [" + field + "] not exists!!!");
                        }

                        if (i == resultJsonObj.size() - 1) {
                            stringBuilder.append(textObj.get(field) + "");
                        } else
                            stringBuilder.append(textObj.get(field) + "").append("<br>");
                    }
                    break;
                case "str":// ...> str:str
                    if (jsonArray != null) {
                        for (int i = 0; i < jsonArray.size(); i++) {
                            if (i == jsonArray.size() - 1) {
                                stringBuilder.append(jsonArray.get(i) + "");
                            } else
                                stringBuilder.append(jsonArray.get(i) + "").append("<br>");
                        }
                    }
                    break;
            }
        }

    }
}

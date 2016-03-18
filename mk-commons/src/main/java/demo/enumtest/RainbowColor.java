package demo.enumtest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by mx on 16/2/18.
 */
public class RainbowColor {
    // 红橙黄绿青蓝紫七种颜色的常量定义
    public static final int RED = 0;
    public static final int ORANGE = 1;
    public static final int YELLOW = 2;
    public static final int GREEN = 3;
    public static final int CYAN = 4;
    public static final int BLUE = 5;
    public static final int PURPLE = 6;

    private RainbowColor() {

    }

    public static String[] values() {
        Map<Integer, String> map = new HashMap<Integer, String>();
        map.put(0, "RED");
        map.put(1, "ORANGE");
        map.put(2, "YELLOW");
        map.put(3, "GREEN");
        map.put(4, "CYAN");
        map.put(5, "BLUE");
        map.put(6, "RPURPLEED");

        String a[] = new String[map.size()];
        for (int i = 0; i < map.size(); i++) {
            a[i] = map.values().iterator().next();
        }

        return a;
    }

    public static void main(String[] args) {
        System.out.println(RainbowColor.values()[0]);
    }
}

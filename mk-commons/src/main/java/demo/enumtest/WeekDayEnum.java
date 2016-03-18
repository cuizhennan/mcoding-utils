package demo.enumtest;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Created by mx on 16/2/18.
 */
public enum WeekDayEnum {
    Mon, Tue, Wed, Thu, Fri, Sat, Sun;


    public static void main(String[] args) throws Exception {
        for (WeekDayEnum day : EnumSet.range(WeekDayEnum.Mon, WeekDayEnum.Wed)) {
            System.out.println(day);
        }

        System.out.println("========================");

        EnumSet<WeekDayEnum> subset = EnumSet.of(WeekDayEnum.Mon);
        for (WeekDayEnum day : subset) {
            System.out.println(day);
        }

        Map<WeekDayEnum, String> scheme = new EnumMap<WeekDayEnum, String>(WeekDayEnum.class);
        for (int i = 0; i < WeekDayEnum.values().length; i++) {
            scheme.put(WeekDayEnum.values()[i], RainbowColor.values()[i]);
        }

        System.out.println("========================");
        System.out.println(scheme.get(WeekDayEnum.Fri));
    }
}

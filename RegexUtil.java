mport org.apache.commons.lang.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mx on 16/2/25.
 */
public class RegexUtil {
    private RegexUtil() {
    }

    /**
     * 默认返回group为1的值
     *
     * @param regexp
     * @param sourcestr
     * @return
     */
    public static String regexMatch(String regexp, String sourcestr) {
        regexp = StringUtils.trimToNull(regexp);
        if (regexp != null) {
            Pattern pattern = Pattern.compile(regexp);
            Matcher matcher = pattern.matcher(sourcestr);
            while (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "";
    }

    /**
     * 返回特定groupId 的值
     *
     * @param regexp
     * @param sourcestr
     * @param groupId
     * @return
     */
    public static String regexMatchByGroup(String regexp, String sourcestr, int groupId) {
        regexp = StringUtils.trimToNull(regexp);
        if (regexp != null) {
            Pattern pattern = Pattern.compile(regexp);
            Matcher matcher = pattern.matcher(sourcestr);
            while (matcher.find()) {
                return matcher.group(groupId);
            }
        }

        return "";
    }

    /**
     * 正则匹配工具
     *
     * @param regexp
     * @param sourcestr
     * @return
     */
    public static boolean isMatch(String regexp, String sourcestr) {
        regexp = StringUtils.trimToNull(regexp);
        if (regexp == null) {
            return false;
        }

        Pattern pattern = Pattern.compile(regexp);
        Matcher matcher = pattern.matcher(sourcestr);
        while (matcher.find()) {
            return true;
        }

        return false;
    }

    /**
     * 正则匹配替换指定字符串
     *
     * @param regex
     * @param source
     * @param newstr
     * @return
     */
    public static String regexMatchReplaceAll(String regex, String source, String newstr) {
        regex = StringUtils.trimToNull(regex);
        if (regex != null) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(source);

            StringBuffer stringBuffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(stringBuffer, newstr);
            }
            matcher.appendTail(stringBuffer);

            return stringBuffer.toString();
        }

        return "";
    }

    /**
     * 从正则匹配的字符串中替换指定的字符
     *
     * @param regex
     * @param source
     * @param oldstr
     * @param newstr
     * @return
     */
    public static String regexMatchReplace(String regex, String source, String oldstr, String newstr) {
        regex = StringUtils.trimToNull(regex);
        if (regex != null) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(source);

            StringBuffer stringBuffer = new StringBuffer();
            while (matcher.find()) {
                String subsource = matcher.group(1);
                subsource = subsource.replace(oldstr, newstr);
                matcher.appendReplacement(stringBuffer, subsource);
            }
            matcher.appendTail(stringBuffer);

            return stringBuffer.toString();
        }

        return "";
    }

//    public static void main(String[] args) {
//        String source = "div > div.asdf,div > table.content:not(DIV > div.rm;div .Tb-ssT),DIV .span:not(tr;div #td-st;span), #asdF";
//        System.out.println(RegexUtil.regexMatchReplace("(:not\\((((#|.)\\w+(,)?)?){1,}\\))", source, ",", ";"));
//    }
}


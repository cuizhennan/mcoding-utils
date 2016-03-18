package htmlunit;

import core.UrlList;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mx on 16/3/14.
 */
public class DoubanUrlList implements UrlList<String> {

    @Override
    public List<String> returnUrlList() {
        // TODO Auto-generated method stub
        List<String> urlList = new ArrayList<String>();
        urlList.add("http://people.ladymax.cn/201505/28-27402.html");
        return urlList;
    }
}

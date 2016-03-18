package htmlunit;

import core.Processor;
import core.Saver;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

/**
 * Created by mx on 16/3/14.
 */
public class DoubanProcessor implements Processor<String> {

    @Override
    public void process(List<String> pages, Saver saver) {
        // 将返回的每一个网页中的电影名称和链接，提取出来，使用jsoup
        // 最后使用saver做数据持久化
        for (String each : pages) {
            Document doc = Jsoup.parse(each);
            System.out.println(doc);
//            Element ele = doc.body();
//            Elements es = ele.select("div#wrapper").select("div#content")
//                    .select("div.clearfix").select("div.article")
//                    .select("div.movie-list").select("dl");
//            for (int i = 0; i < es.size(); i++) {
//                saver.save("href", es.get(i).select("dt").select("a").attr("href"));
//                saver.save("title", es.get(i).select("dd").select("a").text());
//            }
        }
    }
}

package htmlunit;

import core.Spider;
import extension.DefaultDownloader;
import extension.PrintSaver;

/**
 * Created by mx on 16/3/14.
 */
public class JLiteSpider {
    private static final String AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_2) "
            + "AppleWebKit/537.31 (KHTML, like Gecko) Chrome/26.0.1410.65 Safari/537.31";

    public static void main(String[] args) {
        Spider.create().setUrlList(new DoubanUrlList())     //组装UrlList
                .setDownloader(new DefaultDownloader() //组装下载器
                        .setThreadPoolSize(1)
                        .setUserAgent(AGENT))
                .setProcessor(new DoubanProcessor())   //组装解析器
                .setSaver(new PrintSaver())            //组装数据持久化方法
                .begin();
    }
}

package htmlunit;

/**
 * Created by mx on 16/3/13.
 */
public class CrawlerScheduler {
    public static void main(String[] args) {
        String url = "http://people.ladymax.cn/201505/28-27402.html";
        String cmd = "click";
        String param = "yohoboys";
        String referer = "http://people.ladymax.cn/201505/28-27402.html";

        if (args.length > 0) {
            url = String.valueOf(args[0]);
        }
        if (args.length > 1) {
            cmd = String.valueOf(args[1]);
        }
        if (args.length > 2) {
            param = String.valueOf(args[2]);
        }
        if (args.length > 3) {
            referer = String.valueOf(args[3]);
        }

        FasterCrawler worker = new FasterCrawler(url, cmd, param, referer);
        Thread thread = new Thread(worker);
        thread.start();
    }
}

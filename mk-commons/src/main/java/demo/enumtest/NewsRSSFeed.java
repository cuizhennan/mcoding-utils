package demo.enumtest;

/**
 * Created by mx on 16/2/18.
 * enum test
 */
public enum NewsRSSFeed {

    YAHOO_TOP_STORIES("http://rss.news.yahoo.com/rss/topstories"),

    LATIMES_TOP_STORIES("http://feeds.latimes.com/latimes/news?format=xml"),

    CBS_TOP_STORIES("http://feeds.cbsnews.com/CBSNewsMain?format=xml");

    private String rss_url;

    private NewsRSSFeed(String rss_url) {
        this.rss_url = rss_url;
    }

    public String getRssUrl() {
        return this.rss_url;
    }


    public static void main(String[] args) {
        System.out.println(NewsRSSFeed.CBS_TOP_STORIES.getRssUrl());
        System.out.println(NewsRSSFeed.CBS_TOP_STORIES);
    }
}

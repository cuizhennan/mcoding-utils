package htmlunit;

import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.io.IOException;

/**
 * Created by mx on 16/3/14.
 */
public class HttpUnitDemo {
    public static void main(String[] args) throws IOException {
        final WebClient webClient = new WebClient();
        webClient.getOptions().setTimeout(1000 * 60);
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setActiveXNative(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());


        HtmlPage rootPage = webClient.getPage("http://www.cynthiarowley.com/new-arrivals.html/");

        webClient.waitForBackgroundJavaScript(5000);
        webClient.setJavaScriptTimeout(0);

        System.out.println(rootPage.asXml());
    }
}

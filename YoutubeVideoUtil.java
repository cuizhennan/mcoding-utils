package com.mogu.international.spider.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mogu.international.spider.common.exception.ScrapyException;
import com.mogu.international.spider.entity.YoutubeResultDto;
import com.mogu.international.spider.filehandler.downloader.VideoDownloader;
import com.mogu.international.spider.mapper.YoutubeVideoProcessMapper;
import com.mogu.international.spider.model.YoutubeVideoProcess;
import com.mogu.international.spider.service.impl.SmsComponent;
import com.mogujie.service.content.video.qcloud.util.UploadVideoUtil;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.CookieStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bajie on 16/6/30.
 */

@Component
public class YoutubeVideoUtil {

    private static final Logger logger = LoggerFactory.getLogger(YoutubeVideoUtil.class.getCanonicalName());

    @Value("${local.test}")
    private Boolean isLocalTest;

    @Value("${youtube.url}")
    private String domain;

    @Value("${youtube.request}")
    private String requestUrl;

    @Value("${youtube.upload.url}")
    private String uploadUrl;

    //三方网站720验证
    private static final String check_video_720p_text_regex = "\\bYouTube Video.*\\(720p\\).*";

    //获取youtube原网站json中的视频地址
    private static final String youtube_resource_check_regex = "\\bytplayer.config[\\s=]*(\\{.*\\});ytplayer.load";

    @Autowired
    private SmsComponent smsComponent;

    // 失败重试次数
    private final int VIDEO_HANDLE_FAIL_RETRY_TIMES = 3;

    @Autowired
    private VideoDownloader videoDownloader;

    @Autowired
    private YoutubeVideoProcessMapper youtubeVideoProcessMapper;

    /**
     * Youtube 视频下载
     *
     * @param srcUrl
     * @return
     * @throws Exception
     */
    public YoutubeResultDto processYoutubeVideo(String srcUrl) throws Exception {
        logger.info("download video start... {} ", srcUrl);
        YoutubeResultDto videoProcessResult = new YoutubeResultDto();

        //查询数据库 是否需要下载
        final YoutubeVideoProcess youtubeVideoProcess = buildYoutubeVideoProcessHandle(srcUrl);

        videoProcessResult.setPrimaryKey(youtubeVideoProcess.getId().intValue());
        videoProcessResult.setVideoId(youtubeVideoProcess.getVideoid());

        if (youtubeVideoProcess.getExistTask()) {
            return videoProcessResult;
        }

        srcUrl = UrlUtil.urlEncoder(srcUrl);
        final String temp = srcUrl;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //1.初始下载状态
                youtubeVideoProcess.setStatus((byte) 1);
                updateProcess(youtubeVideoProcess);

                String downloadUrl = null;
                try {
                    downloadUrl = getDownLoadUrl(temp, youtubeVideoProcess);
                } catch (Exception e) {
                    //failed code -1
                    logger.error("Youtube getDownLoadUrl error : ", e);

                    youtubeVideoProcess.setStatus((byte) -1);
                    updateProcess(youtubeVideoProcess);

                    String getRequest = uploadUrl + "key=" + youtubeVideoProcess.getId() + "&" + "videoId=" + "&" + "status=-1";
                    logger.info("Youtube exception response editor-platform: {}", getRequest);

                    smsComponent.doSend("视频下载失败:" + JSONObject.toJSONString(youtubeVideoProcess), "15910990537");

                    requestEditor(getRequest);

                    return;
                }

                //failed code -1
                if (StringUtils.isEmpty(downloadUrl)) {
                    logger.error("Youtube video Real downloadUrl EMPTY: {}", downloadUrl);

                    youtubeVideoProcess.setStatus((byte) -1);
                    updateProcess(youtubeVideoProcess);

                    String getRequest = uploadUrl + "key=" + youtubeVideoProcess.getId() + "&" + "videoId=" + "&" + "status=-1";
                    logger.error("Youtube response ediotr-platform getRequest: {}", getRequest);

                    smsComponent.doSend("视频下载失败-1:" + JSONObject.toJSONString(youtubeVideoProcess), "15910990537");

                    requestEditor(getRequest);

                    return;
                }

                logger.info("downloadUrl: {}", downloadUrl);
                youtubeVideoProcess.setStatus((byte) 2);
                updateProcess(youtubeVideoProcess);

                String resultPath = "";
                // 下载,失败后重试;
                int downloadTimes = 0;
                for (; downloadTimes < VIDEO_HANDLE_FAIL_RETRY_TIMES; ) {
                    try {
                        logger.info("Youtube try downloading for {} times... {}", downloadTimes + 1, downloadUrl);
                        resultPath = videoDownloader.doDownload(downloadUrl, youtubeVideoProcess);
                        logger.info("Youtube download finished to local path: {}", resultPath);

                        break;
                    } catch (ScrapyException e) {
                        logger.error("Youtube downloading {}, {}rd fail：", downloadUrl, downloadTimes + 1, e);
                        downloadTimes++;
                    }
                }

                //failed code -2
                if (downloadTimes == VIDEO_HANDLE_FAIL_RETRY_TIMES) {
                    logger.error("{} download has failed", downloadUrl);

                    youtubeVideoProcess.setStatus((byte) -2);
                    updateProcess(youtubeVideoProcess);

                    String getRequest = uploadUrl + "key=" + youtubeVideoProcess.getId() + "&" + "videoId=" + "&" + "status=-2";
                    logger.info("getRequest: {}", getRequest);

                    smsComponent.doSend("视频下载失败-2:" + JSONObject.toJSONString(youtubeVideoProcess), "15910990537");

                    requestEditor(getRequest);
                    return;
                }
                //download success
                else {
                    youtubeVideoProcess.setStatus((byte) 3);
                    updateProcess(youtubeVideoProcess);

                    String videoID = uploadVideo(resultPath, youtubeVideoProcess.getVideotype());

                    //failed code -3
                    if (StringUtil.isNullOrEmpty(videoID)) {
                        logger.error("uploadVideo fail");

                        youtubeVideoProcess.setStatus((byte) -3);
                        updateProcess(youtubeVideoProcess);

                        String getRequest = uploadUrl + "key=" + youtubeVideoProcess.getId() + "&" + "videoId=" + "&" + "status=-3";
                        logger.info("getRequest: {}", getRequest);

                        smsComponent.doSend("视频下载失败-3:" + JSONObject.toJSONString(youtubeVideoProcess), "15910990537");

                        requestEditor(getRequest);

                        return;
                    }

                    //rm local video file
                    deleteLocalFile(resultPath);

                    youtubeVideoProcess.setVideoid(videoID);
                    youtubeVideoProcess.setStatus((byte) 4);
                    updateProcess(youtubeVideoProcess);

                    if (StringUtils.isNotEmpty(videoID)) {
                        String getRequest = uploadUrl + "key=" + youtubeVideoProcess.getId() + "&" + "videoId=" + videoID + "&" + "status=1";
                        logger.info("getRequest: {}", getRequest);

                        try {
                            if (requestEditor(getRequest)) {
                                logger.info("getRequest: ok.");

                                youtubeVideoProcess.setStatus((byte) 5);
                                updateProcess(youtubeVideoProcess);

                                smsComponent.doSend("视频下载成功:" + JSONObject.toJSONString(youtubeVideoProcess), "15910990537");
                            }
                            //failed code -4
                            else {
                                logger.info("getRequest: failed.");

                                youtubeVideoProcess.setStatus((byte) -4);
                                updateProcess(youtubeVideoProcess);

                                smsComponent.doSend("视频下载失败-4:" + JSONObject.toJSONString(youtubeVideoProcess), "15910990537");

                                return;
                            }
                        } catch (Exception e) {
                            logger.error("getRequest error : ", e);

                            youtubeVideoProcess.setStatus((byte) -4);
                            updateProcess(youtubeVideoProcess);

                            smsComponent.doSend("视频下载失败:" + JSONObject.toJSONString(youtubeVideoProcess), "15910990537");

                            return;
                        }
                    }
                }
            }
        }).start();

        return videoProcessResult;
    }

    /**
     * 下载线程-获取视频的url链接
     *
     * @param srcUrl
     * @param youtubeVideoProcess
     * @return
     * @throws Exception
     */
    private String getDownLoadUrl(String srcUrl, YoutubeVideoProcess youtubeVideoProcess) throws Exception {
        try {
            //从第三方网站获取url转码后的视频地址
            String proxyVideoUrl = getYoutubeResourceVideoUrl(srcUrl, youtubeVideoProcess);
            if (StringUtils.isNotEmpty(proxyVideoUrl)) {
                return proxyVideoUrl;
            }
            //logger
            else logger.error("Youtube GET RESOURCE VIDEO URL FAILED [{}], {}", "clipconverter.cc", srcUrl);


            //从youtube原网站获取视频地址
            logger.info("Youtube GET RESOURCE WEBSITE VIDEO URL OF START [{}], {}", "youtube.com", srcUrl);
            String sourceVideoUrl = getYoutubeResouceSiteUrl(srcUrl, youtubeVideoProcess);

            if (StringUtils.isNotEmpty(sourceVideoUrl)) {
                return sourceVideoUrl;
            }
            logger.error("Youtube GET RESOURCE VIDEO URL FAILED [{}], {}", "clipconverter.cc", srcUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    /**
     * Youtube 原网站视频下载
     *
     * @param srcUrl
     * @param youtubeVideoProcess
     * @return
     */
    private String getYoutubeResouceSiteUrl(String srcUrl, YoutubeVideoProcess youtubeVideoProcess) {

        if (StringUtils.isEmpty(srcUrl)) {
            logger.error("Youtube GET RESOURCE VIDEO URL EMPTY [{}], {}", "youtube.com", srcUrl);
            return "";
        }

        try {
            String youtubeHtml = HttpClientUtil.get(srcUrl);

            String soueceJson = RegexUtil.regexMatch(youtube_resource_check_regex, youtubeHtml);
            if (StringUtils.isNotEmpty(soueceJson)) {
                JSONObject jsonObject = JSONObject.parseObject(soueceJson).getJSONObject("args");

                String videoUrl = jsonObject.getString("url_encoded_fmt_stream_map");

                String title = jsonObject.getString("title");

                String cutUrl = parse_qs(videoUrl);
                if (StringUtils.isNotEmpty(videoUrl)) {
                    cutUrl = cutUrl + "&title=" + title;

                    youtubeVideoProcess.setVideotype("MP4");

                    cutUrl = URLDecoder.decode(URLDecoder.decode(cutUrl, "UTF-8"), "UTF-8");

                    logger.info("Youtube GET RESOURCE JSON PARSE SUCCEED {}, {}", srcUrl, JSONObject.toJSONString(youtubeVideoProcess));
                    return UrlUtil.urlEncoder(cutUrl);
                }
                logger.error("Youtube GET RESOURCE JSON PARSE FAILED {}, {}", srcUrl, videoUrl);
            }
        } catch (Exception e) {
            logger.error("Youtube GET RESOURCE JSON PARSE FAILED {}", srcUrl, e);
        }

        return "";
    }

    /**
     * 获取视频原链接(3步),从第三方网站
     * <p/>
     * 代理网站: http://www.clipconverter.cc/
     *
     * @param srcUrl
     * @param youtubeVideoProcess
     * @return
     * @throws Exception
     */
    private String getYoutubeResourceVideoUrl(String srcUrl, YoutubeVideoProcess youtubeVideoProcess) throws Exception {

        //1.验证视频地址
        String resContent = checkUrlForYoutube(srcUrl);
        if (StringUtils.isEmpty(resContent)) {
            logger.info("Youtube Check URL EMPTY {}", srcUrl);
        }

        //2.二次验证视频链接信息
        String resRedirctContent = checkDirectVideoUrl(resContent, srcUrl, youtubeVideoProcess);
        if (StringUtils.isEmpty(resRedirctContent)) {
            logger.info("Youtube Check SECOND REDIRECT URL EMPTY {}", srcUrl);
        }

        //3.获取源视频链接
        try {
            JSONObject object = JSONObject.parseObject(resRedirctContent);
            String redirect = object.getString("redirect");
            redirect = redirect.replaceAll("\\/", "/");

            //GET HEADERS
            Map<String, String> headers = new HashMap<>();
            headers.put("Connection", "keep-alive");
            headers.put("Referer", "http://www.clipconverter.cc/");

            //redirect url
            String reqUrl = "http://www.clipconverter.cc" + redirect + "?ajax";
            String resHtml = HttpClientUtil.get(reqUrl, headers, "UTF-8");
            Document document = Jsoup.parse(resHtml);

            String sourceVideoHref = document.select("#downloadbutton").attr("href");
            if (StringUtils.isEmpty(sourceVideoHref)) {
                logger.error("Youtube GET VIDEO SOURCE URL EMPTY {}, {}", srcUrl, resHtml);
            }

            logger.info("Youtube GET VIDEO SOURCE URL SUCCEED {}, {}", srcUrl, JSONObject.toJSONString(youtubeVideoProcess));
            return sourceVideoHref;
        } catch (Exception ex) {
            logger.error("Youtube GET VIDEO SOURCE URL FAILED {}", srcUrl);
        }

        return "";
    }

    /**
     * 二次验证获取调转后的video url
     *
     * @param resContent
     * @param srcUrl
     * @param youtubeVideoProcess
     * @return
     */
    protected String checkDirectVideoUrl(String resContent, String srcUrl, YoutubeVideoProcess youtubeVideoProcess) {

        Map<String, Object> postParams = new HashMap<>();

        try {
            JSONObject resObj = JSONObject.parseObject(resContent);

            //VIDEO INFO
            JSONObject video720pObj = new JSONObject();

            boolean isFlag = false;
            //获取跳转视频url(720p)
            JSONArray urlJsons = resObj.getJSONArray("url");
            for (int i = 0; i < urlJsons.size(); i++) {
                JSONObject urlObj = urlJsons.getJSONObject(i);
                String text = urlObj.getString("text");
                //默认取720p的视频
                if (RegexUtil.isMatch(check_video_720p_text_regex, text)) {
                    video720pObj.put("url", urlObj.getString("url"));
                    video720pObj.put("filetype", urlObj.getString("filetype"));
                    video720pObj.put("size", urlObj.getString("size"));

                    logger.info("Youtube Check Second Redirect Url SUCCEED {}", video720pObj.toJSONString());
                    isFlag = true;
                    break;
                }
            }

            if (!isFlag && urlJsons.size() > 0) {
                JSONObject urlObj = urlJsons.getJSONObject(0);
                video720pObj.put("url", urlObj.getString("url"));
                video720pObj.put("filetype", urlObj.getString("filetype"));
                video720pObj.put("size", urlObj.getString("size"));

                logger.info("Youtube Check Second-Array-First Redirect Url SUCCEED {}", video720pObj.toJSONString());
            }

            youtubeVideoProcess.setFilename(resObj.getString("filename"));
            youtubeVideoProcess.setVideotype(video720pObj.getString("filetype"));
            youtubeVideoProcess.setFilesize(Long.valueOf(video720pObj.getString("size")));

            postParams.put("mediaurl", srcUrl);
            postParams.put("url", video720pObj.getString("url"));
            postParams.put("filename", resObj.getString("filename"));
            postParams.put("filetype", video720pObj.getString("filetype"));
            postParams.put("format", "");
            postParams.put("audiovol", 0);
            postParams.put("audiochannel", 2);

            postParams.put("audiobr", "128");
            postParams.put("videobr", "224");
            postParams.put("videores", "352x288");
            postParams.put("videoaspect", "");
            postParams.put("customres", "320x240");
            postParams.put("timefrom-start", 1);
            postParams.put("timeto-end", 1);

            postParams.put("id3-artist", resObj.getString("id3artist"));
            postParams.put("id3-title", resObj.getString("id3title"));
            postParams.put("id3-album", "ClipConverter.cc");
            postParams.put("auto", 0);

            postParams.put("hash", "");
            postParams.put("image", resObj.getString("thumb"));
            postParams.put("org-filename", resObj.getString("filename"));
            postParams.put("videoid", resObj.getString("videoid"));
            postParams.put("pattern", resObj.getString("pattern"));

            postParams.put("server", resObj.getString("server"));
            postParams.put("serverinterface", resObj.getString("serverinterface"));
            postParams.put("service", resObj.getString("service"));
            postParams.put("ref", "");
            postParams.put("lang", "en");

            postParams.put("client_urlmap", "none");
            postParams.put("ipv6", "false");
            postParams.put("addon_urlmap", "");
            postParams.put("cookie", "");

            postParams.put("addon_cookie", "");
            postParams.put("addon_title", "");

            postParams.put("ablock", 0);
            postParams.put("clientside", resObj.getInteger("clientside"));
            postParams.put("addon_page", "none");
            postParams.put("verify", resObj.getString("verify"));

            postParams.put("result", "");
            postParams.put("again", "");
            postParams.put("addon_browser", "");
            postParams.put("addon_version", "");
        } catch (Exception ex) {
            logger.error("Youtube GET SECOND REDIRECT Parse Params Faield {}, {}", srcUrl, resContent);
        }

        if (MapUtils.isEmpty(postParams)) {
            logger.info("Youtube GET SECOND REDIRECT POST Params EMPTY {}, {}", srcUrl, resContent);
            return "";
        }

        //Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Connection", "keep-alive");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Referer", "http://www.clipconverter.cc/");

        try {
            String resSecondContent = HttpClientUtil.post("http://www.clipconverter.cc/check.php", postParams, headers);

            logger.info("Youtube Check Second URL SUCCEED {}", srcUrl);
            return resSecondContent;
        } catch (Exception e) {
            logger.error("Youtube Check SECOND URL FAILED {}", srcUrl);
        }

        return "";
    }


    /**
     * 视频Url验证 Check video url
     *
     * @param videoUrl
     * @return
     */
    protected String checkUrlForYoutube(String videoUrl) {

        //获取cookie
        Map<String, CookieStore> cookieStoreMap = new HashMap<>();

        //Init Headers
        Map<String, String> initHeaders = new HashMap<>();
        initHeaders.put("Connection", "keep-alive");

        //Send Request|GET COOKIE
        HttpClientUtil.getAndGetCookie("http://www.clipconverter.cc", initHeaders, "UTF-8", cookieStoreMap);

        logger.info("Youtube Checked Video Url -- GET COOKIE Succeed {} ", videoUrl);

        //=====POST CHECK URL START====
        Map<String, Object> postFormData = new HashMap<>();
        postFormData.put("mediaurl", videoUrl);
        postFormData.put("filename", "");
        postFormData.put("filetype", "");
        postFormData.put("format", "");
        postFormData.put("audiovol", "0");
        postFormData.put("audiochannel", "2");

        postFormData.put("audiobr", "128");
        postFormData.put("videobr", "224");
        postFormData.put("videores", "352x288");
        postFormData.put("videoaspect", "");
        postFormData.put("customres", "320x240");
        postFormData.put("timefrom-start", "1");

        postFormData.put("timeto-end", "1");
        postFormData.put("id3-artist", "");
        postFormData.put("id3-title", "");
        postFormData.put("id3-album", "ClipConverter.cc");
        postFormData.put("auto", "0");

        postFormData.put("hash", "");
        postFormData.put("image", "");
        postFormData.put("org-filename", "");
        postFormData.put("videoid", "");
        postFormData.put("pattern", "");

        postFormData.put("server", "");
        postFormData.put("serverinterface", "");
        postFormData.put("service", "");
        postFormData.put("ref", "");
        postFormData.put("lang", "en");

        postFormData.put("client_urlmap", "none");
        postFormData.put("ipv6", "false");
        postFormData.put("addon_urlmap", "");
        postFormData.put("cookie", "");
        postFormData.put("addon_cookie", "");

        postFormData.put("addon_title", "");
        postFormData.put("ablock", "0");
        postFormData.put("clientside", "0");
        postFormData.put("addon_page", "none");
        postFormData.put("verify", "");

        postFormData.put("result", "");
        postFormData.put("again", "");
        postFormData.put("addon_browser", "");
        postFormData.put("addon_version", "");

        try {
            //Send POST Request CHECK URL
            String resContent = HttpClientUtil.postAndSetCookie("http://www.clipconverter.cc/check.php", postFormData, initHeaders, (CookieStore) cookieStoreMap.values().toArray()[0]);

            logger.info("Youtube GET FIRST CHECKED Video URL SUCCEED {}", videoUrl);
            return resContent;
        } catch (IOException e) {
            logger.info("Youtube Check Url Failed {}", videoUrl);
        }
        return "";
    }

    /**
     * 解析YouTube 原网站视频url query参数,拼接
     *
     * @param streamMap
     * @return
     * @throws Exception
     * @author Bajie
     */
    private String parse_qs(String streamMap) throws Exception {
        String resultUrl = "";
        String[] videoInfoArray = streamMap.split(",");
        for (String videoInfoItem : videoInfoArray) {
            String type = "";
            String quality = "";
            String url = "";

            String[] videoInfo = videoInfoItem.split("\\&");
            for (String item : videoInfo) {
                String pair = URLDecoder.decode(item, "utf-8");

                String[] keyValue = pair.split("=");

                String key = keyValue[0];
                String value = keyValue[1];

                for (int i = 2; i < keyValue.length; i++) {
                    value += "=" + keyValue[i];
                }

                logger.info("Youtube Direct key: {}, value: {}", key, value);

                if (key.equals("type")) {
                    type = value;
                } else if (key.equals("quality")) {
                    quality = value;
                } else if (key.equals("url")) {
                    url = value;
                }
            }

            resultUrl = url;
            if (type.indexOf("video/mp4") != -1 && quality.indexOf("small") == -1) {
                break;
            }
        }

        return resultUrl;
    }

    /**
     * 返回编辑平台视频下载状态
     *
     * @param requestUrl
     * @return
     * @throws Exception
     * @description //response = "{\"result\":null,\"status\":{\"code\":1001,\"msg\":\"\"}}";
     */
    private boolean requestEditor(String requestUrl) {
        String response = "";

        try {
            logger.info("try Request Url: {} ...", requestUrl);
            response = HttpClientUtil.get(requestUrl);
            logger.info("Request finish: {}", response);

            JSONObject object = JSONObject.parseObject(response);
            JSONObject status = (JSONObject) object.get("status");
            String code = status.getString("code");

            if (code.contains("1001")) {
                logger.info("Youtube response editor-platform successful finished.");
                return true;
            }
            //error
            else {
                logger.error("Youtube response editor-platform download failed. resCode: {}", code);
                return false;
            }
        } catch (Exception ex) {
            logger.error("Youtube response editor-platform download EXCEPTION ", ex);
            return false;
        }
    }

    /**
     * 验证|查询DB,视频是否存在
     *
     * @param url
     * @return
     */
    private YoutubeVideoProcess buildYoutubeVideoProcessHandle(String url) {
        List<YoutubeVideoProcess> youtubeVideoProcessList = youtubeVideoProcessMapper.selectBySrcUrl(url);

        for (YoutubeVideoProcess item : youtubeVideoProcessList) {
            if (item != null && item.getId() != 0) {

                item.setExistTask(true);

                byte status = item.getStatus();
                if (status > 0)
                    return item;
            }
        }

        YoutubeVideoProcess youtubeVideoProcess = new YoutubeVideoProcess();
        youtubeVideoProcess.setSrcurl(url);

        youtubeVideoProcess.setCreated(System.currentTimeMillis());
        youtubeVideoProcess.setUpdated(System.currentTimeMillis());
        youtubeVideoProcessMapper.insertSelective(youtubeVideoProcess);

        return youtubeVideoProcess;
    }

    /**
     * UPDATE DB
     *
     * @param youtubeVideoProcess
     */
    private void updateProcess(YoutubeVideoProcess youtubeVideoProcess) {
        youtubeVideoProcess.setUpdated(System.currentTimeMillis());
        youtubeVideoProcessMapper.updateByPrimaryKeySelective(youtubeVideoProcess);
    }

    private String uploadVideo(String fileLoc, String videoType) {
        // 第二个参数文件名限制颇多,索性就不写了吧;
        Map<String, Object> resultMap = new HashMap<>();
        return UploadVideoUtil.upload(fileLoc, null, videoType, null, resultMap);
    }

    private void deleteLocalFile(String fileLoc) {
        File file = new File(fileLoc);
        if (file.delete()) {
            logger.info("deleteLocalFile {} ok!", fileLoc);
        } else {
            logger.error("deleteLocalFile {} failed!", fileLoc);
        }
    }
}

package com.mogu.international.spider.util;

import com.sun.mail.util.MailSSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by mx on 16/3/22.
 */
public class MailUtil {

    private static final Logger logger = LoggerFactory.getLogger(MailUtil.class);

    private MailUtil() {
    }


    public static void send(List<String> toList, String subject, String content) {
        try {
            Properties properties = System.getProperties();
            properties.setProperty("mail.smtp.host", "smtp.exmail.qq.com");
            properties.setProperty("mail.smtp.port", "465");
            properties.setProperty("mail.smtp.auth", "true");
            properties.setProperty("mail.transport.protocol", "smtp");

            //开启 ssl
            MailSSLSocketFactory sf = new MailSSLSocketFactory();
            sf.setTrustAllHosts(true);
            properties.put("mail.smtp.ssl.enable", "true");
            properties.put("mail.smtp.ssl.socketFactory", sf);

            Session session = Session.getInstance(properties, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication("mailaddress", "password");
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("from mail address"));
            for (String to : toList) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }

            message.setSubject(subject);
            message.setContent(content, "text/html; charset=utf-8");
            Transport.send(message);

            logger.info("SEND MAIL SUCCESS :{}", toList.size());
        } catch (Exception ex) {
            logger.error("SEND MAIL FAILED {}", ex.getMessage());
            logger.error("SEND MAIL FAILED {}", toList.toString(), ex);
        }
    }

    public static void send(String to, String subject, String content) {
        List<String> toList = new ArrayList<String>();
        toList.add(to);
        send(toList, subject, content);
    }
}

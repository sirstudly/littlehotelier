package com.macbackpackers.services;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExternalWebService {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private WordPressDAO dao;

    @Autowired
    @Qualifier("gsonForExternalWebService")
    private Gson gson;

    public String getCloudbedsLast2faCode(WebClient webClient) throws IOException {
        final String url = dao.getMandatoryOption("hbo_sms_lookup_url") + "/last2fa/cloudbeds";
        LOGGER.info("Looking up 2FA code from " + url);
        WebRequest requestSettings = new WebRequest(new URL(url), HttpMethod.GET);
        Page page = webClient.getPage(requestSettings);
        LOGGER.info(page.getWebResponse().getContentAsString());
        JsonElement jelement = gson.fromJson(page.getWebResponse().getContentAsString(), JsonElement.class);
        if (false == jelement.getAsJsonObject().has("message")) {
            throw new MissingUserDataException("Failed to lookup 2FA code for Cloudbeds");
        }
        Pattern p = Pattern.compile(".*(\\d{7})$");
        Matcher m = p.matcher(jelement.getAsJsonObject().get("message").getAsString());
        if (m.find()) {
            return m.group(1);
        }
        throw new MissingUserDataException("Failed to lookup 2FA code for Cloudbeds");
    }
}

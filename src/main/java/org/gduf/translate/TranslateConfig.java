package org.gduf.translate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslateConfig {

    @Value("${translate.baidu.app-id:}")
    private String appId;

    @Value("${translate.baidu.secret-key:}")
    private String secretKey;

    @Value("${translate.baidu.llm-endpoint:https://fanyi-api.baidu.com/api/trans/vip/translate}")
    private String llmEndpoint;

    @Value("${translate.baidu.general-endpoint:https://fanyi-api.baidu.com/api/trans/vip/translate}")
    private String generalEndpoint;

    @Bean
    public Translator baiduLlmTranslator() {
        return new BaiduTranslator("百度大模型翻译", 1, appId, secretKey, llmEndpoint, true);
    }

    @Bean
    public Translator baiduGeneralTranslator() {
        return new BaiduTranslator("百度通用翻译", 2, appId, secretKey, generalEndpoint, false);
    }
}

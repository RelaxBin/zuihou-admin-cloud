package top.tangyh.lamp.base.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.tangyh.basic.boot.config.BaseConfig;
import top.tangyh.basic.constant.Constants;
import top.tangyh.basic.log.event.SysLogListener;
import top.tangyh.lamp.base.interceptor.AuthenticationSaInterceptor;
import top.tangyh.lamp.base.interceptor.TokenContextFilter;
import top.tangyh.lamp.common.properties.IgnoreProperties;
import top.tangyh.lamp.common.properties.SystemProperties;
import top.tangyh.lamp.oauth.facade.LogFacade;
import top.tangyh.lamp.system.facade.DefResourceFacade;

/**
 * 基础服务-Web配置
 *
 * @author zuihou
 * @date 2021-10-08
 */
@Configuration
@EnableConfigurationProperties({IgnoreProperties.class, SystemProperties.class})
@RequiredArgsConstructor
public class BootWebConfiguration extends BaseConfig implements WebMvcConfigurer {


    private final IgnoreProperties ignoreProperties;
    private final DefResourceFacade defResourceFacade;
    @Value("${spring.profiles.active:dev}")
    protected String profiles;

    @Bean
    public HandlerInterceptor getTokenContextFilter() {
        return new TokenContextFilter(profiles, ignoreProperties);
    }

    @Bean
    public HandlerInterceptor getSaFilter() {
        return new AuthenticationSaInterceptor(ignoreProperties, defResourceFacade);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    /**
     * 注册 拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] commonPathPatterns = getExcludeCommonPathPatterns();
        registry.addInterceptor(getTokenContextFilter())
                .addPathPatterns("/**")
                .order(5)
                .excludePathPatterns(commonPathPatterns);

        // 注册 Sa-Token 拦截器，定义详细认证规则
        registry.addInterceptor(getSaFilter()).addPathPatterns("/**").order(10);


        WebMvcConfigurer.super.addInterceptors(registry);
    }


    /**
     * auth-client 中的拦截器需要排除拦截的地址
     */
    protected String[] getExcludeCommonPathPatterns() {
        return new String[]{
                "/*.css",
                "/*.js",
                "/*.html",
                "/error",
                "/login",
                "/v2/api-docs",
                "/v2/api-docs-ext",
                "/swagger-resources/**",
                "/webjars/**",

                "/",
                "/csrf",

                "/META-INF/resources/**",
                "/resources/**",
                "/static/**",
                "/public/**",
                "classpath:/META-INF/resources/**",
                "classpath:/resources/**",
                "classpath:/static/**",
                "classpath:/public/**",

                "/cache/**",
                "/swagger-ui.html**",
                "/doc.html**"
        };
    }


    /**
     * lamp.log.enabled = true 并且 lamp.log.type=DB时实例该类
     */
    @Bean
    @ConditionalOnExpression("${" + Constants.PROJECT_PREFIX + ".log.enabled:true} && 'DB'.equals('${" + Constants.PROJECT_PREFIX + ".log.type:LOGGER}')")
    public SysLogListener sysLogListener(LogFacade logApi) {
        return new SysLogListener(logApi::save);
    }
}

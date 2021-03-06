/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Code Technology Studio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.jpom;

import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.ArrayUtil;
import cn.jiangzeyin.common.EnableCommonBoot;
import cn.jiangzeyin.common.spring.SpringUtil;
import cn.jiangzeyin.common.spring.event.ApplicationEventLoad;
import io.jpom.common.Type;
import io.jpom.common.interceptor.IpInterceptor;
import io.jpom.common.interceptor.LoginInterceptor;
import io.jpom.common.interceptor.OpenApiInterceptor;
import io.jpom.common.interceptor.PermissionInterceptor;
import io.jpom.model.data.SystemIpConfigModel;
import io.jpom.service.system.SystemParametersServer;
import io.jpom.service.user.UserService;
import io.jpom.system.db.DbConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * jpom ?????????
 *
 * @author jiangzeyin
 * @since 2017/9/14
 */
@SpringBootApplication
@ServletComponentScan
@EnableCommonBoot
public class JpomServerApplication implements ApplicationEventLoad {

    /**
     * ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private static boolean loadInitDb = false;
    /**
     * ???????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private static boolean recoverH2Db = false;

    /**
     * ????????????
     * --rest:ip_config ?????? IP ???????????????
     * --rest:load_init_db ????????????????????????????????????
     * --rest:super_user_pwd ???????????????????????????
     * --recover:h2db ??? h2 ??????????????????????????????????????????????????????
     * --close:super_user_mfa ????????????????????? mfa
     *
     * @param args ??????
     * @throws Exception ??????
     */
    public static void main(String[] args) throws Exception {
        long time = SystemClock.now();
        if (ArrayUtil.containsIgnoreCase(args, "--rest:load_init_db")) {
            loadInitDb = true;
        }
        if (ArrayUtil.containsIgnoreCase(args, "--recover:h2db")) {
            recoverH2Db = true;
        }
        //
        JpomApplication jpomApplication = new JpomApplication(Type.Server, JpomServerApplication.class, args);
        jpomApplication
                // ?????????
                .addInterceptor(IpInterceptor.class)
                .addInterceptor(LoginInterceptor.class)
                .addInterceptor(OpenApiInterceptor.class)
                .addInterceptor(PermissionInterceptor.class)
                .run(args);
        // ?????? ip ???????????????
        if (ArrayUtil.containsIgnoreCase(args, "--rest:ip_config")) {
            SystemParametersServer parametersServer = SpringUtil.getBean(SystemParametersServer.class);
            parametersServer.delByKey(SystemIpConfigModel.ID);
            Console.log("Clear IP whitelist configuration successfully");
        }
        //  ???????????????????????????
        if (ArrayUtil.containsIgnoreCase(args, "--rest:super_user_pwd")) {
            UserService userService = SpringUtil.getBean(UserService.class);
            String restResult = userService.restSuperUserPwd();
            if (restResult != null) {
                Console.log(restResult);
            } else {
                Console.log("There is no super administrator account in the system");
            }
        }
        // ????????????????????? mfa
        if (ArrayUtil.containsIgnoreCase(args, "--close:super_user_mfa")) {
            UserService userService = SpringUtil.getBean(UserService.class);
            String restResult = userService.closeSuperUserMfa();
            if (restResult != null) {
                Console.log(restResult);
            } else {
                Console.log("There is no super administrator account in the system");
            }
        }
        Console.log("Time-consuming to start this time???{}", DateUtil.formatBetween(SystemClock.now() - time, BetweenFormatter.Level.MILLISECOND));
    }

    @Override
    public void applicationLoad() {
        DbConfig instance = DbConfig.getInstance();
        if (loadInitDb) {
            instance.clearExecuteSqlLog();
        }
        if (recoverH2Db) {
            try {
                instance.recoverDb();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-2);
            }
        }
    }
}

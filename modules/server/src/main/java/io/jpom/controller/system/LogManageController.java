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
package io.jpom.controller.system;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.jiangzeyin.common.JsonMessage;
import cn.jiangzeyin.common.spring.SpringUtil;
import cn.jiangzeyin.common.validator.ValidatorItem;
import cn.jiangzeyin.common.validator.ValidatorRule;
import com.alibaba.fastjson.JSONArray;
import io.jpom.common.BaseServerController;
import io.jpom.common.forward.NodeForward;
import io.jpom.common.forward.NodeUrl;
import io.jpom.permission.ClassFeature;
import io.jpom.permission.Feature;
import io.jpom.permission.MethodFeature;
import io.jpom.permission.SystemPermission;
import io.jpom.socket.ServiceFileTailWatcher;
import io.jpom.system.WebAopLog;
import io.jpom.util.LayuiTreeUtil;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * ??????????????????
 *
 * @author bwcx_jzy
 * @since 2019/7/20
 */
@RestController
@RequestMapping(value = "system")
@Feature(cls = ClassFeature.SYSTEM_LOG)
@SystemPermission
public class LogManageController extends BaseServerController {


    @RequestMapping(value = "log_data.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public String logData(String nodeId) {
        if (StrUtil.isNotEmpty(nodeId)) {
            return NodeForward.request(getNode(), getRequest(), NodeUrl.SystemLog).toString();
        }
        WebAopLog webAopLog = SpringUtil.getBean(WebAopLog.class);
        JSONArray data = LayuiTreeUtil.getTreeData(webAopLog.getPropertyValue());
        return JsonMessage.getString(200, "", data);
    }

    /**
     * ?????? ????????????????????????????????????
     *
     * @param nodeId ??????
     * @param path   ??????
     * @return json
     */
    @RequestMapping(value = "log_del.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DEL)
    public String logData(String nodeId,
                          @ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "path??????") String path) {
        if (StrUtil.isNotEmpty(nodeId)) {
            return NodeForward.request(getNode(), getRequest(), NodeUrl.DelSystemLog).toString();
        }
        WebAopLog webAopLog = SpringUtil.getBean(WebAopLog.class);
        File file = FileUtil.file(webAopLog.getPropertyValue(), path);
        // ??????????????????
        long modified = file.lastModified();
        Assert.state(System.currentTimeMillis() - modified > TimeUnit.DAYS.toMillis(1), "????????????????????????????????????(??????????????????)");

        if (FileUtil.del(file)) {
            // ?????????????????????
            ServiceFileTailWatcher.offlineFile(file);
            return JsonMessage.getString(200, "????????????");
        }
        return JsonMessage.getString(500, "????????????");
    }


    @RequestMapping(value = "log_download", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DOWNLOAD)
    public void logDownload(String nodeId,
                            @ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "path??????") String path) {
        if (StrUtil.isNotEmpty(nodeId)) {
            NodeForward.requestDownload(getNode(), getRequest(), getResponse(), NodeUrl.DownloadSystemLog);
            return;
        }
        WebAopLog webAopLog = SpringUtil.getBean(WebAopLog.class);
        File file = FileUtil.file(webAopLog.getPropertyValue(), path);
        if (file.isFile()) {
            ServletUtil.write(getResponse(), file);
        }
    }
}

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
package io.jpom.common;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Tuple;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.jiangzeyin.common.DefaultSystemLog;
import cn.jiangzeyin.common.JsonMessage;
import com.alibaba.fastjson.JSONObject;
import io.jpom.JpomApplication;
import io.jpom.model.BaseJsonModel;
import io.jpom.system.ConfigBean;
import io.jpom.system.ExtConfigBean;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ?????????????????????
 *
 *
 * <pre>
 * {
 * "tag_name": "v2.6.4",
 * "agentUrl": "",
 * "serverUrl": "",
 * "changelog": ""
 * }
 * </pre>
 *
 * @author bwcx_jzy
 * @since 2021/9/19
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class RemoteVersion extends BaseJsonModel {

    /**
     * ??? url ??????????????????????????????
     * <p>
     * 1. https://dromara.gitee.io/Jpom/docs/release-versions.json
     * <p>
     * 2. https://jpom.io/docs/release-versions.json
     * <p>
     * 3. https://cdn.jsdelivr.net/gh/dromara/Jpom@docs/docs/release-versions.json
     */
    private static final String DEFAULT_URL = "https://jpom.io/docs/release-versions.json";
    /**
     * ??????????????????
     */
    private static final int CHECK_INTERVAL = 24;

    /**
     * ????????????
     */
    private String tagName;
    /**
     * ?????????????????????
     */
    private String agentUrl;
    /**
     * ?????????????????????
     */
    private String serverUrl;
    /**
     * ???????????? (??????url)
     */
    private String changelogUrl;
    /**
     * ????????????
     */
    private String changelog;
    /**
     * ??????????????????
     */
    private Long lastTime;

    /**
     * ??????????????????
     */
    private Boolean upgrade;

    @Override
    public String toString() {
        return super.toString();
    }

    /**
     * ????????????????????????
     *
     * @return ????????????
     */
    public static RemoteVersion loadRemoteInfo() {
        String body = StrUtil.EMPTY;
        try {
            String remoteVersionUrl = ExtConfigBean.getInstance().getRemoteVersionUrl();
            remoteVersionUrl = StrUtil.emptyToDefault(remoteVersionUrl, DEFAULT_URL);
            remoteVersionUrl = Validator.isUrl(remoteVersionUrl) ? remoteVersionUrl : DEFAULT_URL;
            // ????????????????????????
            RemoteVersion remoteVersion = RemoteVersion.loadTransitUrl(remoteVersionUrl);
            if (remoteVersion == null || StrUtil.isEmpty(remoteVersion.getTagName())) {
                // ??????????????????
                return null;
            }
            // ????????????
            RemoteVersion.cacheLoadTime(remoteVersion);
            return remoteVersion;
        } catch (Exception e) {
            DefaultSystemLog.getLog().warn("??????????????????????????????:{} {}", e.getMessage(), body);
            return null;
        }
    }

    /**
     * ????????????????????????????????????
     *
     * @param remoteVersionUrl ???url
     * @return ??????URL
     */
    private static RemoteVersion loadTransitUrl(String remoteVersionUrl) {
        String body = StrUtil.EMPTY;
        try {
            log.debug("use remote version url: {}", remoteVersionUrl);
            HttpRequest request = HttpUtil.createGet(remoteVersionUrl);
            try (HttpResponse execute = request.execute()) {
                body = execute.body();
            }
            //
            JSONObject jsonObject = JSONObject.parseObject(body);
            RemoteVersion remoteVersion = jsonObject.toJavaObject(RemoteVersion.class);
            if (StrUtil.isAllNotEmpty(remoteVersion.getTagName(), remoteVersion.getAgentUrl(), remoteVersion.getServerUrl(), remoteVersion.getServerUrl())) {
                return remoteVersion;
            }
            String jumpUrl = jsonObject.getString("url");
            if (StrUtil.isEmpty(jumpUrl)) {
                return null;
            }
            return loadTransitUrl(jumpUrl);
        } catch (Exception e) {
            DefaultSystemLog.getLog().warn("??????????????????????????????:{} {}", e.getMessage(), body);
            return null;
        }
    }

    /**
     * ????????????
     *
     * @param remoteVersion ??????????????????
     */
    private static void cacheLoadTime(RemoteVersion remoteVersion) {
        remoteVersion = ObjectUtil.defaultIfNull(remoteVersion, new RemoteVersion());
        remoteVersion.setLastTime(SystemClock.now());
        // ????????????????????????
        JpomManifest instance = JpomManifest.getInstance();
        if (!instance.isDebug()) {
            String version = instance.getVersion();
            String tagName = remoteVersion.getTagName();
            tagName = StrUtil.removePrefixIgnoreCase(tagName, "v");
            remoteVersion.setUpgrade(StrUtil.compareVersion(version, tagName) < 0);
        } else {
            remoteVersion.setUpgrade(false);
        }
        // ??????????????????????????????
        Type type = instance.getType();
        String remoteUrl = type.getRemoteUrl(remoteVersion);
        if (StrUtil.isEmpty(remoteUrl)) {
            remoteVersion.setUpgrade(false);
        }
        // ?????? changelog
        String changelogUrl = remoteVersion.getChangelogUrl();
        if (StrUtil.isNotEmpty(changelogUrl)) {
            try (HttpResponse execute = HttpUtil.createGet(changelogUrl).execute()) {
                String body = execute.body();
                remoteVersion.setChangelog(body);
            }
        }
        //
        FileUtil.writeUtf8String(remoteVersion.toString(), getFile());
    }

    /**
     * ?????????????????? ??????????????????
     *
     * @return RemoteVersion
     */
    public static RemoteVersion cacheInfo() {
        if (!FileUtil.isFile(getFile())) {
            return null;
        }
        RemoteVersion remoteVersion = null;
        String fileStr = StrUtil.EMPTY;
        try {
            fileStr = FileUtil.readUtf8String(getFile());
            if (StrUtil.isEmpty(fileStr)) {
                return null;
            }
            remoteVersion = JSONObject.parseObject(fileStr, RemoteVersion.class);
        } catch (Exception e) {
            DefaultSystemLog.getLog().warn("??????????????????????????????:{} {}", e.getMessage(), fileStr);
        }
        // ????????????????????????
        Long lastTime = remoteVersion == null ? 0 : remoteVersion.getLastTime();
        lastTime = ObjectUtil.defaultIfNull(lastTime, 0L);
        long interval = SystemClock.now() - lastTime;
        return interval >= TimeUnit.HOURS.toMillis(CHECK_INTERVAL) ? null : remoteVersion;
    }

    /**
     * ??????
     *
     * @param savePath    ????????????????????????
     * @param type        ??????
     * @param checkRepeat ??????????????????
     * @return ??????????????????
     * @throws IOException ??????
     */
    public static Tuple download(String savePath, Type type, boolean checkRepeat) throws IOException {
        RemoteVersion remoteVersion = loadRemoteInfo();
        Assert.notNull(remoteVersion, "??????????????????????????????:-1");
        // ??????????????????????????????
        String remoteUrl = type.getRemoteUrl(remoteVersion);
        Assert.hasText(remoteUrl, "???????????????,?????????????????????");
        // ??????
        File downloadFileFromUrl = HttpUtil.downloadFileFromUrl(remoteUrl, savePath);
        // ???????????????
        File file = JpomManifest.zipFileFind(FileUtil.getAbsolutePath(downloadFileFromUrl), type, savePath);
        // ??????
        JsonMessage<Tuple> error = JpomManifest.checkJpomJar(FileUtil.getAbsolutePath(file), type, checkRepeat);
        Assert.state(error.getCode() == HttpStatus.HTTP_OK, error.getMsg());
        return error.getData();
    }

    /**
     * ??????
     *
     * @param savePath ????????????????????????
     * @param type     ??????
     * @return ??????????????????
     * @throws IOException ??????
     */
    public static Tuple download(String savePath, Type type) throws IOException {
        return download(savePath, type, true);
    }

    /**
     * ??????
     *
     * @param savePath ????????????????????????
     * @throws IOException ??????
     */
    public static void upgrade(String savePath) throws IOException {
        upgrade(savePath, null);
    }

    /**
     * ??????
     *
     * @param savePath ????????????????????????
     * @param consumer ?????????????????????
     * @throws IOException ??????
     */
    public static void upgrade(String savePath, Consumer<Tuple> consumer) throws IOException {
        Type type = JpomManifest.getInstance().getType();
        // ??????
        Tuple data = download(savePath, type);
        File file = data.get(3);
        // ????????????
        String path = FileUtil.getAbsolutePath(file);
        String version = data.get(0);
        JpomManifest.releaseJar(path, version);
        //
        if (consumer != null) {
            consumer.accept(data);
        }
        JpomApplication.restart();
    }

    /**
     * ???????????????
     *
     * @return file
     */
    private static File getFile() {
        return FileUtil.file(ConfigBean.getInstance().getDataPath(), ConfigBean.REMOTE_VERSION);
    }
}

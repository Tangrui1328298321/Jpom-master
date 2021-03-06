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
package io.jpom.common.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.jwt.JWT;
import cn.jiangzeyin.common.JsonMessage;
import cn.jiangzeyin.common.interceptor.InterceptorPattens;
import cn.jiangzeyin.common.spring.SpringUtil;
import io.jpom.common.BaseServerController;
import io.jpom.common.ServerOpenApi;
import io.jpom.model.data.UserModel;
import io.jpom.service.user.UserService;
import io.jpom.system.ServerConfigBean;
import io.jpom.system.ServerExtConfigBean;
import io.jpom.system.db.DbConfig;
import io.jpom.util.JwtUtil;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * ???????????????
 *
 * @author jiangzeyin
 * @since 2017/2/4.
 */
@InterceptorPattens(sort = -1, exclude = ServerOpenApi.API + "**")
public class LoginInterceptor extends BaseJpomInterceptor {
	/**
	 * session
	 */
	public static final String SESSION_NAME = "user";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
		HttpSession session = getSession();
		boolean init = DbConfig.getInstance().isInit();
		if (!init) {
			ServletUtil.write(response, JsonMessage.getString(100, "?????????????????????????????????,???????????????"), MediaType.APPLICATION_JSON_VALUE);
			return false;
		}
		//
		NotLogin notLogin = handlerMethod.getMethodAnnotation(NotLogin.class);
		if (notLogin == null) {
			notLogin = handlerMethod.getBeanType().getAnnotation(NotLogin.class);
		}
		if (notLogin == null) {
			// ??????????????????????????????????????? Authorization ??????
			String authorization = request.getHeader(ServerOpenApi.HTTP_HEAD_AUTHORIZATION);
			if (StrUtil.isNotEmpty(authorization)) {
				// jwt token ????????????
				int code = this.checkHeaderUser(request, session);
				if (code > 0) {
					this.responseLogin(request, response, handlerMethod, code);
					return false;
				}
			}
			// ?????????????????????
			if (!this.tryGetHeaderUser(request, session)) {
				this.responseLogin(request, response, handlerMethod, ServerConfigBean.AUTHORIZE_TIME_OUT_CODE);
				return false;
			}
		}
		reload();
		//
		return true;
	}

	/**
	 * ???????????? header ????????????
	 *
	 * @param session ses
	 * @param request req
	 * @return true ????????????
	 */
	private int checkHeaderUser(HttpServletRequest request, HttpSession session) {
		String token = request.getHeader(ServerOpenApi.HTTP_HEAD_AUTHORIZATION);
		if (StrUtil.isEmpty(token)) {
			return ServerConfigBean.AUTHORIZE_TIME_OUT_CODE;
		}
		JWT jwt = JwtUtil.readBody(token);
		if (JwtUtil.expired(jwt, 0)) {
			int renewal = ServerExtConfigBean.getInstance().getAuthorizeRenewal();
			if (jwt == null || renewal <= 0 || JwtUtil.expired(jwt, TimeUnit.MINUTES.toSeconds(renewal))) {
				return ServerConfigBean.AUTHORIZE_TIME_OUT_CODE;
			}
			return ServerConfigBean.RENEWAL_AUTHORIZE_CODE;
		}
		UserModel user = (UserModel) session.getAttribute(SESSION_NAME);
		UserService userService = SpringUtil.getBean(UserService.class);
		String id = JwtUtil.getId(jwt);
		UserModel newUser = userService.checkUser(id);
		if (newUser == null) {
			return ServerConfigBean.AUTHORIZE_TIME_OUT_CODE;
		}
		if (null != user) {
			String tokenUserId = JwtUtil.readUserId(jwt);
			boolean b = user.getId().equals(tokenUserId);
			if (!b) {
				return ServerConfigBean.AUTHORIZE_TIME_OUT_CODE;
			}
		}
		session.setAttribute(LoginInterceptor.SESSION_NAME, newUser);
		return 0;
	}


	/**
	 * ???????????? header ????????????
	 *
	 * @param session ses
	 * @param request req
	 * @return true ????????????
	 */
	private boolean tryGetHeaderUser(HttpServletRequest request, HttpSession session) {
		String header = request.getHeader(ServerOpenApi.USER_TOKEN_HEAD);
		if (StrUtil.isEmpty(header)) {
			// ??????????????? ????????????
			UserModel user = (UserModel) session.getAttribute(SESSION_NAME);
			return user != null;
		}
		UserService userService = SpringUtil.getBean(UserService.class);
		UserModel userModel = userService.checkUser(header);
		if (userModel == null) {
			return false;
		}
		session.setAttribute(LoginInterceptor.SESSION_NAME, userModel);
		return true;
	}

	/**
	 * ????????????
	 *
	 * @param request       req
	 * @param response      res
	 * @param handlerMethod ??????
	 * @throws IOException ??????
	 */
	private void responseLogin(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod, int code) throws IOException {
//        if (isPage(handlerMethod)) {
//            String url = "/login.html?";
//            String uri = request.getRequestURI();
//            if (StrUtil.isNotEmpty(uri) && !StrUtil.SLASH.equals(uri)) {
//                String queryString = request.getQueryString();
//                if (queryString != null) {
//                    uri += "?" + queryString;
//                }
//                // ??????
//                String newUri = BaseJpomInterceptor.getHeaderProxyPath(request) + uri;
//                newUri = UrlRedirectUtil.getRedirect(request, newUri);
//                url += "&url=" + URLUtil.encodeAll(newUri);
//            }
//            String header = request.getHeader(HttpHeaders.REFERER);
//            if (header != null) {
//                url += "&r=" + header;
//            }
//            sendRedirects(request, response, url);
//            return;
//        }
		ServletUtil.write(response, JsonMessage.getString(code, "?????????????????????,????????????"), MediaType.APPLICATION_JSON_VALUE);
	}


//    @Override
//    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
//        super.postHandle(request, response, handler, modelAndView);
//        HttpSession session;
//        try {
//            session = getSession();
//        } catch (Exception ignored) {
//            return;
//        }
//        try {
//            // ????????????????????????
//            session.setAttribute("staticCacheTime", DateUtil.currentSeconds());
//            // ??????????????????
//            Object jpomProxyPath = session.getAttribute("jpomProxyPath");
//            if (jpomProxyPath == null) {
//                String path = getHeaderProxyPath(request);
//                session.setAttribute("jpomProxyPath", path);
//            }
//        } catch (Exception ignored) {
//        }
//        try {
//            // ?????????js ??????
//            String jsCommonContext = (String) session.getAttribute("jsCommonContext");
//            if (jsCommonContext == null) {
//                String path = ExtConfigBean.getInstance().getPath();
//                File file = FileUtil.file(String.format("%s/script/common.js", path));
//                if (file.exists()) {
//                    jsCommonContext = FileUtil.readString(file, CharsetUtil.CHARSET_UTF_8);
//                    jsCommonContext = URLEncoder.DEFAULT.encode(jsCommonContext, CharsetUtil.CHARSET_UTF_8);
//                }
//                session.setAttribute("jsCommonContext", jsCommonContext);
//            }
//        } catch (IllegalStateException ignored) {
//        }
//    }

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		super.afterCompletion(request, response, handler, ex);
		BaseServerController.removeAll();
	}


}

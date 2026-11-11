/*
 * Copyright 2002-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.jtrac.web;

import info.jtrac.Jtrac;
import java.lang.reflect.Method;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for MultiActionControllers handling HTTP requests
 */
public abstract class AbstractMultiActionController {        

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected Jtrac jtrac;

    public void setJtrac(Jtrac jtrac) {
        this.jtrac = jtrac;
    }

    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        handleRequestInternal(request, response);
    }

    protected void handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String methodName = getHandlerMethodName(request);
        if (methodName == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            Method method = getClass().getMethod(methodName, HttpServletRequest.class, HttpServletResponse.class);
            method.invoke(this, request, response);
        } catch (NoSuchMethodException e) {
            logger.warn("Handler method '{}' not found: {}", methodName, e.getMessage());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    protected abstract String getHandlerMethodName(HttpServletRequest request);

    protected void applyCacheSeconds(HttpServletResponse response, int seconds, boolean mustRevalidate) {
        if (seconds > 0) {
            response.setDateHeader("Expires", System.currentTimeMillis() + seconds * 1000L);
            String headerVal = "max-age=" + seconds;
            if (mustRevalidate) {
                headerVal += ", must-revalidate";
            }
            response.setHeader("Cache-Control", headerVal);
        } else if (seconds == 0) {
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 1L);
            response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        }
    }
}

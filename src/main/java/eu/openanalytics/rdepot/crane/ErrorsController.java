package eu.openanalytics.rdepot.crane;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ErrorsController implements ErrorController {

    @RequestMapping(value = "/error", produces = "text/html")
    public String handleErrorAsHtml(HttpServletRequest request, HttpServletResponse response, ModelMap map) {
        setNoCacheHeader(response);
        map.put("mainPage", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
        map.put("resource", request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH));

        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "not-found";
            }
        }

        return "error";
    }

    @RequestMapping(value =     "/access-denied", produces = "text/html")
    public String handleAccessDeniedAsHtml(HttpServletRequest request, HttpServletResponse response, ModelMap map) {
        setNoCacheHeader(response);
        map.put("mainPage", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
        map.put("resource", request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH));
        return "access-denied";
    }

    @RequestMapping("/error")
    @ResponseBody
    public Map<String, Object> handleError(HttpServletRequest request, HttpServletResponse response) {
        setNoCacheHeader(response);
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        return new HashMap<>() {{
            put("status", "error");
            put("code", status != null ? Integer.parseInt(status.toString()) : null);
            put("resource", request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH).toString());
        }};
    }

    @RequestMapping( "/access-denied")
    @ResponseBody
    public Map<String, Object> handleAccessDenied(HttpServletRequest request, HttpServletResponse response) {
        setNoCacheHeader(response);

        return new HashMap<>() {{
            put("status", "access_denied");
            put("code", 403);
            put("resource", request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH).toString());
        }};
    }

    /**
     * Add Cache-Control header to never cacher error responses.
     */
    private void setNoCacheHeader(HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noCache().getHeaderValue());
    }

}

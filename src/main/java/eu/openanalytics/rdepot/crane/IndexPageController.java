package eu.openanalytics.rdepot.crane;

import eu.openanalytics.rdepot.crane.model.Repository;
import eu.openanalytics.rdepot.crane.service.IndexPageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;

@Controller
public class IndexPageController {

    private final IndexPageService indexPageService;

    public IndexPageController(IndexPageService indexPageService) {
        this.indexPageService = indexPageService;
    }

    @GetMapping("/__index")
    public String main(ModelMap map, HttpServletRequest request) throws IOException {
        Path path = (Path) request.getAttribute("path");
        Repository repo = (Repository) request.getAttribute("repo");

        String resource = (String) request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
        resource = resource.replace(repo.getIndexFileName(), "");
        map.put("resource", resource);
        map.putAll(indexPageService.getTemplateVariables(repo, path));

        return indexPageService.getTemplateName(repo);
    }

}

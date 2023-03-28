package eu.openanalytics.rdepot.crane.service;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.CraneFile;
import eu.openanalytics.rdepot.crane.model.Repository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class IndexPageService {

    private final CraneConfig config;

    public IndexPageService(CraneConfig config) {
        this.config = config;
    }

    public String getTemplateName(Repository repository) {
        return "default-index";
    }

    public Map<String, Object> getTemplateVariables(Repository repository, Path path) throws IOException {
        List<CraneFile> craneFiles;
        try (Stream<Path> dirListing = Files.list(path)) {
            craneFiles = dirListing.map(CraneFile::createFromPath).collect(Collectors.toList());
        }

        Map<String, Object> map = new HashMap<>();
        map.put("files", craneFiles);
        return map;
    }

}

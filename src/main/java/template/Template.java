package template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract Template defining the Template Method skeleton for generating
 * standardized scaffolded files.
 */
public abstract class Template {

    /**
     * Template Method defining the standard structure of a scaffolded file.
     */
    public final List<String> generateScaffold(String fileName) {
        List<String> lines = new ArrayList<>();
        lines.addAll(generateHeader(fileName));
        lines.addAll(generateBody(fileName));
        lines.addAll(generateFooter(fileName));
        return Collections.unmodifiableList(lines);
    }

    protected abstract List<String> generateHeader(String fileName);

    protected abstract List<String> generateBody(String fileName);

    protected List<String> generateFooter(String fileName) {
        return Collections.emptyList(); // Default hook is empty
    }

    public abstract String getTemplateType();
    public abstract String getDefaultExtension();
}

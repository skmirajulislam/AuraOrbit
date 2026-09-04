package template;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for retrieving Template instances based on file extension or template name.
 */
public class TemplateFactory {
    private static final Map<String, Template> REGISTRY = new HashMap<>();

    static {
        register(new JavaTemplate());
        register(new MarkdownTemplate());
        register(new JsonTemplate());
    }

    public static void register(Template template) {
        REGISTRY.put(template.getDefaultExtension().toLowerCase(), template);
        REGISTRY.put(template.getTemplateType().toLowerCase(), template);
    }

    public static Template getTemplate(String identifier) {
        if (identifier == null) return null;
        String key = identifier.trim().toLowerCase();
        if (key.startsWith(".")) key = key.substring(1);
        return REGISTRY.get(key);
    }

    public static Map<String, String> getAvailableTemplates() {
        Map<String, String> map = new HashMap<>();
        for (Template t : REGISTRY.values()) {
            map.put(t.getDefaultExtension(), t.getTemplateType());
        }
        return map;
    }
}

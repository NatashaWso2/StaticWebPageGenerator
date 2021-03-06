package org.wso2.generator;

import com.vladsch.flexmark.ast.Document;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.jekyll.tag.JekyllTag;
import com.vladsch.flexmark.ext.jekyll.tag.JekyllTagExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.xwiki.macros.MacroExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.MutableDataSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates the HTML page by parsing the markdown file
 */
public class HtmlGenerator {
    static final MutableDataSet OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, Arrays.asList(
                    AttributesExtension.create(), YamlFrontMatterExtension.create(),
                    JekyllTagExtension.create(), MacroExtension.create(), CustomExtension.create(),
                    TablesExtension.create()
            ))
            .set(MacroExtension.ENABLE_RENDERING, true)
            .set(TablesExtension.CLASS_NAME, "table-style");
    static final String userDir = System.getProperty("user.dir");
    static final String generated_file_directory = userDir + "/src/main/resources/generated/";
    static final String layout_template_directory = userDir + "/src/main/resources/layouts/";
    static final String markdown_file_directory = userDir + "/src/main/resources/markdown/";
    static final String included_file_directory = userDir + "/src/main/resources/included/";
    static final String default_template = "default";
    static final Parser parser = Parser.builder(OPTIONS).build();
    static final HtmlRenderer renderer = HtmlRenderer.builder(OPTIONS).build();
    static Map<String, String> included;
    static Map<String, List<String>> frontMatterList;

    /**
     * Generate pages for all markdown files given in the directory
     *
     * @throws IOException
     */
    public static void generatePagesForAllFiles() throws IOException {
        List<File> fileList = listAllFilesInDirectory(markdown_file_directory);
        included = getIncludedFilesWithContent();

        for (File file : fileList) {
            generatePageForFile(file);
        }
    }

    /**
     * Generate HTML page for a file
     *
     * @param file
     * @throws IOException
     */
    public static void generatePageForFile(File file) throws IOException {
        included = getIncludedFilesWithContent();
        String markdown = readMarkdownFile(file.getName());
        Node document = processMarkDownFile(markdown);

        populateYamlFrontmatterList(document);

        StringBuilder layoutHTML = selectLayoutTemplate();

        String generatedHtmlFromMarkdown = renderDocument(document);

        addIncludedFilesInHTML(layoutHTML);
        generateHtmlFullDocument(layoutHTML, frontMatterList, generatedHtmlFromMarkdown);

        String htmlFileName = getNameForGeneratedHtml(file.getName());
        writeHtmlToFile(htmlFileName, layoutHTML.toString());
    }

    /**
     * Get the yaml front matter properties from markdown
     *
     * @param document
     */
    public static void populateYamlFrontmatterList(Node document) {
        AbstractYamlFrontMatterVisitor visitor = new AbstractYamlFrontMatterVisitor();
        visitor.visit(document);
        frontMatterList = visitor.getData();
    }

    /**
     * Decide the layout from the front matter declared
     *
     * @return layout content
     * @throws IOException
     */
    public static StringBuilder selectLayoutTemplate() throws IOException {
        StringBuilder layoutHTML;
        if (frontMatterList.get("layout") != null) {
            String layoutTemplate = frontMatterList.get("layout").get(0);
            layoutHTML = new StringBuilder(getLayoutContent(layoutTemplate));
        } else {
            layoutHTML = new StringBuilder(getLayoutContent(default_template));
        }
        return layoutHTML;
    }

    /**
     * Render HTML document by parsing through the HtmlRender
     *
     * @param document
     * @return HTML document as a string
     */
    public static String renderDocument(Node document) {
        return renderer.render(document);
    }

    /**
     * Get file name for generated HTML page
     *
     * @param fileName
     * @return file name for generated HTML page
     */
    public static String getNameForGeneratedHtml(String fileName) {
        return fileName.replace(".md", "");
    }

    /**
     * Processes the markdown content
     *
     * @param markdown content in the markdown file
     * @return processed node
     * @throws IOException
     */
    public static Node processMarkDownFile(String markdown) throws IOException {
        Node document = parser.parse(markdown);

        // see if markdown document has includes
        if (document instanceof Document) {
            Document doc = (Document) document;
            if (doc.contains(JekyllTagExtension.TAG_LIST)) {
                List<JekyllTag> tagList = JekyllTagExtension.TAG_LIST.getFrom(doc);
                Map<String, String> includeHtmlMap = new HashMap<>();

                for (JekyllTag tag : tagList) {
                    String includeFile = tag.getParameters().toString();
                    if (tag.getTag().equals("include") && !includeFile.isEmpty() && !includeHtmlMap.containsKey(includeFile)) {
                        // see if it exists
                        if (included.containsKey(includeFile)) {
                            // have the file
                            String text = included.get(includeFile);

                            if (includeFile.endsWith(".md")) {
                                Node includeDoc = parser.parse(text);
                                String includeHtml = renderer.render(includeDoc);
                                includeHtmlMap.put(includeFile, includeHtml);

                                if (includeDoc instanceof com.vladsch.flexmark.ast.Document) {
                                    // copy any definition of reference elements from included file to our document
                                    parser.transferReferences(doc, (com.vladsch.flexmark.ast.Document) includeDoc);
                                }
                            } else {
                                includeHtmlMap.put(includeFile, text);
                            }
                        }
                    }

                    if (!includeHtmlMap.isEmpty()) {
                        doc.set(JekyllTagExtension.INCLUDED_HTML, includeHtmlMap);
                    }
                }
            }
        }
        return document;
    }

    /**
     * Write the HTMl content to file
     *
     * @param fileName    file name
     * @param htmlContent content to be written
     * @throws IOException
     */
    public static void writeHtmlToFile(String fileName, String htmlContent) throws IOException {
        String filePath = generated_file_directory + fileName + ".html";
        Files.write(Paths.get(filePath), htmlContent.getBytes());
    }

    /**
     * Get HTML layout content from layout file
     *
     * @param layoutTemplateFileName
     * @return
     * @throws IOException
     */
    public static String getLayoutContent(String layoutTemplateFileName) throws IOException {
        return Files.lines(Paths.get(layout_template_directory + layoutTemplateFileName + ".html")).
                collect(Collectors.joining("\n"));
    }

    /**
     * Read the markdown files and get the content
     *
     * @param name markdown file name
     * @return markdown content of the file
     * @throws IOException
     */
    public static String readMarkdownFile(String name) throws IOException {
        String markdownContent = Files.lines(Paths.get(markdown_file_directory + name))
                .collect(Collectors.joining("\n"));
        return markdownContent;
    }

    /**
     * List all files in the directory
     *
     * @param fileDir file directory
     * @return list of files in that directory
     * @throws IOException
     */
    public static List<File> listAllFilesInDirectory(String fileDir) throws IOException {
        List<File> filesInFolder = Files.walk(Paths.get(fileDir))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .collect(Collectors.toList());
        return filesInFolder;
    }

    /**
     * Reads the included files i.e. commonly shared files and read their contents
     *
     * @return list of ncluded files i.e. commonly shared files and their content
     * @throws IOException
     */
    public static Map<String, String> getIncludedFilesWithContent() throws IOException {
        List<File> fileList = listAllFilesInDirectory(included_file_directory);
        Map<String, String> included = new HashMap<>();
        for (File file : fileList) {
            String content = Files.lines(Paths.get(included_file_directory + file.getName()))
                    .collect(Collectors.joining("\n"));
            included.put(file.getName(), content);
        }
        return included;
    }

    /**
     * Generate the full HTML content by replacing the content passed down from the markdown
     *
     * @param layoutContent             content of the layout file
     * @param frontMatterList           list of yaml front matter defined in the markdown file
     * @param generatedHtmlFromMarkdown partial HTML file generated from markdown content
     */
    public static void generateHtmlFullDocument(StringBuilder layoutContent, Map<String, List<String>> frontMatterList, String generatedHtmlFromMarkdown) {
        Pattern pattern = Pattern.compile("\\{\\{(\\w*)\\}\\}");
        Matcher matchPattern = pattern.matcher(layoutContent);
        while (matchPattern.find()) {
            String matchedWord = matchPattern.group(0);

            // Remove curly braces from word
            matchedWord = matchedWord.replaceAll("\\{", "");
            matchedWord = matchedWord.replaceAll("\\}", "");

            if (matchedWord.equals("content")) {
                layoutContent = layoutContent.replace(matchPattern.start(), matchPattern.end(), generatedHtmlFromMarkdown);
            } else {
                String replacement = frontMatterList.get(matchedWord).get(0);
                layoutContent = layoutContent.replace(matchPattern.start(), matchPattern.end(), replacement);
            }
        }
    }

    /**
     * Adds the included files in the layout HTMl as HTML content
     *
     * @param layoutContent content of the layout
     * @throws IOException
     */
    public static void addIncludedFilesInHTML(StringBuilder layoutContent) throws IOException {
        Pattern pattern = Pattern.compile("\\{[%](.*?)\\}");
        Matcher matchPattern = pattern.matcher(layoutContent);
        while (matchPattern.find()) {
            String matchedWord = matchPattern.group(0);

            // Remove curly braces from word
            matchedWord = matchedWord.replaceAll("\\{% include", "").trim();
            matchedWord = matchedWord.replaceAll("%\\}", "").trim();

            // Generate HTML content from the matched file name
            String matchedFileContent = readMarkdownFile(matchedWord);
            String generatedHtml = renderer.render(processMarkDownFile(matchedFileContent));
            layoutContent = layoutContent.replace(matchPattern.start(), matchPattern.end(), generatedHtml);

        }
    }

    /**
     * Generate HTML page for file by file path
     *
     * @param filePath
     */
    public static void generatePageForFile(String filePath) throws IOException {
        File file = new File(filePath);
        generatePageForFile(file);
    }
}
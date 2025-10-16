package project.pipepipe.app.helper

/**
 * Helper interface for parsing and handling HTML content across platforms
 */
interface HtmlHelper {
    /**
     * Parses HTML string and returns a platform-specific formatted text object
     * @param html The HTML string to parse
     * @return Platform-specific formatted text representation
     */
    fun parseHtml(html: String): Any
}

expect fun getHtmlHelper(): HtmlHelper

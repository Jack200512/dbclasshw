package com.dbclasshw.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RichTextContentTest {
    private final RichTextContent validator=new RichTextContent();
    private final ObjectMapper json=new ObjectMapper();

    @Test void acceptsOnlySupportedStructures() throws Exception {
        var value=json.readTree("""
                {"type":"doc","content":[{"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Title","marks":[{"type":"bold"}]},{"type":"hardBreak","marks":[{"type":"bold"}]}]},
                {"type":"orderedList","attrs":{"start":1,"type":null},"content":[{"type":"listItem","content":[{"type":"paragraph"}]}]}]}
                """);
        assertThat(validator.serialize(value)).contains("orderedList");
    }

    @Test void rejectsExecutableNodesAttributesAndOversizedContent() throws Exception {
        for(String value:new String[]{
                "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"src\":\"javascript:alert(1)\"}}]}",
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"attrs\":{\"onclick\":\"alert(1)\"}}]}",
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"link\"}]}]}]}",
                "{\"type\":\"doc\",\"content\":[]}", "\"<script>bad</script>\""}) {
            var node=json.readTree(value);
            assertThatThrownBy(()->validator.serialize(node)).isInstanceOf(IllegalArgumentException.class);
        }
        var oversized=json.createObjectNode().put("type","doc");
        oversized.putArray("content").addObject().put("type","paragraph").putArray("content")
                .addObject().put("type","text").put("text","x".repeat(60001));
        assertThatThrownBy(()->validator.serialize(oversized)).isInstanceOf(IllegalArgumentException.class);
    }
}
